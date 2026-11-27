package io.github.katehok.finance;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.katehok.finance.cli.Cli;
import io.github.katehok.finance.core.AlertService;
import io.github.katehok.finance.core.AuthService;
import io.github.katehok.finance.core.ReportService;
import io.github.katehok.finance.core.WalletService;
import io.github.katehok.finance.infra.CsvWalletFormat;
import io.github.katehok.finance.infra.FileUserRepository;
import io.github.katehok.finance.infra.FileWalletRepository;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScenarioTest {

    private static final String PASSWORD = "secret1";
    private static final String SPEC_REPORT = """
            Total income: 63,000.00
            Income by category:
              Зарплата  60,000.00
              Бонус      3,000.00
            Total expenses: 8,300.00
            Expenses by category:
              Еда                    800.00
              Развлечения          3,000.00
              Коммунальные услуги  3,000.00
              Такси                1,500.00
            Budgets:
              Category               Budget     Spent  Remaining
              Еда                  4,000.00    800.00   3,200.00
              Развлечения          3,000.00  3,000.00       0.00
              Коммунальные услуги  2,500.00  3,000.00    -500.00
            Balance: 54,700.00
            """;

    @TempDir
    Path dir;

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-25T10:00:00Z"), ZoneOffset.UTC);

    private Path data() {
        return dir.resolve("data");
    }

    private String run(String... lines) {
        FileUserRepository users = new FileUserRepository(data().resolve("users.tsv"));
        CsvWalletFormat format = new CsvWalletFormat();
        FileWalletRepository wallets = new FileWalletRepository(data().resolve("wallets"), format);
        AuthService auth = new AuthService(users, wallets);
        WalletService service = new WalletService(users, wallets, format, new AlertService(), clock);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        String script = String.join("\n", lines) + "\n";
        Cli cli = new Cli(auth, service, new ReportService(), null, new ByteArrayInputStream(script.getBytes(UTF_8)),
                new PrintStream(output, true, UTF_8), data());
        cli.run();
        return output.toString(UTF_8);
    }

    private static List<String> specLines() {
        return List.of(
                "category add Еда", "category add Развлечения", "category add \"Коммунальные услуги\"",
                "category add Такси", "category add Зарплата", "category add Бонус",
                "expense 300 Еда", "expense 500 Еда", "expense 3000 Развлечения",
                "expense 3000 \"Коммунальные услуги\"", "expense 1500 Такси",
                "income 20000 Зарплата", "income 40000 Зарплата", "income 3000 Бонус",
                "budget set Еда 4000", "budget set Развлечения 3000", "budget set \"Коммунальные услуги\" 2500");
    }

    private static String[] script(List<String> head, List<String> body, String... tail) {
        List<String> lines = new ArrayList<>(head);
        lines.addAll(body);
        lines.addAll(List.of(tail));
        return lines.toArray(new String[0]);
    }

    private static List<String> register(String login) {
        return List.of("register " + login, PASSWORD, PASSWORD);
    }

    private static List<String> login(String login) {
        return List.of("login " + login, PASSWORD);
    }

    private void registerAliceWithSpecData() {
        String txt = run(script(register("alice"), specLines(), "exit"));
        assertTrue(txt.contains("Your wallet is saved"), txt);
    }

    @Test
    void specExampleReport() {
        String txt = run(script(register("alice"), specLines(), "report", "exit"));
        assertTrue(txt.contains(SPEC_REPORT), txt);
    }

    @Test
    void specExampleAlertsComeRightAfterTheOperation() {
        String txt = run(script(register("alice"), specLines(), "exit"));
        assertTrue(txt.contains("Added expense 300.00 to Еда on 2026-11-25. Balance: -300.00\n"
                + "Alert: Expenses exceed income by 300.00\n"), txt);
        assertTrue(txt.contains("Alert: Развлечения budget is 100% used (3,000.00 of 3,000.00)"), txt);
        assertTrue(txt.contains("Alert: Коммунальные услуги budget is exceeded by 500.00 (3,000.00 of 2,500.00)"), txt);
    }

    @Test
    void walletSurvivesRestart() {
        registerAliceWithSpecData();
        assertTrue(Files.exists(data().resolve("wallets").resolve("alice.csv")));
        String txt = run(script(login("alice"), List.of("report"), "exit"));
        assertTrue(txt.contains("Logged in as alice"), txt);
        assertTrue(txt.contains(SPEC_REPORT), txt);
    }

    @Test
    void loginShowsCurrentAlerts() {
        registerAliceWithSpecData();
        String txt = run(script(login("alice"), List.of(), "exit"));
        assertTrue(txt.contains("Alert: Коммунальные услуги budget is exceeded by 500.00"), txt);
    }

    @Test
    void passwordIsCheckedAfterRestart() {
        registerAliceWithSpecData();
        String txt = run("login alice", "wrong-password", "balance", "exit");
        assertTrue(txt.contains("Oops: Wrong login or password"), txt);
        assertTrue(txt.contains("Log in first"), txt);
        String users = readFile(data().resolve("users.tsv"));
        assertFalse(users.contains(PASSWORD), users);
    }

    @Test
    void unsavedChangesAreLostWithoutExitButSavedOnLogout() {
        registerAliceWithSpecData();
        run(script(login("alice"), List.of("income 1000 Бонус", "logout"), "exit"));
        String txt = run(script(login("alice"), List.of("balance"), "exit"));
        assertTrue(txt.contains("55,700.00"), txt);
    }

    @Test
    void eofSavesWalletLikeExit() {
        registerAliceWithSpecData();
        run(script(login("alice"), List.of("income 1000 Бонус")));
        String txt = run(script(login("alice"), List.of("balance"), "exit"));
        assertTrue(txt.contains("55,700.00"), txt);
    }

    @Test
    void transferReachesAnotherUserAfterRestart() {
        registerAliceWithSpecData();
        run(script(register("bob"), List.of(), "exit"));
        String txt = run(script(login("alice"), List.of("transfer bob 700 for pizza"), "exit"));
        assertFalse(txt.contains("Oops"), txt);
        txt = run(script(login("bob"), List.of("balance", "history"), "exit"));
        assertTrue(txt.contains("700.00"), txt);
        assertTrue(txt.contains("from alice: for pizza"), txt);
        txt = run(script(login("alice"), List.of("balance"), "exit"));
        assertTrue(txt.contains("54,000.00"), txt);
    }

    @Test
    void exportedWalletImportsIntoAnotherUser() {
        registerAliceWithSpecData();
        Path file = dir.resolve("alice-export.csv");
        run(script(login("alice"), List.of("export " + file), "exit"));
        String txt = run(script(register("carol"), List.of("import " + file, "report"), "exit"));
        assertTrue(txt.contains(SPEC_REPORT), txt);
    }

    @Test
    void severalUsersKeepSeparateWallets() {
        registerAliceWithSpecData();
        run(script(register("bob"), List.of("category add Food", "income 100 Food"), "exit"));
        String txt = run(script(login("bob"), List.of("balance"), "exit"));
        assertTrue(txt.contains("Balance: 100.00"), txt);
        txt = run(script(login("alice"), List.of("balance"), "exit"));
        assertTrue(txt.contains("Balance: 54,700.00"), txt);
    }

    @Test
    void filesInsideDataDirAreRefused() {
        registerAliceWithSpecData();
        run(script(register("bob"), List.of("category add Food", "income 100 Food"), "exit"));
        Path users = data().resolve("users.tsv");
        String before = readFile(users);
        String txt = run(script(login("alice"),
                List.of("export " + users, "import " + data().resolve("wallets/bob.csv"),
                        "report --file " + data().resolve("../data/wallets/alice.csv")),
                "exit"));
        assertEquals(3, txt.split("is inside the data directory", -1).length - 1, txt);
        assertEquals(before, readFile(users));
        txt = run(script(login("bob"), List.of("balance"), "exit"));
        assertTrue(txt.contains("Balance: 100.00"), txt);
        txt = run(script(login("alice"), List.of("balance"), "exit"));
        assertTrue(txt.contains("Balance: 54,700.00"), txt);
    }

    @Test
    void symlinkToDataDirIsRefused() throws IOException {
        registerAliceWithSpecData();
        Path link = Files.createSymbolicLink(dir.resolve("link"), data());
        String before = readFile(data().resolve("users.tsv"));
        String txt = run(script(login("alice"), List.of("export " + link.resolve("users.tsv")), "exit"));
        assertTrue(txt.contains("is inside the data directory"), txt);
        assertEquals(before, readFile(data().resolve("users.tsv")));
    }

    private static String readFile(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
