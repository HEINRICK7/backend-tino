package com.tino.backend.catalog.adapter.out.persistence;

import com.tino.backend.catalog.application.model.ProductResolution;
import com.tino.backend.catalog.application.model.ProductSearchItem;
import com.tino.backend.catalog.application.model.ExternalProductProjection;
import com.tino.backend.catalog.application.model.ExternalProductProjectionResult;
import com.tino.backend.catalog.application.port.out.ProductCatalog;
import com.tino.backend.fiscal.domain.model.CanonicalNfeItem;
import com.tino.backend.shared.kernel.BusinessId;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
public class JooqProductCatalog implements ProductCatalog {
    private static final Table<?> PRODUCTS = table("products");
    private static final Table<?> IDENTIFIERS = table("product_identifiers");
    private static final Table<?> MAPPINGS = table("supplier_product_mappings");
    private static final Table<?> CONVERSIONS = table("packaging_conversions");
    private static final Table<?> EXTERNAL_MAPPINGS = table("external_product_mappings");
    private static final Table<?> EXTERNAL_OPTIONS = table("external_product_price_options");
    private static final Field<UUID> ID = field("id", UUID.class);
    private static final Field<UUID> BUSINESS_ID = field("business_id", UUID.class);
    private static final Field<String> NAME = field("name", String.class);
    private static final Field<String> BASE_UNIT = field("base_unit", String.class);
    private static final Field<String> TYPE = field("identifier_type", String.class);
    private static final Field<String> VALUE = field("identifier_value", String.class);
    private static final Field<String> ISSUER = field("issuer_document", String.class);
    private static final Field<String> SUPPLIER_CODE = field("supplier_product_code", String.class);
    private static final Field<UUID> PRODUCT_ID = field("product_id", UUID.class);
    private static final Field<String> PURCHASE_UNIT = field("purchase_unit", String.class);
    private static final Field<BigDecimal> FACTOR = field("conversion_factor", BigDecimal.class);
    private static final Field<String> STATUS = field("status", String.class);
    private static final Field<OffsetDateTime> CREATED_AT = field("created_at", OffsetDateTime.class);
    private static final Field<OffsetDateTime> UPDATED_AT = field("updated_at", OffsetDateTime.class);
    private static final Field<BigDecimal> SALE_PRICE = field("sale_price", BigDecimal.class);
    private static final Field<UUID> PRODUCTS_ID = qualified("products", "id", UUID.class);
    private static final Field<UUID> PRODUCTS_BUSINESS_ID = qualified("products", "business_id", UUID.class);
    private static final Field<String> PRODUCTS_NAME = qualified("products", "name", String.class);
    private static final Field<String> PRODUCTS_BASE_UNIT = qualified("products", "base_unit", String.class);
    private static final Field<UUID> IDENTIFIERS_BUSINESS_ID = qualified("product_identifiers", "business_id", UUID.class);
    private static final Field<UUID> IDENTIFIERS_PRODUCT_ID = qualified("product_identifiers", "product_id", UUID.class);
    private static final Field<String> IDENTIFIERS_TYPE = qualified("product_identifiers", "identifier_type", String.class);
    private static final Field<String> IDENTIFIERS_VALUE = qualified("product_identifiers", "identifier_value", String.class);
    private static final Field<UUID> MAPPINGS_BUSINESS_ID = qualified("supplier_product_mappings", "business_id", UUID.class);
    private static final Field<UUID> MAPPINGS_PRODUCT_ID = qualified("supplier_product_mappings", "product_id", UUID.class);
    private static final Field<String> MAPPINGS_ISSUER = qualified("supplier_product_mappings", "issuer_document", String.class);
    private static final Field<String> MAPPINGS_SUPPLIER_CODE = qualified("supplier_product_mappings", "supplier_product_code", String.class);
    private final DSLContext dsl;

    public JooqProductCatalog(DSLContext dsl) { this.dsl = dsl; }

