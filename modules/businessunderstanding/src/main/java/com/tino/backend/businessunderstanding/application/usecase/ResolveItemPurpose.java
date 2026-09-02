package com.tino.backend.businessunderstanding.application.usecase;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.businessunderstanding.application.exception.BusinessNotReadyException;
import com.tino.backend.businessunderstanding.application.model.ItemPurposeResolution;
import com.tino.backend.businessunderstanding.application.port.out.BusinessUnderstandingRepository;
import com.tino.backend.businessunderstanding.domain.model.BusinessItemPurpose;
import com.tino.backend.businessunderstanding.domain.model.BusinessUnderstandingSnapshot;
import com.tino.backend.businessunderstanding.domain.model.ItemPurpose;
import com.tino.backend.businessunderstanding.domain.model.ItemPurposeSource;
import com.tino.backend.businessunderstanding.domain.model.OperatingMode;
import com.tino.backend.businessunderstanding.domain.model.UsageContext;
import com.tino.backend.shared.kernel.BusinessId;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class ResolveItemPurpose {
    private static final BigDecimal AMBIGUOUS_CONFIDENCE = new BigDecimal("0.42");
    private static final BigDecimal SUGGESTED_CONFIDENCE = new BigDecimal("0.60");
    private final BusinessAuthorization authorization;
    private final BusinessUnderstandingRepository repository;

    public ResolveItemPurpose(BusinessAuthorization authorization, BusinessUnderstandingRepository repository) {
        this.authorization = authorization;
        this.repository = repository;
    }

    public ItemPurposeResolution execute(UUID userId, BusinessId businessId,
            UUID productId, String description, String source) {
        return execute(userId, businessId, productId, description, UsageContext.LEGACY, source);
    }

    public ItemPurposeResolution execute(UUID userId, BusinessId businessId,
            UUID productId, String description, UsageContext usageContext, String source) {
        if (productId == null && (description == null || description.isBlank())) {
            throw new IllegalArgumentException("product_id or description is required");
        }
        if (usageContext == null) {
            throw new IllegalArgumentException("usage context is required");
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
            if (known.isEmpty() && key != null) {
                known = repository.findPurposeByCanonicalKey(authorized, key, usageContext);
            }
            if (known.isPresent()) {
                var purpose = known.get();
                var resolution = purpose.source().name();
                return new ItemPurposeResolution(purpose.purpose(), purpose.confidence(), resolution,
                        purpose.source() != ItemPurposeSource.USER_CONFIRMED, List.of());
            }
            return suggestFromContext(snapshot);
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

    private static ItemPurposeResolution suggestFromContext(BusinessUnderstandingSnapshot snapshot) {
        var suggestions = new ArrayList<ItemPurpose>();
        for (var purpose : List.of(ItemPurpose.PRODUCTION, ItemPurpose.RESALE, ItemPurpose.SERVICE_INPUT)) {
            if (supports(snapshot, purpose)) {
                suggestions.add(purpose);
            }
        }
        if (suggestions.size() == 1) {
            return new ItemPurposeResolution(suggestions.getFirst(), SUGGESTED_CONFIDENCE,
                    "SYSTEM_SUGGESTED", true, suggestions);
        }
        return new ItemPurposeResolution(ItemPurpose.UNKNOWN, AMBIGUOUS_CONFIDENCE,
                "AMBIGUOUS", true, suggestions);
    }

    private static boolean supports(BusinessUnderstandingSnapshot snapshot, ItemPurpose purpose) {
        var modes = snapshot.operatingModes().stream().map(mode -> mode.mode()).toList();
        return switch (purpose) {
            case RESALE -> modes.contains(OperatingMode.RESELLS_GOODS);
            case PRODUCTION -> modes.contains(OperatingMode.PRODUCES_GOODS);
            case SERVICE_INPUT -> modes.contains(OperatingMode.PROVIDES_SERVICES);
            case BUSINESS_USE, ASSET, UNKNOWN -> false;
        };
    }
}
