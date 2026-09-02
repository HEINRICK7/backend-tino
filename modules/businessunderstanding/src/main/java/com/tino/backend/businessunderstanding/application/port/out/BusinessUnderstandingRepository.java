package com.tino.backend.businessunderstanding.application.port.out;

import com.tino.backend.businessunderstanding.domain.model.BusinessActivity;
import com.tino.backend.businessunderstanding.domain.model.BusinessItemPurpose;
import com.tino.backend.businessunderstanding.domain.model.BusinessOperatingMode;
import com.tino.backend.businessunderstanding.domain.model.UsageContext;
import com.tino.backend.shared.kernel.BusinessId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BusinessUnderstandingRepository {
    List<BusinessActivity> findActivities(BusinessId businessId);

    List<BusinessOperatingMode> findOperatingModes(BusinessId businessId);

    void replaceActivities(BusinessId businessId, List<BusinessActivity> activities, java.time.Instant now);

    void replaceOperatingModes(BusinessId businessId, List<BusinessOperatingMode> modes, java.time.Instant now);

    Optional<BusinessItemPurpose> findPurposeByProduct(BusinessId businessId, UUID productId,
            UsageContext usageContext);

    Optional<BusinessItemPurpose> findPurposeByCanonicalKey(BusinessId businessId, String canonicalItemKey,
            UsageContext usageContext);

    default Optional<BusinessItemPurpose> findPurposeByProduct(BusinessId businessId, UUID productId) {
        return findPurposeByProduct(businessId, productId, UsageContext.LEGACY);
    }

    default Optional<BusinessItemPurpose> findPurposeByCanonicalKey(BusinessId businessId, String canonicalItemKey) {
        return findPurposeByCanonicalKey(businessId, canonicalItemKey, UsageContext.LEGACY);
    }

    void upsertAutomaticPurpose(BusinessItemPurpose purpose);

    void upsertConfirmedPurpose(BusinessItemPurpose purpose);
}
