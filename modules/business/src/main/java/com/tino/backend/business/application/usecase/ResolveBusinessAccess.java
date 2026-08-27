package com.tino.backend.business.application.usecase;

import com.tino.backend.business.application.exception.BusinessAccessDeniedException;
import com.tino.backend.business.application.model.AuthorizedBusinessContext;
import com.tino.backend.business.application.port.out.BusinessMembershipRepository;
import com.tino.backend.business.application.port.out.BusinessRepository;
import com.tino.backend.business.domain.model.BusinessStatus;
import com.tino.backend.business.domain.model.MembershipStatus;
import com.tino.backend.business.domain.model.UserId;
import com.tino.backend.shared.kernel.BusinessId;
import java.util.Objects;

/** Resolves requested Business access from active membership before any tenant operation. */
public final class ResolveBusinessAccess {
    private final BusinessMembershipRepository memberships;
    private final BusinessRepository businesses;

    public ResolveBusinessAccess(
            BusinessMembershipRepository memberships, BusinessRepository businesses) {
        this.memberships = Objects.requireNonNull(memberships, "memberships");
        this.businesses = Objects.requireNonNull(businesses, "businesses");
    }

    public AuthorizedBusinessContext execute(UserId userId, BusinessId requestedBusinessId) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(requestedBusinessId, "requestedBusinessId");
        var membership = memberships.findByUserAndBusiness(userId, requestedBusinessId)
                .filter(value -> value.status() == MembershipStatus.ACTIVE)
                .orElseThrow(BusinessAccessDeniedException::new);
        var business = businesses.findById(requestedBusinessId)
                .filter(value -> value.status() == BusinessStatus.ACTIVE)
                .orElseThrow(BusinessAccessDeniedException::new);
        return new AuthorizedBusinessContext(userId, business.id(), membership.role());
    }
}
