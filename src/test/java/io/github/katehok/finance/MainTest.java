package io.github.katehok.finance;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MainTest {

    @TempDir
    Path dir;

    @Test
    void helpIsOk() {
        assertTrue(Main.run(new String[] {"--help"}));
        assertTrue(Main.run(new String[] {"-h"}));
    }

    @Test
    void tooManyArgsFail() {
        assertFalse(Main.run(new String[] {"a", "b"}));
    }

    @Test
    void brokenUsersFileFails() throws IOException {
        Files.writeString(dir.resolve("users.tsv"), "not a valid line\n");
        assertFalse(Main.run(new String[] {dir.toString()}));
    }
}
