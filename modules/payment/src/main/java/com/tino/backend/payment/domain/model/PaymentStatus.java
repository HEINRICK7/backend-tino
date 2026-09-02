package com.tino.backend.payment.domain.model;

public enum PaymentStatus {
    CREATED, AUTHORIZED, CAPTURED, FAILED, CANCELLED, REFUNDED;

    public boolean canTransitionTo(PaymentStatus next) {
        return switch (this) {
            case CREATED -> next == AUTHORIZED || next == FAILED || next == CANCELLED;
            case AUTHORIZED -> next == CAPTURED || next == FAILED;
            case CAPTURED -> next == REFUNDED;
            case FAILED, CANCELLED, REFUNDED -> false;
        };
    }
}
