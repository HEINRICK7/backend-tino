package com.tino.backend.businessunderstanding.domain.service;

import com.tino.backend.businessunderstanding.domain.model.ActivityCode;
import com.tino.backend.businessunderstanding.domain.model.BusinessActivity;
import com.tino.backend.businessunderstanding.domain.model.BusinessUnderstandingSnapshot;
import com.tino.backend.businessunderstanding.domain.model.ItemPurpose;
import com.tino.backend.businessunderstanding.domain.model.ItemPurposeAuthority;
import com.tino.backend.businessunderstanding.domain.model.ItemPurposeHint;
import com.tino.backend.businessunderstanding.domain.model.ItemPurposeResolutionDecision;
import com.tino.backend.businessunderstanding.domain.model.ItemPurposeResolutionEvidence;
import com.tino.backend.businessunderstanding.domain.model.OperatingMode;
import com.tino.backend.businessunderstanding.domain.model.UsageContext;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Resolves an item from business context and explicit semantic evidence.
 * Product names are deliberately not interpreted here: a name alone cannot
 * decide how an item is used by a business.
 */
public final class ItemPurposeResolutionEngine {
    private static final BigDecimal CONTEXT_CONFIDENCE = new BigDecimal("0.95");
    private static final BigDecimal HINT_CONFIDENCE = new BigDecimal("0.85");
    private static final BigDecimal CONTEXTUAL_CONFIDENCE = new BigDecimal("0.60");
    private static final BigDecimal AMBIGUOUS_CONFIDENCE = new BigDecimal("0.42");
    private static final BigDecimal UNKNOWN_CONFIDENCE = new BigDecimal("0.20");
    private static final List<ItemPurpose> PURPOSE_ORDER = List.of(
            ItemPurpose.PRODUCTION, ItemPurpose.RESALE, ItemPurpose.SERVICE_INPUT,
            ItemPurpose.BUSINESS_USE, ItemPurpose.ASSET);

    public ItemPurposeResolutionDecision resolve(BusinessUnderstandingSnapshot snapshot,
            UsageContext usageContext, List<ItemPurposeHint> hints) {
        if (snapshot == null || usageContext == null || hints == null) {
            throw new IllegalArgumentException("resolution context is required");
        }

        var evidence = new ArrayList<ItemPurposeResolutionEvidence>();
        var candidates = new LinkedHashSet<ItemPurpose>();
        var scores = new EnumMap<ItemPurpose, Integer>(ItemPurpose.class);
        var contextPurpose = purposeForUsageContext(usageContext);

        if (contextPurpose != null) {
            candidates.add(contextPurpose);
            score(scores, contextPurpose, 100);
            evidence.add(new ItemPurposeResolutionEvidence("USAGE_CONTEXT", contextPurpose,
                    "Usage context " + usageContext.value() + " identifies this purpose"));
        } else {
            evidence.add(new ItemPurposeResolutionEvidence("USAGE_CONTEXT", ItemPurpose.UNKNOWN,
                    "Usage context " + usageContext.value() + " does not decide the purpose"));
        }

        for (var activity : snapshot.activities()) {
            var purpose = purposeForActivity(activity);
            if (purpose != null) {
                // Activity is supporting evidence, never a product-name rule and
                // never enough to break a mixed resale/service/production case.
                score(scores, purpose, 10);
                evidence.add(new ItemPurposeResolutionEvidence("BUSINESS_ACTIVITY", purpose,
                        "Business activity " + activityLabel(activity) + " is compatible with this purpose"));
            } else {
                evidence.add(new ItemPurposeResolutionEvidence("BUSINESS_ACTIVITY", ItemPurpose.UNKNOWN,
                        "Business activity " + activityLabel(activity) + " does not decide the item purpose"));
            }
        }

        for (var operatingMode : snapshot.operatingModes()) {
            var purpose = purposeForOperatingMode(operatingMode.mode());
            if (purpose != null) {
                candidates.add(purpose);
                score(scores, purpose, 20);
                evidence.add(new ItemPurposeResolutionEvidence("OPERATING_MODE", purpose,
                        "Business mode " + operatingMode.mode().name() + " permits this purpose"));
            } else {
                evidence.add(new ItemPurposeResolutionEvidence("OPERATING_MODE", ItemPurpose.UNKNOWN,
                        "Business mode " + operatingMode.mode().name() + " is supporting context only"));
            }
        }

        var hintPurposes = EnumSet.noneOf(ItemPurpose.class);
        for (var hint : hints) {
            hintPurposes.add(hint.purpose());
            candidates.add(hint.purpose());
            score(scores, hint.purpose(), 80);
            evidence.add(new ItemPurposeResolutionEvidence("ITEM_HINT", hint.purpose(),
                    hint.source() + ": " + hint.reason()));
        }

        if (contextPurpose != null) {
            return suggested(contextPurpose, CONTEXT_CONFIDENCE, List.of(contextPurpose), evidence,
                    "SYSTEM_SUGGESTED");
        }

        if (hintPurposes.size() == 1) {
            var purpose = hintPurposes.iterator().next();
            return suggested(purpose, HINT_CONFIDENCE, List.of(purpose), evidence, "SYSTEM_SUGGESTED");
        }

        var orderedCandidates = PURPOSE_ORDER.stream().filter(candidates::contains).toList();
        if (orderedCandidates.size() == 1) {
            var purpose = orderedCandidates.getFirst();
            return suggested(purpose, CONTEXTUAL_CONFIDENCE, List.of(purpose), evidence,
                    "SYSTEM_SUGGESTED");
        }

        if (orderedCandidates.isEmpty()) {
            evidence.add(new ItemPurposeResolutionEvidence("RESOLUTION", ItemPurpose.UNKNOWN,
                    "No deterministic evidence identified a purpose; user confirmation is required"));
            return unknown(UNKNOWN_CONFIDENCE, List.of(), evidence, "UNKNOWN");
        }

        var ranked = orderedCandidates.stream()
                .sorted(Comparator.comparingInt((ItemPurpose purpose) -> scores.getOrDefault(purpose, 0))
                        .reversed().thenComparingInt(PURPOSE_ORDER::indexOf))
                .toList();
        evidence.add(new ItemPurposeResolutionEvidence("RESOLUTION", ItemPurpose.UNKNOWN,
                "Evidence supports multiple purposes; user confirmation is required"));
        return unknown(AMBIGUOUS_CONFIDENCE, ranked, evidence, "AMBIGUOUS");
    }

