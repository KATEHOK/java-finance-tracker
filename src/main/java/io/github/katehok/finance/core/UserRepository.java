package io.github.katehok.finance.core;

import java.util.Optional;

public interface UserRepository {

    boolean exists(String login);

    Optional<Password> findPassword(String login);

    void save(String login, Password password);
}
