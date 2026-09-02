package com.tino.backend.receiving.application.usecase;

import com.tino.backend.receiving.application.model.PurchaseDocument;
import com.tino.backend.receiving.application.model.PurchaseDocumentMatch;
import com.tino.backend.receiving.application.port.out.PurchaseDocumentProductLookup;
import com.tino.backend.receiving.application.port.out.PurchaseDocumentProductLookup.ProductCandidate;
import com.tino.backend.shared.kernel.BusinessId;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Deterministic, conservative matching. It never creates or mutates catalog data. */
public final class PurchaseDocumentMatcher {
    private final PurchaseDocumentProductLookup catalog;

    public PurchaseDocumentMatcher(PurchaseDocumentProductLookup catalog) {
        this.catalog = Objects.requireNonNull(catalog);
    }

    public List<PurchaseDocumentMatch> match(BusinessId businessId, PurchaseDocument document) {
        var candidates = catalog.findActive(businessId);
        return document.items().stream().map(item -> matchItem(businessId, document, item, candidates)).toList();
    }

    private PurchaseDocumentMatch matchItem(BusinessId businessId, PurchaseDocument document,
            PurchaseDocument.Item item, List<ProductCandidate> activeProducts) {
        var gtin = usableGtin(item.gtin());
        if (gtin != null) {
            var exactGtin = catalog.findByGtin(businessId, gtin);
            if (exactGtin.size() == 1) return resolved(item, PurchaseDocumentMatch.Status.EXACT_MATCH,
                    exactGtin.get(0), new BigDecimal("1.0000"), false);
            if (exactGtin.size() > 1) return review(item, exactGtin.get(0), new BigDecimal("1.0000"));
        }

        var issuerTaxId = digits(document.issuer().taxId());
        if (issuerTaxId != null && item.externalCode() != null) {
            var learned = catalog.findByIssuerAndExternalCode(businessId, issuerTaxId, item.externalCode().trim());
            if (learned.isPresent()) return resolved(item, PurchaseDocumentMatch.Status.HIGH_CONFIDENCE_MATCH,
                    learned.get(), new BigDecimal("0.9500"), false);
        }

        var normalizedDescription = normalize(item.rawDescription());
        var exactDescription = activeProducts.stream()
                .filter(candidate -> descriptionTexts(candidate).stream().anyMatch(normalizedDescription::equals))
                .toList();
        if (exactDescription.size() == 1) return resolved(item, PurchaseDocumentMatch.Status.HIGH_CONFIDENCE_MATCH,
                exactDescription.get(0), new BigDecimal("0.8500"), false);
        if (exactDescription.size() > 1) return review(item, exactDescription.get(0), new BigDecimal("0.8500"));

        var nearest = activeProducts.stream()
                .map(candidate -> new Scored(candidate, descriptionTexts(candidate).stream()
                        .mapToDouble(value -> similarity(normalizedDescription, value)).max().orElse(0)))
                .filter(value -> value.score() > 0)
                .max(Comparator.comparing(Scored::score).thenComparing(value -> value.candidate().productId().toString()))
                .orElse(null);
        if (nearest != null) return review(item, nearest.candidate(), decimal(nearest.score()));
        return new PurchaseDocumentMatch(item.lineNumber(), PurchaseDocumentMatch.Status.NEW_PRODUCT, null,
                item.rawDescription(), item.unit(), null, true);
    }

    private static PurchaseDocumentMatch resolved(PurchaseDocument.Item item, PurchaseDocumentMatch.Status status,
            ProductCandidate candidate, BigDecimal confidence, boolean requiresAction) {
        return new PurchaseDocumentMatch(item.lineNumber(), status, candidate.productId(), candidate.name(),
                candidate.baseUnit(), confidence, requiresAction);
    }

    private static PurchaseDocumentMatch review(PurchaseDocument.Item item, ProductCandidate candidate,
            BigDecimal confidence) {
        return resolved(item, PurchaseDocumentMatch.Status.REVIEW_REQUIRED, candidate, confidence, true);
    }

    private static String usableGtin(String value) {
        if (value == null || value.isBlank()) return null;
        var normalized = digits(value);
        return normalized != null && normalized.matches("(?:\\d{8}|\\d{12,14})") && validGtin(normalized)
                ? normalized : null;
    }

    private static boolean validGtin(String value) {
        var sum = 0;
        var weight = 3;
        for (var index = value.length() - 2; index >= 0; index--, weight = 4 - weight) {
            sum += Character.digit(value.charAt(index), 10) * weight;
        }
        return (10 - sum % 10) % 10 == Character.digit(value.charAt(value.length() - 1), 10);
    }

    private static String digits(String value) {
        if (value == null) return null;
        var normalized = value.replaceAll("\\D", "");
        return normalized.isBlank() ? null : normalized;
    }

    static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " ");
    }

    private static List<String> descriptionTexts(ProductCandidate candidate) {
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(candidate.name()), candidate.aliases().stream())
                .map(PurchaseDocumentMatcher::normalize).filter(value -> !value.isBlank()).toList();
    }

    private static double similarity(String left, String right) {
        if (left.isBlank() || right.isBlank()) return 0;
        var leftTokens = List.of(left.split(" "));
        var rightTokens = List.of(right.split(" "));
        var intersection = leftTokens.stream().filter(rightTokens::contains).distinct().count();
        var union = java.util.stream.Stream.concat(leftTokens.stream(), rightTokens.stream()).distinct().count();
        return union == 0 ? 0 : (double) intersection / union;
    }

    private static BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }

    private record Scored(ProductCandidate candidate, double score) {}
}
