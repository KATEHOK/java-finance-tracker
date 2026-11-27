package io.github.katehok.finance.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.katehok.finance.core.FinanceException;
import io.github.katehok.finance.core.FinanceException.Reason;
import io.github.katehok.finance.core.Password;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FileUserRepositoryTest {

    private static final String SALT = Base64.getEncoder().encodeToString(new byte[] {1, 2, 3});
    private static final String HASH = Base64.getEncoder().encodeToString(new byte[] {4, 5, 6});

    @TempDir
    Path dir;

    @Test
    void emptyWhenFileMissing() {
        Path path = dir.resolve("users.tsv");
        FileUserRepository users = new FileUserRepository(path);
        assertFalse(users.exists("alice"));
        assertTrue(users.findPassword("alice").isEmpty());
        assertFalse(Files.exists(path));
    }

    @Test
    void savedUserSurvivesReload() {
        Path path = dir.resolve("data/users.tsv");
        FileUserRepository users = new FileUserRepository(path);
        users.save("alice", Password.create("secret1"));
        users.save("bob", Password.create("secret2"));

        FileUserRepository reloaded = new FileUserRepository(path);
        assertTrue(reloaded.exists("alice"));
        assertTrue(reloaded.exists("bob"));
        assertFalse(reloaded.exists("carol"));
        assertTrue(reloaded.findPassword("alice").orElseThrow().matches("secret1"));
        assertFalse(reloaded.findPassword("alice").orElseThrow().matches("secret2"));
        assertTrue(reloaded.findPassword("bob").orElseThrow().matches("secret2"));
    }

    @Test
    void savesLoginSaltAndHash() throws IOException {
        Path path = dir.resolve("users.tsv");
        Password password = Password.create("secret1");
        new FileUserRepository(path).save("alice", password);

        String salt = Base64.getEncoder().encodeToString(password.getSalt());
        String hash = Base64.getEncoder().encodeToString(password.getHash());
        assertEquals(List.of("alice\t" + salt + "\t" + hash), Files.readAllLines(path));
    }

    @Test
    void saveReplacesPassword() {
        Path path = dir.resolve("users.tsv");
        FileUserRepository users = new FileUserRepository(path);
        users.save("alice", Password.create("secret1"));
        users.save("alice", Password.create("secret2"));

        Password password = new FileUserRepository(path).findPassword("alice").orElseThrow();
        assertTrue(password.matches("secret2"));
        assertFalse(password.matches("secret1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"broken", "bob\tAQID", "bob\tAQID\tBAUG\textra", "Bob\tAQID\tBAUG", "../bob\tAQID\tBAUG",
        "bob\tnot base64!\tBAUG", "bob\t\tBAUG"})
    void failsOnMalformedLine(String line) throws IOException {
        Path path = dir.resolve("users.tsv");
        Files.writeString(path, "alice\t" + SALT + "\t" + HASH + "\n\n" + line + "\n");
        FinanceException e = assertThrows(FinanceException.class, () -> new FileUserRepository(path));
        assertEquals(Reason.BAD_FILE, e.getReason());
        assertEquals(path + " line 3: expected login, salt and hash separated by tabs", e.getMessage());
    }

    @Test
    void failsOnDuplicateLogin() throws IOException {
        Path path = dir.resolve("users.tsv");
        String line = "alice\t" + SALT + "\t" + HASH + "\n";
        Files.writeString(path, line + line);
        FinanceException e = assertThrows(FinanceException.class, () -> new FileUserRepository(path));
        assertEquals(path + " line 2: duplicate login alice", e.getMessage());
    }

    @Test
    void failedWriteRollsBack() throws IOException {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        Path data = dir.resolve("data");
        Path path = data.resolve("users.tsv");
        FileUserRepository users = new FileUserRepository(path);
        users.save("alice", Password.create("secret1"));

        Set<PosixFilePermission> perms = Files.getPosixFilePermissions(data);
        Files.setPosixFilePermissions(data, PosixFilePermissions.fromString("r-xr-xr-x"));
        try {
            assumeFalse(Files.isWritable(data));
            assertThrows(UncheckedIOException.class, () -> users.save("bob", Password.create("secret2")));
            assertThrows(UncheckedIOException.class, () -> users.save("alice", Password.create("secret3")));
            assertFalse(users.exists("bob"));
            assertTrue(users.findPassword("alice").orElseThrow().matches("secret1"));
        } finally {
            Files.setPosixFilePermissions(data, perms);
        }
        try (Stream<Path> files = Files.list(data)) {
            assertEquals(List.of(path), files.toList());
        }
        assertFalse(new FileUserRepository(path).exists("bob"));
    }
}
