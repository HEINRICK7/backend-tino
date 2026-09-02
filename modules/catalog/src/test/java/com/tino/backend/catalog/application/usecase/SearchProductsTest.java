package com.tino.backend.catalog.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.catalog.application.port.out.ProductCatalog;
import com.tino.backend.shared.kernel.BusinessId;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class SearchProductsTest {
    @Test
    void allowsBoundedUnfilteredCatalogListingForMobileSync() {
        var catalog = mock(ProductCatalog.class);
        var business = new BusinessId(UUID.randomUUID());
        BusinessAuthorization authorization = new BusinessAuthorization() {
            @Override
            public <T> T execute(UUID userId, BusinessId requestedBusiness, Function<BusinessId, T> operation) {
                return operation.apply(requestedBusiness);
            }
        };
        var useCase = new SearchProducts(authorization, catalog);

        var result = useCase.execute(UUID.randomUUID(), business, null, null, 100);

        assertThat(result).isEqualTo(List.of());
        verify(catalog).search(business, null, null, 100);
    }

    @Test
    void allowsReadingTheNextBoundedCatalogPage() {
        var catalog = mock(ProductCatalog.class);
        var business = new BusinessId(UUID.randomUUID());
        BusinessAuthorization authorization = new BusinessAuthorization() {
            @Override
            public <T> T execute(UUID userId, BusinessId requestedBusiness, Function<BusinessId, T> operation) {
                return operation.apply(requestedBusiness);
            }
        };
        var useCase = new SearchProducts(authorization, catalog);

        var result = useCase.execute(UUID.randomUUID(), business, null, null, 100, 100);

        assertThat(result).isEqualTo(List.of());
        verify(catalog).search(business, null, null, 100, 100);
    }
}
