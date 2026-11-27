package io.github.katehok.finance.infra;

import io.github.katehok.finance.core.AuthService;
import io.github.katehok.finance.core.FinanceException;
import io.github.katehok.finance.core.FinanceException.Reason;
import io.github.katehok.finance.core.Wallet;
import io.github.katehok.finance.core.WalletFormat;
import io.github.katehok.finance.core.WalletRepository;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileWalletRepository implements WalletRepository {

    private static final String EXTENSION = ".csv";

    private final Path dir;
    private final WalletFormat format;

    public FileWalletRepository(Path dir, WalletFormat format) {
        this.dir = dir;
        this.format = format;
    }

    @Override
    public Wallet load(String login) {
        Path path = pathOf(login);
        if (!Files.exists(path)) return new Wallet();
        try {
            return format.read(path);
        } catch (FinanceException e) {
            throw new FinanceException(Reason.BAD_FILE, path + ": " + e.getMessage());
        }
    }

    @Override
    public void save(String login, Wallet wallet) {
        format.write(wallet, pathOf(login));
    }

    private Path pathOf(String login) {
        if (!AuthService.isValidLogin(login) || !login.equals(AuthService.normalize(login))) {
            throw new IllegalArgumentException("Bad login " + login);
        }
        return dir.resolve(login + EXTENSION);
    }
}
