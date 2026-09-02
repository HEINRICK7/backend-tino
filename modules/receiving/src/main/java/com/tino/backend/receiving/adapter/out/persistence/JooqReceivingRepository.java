package com.tino.backend.receiving.adapter.out.persistence;

import com.tino.backend.catalog.application.model.ProductResolution;
import com.tino.backend.receiving.application.model.PreviewItem;
import com.tino.backend.receiving.application.model.PreviewSnapshot;
import com.tino.backend.receiving.application.model.GoodsReceiptItemResult;
import com.tino.backend.receiving.application.model.GoodsReceiptPreviewStatus;
import com.tino.backend.receiving.application.model.GoodsReceiptResult;
import com.tino.backend.receiving.application.model.GoodsReceiptStatus;
import com.tino.backend.receiving.application.port.out.ReceivingRepository;
import com.tino.backend.shared.kernel.BusinessId;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
public class JooqReceivingRepository implements ReceivingRepository {
    private static final Table<?> PREVIEWS = table("goods_receipt_previews");
    private static final Table<?> ITEMS = table("goods_receipt_preview_items");
    private static final Table<?> RECEIPTS = table("goods_receipts");
    private static final Table<?> RECEIPT_ITEMS = table("goods_receipt_items");
    private static final Field<UUID> ID = field("id", UUID.class); private static final Field<UUID> BUSINESS_ID = field("business_id", UUID.class);
    private static final Field<UUID> DOCUMENT_ID = field("document_id", UUID.class); private static final Field<UUID> PREVIEW_ID = field("preview_id", UUID.class);
    private static final Field<String> STATUS = field("status", String.class); private static final Field<Long> VERSION = field("version", Long.class);
    private static final Field<OffsetDateTime> CREATED_AT = field("created_at", OffsetDateTime.class); private static final Field<OffsetDateTime> UPDATED_AT = field("updated_at", OffsetDateTime.class);
    private static final Field<Integer> LINE = field("line_number", Integer.class); private static final Field<String> RESOLUTION = field("resolution_status", String.class);
    private static final Field<UUID> PRODUCT_ID = field("product_id", UUID.class); private static final Field<String> CANDIDATE = field("candidate_name", String.class);
    private static final Field<String> PURCHASE_UNIT = field("purchase_unit", String.class); private static final Field<BigDecimal> PURCHASE_QTY = field("purchase_quantity", BigDecimal.class);
    private static final Field<String> BASE_UNIT = field("base_unit", String.class); private static final Field<BigDecimal> FACTOR = field("conversion_factor", BigDecimal.class);
    private static final Field<BigDecimal> UNIT_COST = field("unit_cost", BigDecimal.class); private static final Field<UUID> RECEIPT_ID = field("receipt_id", UUID.class);
    private final DSLContext dsl;
    public JooqReceivingRepository(DSLContext dsl) { this.dsl = dsl; }
    @Override public PreviewSnapshot createPreview(BusinessId businessId, UUID documentId, List<PreviewItem> values, Instant now) {
        var id = UUID.randomUUID(); dsl.insertInto(PREVIEWS).columns(ID, BUSINESS_ID, DOCUMENT_ID, STATUS, VERSION, CREATED_AT, UPDATED_AT)
                .values(id, businessId.value(), documentId, values.stream().allMatch(v -> v.resolutionStatus() == ProductResolution.Status.MATCHED) ? "READY" : "REVIEW_REQUIRED", 0L, time(now), time(now)).execute();
        for (var value : values) dsl.insertInto(ITEMS).columns(ID, BUSINESS_ID, PREVIEW_ID, LINE, RESOLUTION, PRODUCT_ID, CANDIDATE, PURCHASE_UNIT, PURCHASE_QTY, BASE_UNIT, FACTOR, UNIT_COST)
                .values(UUID.randomUUID(), businessId.value(), id, value.lineNumber(), value.resolutionStatus().name(), value.productId(), value.candidateName(), value.purchaseUnit(), value.purchaseQuantity(), value.baseUnit(), value.conversionFactor(), value.unitCost()).execute();
        return findPreview(businessId, id, false).orElseThrow();
    }
    @Override public Optional<PreviewSnapshot> findPreview(BusinessId businessId, UUID previewId, boolean lock) {
        var query = dsl.select(ID, DOCUMENT_ID, STATUS, VERSION).from(PREVIEWS).where(BUSINESS_ID.eq(businessId.value()).and(ID.eq(previewId)));
        var row = (lock ? query.forUpdate() : query).fetchOptional();
        return row.map(r -> new PreviewSnapshot(r.get(ID), r.get(DOCUMENT_ID), GoodsReceiptPreviewStatus.valueOf(r.get(STATUS)), r.get(VERSION), dsl.select(LINE, RESOLUTION, PRODUCT_ID, CANDIDATE, PURCHASE_UNIT, PURCHASE_QTY, BASE_UNIT, FACTOR, UNIT_COST).from(ITEMS).where(BUSINESS_ID.eq(businessId.value()).and(PREVIEW_ID.eq(previewId))).orderBy(LINE.asc()).fetch().map(i -> new PreviewItem(i.get(LINE), ProductResolution.Status.valueOf(i.get(RESOLUTION)), i.get(PRODUCT_ID), i.get(CANDIDATE), i.get(PURCHASE_UNIT), i.get(PURCHASE_QTY), i.get(BASE_UNIT), i.get(FACTOR), i.get(UNIT_COST)))));
    }
    @Override public Optional<PreviewSnapshot> findPreviewByDocument(BusinessId businessId, UUID documentId) {
        return dsl.select(ID).from(PREVIEWS).where(BUSINESS_ID.eq(businessId.value()).and(DOCUMENT_ID.eq(documentId))).fetchOptional(ID).flatMap(id -> findPreview(businessId, id, false));
    }
    @Override public Optional<GoodsReceiptResult> findReceiptByPreview(BusinessId businessId, UUID previewId) { return dsl.select(ID, STATUS).from(RECEIPTS).where(BUSINESS_ID.eq(businessId.value()).and(PREVIEW_ID.eq(previewId))).fetchOptional().map(row -> receipt(businessId, row.get(ID), GoodsReceiptStatus.valueOf(row.get(STATUS)))); }
    @Override public Optional<GoodsReceiptResult> findReceipt(BusinessId businessId, UUID receiptId) { return dsl.select(ID, STATUS).from(RECEIPTS).where(BUSINESS_ID.eq(businessId.value()).and(ID.eq(receiptId))).fetchOptional().map(row -> receipt(businessId, row.get(ID), GoodsReceiptStatus.valueOf(row.get(STATUS)))); }
    @Override public void createReceipt(BusinessId businessId, UUID receiptId, UUID documentId, UUID previewId, UUID userId, Instant now) { dsl.insertInto(RECEIPTS).columns(ID, BUSINESS_ID, DOCUMENT_ID, PREVIEW_ID, STATUS, field("confirmed_by", UUID.class), field("confirmed_at", OffsetDateTime.class), CREATED_AT).values(receiptId, businessId.value(), documentId, previewId, "CONFIRMED", userId, time(now), time(now)).execute(); }
    @Override public void addReceiptItem(BusinessId businessId, UUID receiptId, int lineNumber, UUID productId, BigDecimal purchaseQuantity, BigDecimal conversionFactor, BigDecimal stockQuantity, BigDecimal unitCost) { dsl.insertInto(RECEIPT_ITEMS).columns(ID, BUSINESS_ID, RECEIPT_ID, LINE, PRODUCT_ID, PURCHASE_QTY, FACTOR, field("stock_quantity", BigDecimal.class), UNIT_COST).values(UUID.randomUUID(), businessId.value(), receiptId, lineNumber, productId, purchaseQuantity, conversionFactor, stockQuantity, unitCost).execute(); }
    @Override public void markPreviewConfirmed(BusinessId businessId, UUID previewId, Instant now) { dsl.update(PREVIEWS).set(STATUS, "CONFIRMED").set(VERSION, VERSION.plus(1L)).set(UPDATED_AT, time(now)).where(BUSINESS_ID.eq(businessId.value()).and(ID.eq(previewId))).execute(); }
    private GoodsReceiptResult receipt(BusinessId businessId, UUID receiptId, GoodsReceiptStatus status) {
        var productName = DSL.field(DSL.name("products", "name"), String.class);
        var productBaseUnit = DSL.field(DSL.name("products", "base_unit"), String.class);
        var stockQuantity = DSL.field(DSL.name("goods_receipt_items", "stock_quantity"), BigDecimal.class);
        var products = table("products");
        var productBusinessId = DSL.field(DSL.name("products", "business_id"), UUID.class);
        var productKey = DSL.field(DSL.name("products", "id"), UUID.class);
        var receiptBusinessId = DSL.field(DSL.name("goods_receipt_items", "business_id"), UUID.class);
        var receiptIdField = DSL.field(DSL.name("goods_receipt_items", "receipt_id"), UUID.class);
        var items = dsl.select(DSL.field(DSL.name("goods_receipt_items", "line_number"), Integer.class),
                        DSL.field(DSL.name("goods_receipt_items", "product_id"), UUID.class), productName,
                        productBaseUnit, stockQuantity,
                        DSL.field(DSL.name("goods_receipt_items", "unit_cost"), BigDecimal.class))
                .from(RECEIPT_ITEMS).leftJoin(products)
                .on(productBusinessId.eq(businessId.value()).and(productKey.eq(PRODUCT_ID)))
                .where(receiptBusinessId.eq(businessId.value()).and(receiptIdField.eq(receiptId)))
                .orderBy(DSL.field(DSL.name("goods_receipt_items", "line_number"), Integer.class).asc())
                .fetch().map(row -> new GoodsReceiptItemResult(
                        row.get(DSL.field(DSL.name("goods_receipt_items", "line_number"), Integer.class)),
                        row.get(DSL.field(DSL.name("goods_receipt_items", "product_id"), UUID.class)),
                        row.get(productName), row.get(productBaseUnit), row.get(stockQuantity),
                        row.get(DSL.field(DSL.name("goods_receipt_items", "unit_cost"), BigDecimal.class))));
        return new GoodsReceiptResult(receiptId, status, items.size(), items);
    }
    private static Table<?> table(String name) { return DSL.table(DSL.name("public", name)); }
    private static <T> Field<T> field(String name, Class<T> type) { return DSL.field(DSL.name(name), type); }
    private static OffsetDateTime time(Instant value) { return value.atOffset(ZoneOffset.UTC); }
}
