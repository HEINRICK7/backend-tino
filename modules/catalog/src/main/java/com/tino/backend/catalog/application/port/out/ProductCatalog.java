package com.tino.backend.catalog.application.port.out;

import com.tino.backend.catalog.application.model.ProductResolution;
import com.tino.backend.catalog.application.model.ProductSearchItem;
import com.tino.backend.fiscal.domain.model.CanonicalNfeItem;
import com.tino.backend.shared.kernel.BusinessId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.Optional;
import java.util.List;

public interface ProductCatalog {
    ProductResolution resolve(BusinessId businessId, String issuerDocument, CanonicalNfeItem item);
    UUID create(BusinessId businessId, String name, String baseUnit, String gtin, Instant now);
    void mapSupplier(BusinessId businessId, String issuerDocument, String supplierCode, UUID productId, Instant now);
    void confirmConversion(BusinessId businessId, String issuerDocument, String supplierCode, String purchaseUnit,
            String baseUnit, BigDecimal factor, Instant now);
    Optional<BigDecimal> conversion(BusinessId businessId, String issuerDocument, String supplierCode,
            String purchaseUnit, String baseUnit);
    List<ProductSearchItem> search(BusinessId businessId, String text, String gtin, int limit);
}
