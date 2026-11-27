package io.github.katehok.finance.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.katehok.finance.core.FinanceException.Reason;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class WalletTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 25);

    private final Wallet wallet = new Wallet();

    private void assertReason(Reason reason, Executable executable) {
        FinanceException e = assertThrows(FinanceException.class, executable);
        assertEquals(reason, e.getReason());
    }

    private void income(String category, long rubles) {
        wallet.add(new Income(DAY, category, Money.ofRubles(rubles), ""));
    }

    private void expense(String category, long rubles) {
        wallet.add(new Expense(DAY, category, Money.ofRubles(rubles), ""));
    }

    private List<String> names() {
        return wallet.getCategories().stream().map(Category::getName).toList();
    }

    @Test
    void addCategoryStripsName() {
        assertEquals("Food", wallet.addCategory("  Food ").getName());
        assertEquals(List.of("Food"), names());
    }

    @Test
    void categoriesAreCaseInsensitive() {
        wallet.addCategory("Food");
        assertTrue(wallet.hasCategory("FOOD"));
        assertTrue(wallet.hasCategory(" food "));
        assertEquals("Food", wallet.getCategory("fOoD").getName());
        assertReason(Reason.CATEGORY_EXISTS, () -> wallet.addCategory("food"));
    }

    @Test
    void russianNamesAreCaseInsensitive() {
        wallet.addCategory("Еда");
        assertTrue(wallet.hasCategory("ЕДА"));
        assertTrue(wallet.hasCategory("еда"));
    }

    @Test
    void rejectsBadNames() {
        assertReason(Reason.EMPTY_NAME, () -> wallet.addCategory(null));
        assertReason(Reason.EMPTY_NAME, () -> wallet.addCategory("   "));
        assertReason(Reason.INVALID_NAME, () -> wallet.addCategory("a".repeat(Wallet.MAX_NAME_LENGTH + 1)));
        assertReason(Reason.INVALID_NAME, () -> wallet.addCategory("Fo\tod"));
        assertEquals("a".repeat(Wallet.MAX_NAME_LENGTH),
                wallet.addCategory("a".repeat(Wallet.MAX_NAME_LENGTH)).getName());
    }

    @Test
    void ensureCategoryReturnsExisting() {
        Category food = wallet.addCategory("Food");
        assertSame(food, wallet.ensureCategory("food"));
        assertEquals("Taxi", wallet.ensureCategory("Taxi").getName());
        assertEquals(List.of("Food", "Taxi"), names());
    }

    @Test
    void unknownCategory() {
        assertFalse(wallet.findCategory("Food").isPresent());
        assertFalse(wallet.findCategory(null).isPresent());
        FinanceException e = assertThrows(FinanceException.class, () -> wallet.getCategory("Food"));
        assertEquals(Reason.UNKNOWN_CATEGORY, e.getReason());
        assertEquals("Unknown category Food", e.getMessage());
    }

    @Test
    void renameUpdatesOperationsAndKeepsBudget() {
        wallet.addCategory("Food");
        wallet.setBudget("Food", Money.ofRubles(4000));
        expense("Food", 300);
        expense("food", 500);
        wallet.renameCategory("FOOD", "Groceries");
        assertFalse(wallet.hasCategory("Food"));
        assertEquals(Money.ofRubles(4000), wallet.getCategory("groceries").getBudget().get());
        for (Operation op : wallet.getOperations()) {
            assertEquals("Groceries", op.category());
        }
        assertEquals(Money.ofRubles(800), wallet.getExpense("Groceries"));
    }

    @Test
    void renameKeepsOtherOperations() {
        wallet.addCategory("Food");
        wallet.addCategory("Taxi");
        expense("Food", 300);
        expense("Taxi", 100);
        wallet.renameCategory("Food", "Groceries");
        assertEquals("Taxi", wallet.getOperations().get(1).category());
        assertEquals(List.of("Groceries", "Taxi"), names());
    }

    @Test
    void renameToCaseVariant() {
        wallet.addCategory("food");
        expense("food", 300);
        Category category = wallet.renameCategory("food", "Food");
        assertEquals("Food", category.getName());
        assertEquals(List.of("Food"), names());
        assertEquals("Food", wallet.getOperations().get(0).category());
    }

    @Test
    void renameCollision() {
        wallet.addCategory("Food");
        wallet.addCategory("Taxi");
        FinanceException e = assertThrows(FinanceException.class, () -> wallet.renameCategory("Food", "taxi"));
        assertEquals(Reason.CATEGORY_EXISTS, e.getReason());
        assertEquals("Category Taxi already exists", e.getMessage());
        assertEquals(List.of("Food", "Taxi"), names());
    }

    @Test
    void renameUnknownOrBadName() {
        assertReason(Reason.UNKNOWN_CATEGORY, () -> wallet.renameCategory("Food", "Groceries"));
        wallet.addCategory("Food");
        assertReason(Reason.EMPTY_NAME, () -> wallet.renameCategory("Food", " "));
        assertTrue(wallet.hasCategory("Food"));
    }

    @Test
    void deleteUnusedCategory() {
        wallet.addCategory("Food");
        wallet.deleteCategory("FOOD");
        assertFalse(wallet.hasCategory("Food"));
        assertReason(Reason.UNKNOWN_CATEGORY, () -> wallet.deleteCategory("Food"));
    }

    @Test
    void deleteCategoryInUse() {
        wallet.addCategory("Food");
        expense("Food", 300);
        expense("Food", 500);
        FinanceException e = assertThrows(FinanceException.class, () -> wallet.deleteCategory("food"));
        assertEquals(Reason.CATEGORY_IN_USE, e.getReason());
        assertEquals("Category Food has 2 operation(s)", e.getMessage());
        assertTrue(wallet.hasCategory("Food"));
    }

    @Test
    void setAndRemoveBudget() {
        wallet.addCategory("Food");
        assertFalse(wallet.getCategory("Food").hasBudget());
        wallet.setBudget("food", Money.ofRubles(4000));
        assertEquals(Money.ofRubles(4000), wallet.getCategory("Food").getBudget().get());
        wallet.setBudget("Food", Money.ofRubles(5000));
        assertEquals(Money.ofRubles(5000), wallet.getCategory("Food").getBudget().get());
        wallet.removeBudget("FOOD");
        assertFalse(wallet.getCategory("Food").hasBudget());
        assertReason(Reason.NO_BUDGET, () -> wallet.removeBudget("Food"));
    }

    @Test
    void invalidBudget() {
        wallet.addCategory("Food");
        assertReason(Reason.INVALID_AMOUNT, () -> wallet.setBudget("Food", Money.ZERO));
        assertReason(Reason.INVALID_AMOUNT, () -> wallet.setBudget("Food", new Money(-100)));
        assertReason(Reason.INVALID_AMOUNT, () -> wallet.setBudget("Food", null));
        assertReason(Reason.UNKNOWN_CATEGORY, () -> wallet.setBudget("Taxi", Money.ofRubles(100)));
        assertReason(Reason.UNKNOWN_CATEGORY, () -> wallet.removeBudget("Taxi"));
        assertFalse(wallet.getCategory("Food").hasBudget());
    }

    @Test
    void addWithUnknownCategory() {
        assertReason(Reason.UNKNOWN_CATEGORY, () -> expense("Food", 300));
        assertTrue(wallet.getOperations().isEmpty());
    }

    @Test
    void addUsesCanonicalCategoryName() {
        wallet.addCategory("Food");
        Operation saved = wallet.add(new Expense(DAY, " FOOD ", Money.ofRubles(300), "lunch"));
        assertEquals(new Expense(DAY, "Food", Money.ofRubles(300), "lunch"), saved);
        assertEquals(List.of(saved), wallet.getOperations());
    }

    @Test
    void totals() {
        wallet.addCategory("Salary");
        wallet.addCategory("Food");
        wallet.addCategory("Taxi");
        income("Salary", 20000);
        income("Salary", 40000);
        expense("Food", 300);
        expense("Food", 500);
        expense("Taxi", 1500);
        assertEquals(Money.ofRubles(60000), wallet.getIncome());
        assertEquals(Money.ofRubles(2300), wallet.getExpense());
        assertEquals(Money.ofRubles(57700), wallet.getBalance());
        assertEquals(Money.ofRubles(60000), wallet.getIncome("salary"));
        assertEquals(Money.ofRubles(800), wallet.getExpense("FOOD"));
        assertEquals(Money.ZERO, wallet.getIncome("Food"));
        assertEquals(Money.ZERO, wallet.getExpense("Salary"));
        assertReason(Reason.UNKNOWN_CATEGORY, () -> wallet.getExpense("Bonus"));
    }

    @Test
    void emptyWalletTotals() {
        assertEquals(Money.ZERO, wallet.getBalance());
        assertEquals(Money.ZERO, wallet.getIncome());
        assertEquals(Money.ZERO, wallet.getExpense());
    }

    @Test
    void balanceCanBeNegative() {
        wallet.addCategory("Food");
        expense("Food", 300);
        assertEquals(Money.ofRubles(-300), wallet.getBalance());
    }

    @Test
    void merge() {
        wallet.addCategory("Food");
        wallet.setBudget("Food", Money.ofRubles(4000));
        expense("Food", 300);
        Wallet other = new Wallet();
        other.addCategory("food");
        other.setBudget("food", Money.ofRubles(100));
        other.addCategory("Taxi");
        other.setBudget("Taxi", Money.ofRubles(2000));
        other.addCategory("Salary");
        other.add(new Expense(DAY, "food", Money.ofRubles(500), "dinner"));
        other.add(new Income(DAY, "Salary", Money.ofRubles(1000), ""));
        Wallet.MergeResult result = wallet.merge(other);
        assertEquals(new Wallet.MergeResult(2, 1, 2), result);
        assertEquals(List.of("Food", "Taxi", "Salary"), names());
        assertEquals(Money.ofRubles(4000), wallet.getCategory("Food").getBudget().get());
        assertEquals(Money.ofRubles(2000), wallet.getCategory("Taxi").getBudget().get());
        assertEquals(new Expense(DAY, "Food", Money.ofRubles(500), "dinner"), wallet.getOperations().get(1));
        assertEquals(Money.ofRubles(200), wallet.getBalance());
        assertEquals(2, other.getOperations().size());
    }

    @Test
    void mergeEmptyWallet() {
        wallet.addCategory("Food");
        assertEquals(new Wallet.MergeResult(0, 0, 0), wallet.merge(new Wallet()));
        assertEquals(List.of("Food"), names());
    }

    @Test
    void getOperationsIsUnmodifiableCopy() {
        wallet.addCategory("Food");
        expense("Food", 300);
        List<Operation> ops = wallet.getOperations();
        assertThrows(UnsupportedOperationException.class,
                () -> ops.add(new Expense(DAY, "Food", Money.ofRubles(1), "")));
        expense("Food", 500);
        assertEquals(1, ops.size());
        assertEquals(2, wallet.getOperations().size());
    }

    @Test
    void getCategoriesIsUnmodifiableCopy() {
        wallet.addCategory("Food");
        List<Category> categories = wallet.getCategories();
        assertThrows(UnsupportedOperationException.class, () -> categories.remove(0));
        wallet.addCategory("Taxi");
        assertEquals(1, categories.size());
        assertEquals(2, wallet.getCategories().size());
    }
}
