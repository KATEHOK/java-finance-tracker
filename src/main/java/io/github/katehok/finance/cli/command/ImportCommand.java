package io.github.katehok.finance.cli.command;

import io.github.katehok.finance.core.Wallet.MergeResult;
import io.github.katehok.finance.core.WalletService;
import java.nio.file.Path;
import java.util.List;

public class ImportCommand implements Command {

    private final WalletService wallets;

    public ImportCommand(WalletService wallets) {
        this.wallets = wallets;
    }

    @Override
    public String name() {
        return "import";
    }

    @Override
    public String usage() {
        return "import <file>";
    }

    @Override
    public List<Help> help() {
        return List.of(new Help(usage(), "add operations from CSV, e.g. import wallet.csv"));
    }

    @Override
    public void execute(Session session, String[] args) {
        if (args.length != 2) {
            printUsage(session);
            return;
        }
        Path path = Path.of(args[1]);
        if (!session.tryUseFile(path)) return;
        MergeResult result = wallets.importFrom(session.getUser(), path);
        session.println("Imported " + Args.plural(result.operations(), "operation", "operations") + ", "
                + Args.plural(result.categories(), "new category", "new categories") + " and "
                + Args.plural(result.budgets(), "new budget", "new budgets") + " from " + args[1]);
        session.printAlerts(wallets.alerts(session.getUser()));
    }
}
