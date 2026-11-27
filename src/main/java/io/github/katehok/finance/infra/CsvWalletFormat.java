package io.github.katehok.finance.infra;

import static java.nio.charset.StandardCharsets.UTF_8;

import io.github.katehok.finance.core.Category;
import io.github.katehok.finance.core.Expense;
import io.github.katehok.finance.core.FinanceException;
import io.github.katehok.finance.core.FinanceException.Reason;
import io.github.katehok.finance.core.Income;
import io.github.katehok.finance.core.Money;
import io.github.katehok.finance.core.Operation;
import io.github.katehok.finance.core.Wallet;
import io.github.katehok.finance.core.WalletFormat;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.CharacterCodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CsvWalletFormat implements WalletFormat {

    public static final String HEADER = "type,date,category,amount,note";

    private static final int FIELDS = 5;
    private static final char BOM = '\uFEFF';

    private enum Type {
        CATEGORY,
        BUDGET,
        INCOME,
        EXPENSE,
    }

    private enum State {
        FIELD_START,
        PLAIN,
        QUOTED,
        QUOTE_IN_QUOTED,
    }

    @Override
    public void write(Wallet wallet, Path path) {
        StringBuilder sb = new StringBuilder(HEADER).append('\n');
        for (Category category : wallet.getCategories()) {
            appendRow(sb, Type.CATEGORY, "", category.getName(), "", "");
        }
        for (Category category : wallet.getCategories()) {
            if (!category.hasBudget()) continue;
            appendRow(sb, Type.BUDGET, "", category.getName(), category.getBudget().get().toPlain(), "");
        }
        for (Operation op : wallet.getOperations()) {
            Type type = op instanceof Income ? Type.INCOME : Type.EXPENSE;
            appendRow(sb, type, op.date().toString(), op.category(), op.amount().toPlain(), op.note());
        }
        AtomicFile.write(path, sb.toString());
    }

    @Override
    public Wallet read(Path path) {
        List<String> lines;
        try {
            lines = Files.readAllLines(path, UTF_8);
        } catch (CharacterCodingException e) {
            throw new FinanceException(Reason.BAD_FILE, path + " is not a valid UTF-8 file");
        } catch (IOException e) {
            throw new UncheckedIOException("Couldn't read " + path, e);
        }
        if (!lines.isEmpty() && !lines.get(0).isEmpty() && lines.get(0).charAt(0) == BOM) {
            lines.set(0, lines.get(0).substring(1));
        }
        return parse(lines);
    }

    private static Wallet parse(List<String> lines) {
        Wallet wallet = new Wallet();
        boolean hasHeader = false;
        for (int i = 0; i < lines.size(); ++i) {
            String line = lines.get(i);
            if (line.isBlank()) continue;
            try {
                if (hasHeader) {
                    apply(wallet, line);
                } else {
                    checkHeader(line);
                    hasHeader = true;
                }
            } catch (FinanceException e) {
                throw new FinanceException(Reason.BAD_FILE, "Line " + (i + 1) + ": " + e.getMessage());
            }
        }
        if (!hasHeader) throw new FinanceException(Reason.BAD_FILE, "File is empty");
        return wallet;
    }

    private static void checkHeader(String line) {
        if (!line.strip().equalsIgnoreCase(HEADER)) throw bad("Expected header " + HEADER);
    }

    private static void apply(Wallet wallet, String line) {
        List<String> fields = tryParseLine(line);
        if (fields == null) throw bad("Broken quotes");
        if (fields.size() != FIELDS) throw bad("Expected " + FIELDS + " fields, got " + fields.size());
        Type type = tryParseType(fields.get(0));
        if (type == null) throw bad("Unknown type " + fields.get(0).strip());
        String category = fields.get(2);
        wallet.ensureCategory(category);
        switch (type) {
            case BUDGET:
                wallet.setBudget(category, parseAmount(fields.get(3)));
                break;
            case INCOME:
                wallet.add(new Income(parseDate(fields.get(1)), category, parseAmount(fields.get(3)), note(fields)));
                break;
            case EXPENSE:
                wallet.add(new Expense(parseDate(fields.get(1)), category, parseAmount(fields.get(3)), note(fields)));
                break;
            default:
                break;
        }
    }

    private static String note(List<String> fields) {
        return fields.get(4).strip();
    }

    private static Type tryParseType(String txt) {
        try {
            return Type.valueOf(txt.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static LocalDate parseDate(String txt) {
        if (txt.isBlank()) throw bad("Date is empty");
        try {
            return LocalDate.parse(txt.strip());
        } catch (DateTimeParseException e) {
            throw bad("Bad date " + txt.strip() + ", expected yyyy-mm-dd");
        }
    }

    private static Money parseAmount(String txt) {
        Money amount = Money.tryParse(txt);
        if (amount == null) throw bad("Bad amount " + txt.strip());
        return amount;
    }

    private static FinanceException bad(String message) {
        return new FinanceException(Reason.BAD_FILE, message);
    }

    // Многострочные поля не поддерживаю: категории и заметки вводятся одной строкой
    private static List<String> tryParseLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        State state = State.FIELD_START;
        for (int i = 0; i < line.length(); ++i) {
            char ch = line.charAt(i);
            switch (state) {
                case FIELD_START:
                    if (ch == '"') {
                        state = State.QUOTED;
                    } else if (ch == ',') {
                        fields.add("");
                    } else {
                        sb.append(ch);
                        state = State.PLAIN;
                    }
                    break;
                case PLAIN:
                    if (ch == '"') return null;
                    if (ch == ',') {
                        fields.add(sb.toString());
                        sb.setLength(0);
                        state = State.FIELD_START;
                    } else {
                        sb.append(ch);
                    }
                    break;
                case QUOTED:
                    if (ch == '"') {
                        state = State.QUOTE_IN_QUOTED;
                    } else {
                        sb.append(ch);
                    }
                    break;
                case QUOTE_IN_QUOTED:
                    if (ch == '"') {
                        sb.append(ch);
                        state = State.QUOTED;
                    } else if (ch == ',') {
                        fields.add(sb.toString());
                        sb.setLength(0);
                        state = State.FIELD_START;
                    } else {
                        return null;
                    }
                    break;
            }
        }
        if (state == State.QUOTED) return null;
        fields.add(sb.toString());
        return fields;
    }

    private static void appendRow(StringBuilder sb, Type type, String... fields) {
        sb.append(type.name().toLowerCase(Locale.ROOT));
        for (String field : fields) {
            sb.append(',').append(quote(field));
        }
        sb.append('\n');
    }

    private static String quote(String txt) {
        boolean isPlain = txt.indexOf(',') < 0 && txt.indexOf('"') < 0 && txt.indexOf('\n') < 0
                && txt.indexOf('\r') < 0;
        if (isPlain) return txt;
        return '"' + txt.replace("\"", "\"\"") + '"';
    }
}
