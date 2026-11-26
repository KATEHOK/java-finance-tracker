package io.github.katehok.finance.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MoneyTest {

    @Test
    void parseRubles() {
        assertEquals(new Money(30000), Money.tryParse("300"));
    }

    @Test
    void parseOneDigitKopecksAsTens() {
        assertEquals(new Money(30050), Money.tryParse("300.5"));
    }

    @Test
    void parseCommaAsSeparator() {
        assertEquals(new Money(30050), Money.tryParse("300,50"));
        assertEquals(new Money(30007), Money.tryParse("300.07"));
    }

    @Test
    void parseStripsSpaces() {
        assertEquals(new Money(150000), Money.tryParse("  1500 "));
    }

    @Test
    void parseZero() {
        assertEquals(Money.ZERO, Money.tryParse("0"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "abc", "-5", "+5", "300.", ".5", "300.555", "1e3", "1 000", "300.5.1", "12,34,56"})
    void parseRejectsBadInput(String src) {
        assertNull(Money.tryParse(src));
    }

    @Test
    void parseRejectsNull() {
        assertNull(Money.tryParse(null));
    }

    @Test
    void parseLimits() {
        assertEquals(Money.ofRubles(Money.MAX_RUBLES), Money.tryParse("1000000000000"));
        assertEquals(new Money(Money.MAX_RUBLES * 100 + 99), Money.tryParse("1000000000000.99"));
        assertNull(Money.tryParse("1000000000001"));
        assertNull(Money.tryParse("10000000000000"));
    }

    @Test
    void formatsWithGrouping() {
        assertEquals("63,000.00", Money.ofRubles(63000).toString());
        assertEquals("1,234,567.89", new Money(123456789).toString());
        assertEquals("999.99", new Money(99999).toString());
    }

    @Test
    void formatsSmallAmounts() {
        assertEquals("0.00", Money.ZERO.toString());
        assertEquals("0.05", new Money(5).toString());
    }

    @Test
    void formatsNegative() {
        assertEquals("-500.00", Money.ofRubles(-500).toString());
        assertEquals("-0.50", new Money(-50).toString());
        assertEquals("-1,500.25", new Money(-150025).toString());
    }

    @Test
    void toPlainHasNoGrouping() {
        assertEquals("63000.00", Money.ofRubles(63000).toPlain());
        assertEquals("-1500.25", new Money(-150025).toPlain());
        assertEquals("0.07", new Money(7).toPlain());
    }

    @Test
    void toPlainParsesBack() {
        Money money = new Money(123456789);
        assertEquals(money, Money.tryParse(money.toPlain()));
    }

    @Test
    void arithmeticIsExact() {
        Money sum = Money.tryParse("0.1").plus(Money.tryParse("0.2"));
        assertEquals(Money.tryParse("0.3"), sum);
        assertEquals(new Money(-100), Money.ZERO.minus(new Money(100)));
    }

    @Test
    void signChecks() {
        assertTrue(new Money(1).isPositive());
        assertTrue(new Money(-1).isNegative());
        assertTrue(Money.ZERO.isZero());
        assertFalse(Money.ZERO.isPositive());
        assertFalse(Money.ZERO.isNegative());
    }

    @Test
    void percentCheck() {
        Money limit = Money.ofRubles(4000);
        assertTrue(Money.ofRubles(3200).isAtLeastPercentOf(limit, 80));
        assertFalse(new Money(319999).isAtLeastPercentOf(limit, 80));
    }

    @Test
    void compares() {
        assertTrue(Money.ofRubles(2).compareTo(Money.ofRubles(1)) > 0);
        assertEquals(0, Money.ofRubles(1).compareTo(new Money(100)));
    }
}
