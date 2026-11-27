package io.github.katehok.finance.cli.command;

import io.github.katehok.finance.cli.ReportFormatter;
import io.github.katehok.finance.core.Alert;
import io.github.katehok.finance.core.Category;
import io.github.katehok.finance.core.FinanceException.Reason;
import io.github.katehok.finance.core.Money;
import io.github.katehok.finance.core.ReportService;
import io.github.katehok.finance.core.ReportService.Report;
import io.github.katehok.finance.core.Wallet;
import io.github.katehok.finance.core.WalletService;
import java.util.List;

public class BudgetCommand implements Command {

    private final WalletService wallets;
    private final ReportService reports;

    public BudgetCommand(WalletService wallets, ReportService reports) {
        this.wallets = wallets;
        this.reports = reports;
    }

    @Override
    public String name() {
        return "budget";
    }

    @Override
    public String usage() {
        return "budget set <category> <amount> | budget delete <category> | budget list";
    }

    @Override
    public List<Help> help() {
        return List.of(
                new Help("budget set <category> <amount>", "set a budget, e.g. budget set Food 4000"),
                new Help("budget delete <category>", "remove a budget, e.g. budget delete Food"),
                new Help("budget list | budgets", "show budgets and what is left"));
    }

    @Override
    public String hint(Reason reason) {
        if (reason == Reason.UNKNOWN_CATEGORY) return CREATE_CATEGORY_HINT;
        return null;
    }

    @Override
    public void execute(Session session, String[] args) {
        if (Args.isSub(args, "set", 4)) {
            set(session, args[2], args[3]);
        } else if (Args.isSub(args, "delete", 3)) {
            Category category = wallets.removeBudget(session.getUser(), args[2]);
            session.println("Removed budget for " + category.getName());
        } else if (Args.isSub(args, "list", 2)) {
            list(session);
        } else {
            printUsage(session);
        }
    }

    private void set(Session session, String name, String src) {
        Money amount = Args.tryAmount(src);
        if (amount == null) {
            session.println(Args.BAD_AMOUNT);
            return;
        }
        List<Alert> fresh = wallets.setBudget(session.getUser(), name, amount);
        Wallet wallet = session.getWallet();
        String txt = wallet.getCategory(name).getName();
        session.println("Budget for " + txt + " is set to " + amount + ". Spent: " + wallet.getExpense(txt));
        session.printAlerts(fresh);
    }

    private void list(Session session) {
        Report report = reports.report(session.getWallet());
        if (report.budgets().isEmpty()) {
            session.println("You have no budgets yet. Set one: budget set <category> <amount>");
            return;
        }
        session.print(ReportFormatter.formatBudgets("", report.budgets()));
    }
}
