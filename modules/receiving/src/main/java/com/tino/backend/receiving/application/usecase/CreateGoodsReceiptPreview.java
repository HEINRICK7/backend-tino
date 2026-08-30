package com.tino.backend.receiving.application.usecase;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.catalog.application.model.ProductResolution;
import com.tino.backend.catalog.application.port.out.ProductCatalog;
import com.tino.backend.fiscal.application.port.in.NfeReader;
import com.tino.backend.fiscal.domain.model.FiscalStatus;
import com.tino.backend.receiving.application.exception.ReceivingException;
import com.tino.backend.receiving.application.model.PreviewItem;
import com.tino.backend.receiving.application.model.PreviewSnapshot;
import com.tino.backend.receiving.application.port.out.ReceivingRepository;
import com.tino.backend.shared.kernel.BusinessId;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class CreateGoodsReceiptPreview {
    private final BusinessAuthorization authorization; private final NfeReader fiscal; private final ProductCatalog catalog;
    private final ReceivingRepository previews; private final Clock clock;
    public CreateGoodsReceiptPreview(BusinessAuthorization authorization, NfeReader fiscal, ProductCatalog catalog, ReceivingRepository previews, Clock clock) {
        this.authorization = Objects.requireNonNull(authorization); this.fiscal = Objects.requireNonNull(fiscal); this.catalog = Objects.requireNonNull(catalog); this.previews = Objects.requireNonNull(previews); this.clock = Objects.requireNonNull(clock);
    }
    public PreviewSnapshot execute(UUID userId, BusinessId businessId, UUID documentId) {
        return authorization.execute(userId, businessId, authorized -> {
            var existingPreview = previews.findPreviewByDocument(authorized, documentId);
            if (existingPreview.isPresent()) return existingPreview.get();
            var document = fiscal.find(authorized, documentId).orElseThrow(() -> new ReceivingException("fiscal document not found"));
            if (document.retrievalStatus() != com.tino.backend.fiscal.domain.model.RetrievalStatus.SUCCESS || document.document() == null) throw new ReceivingException("fiscal document is not ready");
            if (document.fiscalStatus() == FiscalStatus.CANCELLED || document.fiscalStatus() == FiscalStatus.DENIED) throw new ReceivingException("fiscal document cannot enter stock");
            var items = document.document().items().stream().map(item -> {
                var resolution = catalog.resolve(authorized, document.document().issuer().document(), item);
                var factor = resolution.status() == ProductResolution.Status.MATCHED && resolution.baseUnit().equalsIgnoreCase(item.commercialUnit()) ? BigDecimal.ONE : null;
                var baseUnit = resolution.status() == ProductResolution.Status.MATCHED ? resolution.baseUnit() : null;
                if (factor == null && resolution.status() == ProductResolution.Status.MATCHED) factor = catalog.conversion(authorized, document.document().issuer().document(), item.supplierProductCode(), item.commercialUnit(), baseUnit).orElse(null);
                var status = resolution.status() == ProductResolution.Status.MATCHED && factor != null ? ProductResolution.Status.MATCHED : resolution.status() == ProductResolution.Status.MATCHED ? ProductResolution.Status.NEEDS_REVIEW : resolution.status();
                return new PreviewItem(item.lineNumber(), status, resolution.productId(), resolution.name(), item.commercialUnit(), item.commercialQuantity(), baseUnit, factor, item.commercialUnitPrice());
            }).toList();
            return previews.createPreview(authorized, documentId, items, Instant.now(clock));
        });
    }
}
