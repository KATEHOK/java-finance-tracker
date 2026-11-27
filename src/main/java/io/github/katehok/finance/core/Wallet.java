package io.github.katehok.finance.core;

import io.github.katehok.finance.core.FinanceException.Reason;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class Wallet {

    public static final int MAX_NAME_LENGTH = 40;

    public record MergeResult(int categories, int budgets, int operations) {
    }

    private final Map<String, Category> categories = new LinkedHashMap<>();
    private final List<Operation> operations = new ArrayList<>();

    public List<Category> getCategories() {
        return List.copyOf(categories.values());
    }

    public List<Operation> getOperations() {
        return List.copyOf(operations);
    }

    public Optional<Category> findCategory(String name) {
        if (name == null) return Optional.empty();
        return Optional.ofNullable(categories.get(Category.keyOf(name)));
    }

    public boolean hasCategory(String name) {
        return findCategory(name).isPresent();
    }

    public Category getCategory(String name) {
        return findCategory(name).orElseThrow(
                () -> new FinanceException(Reason.UNKNOWN_CATEGORY, "Unknown category " + name));
    }

    public Category addCategory(String name) {
        String txt = checkName(name);
        Optional<Category> other = findCategory(txt);
        if (other.isPresent()) {
            throw new FinanceException(Reason.CATEGORY_EXISTS, "Category " + other.get().getName() + " already exists");
        }
        Category category = new Category(txt);
        categories.put(category.getKey(), category);
        return category;
    }

    public Category ensureCategory(String name) {
        Optional<Category> category = findCategory(name);
        if (category.isPresent()) return category.get();
        return addCategory(name);
    }

    public Category renameCategory(String oldName, String newName) {
        Category category = getCategory(oldName);
        String txt = checkName(newName);
        Optional<Category> other = findCategory(txt);
        if (other.isPresent() && other.get() != category) {
            throw new FinanceException(Reason.CATEGORY_EXISTS, "Category " + other.get().getName() + " already exists");
        }
        String oldKey = category.getKey();
        category.setName(txt);
        Map<String, Category> copy = new LinkedHashMap<>(categories);
        categories.clear();
        for (Category item : copy.values()) {
            categories.put(item.getKey(), item);
        }
        // Операции - неизменяемые record, поэтому при переименовании заменяю их копиями с новым именем
        operations.replaceAll(op -> Category.keyOf(op.category()).equals(oldKey) ? op.withCategory(txt) : op);
        return category;
    }

    public void deleteCategory(String name) {
        Category category = getCategory(name);
        long count = operations.stream().filter(op -> isIn(op, category)).count();
        if (count > 0) {
            throw new FinanceException(Reason.CATEGORY_IN_USE,
                    "Category " + category.getName() + " has " + count + " operation(s)");
        }
        categories.remove(category.getKey());
    }

    public Category setBudget(String name, Money limit) {
        Category category = getCategory(name);
        if (limit == null || !limit.isPositive()) {
            throw new FinanceException(Reason.INVALID_AMOUNT, "Budget must be greater than zero");
        }
        category.setBudget(limit);
        return category;
    }

    public Category removeBudget(String name) {
        Category category = getCategory(name);
        if (!category.hasBudget()) {
            throw new FinanceException(Reason.NO_BUDGET, "Category " + category.getName() + " has no budget");
        }
        category.setBudget(null);
        return category;
    }

    public Operation add(Operation op) {
        Category category = getCategory(op.category());
        Operation saved = op.withCategory(category.getName());
        operations.add(saved);
        return saved;
    }

    public MergeResult merge(Wallet other) {
        if (other == this) return new MergeResult(0, 0, 0);
        int newCategories = 0;
        int newBudgets = 0;
        for (Category src : other.categories.values()) {
            if (!hasCategory(src.getName())) {
                addCategory(src.getName());
                ++newCategories;
            }
            Category category = getCategory(src.getName());
            if (src.hasBudget() && !category.hasBudget()) {
                category.setBudget(src.getBudget().get());
                ++newBudgets;
            }
        }
        for (Operation op : other.operations) {
            add(op);
        }
        return new MergeResult(newCategories, newBudgets, other.operations.size());
    }

    public Money getBalance() {
        Money balance = Money.ZERO;
        for (Operation op : operations) {
            balance = op.applyTo(balance);
        }
        return balance;
    }

    public Money getIncome() {
        return sum(Income.class, null);
    }

    public Money getExpense() {
        return sum(Expense.class, null);
    }

    public Money getIncome(String name) {
        return sum(Income.class, getCategory(name));
    }

    public Money getExpense(String name) {
        return sum(Expense.class, getCategory(name));
    }

    private Money sum(Class<? extends Operation> type, Category category) {
        Money total = Money.ZERO;
        for (Operation op : operations) {
            if (type.isInstance(op) && (category == null || isIn(op, category))) total = total.plus(op.amount());
        }
        return total;
    }

    private static boolean isIn(Operation op, Category category) {
        return Category.keyOf(op.category()).equals(category.getKey());
    }

    private static String checkName(String name) {
        if (name == null || name.isBlank()) throw new FinanceException(Reason.EMPTY_NAME, "Category name is empty");
        String txt = name.strip();
        if (txt.length() > MAX_NAME_LENGTH) {
            throw new FinanceException(Reason.INVALID_NAME,
                    "Category name is longer than " + MAX_NAME_LENGTH + " characters");
        }
        if (Text.hasControlChars(txt)) {
            throw new FinanceException(Reason.INVALID_NAME, "Category name has control characters");
        }
        return txt;
    }
}
