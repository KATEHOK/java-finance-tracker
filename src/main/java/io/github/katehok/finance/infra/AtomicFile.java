package io.github.katehok.finance.infra;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;

final class AtomicFile {

    private AtomicFile() {
    }

    // Пишу во временный файл рядом и подменяю, чтобы при падении не остаться с полузаписанным файлом
    static void write(Path path, String txt) {
        Path target = path.toAbsolutePath();
        Path tmp = null;
        try {
            Files.createDirectories(target.getParent());
            tmp = Files.createTempFile(target.getParent(), target.getFileName() + ".", ".tmp");
            Files.writeString(tmp, txt, UTF_8);
            move(tmp, target);
        } catch (IOException e) {
            tryDelete(tmp);
            throw new UncheckedIOException("Couldn't write " + path, e);
        }
    }

    private static void move(Path src, Path target) throws IOException {
        try {
            Files.move(src, target, REPLACE_EXISTING, ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(src, target, REPLACE_EXISTING);
        }
    }

    private static boolean tryDelete(Path path) {
        if (path == null) return false;
        try {
            return Files.deleteIfExists(path);
        } catch (IOException e) {
            return false;
        }
    }
}
