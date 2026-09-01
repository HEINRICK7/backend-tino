package com.tino.backend.receiving.adapter.out.persistence;

import com.tino.backend.receiving.application.model.PurchaseDocument;
import com.tino.backend.receiving.application.model.PurchasePreviewSnapshot;
import com.tino.backend.receiving.application.model.PurchaseDocumentMatch;
import com.tino.backend.receiving.application.exception.ReceivingErrorCode;
import com.tino.backend.receiving.application.exception.ReceivingException;
import com.tino.backend.receiving.application.port.out.PurchaseReceivingRepository;
import com.tino.backend.receiving.application.port.out.PurchaseReceiptRepository;
import com.tino.backend.receiving.application.port.out.PurchaseHistoryRepository;
import com.tino.backend.shared.kernel.BusinessId;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JooqPurchaseReceivingRepository implements PurchaseReceivingRepository, PurchaseReceiptRepository, PurchaseHistoryRepository {
    private static final Table<?> DOCUMENTS = table("purchase_documents");
    private static final Table<?> DOCUMENT_ITEMS = table("purchase_document_items");
    private static final Table<?> PREVIEWS = table("receiving_purchase_previews");
    private static final Table<?> PREVIEW_ITEMS = table("receiving_purchase_preview_items");
    private static final Table<?> IDEMPOTENCY = table("receiving_purchase_preview_idempotency");
    private static final Table<?> RECEIPTS = table("purchase_receipts");
    private static final Table<?> RECEIPT_ITEMS = table("purchase_receipt_items");
    private static final Table<?> PRICE_OBSERVATIONS = table("purchase_price_observations");
    private static final Table<?> EVENTS = table("receiving_events");
    private static final Table<?> CONFIRMATION_IDEMPOTENCY = table("purchase_receipt_confirmation_idempotency");
    private static final Table<?> INVENTORY_MOVEMENTS = table("inventory_movements");
    private static final Table<?> INVENTORY_BALANCES = table("inventory_balances");

    private static final Field<UUID> ID = field("id", UUID.class);
    private static final Field<UUID> BUSINESS_ID = field("business_id", UUID.class);
    private static final Field<UUID> DOCUMENT_ID = field("document_id", UUID.class);
    private static final Field<UUID> PREVIEW_ID = field("preview_id", UUID.class);
    private static final Field<String> SOURCE = field("source", String.class);
    private static final Field<String> DOCUMENT_TYPE = field("document_type", String.class);
    private static final Field<String> ACCESS_KEY = field("access_key", String.class);
    private static final Field<OffsetDateTime> ISSUED_AT = field("issued_at", OffsetDateTime.class);
    private static final Field<String> ISSUER_NAME = field("issuer_name", String.class);
    private static final Field<String> ISSUER_TAX_ID = field("issuer_tax_id", String.class);
    private static final Field<BigDecimal> TOTAL = field("total", BigDecimal.class);
    private static final Field<String> PAYLOAD_SHA256 = field("payload_sha256", String.class);
    private static final Field<String> STATUS = field("status", String.class);
    private static final Field<Long> VERSION = field("version", Long.class);
    private static final Field<OffsetDateTime> CREATED_AT = field("created_at", OffsetDateTime.class);
    private static final Field<OffsetDateTime> UPDATED_AT = field("updated_at", OffsetDateTime.class);
    private static final Field<Integer> LINE_NUMBER = field("line_number", Integer.class);
    private static final Field<String> EXTERNAL_CODE = field("external_code", String.class);
    private static final Field<String> GTIN = field("gtin", String.class);
    private static final Field<String> RAW_DESCRIPTION = field("raw_description", String.class);
    private static final Field<BigDecimal> QUANTITY = field("quantity", BigDecimal.class);
    private static final Field<String> UNIT = field("unit", String.class);
    private static final Field<BigDecimal> UNIT_PRICE = field("unit_price", BigDecimal.class);
    private static final Field<BigDecimal> INVENTORY_UNIT_COST = field("unit_cost", BigDecimal.class);
    private static final Field<BigDecimal> TOTAL_PRICE = field("total_price", BigDecimal.class);
    private static final Field<String> MATCH_STATUS = field("match_status", String.class);
    private static final Field<UUID> MATCHED_PRODUCT_ID = field("matched_product_id", UUID.class);
    private static final Field<String> CANDIDATE_NAME = field("candidate_name", String.class);
    private static final Field<String> BASE_UNIT = field("base_unit", String.class);
    private static final Field<BigDecimal> MATCH_CONFIDENCE = field("match_confidence", BigDecimal.class);
    private static final Field<Boolean> REQUIRES_USER_ACTION = field("requires_user_action", Boolean.class);
    private static final Field<String> IDEMPOTENCY_KEY = field("idempotency_key", String.class);
    private static final Field<String> REQUEST_FINGERPRINT = field("request_fingerprint", String.class);
    private static final Field<UUID> PURCHASE_DOCUMENT_ID = field("purchase_document_id", UUID.class);
    private static final Field<UUID> RECEIPT_ID = field("receipt_id", UUID.class);
    private static final Field<UUID> CONFIRMED_BY = field("confirmed_by", UUID.class);
    private static final Field<OffsetDateTime> CONFIRMED_AT = field("confirmed_at", OffsetDateTime.class);
    private static final Field<BigDecimal> STOCK_QUANTITY = field("stock_quantity", BigDecimal.class);
    private static final Field<String> EVENT_TYPE = field("event_type", String.class);
    private static final Field<org.jooq.JSONB> EVENT_PAYLOAD = field("payload", org.jooq.JSONB.class);
    private static final Field<UUID> PRODUCT_ID = field("product_id", UUID.class);
    private static final Field<UUID> PURCHASE_RECEIPT_ID = field("purchase_receipt_id", UUID.class);
    private static final Field<String> CONFIRMATION_FINGERPRINT = field("request_fingerprint", String.class);

    private final org.jooq.DSLContext dsl;

    public JooqPurchaseReceivingRepository(org.jooq.DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<PreviewIdempotency> findIdempotency(BusinessId businessId, String idempotencyKey) {
        return dsl.select(ACCESS_KEY, REQUEST_FINGERPRINT, PREVIEW_ID).from(IDEMPOTENCY)
                .where(BUSINESS_ID.eq(businessId.value()).and(IDEMPOTENCY_KEY.eq(idempotencyKey)))
                .fetchOptional().map(row -> new PreviewIdempotency(
                        row.get(ACCESS_KEY), row.get(REQUEST_FINGERPRINT), row.get(PREVIEW_ID)));
    }

    @Override
    public Optional<PurchaseDocumentRecord> findDocumentByAccessKey(BusinessId businessId, String accessKey) {
        return dsl.select(ID, ACCESS_KEY, PAYLOAD_SHA256).from(DOCUMENTS)
                .where(BUSINESS_ID.eq(businessId.value()).and(ACCESS_KEY.eq(accessKey)))
                .fetchOptional().map(row -> new PurchaseDocumentRecord(
                        row.get(ID), row.get(ACCESS_KEY), row.get(PAYLOAD_SHA256)));
    }

    @Override
    public Optional<PurchasePreviewSnapshot> findPreview(BusinessId businessId, UUID previewId) {
        return findPreviewInternal(businessId, previewId, false);
    }

    @Override
    public Optional<PurchasePreviewSnapshot> findPreviewForUpdate(BusinessId businessId, UUID previewId) {
        return findPreviewInternal(businessId, previewId, true);
    }

    private Optional<PurchasePreviewSnapshot> findPreviewInternal(BusinessId businessId, UUID previewId, boolean lock) {
        var query = dsl.select(ID, DOCUMENT_ID, STATUS, VERSION).from(PREVIEWS)
                .where(BUSINESS_ID.eq(businessId.value()).and(ID.eq(previewId)));
        var preview = (lock ? query.forUpdate() : query).fetchOptional();
        if (preview.isEmpty()) return Optional.empty();
        var row = preview.get();
        var document = dsl.select(SOURCE, DOCUMENT_TYPE, ACCESS_KEY, ISSUED_AT, ISSUER_NAME,
                        ISSUER_TAX_ID, TOTAL).from(DOCUMENTS)
                .where(BUSINESS_ID.eq(businessId.value()).and(ID.eq(row.get(DOCUMENT_ID))))
                .fetchOptional().map(value -> new PurchaseDocument(
                        PurchaseDocument.Source.valueOf(value.get(SOURCE)),
                        PurchaseDocument.DocumentType.valueOf(value.get(DOCUMENT_TYPE)),
                        value.get(ACCESS_KEY), value.get(ISSUED_AT),
                        new PurchaseDocument.Issuer(value.get(ISSUER_NAME), value.get(ISSUER_TAX_ID)),
                        dsl.select(LINE_NUMBER, EXTERNAL_CODE, GTIN, RAW_DESCRIPTION, QUANTITY, UNIT,
                                        UNIT_PRICE, TOTAL_PRICE).from(DOCUMENT_ITEMS)
                                .where(BUSINESS_ID.eq(businessId.value()).and(DOCUMENT_ID.eq(row.get(DOCUMENT_ID))))
                                .orderBy(LINE_NUMBER.asc()).fetch().map(JooqPurchaseReceivingRepository::item),
                        value.get(TOTAL)));
        var matches = dsl.select(LINE_NUMBER, MATCH_STATUS, MATCHED_PRODUCT_ID, CANDIDATE_NAME, BASE_UNIT,
                        MATCH_CONFIDENCE, REQUIRES_USER_ACTION).from(PREVIEW_ITEMS)
                .where(BUSINESS_ID.eq(businessId.value()).and(PREVIEW_ID.eq(row.get(ID))))
                .orderBy(LINE_NUMBER.asc()).fetch(match -> new PurchaseDocumentMatch(
                        match.get(LINE_NUMBER), PurchaseDocumentMatch.Status.valueOf(match.get(MATCH_STATUS)),
                        match.get(MATCHED_PRODUCT_ID), match.get(CANDIDATE_NAME), match.get(BASE_UNIT),
                        match.get(MATCH_CONFIDENCE), Boolean.TRUE.equals(match.get(REQUIRES_USER_ACTION))));
        return document.map(value -> new PurchasePreviewSnapshot(row.get(ID), row.get(DOCUMENT_ID),
                row.get(STATUS), row.get(VERSION), value, matches));
    }

    @Override
    @Transactional
    public PurchasePreviewSnapshot createPreview(BusinessId businessId, PurchaseDocument document,
            String payloadSha256, String idempotencyKey, Instant now, java.util.List<PurchaseDocumentMatch> matches) {
        var documentId = UUID.randomUUID();
        var insertedDocument = dsl.insertInto(DOCUMENTS).columns(ID, BUSINESS_ID, SOURCE, DOCUMENT_TYPE,
                        ACCESS_KEY, ISSUED_AT, ISSUER_NAME, ISSUER_TAX_ID, TOTAL, PAYLOAD_SHA256, CREATED_AT)
                .values(documentId, businessId.value(), document.source().name(), document.documentType().name(),
                        document.accessKey(), document.issuedAt(), document.issuer().name(), document.issuer().taxId(),
                        document.total(), payloadSha256, time(now))
                .onConflict(BUSINESS_ID, ACCESS_KEY).doNothing().execute();
        if (insertedDocument == 0) {
            var storedDocument = dsl.select(ID, PAYLOAD_SHA256).from(DOCUMENTS)
                    .where(BUSINESS_ID.eq(businessId.value()).and(ACCESS_KEY.eq(document.accessKey())))
                    .fetchOne();
            if (storedDocument == null) throw new IllegalStateException("purchase document conflict was not readable");
            if (!payloadSha256.equals(storedDocument.get(PAYLOAD_SHA256))) {
                throw conflict("access key already belongs to another purchase document payload");
            }
            documentId = storedDocument.get(ID);
        } else {
            for (var item : document.items()) insertItem(DOCUMENT_ITEMS, businessId, documentId, item, null);
        }

        var previewId = UUID.randomUUID();
        var insertedPreview = dsl.insertInto(PREVIEWS).columns(ID, BUSINESS_ID, DOCUMENT_ID, STATUS, VERSION,
                        CREATED_AT, UPDATED_AT)
                .values(previewId, businessId.value(), documentId, "REVIEW_READY", 0L, time(now), time(now))
                .onConflict(BUSINESS_ID, DOCUMENT_ID).doNothing().execute();
        if (insertedPreview == 0) {
            previewId = dsl.select(ID).from(PREVIEWS)
                    .where(BUSINESS_ID.eq(businessId.value()).and(DOCUMENT_ID.eq(documentId)))
                    .fetchOne(ID);
        } else {
            for (var item : document.items()) {
                var match = matches.stream().filter(value -> value.lineNumber() == item.lineNumber()).findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("purchase item match is missing"));
                insertItem(PREVIEW_ITEMS, businessId, previewId, item, match);
            }
        }

        dsl.insertInto(IDEMPOTENCY).columns(BUSINESS_ID, IDEMPOTENCY_KEY, ACCESS_KEY,
                        REQUEST_FINGERPRINT, PREVIEW_ID, CREATED_AT)
                .values(businessId.value(), idempotencyKey, document.accessKey(), payloadSha256, previewId, time(now))
                .onConflict(BUSINESS_ID, IDEMPOTENCY_KEY).doNothing().execute();
        var storedIdempotency = findIdempotency(businessId, idempotencyKey).orElseThrow();
        if (!document.accessKey().equals(storedIdempotency.accessKey())
                || !payloadSha256.equals(storedIdempotency.requestFingerprint())) {
            throw conflict("Idempotency-Key was already used for another purchase document");
        }
        return findPreview(businessId, storedIdempotency.previewId()).orElseThrow();
    }

    @Override
    public Optional<PurchaseReceiptRepository.PurchaseReceiptResult> findReceiptByPreview(
            BusinessId businessId, UUID previewId) {
        return dsl.select(ID, STATUS).from(RECEIPTS)
                .where(BUSINESS_ID.eq(businessId.value()).and(field("preview_id", UUID.class).eq(previewId)))
                .fetchOptional(row -> receipt(businessId, row.get(ID), row.get(STATUS)));
    }

    @Override
    public Optional<PurchaseReceiptRepository.ConfirmationIdempotency> findConfirmationIdempotency(
            BusinessId businessId, String idempotencyKey) {
        return dsl.select(PREVIEW_ID, CONFIRMATION_FINGERPRINT, RECEIPT_ID).from(CONFIRMATION_IDEMPOTENCY)
                .where(BUSINESS_ID.eq(businessId.value()).and(IDEMPOTENCY_KEY.eq(idempotencyKey)))
                .fetchOptional(row -> new PurchaseReceiptRepository.ConfirmationIdempotency(
                        row.get(PREVIEW_ID), row.get(CONFIRMATION_FINGERPRINT), row.get(RECEIPT_ID)));
    }

    @Override
    public void recordConfirmationIdempotency(BusinessId businessId, String idempotencyKey, UUID previewId,
            String requestFingerprint, UUID receiptId, Instant now) {
        dsl.insertInto(CONFIRMATION_IDEMPOTENCY).columns(BUSINESS_ID, IDEMPOTENCY_KEY, PREVIEW_ID,
                        CONFIRMATION_FINGERPRINT, RECEIPT_ID, CREATED_AT)
                .values(businessId.value(), idempotencyKey, previewId, requestFingerprint, receiptId, time(now))
                .onConflict(BUSINESS_ID, IDEMPOTENCY_KEY).doNothing().execute();
    }

    @Override
    public void createReceipt(BusinessId businessId, UUID receiptId, UUID documentId, UUID previewId,
            UUID userId, Instant now) {
        dsl.insertInto(RECEIPTS).columns(ID, BUSINESS_ID, PURCHASE_DOCUMENT_ID, PREVIEW_ID, STATUS,
                        CONFIRMED_BY, CONFIRMED_AT, CREATED_AT)
                .values(receiptId, businessId.value(), documentId, previewId, "CONFIRMED", userId,
                        time(now), time(now)).execute();
    }

    @Override
    public void addReceiptItem(BusinessId businessId, UUID receiptId, PurchaseDocument.Item source,
            PurchaseDocumentMatch match, UUID productId, String matchStatus, String baseUnit,
            BigDecimal conversionFactor, BigDecimal stockQuantity) {
        dsl.insertInto(RECEIPT_ITEMS).columns(ID, BUSINESS_ID, RECEIPT_ID, LINE_NUMBER, PRODUCT_ID,
                        MATCH_STATUS, RAW_DESCRIPTION, EXTERNAL_CODE, GTIN, QUANTITY, UNIT, UNIT_PRICE,
                        TOTAL_PRICE, STOCK_QUANTITY, BASE_UNIT, field("conversion_factor", BigDecimal.class))
                .values(UUID.randomUUID(), businessId.value(), receiptId, source.lineNumber(), productId,
                        matchStatus, source.rawDescription(), source.externalCode(), source.gtin(), source.quantity(),
                        source.unit(), source.unitPrice(), source.totalPrice(), stockQuantity, baseUnit, conversionFactor)
                .execute();
    }

    @Override
    public void addInventoryMovement(BusinessId businessId, UUID receiptId, UUID productId,
            BigDecimal quantity, BigDecimal unitCost, Instant now) {
        dsl.insertInto(INVENTORY_MOVEMENTS).columns(ID, BUSINESS_ID, PRODUCT_ID, PURCHASE_RECEIPT_ID,
                        QUANTITY, INVENTORY_UNIT_COST, CREATED_AT)
                .values(UUID.randomUUID(), businessId.value(), productId, receiptId, quantity, unitCost, time(now))
                .execute();
        dsl.insertInto(INVENTORY_BALANCES).columns(BUSINESS_ID, PRODUCT_ID, QUANTITY, UPDATED_AT)
                .values(businessId.value(), productId, quantity, time(now))
                .onConflict(BUSINESS_ID, PRODUCT_ID).doUpdate()
                .set(QUANTITY, DSL.field(DSL.name("inventory_balances", "quantity"), BigDecimal.class).plus(quantity))
                .set(UPDATED_AT, time(now)).execute();
    }

    @Override
    public void addPriceObservation(BusinessId businessId, UUID receiptId, UUID productId,
            PurchaseDocument document, PurchaseDocument.Item item, Instant now) {
        dsl.insertInto(PRICE_OBSERVATIONS).columns(ID, BUSINESS_ID, PRODUCT_ID, RECEIPT_ID,
                        ISSUER_TAX_ID, UNIT_PRICE, QUANTITY, UNIT, field("observed_at", OffsetDateTime.class))
                .values(UUID.randomUUID(), businessId.value(), productId, receiptId, document.issuer().taxId(),
                        item.unitPrice(), item.quantity(), item.unit(), time(now)).execute();
    }

    @Override
    public void addEvent(BusinessId businessId, UUID receiptId, String eventType, String payload, Instant now) {
        dsl.insertInto(EVENTS).columns(ID, BUSINESS_ID, RECEIPT_ID, EVENT_TYPE, EVENT_PAYLOAD, CREATED_AT)
                .values(UUID.randomUUID(), businessId.value(), receiptId, eventType, org.jooq.JSONB.jsonb(payload), time(now))
                .execute();
    }

    @Override
    public void markPreviewConfirmed(BusinessId businessId, UUID previewId, Instant now) {
        dsl.update(PREVIEWS).set(STATUS, "CONFIRMED").set(VERSION, VERSION.plus(1L)).set(UPDATED_AT, time(now))
                .where(BUSINESS_ID.eq(businessId.value()).and(ID.eq(previewId))).execute();
    }

    @Override
    public Optional<PurchaseReceiptRepository.PurchaseReceiptResult> findReceipt(BusinessId businessId, UUID receiptId) {
        return dsl.select(ID, STATUS).from(RECEIPTS)
                .where(BUSINESS_ID.eq(businessId.value()).and(ID.eq(receiptId)))
                .fetchOptional(row -> receipt(businessId, row.get(ID), row.get(STATUS)));
    }

    @Override
    public java.util.List<PurchaseHistoryRepository.PurchaseHistoryEntry> findEntries(BusinessId businessId,
            Instant from, Instant to) {
        var r = RECEIPTS.as("history_receipt");
        var d = DOCUMENTS.as("history_document");
        var i = RECEIPT_ITEMS.as("history_item");
        var rId = DSL.field(DSL.name("history_receipt", "id"), UUID.class);
        var rBusiness = DSL.field(DSL.name("history_receipt", "business_id"), UUID.class);
        var rDocument = DSL.field(DSL.name("history_receipt", "purchase_document_id"), UUID.class);
        var rStatus = DSL.field(DSL.name("history_receipt", "status"), String.class);
        var rConfirmed = DSL.field(DSL.name("history_receipt", "confirmed_at"), OffsetDateTime.class);
        var dId = DSL.field(DSL.name("history_document", "id"), UUID.class);
        var dIssuer = DSL.field(DSL.name("history_document", "issuer_name"), String.class);
        var dTotal = DSL.field(DSL.name("history_document", "total"), BigDecimal.class);
        var iId = DSL.field(DSL.name("history_item", "id"), UUID.class);
        var iMatch = DSL.field(DSL.name("history_item", "match_status"), String.class);
        var iStock = DSL.field(DSL.name("history_item", "stock_quantity"), BigDecimal.class);
        var itemCount = DSL.count(iId).as("history_item_count");
        var newProductCount = DSL.count(iId).filterWhere(iMatch.eq("NEW_PRODUCT")).as("history_new_product_count");
        var stockQuantity = DSL.sum(iStock).as("history_stock_quantity");
        return dsl.select(rId, rConfirmed, dIssuer, dTotal, itemCount, newProductCount, stockQuantity)
                .from(r).join(d).on(rBusiness.eq(DSL.field(DSL.name("history_document", "business_id"), UUID.class))
                        .and(rDocument.eq(dId)))
                .leftJoin(i).on(rBusiness.eq(DSL.field(DSL.name("history_item", "business_id"), UUID.class))
                        .and(rId.eq(DSL.field(DSL.name("history_item", "receipt_id"), UUID.class))))
                .where(rBusiness.eq(businessId.value()).and(rStatus.eq("CONFIRMED"))
                        .and(rConfirmed.ge(time(from))).and(rConfirmed.lt(time(to))))
                .groupBy(rId, rConfirmed, dIssuer, dTotal).orderBy(rConfirmed.desc())
                .fetch(row -> new PurchaseHistoryRepository.PurchaseHistoryEntry(
                        row.get(rId), row.get(rConfirmed).toInstant(), row.get(dIssuer), row.get(dTotal),
                        row.get(itemCount), row.get(newProductCount), row.get(stockQuantity)));
    }

    @Override
    public Optional<PurchaseHistoryRepository.PurchaseHistoryDetail> findDetail(BusinessId businessId, UUID receiptId) {
        var r = RECEIPTS.as("detail_receipt");
        var d = DOCUMENTS.as("detail_document");
        var i = RECEIPT_ITEMS.as("detail_item");
        var rId = DSL.field(DSL.name("detail_receipt", "id"), UUID.class);
        var rBusiness = DSL.field(DSL.name("detail_receipt", "business_id"), UUID.class);
        var rDocument = DSL.field(DSL.name("detail_receipt", "purchase_document_id"), UUID.class);
        var rConfirmed = DSL.field(DSL.name("detail_receipt", "confirmed_at"), OffsetDateTime.class);
        var dId = DSL.field(DSL.name("detail_document", "id"), UUID.class);
        var dIssuer = DSL.field(DSL.name("detail_document", "issuer_name"), String.class);
        var dIssuerTax = DSL.field(DSL.name("detail_document", "issuer_tax_id"), String.class);
        var dAccessKey = DSL.field(DSL.name("detail_document", "access_key"), String.class);
        var dTotal = DSL.field(DSL.name("detail_document", "total"), BigDecimal.class);
        var iLine = DSL.field(DSL.name("detail_item", "line_number"), Integer.class);
        var iProduct = DSL.field(DSL.name("detail_item", "product_id"), UUID.class);
        var iDescription = DSL.field(DSL.name("detail_item", "raw_description"), String.class);
        var iQuantity = DSL.field(DSL.name("detail_item", "quantity"), BigDecimal.class);
        var iUnit = DSL.field(DSL.name("detail_item", "unit"), String.class);
        var iUnitPrice = DSL.field(DSL.name("detail_item", "unit_price"), BigDecimal.class);
        var iStock = DSL.field(DSL.name("detail_item", "stock_quantity"), BigDecimal.class);
        var iMatch = DSL.field(DSL.name("detail_item", "match_status"), String.class);
        var rows = dsl.select(rId, rConfirmed, dIssuer, dIssuerTax, dAccessKey, dTotal, iLine, iProduct,
                        iDescription, iQuantity, iUnit, iUnitPrice, iStock, iMatch)
                .from(r).join(d).on(rBusiness.eq(DSL.field(DSL.name("detail_document", "business_id"), UUID.class))
                        .and(rDocument.eq(dId)))
                .leftJoin(i).on(rBusiness.eq(DSL.field(DSL.name("detail_item", "business_id"), UUID.class))
                        .and(rId.eq(DSL.field(DSL.name("detail_item", "receipt_id"), UUID.class))))
                .where(rBusiness.eq(businessId.value()).and(rId.eq(receiptId)))
                .orderBy(iLine.asc()).fetch();
        if (rows.isEmpty()) return Optional.empty();
        var first = rows.get(0);
        var items = rows.stream().filter(row -> row.get(iLine) != null).map(row ->
                new PurchaseHistoryRepository.PurchaseHistoryItem(row.get(iLine), row.get(iProduct),
                        row.get(iDescription), row.get(iQuantity), row.get(iUnit), row.get(iUnitPrice),
                        row.get(iStock), row.get(iMatch))).toList();
        return Optional.of(new PurchaseHistoryRepository.PurchaseHistoryDetail(first.get(rId),
                rowInstant(first.get(rConfirmed)), first.get(dIssuer), first.get(dIssuerTax), first.get(dAccessKey),
                first.get(dTotal), items));
    }

    @Override
    public java.util.List<PurchaseHistoryRepository.PurchasePriceFact> findPriceFacts(BusinessId businessId,
            Instant from, Instant to) {
        var o = PRICE_OBSERVATIONS.as("history_observation");
        var p = DSL.table(DSL.name("public", "products")).as("history_product");
        var oId = DSL.field(DSL.name("history_observation", "id"), UUID.class);
        var oBusiness = DSL.field(DSL.name("history_observation", "business_id"), UUID.class);
        var oProduct = DSL.field(DSL.name("history_observation", "product_id"), UUID.class);
        var oReceipt = DSL.field(DSL.name("history_observation", "receipt_id"), UUID.class);
        var oPrice = DSL.field(DSL.name("history_observation", "unit_price"), BigDecimal.class);
        var oQuantity = DSL.field(DSL.name("history_observation", "quantity"), BigDecimal.class);
        var oUnit = DSL.field(DSL.name("history_observation", "unit"), String.class);
        var oObserved = DSL.field(DSL.name("history_observation", "observed_at"), OffsetDateTime.class);
        var pBusiness = DSL.field(DSL.name("history_product", "business_id"), UUID.class);
        var pId = DSL.field(DSL.name("history_product", "id"), UUID.class);
        var pName = DSL.field(DSL.name("history_product", "name"), String.class);
        var pSalePrice = DSL.field(DSL.name("history_product", "sale_price"), BigDecimal.class);
        return dsl.select(oId, oReceipt, oProduct, pName, oPrice, oQuantity, oUnit, oObserved, pSalePrice)
                .from(o).join(p).on(oBusiness.eq(pBusiness).and(oProduct.eq(pId)))
                .where(oBusiness.eq(businessId.value()).and(oObserved.ge(time(from))).and(oObserved.lt(time(to))))
                .orderBy(oObserved.asc(), oId.asc())
                .fetch(row -> new PurchaseHistoryRepository.PurchasePriceFact(row.get(oId), row.get(oReceipt),
                        row.get(oProduct), row.get(pName), row.get(oPrice), row.get(oQuantity), row.get(oUnit),
                        rowInstant(row.get(oObserved)), row.get(pSalePrice)));
    }

    private static Instant rowInstant(OffsetDateTime value) { return value == null ? null : value.toInstant(); }

    private PurchaseReceiptRepository.PurchaseReceiptResult receipt(BusinessId businessId, UUID receiptId,
            String status) {
        var rows = dsl.select(LINE_NUMBER, PRODUCT_ID, MATCH_STATUS, STOCK_QUANTITY, UNIT_PRICE)
                .from(RECEIPT_ITEMS).where(BUSINESS_ID.eq(businessId.value()).and(RECEIPT_ID.eq(receiptId)))
                .orderBy(LINE_NUMBER.asc()).fetch();
        var items = rows.map(row -> new PurchaseReceiptRepository.PurchaseReceiptItemResult(
                row.get(LINE_NUMBER), row.get(PRODUCT_ID), row.get(MATCH_STATUS), row.get(STOCK_QUANTITY), row.get(UNIT_PRICE)));
        return new PurchaseReceiptRepository.PurchaseReceiptResult(receiptId, status, items.size(), items);
    }

    private void insertItem(Table<?> table, BusinessId businessId, UUID ownerId, PurchaseDocument.Item item,
            PurchaseDocumentMatch match) {
        if (table == DOCUMENT_ITEMS) {
            dsl.insertInto(table).columns(ID, BUSINESS_ID, DOCUMENT_ID, LINE_NUMBER, EXTERNAL_CODE, GTIN,
                            RAW_DESCRIPTION, QUANTITY, UNIT, UNIT_PRICE, TOTAL_PRICE)
                    .values(UUID.randomUUID(), businessId.value(), ownerId, item.lineNumber(), item.externalCode(),
                            item.gtin(), item.rawDescription(), item.quantity(), item.unit(), item.unitPrice(), item.totalPrice())
                    .execute();
            return;
        }
        dsl.insertInto(table).columns(ID, BUSINESS_ID, PREVIEW_ID, LINE_NUMBER, EXTERNAL_CODE, GTIN,
                        RAW_DESCRIPTION, QUANTITY, UNIT, UNIT_PRICE, TOTAL_PRICE, MATCH_STATUS,
                        MATCHED_PRODUCT_ID, CANDIDATE_NAME, BASE_UNIT, MATCH_CONFIDENCE, REQUIRES_USER_ACTION)
                .values(UUID.randomUUID(), businessId.value(), ownerId, item.lineNumber(), item.externalCode(),
                        item.gtin(), item.rawDescription(), item.quantity(), item.unit(), item.unitPrice(), item.totalPrice(),
                        match.status().name(), match.productId(), match.candidateName(), match.baseUnit(),
                        match.confidence(), match.requiresUserAction())
                .execute();
    }

    private static PurchaseDocument.Item item(Record row) {
        return new PurchaseDocument.Item(row.get(LINE_NUMBER), row.get(EXTERNAL_CODE), row.get(GTIN),
                row.get(RAW_DESCRIPTION), row.get(QUANTITY), row.get(UNIT), row.get(UNIT_PRICE), row.get(TOTAL_PRICE));
    }

    private static Table<?> table(String name) { return DSL.table(DSL.name("public", name)); }
    private static <T> Field<T> field(String name, Class<T> type) { return DSL.field(DSL.name(name), type); }
    private static OffsetDateTime time(Instant value) { return value.atOffset(ZoneOffset.UTC); }

    private static ReceivingException conflict(String message) {
        return new ReceivingException(ReceivingErrorCode.IDEMPOTENCY_CONFLICT, message, false, 409);
    }
}
