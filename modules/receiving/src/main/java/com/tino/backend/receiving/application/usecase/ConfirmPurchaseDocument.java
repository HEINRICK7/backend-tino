package com.tino.backend.receiving.application.usecase;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.catalog.application.port.out.ProductCatalog;
import com.tino.backend.receiving.application.exception.ReceivingErrorCode;
import com.tino.backend.receiving.application.exception.ReceivingException;
import com.tino.backend.receiving.application.model.PurchaseDocument;
import com.tino.backend.receiving.application.model.PurchaseDocumentMatch;
import com.tino.backend.receiving.application.model.PurchasePreviewSnapshot;
import com.tino.backend.receiving.application.port.out.PurchaseDocumentProductLookup;
import com.tino.backend.receiving.application.port.out.PurchaseReceiptRepository;
import com.tino.backend.shared.kernel.BusinessId;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.transaction.annotation.Transactional;

/** Confirms one preview exactly once and records every operational consequence atomically. */
public class ConfirmPurchaseDocument {
    private final BusinessAuthorization authorization;
    private final PurchaseReceiptRepository receipts;
    private final ProductCatalog catalog;
    private final PurchaseDocumentProductLookup products;
    private final Clock clock;

    public ConfirmPurchaseDocument(BusinessAuthorization authorization, PurchaseReceiptRepository receipts,
            ProductCatalog catalog, PurchaseDocumentProductLookup products, Clock clock) {
        this.authorization = Objects.requireNonNull(authorization);
        this.receipts = Objects.requireNonNull(receipts);
        this.catalog = Objects.requireNonNull(catalog);
        this.products = Objects.requireNonNull(products);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional
    public PurchaseReceiptRepository.PurchaseReceiptResult execute(UUID userId, BusinessId businessId,
            UUID previewId, long expectedVersion, List<Decision> decisions, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 200) {
            throw invalid(ReceivingErrorCode.INVALID_REQUEST,
                    "Idempotency-Key is required and must be at most 200 characters", 400);
        }
        var fingerprint = fingerprint(previewId, expectedVersion, decisions);
        return authorization.execute(userId, businessId, authorized -> confirmAuthorized(
                userId, authorized, previewId, expectedVersion, decisions, idempotencyKey, fingerprint));
    }

