package io.github.katehok.finance.cli.command;

import java.util.List;

public class HelpCommand implements Command {

    private final CommandRegistry commands;

    public HelpCommand(CommandRegistry commands) {
        this.commands = commands;
    }

    @Override
    public String name() {
        return "help";
    }

    @Override
    public List<String> aliases() {
        return List.of("h");
    }

    @Override
    public String usage() {
        return "help";
    }

    @Override
    public List<Help> help() {
        return List.of(new Help("help | h", "show this help"));
    }

    @Override
    public boolean needsUser() {
        return false;
    }

    @Override
    public void execute(Session session, String[] args) {
        session.println("Commands:");
        for (Command cmd : commands.getAll()) {
            for (Help line : cmd.help()) {
                session.println(String.format("  %-42s %s", line.usage(), line.txt()));
            }
        }
        session.println("Dates look like " + Args.EXAMPLE_DATE + ", amounts like 1500 or 99.90. "
                + "Put names with spaces in quotes: \"Utility bills\"");
    }
}
