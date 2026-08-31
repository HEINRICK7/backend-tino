package com.tino.backend.external.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.business.application.port.in.BusinessDataSourceConfiguration;
import com.tino.backend.catalog.application.model.ExternalProductProjection;
import com.tino.backend.catalog.application.model.ExternalProductProjectionResult;
import com.tino.backend.catalog.application.model.ProductResolution;
import com.tino.backend.catalog.application.model.ProductSearchItem;
import com.tino.backend.catalog.application.port.out.ProductCatalog;
import com.tino.backend.external.application.model.ExternalCatalogPage;
import com.tino.backend.external.application.model.ExternalPriceOption;
import com.tino.backend.external.application.model.ExternalProduct;
import com.tino.backend.external.application.port.out.ExternalBusinessConnectionRepository;
import com.tino.backend.external.application.port.out.ExternalCatalogProvider;
import com.tino.backend.external.domain.model.ExternalBusinessConnection;
import com.tino.backend.external.domain.model.ExternalConnectionStatus;
import com.tino.backend.external.domain.model.ExternalDataSourceType;
import com.tino.backend.shared.kernel.BusinessId;
import com.tino.backend.shared.kernel.TenantContextExecutor;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.HashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class ManageExternalBusinessDataSourceTest {
    private static final BusinessId BUSINESS = new BusinessId(UUID.fromString("01a04d7c-a223-757f-8a96-861ceefd8ec7"));
    private static final UUID CONNECTION_ID = UUID.fromString("01a04d7c-a223-757f-8a96-861ceefd8ec8");
    private static final Instant NOW = Instant.parse("2026-08-30T12:00:00Z");

    @Test
    void sourceSelectionIsExplicitAndReplaySafe() {
        var connections = new InMemoryConnectionRepository();
        var businesses = new InMemoryBusinessDataSource();
        var service = service(connections, businesses);

        var selected = service.configure(UUID.randomUUID(), BUSINESS, ExternalDataSourceType.EXTERNAL_API, "DOCES_SONHOS");
        var replay = service.configure(UUID.randomUUID(), BUSINESS, ExternalDataSourceType.EXTERNAL_API, "DOCES_SONHOS");

        assertThat(selected.sourceType()).isEqualTo(ExternalDataSourceType.EXTERNAL_API);
        assertThat(selected.provider()).isEqualTo("DOCES_SONHOS");
        assertThat(replay.connectionId()).isEqualTo(selected.connectionId());
        assertThat(connections.list(BUSINESS)).hasSize(1);
        assertThat(businesses.readSourceType(BUSINESS)).isEqualTo(ExternalDataSourceType.EXTERNAL_API.name());
    }

    @Test
    void nativeSelectionDoesNotCreateAnExternalConnection() {
        var connections = new InMemoryConnectionRepository();
        var businesses = new InMemoryBusinessDataSource();
        var selected = service(connections, businesses).configure(
                UUID.randomUUID(), BUSINESS, ExternalDataSourceType.TINO_NATIVE, null);

        assertThat(selected.sourceType()).isEqualTo(ExternalDataSourceType.TINO_NATIVE);
        assertThat(selected.provider()).isNull();
        assertThat(selected.connectionId()).isNull();
        assertThat(connections.list(BUSINESS)).isEmpty();
    }

    @Test
    void unknownProviderCannotBeSelected() {
        var service = service(new InMemoryConnectionRepository(), new InMemoryBusinessDataSource());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.configure(
                        UUID.randomUUID(), BUSINESS, ExternalDataSourceType.EXTERNAL_API, "UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ManageExternalBusinessDataSource service(
            InMemoryConnectionRepository connections, InMemoryBusinessDataSource businesses) {
        return new ManageExternalBusinessDataSource(
                new BusinessAuthorization() {
                    @Override public <T> T execute(UUID user, BusinessId business, Function<BusinessId, T> operation) { return operation.apply(business); }
                },
                new TenantContextExecutor() {
                    @Override public <T> T execute(BusinessId business, Supplier<T> operation) { return operation.get(); }
                }, connections, new InMemoryProductCatalog(), List.of(new TestProvider()),
                Clock.fixed(NOW, ZoneOffset.UTC), businesses);
    }

    @Test
    void paginatesAdvancesCursorAfterEachSuccessfulPageAndReplaysWithoutProductDuplication() {
        var repository = new InMemoryConnectionRepository();
        repository.connection = connection(ExternalConnectionStatus.CONNECTED, null);
        var catalog = new InMemoryProductCatalog();
        var provider = new TestProvider();
        var service = new ManageExternalBusinessDataSource(
                new BusinessAuthorization() {
                    @Override public <T> T execute(UUID user, BusinessId business, Function<BusinessId, T> operation) { return operation.apply(business); }
                },
                new TenantContextExecutor() {
                    @Override public <T> T execute(BusinessId business, Supplier<T> operation) { return operation.get(); }
                }, repository, catalog,
                List.of(provider), Clock.fixed(NOW, ZoneOffset.UTC));

        var first = service.sync(UUID.randomUUID(), BUSINESS, CONNECTION_ID);
        var second = service.sync(UUID.randomUUID(), BUSINESS, CONNECTION_ID);

        assertThat(first.status()).isEqualTo(ExternalConnectionStatus.READY);
        assertThat(first.received()).isEqualTo(2);
        assertThat(first.created()).isEqualTo(1);
        assertThat(first.deactivated()).isEqualTo(1);
        assertThat(second.status()).isEqualTo(ExternalConnectionStatus.READY);
        assertThat(second.created()).isEqualTo(0);
        assertThat(catalog.products).hasSize(1);
        assertThat(repository.cursors).containsExactly("page-2", null, NOW.toString(), null, NOW.toString());
        assertThat(provider.cursors).containsExactly(null, "page-2", NOW.toString());
    }

    private static ExternalBusinessConnection connection(ExternalConnectionStatus status, String cursor) {
        return new ExternalBusinessConnection(CONNECTION_ID, BUSINESS, "DOCES_SONHOS", status,
                ExternalDataSourceType.EXTERNAL_API, null, cursor, null, null, null, 0, 0, 0, 0, 0, NOW, NOW);
    }

    private static ExternalProduct product(boolean active) {
        var option = new ExternalPriceOption("p", "P", new BigDecimal("1"), "P", "P", new BigDecimal("50.00"), true);
        return new ExternalProduct(CONNECTION_ID, "bolo-1", "Bolo", active, NOW, option.price(), List.of(option),
                option.quantity(), option.unit(), option.unitRaw(), "Bolos", "Tradicionais");
    }

    private static final class TestProvider implements ExternalCatalogProvider {
        private final List<String> cursors = new ArrayList<>();
        private int calls;
        @Override public String provider() { return "DOCES_SONHOS"; }
        @Override public ExternalCatalogPage fetch(UUID connectionId, String cursor, Instant watermark) {
            cursors.add(cursor);
            calls++;
            if (calls == 1) return new ExternalCatalogPage(List.of(product(true)), "page-2", NOW);
            if (calls == 2) return new ExternalCatalogPage(List.of(product(false)), null, NOW);
            return new ExternalCatalogPage(List.of(product(true)), null, NOW);
        }
    }

    private static final class InMemoryConnectionRepository implements ExternalBusinessConnectionRepository {
        private ExternalBusinessConnection connection;
        private final List<String> cursors = new ArrayList<>();
        @Override public ExternalBusinessConnection create(BusinessId businessId, String provider, Instant now) {
            connection = new ExternalBusinessConnection(CONNECTION_ID, businessId, provider,
                    ExternalConnectionStatus.CONNECTED, ExternalDataSourceType.EXTERNAL_API,
                    null, null, null, null, null, 0, 0, 0, 0, 0, now, now);
            return connection;
        }
        @Override public Optional<ExternalBusinessConnection> find(BusinessId businessId, UUID id) { return Optional.ofNullable(connection); }
        @Override public List<ExternalBusinessConnection> list(BusinessId businessId) { return connection == null ? List.of() : List.of(connection); }
        @Override public ExternalBusinessConnection markSyncing(BusinessId businessId, UUID id, Instant now) {
            connection = copy(ExternalConnectionStatus.SYNCING, connection.syncCursor(), null);
            return connection;
        }
        @Override public void pageSucceeded(BusinessId businessId, UUID id, String cursor, int received, int created, int updated,
                int deactivated, int rejected, Instant now) {
            cursors.add(cursor);
            connection = copy(ExternalConnectionStatus.SYNCING, cursor, null, received, created, updated, deactivated, rejected);
        }
        @Override public ExternalBusinessConnection markSucceeded(BusinessId businessId, UUID id, String cursor, int received,
                int created, int updated, int deactivated, int rejected, Instant completedAt) {
            cursors.add(cursor);
            connection = copy(ExternalConnectionStatus.READY, cursor, completedAt, received, created, updated, deactivated, rejected);
            return connection;
        }
        @Override public ExternalBusinessConnection markFailed(BusinessId businessId, UUID id, ExternalConnectionStatus status,
                String errorCode, int received, int created, int updated, int deactivated, int rejected, Instant finishedAt) {
            connection = copy(status, connection.syncCursor(), finishedAt, received, created, updated, deactivated, rejected);
            return connection;
        }
        private ExternalBusinessConnection copy(ExternalConnectionStatus status, String cursor, Instant finished) {
            return copy(status, cursor, finished, 0, 0, 0, 0, 0);
        }
        private ExternalBusinessConnection copy(ExternalConnectionStatus status, String cursor, Instant finished, int received,
                int created, int updated, int deactivated, int rejected) {
            return new ExternalBusinessConnection(CONNECTION_ID, BUSINESS, "DOCES_SONHOS", status, ExternalDataSourceType.EXTERNAL_API,
                    finished, cursor, NOW, finished, null, received, created, updated, deactivated, rejected, NOW, NOW);
        }
    }

    private static final class InMemoryBusinessDataSource implements BusinessDataSourceConfiguration {
        private final HashMap<BusinessId, String> values = new HashMap<>();
        private InMemoryBusinessDataSource() { values.put(BUSINESS, ExternalDataSourceType.TINO_NATIVE.name()); }
        @Override public String readSourceType(BusinessId businessId) { return values.get(businessId); }
        @Override public void updateSourceType(BusinessId businessId, String sourceType) { values.put(businessId, sourceType); }
    }

    private static final class InMemoryProductCatalog implements ProductCatalog {
        private final List<ExternalProductProjection> products = new ArrayList<>();
        @Override public ProductResolution resolve(BusinessId businessId, String issuerDocument, com.tino.backend.fiscal.domain.model.CanonicalNfeItem item) { return null; }
        @Override public UUID create(BusinessId businessId, String name, String baseUnit, String gtin, Instant now) { return UUID.randomUUID(); }
        @Override public void mapSupplier(BusinessId businessId, String issuerDocument, String supplierCode, UUID productId, Instant now) {}
        @Override public void confirmConversion(BusinessId businessId, String issuerDocument, String supplierCode, String purchaseUnit, String baseUnit, BigDecimal factor, Instant now) {}
        @Override public Optional<BigDecimal> conversion(BusinessId businessId, String issuerDocument, String supplierCode, String purchaseUnit, String baseUnit) { return Optional.empty(); }
        @Override public List<ProductSearchItem> search(BusinessId businessId, String text, String gtin, int limit) { return List.of(); }
        @Override public ExternalProductProjectionResult upsertExternalProduct(BusinessId businessId, ExternalProductProjection projection) {
            var existing = products.stream().filter(value -> value.externalId().equals(projection.externalId())).findFirst();
            if (existing.isEmpty()) { products.add(projection); return new ExternalProductProjectionResult(UUID.randomUUID(), true, false, !projection.active()); }
            products.set(products.indexOf(existing.orElseThrow()), projection);
            return new ExternalProductProjectionResult(UUID.randomUUID(), false, true, !projection.active());
        }
    }
}