    private PurchaseReceiptRepository.PurchaseReceiptResult confirmAuthorized(UUID userId, BusinessId businessId,
            UUID previewId, long expectedVersion, List<Decision> decisions, String idempotencyKey,
            String fingerprint) {
        var storedKey = receipts.findConfirmationIdempotency(businessId, idempotencyKey);
        if (storedKey.isPresent()) {
            if (!previewId.equals(storedKey.get().previewId()) || !fingerprint.equals(storedKey.get().requestFingerprint())) {
                throw invalid(ReceivingErrorCode.IDEMPOTENCY_CONFLICT,
                        "Idempotency-Key was already used for another confirmation", 409);
            }
            return receipts.findReceipt(businessId, storedKey.get().receiptId()).orElseThrow();
        }
        var existing = receipts.findReceiptByPreview(businessId, previewId);
        if (existing.isPresent()) return recordKeyAndReturn(businessId, previewId, idempotencyKey, fingerprint, existing.get());
        var preview = receipts.findPreviewForUpdate(businessId, previewId).orElseThrow(() -> invalid(
                ReceivingErrorCode.NFE_NOT_FOUND, "preview not found", 404));
        existing = receipts.findReceiptByPreview(businessId, previewId);
        if (existing.isPresent()) return recordKeyAndReturn(businessId, previewId, idempotencyKey, fingerprint, existing.get());
        if (preview.version() != expectedVersion || !"REVIEW_READY".equals(preview.status())) {
            throw invalid(ReceivingErrorCode.STALE_PREVIEW, "preview is obsolete or not confirmable", 409);
        }

        var byLine = decisionsByLine(decisions, preview);
        var receiptId = UUID.randomUUID();
        var now = Instant.now(clock);
        receipts.createReceipt(businessId, receiptId, preview.documentId(), previewId, userId, now);
        var inventoryByProduct = new HashMap<UUID, InventoryLine>();
        for (var item : preview.document().items()) {
            var match = preview.matches().stream().filter(value -> value.lineNumber() == item.lineNumber())
                    .findFirst().orElseThrow(() -> invalid(ReceivingErrorCode.INVALID_REQUEST, "preview match is missing", 400));
            var decision = byLine.get(item.lineNumber());
            if (decision != null && decision.action() == Action.IGNORE) {
                receipts.addReceiptItem(businessId, receiptId, item, match, null, "IGNORED", null, null, null);
                continue;
            }
            var productId = decision == null ? match.productId() : decision.productId();
            var matchStatus = match.status().name();
            var baseUnit = decision == null ? match.baseUnit() : decision.baseUnit();
            if (decision != null && decision.action() == Action.CREATE_PRODUCT) {
                requireText(item.rawDescription(), "product name");
                baseUnit = requiredUnit(decision.baseUnit(), item.unit());
                productId = catalog.create(businessId, item.rawDescription(), baseUnit, item.gtin(), now);
                matchStatus = PurchaseDocumentMatch.Status.NEW_PRODUCT.name();
            } else if (decision != null && decision.action() == Action.USE_EXISTING) {
                if (decision.productId() == null || products.findById(businessId, decision.productId()).isEmpty()) {
                    throw invalid(ReceivingErrorCode.INVALID_PRODUCT_SELECTION, "selected product is not available", 400);
                }
                productId = decision.productId();
            } else if (match.requiresUserAction()) {
                throw invalid(ReceivingErrorCode.INVALID_PRODUCT_SELECTION, "each pending item needs a product decision", 400);
            }
            if (productId == null) throw invalid(ReceivingErrorCode.INVALID_PRODUCT_SELECTION, "each item needs a product decision", 400);
            var quantity = requiredPositive(item.quantity(), "item quantity");
            var unitPrice = requiredNonNegative(item.unitPrice(), "item unit price");
            baseUnit = requiredUnit(baseUnit, item.unit());
            var factor = decision == null ? null : decision.conversionFactor();
            if (factor == null && baseUnit.equalsIgnoreCase(requiredUnit(null, item.unit()))) factor = BigDecimal.ONE;
            if (factor == null || factor.signum() <= 0) throw invalid(ReceivingErrorCode.PACKAGING_CONVERSION_REQUIRED,
                    "a positive packaging conversion is required", 400);
            var stockQuantity = quantity.multiply(factor);
            receipts.addReceiptItem(businessId, receiptId, item, match, productId, matchStatus, baseUnit, factor, stockQuantity);
            inventoryByProduct.merge(productId, new InventoryLine(stockQuantity, unitPrice.divide(factor, 9, java.math.RoundingMode.HALF_UP)),
                    InventoryLine::plus);
            receipts.addPriceObservation(businessId, receiptId, productId, preview.document(), item, now);
            var issuerTaxId = digits(preview.document().issuer().taxId());
            if (item.externalCode() != null && issuerTaxId != null && !issuerTaxId.isBlank()) {
                catalog.mapSupplier(businessId, issuerTaxId, item.externalCode().trim(), productId, now);
                if (!baseUnit.equalsIgnoreCase(item.unit())) {
                    catalog.confirmConversion(businessId, issuerTaxId, item.externalCode().trim(),
                            requiredUnit(null, item.unit()), baseUnit, factor, now);
                }
            }
        }
        inventoryByProduct.forEach((productId, value) -> receipts.addInventoryMovement(
                businessId, receiptId, productId, value.quantity(), value.unitCost(), now));
        receipts.addEvent(businessId, receiptId, "purchase.receiving.confirmed",
                "{\"preview_id\":\"" + previewId + "\",\"version\":" + expectedVersion + "}", now);
        receipts.markPreviewConfirmed(businessId, previewId, now);
        var result = receipts.findReceipt(businessId, receiptId).orElseThrow();
        return recordKeyAndReturn(businessId, previewId, idempotencyKey, fingerprint, result);
    }

