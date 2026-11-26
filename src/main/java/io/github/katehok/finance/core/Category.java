package io.github.katehok.finance.core;

import java.util.Locale;
import java.util.Optional;

public class Category {

    private String name;
    private Money budget;

    Category(String name) {
        this.name = name;
    }

    public static String keyOf(String name) {
        return name.strip().toLowerCase(Locale.ROOT);
    }

    public String getName() {
        return name;
    }

    public String getKey() {
        return keyOf(name);
    }

    public Optional<Money> getBudget() {
        return Optional.ofNullable(budget);
    }

    public boolean hasBudget() {
        return budget != null;
    }

    // Сеттеры package-private - менять категорию можно только через кошелек, он следит за инвариантами
    void setName(String name) {
        this.name = name;
    }

    void setBudget(Money budget) {
        this.budget = budget;
    }
}
