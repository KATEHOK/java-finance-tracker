package io.github.katehok.finance.cli;

import io.github.katehok.finance.cli.command.AlertsCommand;
import io.github.katehok.finance.cli.command.Args;
import io.github.katehok.finance.cli.command.BalanceCommand;
import io.github.katehok.finance.cli.command.BudgetCommand;
import io.github.katehok.finance.cli.command.CategoryCommand;
import io.github.katehok.finance.cli.command.Command;
import io.github.katehok.finance.cli.command.CommandRegistry;
import io.github.katehok.finance.cli.command.ExitCommand;
import io.github.katehok.finance.cli.command.ExpenseCommand;
import io.github.katehok.finance.cli.command.ExportCommand;
import io.github.katehok.finance.cli.command.HelpCommand;
import io.github.katehok.finance.cli.command.HistoryCommand;
import io.github.katehok.finance.cli.command.ImportCommand;
import io.github.katehok.finance.cli.command.IncomeCommand;
import io.github.katehok.finance.cli.command.LoginCommand;
import io.github.katehok.finance.cli.command.LogoutCommand;
import io.github.katehok.finance.cli.command.RegisterCommand;
import io.github.katehok.finance.cli.command.ReportCommand;
import io.github.katehok.finance.cli.command.Session;
import io.github.katehok.finance.cli.command.Shortcut;
import io.github.katehok.finance.cli.command.TransferCommand;
import io.github.katehok.finance.core.AuthService;
import io.github.katehok.finance.core.FinanceException;
import io.github.katehok.finance.core.FinanceException.Reason;
import io.github.katehok.finance.core.ReportService;
import io.github.katehok.finance.core.WalletService;
import java.io.Console;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;

public class Cli {

    private final Session session;
    private final CommandRegistry commands = new CommandRegistry();
    private final Object lock = new Object();

    public Cli(AuthService auth, WalletService wallets, ReportService reports, Console console, InputStream in,
            PrintStream out) {
        this(auth, wallets, reports, console, in, out, null);
    }

    public Cli(AuthService auth, WalletService wallets, ReportService reports, Console console, InputStream in,
            PrintStream out, Path dataDir) {
        session = new Session(auth, console, in, out, dataDir);
        CategoryCommand category = new CategoryCommand(wallets);
        BudgetCommand budget = new BudgetCommand(wallets, reports);
        commands.add(new RegisterCommand(auth))
                .add(new LoginCommand(auth, wallets))
                .add(new LogoutCommand())
                .add(new IncomeCommand(wallets))
                .add(new ExpenseCommand(wallets))
                .add(category)
                .add(new Shortcut("categories", category, "list"))
                .add(budget)
                .add(new Shortcut("budgets", budget, "list"))
                .add(new BalanceCommand())
                .add(new ReportCommand(reports))
                .add(new HistoryCommand())
                .add(new AlertsCommand(wallets))
                .add(new TransferCommand(wallets))
                .add(new ExportCommand(wallets))
                .add(new ImportCommand(wallets))
                .add(new HelpCommand(commands))
                .add(new ExitCommand());
    }

    private String formatHint(Reason reason, Command cmd) {
        String hint = cmd.hint(reason);
        if (hint != null) return hint;
        switch (reason) {
            case INVALID_LOGIN:
                return "A login looks like alice or bob_92";
            case INVALID_PASSWORD:
                return "Choose another password";
            case LOGIN_TAKEN:
                return "Choose another login or log in: login <login>";
            case BAD_CREDENTIALS:
                return "Check the login and password or register: register <login>";
            case UNKNOWN_USER:
                return "Check the login of the recipient";
            case EMPTY_NAME:
                return "Type a name, put it in quotes if it has spaces: \"Utility bills\"";
            case INVALID_NAME:
                return "Try a shorter name or note without control characters";
            case INVALID_AMOUNT:
                return "Use a positive number like 1500 or 99.90";
            case INVALID_DATE:
                return "Date must look like " + Args.EXAMPLE_DATE;
            case INVALID_PERIOD:
                return "Put the earlier date after --from";
            case UNKNOWN_CATEGORY:
            case CATEGORY_EXISTS:
                return "See your categories: category list";
            case CATEGORY_IN_USE:
                return "Rename it instead: category rename <old> <new>";
            case NO_BUDGET:
                return "See your budgets: budget list";
            case NOT_ENOUGH_MONEY:
                return "Check your balance: balance";
            case SELF_TRANSFER:
                return "Choose another user";
            case BAD_FILE:
                return "The file must be a CSV with the header type,date,category,amount,note";
            default:
                return "Try again";
        }
    }

    public boolean execute(String input) {
        List<String> tokens = Tokenizer.trySplit(input);
        if (tokens == null) {
            session.println("Oops: Unclosed quote");
            return true;
        }
        if (tokens.isEmpty()) return true;
        String[] args = tokens.toArray(new String[0]);
        Command cmd = commands.tryFind(args[0]);
        if (cmd == null) {
            session.println("Unknown command. Type help to see commands");
            return true;
        }
        if (cmd.needsUser() && !session.isLoggedIn()) {
            session.println("Log in first: login <login> or register <login>");
            return true;
        }
        try {
            synchronized (lock) {
                cmd.execute(session, args);
            }
        } catch (FinanceException e) {
            session.println("Oops: " + e.getMessage() + ". " + formatHint(e.getReason(), cmd));
        } catch (UncheckedIOException e) {
            session.println(Messages.describe(e));
        } catch (RuntimeException e) {
            session.println("Unexpected error: " + e.getMessage());
        }
        return !session.takeExitRequest();
    }

    public void run() {
        session.println("Hi! This is a console personal finance tracker. Type help to see commands");
        while (true) {
            String input = session.scanLine("");
            if (input == null) {
                session.println("");
                break;
            }
            if (input.isEmpty()) continue;
            if (!execute(input)) break;
        }
        synchronized (lock) {
            if (session.isLoggedIn()) {
                if (session.trySave()) session.println("Your wallet is saved");
                session.logOut();
            }
        }
        session.println("Bye");
    }

    public void saveOnShutdown() {
        if (!session.isLoggedIn()) return;
        // Жду, пока закончится текущая команда, чтобы не сохранить кошелек на середине перевода или импорта.
        // До входа не жду: register и login держат lock, пока ждут пароль
        synchronized (lock) {
            if (session.isLoggedIn() && session.trySave()) session.println("\nYour wallet is saved");
        }
    }
}
