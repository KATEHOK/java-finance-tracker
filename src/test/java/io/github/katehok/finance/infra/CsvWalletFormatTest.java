package io.github.katehok.finance.infra;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.katehok.finance.core.Category;
import io.github.katehok.finance.core.Expense;
import io.github.katehok.finance.core.FinanceException;
import io.github.katehok.finance.core.FinanceException.Reason;
import io.github.katehok.finance.core.Income;
import io.github.katehok.finance.core.Money;
import io.github.katehok.finance.core.Operation;
import io.github.katehok.finance.core.Wallet;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CsvWalletFormatTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 25);
    private static final String HEADER = "type,date,category,amount,note\n";

    @TempDir
    Path dir;

    private final CsvWalletFormat format = new CsvWalletFormat();

    private static Money money(String txt) {
        return Money.tryParse(txt);
    }

    private static List<String> categories(Wallet wallet) {
        List<String> names = new ArrayList<>();
        for (Category category : wallet.getCategories()) {
            names.add(category.getName() + "=" + category.getBudget().map(Money::toPlain).orElse("-"));
        }
        return names;
    }

    private static void assertSameWallet(Wallet expected, Wallet actual) {
        assertEquals(categories(expected), categories(actual));
        assertEquals(expected.getOperations(), actual.getOperations());
    }

    private Wallet read(String txt) throws IOException {
        Path path = dir.resolve("wallet.csv");
        Files.writeString(path, txt, UTF_8);
        return format.read(path);
    }

    private void assertBadFile(String txt, String message) {
        FinanceException e = assertThrows(FinanceException.class, () -> read(txt));
        assertEquals(Reason.BAD_FILE, e.getReason());
        assertEquals(message, e.getMessage());
    }

    @Test
    void roundTripKeepsEverything() {
        Wallet wallet = new Wallet();
        wallet.addCategory("Зарплата");
        wallet.addCategory("Food");
        wallet.addCategory("Taxi, cabs");
        wallet.addCategory("Say \"hi\"");
        wallet.addCategory("Unused");
        wallet.setBudget("Food", money("4000"));
        wallet.setBudget("Taxi, cabs", money("1500.5"));
        wallet.add(new Income(DAY, "Зарплата", money("60000"), "аванс за ноябрь"));
        wallet.add(new Expense(DAY, "Food", money("300.25"), "lunch, with friends"));
        wallet.add(new Expense(DAY.plusDays(1), "Taxi, cabs", money("450"), "he said \"go\""));
        wallet.add(new Expense(DAY.plusDays(2), "Say \"hi\"", money("1"), "кофе ☕"));
        wallet.add(new Expense(DAY.plusDays(3), "Food", money("99.99"), ""));
        Path path = dir.resolve("wallet.csv");

        format.write(wallet, path);

        assertSameWallet(wallet, format.read(path));
    }

    @Test
    void writesExpectedCsv() throws IOException {
        Wallet wallet = new Wallet();
        wallet.addCategory("Food");
        wallet.addCategory("Salary");
        wallet.setBudget("Food", money("4000"));
        wallet.add(new Income(DAY, "Salary", money("60000"), ""));
        wallet.add(new Expense(DAY, "Food", money("300.5"), "pizza, \"big\""));
        Path path = dir.resolve("wallet.csv");

        format.write(wallet, path);

        String expected = HEADER
                + "category,,Food,,\n"
                + "category,,Salary,,\n"
                + "budget,,Food,4000.00,\n"
                + "income,2026-11-25,Salary,60000.00,\n"
                + "expense,2026-11-25,Food,300.50,\"pizza, \"\"big\"\"\"\n";
        assertEquals(expected, Files.readString(path, UTF_8));
    }

    @Test
    void writeCreatesDirsAndReplacesFile() throws IOException {
        Path path = dir.resolve("nested/data/wallet.csv");
        Wallet wallet = new Wallet();
        wallet.addCategory("Food");
        format.write(new Wallet(), path);
        format.write(wallet, path);

        assertEquals(List.of("Food=-"), categories(format.read(path)));
        try (Stream<Path> files = Files.list(path.getParent())) {
            assertEquals(List.of(path), files.toList());
        }
    }

    @Test
    void readsHandMadeCsvWithoutCategoryRows() throws IOException {
        Wallet wallet = read("Type,Date,Category,Amount,Note\n"
                + "\n"
                + "INCOME,2026-11-01,Salary,60000,\n"
                + "Expense,2026-11-02,Food,\"1,5\",\"\"\n"
                + "   \n"
                + "expense, 2026-11-03 ,food, 300 , lunch \n"
                + "budget,,Taxi,1000,\n");

        assertEquals(List.of("Salary=-", "Food=-", "Taxi=1000.00"), categories(wallet));
        List<Operation> ops = List.of(
                new Income(LocalDate.of(2026, 11, 1), "Salary", money("60000"), ""),
                new Expense(LocalDate.of(2026, 11, 2), "Food", money("1.5"), ""),
                new Expense(LocalDate.of(2026, 11, 3), "Food", money("300"), "lunch"));
        assertEquals(ops, wallet.getOperations());
    }

    @Test
    void skipsBom() throws IOException {
        Wallet wallet = read("﻿" + HEADER + "category,,Food,,\n");
        assertEquals(List.of("Food=-"), categories(wallet));
    }

    @Test
    void headerOnlyGivesEmptyWallet() throws IOException {
        Wallet wallet = read(HEADER);
        assertEquals(List.of(), categories(wallet));
        assertEquals(List.of(), wallet.getOperations());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "transfer,2026-11-01,Food,300,|Line 3: Unknown type transfer",
        "expense,01.11.2026,Food,300,|Line 3: Bad date 01.11.2026, expected yyyy-mm-dd",
        "expense,,Food,300,|Line 3: Date is empty",
        "expense,2026-11-01,Food,abc,|Line 3: Bad amount abc",
        "expense,2026-11-01,Food,-5,|Line 3: Bad amount -5",
        "expense,2026-11-01,Food,0,|Line 3: Amount must be greater than zero",
        "budget,,Food,0,|Line 3: Budget must be greater than zero",
        "expense,2026-11-01,,300,|Line 3: Category name is empty",
        "expense,2026-11-01,Food,300|Line 3: Expected 5 fields, got 4",
        "expense,2026-11-01,Food,300,note,extra|Line 3: Expected 5 fields, got 6",
        "expense,2026-11-01,Food,300,\"unclosed|Line 3: Broken quotes",
        "expense,2026-11-01,Food,300,a\"b|Line 3: Broken quotes",
        "expense,2026-11-01,Food,300,\"a\"b|Line 3: Broken quotes",
        "expense,2026-11-01,Food,300,a\tb|Line 3: Note has control characters",
    })
    void failsOnBadLine(String line, String message) {
        assertBadFile(HEADER + "category,,Food,,\n" + line + "\n", message);
    }

    @Test
    void failsOnBadHeader() {
        assertBadFile("\n\ndate,type,category,amount,note\n", "Line 3: Expected header type,date,category,amount,note");
    }

    @Test
    void failsOnEmptyFile() {
        assertBadFile("\n  \n", "File is empty");
    }

    @Test
    void failsOnTooLongCategory() {
        String name = "x".repeat(Wallet.MAX_NAME_LENGTH + 1);
        assertBadFile(HEADER + "category,," + name + ",,\n",
                "Line 2: Category name is longer than " + Wallet.MAX_NAME_LENGTH + " characters");
    }

    @Test
    void failsOnNonUtf8File() throws IOException {
        Path path = dir.resolve("wallet.csv");
        Files.write(path, new byte[] {'t', (byte) 0xff, '\n'});
        FinanceException e = assertThrows(FinanceException.class, () -> format.read(path));
        assertEquals(path + " is not a valid UTF-8 file", e.getMessage());
    }

    @Test
    void failsOnMissingFile() {
        Path path = dir.resolve("missing.csv");
        UncheckedIOException e = assertThrows(UncheckedIOException.class, () -> format.read(path));
        assertEquals("Couldn't read " + path, e.getMessage());
    }
}