    @Override
    public ProductResolution resolve(BusinessId businessId, String issuerDocument, CanonicalNfeItem item) {
        var gtin = usableGtin(item.gtin());
        if (gtin != null) {
            var matched = dsl.select(PRODUCTS_ID, PRODUCTS_NAME, PRODUCTS_BASE_UNIT)
                    .from(PRODUCTS).join(IDENTIFIERS).on(PRODUCTS_BUSINESS_ID.eq(IDENTIFIERS_BUSINESS_ID).and(PRODUCTS_ID.eq(IDENTIFIERS_PRODUCT_ID)))
                    .where(PRODUCTS_BUSINESS_ID.eq(businessId.value()).and(IDENTIFIERS_TYPE.eq("GTIN")).and(IDENTIFIERS_VALUE.eq(gtin)))
                    .fetchOptional();
            if (matched.isPresent()) return new ProductResolution(ProductResolution.Status.MATCHED, matched.get().get(PRODUCTS_ID), matched.get().get(PRODUCTS_NAME), matched.get().get(PRODUCTS_BASE_UNIT));
        }
        if (issuerDocument != null && item.supplierProductCode() != null) {
            var mapped = dsl.select(PRODUCTS_ID, PRODUCTS_NAME, PRODUCTS_BASE_UNIT).from(PRODUCTS).join(MAPPINGS)
                    .on(PRODUCTS_BUSINESS_ID.eq(MAPPINGS_BUSINESS_ID).and(PRODUCTS_ID.eq(MAPPINGS_PRODUCT_ID)))
                    .where(PRODUCTS_BUSINESS_ID.eq(businessId.value()).and(MAPPINGS_ISSUER.eq(issuerDocument)).and(MAPPINGS_SUPPLIER_CODE.eq(item.supplierProductCode())))
                    .fetchOptional();
            if (mapped.isPresent()) return new ProductResolution(ProductResolution.Status.MATCHED, mapped.get().get(PRODUCTS_ID), mapped.get().get(PRODUCTS_NAME), mapped.get().get(PRODUCTS_BASE_UNIT));
        }
        return new ProductResolution(ProductResolution.Status.NEW_CANDIDATE, null, item.description(), item.commercialUnit());
    }

    @Override
    public UUID create(BusinessId businessId, String name, String baseUnit, String gtin, Instant now) {
        var id = UUID.randomUUID();
        dsl.insertInto(PRODUCTS).columns(ID, BUSINESS_ID, NAME, BASE_UNIT, STATUS, CREATED_AT, UPDATED_AT)
                .values(id, businessId.value(), name, baseUnit, "ACTIVE", time(now), time(now)).execute();
        var normalized = usableGtin(gtin);
        if (normalized != null) dsl.insertInto(IDENTIFIERS).columns(ID, BUSINESS_ID, PRODUCT_ID, TYPE, VALUE, DSL.field("source", String.class), CREATED_AT)
                .values(UUID.randomUUID(), businessId.value(), id, "GTIN", normalized, "NFE", time(now)).execute();
        return id;
    }

    @Override
    public void mapSupplier(BusinessId businessId, String issuerDocument, String supplierCode, UUID productId, Instant now) {
        dsl.insertInto(MAPPINGS).columns(ID, BUSINESS_ID, ISSUER, SUPPLIER_CODE, PRODUCT_ID, CREATED_AT, UPDATED_AT)
                .values(UUID.randomUUID(), businessId.value(), issuerDocument, supplierCode, productId, time(now), time(now))
                .onConflict(BUSINESS_ID, ISSUER, SUPPLIER_CODE).doUpdate().set(PRODUCT_ID, productId).set(UPDATED_AT, time(now)).execute();
    }

