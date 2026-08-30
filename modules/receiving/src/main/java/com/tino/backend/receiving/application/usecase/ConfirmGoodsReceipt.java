package com.tino.backend.receiving.application.usecase;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.catalog.application.port.out.ProductCatalog;
import com.tino.backend.fiscal.application.port.in.NfeReader;
import com.tino.backend.fiscal.domain.model.FiscalStatus;
import com.tino.backend.receiving.application.exception.ReceivingException;
import com.tino.backend.receiving.application.exception.ReceivingErrorCode;
import com.tino.backend.receiving.application.model.GoodsReceiptResult;
import com.tino.backend.receiving.application.model.PreviewItem;
import com.tino.backend.receiving.application.port.out.ReceivingRepository;
import com.tino.backend.inventory.application.port.out.InventoryPort;
import com.tino.backend.shared.kernel.BusinessId;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ConfirmGoodsReceipt {
    private final BusinessAuthorization authorization; private final NfeReader fiscal; private final ProductCatalog catalog;
    private final ReceivingRepository receiving; private final InventoryPort inventory; private final Clock clock;
    public ConfirmGoodsReceipt(BusinessAuthorization authorization, NfeReader fiscal, ProductCatalog catalog, ReceivingRepository receiving, InventoryPort inventory, Clock clock) {
        this.authorization = Objects.requireNonNull(authorization); this.fiscal = Objects.requireNonNull(fiscal); this.catalog = Objects.requireNonNull(catalog); this.receiving = Objects.requireNonNull(receiving); this.inventory = Objects.requireNonNull(inventory); this.clock = Objects.requireNonNull(clock);
    }
    public GoodsReceiptResult execute(UUID userId, BusinessId businessId, UUID previewId, long expectedVersion, List<Decision> decisions) {
        return authorization.execute(userId, businessId, authorized -> {
            var preview = receiving.findPreview(authorized, previewId, true).orElseThrow(() -> new ReceivingException(
                    ReceivingErrorCode.NFE_NOT_FOUND, "preview not found", false, 404));
            var existing = receiving.findReceiptByPreview(authorized, previewId); if (existing.isPresent()) return existing.get();
            if (preview.version() != expectedVersion) throw new ReceivingException(ReceivingErrorCode.STALE_PREVIEW, "preview is obsolete or not confirmable");
            if (preview.status() != com.tino.backend.receiving.application.model.GoodsReceiptPreviewStatus.READY
                    && preview.status() != com.tino.backend.receiving.application.model.GoodsReceiptPreviewStatus.REVIEW_REQUIRED) {
                throw new ReceivingException(ReceivingErrorCode.STALE_PREVIEW, "preview is obsolete or not confirmable");
            }
            var document = fiscal.find(authorized, preview.documentId()).orElseThrow(() -> new ReceivingException(
                    ReceivingErrorCode.NFE_NOT_FOUND, "fiscal document not found", false, 404));
            if (document.fiscalStatus() == FiscalStatus.CANCELLED) throw new ReceivingException(ReceivingErrorCode.FISCAL_CANCELLED, "cancelled fiscal document cannot enter stock");
            if (document.fiscalStatus() == FiscalStatus.DENIED) throw new ReceivingException(ReceivingErrorCode.FISCAL_DENIED, "denied fiscal document cannot enter stock");
            var receiptId = UUID.randomUUID(); var now = Instant.now(clock); receiving.createReceipt(authorized, receiptId, preview.documentId(), previewId, userId, now);
            for (var item : preview.items()) {
                var decision = decisions.stream().filter(value -> value.lineNumber() == item.lineNumber()).findFirst().orElse(new Decision(item.lineNumber(), Action.IGNORE, null, null, null));
                if (decision.action() == Action.IGNORE) { receiving.addReceiptItem(authorized, receiptId, item.lineNumber(), null, item.purchaseQuantity(), null, null, item.unitCost()); continue; }
                var productId = item.productId(); var baseUnit = item.baseUnit(); var factor = item.conversionFactor();
                var source = document.document().items().stream().filter(value -> value.lineNumber() == item.lineNumber()).findFirst().orElseThrow();
                if (decision.action() == Action.CREATE_PRODUCT) productId = catalog.create(authorized, source.description(), decision.baseUnit() == null ? item.purchaseUnit() : decision.baseUnit(), source.gtin(), now);
                if (decision.action() == Action.USE_EXISTING) {
                    if (decision.productId() == null) throw new ReceivingException(
                            ReceivingErrorCode.INVALID_PRODUCT_SELECTION, "USE_EXISTING requires productId");
                    productId = decision.productId();
                }
                if (productId == null) throw new ReceivingException(ReceivingErrorCode.INVALID_PRODUCT_SELECTION, "each non-ignored item needs a product decision");
                baseUnit = decision.baseUnit() == null ? baseUnit : decision.baseUnit(); factor = decision.conversionFactor() == null ? factor : decision.conversionFactor();
                if (factor == null) { if (baseUnit != null && baseUnit.equalsIgnoreCase(item.purchaseUnit())) factor = BigDecimal.ONE; else throw new ReceivingException(ReceivingErrorCode.PACKAGING_CONVERSION_REQUIRED, "packaging conversion is required"); }
                if (factor.signum() <= 0) throw new ReceivingException(ReceivingErrorCode.PACKAGING_CONVERSION_REQUIRED, "conversion factor must be positive");
                if (baseUnit == null || baseUnit.isBlank()) throw new ReceivingException(
                        ReceivingErrorCode.PACKAGING_CONVERSION_REQUIRED, "baseUnit is required");
                if (source.supplierProductCode() != null && document.document().issuer().document() != null) { catalog.mapSupplier(authorized, document.document().issuer().document(), source.supplierProductCode(), productId, now); if (!baseUnit.equalsIgnoreCase(item.purchaseUnit())) catalog.confirmConversion(authorized, document.document().issuer().document(), source.supplierProductCode(), item.purchaseUnit(), baseUnit, factor, now); }
                var stockQuantity = item.purchaseQuantity().multiply(factor); receiving.addReceiptItem(authorized, receiptId, item.lineNumber(), productId, item.purchaseQuantity(), factor, stockQuantity, item.unitCost()); inventory.receive(authorized, receiptId, productId, stockQuantity, item.unitCost().divide(factor, 9, java.math.RoundingMode.HALF_UP), now);
            }
            receiving.markPreviewConfirmed(authorized, previewId, now);
            return receiving.findReceipt(authorized, receiptId).orElseThrow();
        });
    }
    public record Decision(int lineNumber, Action action, UUID productId, BigDecimal conversionFactor, String baseUnit) {}
    public enum Action { USE_EXISTING, CREATE_PRODUCT, IGNORE }
}
