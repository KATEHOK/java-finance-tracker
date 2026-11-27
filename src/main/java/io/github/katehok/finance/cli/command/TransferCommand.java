package io.github.katehok.finance.cli.command;

import io.github.katehok.finance.core.Alert;
import io.github.katehok.finance.core.AuthService;
import io.github.katehok.finance.core.Money;
import io.github.katehok.finance.core.WalletService;
import java.util.List;

public class TransferCommand implements Command {

    private final WalletService wallets;

    public TransferCommand(WalletService wallets) {
        this.wallets = wallets;
    }

    @Override
    public String name() {
        return "transfer";
    }

    @Override
    public String usage() {
        return "transfer <login> <amount> [note]";
    }

    @Override
    public List<Help> help() {
        return List.of(new Help(usage(), "send money to another user, e.g. transfer bob 500 for pizza"));
    }

    @Override
    public void execute(Session session, String[] args) {
        if (args.length < 3) {
            printUsage(session);
            return;
        }
        Money amount = Args.tryAmount(args[2]);
        if (amount == null) {
            session.println(Args.BAD_AMOUNT);
            return;
        }
        List<Alert> fresh = wallets.transfer(session.getUser(), args[1], amount, Args.join(args, 3));
        session.println("Transferred " + amount + " to " + AuthService.normalize(args[1]) + ". Balance: "
                + session.getWallet().getBalance());
        session.printAlerts(fresh);
    }
}
