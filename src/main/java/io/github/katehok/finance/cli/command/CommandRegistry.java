package io.github.katehok.finance.cli.command;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CommandRegistry {

    private final Map<String, Command> byName = new HashMap<>();
    private final List<Command> commands = new ArrayList<>();

    public CommandRegistry add(Command cmd) {
        List<String> names = new ArrayList<>();
        names.add(cmd.name());
        names.addAll(cmd.aliases());
        for (String name : names) {
            if (byName.containsKey(keyOf(name))) {
                throw new IllegalArgumentException("Command " + name + " is already registered");
            }
        }
        for (String name : names) {
            byName.put(keyOf(name), cmd);
        }
        commands.add(cmd);
        return this;
    }

    public Command tryFind(String name) {
        if (name == null) return null;
        return byName.get(keyOf(name));
    }

    public List<Command> getAll() {
        return List.copyOf(commands);
    }

    private static String keyOf(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
