package com.tino.backend.business.application.port.out;

import com.tino.backend.business.domain.model.BusinessMembership;
import com.tino.backend.business.domain.model.UserId;
import com.tino.backend.shared.kernel.BusinessId;
import java.util.List;
import java.util.Optional;

/** Specific membership authorization queries and insertion operation. */
public interface BusinessMembershipRepository {
    void insert(BusinessMembership membership) throws DuplicateMembershipException;

    Optional<BusinessMembership> findByUserAndBusiness(UserId userId, BusinessId businessId);

    List<BusinessMembership> findActiveByUser(UserId userId);
}
