package com.tino.backend.receiving.application.port.out;

import com.tino.backend.receiving.application.model.PurchaseDocument;
import com.tino.backend.receiving.application.model.PurchasePreviewSnapshot;
import com.tino.backend.shared.kernel.BusinessId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseReceivingRepository {
    Optional<PreviewIdempotency> findIdempotency(BusinessId businessId, String idempotencyKey);

    Optional<PurchaseDocumentRecord> findDocumentByAccessKey(BusinessId businessId, String accessKey);

    Optional<PurchasePreviewSnapshot> findPreview(BusinessId businessId, UUID previewId);

    PurchasePreviewSnapshot createPreview(BusinessId businessId, PurchaseDocument document,
            String payloadSha256, String idempotencyKey, Instant now,
            java.util.List<com.tino.backend.receiving.application.model.PurchaseDocumentMatch> matches);

    record PreviewIdempotency(String accessKey, String requestFingerprint, UUID previewId) {}

    record PurchaseDocumentRecord(UUID documentId, String accessKey, String payloadSha256) {}
}