    private PurchaseReceiptRepository.PurchaseReceiptResult recordKeyAndReturn(BusinessId businessId, UUID previewId,
            String idempotencyKey, String fingerprint, PurchaseReceiptRepository.PurchaseReceiptResult result) {
        receipts.recordConfirmationIdempotency(businessId, idempotencyKey, previewId, fingerprint, result.receiptId(), Instant.now(clock));
        var stored = receipts.findConfirmationIdempotency(businessId, idempotencyKey).orElseThrow();
        if (!previewId.equals(stored.previewId()) || !fingerprint.equals(stored.requestFingerprint())) {
            throw invalid(ReceivingErrorCode.IDEMPOTENCY_CONFLICT,
                    "Idempotency-Key was already used for another confirmation", 409);
        }
        return receipts.findReceipt(businessId, stored.receiptId()).orElseThrow();
    }

    private static String fingerprint(UUID previewId, long expectedVersion, List<Decision> decisions) {
        var canonical = new StringBuilder().append(previewId).append('|').append(expectedVersion).append('|');
        if (decisions != null) decisions.stream().filter(Objects::nonNull)
                .sorted(java.util.Comparator.comparingInt(Decision::lineNumber))
                .forEach(decision -> canonical.append(decision.lineNumber()).append(':').append(decision.action()).append(':')
                        .append(decision.productId()).append(':').append(decision.conversionFactor()).append(':')
                        .append(decision.baseUnit()).append(';'));
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static Map<Integer, Decision> decisionsByLine(List<Decision> decisions, PurchasePreviewSnapshot preview) {
        var values = decisions == null ? List.<Decision>of() : decisions;
        var validLines = preview.document().items().stream().map(PurchaseDocument.Item::lineNumber).collect(java.util.stream.Collectors.toSet());
        var result = new HashMap<Integer, Decision>();
        for (var decision : values) {
            if (decision == null || decision.lineNumber() < 1 || !validLines.contains(decision.lineNumber())
                    || result.put(decision.lineNumber(), decision) != null) {
                throw invalid(ReceivingErrorCode.INVALID_PRODUCT_SELECTION, "confirmation contains an invalid item decision", 400);
            }
        }
        return result;
    }

    private static String requiredUnit(String preferred, String fallback) {
        var value = preferred == null || preferred.isBlank() ? fallback : preferred;
        if (value == null || value.isBlank()) throw invalid(ReceivingErrorCode.INVALID_REQUEST, "item unit is required", 400);
        return value.trim();
    }

    private static BigDecimal requiredPositive(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0) throw invalid(ReceivingErrorCode.INVALID_REQUEST, field + " is required and must be positive", 400);
        return value;
    }

    private static BigDecimal requiredNonNegative(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) throw invalid(ReceivingErrorCode.INVALID_REQUEST, field + " is required and must not be negative", 400);
        return value;
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) throw invalid(ReceivingErrorCode.INVALID_REQUEST, field + " is required", 400);
    }

    private static String digits(String value) { return value == null ? null : value.replaceAll("\\D", ""); }

    private static ReceivingException invalid(ReceivingErrorCode code, String message, int status) {
        return new ReceivingException(code, message, false, status);
    }

    public record Decision(int lineNumber, Action action, UUID productId,
            BigDecimal conversionFactor, String baseUnit) {}

    public enum Action { USE_EXISTING, CREATE_PRODUCT, IGNORE }

    private record InventoryLine(BigDecimal quantity, BigDecimal unitCost) {
        private InventoryLine plus(InventoryLine other) {
            var totalQuantity = quantity.add(other.quantity);
            return new InventoryLine(totalQuantity, unitCost.multiply(quantity).add(other.unitCost.multiply(other.quantity))
                    .divide(totalQuantity, 9, java.math.RoundingMode.HALF_UP));
        }
    }
}
