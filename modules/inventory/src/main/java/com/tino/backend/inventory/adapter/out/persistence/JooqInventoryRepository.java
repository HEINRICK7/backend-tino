package com.tino.backend.inventory.adapter.out.persistence;

import com.tino.backend.inventory.application.port.out.InventoryPort;
import com.tino.backend.shared.kernel.BusinessId;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
public class JooqInventoryRepository implements InventoryPort {
    private static final Table<?> MOVEMENTS = table("inventory_movements");
    private static final Table<?> BALANCES = table("inventory_balances");
    private static final Field<UUID> ID = field("id", UUID.class);
    private static final Field<UUID> BUSINESS_ID = field("business_id", UUID.class);
    private static final Field<UUID> PRODUCT_ID = field("product_id", UUID.class);
    private static final Field<UUID> RECEIPT_ID = field("receipt_id", UUID.class);
    private static final Field<BigDecimal> QUANTITY = field("quantity", BigDecimal.class);
    private static final Field<BigDecimal> UNIT_COST = field("unit_cost", BigDecimal.class);
    private static final Field<OffsetDateTime> UPDATED_AT = field("updated_at", OffsetDateTime.class);
    private final DSLContext dsl;
    public JooqInventoryRepository(DSLContext dsl) { this.dsl = dsl; }
    @Override
    public void receive(BusinessId businessId, UUID receiptId, UUID productId, BigDecimal quantity, BigDecimal unitCost, Instant now) {
        var inserted = dsl.insertInto(MOVEMENTS).columns(ID, BUSINESS_ID, PRODUCT_ID, RECEIPT_ID, QUANTITY, UNIT_COST, DSL.field("created_at", OffsetDateTime.class))
                .values(UUID.randomUUID(), businessId.value(), productId, receiptId, quantity, unitCost, time(now)).onConflict(BUSINESS_ID, RECEIPT_ID, PRODUCT_ID).doNothing().execute();
        if (inserted == 0) return;
        dsl.insertInto(BALANCES).columns(BUSINESS_ID, PRODUCT_ID, QUANTITY, UPDATED_AT).values(businessId.value(), productId, quantity, time(now))
                .onConflict(BUSINESS_ID, PRODUCT_ID).doUpdate().set(QUANTITY, BALANCES.field(QUANTITY).plus(quantity)).set(UPDATED_AT, time(now)).execute();
    }
    private static Table<?> table(String name) { return DSL.table(DSL.name("public", name)); }
    private static <T> Field<T> field(String name, Class<T> type) { return DSL.field(DSL.name(name), type); }
    private static OffsetDateTime time(Instant value) { return value.atOffset(ZoneOffset.UTC); }
}
