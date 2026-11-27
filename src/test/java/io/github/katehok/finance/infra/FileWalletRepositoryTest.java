package io.github.katehok.finance.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.katehok.finance.core.Expense;
import io.github.katehok.finance.core.FinanceException;
import io.github.katehok.finance.core.FinanceException.Reason;
import io.github.katehok.finance.core.Money;
import io.github.katehok.finance.core.Wallet;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileWalletRepositoryTest {

    @TempDir
    Path dir;

    private FileWalletRepository repository(Path walletDir) {
        return new FileWalletRepository(walletDir, new CsvWalletFormat());
    }

    @Test
    void loadsEmptyWalletWhenFileMissing() {
        Path walletDir = dir.resolve("wallets");
        Wallet wallet = repository(walletDir).load("alice");
        assertTrue(wallet.getCategories().isEmpty());
        assertTrue(wallet.getOperations().isEmpty());
        assertFalse(Files.exists(walletDir));
    }

    @Test
    void savedWalletSurvivesReload() {
        Path walletDir = dir.resolve("data/wallets");
        Wallet wallet = new Wallet();
        wallet.addCategory("Food");
        wallet.setBudget("Food", Money.ofRubles(4000));
        wallet.add(new Expense(LocalDate.of(2026, 11, 25), "Food", Money.ofRubles(300), "lunch"));
        repository(walletDir).save("alice", wallet);

        Wallet loaded = repository(walletDir).load("alice");
        assertTrue(Files.exists(walletDir.resolve("alice.csv")));
        assertEquals(Money.ofRubles(4000), loaded.getCategory("food").getBudget().orElseThrow());
        assertEquals(wallet.getOperations(), loaded.getOperations());
        assertTrue(repository(walletDir).load("bob").getOperations().isEmpty());
    }

    @Test
    void brokenFileMentionsPath() throws IOException {
        Path path = dir.resolve("alice.csv");
        Files.writeString(path, "type,date,category,amount,note\nexpense,2026-11-25,Food,abc,\n");
        FinanceException e = assertThrows(FinanceException.class, () -> repository(dir).load("alice"));
        assertEquals(Reason.BAD_FILE, e.getReason());
        assertEquals(path + ": Line 2: Bad amount abc", e.getMessage());
    }

    @Test
    void rejectsLoginOutsideDir() {
        FileWalletRepository repository = repository(dir);
        assertThrows(IllegalArgumentException.class, () -> repository.load("../alice"));
        assertThrows(IllegalArgumentException.class, () -> repository.save("Alice", new Wallet()));
    }
}
