package io.github.katehok.finance.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.katehok.finance.core.AlertService;
import io.github.katehok.finance.core.AuthService;
import io.github.katehok.finance.core.Money;
import io.github.katehok.finance.core.User;
import io.github.katehok.finance.core.Wallet;
import io.github.katehok.finance.core.WalletRepository;
import io.github.katehok.finance.core.WalletService;
import io.github.katehok.finance.infra.MemoryUserRepository;
import io.github.katehok.finance.infra.MemoryWalletRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CliTest {

    private static final String PASSWORD = "secret1";
    private static final String LOG_IN_FIRST = "Log in first: login <login> or register <login>\n";
    private static final String BAD_AMOUNT = "Oops: Amount must be a positive number like 1500 or 99.90\n";
    private static final String BAD_DATE = "Oops: Date must look like 2026-11-25\n";

    private final MemoryUserRepository users = new MemoryUserRepository();
    private final MemoryWalletFormat format = new MemoryWalletFormat();
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-25T10:00:00Z"), ZoneOffset.UTC);
    private AuthService auth;
    private WalletService wallets;
    private CliRunner runner;

    @BeforeEach
    void setUp() {
        build(new MemoryWalletRepository());
        start();
    }

    private void build(WalletRepository walletRepository) {
        auth = new AuthService(users, walletRepository);
        wallets = new WalletService(users, walletRepository, format, new AlertService(), clock);
    }

    private void start(String... lines) {
        String script = lines.length == 0 ? "" : String.join("\n", lines) + "\n";
        runner = new CliRunner(auth, wallets, script);
    }

    private String execute(String input) {
        return runner.execute(input);
    }

    private void registerAlice(String... more) {
        String[] lines = new String[more.length + 2];
        lines[0] = PASSWORD;
        lines[1] = PASSWORD;
        System.arraycopy(more, 0, lines, 2, more.length);
        start(lines);
        String txt = execute("register alice");
        assertTrue(txt.contains("Registered and logged in as alice"), txt);
    }

    private void addSamples() {
        execute("category add Salary");
        execute("category add Bonus");
        execute("category add Food");
        execute("category add Taxi");
        execute("income 60000 Salary 2026-11-01");
        execute("income 3000 Bonus 2026-11-10");
        execute("expense 800 Food 2026-11-12");
        execute("expense 500 Taxi 2026-10-20");
        execute("budget set Food 4000");
    }

    @Test
    void helpListsAllCommands() {
        String txt = execute("help");
        List<String> cmds = List.of("register", "login", "logout", "income", "expense", "category add",
                "category rename", "category delete", "categories", "budget set", "budget delete", "budgets",
                "balance", "report", "--from", "--file", "history", "alerts", "transfer", "export", "import",
                "help | h", "exit | quit");
        for (String cmd : cmds) {
            assertTrue(txt.contains(cmd), "no " + cmd + " in help");
        }
        assertTrue(txt.contains("e.g. expense 300 Food 2026-11-25 lunch"), txt);
        assertEquals(txt, execute("H"));
    }

    @Test
    void guestCannotUseWalletCommands() {
        for (String input : List.of("balance", "expense 300 Food", "category list", "report", "logout", "history")) {
            assertEquals(LOG_IN_FIRST, execute(input), input);
        }
    }

    @Test
    void unknownCommand() {
        assertEquals("Unknown command. Type help to see commands\n", execute("fly away"));
        assertTrue(runner.getCli().execute("   "));
    }

    @Test
    void registerAsksPasswordTwiceAndLogsIn() {
        start(PASSWORD, PASSWORD);
        String txt = execute("register Alice");
        assertEquals("finance $ Password: finance $ Repeat password: Registered and logged in as alice\n"
                + "Start with a category: category add Food\n", txt);
        assertTrue(users.exists("alice"));
        assertTrue(execute("balance").contains("Balance: 0.00"));
    }

    @Test
    void registerWithDifferentPasswordsRefused() {
        start(PASSWORD, "secret2");
        String txt = execute("register alice");
        assertTrue(txt.endsWith("Oops: Passwords don't match. Try again: register alice\n"), txt);
        assertFalse(users.exists("alice"));
        assertEquals(LOG_IN_FIRST, execute("balance"));
    }

    @Test
    void registerWithBadLoginDoesNotAskPassword() {
        String txt = execute("register a!");
        String expected = "Oops: Login must be 3 to 20 latin letters, digits or _. "
                + "A login looks like alice or bob_92\n";
        assertEquals(expected, txt);
        assertEquals("Usage: register <login>\n", execute("register"));
    }

    @Test
    void registerWithShortPasswordRefused() {
        start("12345", "12345");
        String txt = execute("register alice");
        assertTrue(txt.contains("Oops: Password must be 6 to 64 characters long. Choose another password"), txt);
    }

    @Test
    void registerTakenLoginRefused() {
        auth.register("alice", PASSWORD);
        start(PASSWORD, PASSWORD);
        String txt = execute("register alice");
        assertTrue(txt.contains("Oops: Login alice is already taken. Choose another login or log in"), txt);
    }

    @Test
    void loginPrintsBalanceAndAlerts() {
        registerAlice(PASSWORD);
        execute("category add Food");
        execute("income 1000 Food");
        execute("budget set Food 1000");
        execute("expense 900 Food");
        assertEquals("Logged out. Your wallet is saved\n", execute("logout"));
        String txt = execute("login ALICE");
        assertEquals("finance $ Password: Logged in as alice\nBalance: 100.00\n"
                + "Alert: Food budget is 90% used (900.00 of 1,000.00)\n", txt);
    }

    @Test
    void loginWithWrongPasswordRefused() {
        auth.register("alice", PASSWORD);
        start("wrong-password");
        String txt = execute("login alice");
        assertTrue(txt.endsWith("Oops: Wrong login or password. "
                + "Check the login and password or register: register <login>\n"), txt);
        assertEquals(LOG_IN_FIRST, execute("balance"));
    }

    @Test
    void loginAndRegisterWhileLoggedInRefused() {
        registerAlice();
        String expected = "You are already logged in as alice. Type logout first\n";
        assertEquals(expected, execute("login bob"));
        assertEquals(expected, execute("register bob"));
    }

    @Test
    void logoutReturnsToGuest() {
        registerAlice();
        execute("logout");
        assertEquals(LOG_IN_FIRST, execute("balance"));
    }

    @Test
    void runShowsLoginInPromptAndSavesOnExit() {
        String script = "register alice\n" + PASSWORD + "\n" + PASSWORD + "\ncategory add Food\nincome 500 Food\n"
                + "exit\nbalance\n";
        runner = new CliRunner(auth, wallets, script);
        String txt = runner.run();
        assertTrue(txt.startsWith("Hi! This is a console personal finance tracker. Type help to see commands\n"
                + "finance $ finance $ Password: "), txt);
        assertTrue(txt.contains("alice@finance $ Added category Food"), txt);
        assertTrue(txt.endsWith("alice@finance $ Your wallet is saved\nBye\n"), txt);
        assertFalse(txt.contains("Total income"), txt);

        start(PASSWORD);
        assertTrue(execute("login alice").contains("Balance: 500.00"));
    }

    @Test
    void runSavesOnEndOfInput() {
        String script = "register alice\n" + PASSWORD + "\n" + PASSWORD + "\ncategory add Food\n\nQUIT\n";
        runner = new CliRunner(auth, wallets, script);
        assertTrue(runner.run().endsWith("Your wallet is saved\nBye\n"));

        script = "login alice\n" + PASSWORD + "\ncategories\n";
        runner = new CliRunner(auth, wallets, script);
        String txt = runner.run();
        assertTrue(txt.contains("Food"), txt);
        assertTrue(txt.endsWith("alice@finance $ \nYour wallet is saved\nBye\n"), txt);
    }

    @Test
    void exitReturnsFalse() {
        Cli cli = runner.getCli();
        assertFalse(cli.execute("exit"));
        assertFalse(cli.execute("Quit"));
        assertTrue(cli.execute("help"));
    }

    @Test
    void quotedCategoryName() {
        registerAlice();
        assertEquals("Added category Utility bills\n", execute("category add \"Utility bills\""));
        String txt = execute("expense 3000 \"utility BILLS\" \"for october\"");
        assertEquals("Added expense 3,000.00 to Utility bills on 2026-11-25. Balance: -3,000.00\n"
                + "Alert: Expenses exceed income by 3,000.00\n", txt);
        assertTrue(execute("history").contains("Utility bills  3,000.00  for october"));
        assertEquals("Oops: Unclosed quote\n", execute("category add \"Food"));
    }

    @Test
    void incomeWithDateAndNote() {
        registerAlice();
        execute("category add Salary");
        String txt = execute("INCOME 60000,50 salary 2026-11-01 for   november");
        assertEquals("Added income 60,000.50 to Salary on 2026-11-01. Balance: 60,000.50\n", txt);
        assertTrue(execute("history").contains("2026-11-01  income  Salary    60,000.50  for november"));
    }

    @Test
    void expenseWithoutDateUsesToday() {
        registerAlice();
        execute("category add Food");
        execute("income 1000 Food");
        String txt = execute("expense 300 Food lunch with friends");
        assertEquals("Added expense 300.00 to Food on 2026-11-25. Balance: 700.00\n", txt);
        assertTrue(execute("history 1").contains("2026-11-25  expense  Food      300.00  lunch with friends"));
    }

    @Test
    void expenseToUnknownCategoryRefused() {
        registerAlice();
        String txt = execute("expense 300 Taxi");
        assertEquals("Oops: Unknown category Taxi. Create it first: category add <name>\n", txt);
        assertTrue(execute("history").contains("No operations yet"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0", "-5", "1.234", "1e3", "99999999999999"})
    void badAmountRefused(String amount) {
        registerAlice();
        execute("category add Food");
        assertEquals(BAD_AMOUNT, execute("expense " + amount + " Food"));
        assertEquals(BAD_AMOUNT, execute("budget set Food " + amount));
        assertEquals(BAD_AMOUNT, execute("transfer bob " + amount));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-13-01", "2026-02-30", "2026-1-5"})
    void badDateRefused(String date) {
        registerAlice();
        execute("category add Food");
        assertEquals(BAD_DATE, execute("expense 300 Food " + date + " lunch"));
        assertEquals(BAD_DATE, execute("report --from " + date));
    }

    @Test
    void wrongArgumentsPrintUsage() {
        registerAlice();
        assertEquals("Usage: expense <amount> <category> [yyyy-mm-dd] [note]\n", execute("expense 300"));
        assertEquals("Usage: income <amount> <category> [yyyy-mm-dd] [note]\n", execute("income"));
        assertTrue(execute("category add").startsWith("Usage: category add <name>"));
        assertTrue(execute("category add Utility bills").startsWith("Usage: category add <name>"));
        assertTrue(execute("budget").startsWith("Usage: budget set <category> <amount>"));
        assertEquals("Usage: transfer <login> <amount> [note]\n", execute("transfer bob"));
        assertEquals("Usage: export <file>\n", execute("export"));
        assertEquals("Usage: import <file>\n", execute("import a b"));
        assertEquals("Usage: history [n]\n", execute("history 1 2"));
    }

    @Test
    void categoriesAddRenameDelete() {
        registerAlice();
        assertEquals("Added category Food\n", execute("category add Food"));
        assertEquals("Oops: Category Food already exists. See your categories: category list\n",
                execute("category add FOOD"));
        assertEquals("Renamed category Food to Groceries\n", execute("category rename food Groceries"));
        assertEquals("Deleted category Groceries\n", execute("category delete groceries"));
        assertEquals("You have no categories yet. Add one: category add <name>\n", execute("categories"));
        assertEquals("Oops: Unknown category Food. See your categories: category list\n",
                execute("category delete Food"));
    }

    @Test
    void categoryInUseCannotBeDeleted() {
        registerAlice();
        execute("category add Food");
        execute("income 100 Food");
        assertEquals("Oops: Category Food has 1 operation(s). Rename it instead: category rename <old> <new>\n",
                execute("category delete Food"));
    }

    @Test
    void categoryListShowsTotalsAndBudgets() {
        registerAlice();
        addSamples();
        String expected = """
                Category     Income  Expenses    Budget
                Salary    60,000.00      0.00         -
                Bonus      3,000.00      0.00         -
                Food           0.00    800.00  4,000.00
                Taxi           0.00    500.00         -
                """;
        assertEquals(expected, execute("category list"));
        assertEquals(expected, execute("categories"));
    }

    @Test
    void budgetsSetDeleteList() {
        registerAlice();
        assertEquals("You have no budgets yet. Set one: budget set <category> <amount>\n", execute("budgets"));
        assertEquals("Oops: Unknown category Food. Create it first: category add <name>\n",
                execute("budget set Food 4000"));
        execute("category add Food");
        execute("income 10000 Food");
        execute("expense 800 food");
        assertEquals("Budget for Food is set to 4,000.00. Spent: 800.00\n", execute("budget set food 4000"));
        String expected = """
                Category    Budget   Spent  Remaining
                Food      4,000.00  800.00   3,200.00
                """;
        assertEquals(expected, execute("budget list"));
        assertEquals(expected, execute("budgets"));
        assertEquals("Removed budget for Food\n", execute("budget delete FOOD"));
        assertEquals("Oops: Category Food has no budget. See your budgets: budget list\n",
                execute("budget delete Food"));
    }

    @Test
    void budgetAlerts() {
        registerAlice();
        execute("category add Food");
        execute("income 10000 Food");
        execute("budget set Food 4000");
        assertEquals("No alerts. All good\n", execute("alerts"));
        String txt = execute("expense 3400 Food");
        assertTrue(txt.endsWith("\nAlert: Food budget is 85% used (3,400.00 of 4,000.00)\n"), txt);
        assertFalse(execute("expense 1 Food").contains("Alert"));
        txt = execute("expense 1099 Food");
        assertTrue(txt.endsWith("\nAlert: Food budget is exceeded by 500.00 (4,500.00 of 4,000.00)\n"), txt);
        assertEquals("Alert: Food budget is exceeded by 500.00 (4,500.00 of 4,000.00)\n", execute("alerts"));
        txt = execute("budget set Food 5000");
        assertTrue(txt.endsWith("\nAlert: Food budget is 90% used (4,500.00 of 5,000.00)\n"), txt);
    }

    @Test
    void balanceAlerts() {
        registerAlice();
        execute("category add Food");
        execute("income 1000 Food");
        assertTrue(execute("expense 1000 Food").endsWith("\nAlert: Your balance is zero\n"));
        assertTrue(execute("expense 500 Food").endsWith("\nAlert: Expenses exceed income by 500.00\n"));
        assertEquals("Total income: 1,000.00\nTotal expenses: 1,500.00\nBalance: -500.00\n", execute("balance"));
    }

    @Test
    void fullReport() {
        registerAlice();
        addSamples();
        String expected = """
                Total income: 63,000.00
                Income by category:
                  Salary  60,000.00
                  Bonus    3,000.00
                Total expenses: 1,300.00
                Expenses by category:
                  Food  800.00
                  Taxi  500.00
                Budgets:
                  Category    Budget   Spent  Remaining
                  Food      4,000.00  800.00   3,200.00
                Balance: 61,700.00
                """;
        assertEquals(expected, execute("report"));
    }

    @Test
    void filteredReport() {
        registerAlice();
        addSamples();
        String expected = """
                Report for Food, Taxi from 2026-11-01 to 2026-11-30
                Total income: 0.00
                Income by category:
                  none
                Total expenses: 800.00
                Expenses by category:
                  Food  800.00
                Budgets (spent for all time):
                  Category    Budget   Spent  Remaining
                  Food      4,000.00  800.00   3,200.00
                Balance: -800.00
                """;
        assertEquals(expected, execute("report food TAXI --from 2026-11-01 --to 2026-11-30"));
        assertTrue(execute("report --to 2026-10-31").startsWith("Report for all categories to 2026-10-31\n"));
    }

    @Test
    void categoryFilterKeepsPlainBudgetsTitle() {
        registerAlice();
        addSamples();
        String txt = execute("report Food");
        assertTrue(txt.contains("\nBudgets:\n"), txt);
        assertFalse(txt.contains("all time"), txt);
    }

    @Test
    void reportForEmptyPeriodStillShowsBudgets() {
        registerAlice();
        addSamples();
        String expected = """
                Report for all categories from 2026-12-01
                No operations for the chosen categories/period
                Budgets (spent for all time):
                  Category    Budget   Spent  Remaining
                  Food      4,000.00  800.00   3,200.00
                """;
        assertEquals(expected, execute("report --from 2026-12-01"));
    }

    @Test
    void reportWithoutOperations() {
        registerAlice();
        assertEquals("No operations yet\nBudgets:\n  none\n", execute("report"));
    }

    @Test
    void reportWithUnknownCategoryOrBadOptions() {
        registerAlice();
        addSamples();
        assertEquals("Oops: Unknown categories Cafe, Bar. See your categories: category list\n",
                execute("report Food Cafe Bar"));
        String usage = "Usage: report [category...] [--from yyyy-mm-dd] [--to yyyy-mm-dd] [--file <path>]\n";
        assertEquals(usage, execute("report --from"));
        assertEquals(usage, execute("report --color red"));
        String txt = execute("report --from 2026-11-30 --to 2026-11-01");
        assertEquals("Oops: Start date 2026-11-30 is after end date 2026-11-01. Put the earlier date after --from\n",
                txt);
    }

    @Test
    void reportToFile(@TempDir Path dir) throws IOException {
        registerAlice();
        addSamples();
        Path file = dir.resolve("my report.txt");
        String txt = execute("report Food --file \"" + file + "\"");
        assertEquals("Report saved to " + file + "\n", txt);
        assertEquals(execute("report Food"), Files.readString(file, UTF_8));

        Path missing = dir.resolve("no/such/dir/report.txt");
        txt = execute("report --file " + missing);
        assertEquals("Couldn't write " + missing + " (No such file or directory)\n", txt);
    }

    @Test
    void historyShowsLastOperations() {
        registerAlice();
        assertEquals("No operations yet. Add one: income <amount> <category>\n", execute("history"));
        addSamples();
        String expected = """
                Date        Type     Category     Amount  Note
                2026-11-01  income   Salary    60,000.00
                2026-11-10  income   Bonus      3,000.00
                2026-11-12  expense  Food         800.00
                2026-10-20  expense  Taxi         500.00
                """;
        assertEquals(expected, execute("history"));
        assertEquals(expected, execute("history 100"));
        String last = """
                Date        Type     Category  Amount  Note
                2026-11-12  expense  Food      800.00
                2026-10-20  expense  Taxi      500.00
                """;
        assertEquals(last, execute("history 2"));
        String badNum = "Oops: Number of operations must be a whole number like 10\n";
        assertEquals(badNum, execute("history 0"));
        assertEquals(badNum, execute("history all"));
    }

    @Test
    void transferToAnotherUser() {
        auth.register("bob", PASSWORD);
        registerAlice(PASSWORD);
        execute("category add Salary");
        execute("income 1000 Salary");
        String txt = execute("transfer BOB 300 for pizza");
        assertEquals("Transferred 300.00 to bob. Balance: 700.00\n", txt);
        assertTrue(execute("history 1").contains("Transfers  300.00  to bob: for pizza"));
        assertEquals("Oops: Not enough money, balance is 700.00. Check your balance: balance\n",
                execute("transfer bob 701"));
        assertEquals("Oops: Unknown user carol. Check the login of the recipient\n", execute("transfer carol 1"));
        assertEquals("Oops: You can't transfer to yourself. Choose another user\n", execute("transfer alice 1"));
        execute("logout");
        txt = execute("login bob");
        assertTrue(txt.contains("Balance: 300.00"), txt);
        assertTrue(execute("history").contains("income  Transfers  300.00  from alice: for pizza"));
    }

    @Test
    void transferAlertsSender() {
        auth.register("bob", PASSWORD);
        registerAlice();
        execute("category add Salary");
        execute("income 300 Salary");
        assertEquals("Transferred 300.00 to bob. Balance: 0.00\nAlert: Your balance is zero\n",
                execute("transfer bob 300"));
    }

    @Test
    void exportAndImport() {
        registerAlice(PASSWORD, PASSWORD);
        addSamples();
        assertEquals("Exported 4 operations to wallet.csv\n", execute("export wallet.csv"));
        assertTrue(format.exists(Path.of("wallet.csv")));
        execute("logout");

        execute("register bob");
        execute("category add food");
        execute("budget set Food 500");
        String txt = execute("import wallet.csv");
        assertEquals("Imported 4 operations, 3 new categories and 0 new budgets from wallet.csv\n"
                + "Alert: food budget is exceeded by 300.00 (800.00 of 500.00)\n", txt);
        assertTrue(execute("balance").contains("Balance: 61,700.00"));
        assertEquals("Couldn't read nope.csv (No such file or directory)\n", execute("import nope.csv"));
    }

    @Test
    void shutdownSavesWalletOfLoggedInUser() {
        registerAlice();
        execute("category add Food");
        execute("income 500 Food");
        assertEquals("\nYour wallet is saved\n", runner.saveOnShutdown());

        start(PASSWORD);
        assertTrue(execute("login alice").contains("Balance: 500.00"));
        execute("logout");
        assertEquals("", runner.saveOnShutdown());
    }

    @Test
    void shutdownWaitsForRunningCommand() throws InterruptedException {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean slow = new AtomicBoolean();
        MemoryWalletRepository repository = new MemoryWalletRepository() {
            @Override
            public void save(String login, Wallet wallet) {
                if (slow.get() && login.equals("bob")) {
                    entered.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                super.save(login, wallet);
            }
        };
        build(repository);
        start(PASSWORD, PASSWORD);
        execute("register bob");
        execute("logout");
        registerAlice();
        execute("category add Salary");
        execute("income 1000 Salary");
        slow.set(true);
        Thread transfer = new Thread(() -> runner.execute("transfer bob 300"));
        transfer.start();
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        Thread shutdown = new Thread(runner.getCli()::saveOnShutdown);
        shutdown.start();
        shutdown.join(200);
        assertTrue(shutdown.isAlive());
        release.countDown();
        transfer.join(5000);
        shutdown.join(5000);
        assertEquals(Money.ofRubles(700), repository.load("alice").getBalance());
        assertEquals(Money.ofRubles(300), repository.load("bob").getBalance());
    }

    @Test
    void exitAfterSaveDoesNotSaveAgainOnShutdown() {
        registerAlice();
        assertEquals("Your wallet is saved\n", execute("exit"));
        assertEquals("", runner.saveOnShutdown());
    }

    @Test
    void shortPasswordFailsBeforeRepeat() {
        start("123", PASSWORD, PASSWORD);
        String txt = execute("register alice");
        assertTrue(txt.startsWith("finance $ Password: Oops: Password must be 6 to 64 characters long"), txt);
        assertFalse(txt.contains("Repeat password"), txt);
    }

    @Test
    void emptyNameGetsItsOwnHint() {
        registerAlice();
        assertEquals("Oops: Category name is empty. Type a name, put it in quotes if it has spaces: \"Utility bills\"\n",
                execute("category add \"  \""));
    }

    @Test
    void reportListsRepeatedCategoryOnce() {
        registerAlice();
        addSamples();
        assertTrue(execute("report Food food").startsWith("Report for Food\n"));
    }

    @Test
    void failedSaveKeepsUserLoggedIn() {
        build(new MemoryWalletRepository() {
            @Override
            public void save(String login, Wallet wallet) {
                IOException cause = new AccessDeniedException("data/" + login + ".csv.tmp");
                throw new UncheckedIOException("Couldn't write data/" + login + ".csv", cause);
            }
        });
        String script = "register alice\n" + PASSWORD + "\n" + PASSWORD + "\nlogout\nbalance\nexit\n";
        runner = new CliRunner(auth, wallets, script);
        String txt = runner.run();
        String error = "Couldn't save your wallet: Couldn't write data/alice.csv (Permission denied)\n";
        String stillIn = "You are still logged in. Try again or keep a copy: export <file>\n";
        assertTrue(txt.contains(error + stillIn + "alice@finance $ Total income"), txt);
        assertTrue(txt.endsWith("alice@finance $ " + error + stillIn + "alice@finance $ \n" + error + "Bye\n"), txt);
    }

    @Test
    void unexpectedErrorDoesNotStopCli() {
        wallets = new WalletService(users, new MemoryWalletRepository(), format, new AlertService(), clock) {
            @Override
            public int exportTo(User user, Path path) {
                throw new IllegalStateException("boom");
            }
        };
        registerAlice();
        assertEquals("Unexpected error: boom\n", execute("export wallet.csv"));
        assertTrue(runner.getCli().execute("balance"));
    }
}
