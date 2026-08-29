package com.tino.backend.credit.domain.model;

public enum CreditDirection {
    CREDIT,
    DEBIT;

    public CreditDirection opposite() {
        return this == CREDIT ? DEBIT : CREDIT;
    }
}
