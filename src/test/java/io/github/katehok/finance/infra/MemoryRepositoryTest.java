package io.github.katehok.finance.infra;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.katehok.finance.core.Income;
import io.github.katehok.finance.core.Money;
import io.github.katehok.finance.core.Password;
import io.github.katehok.finance.core.Wallet;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class MemoryRepositoryTest {

    @Test
    void walletRepositoryKeepsCopies() {
        MemoryWalletRepository wallets = new MemoryWalletRepository();
        Wallet wallet = new Wallet();
        wallet.addCategory("Salary");
        wallets.save("alice", wallet);
        wallet.add(new Income(LocalDate.of(2026, 11, 25), "Salary", Money.ofRubles(100), ""));

        Wallet loaded = wallets.load("alice");
        assertTrue(loaded.hasCategory("Salary"));
        assertTrue(loaded.getOperations().isEmpty());

        loaded.addCategory("Food");
        assertFalse(wallets.load("alice").hasCategory("Food"));
        assertTrue(wallets.load("bob").getCategories().isEmpty());
    }

    @Test
    void userRepositoryFindsSavedPassword() {
        MemoryUserRepository users = new MemoryUserRepository();
        assertFalse(users.exists("alice"));
        users.save("alice", Password.create("secret1"));
        assertTrue(users.exists("alice"));
        assertTrue(users.findPassword("alice").orElseThrow().matches("secret1"));
        assertTrue(users.findPassword("bob").isEmpty());
    }
}
