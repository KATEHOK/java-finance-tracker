package io.github.katehok.finance.core;

final class Text {

    private Text() {
    }

    static boolean hasControlChars(String txt) {
        for (int i = 0; i < txt.length(); ++i) {
            if (Character.isISOControl(txt.charAt(i))) return true;
        }
        return false;
    }
}
