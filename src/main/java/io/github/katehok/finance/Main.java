package io.github.katehok.finance;

import io.github.katehok.finance.cli.Cli;
import io.github.katehok.finance.cli.Messages;
import io.github.katehok.finance.core.AlertService;
import io.github.katehok.finance.core.AuthService;
import io.github.katehok.finance.core.FinanceException;
import io.github.katehok.finance.core.ReportService;
import io.github.katehok.finance.core.UserRepository;
import io.github.katehok.finance.core.WalletFormat;
import io.github.katehok.finance.core.WalletRepository;
import io.github.katehok.finance.core.WalletService;
import io.github.katehok.finance.infra.CsvWalletFormat;
import io.github.katehok.finance.infra.FileUserRepository;
import io.github.katehok.finance.infra.FileWalletRepository;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Clock;

public class Main {

    private static final Path DEFAULT_DATA_DIR = Path.of("data");
    private static final String USAGE = "Usage: java -jar finance-tracker.jar [data-dir]";

    static void main(String[] args) {
        if (!run(args)) System.exit(1);
    }

    private static boolean isHelp(String src) {
        return src.equals("-h") || src.equals("--help");
    }

    private static UserRepository loadUsers(Path file) {
        try {
            return new FileUserRepository(file);
        } catch (FinanceException e) {
            System.err.println("Can't load users: " + e.getMessage());
            return null;
        } catch (UncheckedIOException e) {
            System.err.println("Can't load users from " + file + ": " + Messages.describe(e.getCause()));
            return null;
        }
    }

    static boolean run(String[] args) {
        if (args.length == 1 && isHelp(args[0])) {
            System.out.println(USAGE);
            return true;
        }
        if (args.length > 1) {
            System.err.println(USAGE);
            return false;
        }
        Path dataDir = args.length == 1 ? Path.of(args[0]) : DEFAULT_DATA_DIR;
        UserRepository users = loadUsers(dataDir.resolve("users.tsv"));
        if (users == null) return false;

        WalletFormat format = new CsvWalletFormat();
        WalletRepository wallets = new FileWalletRepository(dataDir.resolve("wallets"), format);
        AuthService auth = new AuthService(users, wallets);
        WalletService walletService = new WalletService(users, wallets, format, new AlertService(),
                Clock.systemDefaultZone());
        Cli cli = new Cli(auth, walletService, new ReportService(), System.console(), System.in, System.out, dataDir);
        Runtime.getRuntime().addShutdownHook(new Thread(cli::saveOnShutdown));
        cli.run();
        return true;
    }
}
