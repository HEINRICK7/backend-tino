package com.tino.backend.business.application.model;

import com.tino.backend.business.domain.model.Business;
import com.tino.backend.business.domain.model.BusinessRole;
import java.util.Objects;

/** Business listing item after active membership authorization. */
public record AccessibleBusiness(Business business, BusinessRole role) {
    public AccessibleBusiness {
        Objects.requireNonNull(business, "business");
        Objects.requireNonNull(role, "role");
    }
}
