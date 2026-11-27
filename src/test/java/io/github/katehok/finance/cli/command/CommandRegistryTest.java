package io.github.katehok.finance.cli.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class CommandRegistryTest {

    private static class FakeCommand implements Command {

        private final String name;
        private final List<String> aliases;

        FakeCommand(String name, String... aliases) {
            this.name = name;
            this.aliases = List.of(aliases);
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public List<String> aliases() {
            return aliases;
        }

        @Override
        public String usage() {
            return name;
        }

        @Override
        public List<Help> help() {
            return List.of(new Help(name, "does " + name));
        }

        @Override
        public void execute(Session session, String[] args) {
            session.println(name + " " + args.length);
        }
    }

    private final CommandRegistry registry = new CommandRegistry();

    @Test
    void findsByNameIgnoringCase() {
        Command help = new FakeCommand("help");
        registry.add(help);
        assertSame(help, registry.tryFind("help"));
        assertSame(help, registry.tryFind("HeLp"));
    }

    @Test
    void findsByAlias() {
        Command exit = new FakeCommand("exit", "quit", "q");
        registry.add(exit);
        assertSame(exit, registry.tryFind("QUIT"));
        assertSame(exit, registry.tryFind("q"));
    }

    @Test
    void unknownNameGivesNull() {
        registry.add(new FakeCommand("help"));
        assertNull(registry.tryFind("hlep"));
        assertNull(registry.tryFind(null));
    }

    @Test
    void rejectsTakenName() {
        registry.add(new FakeCommand("help", "h"));
        assertThrows(IllegalArgumentException.class, () -> registry.add(new FakeCommand("HELP")));
        assertThrows(IllegalArgumentException.class, () -> registry.add(new FakeCommand("history", "h")));
        assertNull(registry.tryFind("history"));
    }

    @Test
    void keepsOrderOfCommands() {
        Command first = new FakeCommand("register");
        Command second = new FakeCommand("login");
        Command third = new FakeCommand("exit", "quit");
        registry.add(first).add(second).add(third);
        assertEquals(List.of(first, second, third), registry.getAll());
    }

    @Test
    void getAllReturnsCopy() {
        registry.add(new FakeCommand("help"));
        List<Command> all = registry.getAll();
        assertThrows(UnsupportedOperationException.class, () -> all.add(new FakeCommand("exit")));
        assertEquals(1, registry.getAll().size());
    }

    @Test
    void helpPrintsLinesOfEveryCommandInOrder() {
        SessionFixture fixture = new SessionFixture();
        registry.add(new FakeCommand("register")).add(new FakeCommand("login"));
        HelpCommand help = new HelpCommand(registry);
        registry.add(help);
        String txt = fixture.run(help, "help");
        String[] lines = txt.split("\n");
        assertEquals("Commands:", lines[0]);
        assertEquals(String.format("  %-42s %s", "register", "does register"), lines[1]);
        assertEquals(String.format("  %-42s %s", "login", "does login"), lines[2]);
        assertEquals(String.format("  %-42s %s", "help | h", "show this help"), lines[3]);
        assertEquals(5, lines.length);
    }
}
