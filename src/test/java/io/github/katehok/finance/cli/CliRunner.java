package io.github.katehok.finance.cli;

import static java.nio.charset.StandardCharsets.UTF_8;

import io.github.katehok.finance.core.AuthService;
import io.github.katehok.finance.core.ReportService;
import io.github.katehok.finance.core.WalletService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

class CliRunner {

    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private final Cli cli;

    CliRunner(AuthService auth, WalletService wallets, String script) {
        PrintStream out = new PrintStream(output, true, UTF_8);
        ByteArrayInputStream in = new ByteArrayInputStream(script.getBytes(UTF_8));
        cli = new Cli(auth, wallets, new ReportService(), null, in, out);
    }

    Cli getCli() {
        return cli;
    }

    String execute(String input) {
        output.reset();
        cli.execute(input);
        return output.toString(UTF_8);
    }

    String saveOnShutdown() {
        output.reset();
        cli.saveOnShutdown();
        return output.toString(UTF_8);
    }

    String run() {
        output.reset();
        cli.run();
        return output.toString(UTF_8);
    }
}
