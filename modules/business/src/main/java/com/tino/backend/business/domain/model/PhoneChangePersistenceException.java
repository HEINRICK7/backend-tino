package com.tino.backend.business.domain.model;

/** Persistence failure isolated from the phone-change application contract. */
public final class PhoneChangePersistenceException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public PhoneChangePersistenceException(Throwable cause) {
        super("phone change persistence failed", cause);
    }
}
