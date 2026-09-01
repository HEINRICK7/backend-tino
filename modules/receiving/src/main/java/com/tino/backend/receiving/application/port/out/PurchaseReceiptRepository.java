package com.tino.backend.receiving.application.port.out;

import com.tino.backend.receiving.application.model.PurchaseDocument;
import com.tino.backend.receiving.application.model.PurchaseDocumentMatch;
import com.tino.backend.receiving.application.model.PurchasePreviewSnapshot;
import com.tino.backend.shared.kernel.BusinessId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Atomic persistence boundary for the operational PurchaseDocument confirmation. */
public interface PurchaseReceiptRepository {
    Optional<ConfirmationIdempotency> findConfirmationIdempotency(BusinessId businessId, String idempotencyKey);

    void recordConfirmationIdempotency(BusinessId businessId, String idempotencyKey, UUID previewId,
            String requestFingerprint, UUID receiptId, Instant now);

    Optional<PurchasePreviewSnapshot> findPreviewForUpdate(BusinessId businessId, UUID previewId);

    Optional<PurchaseReceiptResult> findReceiptByPreview(BusinessId businessId, UUID previewId);

    void createReceipt(BusinessId businessId, UUID receiptId, UUID documentId, UUID previewId,
            UUID userId, Instant now);

    void addReceiptItem(BusinessId businessId, UUID receiptId, PurchaseDocument.Item source,
            PurchaseDocumentMatch match, UUID productId, String matchStatus, String baseUnit,
            BigDecimal conversionFactor, BigDecimal stockQuantity);

    void addInventoryMovement(BusinessId businessId, UUID receiptId, UUID productId,
            BigDecimal quantity, BigDecimal unitCost, Instant now);

    void addPriceObservation(BusinessId businessId, UUID receiptId, UUID productId,
            PurchaseDocument document, PurchaseDocument.Item item, Instant now);

    void addEvent(BusinessId businessId, UUID receiptId, String eventType, String payload, Instant now);

    void markPreviewConfirmed(BusinessId businessId, UUID previewId, Instant now);

    Optional<PurchaseReceiptResult> findReceipt(BusinessId businessId, UUID receiptId);

    record PurchaseReceiptResult(UUID receiptId, String status, int itemCount,
            java.util.List<PurchaseReceiptItemResult> items) {
        public PurchaseReceiptResult {
            items = java.util.List.copyOf(items);
        }
    }

    record PurchaseReceiptItemResult(int lineNumber, UUID productId, String matchStatus,
            BigDecimal stockQuantity, BigDecimal unitCost) {}

    record ConfirmationIdempotency(UUID previewId, String requestFingerprint, UUID receiptId) {}
}
