package io.github.katehok.finance.cli.command;

import io.github.katehok.finance.core.Money;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.regex.Pattern;

public final class Args {

    public static final String EXAMPLE_DATE = "2026-11-25";
    public static final String BAD_AMOUNT = "Oops: Amount must be a positive number like 1500 or 99.90";
    public static final String BAD_DATE = "Oops: Date must look like " + EXAMPLE_DATE;
    private static final Pattern DATE_PATTERN = Pattern.compile("\\d{4}-\\d{1,2}-\\d{1,2}");

    private Args() {
    }

    public static Integer tryNum(String src) {
        try {
            return Integer.parseInt(src);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Money tryAmount(String src) {
        Money amount = Money.tryParse(src);
        if (amount == null || !amount.isPositive()) return null;
        return amount;
    }

    public static LocalDate tryDate(String src) {
        try {
            return LocalDate.parse(src);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public static boolean isDateLike(String src) {
        return DATE_PATTERN.matcher(src).matches();
    }

    public static boolean isSub(String[] args, String name, int count) {
        return args.length == count && args[1].equalsIgnoreCase(name);
    }

    public static String join(String[] args, int from) {
        if (from >= args.length) return "";
        return String.join(" ", Arrays.copyOfRange(args, from, args.length));
    }

    public static String plural(int num, String one, String many) {
        return num + " " + (num == 1 ? one : many);
    }
}
