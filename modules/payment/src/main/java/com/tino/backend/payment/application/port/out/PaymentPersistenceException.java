package com.tino.backend.payment.application.port.out;

public final class PaymentPersistenceException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public PaymentPersistenceException(Throwable cause) { super(cause); }
}
