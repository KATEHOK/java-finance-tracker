package io.github.katehok.finance.core;

public class User {

    private final String login;
    private final Wallet wallet;

    public User(String login, Wallet wallet) {
        this.login = login;
        this.wallet = wallet;
    }

    public String getLogin() {
        return login;
    }

    public Wallet getWallet() {
        return wallet;
    }
}
