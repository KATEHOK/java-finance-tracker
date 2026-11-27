package io.github.katehok.finance.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.katehok.finance.core.FinanceException.Reason;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class OperationTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 25);
    private static final Money AMOUNT = Money.ofRubles(300);

    private void assertReason(Reason reason, Executable executable) {
        FinanceException e = assertThrows(FinanceException.class, executable);
        assertEquals(reason, e.getReason());
    }

    @Test
    void rejectsEmptyDate() {
        assertReason(Reason.INVALID_DATE, () -> new Income(null, "Salary", AMOUNT, ""));
    }

    @Test
    void rejectsEmptyCategory() {
        assertReason(Reason.EMPTY_NAME, () -> new Expense(DAY, null, AMOUNT, ""));
        assertReason(Reason.EMPTY_NAME, () -> new Expense(DAY, "  ", AMOUNT, ""));
    }

    @Test
    void rejectsNotPositiveAmount() {
        assertReason(Reason.INVALID_AMOUNT, () -> new Income(DAY, "Salary", null, ""));
        assertReason(Reason.INVALID_AMOUNT, () -> new Income(DAY, "Salary", Money.ZERO, ""));
        assertReason(Reason.INVALID_AMOUNT, () -> new Expense(DAY, "Food", new Money(-1), ""));
    }

    @Test
    void nullNoteBecomesEmpty() {
        assertEquals("", new Income(DAY, "Salary", AMOUNT, null).note());
        assertEquals("", new Expense(DAY, "Food", AMOUNT, null).note());
    }

    @Test
    void applyToDependsOnType() {
        List<Operation> ops = List.of(new Income(DAY, "Salary", Money.ofRubles(1000), ""),
                new Expense(DAY, "Food", AMOUNT, ""), new Expense(DAY, "Taxi", AMOUNT, ""));
        Money balance = Money.ZERO;
        for (Operation op : ops) {
            balance = op.applyTo(balance);
        }
        assertEquals(Money.ofRubles(400), balance);
    }

    @Test
    void withCategoryKeepsTypeAndFields() {
        Operation op = new Expense(DAY, "food", AMOUNT, "lunch").withCategory("Food");
        assertTrue(op instanceof Expense);
        assertEquals(new Expense(DAY, "Food", AMOUNT, "lunch"), op);
        Operation income = new Income(DAY, "bonus", AMOUNT, "q4").withCategory("Bonus");
        assertEquals(new Income(DAY, "Bonus", AMOUNT, "q4"), income);
    }

    @Test
    void withCategoryValidates() {
        Operation op = new Income(DAY, "Salary", AMOUNT, "");
        assertReason(Reason.EMPTY_NAME, () -> op.withCategory(" "));
    }

    @Test
    void checksNote() {
        assertEquals("lunch", new Expense(DAY, "Food", AMOUNT, "  lunch ").note());
        assertEquals("", new Income(DAY, "Salary", AMOUNT, null).note());
        assertReason(Reason.INVALID_NAME, () -> new Expense(DAY, "Food", AMOUNT, "a\tb"));
        assertReason(Reason.INVALID_NAME, () -> new Income(DAY, "Salary", AMOUNT, "a".repeat(Operation.MAX_NOTE_LENGTH + 1)));
        assertEquals(Operation.MAX_NOTE_LENGTH, new Income(DAY, "Salary", AMOUNT, "a".repeat(Operation.MAX_NOTE_LENGTH)).note().length());
    }
}
