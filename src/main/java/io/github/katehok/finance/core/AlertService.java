package io.github.katehok.finance.core;

import io.github.katehok.finance.core.Alert.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AlertService {

    public static final int DEFAULT_WARN_PERCENT = 80;

    private final int warnPercent;

    public AlertService(int warnPercent) {
        if (warnPercent < 1 || warnPercent > 100) throw new IllegalArgumentException("warnPercent must be 1..100");
        this.warnPercent = warnPercent;
    }

    public AlertService() {
        this(DEFAULT_WARN_PERCENT);
    }

    public int getWarnPercent() {
        return warnPercent;
    }

    public List<Alert> check(Wallet wallet) {
        List<Alert> alerts = new ArrayList<>();
        for (Category category : wallet.getCategories()) {
            if (!category.hasBudget()) continue;
            Money limit = category.getBudget().get();
            Money spent = wallet.getExpense(category.getName());
            if (spent.compareTo(limit) > 0) {
                alerts.add(new Alert(Type.BUDGET_EXCEEDED, category.getName(), limit, spent));
            } else if (spent.isPositive() && spent.isAtLeastPercentOf(limit, warnPercent)) {
                alerts.add(new Alert(Type.BUDGET_NEAR, category.getName(), limit, spent));
            }
        }
        Money balance = wallet.getBalance();
        if (balance.isNegative()) {
            alerts.add(Alert.global(Type.EXPENSES_OVER_INCOME, Money.ZERO.minus(balance)));
        } else if (balance.isZero() && !wallet.getOperations().isEmpty()) {
            alerts.add(Alert.global(Type.ZERO_BALANCE, balance));
        }
        return alerts;
    }

    // Показываю только то, что изменилось после операции, иначе одно и то же оповещение сыпалось бы на каждый ввод
    // Исключение - перерасход в категории, куда только что записали расход: о нем говорю каждый раз
    public List<Alert> fresh(List<Alert> before, List<Alert> after, String category) {
        Set<String> seen = new HashSet<>();
        for (Alert alert : before) {
            seen.add(alert.key());
        }
        String key = category == null ? null : Category.keyOf(category);
        List<Alert> alerts = new ArrayList<>();
        for (Alert alert : after) {
            boolean isRepeat = alert.type() == Type.BUDGET_EXCEEDED && Category.keyOf(alert.category()).equals(key);
            if (!seen.contains(alert.key()) || isRepeat) alerts.add(alert);
        }
        return alerts;
    }
}
