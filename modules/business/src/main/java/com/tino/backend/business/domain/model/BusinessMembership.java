package com.tino.backend.business.domain.model;

import com.tino.backend.shared.kernel.BusinessId;
import java.time.Instant;
import java.util.Objects;

/** Explicit authorization relationship between one internal User and one Business. */
public record BusinessMembership(
        MembershipId id,
        BusinessId businessId,
        UserId userId,
        BusinessRole role,
        MembershipStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public BusinessMembership {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(businessId, "businessId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static BusinessMembership owner(
            MembershipId id,
            BusinessId businessId,
            UserId userId,
            Instant createdAt,
            Instant updatedAt) {
        return new BusinessMembership(
                id,
                businessId,
                userId,
                BusinessRole.OWNER,
                MembershipStatus.ACTIVE,
                createdAt,
                updatedAt);
    }
}
