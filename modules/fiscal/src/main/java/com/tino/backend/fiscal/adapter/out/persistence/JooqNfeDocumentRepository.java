package com.tino.backend.fiscal.adapter.out.persistence;

import com.tino.backend.fiscal.application.model.NfeDocumentSnapshot;
import com.tino.backend.fiscal.application.model.NfeRetrievalResult;
import com.tino.backend.fiscal.application.port.out.NfeDocumentRepository;
import com.tino.backend.fiscal.domain.model.CanonicalNfeDocument;
import com.tino.backend.fiscal.domain.model.CanonicalNfeItem;
import com.tino.backend.fiscal.domain.model.CanonicalNfeIssuer;
import com.tino.backend.fiscal.domain.model.FiscalStatus;
import com.tino.backend.fiscal.domain.model.NfeAccessKey;
import com.tino.backend.fiscal.domain.model.RawNfePayload;
import com.tino.backend.fiscal.domain.model.RetrievalStatus;
import com.tino.backend.shared.kernel.BusinessId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Repository
public class JooqNfeDocumentRepository implements NfeDocumentRepository {
    private static final Table<?> DOCUMENTS = table("nfe_documents");
    private static final Table<?> VERSIONS = table("nfe_document_versions");
    private static final Table<?> ITEMS = table("nfe_items");
    private static final Table<?> IDEMPOTENCY = table("nfe_retrieval_idempotency_keys");
    private static final Field<UUID> ID = field("id", UUID.class);
    private static final Field<UUID> BUSINESS_ID = field("business_id", UUID.class);
    private static final Field<String> ACCESS_KEY = field("access_key", String.class);
    private static final Field<String> RETRIEVAL_STATUS = field("retrieval_status", String.class);
    private static final Field<String> FISCAL_STATUS = field("fiscal_status", String.class);
    private static final Field<String> ISSUER_DOCUMENT = field("issuer_document", String.class);
    private static final Field<String> DOCUMENT_NUMBER = field("document_number", String.class);
    private static final Field<String> SERIES = field("series", String.class);
    private static final Field<String> PROVIDER = field("provider", String.class);
    private static final Field<OffsetDateTime> CREATED_AT = field("created_at", OffsetDateTime.class);
    private static final Field<OffsetDateTime> UPDATED_AT = field("updated_at", OffsetDateTime.class);
    private static final Field<String> IDEMPOTENCY_KEY = field("idempotency_key", String.class);
    private static final Field<String> REQUEST_FINGERPRINT = field("request_fingerprint", String.class);
    private static final Field<UUID> DOCUMENT_ID = field("document_id", UUID.class);
    private static final Field<Long> VERSION_NUMBER = field("version_number", Long.class);
    private static final Field<JSONB> RAW_PAYLOAD = field("raw_payload", JSONB.class);
    private static final Field<JSONB> CANONICAL_PAYLOAD = field("canonical_payload", JSONB.class);
    private static final Field<String> PAYLOAD_SHA256 = field("payload_sha256", String.class);
    private static final Field<String> PROVIDER_VERSION = field("provider_version", String.class);
    private static final Field<String> PARSER_VERSION = field("parser_version", String.class);
    private static final Field<String> FAILURE_CODE = field("failure_code", String.class);
    private static final Field<OffsetDateTime> RETRIEVED_AT = field("retrieved_at", OffsetDateTime.class);
    private static final Field<String> DESCRIPTION = field("description", String.class);
    private static final Field<Integer> LINE_NUMBER = field("line_number", Integer.class);
    private static final Field<String> SUPPLIER_CODE = field("supplier_product_code", String.class);
    private static final Field<String> GTIN = field("gtin", String.class);
    private static final Field<String> TAX_GTIN = field("tax_gtin", String.class);
    private static final Field<String> NCM = field("ncm", String.class);
    private static final Field<String> CEST = field("cest", String.class);
    private static final Field<String> CFOP = field("cfop", String.class);
    private static final Field<String> COMMERCIAL_UNIT = field("commercial_unit", String.class);
    private static final Field<java.math.BigDecimal> COMMERCIAL_QUANTITY = field("commercial_quantity", java.math.BigDecimal.class);
    private static final Field<java.math.BigDecimal> COMMERCIAL_UNIT_PRICE = field("commercial_unit_price", java.math.BigDecimal.class);
    private static final Field<java.math.BigDecimal> PRODUCT_TOTAL = field("product_total", java.math.BigDecimal.class);
    private static final Field<String> TAX_UNIT = field("tax_unit", String.class);
    private static final Field<java.math.BigDecimal> TAX_QUANTITY = field("tax_quantity", java.math.BigDecimal.class);
    private static final Field<java.math.BigDecimal> TAX_UNIT_PRICE = field("tax_unit_price", java.math.BigDecimal.class);
    private static final Field<java.math.BigDecimal> DISCOUNT = field("discount", java.math.BigDecimal.class);
    private static final Field<java.math.BigDecimal> FREIGHT = field("freight", java.math.BigDecimal.class);
    private static final Field<java.math.BigDecimal> INSURANCE = field("insurance", java.math.BigDecimal.class);
    private static final Field<java.math.BigDecimal> OTHER_VALUE = field("other_value", java.math.BigDecimal.class);
    private static final Field<Boolean> INCLUDED_IN_TOTAL = field("included_in_total", Boolean.class);

