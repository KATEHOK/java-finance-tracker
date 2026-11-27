package io.github.katehok.finance.cli.command;

import io.github.katehok.finance.core.WalletService;
import java.nio.file.Path;
import java.util.List;

public class ExportCommand implements Command {

    private final WalletService wallets;

    public ExportCommand(WalletService wallets) {
        this.wallets = wallets;
    }

    @Override
    public String name() {
        return "export";
    }

    @Override
    public String usage() {
        return "export <file>";
    }

    @Override
    public List<Help> help() {
        return List.of(new Help(usage(), "save your wallet to CSV, e.g. export wallet.csv"));
    }

    @Override
    public void execute(Session session, String[] args) {
        if (args.length != 2) {
            printUsage(session);
            return;
        }
        Path path = Path.of(args[1]);
        if (!session.tryUseFile(path)) return;
        int count = wallets.exportTo(session.getUser(), path);
        session.println("Exported " + Args.plural(count, "operation", "operations") + " to " + args[1]);
    }
}
