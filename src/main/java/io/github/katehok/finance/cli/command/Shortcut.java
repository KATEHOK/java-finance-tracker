package io.github.katehok.finance.cli.command;

import io.github.katehok.finance.core.FinanceException.Reason;
import java.util.List;

public class Shortcut implements Command {

    private final String name;
    private final Command target;
    private final String[] args;

    public Shortcut(String name, Command target, String... args) {
        this.name = name;
        this.target = target;
        this.args = new String[args.length + 1];
        this.args[0] = target.name();
        System.arraycopy(args, 0, this.args, 1, args.length);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String usage() {
        return name;
    }

    @Override
    public List<Help> help() {
        return List.of();
    }

    @Override
    public boolean needsUser() {
        return target.needsUser();
    }

    @Override
    public String hint(Reason reason) {
        return target.hint(reason);
    }

    @Override
    public void execute(Session session, String[] ignored) {
        target.execute(session, args.clone());
    }
}
