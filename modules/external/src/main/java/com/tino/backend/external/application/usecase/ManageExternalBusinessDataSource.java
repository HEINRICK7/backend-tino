package com.tino.backend.external.application.usecase;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.catalog.application.model.ExternalPriceOptionProjection;
import com.tino.backend.catalog.application.model.ExternalProductProjection;
import com.tino.backend.catalog.application.port.out.ProductCatalog;
import com.tino.backend.external.application.exception.ExternalConnectionNotFoundException;
import com.tino.backend.external.application.exception.ExternalProviderAuthenticationException;
import com.tino.backend.external.application.exception.ExternalProviderException;
import com.tino.backend.external.application.exception.ExternalProviderMalformedException;
import com.tino.backend.external.application.exception.ExternalProviderUnavailableException;
import com.tino.backend.external.application.model.ConnectionRegistrationResult;
import com.tino.backend.external.application.model.BusinessDataSource;
import com.tino.backend.external.application.model.ExternalCatalogPage;
import com.tino.backend.external.application.model.ExternalProduct;
import com.tino.backend.external.application.model.ExternalPriceOption;
import com.tino.backend.external.application.model.ExternalSyncResult;
import com.tino.backend.external.application.port.out.ExternalBusinessConnectionRepository;
import com.tino.backend.external.application.port.out.ExternalCatalogProvider;
import com.tino.backend.external.domain.model.ExternalBusinessConnection;
import com.tino.backend.external.domain.model.ExternalConnectionStatus;
import com.tino.backend.shared.kernel.BusinessId;
import com.tino.backend.shared.kernel.TenantContextExecutor;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Application orchestration for generic external catalog sources. */
public final class ManageExternalBusinessDataSource {
    private static final Logger LOG = LoggerFactory.getLogger(ManageExternalBusinessDataSource.class);
    private final BusinessAuthorization authorization;
    private final TenantContextExecutor tenants;
    private final ExternalBusinessConnectionRepository connections;
    private final ProductCatalog catalog;
    private final Map<String, ExternalCatalogProvider> providers;
    private final Clock clock;

    public ManageExternalBusinessDataSource(BusinessAuthorization authorization, TenantContextExecutor tenants,
            ExternalBusinessConnectionRepository connections, ProductCatalog catalog,
            List<ExternalCatalogProvider> providers, Clock clock) {
        this.authorization = Objects.requireNonNull(authorization);
        this.tenants = Objects.requireNonNull(tenants);
        this.connections = Objects.requireNonNull(connections);
        this.catalog = Objects.requireNonNull(catalog);
        this.providers = providers.stream().collect(Collectors.toUnmodifiableMap(ExternalCatalogProvider::provider, Function.identity()));
        this.clock = Objects.requireNonNull(clock);
    }

    public ConnectionRegistrationResult register(UUID userId, BusinessId businessId, String provider) {
        requireProvider(provider);
        return authorization.execute(userId, businessId, ignored -> tenants.execute(businessId, () -> {
            var existing = connections.list(businessId).stream().filter(c -> provider.equals(c.provider())).findFirst();
            if (existing.isPresent()) return new ConnectionRegistrationResult(existing.orElseThrow(), true);
            return new ConnectionRegistrationResult(connections.create(businessId, provider, now()), false);
        }));
    }

    public List<ExternalBusinessConnection> list(UUID userId, BusinessId businessId) {
        return authorization.execute(userId, businessId, ignored -> tenants.execute(businessId, () -> connections.list(businessId)));
    }

    public BusinessDataSource source(UUID userId, BusinessId businessId) {
        return authorization.execute(userId, businessId, ignored -> tenants.execute(businessId, () -> {
            var current = connections.list(businessId).stream().findFirst();
            if (current.isEmpty()) return new BusinessDataSource(businessId,
                    com.tino.backend.external.domain.model.ExternalDataSourceType.TINO_NATIVE, null, null,
                    ExternalConnectionStatus.READY);
            var connection = current.orElseThrow();
            return new BusinessDataSource(businessId, connection.sourceType(), connection.provider(), connection.id(), connection.status());
        }));
    }

    public ExternalBusinessConnection get(UUID userId, BusinessId businessId, UUID connectionId) {
        return authorization.execute(userId, businessId, ignored -> tenants.execute(businessId,
                () -> connections.find(businessId, connectionId).orElseThrow(ExternalConnectionNotFoundException::new)));
    }

    public ExternalSyncResult sync(UUID userId, BusinessId businessId, UUID connectionId) {
        return authorization.execute(userId, businessId, ignored -> syncAuthorized(businessId, connectionId));
    }

