package com.tino.backend.catalog;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.catalog.application.port.out.ProductCatalog;
import com.tino.backend.catalog.application.usecase.SearchProducts;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CatalogConfiguration {
    @Bean
    SearchProducts searchProducts(BusinessAuthorization authorization, ProductCatalog catalog) {
        return new SearchProducts(authorization, catalog);
    }
}
