package com.tino.backend.businessunderstanding.application.usecase;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.businessunderstanding.application.exception.BusinessNotReadyException;
import com.tino.backend.businessunderstanding.application.model.ItemPurposeResolution;
import com.tino.backend.businessunderstanding.application.port.out.BusinessUnderstandingRepository;
import com.tino.backend.businessunderstanding.domain.model.BusinessItemPurpose;
import com.tino.backend.businessunderstanding.domain.model.BusinessUnderstandingSnapshot;
import com.tino.backend.businessunderstanding.domain.model.ItemPurpose;
import com.tino.backend.businessunderstanding.domain.model.ItemPurposeHint;
import com.tino.backend.businessunderstanding.domain.model.ItemPurposeResolutionDecision;
import com.tino.backend.businessunderstanding.domain.model.ItemPurposeSource;
import com.tino.backend.businessunderstanding.domain.model.UsageContext;
import com.tino.backend.businessunderstanding.domain.service.ItemPurposeResolutionEngine;
import com.tino.backend.shared.kernel.BusinessId;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Comparator;
import java.util.UUID;

public final class ResolveItemPurpose {
    private final BusinessAuthorization authorization;
    private final BusinessUnderstandingRepository repository;
    private final ItemPurposeResolutionEngine engine;

    public ResolveItemPurpose(BusinessAuthorization authorization, BusinessUnderstandingRepository repository) {
        this.authorization = authorization;
        this.repository = repository;
        this.engine = new ItemPurposeResolutionEngine();
    }

    public ItemPurposeResolution execute(UUID userId, BusinessId businessId,
            UUID productId, String description, String source) {
        return execute(userId, businessId, productId, description, UsageContext.LEGACY, source);
    }

    public ItemPurposeResolution execute(UUID userId, BusinessId businessId,
            UUID productId, String description, UsageContext usageContext, String source) {
        return execute(userId, businessId, productId, description, usageContext, List.of(), source);
    }

    public ItemPurposeResolution execute(UUID userId, BusinessId businessId,
            UUID productId, String description, UsageContext usageContext,
            List<ItemPurposeHint> hints, String source) {
        if (productId == null && (description == null || description.isBlank())) {
            throw new IllegalArgumentException("product_id or description is required");
        }
        if (usageContext == null) {
            throw new IllegalArgumentException("usage context is required");
        }
        if (hints == null) {
            throw new IllegalArgumentException("hints are required");
        }
        var key = canonicalKey(description);
        return authorization.execute(userId, businessId, authorized -> {
            var snapshot = new BusinessUnderstandingSnapshot(
                    repository.findActivities(authorized), repository.findOperatingModes(authorized));
            if (snapshot.status() != com.tino.backend.businessunderstanding.domain.model.BusinessUnderstandingStatus.READY) {
                throw new BusinessNotReadyException();
            }
            var known = productId == null ? java.util.Optional.<BusinessItemPurpose>empty()
                    : repository.findPurposeByProduct(authorized, productId, usageContext);
            var canonical = key == null ? java.util.Optional.<BusinessItemPurpose>empty()
                    : repository.findPurposeByCanonicalKey(authorized, key, usageContext);
            known = selectMoreAuthoritative(known, canonical, productId != null);
            if (known.isPresent()) {
                var purpose = known.get();
                return fromStoredPurpose(purpose);
            }
            return fromDecision(engine.resolve(snapshot, usageContext, hints));
        });
    }

    static String canonicalKey(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        var normalized = Normalizer.normalize(description.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("\\s+", " ")
                .toUpperCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    private static java.util.Optional<BusinessItemPurpose> selectMoreAuthoritative(
            java.util.Optional<BusinessItemPurpose> productPurpose,
            java.util.Optional<BusinessItemPurpose> canonicalPurpose,
            boolean hasProductId) {
        return java.util.stream.Stream.concat(productPurpose.stream(), canonicalPurpose.stream())
                .sorted(Comparator
                        .comparingInt((BusinessItemPurpose item) -> item.source().authority().rank()).reversed()
                        .thenComparing(item -> hasProductId && item.productId() != null ? 0 : 1))
                .findFirst();
    }

    private static ItemPurposeResolution fromStoredPurpose(BusinessItemPurpose purpose) {
        var evidence = List.of(new com.tino.backend.businessunderstanding.domain.model.ItemPurposeResolutionEvidence(
                "HISTORY", purpose.purpose(), purpose.source().name() + ": " + purpose.evidenceReason()));
        return new ItemPurposeResolution(purpose.purpose(), purpose.confidence(), purpose.source().name(),
                purpose.source().authority(), purpose.source() != ItemPurposeSource.USER_CONFIRMED,
                List.of(), evidence);
    }

    private static ItemPurposeResolution fromDecision(ItemPurposeResolutionDecision decision) {
        return new ItemPurposeResolution(decision.purpose(), decision.confidence(), decision.resolution(),
                decision.authority(), decision.needsConfirmation(), decision.suggestions(), decision.evidence());
    }
}
