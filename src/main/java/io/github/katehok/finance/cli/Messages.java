package io.github.katehok.finance.cli;

import io.github.katehok.finance.core.Alert;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.NoSuchFileException;

public final class Messages {

    private Messages() {
    }

    public static String formatAlert(Alert alert) {
        switch (alert.type()) {
            case BUDGET_NEAR:
                long percent = alert.amount().kopecks() * 100 / alert.limit().kopecks();
                return alert.category() + " budget is " + percent + "% used (" + alert.amount() + " of "
                        + alert.limit() + ")";
            case BUDGET_EXCEEDED:
                return alert.category() + " budget is exceeded by " + alert.amount().minus(alert.limit()) + " ("
                        + alert.amount() + " of " + alert.limit() + ")";
            case EXPENSES_OVER_INCOME:
                return "Expenses exceed income by " + alert.amount();
            default:
                return "Your balance is zero";
        }
    }

    public static String describe(IOException e) {
        if (e instanceof AccessDeniedException) return "Permission denied";
        if (e instanceof NoSuchFileException) return "No such file or directory";
        if (e instanceof FileAlreadyExistsException) return "File already exists";
        if (e instanceof FileSystemException fse && fse.getReason() != null) return fse.getReason();
        return e.getMessage();
    }

    public static String describe(UncheckedIOException e) {
        String cause = describe(e.getCause());
        if (cause == null || cause.isBlank()) return e.getMessage();
        return e.getMessage() + " (" + cause + ")";
    }
}