    @Override
    public void confirmConversion(BusinessId businessId, String issuerDocument, String supplierCode, String purchaseUnit,
            String baseUnit, BigDecimal factor, Instant now) {
        dsl.insertInto(CONVERSIONS).columns(ID, BUSINESS_ID, ISSUER, SUPPLIER_CODE, PURCHASE_UNIT, BASE_UNIT, FACTOR, STATUS, CREATED_AT, UPDATED_AT)
                .values(UUID.randomUUID(), businessId.value(), issuerDocument, supplierCode, purchaseUnit, baseUnit, factor, "CONFIRMED", time(now), time(now))
                .onConflict(BUSINESS_ID, ISSUER, SUPPLIER_CODE, PURCHASE_UNIT, BASE_UNIT).doUpdate().set(FACTOR, factor).set(STATUS, "CONFIRMED").set(UPDATED_AT, time(now)).execute();
    }

    public Optional<BigDecimal> conversion(BusinessId businessId, String issuerDocument, String supplierCode, String purchaseUnit, String baseUnit) {
        return dsl.select(FACTOR).from(CONVERSIONS).where(BUSINESS_ID.eq(businessId.value()).and(ISSUER.eq(issuerDocument)).and(SUPPLIER_CODE.eq(supplierCode)).and(PURCHASE_UNIT.eq(purchaseUnit)).and(BASE_UNIT.eq(baseUnit)).and(STATUS.eq("CONFIRMED"))).fetchOptional(FACTOR);
    }

    @Override
    public List<ProductSearchItem> search(BusinessId businessId, String text, String gtin, int limit) {
        var condition = PRODUCTS_BUSINESS_ID.eq(businessId.value()).and(field("status", String.class).eq("ACTIVE"));
        if (text != null && !text.isBlank()) {
            condition = condition.and(DSL.lower(PRODUCTS_NAME).like("%" + text.trim().toLowerCase(Locale.ROOT) + "%"));
        }
        var normalizedGtin = usableGtin(gtin);
        if (normalizedGtin != null) {
            condition = condition.and(DSL.exists(DSL.selectOne().from(IDENTIFIERS)
                    .where(IDENTIFIERS_BUSINESS_ID.eq(businessId.value())
                            .and(IDENTIFIERS_PRODUCT_ID.eq(PRODUCTS_ID))
                            .and(IDENTIFIERS_TYPE.eq("GTIN"))
                            .and(IDENTIFIERS_VALUE.eq(normalizedGtin)))));
        }
        var rows = dsl.select(PRODUCTS_ID, PRODUCTS_NAME, PRODUCTS_BASE_UNIT)
                .from(PRODUCTS).where(condition)
                .orderBy(PRODUCTS_NAME.asc(), PRODUCTS_ID.asc()).limit(limit).fetch();
        return rows.map(row -> new ProductSearchItem(row.get(PRODUCTS_ID), row.get(PRODUCTS_NAME),
                row.get(PRODUCTS_BASE_UNIT), dsl.select(VALUE).from(IDENTIFIERS)
                        .where(IDENTIFIERS_BUSINESS_ID.eq(businessId.value())
                                .and(IDENTIFIERS_PRODUCT_ID.eq(row.get(PRODUCTS_ID)))
                                .and(IDENTIFIERS_TYPE.eq("GTIN")))
                        .orderBy(ID.asc()).limit(1).fetchOptional(VALUE).orElse(null)));
    }

