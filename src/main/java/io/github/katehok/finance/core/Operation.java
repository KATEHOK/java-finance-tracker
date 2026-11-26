package io.github.katehok.finance.core;

import io.github.katehok.finance.core.FinanceException.Reason;
import java.time.LocalDate;

public sealed interface Operation permits Income, Expense {

    int MAX_NOTE_LENGTH = 150;

    LocalDate date();

    String category();

    Money amount();

    String note();

    Money applyTo(Money balance);

    Operation withCategory(String category);

    static void check(LocalDate date, String category, Money amount) {
        if (date == null) throw new FinanceException(Reason.INVALID_DATE, "Date is empty");
        if (category == null || category.isBlank()) throw new FinanceException(Reason.EMPTY_NAME, "Category is empty");
        if (amount == null || !amount.isPositive()) {
            throw new FinanceException(Reason.INVALID_AMOUNT, "Amount must be greater than zero");
        }
    }

    static String checkNote(String note) {
        if (note == null) return "";
        String txt = note.strip();
        if (Text.hasControlChars(txt)) throw new FinanceException(Reason.INVALID_NAME, "Note has control characters");
        if (txt.length() > MAX_NOTE_LENGTH) {
            throw new FinanceException(Reason.INVALID_NAME, "Note is longer than " + MAX_NOTE_LENGTH + " characters");
        }
        return txt;
    }
}
