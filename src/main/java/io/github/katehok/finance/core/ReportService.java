package io.github.katehok.finance.core;

import io.github.katehok.finance.core.FinanceException.Reason;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ReportService {

    public record Filter(List<String> categories, LocalDate from, LocalDate to) {

        public Filter {
            categories = categories == null ? List.of() : List.copyOf(categories);
        }

        public static Filter all() {
            return new Filter(List.of(), null, null);
        }

        public boolean isAll() {
            return categories.isEmpty() && from == null && to == null;
        }

        public boolean matches(Operation op) {
            if (from != null && op.date().isBefore(from)) return false;
            if (to != null && op.date().isAfter(to)) return false;
            return categories.isEmpty() || hasCategory(op.category());
        }

        public boolean hasCategory(String name) {
            String key = Category.keyOf(name);
            for (String category : categories) {
                if (Category.keyOf(category).equals(key)) return true;
            }
            return false;
        }
    }

    public record Line(String category, Money amount) {
    }

    public record BudgetLine(String category, Money limit, Money spent) {

        public Money remaining() {
            return limit.minus(spent);
        }
    }

    public record Report(Money income, Money expense, List<Line> incomes, List<Line> expenses,
            List<BudgetLine> budgets, int operations) {

        public Money balance() {
            return income.minus(expense);
        }

        public boolean isEmpty() {
            return operations == 0;
        }
    }

    public Report report(Wallet wallet, Filter filter) {
        check(wallet, filter);
        Map<String, Money> incomes = new LinkedHashMap<>();
        Map<String, Money> expenses = new LinkedHashMap<>();
        Money income = Money.ZERO;
        Money expense = Money.ZERO;
        int count = 0;
        for (Operation op : wallet.getOperations()) {
            if (!filter.matches(op)) continue;
            ++count;
            if (op instanceof Income) {
                income = income.plus(op.amount());
                incomes.merge(op.category(), op.amount(), Money::plus);
            } else {
                expense = expense.plus(op.amount());
                expenses.merge(op.category(), op.amount(), Money::plus);
            }
        }
        List<BudgetLine> budgets = new ArrayList<>();
        for (Category category : wallet.getCategories()) {
            if (!category.hasBudget()) continue;
            if (!filter.categories().isEmpty() && !filter.hasCategory(category.getName())) continue;
            budgets.add(new BudgetLine(category.getName(), category.getBudget().get(),
                    wallet.getExpense(category.getName())));
        }
        return new Report(income, expense, toLines(incomes), toLines(expenses), budgets, count);
    }

    public Report report(Wallet wallet) {
        return report(wallet, Filter.all());
    }

    private static void check(Wallet wallet, Filter filter) {
        if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
            throw new FinanceException(Reason.INVALID_PERIOD, "Start date " + filter.from() + " is after end date "
                    + filter.to());
        }
        Set<String> unknown = new LinkedHashSet<>();
        for (String category : filter.categories()) {
            if (!wallet.hasCategory(category)) unknown.add(category);
        }
        if (!unknown.isEmpty()) {
            String txt = String.join(", ", unknown);
            throw new FinanceException(Reason.UNKNOWN_CATEGORY,
                    (unknown.size() == 1 ? "Unknown category " : "Unknown categories ") + txt);
        }
    }

    private static List<Line> toLines(Map<String, Money> sums) {
        List<Line> lines = new ArrayList<>();
        for (Map.Entry<String, Money> entry : sums.entrySet()) {
            lines.add(new Line(entry.getKey(), entry.getValue()));
        }
        return lines;
    }
}