    @Override
    public ExternalProductProjectionResult upsertExternalProduct(BusinessId businessId, ExternalProductProjection projection) {
        var now = time(projection.syncedAt());
        var productId = stableExternalProductId(businessId, projection.providerConnectionId(), projection.externalId());
        var mappingBusiness = field("business_id", UUID.class);
        var mappingConnection = field("provider_connection_id", UUID.class);
        var mappingExternalId = field("external_product_id", String.class);
        var wasPresent = dsl.fetchExists(dsl.selectOne().from(EXTERNAL_MAPPINGS)
                .where(mappingBusiness.eq(businessId.value()).and(mappingConnection.eq(projection.providerConnectionId()))
                        .and(mappingExternalId.eq(projection.externalId()))));
        dsl.insertInto(PRODUCTS).columns(ID, BUSINESS_ID, NAME, BASE_UNIT, SALE_PRICE, STATUS, CREATED_AT, UPDATED_AT)
                .values(productId, businessId.value(), projection.name(), projection.unitRaw(), projection.defaultPrice(),
                        projection.active() ? "ACTIVE" : "ARCHIVED", now, now)
                .onConflict(BUSINESS_ID, ID).doNothing().execute();
        dsl.update(PRODUCTS).set(NAME, projection.name()).set(BASE_UNIT, projection.unitRaw())
                .set(SALE_PRICE, projection.defaultPrice()).set(STATUS, projection.active() ? "ACTIVE" : "ARCHIVED")
                .set(UPDATED_AT, now).where(BUSINESS_ID.eq(businessId.value()).and(ID.eq(productId))).execute();
        dsl.insertInto(EXTERNAL_MAPPINGS)
                .columns(ID, mappingBusiness, mappingConnection, mappingExternalId, field("tino_product_id", UUID.class),
                        field("external_updated_at", OffsetDateTime.class), field("last_synced_at", OffsetDateTime.class), UPDATED_AT)
                .values(UUID.randomUUID(), businessId.value(), projection.providerConnectionId(), projection.externalId(), productId,
                        time(projection.externalUpdatedAt()), now, now)
                .onConflict(mappingBusiness, mappingConnection, mappingExternalId).doUpdate()
                .set(field("tino_product_id", UUID.class), productId)
                .set(field("external_updated_at", OffsetDateTime.class), time(projection.externalUpdatedAt()))
                .set(field("last_synced_at", OffsetDateTime.class), now)
                .set(UPDATED_AT, now).execute();
        dsl.deleteFrom(EXTERNAL_OPTIONS).where(mappingBusiness.eq(businessId.value())
                .and(mappingConnection.eq(projection.providerConnectionId())).and(mappingExternalId.eq(projection.externalId()))).execute();
        for (var option : projection.priceOptions()) {
            dsl.insertInto(EXTERNAL_OPTIONS).columns(ID, mappingBusiness, mappingConnection, mappingExternalId,
                    field("external_option_id", String.class), field("label", String.class), field("quantity", BigDecimal.class),
                    field("unit", String.class), field("unit_raw", String.class), field("price", BigDecimal.class),
                    field("is_default", Boolean.class), field("category_context", String.class),
                    field("subcategory_context", String.class), UPDATED_AT)
                    .values(UUID.randomUUID(), businessId.value(), projection.providerConnectionId(), projection.externalId(),
                            option.externalId(), option.label(), option.quantity(), option.unit(), option.unitRaw(), option.price(),
                            option.defaultOption(), projection.categoryContext(), projection.subcategoryContext(), now).execute();
        }
        return new ExternalProductProjectionResult(productId, !wasPresent, wasPresent, wasPresent && !projection.active());
    }

    private static UUID stableExternalProductId(BusinessId businessId, UUID connectionId, String externalId) {
        return UUID.nameUUIDFromBytes(("tino:external-product:" + businessId.value() + ":" + connectionId + ":" + externalId)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String usableGtin(String value) { if (value == null || value.isBlank() || value.equalsIgnoreCase("SEM GTIN")) return null; var v = value.replaceAll("\\D", ""); return v.matches("(?:\\d{8}|\\d{12,14})") && validGtin(v) ? v : null; }
    private static boolean validGtin(String value) { var sum = 0; var weight = 3; for (var i = value.length() - 2; i >= 0; i--, weight = 4 - weight) sum += Character.digit(value.charAt(i), 10) * weight; return (10 - sum % 10) % 10 == Character.digit(value.charAt(value.length() - 1), 10); }
    private static Table<?> table(String name) { return DSL.table(DSL.name("public", name)); }
    private static <T> Field<T> field(String name, Class<T> type) { return DSL.field(DSL.name(name), type); }
    private static <T> Field<T> qualified(String table, String column, Class<T> type) { return DSL.field(DSL.name(table, column), type); }
    private static OffsetDateTime time(Instant value) { return value.atOffset(ZoneOffset.UTC); }
}
