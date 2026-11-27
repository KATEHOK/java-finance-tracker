package io.github.katehok.finance.cli.command;

import static java.nio.charset.StandardCharsets.UTF_8;

import io.github.katehok.finance.core.AlertService;
import io.github.katehok.finance.core.AuthService;
import io.github.katehok.finance.core.User;
import io.github.katehok.finance.core.Wallet;
import io.github.katehok.finance.core.WalletService;
import io.github.katehok.finance.infra.MemoryUserRepository;
import io.github.katehok.finance.infra.MemoryWalletRepository;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

class SessionFixture {

    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private final MemoryUserRepository users = new MemoryUserRepository();
    private final MemoryWalletRepository walletRepository = new MemoryWalletRepository();
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-25T10:00:00Z"), ZoneOffset.UTC);
    final AuthService auth = new AuthService(users, walletRepository);
    final WalletService wallets = new WalletService(users, walletRepository, null, new AlertService(), clock);
    final Session session;

    SessionFixture() {
        PrintStream out = new PrintStream(output, true, UTF_8);
        session = new Session(auth, null, new ByteArrayInputStream(new byte[0]), out, null);
    }

    Wallet logIn(String login) {
        Wallet wallet = new Wallet();
        session.logIn(new User(login, wallet));
        return wallet;
    }

    String run(Command cmd, String... args) {
        output.reset();
        cmd.execute(session, args);
        return output.toString(UTF_8);
    }
}
