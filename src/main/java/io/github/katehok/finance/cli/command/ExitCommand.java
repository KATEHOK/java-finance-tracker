package io.github.katehok.finance.cli.command;

import java.util.List;

public class ExitCommand implements Command {

    @Override
    public String name() {
        return "exit";
    }

    @Override
    public List<String> aliases() {
        return List.of("quit");
    }

    @Override
    public String usage() {
        return "exit";
    }

    @Override
    public List<Help> help() {
        return List.of(new Help("exit | quit", "save and stop the program"));
    }

    @Override
    public boolean needsUser() {
        return false;
    }

    @Override
    public void execute(Session session, String[] args) {
        if (session.isLoggedIn()) {
            if (!session.trySaveAndLogOut()) return;
            session.println("Your wallet is saved");
        }
        session.requestExit();
    }
}
