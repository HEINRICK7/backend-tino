package com.tino.backend.credit.application.exception;

public final class CreditEntryNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public CreditEntryNotFoundException() {
        super("credit entry not found");
    }
}
