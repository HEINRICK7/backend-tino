package com.tino.backend.receiving.application.port.out;

import com.tino.backend.catalog.application.model.ProductResolution;
import com.tino.backend.receiving.application.model.PreviewItem;
import com.tino.backend.receiving.application.model.PreviewSnapshot;
import com.tino.backend.receiving.application.model.GoodsReceiptResult;
import com.tino.backend.shared.kernel.BusinessId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReceivingRepository {
    PreviewSnapshot createPreview(BusinessId businessId, UUID documentId, List<PreviewItem> items, Instant now);
    Optional<PreviewSnapshot> findPreview(BusinessId businessId, UUID previewId, boolean lock);
    Optional<PreviewSnapshot> findPreviewByDocument(BusinessId businessId, UUID documentId);
    Optional<GoodsReceiptResult> findReceiptByPreview(BusinessId businessId, UUID previewId);
    Optional<GoodsReceiptResult> findReceipt(BusinessId businessId, UUID receiptId);
    void createReceipt(BusinessId businessId, UUID receiptId, UUID documentId, UUID previewId, UUID userId, Instant now);
    void addReceiptItem(BusinessId businessId, UUID receiptId, int lineNumber, UUID productId,
            BigDecimal purchaseQuantity, BigDecimal conversionFactor, BigDecimal stockQuantity, BigDecimal unitCost);
    void markPreviewConfirmed(BusinessId businessId, UUID previewId, Instant now);
}