    private final DSLContext dsl;
    private final ObjectMapper mapper;

    public JooqNfeDocumentRepository(DSLContext dsl, ObjectMapper mapper) {
        this.dsl = dsl;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<NfeDocumentSnapshot> findByAccessKey(BusinessId businessId, NfeAccessKey accessKey) {
        return findDocument(businessId, ACCESS_KEY.eq(accessKey.value()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<NfeDocumentSnapshot> find(BusinessId businessId, UUID documentId) {
        return findDocument(businessId, ID.eq(documentId));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RetrievalIdempotency> findIdempotency(BusinessId businessId, String key) {
        return dsl.select(ACCESS_KEY, DOCUMENT_ID).from(IDEMPOTENCY)
                .where(BUSINESS_ID.eq(businessId.value()).and(IDEMPOTENCY_KEY.eq(key)))
                .fetchOptional().map(row -> new RetrievalIdempotency(row.get(ACCESS_KEY), row.get(DOCUMENT_ID)));
    }

    @Override
    public boolean claimIdempotency(BusinessId businessId, String key, String accessKey, UUID documentId, Instant now) {
        return dsl.insertInto(IDEMPOTENCY).columns(BUSINESS_ID, IDEMPOTENCY_KEY, ACCESS_KEY, REQUEST_FINGERPRINT, DOCUMENT_ID, CREATED_AT)
                .values(businessId.value(), key, accessKey, sha256(accessKey), documentId, time(now))
                .onConflict(BUSINESS_ID, IDEMPOTENCY_KEY).doNothing().execute() == 1;
    }

    @Override
    @Transactional
    public NfeDocumentSnapshot save(BusinessId businessId, UUID documentId, NfeAccessKey accessKey,
            NfeRetrievalResult result, Instant now) {
        var document = result.document();
        var inserted = dsl.insertInto(DOCUMENTS)
                .columns(ID, BUSINESS_ID, ACCESS_KEY, RETRIEVAL_STATUS, FISCAL_STATUS, ISSUER_DOCUMENT,
                        DOCUMENT_NUMBER, SERIES, PROVIDER, CREATED_AT, UPDATED_AT)
                .values(documentId, businessId.value(), accessKey.value(), result.retrievalStatus().name(),
                        document == null ? FiscalStatus.UNKNOWN.name() : document.fiscalStatus().name(),
                        document == null ? null : document.issuer().document(),
                        document == null ? null : document.number(), document == null ? null : document.series(),
                        result.rawPayload() == null ? null : result.rawPayload().provider(),
                        time(now), time(now))
                .onConflict(BUSINESS_ID, ACCESS_KEY).doNothing().execute();
        if (inserted == 0) {
            dsl.update(DOCUMENTS).set(RETRIEVAL_STATUS, result.retrievalStatus().name())
                    .set(FISCAL_STATUS, document == null ? FiscalStatus.UNKNOWN.name() : document.fiscalStatus().name())
                    .set(UPDATED_AT, time(now)).where(BUSINESS_ID.eq(businessId.value()).and(ACCESS_KEY.eq(accessKey.value()))).execute();
            documentId = dsl.select(ID).from(DOCUMENTS).where(BUSINESS_ID.eq(businessId.value()).and(ACCESS_KEY.eq(accessKey.value())))
                    .fetchOne(ID);
        }
        var version = dsl.select(DSL.coalesce(DSL.max(VERSION_NUMBER), DSL.inline(0L)).add(1L)).from(VERSIONS)
                .where(BUSINESS_ID.eq(businessId.value()).and(DOCUMENT_ID.eq(documentId))).fetchOne(0, Long.class);
        var raw = result.rawPayload() == null ? null : json(result.rawPayload().json());
        var canonical = document == null ? null : json(write(document));
        dsl.insertInto(VERSIONS).columns(ID, BUSINESS_ID, DOCUMENT_ID, VERSION_NUMBER, RAW_PAYLOAD,
                        CANONICAL_PAYLOAD, PAYLOAD_SHA256, PROVIDER, PROVIDER_VERSION, PARSER_VERSION,
                        FAILURE_CODE, RETRIEVED_AT, CREATED_AT)
                .values(UUID.randomUUID(), businessId.value(), documentId, version, raw, canonical,
                        result.rawPayload() == null ? null : sha256(result.rawPayload().json()),
                        result.rawPayload() == null ? "serpro" : result.rawPayload().provider(),
                        result.rawPayload() == null ? "consulta-nfe" : result.rawPayload().providerVersion(),
                        document == null ? null : document.parserVersion(), result.failureCode(), time(now), time(now)).execute();
        if (document != null) {
            dsl.deleteFrom(ITEMS).where(BUSINESS_ID.eq(businessId.value()).and(DOCUMENT_ID.eq(documentId))).execute();
            for (var item : document.items()) insertItem(businessId, documentId, item);
        }
        return find(businessId, documentId).orElseThrow();
    }

    private void insertItem(BusinessId businessId, UUID documentId, CanonicalNfeItem item) {
        dsl.insertInto(ITEMS).columns(ID, BUSINESS_ID, DOCUMENT_ID, LINE_NUMBER, SUPPLIER_CODE, GTIN, TAX_GTIN,
                        DESCRIPTION, NCM, CEST, CFOP, COMMERCIAL_UNIT, COMMERCIAL_QUANTITY, COMMERCIAL_UNIT_PRICE,
                        PRODUCT_TOTAL, TAX_UNIT, TAX_QUANTITY, TAX_UNIT_PRICE, DISCOUNT, FREIGHT, INSURANCE,
                        OTHER_VALUE, INCLUDED_IN_TOTAL)
                .values(UUID.randomUUID(), businessId.value(), documentId, item.lineNumber(), item.supplierProductCode(),
                        item.gtin(), item.taxGtin(), item.description(), item.ncm(), item.cest(), item.cfop(),
                        item.commercialUnit(), item.commercialQuantity(), item.commercialUnitPrice(), item.productTotal(),
                        item.taxUnit(), item.taxQuantity(), item.taxUnitPrice(), item.discount(), item.freight(),
                        item.insurance(), item.otherValue(), item.includedInTotal()).execute();
    }

    private Optional<NfeDocumentSnapshot> findDocument(BusinessId businessId, org.jooq.Condition condition) {
        var row = dsl.select(ID, ACCESS_KEY, RETRIEVAL_STATUS, FISCAL_STATUS, UPDATED_AT)
                .from(DOCUMENTS).where(BUSINESS_ID.eq(businessId.value()).and(condition)).fetchOptional();
        return row.map(record -> {
            var documentId = record.get(ID);
            var latest = dsl.select(RAW_PAYLOAD, CANONICAL_PAYLOAD, FAILURE_CODE, VERSION_NUMBER, PROVIDER,
                            PROVIDER_VERSION, PARSER_VERSION)
                    .from(VERSIONS).where(BUSINESS_ID.eq(businessId.value()).and(DOCUMENT_ID.eq(documentId)))
                    .orderBy(VERSION_NUMBER.desc()).limit(1).fetchOptional();
            var items = dsl.select(LINE_NUMBER, SUPPLIER_CODE, GTIN, DESCRIPTION, NCM, CEST, CFOP, COMMERCIAL_UNIT,
                            COMMERCIAL_QUANTITY, COMMERCIAL_UNIT_PRICE, PRODUCT_TOTAL, TAX_GTIN, TAX_UNIT,
                            TAX_QUANTITY, TAX_UNIT_PRICE, DISCOUNT, FREIGHT, INSURANCE, OTHER_VALUE, INCLUDED_IN_TOTAL)
                    .from(ITEMS).where(BUSINESS_ID.eq(businessId.value()).and(DOCUMENT_ID.eq(documentId)))
                    .orderBy(LINE_NUMBER.asc()).fetch().map(JooqNfeDocumentRepository::toItem);
            var payload = latest.map(value -> value.get(CANONICAL_PAYLOAD));
            CanonicalNfeDocument canonical = payload.filter(value -> value != null).map(value -> read(value.data(), CanonicalNfeDocument.class)).orElse(null);
            RawNfePayload raw = latest.map(value -> value.get(RAW_PAYLOAD)).filter(value -> value != null)
                    .map(value -> new RawNfePayload(value.data(), latest.get().get(PROVIDER), latest.get().get(PROVIDER_VERSION))).orElse(null);
            if (canonical != null && !items.isEmpty()) canonical = new CanonicalNfeDocument(canonical.accessKey(), canonical.number(), canonical.series(),
                    canonical.issuedAt(), canonical.natureOperation(), canonical.operationType(), canonical.issuer(), canonical.fiscalStatus(), items,
                    canonical.parserVersion());
            return new NfeDocumentSnapshot(documentId, new NfeAccessKey(record.get(ACCESS_KEY)), RetrievalStatus.valueOf(record.get(RETRIEVAL_STATUS)),
                    FiscalStatus.valueOf(record.get(FISCAL_STATUS)), canonical, raw,
                    latest.map(value -> value.get(FAILURE_CODE)).orElse(null), latest.map(value -> value.get(VERSION_NUMBER)).orElse(0L),
                    record.get(UPDATED_AT).toInstant());
        });
    }

    private String write(Object value) { try { return mapper.writeValueAsString(value); } catch (Exception exception) { throw new IllegalStateException("could not serialize fiscal document", exception); } }
    private <T> T read(String value, Class<T> type) { try { return mapper.readValue(value, type); } catch (Exception exception) { throw new IllegalStateException("could not deserialize fiscal document", exception); } }
    private JSONB json(String value) { return JSONB.valueOf(value); }
    private static String sha256(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); } }
    private static CanonicalNfeItem toItem(Record record) { return new CanonicalNfeItem(record.get(LINE_NUMBER), record.get(SUPPLIER_CODE), record.get(GTIN), record.get(DESCRIPTION), record.get(NCM), record.get(CEST), record.get(CFOP), record.get(COMMERCIAL_UNIT), record.get(COMMERCIAL_QUANTITY), record.get(COMMERCIAL_UNIT_PRICE), record.get(PRODUCT_TOTAL), record.get(TAX_GTIN), record.get(TAX_UNIT), record.get(TAX_QUANTITY), record.get(TAX_UNIT_PRICE), record.get(DISCOUNT), record.get(FREIGHT), record.get(INSURANCE), record.get(OTHER_VALUE), record.get(INCLUDED_IN_TOTAL)); }
    private static Table<?> table(String name) { return DSL.table(DSL.name("public", name)); }
    private static <T> Field<T> field(String name, Class<T> type) { return DSL.field(DSL.name(name), type); }
    private static OffsetDateTime time(Instant value) { return value.atOffset(ZoneOffset.UTC); }
}
