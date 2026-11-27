package io.github.katehok.finance.cli.command;

import io.github.katehok.finance.core.Alert;
import io.github.katehok.finance.core.Money;
import io.github.katehok.finance.core.User;
import io.github.katehok.finance.core.WalletService;
import java.time.LocalDate;
import java.util.List;

public class IncomeCommand extends OperationCommand {

    public IncomeCommand(WalletService wallets) {
        super(wallets);
    }

    @Override
    public String name() {
        return "income";
    }

    @Override
    public List<Help> help() {
        return List.of(new Help("income <amount> <category> [date] [note]", "add income, e.g. income 60000 Salary"));
    }

    @Override
    protected List<Alert> add(User user, Money amount, String category, LocalDate date, String note) {
        return wallets.addIncome(user, amount, category, date, note);
    }
}
