package com.tino.backend.sync.application.usecase;

import com.tino.backend.business.application.port.in.AccessibleBusinessView;
import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.business.application.port.in.BusinessContextReader;
import com.tino.backend.shared.kernel.BusinessId;
import com.tino.backend.sync.application.exception.SyncAccessDeniedException;
import com.tino.backend.sync.application.exception.SyncBusinessContextRequiredException;
import com.tino.backend.sync.application.model.SyncChangePage;
import com.tino.backend.sync.application.port.out.SyncChangeRepository;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Deterministic, bounded, tenant-authorized Sync Pull orchestration. */
public final class PullSyncChanges {
    public static final int DEFAULT_LIMIT = 100;
    public static final int MAX_LIMIT = 100;

    private final BusinessContextReader businesses;
    private final BusinessAuthorization authorization;
    private final SyncChangeRepository changes;

    public PullSyncChanges(
            BusinessContextReader businesses,
            BusinessAuthorization authorization,
            SyncChangeRepository changes) {
        this.businesses = Objects.requireNonNull(businesses, "businesses");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.changes = Objects.requireNonNull(changes, "changes");
    }

    public SyncChangePage execute(
            UUID authenticatedUserId, UUID requestedBusinessId, long cursor, int limit) {
        Objects.requireNonNull(authenticatedUserId, "authenticatedUserId");
        if (cursor < 0) {
            throw new IllegalArgumentException("cursor must not be negative");
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        var businessId = selectBusiness(
                businesses.listAccessibleBusinesses(authenticatedUserId), requestedBusinessId);
        return authorization.execute(
                authenticatedUserId, new BusinessId(businessId),
                authorizedBusinessId -> changes.findAfter(authorizedBusinessId, cursor, limit));
    }

    private static UUID selectBusiness(
            List<AccessibleBusinessView> accessible, UUID requestedBusinessId) {
        if (requestedBusinessId != null) {
            return accessible.stream()
                    .map(AccessibleBusinessView::businessId)
                    .filter(requestedBusinessId::equals)
                    .findFirst()
                    .orElseThrow(SyncAccessDeniedException::new);
        }
        if (accessible.size() == 1) {
            return accessible.getFirst().businessId();
        }
        if (accessible.size() > 1) {
            throw new SyncBusinessContextRequiredException();
        }
        throw new SyncAccessDeniedException();
    }
}
