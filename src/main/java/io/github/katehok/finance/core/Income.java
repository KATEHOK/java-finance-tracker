package io.github.katehok.finance.core;

import java.time.LocalDate;

public record Income(LocalDate date, String category, Money amount, String note) implements Operation {

    public Income {
        Operation.check(date, category, amount);
        note = Operation.checkNote(note);
    }

    @Override
    public Money applyTo(Money balance) {
        return balance.plus(amount);
    }

    @Override
    public Income withCategory(String category) {
        return new Income(date, category, amount, note);
    }
}