    private static ItemPurposeResolutionDecision suggested(ItemPurpose purpose, BigDecimal confidence,
            List<ItemPurpose> suggestions, List<ItemPurposeResolutionEvidence> evidence, String resolution) {
        return new ItemPurposeResolutionDecision(purpose, confidence, resolution,
                ItemPurposeAuthority.SYSTEM_SUGGESTED, true, suggestions, evidence);
    }

    private static ItemPurposeResolutionDecision unknown(BigDecimal confidence, List<ItemPurpose> suggestions,
            List<ItemPurposeResolutionEvidence> evidence, String resolution) {
        return new ItemPurposeResolutionDecision(ItemPurpose.UNKNOWN, confidence, resolution,
                ItemPurposeAuthority.UNKNOWN, true, suggestions, evidence);
    }

    private static void score(Map<ItemPurpose, Integer> scores, ItemPurpose purpose, int value) {
        scores.merge(purpose, value, Integer::sum);
    }

    private static ItemPurpose purposeForUsageContext(UsageContext context) {
        return switch (context.value()) {
            case "DIRECT_SALE" -> ItemPurpose.RESALE;
            case "SERVICE_CONSUMPTION" -> ItemPurpose.SERVICE_INPUT;
            case "PRODUCTION_INPUT" -> ItemPurpose.PRODUCTION;
            case "INTERNAL_USE" -> ItemPurpose.BUSINESS_USE;
            case "ASSET_ACQUISITION" -> ItemPurpose.ASSET;
            default -> null;
        };
    }

    private static ItemPurpose purposeForOperatingMode(OperatingMode mode) {
        return switch (mode) {
            case RESELLS_GOODS -> ItemPurpose.RESALE;
            case PRODUCES_GOODS -> ItemPurpose.PRODUCTION;
            case PROVIDES_SERVICES -> ItemPurpose.SERVICE_INPUT;
            case BUYS_INPUTS -> null;
        };
    }

    private static ItemPurpose purposeForActivity(BusinessActivity activity) {
        return switch (activity.code()) {
            case MERCADINHO, ACOUGUE, VERDUREIRA -> ItemPurpose.RESALE;
            case PADARIA, CONFEITARIA, RESTAURANTE, LANCHONETE, ENCOMENDAS -> ItemPurpose.PRODUCTION;
            case SALAO_BELEZA, OFICINA -> ItemPurpose.SERVICE_INPUT;
            case OTHER -> null;
        };
    }

    private static String activityLabel(BusinessActivity activity) {
        return activity.code() == ActivityCode.OTHER
                ? activity.customLabel()
                : activity.code().name();
    }
}
