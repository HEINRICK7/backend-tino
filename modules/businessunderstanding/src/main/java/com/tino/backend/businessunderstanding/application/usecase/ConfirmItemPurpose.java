package com.tino.backend.businessunderstanding.application.usecase;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.businessunderstanding.application.port.out.BusinessUnderstandingRepository;
import com.tino.backend.businessunderstanding.domain.model.BusinessItemPurpose;
import com.tino.backend.businessunderstanding.domain.model.ItemPurpose;
import com.tino.backend.businessunderstanding.domain.model.UsageContext;
import com.tino.backend.shared.kernel.BusinessId;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

public final class ConfirmItemPurpose {
    private final BusinessAuthorization authorization;
    private final BusinessUnderstandingRepository repository;
    private final Clock clock;

    public ConfirmItemPurpose(BusinessAuthorization authorization,
            BusinessUnderstandingRepository repository, Clock clock) {
        this.authorization = authorization;
        this.repository = repository;
        this.clock = clock;
    }

    public void execute(UUID userId, BusinessId businessId, UUID productId, ItemPurpose purpose) {
        execute(userId, businessId, productId, UsageContext.LEGACY, purpose, null);
    }

    public void execute(UUID userId, BusinessId businessId, UUID productId, UsageContext usageContext,
            ItemPurpose purpose, String reason) {
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(usageContext, "usageContext");
        Objects.requireNonNull(purpose, "purpose");
        authorization.execute(userId, businessId, authorized -> {
            repository.upsertConfirmedPurpose(BusinessItemPurpose.confirmed(
                    authorized, productId, usageContext, purpose, userId, reason, clock.instant()));
            return null;
        });
    }
}
