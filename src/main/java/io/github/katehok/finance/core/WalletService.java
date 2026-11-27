package io.github.katehok.finance.core;

import io.github.katehok.finance.core.FinanceException.Reason;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

public class WalletService {

    public static final String TRANSFER_CATEGORY = "Transfers";

    private final UserRepository users;
    private final WalletRepository wallets;
    private final WalletFormat format;
    private final AlertService alerts;
    private final Clock clock;

    public WalletService(UserRepository users, WalletRepository wallets, WalletFormat format, AlertService alerts,
            Clock clock) {
        this.users = users;
        this.wallets = wallets;
        this.format = format;
        this.alerts = alerts;
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public List<Alert> addIncome(User user, Money amount, String category, LocalDate date, String note) {
        return add(user, new Income(orToday(date), category, amount, note), null);
    }

    public List<Alert> addExpense(User user, Money amount, String category, LocalDate date, String note) {
        return add(user, new Expense(orToday(date), category, amount, note), category);
    }

    public Category addCategory(User user, String name) {
        return user.getWallet().addCategory(name);
    }

    public Category renameCategory(User user, String oldName, String newName) {
        return user.getWallet().renameCategory(oldName, newName);
    }

    public void deleteCategory(User user, String name) {
        user.getWallet().deleteCategory(name);
    }

    public List<Alert> setBudget(User user, String category, Money limit) {
        Wallet wallet = user.getWallet();
        List<Alert> before = alerts.check(wallet);
        wallet.setBudget(category, limit);
        return alerts.fresh(before, alerts.check(wallet), null);
    }

    public Category removeBudget(User user, String category) {
        return user.getWallet().removeBudget(category);
    }

    public List<Alert> alerts(User user) {
        return alerts.check(user.getWallet());
    }

    public List<Alert> transfer(User from, String toLogin, Money amount, String note) {
        String to = AuthService.normalize(toLogin);
        if (to.equals(from.getLogin())) throw new FinanceException(Reason.SELF_TRANSFER, "You can't transfer to yourself");
        if (!users.exists(to)) throw new FinanceException(Reason.UNKNOWN_USER, "Unknown user " + to);
        if (amount == null || !amount.isPositive()) {
            throw new FinanceException(Reason.INVALID_AMOUNT, "Amount must be greater than zero");
        }
        Wallet wallet = from.getWallet();
        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new FinanceException(Reason.NOT_ENOUGH_MONEY, "Not enough money, balance is " + wallet.getBalance());
        }
        String txt = Operation.checkNote(note);
        String inNote = join("from " + from.getLogin(), txt);
        String outNote = join("to " + to, txt);
        int max = Operation.MAX_NOTE_LENGTH - Math.max(inNote.length(), outNote.length()) + txt.length();
        if (txt.length() > max) {
            throw new FinanceException(Reason.INVALID_NAME, "Transfer note is longer than " + max + " characters");
        }
        Income income = new Income(today(), TRANSFER_CATEGORY, amount, inNote);
        Expense expense = new Expense(today(), TRANSFER_CATEGORY, amount, outNote);
        // Перевод сохраняю сразу у обоих, не дожидаясь выхода. Сначала получатель:
        // если запись упадет, у отправителя ничего не спишется
        Wallet target = wallets.load(to);
        target.ensureCategory(TRANSFER_CATEGORY);
        target.add(income);
        wallets.save(to, target);
        wallet.ensureCategory(TRANSFER_CATEGORY);
        List<Alert> fresh = add(from, expense, TRANSFER_CATEGORY);
        wallets.save(from.getLogin(), wallet);
        return fresh;
    }

    public int exportTo(User user, Path path) {
        format.write(user.getWallet(), path);
        return user.getWallet().getOperations().size();
    }

    public Wallet.MergeResult importFrom(User user, Path path) {
        Wallet src = format.read(path);
        return user.getWallet().merge(src);
    }

    private List<Alert> add(User user, Operation op, String expenseCategory) {
        Wallet wallet = user.getWallet();
        List<Alert> before = alerts.check(wallet);
        wallet.add(op);
        return alerts.fresh(before, alerts.check(wallet), expenseCategory);
    }

    private LocalDate orToday(LocalDate date) {
        return date == null ? today() : date;
    }

    private static String join(String head, String tail) {
        return tail.isEmpty() ? head : head + ": " + tail;
    }
}
