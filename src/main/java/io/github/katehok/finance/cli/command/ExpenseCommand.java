package io.github.katehok.finance.cli.command;

import io.github.katehok.finance.core.Alert;
import io.github.katehok.finance.core.Money;
import io.github.katehok.finance.core.User;
import io.github.katehok.finance.core.WalletService;
import java.time.LocalDate;
import java.util.List;

public class ExpenseCommand extends OperationCommand {

    public ExpenseCommand(WalletService wallets) {
        super(wallets);
    }

    @Override
    public String name() {
        return "expense";
    }

    @Override
    public List<Help> help() {
        return List.of(new Help("expense <amount> <category> [date] [note]",
                "add an expense, e.g. expense 300 Food " + Args.EXAMPLE_DATE + " lunch"));
    }

    @Override
    protected List<Alert> add(User user, Money amount, String category, LocalDate date, String note) {
        return wallets.addExpense(user, amount, category, date, note);
    }
}
