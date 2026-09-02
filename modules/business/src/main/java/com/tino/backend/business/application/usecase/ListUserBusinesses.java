package com.tino.backend.business.application.usecase;

import com.tino.backend.business.application.model.AccessibleBusiness;
import com.tino.backend.business.application.port.out.BusinessMembershipRepository;
import com.tino.backend.business.application.port.out.BusinessRepository;
import com.tino.backend.business.domain.model.BusinessStatus;
import com.tino.backend.business.domain.model.UserId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Lists only active Businesses reached through the authenticated User's active memberships. */
public final class ListUserBusinesses {
    private final BusinessMembershipRepository memberships;
    private final BusinessRepository businesses;

    public ListUserBusinesses(
            BusinessMembershipRepository memberships, BusinessRepository businesses) {
        this.memberships = Objects.requireNonNull(memberships, "memberships");
        this.businesses = Objects.requireNonNull(businesses, "businesses");
    }

    public List<AccessibleBusiness> execute(UserId userId) {
        Objects.requireNonNull(userId, "userId");
        var activeMemberships = memberships.findActiveByUser(userId);
        if (activeMemberships.isEmpty()) {
            return List.of();
        }
        var byId = businesses.findByIds(activeMemberships.stream()
                        .map(membership -> membership.businessId())
                        .toList())
                .stream()
                .collect(Collectors.toMap(business -> business.id(), Function.identity(), (first, ignored) -> first));
        return activeMemberships.stream()
                .map(membership -> Map.entry(byId.get(membership.businessId()), membership))
                .filter(entry -> entry.getKey() != null)
                .filter(entry -> entry.getKey().status() == BusinessStatus.ACTIVE)
                .map(entry -> new AccessibleBusiness(entry.getKey(), entry.getValue().role()))
                .toList();
    }
}
