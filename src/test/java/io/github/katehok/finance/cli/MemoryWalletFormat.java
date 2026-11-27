package io.github.katehok.finance.cli;

import io.github.katehok.finance.core.Wallet;
import io.github.katehok.finance.core.WalletFormat;
import java.io.UncheckedIOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

class MemoryWalletFormat implements WalletFormat {

    private final Map<Path, Wallet> files = new HashMap<>();

    @Override
    public void write(Wallet wallet, Path path) {
        files.put(path, copy(wallet));
    }

    @Override
    public Wallet read(Path path) {
        Wallet wallet = files.get(path);
        if (wallet == null) {
            throw new UncheckedIOException("Couldn't read " + path, new NoSuchFileException(path.toString()));
        }
        return copy(wallet);
    }

    boolean exists(Path path) {
        return files.containsKey(path);
    }

    private static Wallet copy(Wallet wallet) {
        Wallet copy = new Wallet();
        copy.merge(wallet);
        return copy;
    }
}
