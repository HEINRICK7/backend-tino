package com.tino.backend.receiving.adapter.out.persistence;

import com.tino.backend.receiving.application.port.out.PurchaseDocumentProductLookup;
import com.tino.backend.shared.kernel.BusinessId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
public class JooqPurchaseDocumentProductLookup implements PurchaseDocumentProductLookup {
    private static final Table<?> PRODUCTS = table("products");
    private static final Table<?> IDENTIFIERS = table("product_identifiers");
    private static final Table<?> MAPPINGS = table("supplier_product_mappings");
    private static final Field<UUID> PRODUCT_ID = qualified("products", "id", UUID.class);
    private static final Field<UUID> BUSINESS_ID = qualified("products", "business_id", UUID.class);
    private static final Field<String> NAME = qualified("products", "name", String.class);
    private static final Field<String> BASE_UNIT = qualified("products", "base_unit", String.class);
    private static final Field<String> STATUS = qualified("products", "status", String.class);
    private static final Field<UUID> IDENTIFIER_PRODUCT_ID = qualified("product_identifiers", "product_id", UUID.class);
    private static final Field<UUID> IDENTIFIER_BUSINESS_ID = qualified("product_identifiers", "business_id", UUID.class);
    private static final Field<String> IDENTIFIER_TYPE = qualified("product_identifiers", "identifier_type", String.class);
    private static final Field<String> IDENTIFIER_VALUE = qualified("product_identifiers", "identifier_value", String.class);
    private static final Field<UUID> MAPPING_PRODUCT_ID = qualified("supplier_product_mappings", "product_id", UUID.class);
    private static final Field<UUID> MAPPING_BUSINESS_ID = qualified("supplier_product_mappings", "business_id", UUID.class);
    private static final Field<String> MAPPING_ISSUER = qualified("supplier_product_mappings", "issuer_document", String.class);
    private static final Field<String> MAPPING_CODE = qualified("supplier_product_mappings", "supplier_product_code", String.class);

    private final org.jooq.DSLContext dsl;

    public JooqPurchaseDocumentProductLookup(org.jooq.DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<ProductCandidate> findByGtin(BusinessId businessId, String gtin) {
        return dsl.select(PRODUCT_ID, NAME, BASE_UNIT)
                .from(PRODUCTS).join(IDENTIFIERS)
                .on(BUSINESS_ID.eq(IDENTIFIER_BUSINESS_ID).and(PRODUCT_ID.eq(IDENTIFIER_PRODUCT_ID)))
                .where(BUSINESS_ID.eq(businessId.value()).and(STATUS.eq("ACTIVE"))
                        .and(IDENTIFIER_TYPE.eq("GTIN")).and(IDENTIFIER_VALUE.eq(gtin)))
                .orderBy(NAME.asc(), PRODUCT_ID.asc())
                .fetch(row -> new ProductCandidate(row.get(PRODUCT_ID), row.get(NAME), row.get(BASE_UNIT), gtin));
    }

    @Override
    public Optional<ProductCandidate> findById(BusinessId businessId, UUID productId) {
        return dsl.select(PRODUCT_ID, NAME, BASE_UNIT).from(PRODUCTS)
                .where(BUSINESS_ID.eq(businessId.value()).and(PRODUCT_ID.eq(productId)).and(STATUS.eq("ACTIVE")))
                .fetchOptional(row -> new ProductCandidate(row.get(PRODUCT_ID), row.get(NAME), row.get(BASE_UNIT), null));
    }

    @Override
    public Optional<ProductCandidate> findByIssuerAndExternalCode(
            BusinessId businessId, String issuerTaxId, String externalCode) {
        return dsl.select(PRODUCT_ID, NAME, BASE_UNIT)
                .from(PRODUCTS).join(MAPPINGS)
                .on(BUSINESS_ID.eq(MAPPING_BUSINESS_ID).and(PRODUCT_ID.eq(MAPPING_PRODUCT_ID)))
                .where(BUSINESS_ID.eq(businessId.value()).and(STATUS.eq("ACTIVE"))
                        .and(MAPPING_ISSUER.eq(issuerTaxId)).and(MAPPING_CODE.eq(externalCode)))
                .fetchOptional(row -> new ProductCandidate(row.get(PRODUCT_ID), row.get(NAME), row.get(BASE_UNIT), null));
    }

    @Override
    public List<ProductCandidate> findActive(BusinessId businessId) {
        return dsl.select(PRODUCT_ID, NAME, BASE_UNIT).from(PRODUCTS)
                .where(BUSINESS_ID.eq(businessId.value()).and(STATUS.eq("ACTIVE")))
                .orderBy(NAME.asc(), PRODUCT_ID.asc())
                .fetch(row -> new ProductCandidate(row.get(PRODUCT_ID), row.get(NAME), row.get(BASE_UNIT), null,
                        dsl.select(IDENTIFIER_VALUE).from(IDENTIFIERS)
                                .where(IDENTIFIER_BUSINESS_ID.eq(businessId.value())
                                        .and(IDENTIFIER_PRODUCT_ID.eq(row.get(PRODUCT_ID)))
                                        .and(IDENTIFIER_TYPE.eq("ALIAS")))
                                .orderBy(IDENTIFIER_VALUE.asc()).fetch(IDENTIFIER_VALUE)));
    }

    private static Table<?> table(String name) { return DSL.table(DSL.name("public", name)); }
    private static <T> Field<T> qualified(String table, String column, Class<T> type) {
        return DSL.field(DSL.name(table, column), type);
    }
}
