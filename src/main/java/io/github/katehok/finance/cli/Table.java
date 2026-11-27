package io.github.katehok.finance.cli;

import java.util.ArrayList;
import java.util.List;

public class Table {

    public enum Align {
        LEFT,
        RIGHT,
    }

    private static final String GAP = "  ";

    private final String indent;
    private final Align[] aligns;
    private final List<String[]> rows = new ArrayList<>();

    public Table(String indent, Align... aligns) {
        this.indent = indent;
        this.aligns = aligns.clone();
    }

    public Table addRow(String... cells) {
        if (cells.length != aligns.length) {
            throw new IllegalArgumentException("Expected " + aligns.length + " cells, got " + cells.length);
        }
        rows.add(cells.clone());
        return this;
    }

    public String format() {
        int[] widths = new int[aligns.length];
        for (String[] row : rows) {
            for (int i = 0; i < row.length; ++i) {
                widths[i] = Math.max(widths[i], row[i].length());
            }
        }
        StringBuilder sb = new StringBuilder();
        for (String[] row : rows) {
            StringBuilder line = new StringBuilder(indent);
            for (int i = 0; i < row.length; ++i) {
                if (i > 0) line.append(GAP);
                String pad = " ".repeat(widths[i] - row[i].length());
                if (aligns[i] == Align.RIGHT) {
                    line.append(pad).append(row[i]);
                } else {
                    line.append(row[i]).append(pad);
                }
            }
            sb.append(line.toString().stripTrailing()).append('\n');
        }
        return sb.toString();
    }
}
