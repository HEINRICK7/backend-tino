package com.tino.backend.receiving.application.port.out;

import com.tino.backend.shared.kernel.BusinessId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseHistoryRepository {
    List<PurchaseHistoryEntry> findEntries(BusinessId businessId, Instant from, Instant to);

    Optional<PurchaseHistoryDetail> findDetail(BusinessId businessId, UUID receiptId);

    List<PurchasePriceFact> findPriceFacts(BusinessId businessId, Instant from, Instant to);

    record PurchaseHistoryEntry(UUID receiptId, Instant confirmedAt, String issuerName, BigDecimal total,
            int itemCount, int newProductCount, BigDecimal stockQuantity) {}

    record PurchaseHistoryDetail(UUID receiptId, Instant confirmedAt, String issuerName, String issuerTaxId,
            String accessKey, BigDecimal total, List<PurchaseHistoryItem> items) {}

    record PurchaseHistoryItem(int lineNumber, UUID productId, String description, BigDecimal quantity,
            String unit, BigDecimal unitPrice, BigDecimal stockQuantity, String matchStatus) {}

    record PurchasePriceFact(UUID observationId, UUID receiptId, UUID productId, String productName,
            BigDecimal unitPrice, BigDecimal quantity, String unit, Instant observedAt, BigDecimal salePrice) {}
}
