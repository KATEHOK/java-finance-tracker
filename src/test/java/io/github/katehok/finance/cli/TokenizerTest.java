package io.github.katehok.finance.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class TokenizerTest {

    @Test
    void splitsBySpaces() {
        assertEquals(List.of("expense", "300", "Food"), Tokenizer.trySplit("  expense   300\tFood "));
    }

    @Test
    void keepsQuotedTextAsOneToken() {
        assertEquals(List.of("expense", "3000", "Utility bills"), Tokenizer.trySplit("expense 3000 \"Utility bills\""));
        assertEquals(List.of("a  b"), Tokenizer.trySplit("\"a  b\""));
    }

    @Test
    void quotesInsideWordJoinIt() {
        assertEquals(List.of("abc d", "e"), Tokenizer.trySplit("ab\"c d\" e"));
    }

    @Test
    void emptyQuotesGiveEmptyToken() {
        assertEquals(List.of("x", "", "y"), Tokenizer.trySplit("x \"\" y"));
    }

    @Test
    void emptyInputGivesNoTokens() {
        assertEquals(List.of(), Tokenizer.trySplit("   "));
    }

    @Test
    void unclosedQuoteGivesNull() {
        assertNull(Tokenizer.trySplit("expense 300 \"Food"));
    }
}
