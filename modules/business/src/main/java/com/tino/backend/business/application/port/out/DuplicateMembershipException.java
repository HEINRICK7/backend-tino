package com.tino.backend.business.application.port.out;

/** Physical (business_id, user_id) uniqueness violation translated by an adapter. */
public final class DuplicateMembershipException extends BusinessPersistenceException {
    private static final long serialVersionUID = 1L;

    public DuplicateMembershipException(Throwable cause) {
        super(cause);
    }
}
