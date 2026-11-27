package io.github.katehok.finance.cli.command;

import io.github.katehok.finance.cli.Table;
import io.github.katehok.finance.cli.Table.Align;
import io.github.katehok.finance.core.Category;
import io.github.katehok.finance.core.Money;
import io.github.katehok.finance.core.User;
import io.github.katehok.finance.core.Wallet;
import io.github.katehok.finance.core.WalletService;
import java.util.List;

public class CategoryCommand implements Command {

    private final WalletService wallets;

    public CategoryCommand(WalletService wallets) {
        this.wallets = wallets;
    }

    @Override
    public String name() {
        return "category";
    }

    @Override
    public String usage() {
        return "category add <name> | category rename <old> <new> | category delete <name> | category list";
    }

    @Override
    public List<Help> help() {
        return List.of(
                new Help("category add <name>", "add a category, e.g. category add \"Utility bills\""),
                new Help("category rename <old> <new>", "rename a category, e.g. category rename Food Groceries"),
                new Help("category delete <name>", "delete a category without operations, e.g. category delete Taxi"),
                new Help("category list | categories", "show categories with totals and budgets"));
    }

    @Override
    public void execute(Session session, String[] args) {
        User user = session.getUser();
        Wallet wallet = session.getWallet();
        if (Args.isSub(args, "add", 3)) {
            Category category = wallets.addCategory(user, args[2]);
            session.println("Added category " + category.getName());
        } else if (Args.isSub(args, "rename", 4)) {
            String oldName = wallet.getCategory(args[2]).getName();
            Category category = wallets.renameCategory(user, args[2], args[3]);
            session.println("Renamed category " + oldName + " to " + category.getName());
        } else if (Args.isSub(args, "delete", 3)) {
            String name = wallet.getCategory(args[2]).getName();
            wallets.deleteCategory(user, args[2]);
            session.println("Deleted category " + name);
        } else if (Args.isSub(args, "list", 2)) {
            list(session, wallet);
        } else {
            printUsage(session);
        }
    }

    private void list(Session session, Wallet wallet) {
        if (wallet.getCategories().isEmpty()) {
            session.println("You have no categories yet. Add one: category add <name>");
            return;
        }
        Table table = new Table("", Align.LEFT, Align.RIGHT, Align.RIGHT, Align.RIGHT);
        table.addRow("Category", "Income", "Expenses", "Budget");
        for (Category category : wallet.getCategories()) {
            String name = category.getName();
            String budget = category.getBudget().map(Money::toString).orElse("-");
            table.addRow(name, wallet.getIncome(name).toString(), wallet.getExpense(name).toString(), budget);
        }
        session.print(table.format());
    }
}