    private ExternalSyncResult syncAuthorized(BusinessId businessId, UUID connectionId) {
        var started = tenants.execute(businessId, () -> connections.markSyncing(businessId, connectionId, now()));
        var provider = providers.get(started.provider());
        if (provider == null) return failure(businessId, connectionId, new ExternalProviderMalformedException(), 0, 0, 0, 0, 0);
        var cursor = started.syncCursor();
        var received = 0;
        var created = 0;
        var updated = 0;
        var deactivated = 0;
        var rejected = 0;
        try {
            for (var pageNumber = 0; pageNumber < 10_000; pageNumber++) {
                var page = provider.fetch(connectionId, cursor, started.lastSuccessfulSyncAt());
                var pageCounts = persistPage(businessId, page);
                received += page.products().size();
                created += pageCounts.created();
                updated += pageCounts.updated();
                deactivated += pageCounts.deactivated();
                rejected += pageCounts.rejected();
                var next = page.nextCursor();
                if (next != null && next.equals(cursor)) throw new ExternalProviderMalformedException();
                cursor = next;
                var pageCursor = cursor;
                var pageReceived = received;
                var pageCreated = created;
                var pageUpdated = updated;
                var pageDeactivated = deactivated;
                var pageRejected = rejected;
                tenants.execute(businessId, () -> {
                    connections.pageSucceeded(businessId, connectionId, pageCursor,
                            pageReceived, pageCreated, pageUpdated, pageDeactivated, pageRejected, now());
                    return null;
                });
                if (next == null) {
                    var completed = now();
                    var finalReceived = received;
                    var finalCreated = created;
                    var finalUpdated = updated;
                    var finalDeactivated = deactivated;
                    var finalRejected = rejected;
                    var finalConnection = tenants.execute(businessId, () -> connections.markSucceeded(businessId, connectionId,
                            page.watermark().toString(), finalReceived, finalCreated, finalUpdated, finalDeactivated, finalRejected, completed));
                    LOG.info("external_catalog_sync_completed businessId={} connectionId={} provider={} durationMs={} received={} created={} updated={} deactivated={} rejected={} cursorAdvanced={}",
                            businessId.value(), connectionId, started.provider(), 0, received, created, updated, deactivated, rejected, true);
                    return new ExternalSyncResult(connectionId, finalConnection.status(), completed, received, created, updated, deactivated, rejected, null);
                }
            }
            throw new ExternalProviderMalformedException();
        } catch (ExternalProviderException exception) {
            return failure(businessId, connectionId, exception, received, created, updated, deactivated, rejected);
        } catch (RuntimeException exception) {
            return failure(businessId, connectionId, new ExternalProviderMalformedException(exception), received, created, updated, deactivated, rejected);
        }
    }

    private PageCounts persistPage(BusinessId businessId, ExternalCatalogPage page) {
        return tenants.execute(businessId, () -> {
            var created = 0;
            var updated = 0;
            var deactivated = 0;
            for (var product : page.products()) {
                var result = catalog.upsertExternalProduct(businessId, toProjection(product));
                if (result.created()) created++;
                if (result.updated()) updated++;
                if (result.deactivated()) deactivated++;
            }
            return new PageCounts(created, updated, deactivated, 0);
        });
    }

    private ExternalSyncResult failure(BusinessId businessId, UUID connectionId, ExternalProviderException exception,
            int received, int created, int updated, int deactivated, int rejected) {
        var status = exception instanceof ExternalProviderAuthenticationException ? ExternalConnectionStatus.AUTH_ERROR
                : exception instanceof ExternalProviderUnavailableException ? ExternalConnectionStatus.DEGRADED
                : ExternalConnectionStatus.FAILED;
        var finished = now();
        var connection = tenants.execute(businessId, () -> connections.markFailed(businessId, connectionId, status,
                exception.code(), received, created, updated, deactivated, rejected, finished));
        LOG.warn("external_catalog_sync_failed businessId={} connectionId={} status={} errorCode={} received={} created={} updated={} deactivated={} rejected={}",
                businessId.value(), connectionId, connection.status(), exception.code(), received, created, updated, deactivated, rejected);
        return new ExternalSyncResult(connectionId, connection.status(), finished, received, created, updated, deactivated, rejected, exception.code());
    }

    private ExternalProductProjection toProjection(ExternalProduct product) {
        var options = product.priceOptions().stream().map(this::toProjection).toList();
        return new ExternalProductProjection(product.providerConnectionId(), product.externalId(), product.name(), product.active(),
                product.updatedAt(), product.unit(), product.unitRaw(), product.defaultPrice(), options,
                product.categoryContext(), product.subcategoryContext(), now());
    }

    private ExternalPriceOptionProjection toProjection(ExternalPriceOption option) {
        return new ExternalPriceOptionProjection(option.externalId(), option.label(), option.quantity(), option.unit(),
                option.unitRaw(), option.price(), option.defaultOption());
    }

    private void requireProvider(String provider) {
        if (provider == null || provider.isBlank() || !providers.containsKey(provider)) throw new IllegalArgumentException("unsupported external provider");
    }

    private Instant now() { return clock.instant(); }
    private record PageCounts(int created, int updated, int deactivated, int rejected) {}
}
