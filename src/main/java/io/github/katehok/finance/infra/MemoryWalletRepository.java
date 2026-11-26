package io.github.katehok.finance.infra;

import io.github.katehok.finance.core.Wallet;
import io.github.katehok.finance.core.WalletRepository;
import java.util.HashMap;
import java.util.Map;

public class MemoryWalletRepository implements WalletRepository {

    private final Map<String, Wallet> wallets = new HashMap<>();

    @Override
    public Wallet load(String login) {
        Wallet wallet = wallets.get(login);
        return wallet == null ? new Wallet() : copy(wallet);
    }

    @Override
    public void save(String login, Wallet wallet) {
        wallets.put(login, copy(wallet));
    }

    private static Wallet copy(Wallet wallet) {
        Wallet copy = new Wallet();
        copy.merge(wallet);
        return copy;
    }
}
