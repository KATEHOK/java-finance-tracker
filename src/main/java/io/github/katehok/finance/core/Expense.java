package io.github.katehok.finance.core;

import java.time.LocalDate;

public record Expense(LocalDate date, String category, Money amount, String note) implements Operation {

    public Expense {
        Operation.check(date, category, amount);
        note = Operation.checkNote(note);
    }

    @Override
    public Money applyTo(Money balance) {
        return balance.minus(amount);
    }

    @Override
    public Expense withCategory(String category) {
        return new Expense(date, category, amount, note);
    }
}
