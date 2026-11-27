package io.github.katehok.finance.cli;

import java.util.ArrayList;
import java.util.List;

final class Tokenizer {

    private Tokenizer() {
    }

    static List<String> trySplit(String src) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean isQuoted = false;
        boolean hasToken = false;
        for (int i = 0; i < src.length(); ++i) {
            char ch = src.charAt(i);
            if (ch == '"') {
                isQuoted = !isQuoted;
                hasToken = true;
            } else if (!isQuoted && Character.isWhitespace(ch)) {
                if (hasToken) tokens.add(sb.toString());
                sb.setLength(0);
                hasToken = false;
            } else {
                sb.append(ch);
                hasToken = true;
            }
        }
        if (isQuoted) return null;
        if (hasToken) tokens.add(sb.toString());
        return tokens;
    }
}
