package com.tino.backend.catalog.application.usecase;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.catalog.application.model.ProductSearchItem;
import com.tino.backend.catalog.application.port.out.ProductCatalog;
import com.tino.backend.shared.kernel.BusinessId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Tenant-authorized, bounded product search for explicit mobile selection. */
public final class SearchProducts {
    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 100;

    private final BusinessAuthorization authorization;
    private final ProductCatalog catalog;

    public SearchProducts(BusinessAuthorization authorization, ProductCatalog catalog) {
        this.authorization = Objects.requireNonNull(authorization);
        this.catalog = Objects.requireNonNull(catalog);
    }

    public List<ProductSearchItem> execute(UUID userId, BusinessId businessId,
            String text, String gtin, int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        return authorization.execute(userId, businessId,
                authorized -> catalog.search(authorized, text, gtin, limit));
    }
}
