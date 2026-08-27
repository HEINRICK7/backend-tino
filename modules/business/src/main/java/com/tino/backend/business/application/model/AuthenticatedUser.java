package com.tino.backend.business.application.model;

import com.tino.backend.business.domain.model.UserId;
import java.util.Objects;

/** Minimal user snapshot supplied by the identity boundary to Business use cases. */
public record AuthenticatedUser(UserId userId, boolean active) {
    public AuthenticatedUser {
        Objects.requireNonNull(userId, "userId");
    }
}
