package io.github.katehok.finance.core;

public record Alert(Type type, String category, Money limit, Money amount) {

    public enum Type {
        BUDGET_NEAR,
        BUDGET_EXCEEDED,
        EXPENSES_OVER_INCOME,
        ZERO_BALANCE,
    }

    public static Alert global(Type type, Money amount) {
        return new Alert(type, null, null, amount);
    }

    public String key() {
        return category == null ? type.name() : type.name() + ":" + Category.keyOf(category);
    }
}
