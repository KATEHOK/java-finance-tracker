package io.github.katehok.finance.infra;

import io.github.katehok.finance.core.Password;
import io.github.katehok.finance.core.UserRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class MemoryUserRepository implements UserRepository {

    private final Map<String, Password> passwords = new HashMap<>();

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
        passwords.put(login, password);
    }
}
