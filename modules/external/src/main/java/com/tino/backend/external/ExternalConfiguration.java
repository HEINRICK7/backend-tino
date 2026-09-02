package com.tino.backend.external;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.business.application.port.in.BusinessDataSourceConfiguration;
import com.tino.backend.catalog.application.port.out.ProductCatalog;
import com.tino.backend.external.adapter.out.docessonhos.DocesSonhosCatalogAdapter;
import com.tino.backend.external.application.port.out.ExternalBusinessConnectionRepository;
import com.tino.backend.external.application.port.out.ExternalCatalogProvider;
import com.tino.backend.external.application.usecase.ManageExternalBusinessDataSource;
import com.tino.backend.shared.kernel.TenantContextExecutor;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
public class ExternalConfiguration {
    @Bean(name = "externalCatalogHttpClient")
    HttpClient externalCatalogHttpClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @Bean
    ExternalCatalogProvider docesSonhosCatalogProvider(
            @Qualifier("externalCatalogHttpClient") HttpClient http,
            ObjectMapper mapper,
            @Value("${tino.external.doces-sonhos.base-url:https://api.doces-sonhos.otimizanegocio.com}") String baseUrl,
            @Value("${tino.external.doces-sonhos.products-path:/public/products}") String path,
            @Value("${tino.external.doces-sonhos.api-token:}") String token,
            @Value("${tino.external.doces-sonhos.timeout:PT10S}") Duration timeout) {
        return new DocesSonhosCatalogAdapter(http, mapper, baseUrl.isBlank() ? null : URI.create(baseUrl), path, token, timeout);
    }

    @Bean
    ManageExternalBusinessDataSource manageExternalBusinessDataSource(
            BusinessAuthorization authorization,
            TenantContextExecutor tenants,
            ExternalBusinessConnectionRepository connections,
            BusinessDataSourceConfiguration businessDataSource,
            ProductCatalog catalog,
            List<ExternalCatalogProvider> providers,
            Clock clock) {
        return new ManageExternalBusinessDataSource(authorization, tenants, connections, catalog, providers, clock, businessDataSource);
    }
}
