package io.github.katehok.finance.cli.command;

import java.util.List;

public class LogoutCommand implements Command {

    @Override
    public String name() {
        return "logout";
    }

    @Override
    public String usage() {
        return "logout";
    }

    @Override
    public List<Help> help() {
        return List.of(new Help(usage(), "save your wallet and log out"));
    }

    @Override
    public void execute(Session session, String[] args) {
        if (session.trySaveAndLogOut()) session.println("Logged out. Your wallet is saved");
    }
}
