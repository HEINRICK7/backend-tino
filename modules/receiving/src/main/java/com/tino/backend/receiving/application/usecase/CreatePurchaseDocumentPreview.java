package com.tino.backend.receiving.application.usecase;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.receiving.application.exception.ReceivingErrorCode;
import com.tino.backend.receiving.application.exception.ReceivingException;
import com.tino.backend.receiving.application.model.PurchaseDocument;
import com.tino.backend.receiving.application.model.PurchasePreviewSnapshot;
import com.tino.backend.receiving.application.port.out.PurchaseReceivingRepository;
import com.tino.backend.shared.kernel.BusinessId;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Creates a tenant-scoped, non-operational preview from a canonical purchase. */
public final class CreatePurchaseDocumentPreview {
    private final BusinessAuthorization authorization;
    private final PurchaseReceivingRepository repository;
    private final PurchaseDocumentMatcher matcher;
    private final Clock clock;

    public CreatePurchaseDocumentPreview(BusinessAuthorization authorization,
            PurchaseReceivingRepository repository, PurchaseDocumentMatcher matcher, Clock clock) {
        this.authorization = Objects.requireNonNull(authorization);
        this.repository = Objects.requireNonNull(repository);
        this.matcher = Objects.requireNonNull(matcher);
        this.clock = Objects.requireNonNull(clock);
    }

    public PurchasePreviewSnapshot execute(UUID userId, BusinessId businessId,
            PurchaseDocument input, String idempotencyKey) {
        requireIdempotencyKey(idempotencyKey);
        var document = PurchaseDocumentValidator.validate(input);
        var fingerprint = PurchaseDocumentFingerprint.sha256(document);
        return authorization.execute(userId, businessId, authorized -> {
            var existingKey = repository.findIdempotency(authorized, idempotencyKey);
            if (existingKey.isPresent()) {
                var value = existingKey.get();
                if (!value.accessKey().equals(document.accessKey())
                        || !value.requestFingerprint().equals(fingerprint)) {
                    throw new ReceivingException(ReceivingErrorCode.IDEMPOTENCY_CONFLICT,
                            "Idempotency-Key was already used for another purchase document", false, 409);
                }
                return repository.findPreview(authorized, value.previewId()).orElseThrow(() ->
                        new ReceivingException(ReceivingErrorCode.NFE_NOT_FOUND, "preview not found", false, 404));
            }
            var existingDocument = repository.findDocumentByAccessKey(authorized, document.accessKey());
            if (existingDocument.isPresent() && !existingDocument.get().payloadSha256().equals(fingerprint)) {
                throw new ReceivingException(ReceivingErrorCode.IDEMPOTENCY_CONFLICT,
                        "access key already belongs to another purchase document payload", false, 409);
            }
            var matches = matcher.match(authorized, document);
            return repository.createPreview(authorized, document, fingerprint, idempotencyKey, Instant.now(clock), matches);
        });
    }

    private static void requireIdempotencyKey(String value) {
        if (value == null || value.isBlank() || value.length() > 200) {
            throw new ReceivingException(ReceivingErrorCode.INVALID_REQUEST,
                    "Idempotency-Key is required and must be at most 200 characters", false, 400);
        }
    }
}
