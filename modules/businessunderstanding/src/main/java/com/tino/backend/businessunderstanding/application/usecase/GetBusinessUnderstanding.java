package com.tino.backend.businessunderstanding.application.usecase;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.businessunderstanding.application.port.out.BusinessUnderstandingRepository;
import com.tino.backend.businessunderstanding.domain.model.BusinessUnderstandingSnapshot;
import com.tino.backend.shared.kernel.BusinessId;
import java.util.UUID;

public final class GetBusinessUnderstanding {
    private final BusinessAuthorization authorization;
    private final BusinessUnderstandingRepository repository;

    public GetBusinessUnderstanding(BusinessAuthorization authorization, BusinessUnderstandingRepository repository) {
        this.authorization = authorization;
        this.repository = repository;
    }

    public BusinessUnderstandingSnapshot execute(UUID userId, BusinessId businessId) {
        return authorization.execute(userId, businessId, authorized -> new BusinessUnderstandingSnapshot(
                repository.findActivities(authorized), repository.findOperatingModes(authorized)));
    }
}
