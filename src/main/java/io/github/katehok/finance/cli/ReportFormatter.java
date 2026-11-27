package io.github.katehok.finance.cli;

import io.github.katehok.finance.cli.Table.Align;
import io.github.katehok.finance.core.ReportService.BudgetLine;
import io.github.katehok.finance.core.ReportService.Filter;
import io.github.katehok.finance.core.ReportService.Line;
import io.github.katehok.finance.core.ReportService.Report;
import java.util.List;

public final class ReportFormatter {

    private static final String INDENT = "  ";
    private static final String NONE = INDENT + "none\n";

    private ReportFormatter() {
    }

    public static String format(Report report, Filter filter) {
        StringBuilder sb = new StringBuilder();
        if (!filter.isAll()) appendLine(sb, describe(filter));
        if (report.isEmpty()) {
            appendLine(sb, filter.isAll() ? "No operations yet" : "No operations for the chosen categories/period");
        } else {
            appendLine(sb, "Total income: " + report.income());
            appendLine(sb, "Income by category:");
            sb.append(formatLines(report.incomes()));
            appendLine(sb, "Total expenses: " + report.expense());
            appendLine(sb, "Expenses by category:");
            sb.append(formatLines(report.expenses()));
        }
        appendLine(sb, hasPeriod(filter) ? "Budgets (spent for all time):" : "Budgets:");
        sb.append(report.budgets().isEmpty() ? NONE : formatBudgets(INDENT, report.budgets()));
        if (!report.isEmpty()) appendLine(sb, "Balance: " + report.balance());
        return sb.toString();
    }

    public static String formatBudgets(String indent, List<BudgetLine> budgets) {
        Table table = new Table(indent, Align.LEFT, Align.RIGHT, Align.RIGHT, Align.RIGHT);
        table.addRow("Category", "Budget", "Spent", "Remaining");
        for (BudgetLine line : budgets) {
            table.addRow(line.category(), line.limit().toString(), line.spent().toString(),
                    line.remaining().toString());
        }
        return table.format();
    }

    private static boolean hasPeriod(Filter filter) {
        return filter.from() != null || filter.to() != null;
    }

    private static String describe(Filter filter) {
        StringBuilder sb = new StringBuilder("Report for ");
        sb.append(filter.categories().isEmpty() ? "all categories" : String.join(", ", filter.categories()));
        if (filter.from() != null) sb.append(" from ").append(filter.from());
        if (filter.to() != null) sb.append(" to ").append(filter.to());
        return sb.toString();
    }

    private static String formatLines(List<Line> lines) {
        if (lines.isEmpty()) return NONE;
        Table table = new Table(INDENT, Align.LEFT, Align.RIGHT);
        for (Line line : lines) {
            table.addRow(line.category(), line.amount().toString());
        }
        return table.format();
    }

    private static void appendLine(StringBuilder sb, String txt) {
        sb.append(txt).append('\n');
    }
}
