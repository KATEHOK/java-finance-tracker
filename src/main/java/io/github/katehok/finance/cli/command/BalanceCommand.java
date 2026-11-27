package io.github.katehok.finance.cli.command;

import io.github.katehok.finance.core.Wallet;
import java.util.List;

public class BalanceCommand implements Command {

    @Override
    public String name() {
        return "balance";
    }

    @Override
    public String usage() {
        return "balance";
    }

    @Override
    public List<Help> help() {
        return List.of(new Help(usage(), "show total income, expenses and balance"));
    }

    @Override
    public void execute(Session session, String[] args) {
        Wallet wallet = session.getWallet();
        session.println("Total income: " + wallet.getIncome());
        session.println("Total expenses: " + wallet.getExpense());
        session.printBalance();
    }
}
