package io.github.katehok.finance.cli.command;

import io.github.katehok.finance.cli.Messages;
import io.github.katehok.finance.cli.ReportFormatter;
import io.github.katehok.finance.core.Category;
import io.github.katehok.finance.core.ReportService;
import io.github.katehok.finance.core.ReportService.Filter;
import io.github.katehok.finance.core.Wallet;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ReportCommand implements Command {

    private final ReportService reports;

    public ReportCommand(ReportService reports) {
        this.reports = reports;
    }

    @Override
    public String name() {
        return "report";
    }

    @Override
    public String usage() {
        return "report [category...] [--from yyyy-mm-dd] [--to yyyy-mm-dd] [--file <path>]";
    }

    @Override
    public List<Help> help() {
        return List.of(
                new Help("report [category...]", "show statistics, e.g. report Food Taxi"),
                new Help("report ... --from <date> --to <date>",
                        "for a period, e.g. report --from 2026-11-01 --to 2026-11-30"),
                new Help("report ... --file <path>", "save the report to a file, e.g. report --file report.txt"));
    }

    @Override
    public void execute(Session session, String[] args) {
        Wallet wallet = session.getWallet();
        List<String> categories = new ArrayList<>();
        LocalDate from = null;
        LocalDate to = null;
        String file = null;
        for (int i = 1; i < args.length; ++i) {
            if (!args[i].startsWith("--")) {
                String name = wallet.findCategory(args[i]).map(Category::getName).orElse(args[i]);
                if (!categories.contains(name)) categories.add(name);
                continue;
            }
            if (i + 1 == args.length) {
                printUsage(session);
                return;
            }
            String option = args[i].toLowerCase(Locale.ROOT);
            String value = args[++i];
            switch (option) {
                case "--from":
                    from = Args.tryDate(value);
                    if (from == null) {
                        session.println(Args.BAD_DATE);
                        return;
                    }
                    break;
                case "--to":
                    to = Args.tryDate(value);
                    if (to == null) {
                        session.println(Args.BAD_DATE);
                        return;
                    }
                    break;
                case "--file":
                    file = value;
                    break;
                default:
                    printUsage(session);
                    return;
            }
        }
        if (file != null && !session.tryUseFile(Path.of(file))) return;
        Filter filter = new Filter(categories, from, to);
        String txt = ReportFormatter.format(reports.report(wallet, filter), filter);
        if (file == null) {
            session.print(txt);
        } else {
            write(session, Path.of(file), txt);
        }
    }

    private void write(Session session, Path path, String txt) {
        try {
            Files.writeString(path, txt);
        } catch (IOException e) {
            session.println("Couldn't write " + path + " (" + Messages.describe(e) + ")");
            return;
        }
        session.println("Report saved to " + path);
    }
}
