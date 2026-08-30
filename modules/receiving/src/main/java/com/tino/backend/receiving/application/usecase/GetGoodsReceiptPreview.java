package com.tino.backend.receiving.application.usecase;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.receiving.application.exception.ReceivingException;
import com.tino.backend.receiving.application.model.PreviewSnapshot;
import com.tino.backend.receiving.application.port.out.ReceivingRepository;
import com.tino.backend.shared.kernel.BusinessId;
import java.util.Objects;
import java.util.UUID;

public final class GetGoodsReceiptPreview {
    private final BusinessAuthorization authorization; private final ReceivingRepository previews;
    public GetGoodsReceiptPreview(BusinessAuthorization authorization, ReceivingRepository previews) { this.authorization = Objects.requireNonNull(authorization); this.previews = Objects.requireNonNull(previews); }
    public PreviewSnapshot execute(UUID userId, BusinessId businessId, UUID previewId) { return authorization.execute(userId, businessId, authorized -> previews.findPreview(authorized, previewId, false).orElseThrow(() -> new ReceivingException("preview not found"))); }
    public PreviewSnapshot executeByDocument(UUID userId, BusinessId businessId, UUID documentId) { return authorization.execute(userId, businessId, authorized -> previews.findPreviewByDocument(authorized, documentId).orElseThrow(() -> new ReceivingException("preview not found"))); }
}
