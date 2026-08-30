package com.tino.backend.catalog.adapter.out.persistence;

import com.tino.backend.catalog.application.model.ProductResolution;
import com.tino.backend.catalog.application.port.out.ProductCatalog;
import com.tino.backend.fiscal.domain.model.CanonicalNfeItem;
import com.tino.backend.shared.kernel.BusinessId;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
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
    private final DSLContext dsl;

    public JooqProductCatalog(DSLContext dsl) { this.dsl = dsl; }

    @Override
    public ProductResolution resolve(BusinessId businessId, String issuerDocument, CanonicalNfeItem item) {
        var gtin = usableGtin(item.gtin());
        if (gtin != null) {
            var matched = dsl.select(PRODUCTS.field(ID), PRODUCTS.field(NAME), PRODUCTS.field(BASE_UNIT))
                    .from(PRODUCTS).join(IDENTIFIERS).on(PRODUCTS.field(BUSINESS_ID).eq(IDENTIFIERS.field(BUSINESS_ID)).and(PRODUCTS.field(ID).eq(IDENTIFIERS.field(PRODUCT_ID))))
                    .where(PRODUCTS.field(BUSINESS_ID).eq(businessId.value()).and(IDENTIFIERS.field(TYPE).eq("GTIN")).and(IDENTIFIERS.field(VALUE).eq(gtin)))
                    .fetchOptional();
            if (matched.isPresent()) return new ProductResolution(ProductResolution.Status.MATCHED, matched.get().get(PRODUCTS.field(ID)), matched.get().get(PRODUCTS.field(NAME)), matched.get().get(PRODUCTS.field(BASE_UNIT)));
        }
        if (issuerDocument != null && item.supplierProductCode() != null) {
            var mapped = dsl.select(PRODUCTS.field(ID), PRODUCTS.field(NAME), PRODUCTS.field(BASE_UNIT)).from(PRODUCTS).join(MAPPINGS)
                    .on(PRODUCTS.field(BUSINESS_ID).eq(MAPPINGS.field(BUSINESS_ID)).and(PRODUCTS.field(ID).eq(MAPPINGS.field(PRODUCT_ID))))
                    .where(PRODUCTS.field(BUSINESS_ID).eq(businessId.value()).and(MAPPINGS.field(ISSUER).eq(issuerDocument)).and(MAPPINGS.field(SUPPLIER_CODE).eq(item.supplierProductCode())))
                    .fetchOptional();
            if (mapped.isPresent()) return new ProductResolution(ProductResolution.Status.MATCHED, mapped.get().get(PRODUCTS.field(ID)), mapped.get().get(PRODUCTS.field(NAME)), mapped.get().get(PRODUCTS.field(BASE_UNIT)));
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

    private static String usableGtin(String value) { if (value == null || value.isBlank() || value.equalsIgnoreCase("SEM GTIN")) return null; var v = value.replaceAll("\\D", ""); return v.matches("(?:\\d{8}|\\d{12,14})") && validGtin(v) ? v : null; }
    private static boolean validGtin(String value) { var sum = 0; var weight = 3; for (var i = value.length() - 2; i >= 0; i--, weight = 4 - weight) sum += Character.digit(value.charAt(i), 10) * weight; return (10 - sum % 10) % 10 == Character.digit(value.charAt(value.length() - 1), 10); }
    private static Table<?> table(String name) { return DSL.table(DSL.name("public", name)); }
    private static <T> Field<T> field(String name, Class<T> type) { return DSL.field(DSL.name(name), type); }
    private static OffsetDateTime time(Instant value) { return value.atOffset(ZoneOffset.UTC); }
}
