package io.github.katehok.finance.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.katehok.finance.core.FinanceException.Reason;
import io.github.katehok.finance.infra.MemoryUserRepository;
import io.github.katehok.finance.infra.MemoryWalletRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AuthServiceTest {

    private static final String PASSWORD = "secret1";

    private final MemoryUserRepository users = new MemoryUserRepository();
    private final MemoryWalletRepository wallets = new MemoryWalletRepository();
    private final AuthService auth = new AuthService(users, wallets);

    private void assertReason(Reason reason, Executable executable) {
        FinanceException e = assertThrows(FinanceException.class, executable);
        assertEquals(reason, e.getReason());
    }

    @Test
    void registerCreatesUserWithEmptyWallet() {
        User user = auth.register("alice", PASSWORD);
        assertEquals("alice", user.getLogin());
        assertTrue(user.getWallet().getCategories().isEmpty());
        assertTrue(user.getWallet().getOperations().isEmpty());
        assertTrue(users.exists("alice"));
    }

    @Test
    void registerNormalizesLogin() {
        User user = auth.register("  Alice_01 ", PASSWORD);
        assertEquals("alice_01", user.getLogin());
        assertTrue(users.exists("alice_01"));
    }

    @Test
    void normalize() {
        assertEquals("bob", AuthService.normalize(" BoB "));
        assertEquals("", AuthService.normalize(null));
        assertTrue(AuthService.isValidLogin("Bob"));
        assertFalse(AuthService.isValidLogin(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "ab", "abcdefghijklmnopqrstu", "bad login", "alice!", "a-b-c", "алиса"})
    void registerRejectsBadLogin(String login) {
        assertReason(Reason.INVALID_LOGIN, () -> auth.register(login, PASSWORD));
    }

    @Test
    void registerAcceptsLoginLimits() {
        assertEquals("abc", auth.register("abc", PASSWORD).getLogin());
        assertEquals("a".repeat(20), auth.register("a".repeat(20), PASSWORD).getLogin());
    }

    @Test
    void registerRejectsBadPassword() {
        assertReason(Reason.INVALID_PASSWORD, () -> auth.register("alice", null));
        assertReason(Reason.INVALID_PASSWORD, () -> auth.register("alice", "12345"));
        assertReason(Reason.INVALID_PASSWORD, () -> auth.register("alice", "a".repeat(65)));
        assertReason(Reason.INVALID_PASSWORD, () -> auth.register("alice", "      "));
        assertFalse(users.exists("alice"));
    }

    @Test
    void registerTakenLogin() {
        auth.register("alice", PASSWORD);
        FinanceException e = assertThrows(FinanceException.class, () -> auth.register("ALICE", "other12"));
        assertEquals(Reason.LOGIN_TAKEN, e.getReason());
        assertEquals("Login alice is already taken", e.getMessage());
        assertEquals("alice", auth.login("alice", PASSWORD).getLogin());
    }

    @Test
    void loginOk() {
        auth.register("alice", PASSWORD);
        assertEquals("alice", auth.login(" Alice ", PASSWORD).getLogin());
    }

    @Test
    void wrongPasswordAndUnknownLoginGiveSameError() {
        auth.register("alice", PASSWORD);
        FinanceException wrong = assertThrows(FinanceException.class, () -> auth.login("alice", "secret2"));
        FinanceException unknown = assertThrows(FinanceException.class, () -> auth.login("bob", PASSWORD));
        assertEquals(Reason.BAD_CREDENTIALS, wrong.getReason());
        assertEquals(Reason.BAD_CREDENTIALS, unknown.getReason());
        assertEquals(wrong.getMessage(), unknown.getMessage());
        assertReason(Reason.BAD_CREDENTIALS, () -> auth.login("alice", null));
    }

    @Test
    void saveAndLoginRestoresWallet() {
        User user = auth.register("alice", PASSWORD);
        Wallet wallet = user.getWallet();
        wallet.addCategory("Food");
        wallet.setBudget("Food", Money.ofRubles(4000));
        wallet.add(new Expense(LocalDate.of(2026, 11, 25), "Food", Money.ofRubles(300), "lunch"));
        auth.save(user);
        Wallet restored = auth.login("alice", PASSWORD).getWallet();
        assertEquals(wallet.getOperations(), restored.getOperations());
        assertEquals(Money.ofRubles(4000), restored.getCategory("Food").getBudget().get());
    }

    @Test
    void unsavedChangesAreLost() {
        User user = auth.register("alice", PASSWORD);
        auth.save(user);
        user.getWallet().addCategory("Food");
        assertFalse(auth.login("alice", PASSWORD).getWallet().hasCategory("Food"));
    }
}
