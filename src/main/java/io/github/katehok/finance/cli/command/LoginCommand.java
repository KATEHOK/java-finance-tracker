package io.github.katehok.finance.cli.command;

import io.github.katehok.finance.core.AuthService;
import io.github.katehok.finance.core.WalletService;
import java.util.List;

public class LoginCommand implements Command {

    private final AuthService auth;
    private final WalletService wallets;

    public LoginCommand(AuthService auth, WalletService wallets) {
        this.auth = auth;
        this.wallets = wallets;
    }

    @Override
    public String name() {
        return "login";
    }

    @Override
    public String usage() {
        return "login <login>";
    }

    @Override
    public List<Help> help() {
        return List.of(new Help(usage(), "log in, e.g. login alice"));
    }

    @Override
    public boolean needsUser() {
        return false;
    }

    @Override
    public void execute(Session session, String[] args) {
        if (session.isLoggedIn()) {
            session.printAlreadyLoggedIn();
            return;
        }
        if (args.length != 2) {
            printUsage(session);
            return;
        }
        String password = session.scanPassword("Password: ");
        if (password == null) return;
        session.logIn(auth.login(args[1], password));
        session.println("Logged in as " + session.getUser().getLogin());
        session.printBalance();
        session.printAlerts(wallets.alerts(session.getUser()));
    }
}
