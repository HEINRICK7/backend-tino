package com.tino.backend.business.application.usecase;

import com.tino.backend.business.application.exception.InactiveAuthenticatedUserException;
import com.tino.backend.business.application.model.AuthenticatedUser;
import com.tino.backend.business.application.model.CreatedBusiness;
import com.tino.backend.business.application.port.out.BusinessRepository;
import com.tino.backend.business.domain.model.Business;
import com.tino.backend.business.domain.model.BusinessMembership;
import com.tino.backend.business.domain.model.BusinessName;
import com.tino.backend.business.domain.model.BusinessVertical;
import com.tino.backend.business.domain.model.MembershipId;
import com.tino.backend.shared.kernel.BusinessId;
import com.tino.backend.shared.kernel.UuidGenerator;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/** Creates an ACTIVE Business and its ACTIVE OWNER atomically through one port operation. */
public final class CreateBusiness {
    private final BusinessRepository businesses;
    private final UuidGenerator ids;
    private final Clock clock;

    public CreateBusiness(BusinessRepository businesses, UuidGenerator ids, Clock clock) {
        this.businesses = Objects.requireNonNull(businesses, "businesses");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public CreatedBusiness execute(
            AuthenticatedUser authenticatedUser, String tradeName, BusinessVertical vertical) {
        if (authenticatedUser == null || !authenticatedUser.active()) {
            throw new InactiveAuthenticatedUserException();
        }
        Objects.requireNonNull(vertical, "vertical");
        var now = Instant.now(clock);
        var business = Business.active(
                new BusinessId(ids.next()), new BusinessName(tradeName), vertical, now, now);
        var owner = BusinessMembership.owner(
                new MembershipId(ids.next()), business.id(), authenticatedUser.userId(), now, now);
        businesses.createWithOwner(business, owner);
        return new CreatedBusiness(business, owner);
    }
}
