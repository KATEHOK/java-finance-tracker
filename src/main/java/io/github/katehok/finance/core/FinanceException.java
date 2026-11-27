package io.github.katehok.finance.core;

public class FinanceException extends RuntimeException {

    public enum Reason {
        INVALID_LOGIN,
        INVALID_PASSWORD,
        LOGIN_TAKEN,
        BAD_CREDENTIALS,
        UNKNOWN_USER,
        EMPTY_NAME,
        INVALID_NAME,
        INVALID_AMOUNT,
        INVALID_DATE,
        INVALID_PERIOD,
        UNKNOWN_CATEGORY,
        CATEGORY_EXISTS,
        CATEGORY_IN_USE,
        NO_BUDGET,
        NOT_ENOUGH_MONEY,
        SELF_TRANSFER,
        BAD_FILE,
    }

    private final Reason reason;

    public FinanceException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
