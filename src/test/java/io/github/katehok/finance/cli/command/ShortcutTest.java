package io.github.katehok.finance.cli.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.katehok.finance.core.FinanceException.Reason;
import io.github.katehok.finance.core.Money;
import io.github.katehok.finance.core.ReportService;
import io.github.katehok.finance.core.Wallet;
import org.junit.jupiter.api.Test;

class ShortcutTest {

    private final SessionFixture fixture = new SessionFixture();
    private final BudgetCommand budget = new BudgetCommand(fixture.wallets, new ReportService());
    private final Shortcut budgets = new Shortcut("budgets", budget, "list");

    @Test
    void runsTargetWithItsArguments() {
        Wallet wallet = fixture.logIn("alice");
        wallet.addCategory("Food");
        wallet.setBudget("Food", Money.ofRubles(4000));
        String expected = fixture.run(budget, "budget", "list");
        assertEquals(expected, fixture.run(budgets, "budgets"));
        assertEquals(expected, fixture.run(budgets, "budgets", "extra", "words"));
        assertTrue(expected.contains("Food"), expected);
    }

    @Test
    void takesRulesFromTarget() {
        assertEquals("budgets", budgets.name());
        assertTrue(budgets.needsUser());
        assertEquals(budget.hint(Reason.UNKNOWN_CATEGORY), budgets.hint(Reason.UNKNOWN_CATEGORY));
        assertTrue(budgets.help().isEmpty());
    }
}
