package io.github.katehok.finance.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.katehok.finance.core.Alert.Type;
import io.github.katehok.finance.core.FinanceException.Reason;
import io.github.katehok.finance.infra.MemoryUserRepository;
import io.github.katehok.finance.infra.MemoryWalletRepository;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class WalletServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-25T10:00:00Z"), ZoneOffset.UTC);
    private static final Password PASSWORD = Password.create("secret1");
    private static final Money LIMIT = Money.ofRubles(1000);

    private static class MapWalletFormat implements WalletFormat {

        private final Map<Path, Wallet> files = new HashMap<>();

        @Override
        public void write(Wallet wallet, Path path) {
            Wallet copy = new Wallet();
            copy.merge(wallet);
            files.put(path, copy);
        }

        @Override
        public Wallet read(Path path) {
            Wallet wallet = files.get(path);
            if (wallet == null) throw new FinanceException(Reason.BAD_FILE, "No file " + path);
            return wallet;
        }
    }

    private final MemoryUserRepository users = new MemoryUserRepository();
    private final MemoryWalletRepository wallets = new MemoryWalletRepository();
    private final MapWalletFormat format = new MapWalletFormat();
    private final WalletService service = new WalletService(users, wallets, format, new AlertService(), CLOCK);
    private final User alice = new User("alice", new Wallet());

    @BeforeEach
    void setUp() {
        users.save("alice", PASSWORD);
        users.save("bob", PASSWORD);
        service.addCategory(alice, "Salary");
        service.addCategory(alice, "Food");
        service.addIncome(alice, Money.ofRubles(5000), "Salary", null, null);
    }

    private void assertReason(Reason reason, Executable executable) {
        FinanceException e = assertThrows(FinanceException.class, executable);
        assertEquals(reason, e.getReason());
    }

    private Operation last(Wallet wallet) {
        List<Operation> ops = wallet.getOperations();
        return ops.get(ops.size() - 1);
    }

    private List<Alert> spend(long rubles) {
        return service.addExpense(alice, Money.ofRubles(rubles), "food", null, null);
    }

    private Alert foodAlert(Type type, long rubles) {
        return new Alert(type, "Food", LIMIT, Money.ofRubles(rubles));
    }

    @Test
    void todayComesFromClock() {
        assertEquals(TODAY, service.today());
    }

    @Test
    void incomeDefaultsToToday() {
        service.addIncome(alice, Money.ofRubles(100), "salary", null, "  advance ");
        assertEquals(new Income(TODAY, "Salary", Money.ofRubles(100), "advance"), last(alice.getWallet()));
    }

    @Test
    void expenseWithDateAndNote() {
        LocalDate date = LocalDate.of(2026, 11, 1);
        service.addExpense(alice, Money.ofRubles(300), "Food", date, "lunch");
        assertEquals(new Expense(date, "Food", Money.ofRubles(300), "lunch"), last(alice.getWallet()));
        assertEquals(Money.ofRubles(4700), alice.getWallet().getBalance());
    }

    @Test
    void addRejectsUnknownCategoryAndLongNote() {
        assertReason(Reason.UNKNOWN_CATEGORY, () -> service.addExpense(alice, Money.ofRubles(1), "Taxi", null, ""));
        String note = "a".repeat(Operation.MAX_NOTE_LENGTH + 1);
        assertReason(Reason.INVALID_NAME, () -> service.addExpense(alice, Money.ofRubles(1), "Food", null, note));
        assertReason(Reason.INVALID_AMOUNT, () -> service.addIncome(alice, Money.ZERO, "Salary", null, ""));
        assertEquals(1, alice.getWallet().getOperations().size());
    }

    @Test
    void expenseReturnsFreshAlerts() {
        service.setBudget(alice, "Food", LIMIT);
        assertEquals(List.of(), spend(500));
        assertEquals(List.of(foodAlert(Type.BUDGET_NEAR, 800)), spend(300));
        assertEquals(List.of(), spend(100));
        assertEquals(List.of(foodAlert(Type.BUDGET_EXCEEDED, 1100)), spend(200));
        assertEquals(List.of(foodAlert(Type.BUDGET_EXCEEDED, 1150)), spend(50));
    }

    @Test
    void incomeDoesNotRepeatExceeded() {
        service.setBudget(alice, "Food", LIMIT);
        spend(1100);
        assertEquals(List.of(), service.addIncome(alice, Money.ofRubles(10), "Salary", null, null));
        assertEquals(List.of(foodAlert(Type.BUDGET_EXCEEDED, 1100)), service.alerts(alice));
    }

    @Test
    void expenseOverIncomeAlert() {
        assertEquals(List.of(Alert.global(Type.EXPENSES_OVER_INCOME, Money.ofRubles(500))), spend(5500));
    }

    @Test
    void setBudgetReturnsFreshAlerts() {
        spend(900);
        assertEquals(List.of(foodAlert(Type.BUDGET_NEAR, 900)), service.setBudget(alice, "Food", LIMIT));
        assertEquals(List.of(), service.setBudget(alice, "Food", Money.ofRubles(1100)));
        service.removeBudget(alice, "Food");
        assertTrue(service.alerts(alice).isEmpty());
    }

    @Test
    void categoryCommands() {
        service.renameCategory(alice, "Food", "Groceries");
        assertTrue(alice.getWallet().hasCategory("groceries"));
        service.deleteCategory(alice, "Groceries");
        assertEquals(1, alice.getWallet().getCategories().size());
    }

    @Test
    void transferSavesBothWallets() {
        List<Alert> alerts = service.transfer(alice, " BOB ", Money.ofRubles(1500), " for lunch ");
        assertEquals(List.of(), alerts);
        Expense sent = new Expense(TODAY, WalletService.TRANSFER_CATEGORY, Money.ofRubles(1500), "to bob: for lunch");
        assertEquals(sent, last(alice.getWallet()));
        assertEquals(Money.ofRubles(3500), alice.getWallet().getBalance());
        assertEquals(Money.ofRubles(3500), wallets.load("alice").getBalance());
        Wallet bob = wallets.load("bob");
        Income received = new Income(TODAY, WalletService.TRANSFER_CATEGORY, Money.ofRubles(1500),
                "from alice: for lunch");
        assertEquals(List.of(received), bob.getOperations());
    }

    @Test
    void transferWithoutNote() {
        service.transfer(alice, "bob", Money.ofRubles(100), null);
        assertEquals("to bob", last(alice.getWallet()).note());
        assertEquals("from alice", last(wallets.load("bob")).note());
    }

    @Test
    void transferUsesExistingCategory() {
        Wallet bob = new Wallet();
        bob.addCategory("transfers");
        wallets.save("bob", bob);
        service.transfer(alice, "bob", Money.ofRubles(100), "");
        Wallet loaded = wallets.load("bob");
        assertEquals(1, loaded.getCategories().size());
        assertEquals("transfers", last(loaded).category());
    }

    @Test
    void transferWholeBalanceGivesZeroAlert() {
        List<Alert> alerts = service.transfer(alice, "bob", Money.ofRubles(5000), "");
        assertEquals(List.of(Alert.global(Type.ZERO_BALANCE, Money.ZERO)), alerts);
    }

    @Test
    void selfTransfer() {
        assertReason(Reason.SELF_TRANSFER, () -> service.transfer(alice, "ALICE", Money.ofRubles(1), ""));
    }

    @Test
    void transferToUnknownUser() {
        FinanceException e = assertThrows(FinanceException.class,
                () -> service.transfer(alice, "carol", Money.ofRubles(1), ""));
        assertEquals(Reason.UNKNOWN_USER, e.getReason());
        assertEquals("Unknown user carol", e.getMessage());
        assertEquals(1, alice.getWallet().getOperations().size());
    }

    @Test
    void transferWithTooLongNoteChangesNobody() {
        int max = Operation.MAX_NOTE_LENGTH - "from alice: ".length();
        FinanceException e = assertThrows(FinanceException.class,
                () -> service.transfer(alice, "bob", Money.ofRubles(100), "a".repeat(max + 1)));
        assertEquals("Transfer note is longer than " + max + " characters", e.getMessage());
        assertEquals(List.of(), wallets.load("bob").getOperations());
        assertEquals(Money.ofRubles(5000), alice.getWallet().getBalance());
        service.transfer(alice, "bob", Money.ofRubles(100), "a".repeat(max));
        assertEquals(Operation.MAX_NOTE_LENGTH, last(wallets.load("bob")).note().length());
    }

    @Test
    void transferNotEnoughMoney() {
        assertReason(Reason.NOT_ENOUGH_MONEY, () -> service.transfer(alice, "bob", new Money(500001), ""));
        assertEquals(Money.ofRubles(5000), alice.getWallet().getBalance());
        assertTrue(wallets.load("bob").getOperations().isEmpty());
        assertTrue(wallets.load("alice").getOperations().isEmpty());
    }

    @Test
    void transferInvalidAmount() {
        assertReason(Reason.INVALID_AMOUNT, () -> service.transfer(alice, "bob", Money.ZERO, ""));
        assertReason(Reason.INVALID_AMOUNT, () -> service.transfer(alice, "bob", null, ""));
        assertReason(Reason.INVALID_AMOUNT, () -> service.transfer(alice, "bob", new Money(-100), ""));
        assertTrue(wallets.load("bob").getOperations().isEmpty());
    }

    @Test
    void exportAndImport() {
        service.setBudget(alice, "Food", LIMIT);
        spend(300);
        Path path = Path.of("alice.csv");
        assertEquals(2, service.exportTo(alice, path));
        User bob = new User("bob", new Wallet());
        bob.getWallet().addCategory("food");
        Wallet.MergeResult result = service.importFrom(bob, path);
        assertEquals(new Wallet.MergeResult(1, 1, 2), result);
        assertEquals(alice.getWallet().getBalance(), bob.getWallet().getBalance());
        assertEquals("food", last(bob.getWallet()).category());
        assertEquals(LIMIT, bob.getWallet().getCategory("Food").getBudget().get());
    }

    @Test
    void importMissingFile() {
        assertReason(Reason.BAD_FILE, () -> service.importFrom(alice, Path.of("missing.csv")));
    }
}
