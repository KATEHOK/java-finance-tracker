package io.github.katehok.finance.core;

import io.github.katehok.finance.core.FinanceException.Reason;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public class AuthService {

    public static final int MIN_PASSWORD_LENGTH = 6;
    public static final int MAX_PASSWORD_LENGTH = 64;

    private static final Pattern LOGIN_PATTERN = Pattern.compile("[a-z0-9_]{3,20}");

    private final UserRepository users;
    private final WalletRepository wallets;

    public AuthService(UserRepository users, WalletRepository wallets) {
        this.users = users;
        this.wallets = wallets;
    }

    public static String normalize(String login) {
        if (login == null) return "";
        return login.strip().toLowerCase(Locale.ROOT);
    }

    public static boolean isValidLogin(String login) {
        return LOGIN_PATTERN.matcher(normalize(login)).matches();
    }

    public synchronized String checkAvailable(String login) {
        String name = checkLogin(login);
        if (users.exists(name)) throw new FinanceException(Reason.LOGIN_TAKEN, "Login " + name + " is already taken");
        return name;
    }

    public synchronized User register(String login, String password) {
        checkPassword(password);
        String name = checkAvailable(login);
        users.save(name, Password.create(password));
        return new User(name, new Wallet());
    }

    public synchronized User login(String login, String password) {
        String name = normalize(login);
        Optional<Password> saved = users.findPassword(name);
        // Одна и та же ошибка для неизвестного логина и неверного пароля - при входе не говорю, что именно не так
        if (saved.isEmpty() || password == null || !saved.get().matches(password)) {
            throw new FinanceException(Reason.BAD_CREDENTIALS, "Wrong login or password");
        }
        return new User(name, wallets.load(name));
    }

    public synchronized void save(User user) {
        wallets.save(user.getLogin(), user.getWallet());
    }

    public static String checkLogin(String login) {
        String name = normalize(login);
        if (!LOGIN_PATTERN.matcher(name).matches()) {
            throw new FinanceException(Reason.INVALID_LOGIN,
                    "Login must be 3 to 20 latin letters, digits or _");
        }
        return name;
    }

    public static void checkPassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH || password.length() > MAX_PASSWORD_LENGTH) {
            throw new FinanceException(Reason.INVALID_PASSWORD,
                    "Password must be " + MIN_PASSWORD_LENGTH + " to " + MAX_PASSWORD_LENGTH + " characters long");
        }
        if (password.isBlank()) throw new FinanceException(Reason.INVALID_PASSWORD, "Password must not be blank");
    }
}
