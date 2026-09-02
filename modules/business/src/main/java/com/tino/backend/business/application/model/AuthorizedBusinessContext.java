package com.tino.backend.business.application.model;

import com.tino.backend.business.domain.model.UserId;
import com.tino.backend.business.domain.model.BusinessRole;
import com.tino.backend.shared.kernel.BusinessId;
import java.util.Objects;

/** Framework-independent result of membership and business authorization. */
public record AuthorizedBusinessContext(UserId userId, BusinessId businessId, BusinessRole role) {
    public AuthorizedBusinessContext {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(businessId, "businessId");
        Objects.requireNonNull(role, "role");
    }
}
