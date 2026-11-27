package io.github.katehok.finance.infra;

import static java.nio.charset.StandardCharsets.UTF_8;

import io.github.katehok.finance.core.AuthService;
import io.github.katehok.finance.core.FinanceException;
import io.github.katehok.finance.core.FinanceException.Reason;
import io.github.katehok.finance.core.Password;
import io.github.katehok.finance.core.UserRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class FileUserRepository implements UserRepository {

    private static final String SEPARATOR = "\t";
    private static final int FIELDS = 3;

    private final Path path;
    private final Map<String, Password> passwords;

    public FileUserRepository(Path path) {
        this.path = path;
        this.passwords = load();
    }

    @Override
    public boolean exists(String login) {
        return passwords.containsKey(login);
    }

    @Override
    public Optional<Password> findPassword(String login) {
        return Optional.ofNullable(passwords.get(login));
    }

    @Override
    public void save(String login, Password password) {
        Password old = passwords.put(login, password);
        try {
            write();
        } catch (UncheckedIOException e) {
            if (old == null) {
                passwords.remove(login);
            } else {
                passwords.put(login, old);
            }
            throw e;
        }
    }

    private Map<String, Password> load() {
        Map<String, Password> loaded = new LinkedHashMap<>();
        if (!Files.exists(path)) return loaded;
        List<String> lines;
        try {
            lines = Files.readAllLines(path, UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Couldn't read " + path, e);
        }
        for (int i = 0; i < lines.size(); ++i) {
            String line = lines.get(i);
            if (line.isBlank()) continue;
            String[] parts = line.split(SEPARATOR, -1);
            Password password = tryParse(parts);
            if (password == null) throw bad(i, "expected login, salt and hash separated by tabs");
            if (loaded.containsKey(parts[0])) throw bad(i, "duplicate login " + parts[0]);
            loaded.put(parts[0], password);
        }
        return loaded;
    }

    private Password tryParse(String[] parts) {
        if (parts.length != FIELDS) return null;
        String login = parts[0];
        if (!AuthService.isValidLogin(login) || !login.equals(AuthService.normalize(login))) return null;
        try {
            byte[] salt = Base64.getDecoder().decode(parts[1]);
            byte[] hash = Base64.getDecoder().decode(parts[2]);
            if (salt.length == 0 || hash.length == 0) return null;
            return Password.of(salt, hash);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private FinanceException bad(int index, String message) {
        return new FinanceException(Reason.BAD_FILE, path + " line " + (index + 1) + ": " + message);
    }

    private void write() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Password> entry : passwords.entrySet()) {
            Password password = entry.getValue();
            sb.append(String.join(SEPARATOR,
                    entry.getKey(),
                    Base64.getEncoder().encodeToString(password.getSalt()),
                    Base64.getEncoder().encodeToString(password.getHash())));
            sb.append('\n');
        }
        AtomicFile.write(path, sb.toString());
    }
}
