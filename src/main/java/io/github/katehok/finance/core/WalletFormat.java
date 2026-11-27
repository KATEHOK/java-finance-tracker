package io.github.katehok.finance.core;

import java.nio.file.Path;

public interface WalletFormat {

    void write(Wallet wallet, Path path);

    Wallet read(Path path);
}
