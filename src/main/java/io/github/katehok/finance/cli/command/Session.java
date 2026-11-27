package io.github.katehok.finance.cli.command;

import io.github.katehok.finance.cli.Messages;
import io.github.katehok.finance.core.Alert;
import io.github.katehok.finance.core.AuthService;
import io.github.katehok.finance.core.User;
import io.github.katehok.finance.core.Wallet;
import java.io.Console;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class Session {

    private static final String PROMPT_PREFIX = "finance $ ";

    private final AuthService auth;
    private final Console console;
    private final Scanner scanner;
    private final PrintStream out;
    private final Path dataDir;
    private volatile User user;
    private boolean exitRequested;

    public Session(AuthService auth, Console console, InputStream in, PrintStream out, Path dataDir) {
        this.auth = auth;
        // Console бывает и без терминала (ввод из файла или пайпа) - тогда пароль читаю обычной строкой
        this.console = console != null && console.isTerminal() ? console : null;
        this.scanner = new Scanner(in);
        this.out = out;
        this.dataDir = dataDir == null ? null : dataDir.toAbsolutePath().normalize();
    }

    public void print(String txt) {
        out.print(txt);
    }

    public void println(String txt) {
        print(txt + '\n');
    }

    private String getPromptPrefix() {
        return user == null ? PROMPT_PREFIX : user.getLogin() + "@" + PROMPT_PREFIX;
    }

    public String scanLine(String prompt) {
        print(getPromptPrefix() + prompt);
        if (!scanner.hasNextLine()) return null;
        return scanner.nextLine().strip();
    }

    public String scanPassword(String prompt) {
        if (console == null) {
            print(getPromptPrefix() + prompt);
            if (!scanner.hasNextLine()) return null;
            return scanner.nextLine();
        }
        char[] chars = console.readPassword("%s", getPromptPrefix() + prompt);
        if (chars == null) return null;
        String txt = new String(chars);
        Arrays.fill(chars, ' ');
        return txt;
    }

    public boolean isLoggedIn() {
        return user != null;
    }

    public User getUser() {
        return user;
    }

    public Wallet getWallet() {
        return user.getWallet();
    }

    public void logIn(User user) {
        this.user = user;
    }

    public void logOut() {
        user = null;
    }

    public boolean tryUseFile(Path path) {
        if (dataDir == null || !toRealPath(path).startsWith(toRealPath(dataDir))) return true;
        println("Oops: " + path + " is inside the data directory " + dataDir + ". Choose another file");
        return false;
    }

    private static Path toRealPath(Path path) {
        Path abs = path.toAbsolutePath().normalize();
        Path existing = abs;
        while (existing != null && !Files.exists(existing)) {
            existing = existing.getParent();
        }
        if (existing == null) return abs;
        try {
            return existing.toRealPath().resolve(existing.relativize(abs));
        } catch (IOException e) {
            return abs;
        }
    }

    public void requestExit() {
        exitRequested = true;
    }

    public boolean takeExitRequest() {
        boolean requested = exitRequested;
        exitRequested = false;
        return requested;
    }

    public void printAlreadyLoggedIn() {
        println("You are already logged in as " + user.getLogin() + ". Type logout first");
    }

    public void printBalance() {
        println("Balance: " + getWallet().getBalance());
    }

    public void printAlerts(List<Alert> alerts) {
        for (Alert alert : alerts) {
            println("Alert: " + Messages.formatAlert(alert));
        }
    }

    public boolean trySaveAndLogOut() {
        if (!trySave()) {
            println("You are still logged in. Try again or keep a copy: export <file>");
            return false;
        }
        logOut();
        return true;
    }

    public boolean trySave() {
        try {
            auth.save(user);
            return true;
        } catch (UncheckedIOException e) {
            println("Couldn't save your wallet: " + Messages.describe(e));
        } catch (RuntimeException e) {
            println("Couldn't save your wallet: " + e.getMessage());
        }
        return false;
    }
}
