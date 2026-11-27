package io.github.katehok.finance.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.katehok.finance.core.Alert.Type;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AlertServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 25);
    private static final Money LIMIT = Money.ofRubles(4000);

    private final AlertService service = new AlertService();
    private final Wallet wallet = new Wallet();

    @BeforeEach
    void setUp() {
        wallet.addCategory("Salary");
        wallet.addCategory("Food");
        wallet.addCategory("Taxi");
        wallet.setBudget("Food", LIMIT);
        wallet.add(new Income(DAY, "Salary", Money.ofRubles(10000), ""));
    }

    private void expense(String category, Money amount) {
        wallet.add(new Expense(DAY, category, amount, ""));
    }

    private Alert near(Money amount) {
        return new Alert(Type.BUDGET_NEAR, "Food", LIMIT, amount);
    }

    private Alert exceeded(Money amount) {
        return new Alert(Type.BUDGET_EXCEEDED, "Food", LIMIT, amount);
    }

    @Test
    void noAlertsBelowThreshold() {
        expense("Food", new Money(319999));
        assertTrue(service.check(wallet).isEmpty());
    }

    @Test
    void nearAtEightyPercent() {
        expense("Food", Money.ofRubles(3200));
        assertEquals(List.of(near(Money.ofRubles(3200))), service.check(wallet));
    }

    @Test
    void nearAtExactLimit() {
        expense("Food", Money.ofRubles(4000));
        assertEquals(List.of(near(Money.ofRubles(4000))), service.check(wallet));
    }

    @Test
    void exceededOverLimit() {
        expense("Food", Money.ofRubles(4000));
        expense("Food", new Money(1));
        assertEquals(List.of(exceeded(new Money(400001))), service.check(wallet));
    }

    @Test
    void noAlertsWithoutBudget() {
        expense("Taxi", Money.ofRubles(9000));
        assertTrue(service.check(wallet).isEmpty());
    }

    @Test
    void customWarnPercent() {
        expense("Food", Money.ofRubles(2000));
        assertEquals(List.of(near(Money.ofRubles(2000))), new AlertService(50).check(wallet));
        assertTrue(service.check(wallet).isEmpty());
    }

    @Test
    void warnPercentMustBeInRange() {
        assertThrows(IllegalArgumentException.class, () -> new AlertService(0));
        assertThrows(IllegalArgumentException.class, () -> new AlertService(101));
        assertEquals(100, new AlertService(100).getWarnPercent());
    }

    @Test
    void expensesOverIncome() {
        expense("Taxi", Money.ofRubles(10500));
        assertEquals(List.of(Alert.global(Type.EXPENSES_OVER_INCOME, Money.ofRubles(500))), service.check(wallet));
    }

    @Test
    void zeroBalance() {
        expense("Taxi", Money.ofRubles(10000));
        assertEquals(List.of(Alert.global(Type.ZERO_BALANCE, Money.ZERO)), service.check(wallet));
    }

    @Test
    void noZeroBalanceAlertForEmptyWallet() {
        assertTrue(service.check(new Wallet()).isEmpty());
    }

    @Test
    void budgetAndBalanceAlertsTogether() {
        expense("Food", Money.ofRubles(11000));
        List<Alert> alerts = service.check(wallet);
        assertEquals(List.of(exceeded(Money.ofRubles(11000)),
                Alert.global(Type.EXPENSES_OVER_INCOME, Money.ofRubles(1000))), alerts);
    }

    @Test
    void keys() {
        assertEquals("BUDGET_NEAR:food", near(LIMIT).key());
        assertEquals("ZERO_BALANCE", Alert.global(Type.ZERO_BALANCE, Money.ZERO).key());
    }

    @Test
    void freshKeepsOnlyNewAlerts() {
        Alert near = near(Money.ofRubles(3500));
        assertEquals(List.of(near), service.fresh(List.of(), List.of(near), "Food"));
        assertEquals(List.of(), service.fresh(List.of(near(Money.ofRubles(3300))), List.of(near), "Food"));
    }

    @Test
    void freshReportsNearToExceededChange() {
        Alert exceeded = exceeded(Money.ofRubles(4500));
        assertEquals(List.of(exceeded),
                service.fresh(List.of(near(Money.ofRubles(3500))), List.of(exceeded), "Food"));
    }

    @Test
    void freshRepeatsExceededForSameCategory() {
        Alert before = exceeded(Money.ofRubles(4500));
        Alert after = exceeded(Money.ofRubles(4600));
        assertEquals(List.of(after), service.fresh(List.of(before), List.of(after), "food"));
    }

    @Test
    void freshDoesNotRepeatExceededForOtherCategory() {
        Alert before = exceeded(Money.ofRubles(4500));
        assertEquals(List.of(), service.fresh(List.of(before), List.of(before), "Taxi"));
        assertEquals(List.of(), service.fresh(List.of(before), List.of(before), null));
    }

    @Test
    void freshDoesNotRepeatGlobalAlerts() {
        Alert before = Alert.global(Type.EXPENSES_OVER_INCOME, Money.ofRubles(500));
        Alert after = Alert.global(Type.EXPENSES_OVER_INCOME, Money.ofRubles(700));
        assertEquals(List.of(), service.fresh(List.of(before), List.of(after), "Taxi"));
    }
}
