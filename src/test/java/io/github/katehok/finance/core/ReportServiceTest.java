package io.github.katehok.finance.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.katehok.finance.core.FinanceException.Reason;
import io.github.katehok.finance.core.ReportService.BudgetLine;
import io.github.katehok.finance.core.ReportService.Filter;
import io.github.katehok.finance.core.ReportService.Line;
import io.github.katehok.finance.core.ReportService.Report;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReportServiceTest {

    private final ReportService service = new ReportService();
    private final Wallet wallet = new Wallet();

    @BeforeEach
    void setUp() {
        for (String name : List.of("Еда", "Развлечения", "Коммунальные услуги", "Такси", "Зарплата", "Бонус")) {
            wallet.addCategory(name);
        }
        income("2026-10-25", "Зарплата", 20000);
        expense("2026-10-30", "Еда", 300);
        expense("2026-11-01", "Еда", 500);
        expense("2026-11-05", "Развлечения", 3000);
        expense("2026-11-10", "Коммунальные услуги", 3000);
        income("2026-11-25", "Зарплата", 40000);
        expense("2026-11-30", "Такси", 1500);
        income("2026-12-01", "Бонус", 3000);
        wallet.setBudget("Еда", Money.ofRubles(4000));
        wallet.setBudget("Развлечения", Money.ofRubles(3000));
        wallet.setBudget("Коммунальные услуги", Money.ofRubles(2500));
    }

    private void income(String date, String category, long rubles) {
        wallet.add(new Income(LocalDate.parse(date), category, Money.ofRubles(rubles), ""));
    }

    private void expense(String date, String category, long rubles) {
        wallet.add(new Expense(LocalDate.parse(date), category, Money.ofRubles(rubles), ""));
    }

    private static List<String> lines(List<Line> lines) {
        List<String> txt = new ArrayList<>();
        for (Line line : lines) {
            txt.add(line.category() + " " + line.amount());
        }
        return txt;
    }

    private static List<String> budgets(List<BudgetLine> lines) {
        List<String> txt = new ArrayList<>();
        for (BudgetLine line : lines) {
            txt.add(line.category() + " " + line.remaining());
        }
        return txt;
    }

    private static Filter period(String from, String to) {
        return new Filter(List.of(), from == null ? null : LocalDate.parse(from), to == null ? null : LocalDate.parse(to));
    }

    @Test
    void fullReportMatchesAssignmentExample() {
        Report report = service.report(wallet);
        assertEquals("63,000.00", report.income().toString());
        assertEquals(List.of("Зарплата 60,000.00", "Бонус 3,000.00"), lines(report.incomes()));
        assertEquals("8,300.00", report.expense().toString());
        assertEquals(List.of("Еда 800.00", "Развлечения 3,000.00", "Коммунальные услуги 3,000.00", "Такси 1,500.00"),
                lines(report.expenses()));
        assertEquals(List.of("Еда 3,200.00", "Развлечения 0.00", "Коммунальные услуги -500.00"),
                budgets(report.budgets()));
        assertEquals("54,700.00", report.balance().toString());
        assertEquals(8, report.operations());
        assertFalse(report.isEmpty());
    }

    @Test
    void budgetLineHasLimitAndSpent() {
        BudgetLine line = service.report(wallet).budgets().get(2);
        assertEquals(new BudgetLine("Коммунальные услуги", Money.ofRubles(2500), Money.ofRubles(3000)), line);
    }

    @Test
    void filterBySeveralCategories() {
        Report report = service.report(wallet, new Filter(List.of("еда", "ТАКСИ"), null, null));
        assertEquals(Money.ZERO, report.income());
        assertEquals("2,300.00", report.expense().toString());
        assertEquals(List.of(), report.incomes());
        assertEquals(List.of("Еда 800.00", "Такси 1,500.00"), lines(report.expenses()));
        assertEquals(List.of("Еда 3,200.00"), budgets(report.budgets()));
        assertEquals(3, report.operations());
    }

    @Test
    void filterByPeriodIncludesBounds() {
        Report report = service.report(wallet, period("2026-11-01", "2026-11-30"));
        assertEquals("40,000.00", report.income().toString());
        assertEquals("8,000.00", report.expense().toString());
        assertEquals(List.of("Еда 500.00", "Развлечения 3,000.00", "Коммунальные услуги 3,000.00", "Такси 1,500.00"),
                lines(report.expenses()));
        assertEquals(5, report.operations());
    }

    @Test
    void filterByOpenPeriod() {
        assertEquals(3, service.report(wallet, period("2026-11-25", null)).operations());
        assertEquals(2, service.report(wallet, period(null, "2026-10-30")).operations());
    }

    @Test
    void filterByCategoryAndPeriod() {
        Filter filter = new Filter(List.of("Еда"), LocalDate.parse("2026-11-01"), null);
        Report report = service.report(wallet, filter);
        assertEquals(List.of("Еда 500.00"), lines(report.expenses()));
        assertEquals(1, report.operations());
    }

    @Test
    void unknownCategoryListsNames() {
        Filter filter = new Filter(List.of("Еда", "Кино", "Спорт"), null, null);
        FinanceException e = assertThrows(FinanceException.class, () -> service.report(wallet, filter));
        assertEquals(Reason.UNKNOWN_CATEGORY, e.getReason());
        assertEquals("Unknown categories Кино, Спорт", e.getMessage());
    }

    @Test
    void unknownSingleCategory() {
        Filter filter = new Filter(List.of("Кино"), null, null);
        FinanceException e = assertThrows(FinanceException.class, () -> service.report(wallet, filter));
        assertEquals("Unknown category Кино", e.getMessage());
    }

    @Test
    void startAfterEnd() {
        FinanceException e = assertThrows(FinanceException.class,
                () -> service.report(wallet, period("2026-11-30", "2026-11-01")));
        assertEquals(Reason.INVALID_PERIOD, e.getReason());
        assertEquals("Start date 2026-11-30 is after end date 2026-11-01", e.getMessage());
    }

    @Test
    void emptyResultStillHasBudgets() {
        Report report = service.report(wallet, period("2027-01-01", "2027-01-31"));
        assertTrue(report.isEmpty());
        assertEquals(Money.ZERO, report.income());
        assertEquals(Money.ZERO, report.expense());
        assertEquals(List.of(), report.incomes());
        assertEquals(List.of(), report.expenses());
        assertEquals(3, report.budgets().size());
    }

    @Test
    void emptyWallet() {
        Report report = service.report(new Wallet());
        assertTrue(report.isEmpty());
        assertEquals(List.of(), report.budgets());
        assertEquals(Money.ZERO, report.balance());
    }

    @Test
    void filterAll() {
        assertTrue(Filter.all().isAll());
        assertTrue(new Filter(null, null, null).isAll());
        assertFalse(period("2026-11-01", null).isAll());
    }
}
