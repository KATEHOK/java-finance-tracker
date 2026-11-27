package io.github.katehok.finance.cli.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.katehok.finance.core.Expense;
import io.github.katehok.finance.core.FinanceException;
import io.github.katehok.finance.core.FinanceException.Reason;
import io.github.katehok.finance.core.Income;
import io.github.katehok.finance.core.Money;
import io.github.katehok.finance.core.Operation;
import io.github.katehok.finance.core.Wallet;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OperationCommandTest {

    private final SessionFixture fixture = new SessionFixture();
    private final OperationCommand income = new IncomeCommand(fixture.wallets);
    private final OperationCommand expense = new ExpenseCommand(fixture.wallets);
    private Wallet wallet;

    @BeforeEach
    void setUp() {
        wallet = fixture.logIn("alice");
        wallet.addCategory("Salary");
        wallet.addCategory("Food");
    }

    @Test
    void incomeAddsIncomeForToday() {
        assertEquals("Added income 60,000.00 to Salary on 2026-11-25. Balance: 60,000.00\n",
                fixture.run(income, "income", "60000", "salary"));
        Operation op = wallet.getOperations().getLast();
        assertInstanceOf(Income.class, op);
        assertEquals(LocalDate.of(2026, 11, 25), op.date());
    }

    @Test
    void expenseTakesDateAndNote() {
        fixture.run(income, "income", "1000", "Salary");
        assertEquals("Added expense 300.00 to Food on 2026-11-20. Balance: 700.00\n",
                fixture.run(expense, "expense", "300", "Food", "2026-11-20", "lunch", "with", "bob"));
        Operation op = wallet.getOperations().getLast();
        assertInstanceOf(Expense.class, op);
        assertEquals("lunch with bob", op.note());
        assertEquals(Money.ofRubles(300), op.amount());
    }

    @Test
    void expensePrintsFreshAlerts() {
        fixture.run(income, "income", "100", "Salary");
        String txt = fixture.run(expense, "expense", "150", "Food");
        assertTrue(txt.endsWith("Alert: Expenses exceed income by 50.00\n"), txt);
    }

    @Test
    void usageDependsOnCommand() {
        assertEquals("Usage: income <amount> <category> [yyyy-mm-dd] [note]\n", fixture.run(income, "income"));
        assertEquals("Usage: expense <amount> <category> [yyyy-mm-dd] [note]\n",
                fixture.run(expense, "expense", "300"));
    }

    @Test
    void badAmountOrDateAddsNothing() {
        assertEquals(Args.BAD_AMOUNT + "\n", fixture.run(expense, "expense", "-5", "Food"));
        assertEquals(Args.BAD_DATE + "\n", fixture.run(expense, "expense", "5", "Food", "2026-13-01"));
        assertTrue(wallet.getOperations().isEmpty());
    }

    @Test
    void unknownCategoryHintsToCreateIt() {
        FinanceException e = assertThrows(FinanceException.class,
                () -> fixture.run(expense, "expense", "300", "Taxi"));
        assertEquals(Reason.UNKNOWN_CATEGORY, e.getReason());
        assertEquals("Create it first: category add <name>", expense.hint(e.getReason()));
        assertNull(expense.hint(Reason.NOT_ENOUGH_MONEY));
    }

    @Test
    void typeOfNamesOperation() {
        fixture.run(income, "income", "10", "Salary");
        fixture.run(expense, "expense", "5", "Food");
        assertEquals("income", OperationCommand.typeOf(wallet.getOperations().get(0)));
        assertEquals("expense", OperationCommand.typeOf(wallet.getOperations().get(1)));
    }
}
