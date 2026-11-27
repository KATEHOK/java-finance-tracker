package io.github.katehok.finance.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.katehok.finance.cli.Table.Align;
import org.junit.jupiter.api.Test;

class TableTest {

    @Test
    void alignsColumnsByWidestCell() {
        Table table = new Table("  ", Align.LEFT, Align.RIGHT, Align.LEFT);
        table.addRow("Name", "Sum", "Note");
        table.addRow("Еда", "1,000.00", "");
        String expected = """
                  Name       Sum  Note
                  Еда   1,000.00
                """;
        assertEquals(expected, table.format());
    }

    @Test
    void emptyTableGivesEmptyText() {
        assertEquals("", new Table("", Align.LEFT).format());
    }

    @Test
    void wrongCellCountRefused() {
        Table table = new Table("", Align.LEFT, Align.RIGHT);
        assertThrows(IllegalArgumentException.class, () -> table.addRow("one"));
    }
}
