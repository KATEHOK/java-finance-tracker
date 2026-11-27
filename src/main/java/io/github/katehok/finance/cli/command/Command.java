package io.github.katehok.finance.cli.command;

import io.github.katehok.finance.core.FinanceException.Reason;
import java.util.List;

public interface Command {

    String CREATE_CATEGORY_HINT = "Create it first: category add <name>";

    record Help(String usage, String txt) {
    }

    String name();

    String usage();

    List<Help> help();

    void execute(Session session, String[] args);

    default List<String> aliases() {
        return List.of();
    }

    default boolean needsUser() {
        return true;
    }

    default String hint(Reason reason) {
        return null;
    }

    default void printUsage(Session session) {
        session.println("Usage: " + usage());
    }
}
