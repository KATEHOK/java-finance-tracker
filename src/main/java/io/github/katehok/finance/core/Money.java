package io.github.katehok.finance.core;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Храню копейки в long, а не double: 0.1 + 0.2 в деньгах должно давать ровно 0.3
public record Money(long kopecks) implements Comparable<Money> {

    public static final Money ZERO = new Money(0);
    public static final long MAX_RUBLES = 1_000_000_000_000L;

    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(\\d{1,13})(?:[.,](\\d{1,2}))?");

    public static Money ofRubles(long rubles) {
        return new Money(Math.multiplyExact(rubles, 100));
    }

    public static Money tryParse(String src) {
        if (src == null) return null;
        Matcher matcher = AMOUNT_PATTERN.matcher(src.strip());
        if (!matcher.matches()) return null;
        long rubles = Long.parseLong(matcher.group(1));
        if (rubles > MAX_RUBLES) return null;
        String txt = matcher.group(2);
        if (txt == null) return ofRubles(rubles);
        long kopecks = Long.parseLong(txt.length() == 1 ? txt + "0" : txt);
        return new Money(rubles * 100 + kopecks);
    }

    public Money plus(Money other) {
        return new Money(Math.addExact(kopecks, other.kopecks));
    }

    public Money minus(Money other) {
        return new Money(Math.subtractExact(kopecks, other.kopecks));
    }

    public boolean isPositive() {
        return kopecks > 0;
    }

    public boolean isNegative() {
        return kopecks < 0;
    }

    public boolean isZero() {
        return kopecks == 0;
    }

    public boolean isAtLeastPercentOf(Money total, int percent) {
        return Math.multiplyExact(kopecks, 100L) >= Math.multiplyExact(total.kopecks, (long) percent);
    }

    @Override
    public int compareTo(Money other) {
        return Long.compare(kopecks, other.kopecks);
    }

    public String toPlain() {
        return format("%d.%02d");
    }

    @Override
    public String toString() {
        return format("%,d.%02d");
    }

    private String format(String pattern) {
        long abs = Math.abs(kopecks);
        String txt = String.format(Locale.US, pattern, abs / 100, abs % 100);
        return kopecks < 0 ? "-" + txt : txt;
    }
}
