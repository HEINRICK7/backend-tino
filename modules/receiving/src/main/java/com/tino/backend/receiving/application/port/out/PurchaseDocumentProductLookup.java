package com.tino.backend.receiving.application.port.out;

import com.tino.backend.shared.kernel.BusinessId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

/** Tenant-scoped catalog reads needed by PurchaseDocument matching. */
public interface PurchaseDocumentProductLookup {
    List<ProductCandidate> findByGtin(BusinessId businessId, String gtin);

    Optional<ProductCandidate> findById(BusinessId businessId, UUID productId);

    Optional<ProductCandidate> findByIssuerAndExternalCode(
            BusinessId businessId, String issuerTaxId, String externalCode);

    List<ProductCandidate> findActive(BusinessId businessId);

    record ProductCandidate(UUID productId, String name, String baseUnit, String gtin, List<String> aliases) {
        public ProductCandidate(UUID productId, String name, String baseUnit, String gtin) {
            this(productId, name, baseUnit, gtin, List.of());
        }

        public ProductCandidate {
            aliases = aliases == null ? List.of() : List.copyOf(aliases);
        }
    }
}
