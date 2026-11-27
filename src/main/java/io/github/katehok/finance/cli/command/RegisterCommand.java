package io.github.katehok.finance.cli.command;

import io.github.katehok.finance.core.AuthService;
import java.util.List;

public class RegisterCommand implements Command {

    private final AuthService auth;

    public RegisterCommand(AuthService auth) {
        this.auth = auth;
    }

    @Override
    public String name() {
        return "register";
    }

    @Override
    public String usage() {
        return "register <login>";
    }

    @Override
    public List<Help> help() {
        return List.of(new Help(usage(), "create an account and log in, e.g. register alice"));
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
        auth.checkAvailable(args[1]);
        String password = session.scanPassword("Password: ");
        if (password == null) return;
        AuthService.checkPassword(password);
        String repeat = session.scanPassword("Repeat password: ");
        if (repeat == null) return;
        if (!password.equals(repeat)) {
            session.println("Oops: Passwords don't match. Try again: register " + args[1]);
            return;
        }
        session.logIn(auth.register(args[1], password));
        session.println("Registered and logged in as " + session.getUser().getLogin());
        session.println("Start with a category: category add Food");
    }
}
