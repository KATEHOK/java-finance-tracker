package io.github.katehok.finance.core;

public interface WalletRepository {

    Wallet load(String login);

    void save(String login, Wallet wallet);
}
