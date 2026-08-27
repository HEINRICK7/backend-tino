package com.tino.backend.business.application.model;

import com.tino.backend.business.domain.model.Business;
import com.tino.backend.business.domain.model.BusinessMembership;
import java.util.Objects;

/** Result of atomic Business plus first OWNER creation. */
public record CreatedBusiness(Business business, BusinessMembership membership) {
    public CreatedBusiness {
        Objects.requireNonNull(business, "business");
        Objects.requireNonNull(membership, "membership");
    }
}
