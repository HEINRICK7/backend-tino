package com.tino.backend.messaging.adapter.out.persistence;

import com.tino.backend.messaging.application.model.WhatsAppStatementSnapshot;
import com.tino.backend.messaging.application.port.out.WhatsAppStatementDataSource;
import com.tino.backend.messaging.domain.model.DebtDisplayStatus;
import com.tino.backend.messaging.domain.model.DebtEntryDisplayType;
import com.tino.backend.messaging.domain.model.DebtEntryView;
import com.tino.backend.messaging.domain.model.DebtStatementView;
import com.tino.backend.messaging.domain.model.MoneyView;
import com.tino.backend.shared.kernel.BusinessId;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
public class JooqWhatsAppStatementDataSource implements WhatsAppStatementDataSource {
    private static final Table<?> ACCOUNTS = table("credit_accounts").as("account");
    private static final Table<?> CUSTOMERS = table("customers").as("customer");
    private static final Table<?> BUSINESSES = table("businesses").as("business");
    private static final Table<?> ENTRIES = table("credit_ledger_entries");
    private static final Field<UUID> ACCOUNT_ID = qualified("account", "id", UUID.class);
    private static final Field<UUID> BUSINESS_ID = qualified("account", "business_id", UUID.class);
    private static final Field<UUID> CUSTOMER_ID = qualified("account", "customer_id", UUID.class);
    private static final Field<String> CURRENCY = qualified("account", "currency", String.class);
    private static final Field<BigDecimal> BALANCE = qualified("account", "balance", BigDecimal.class);
    private static final Field<Long> VERSION = qualified("account", "version", Long.class);
    private static final Field<String> CUSTOMER_NAME = qualified("customer", "name", String.class);
    private static final Field<String> PHONE = qualified("customer", "phone", String.class);
    private static final Field<String> BUSINESS_NAME = qualified("business", "trade_name", String.class);
    private static final Field<UUID> ENTRY_ID = field("id", UUID.class);
    private static final Field<String> ENTRY_DIRECTION = field("direction", String.class);
    private static final Field<BigDecimal> ENTRY_AMOUNT = field("amount", BigDecimal.class);
    private static final Field<String> ENTRY_REASON = field("reason", String.class);
    private static final Field<OffsetDateTime> ENTRY_CREATED_AT = field("created_at", OffsetDateTime.class);
    private final DSLContext dsl;

    public JooqWhatsAppStatementDataSource(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<WhatsAppStatementSnapshot> find(BusinessId businessId, UUID accountId) {
        try {
            var account = dsl.select(ACCOUNT_ID, BUSINESS_ID, CUSTOMER_ID, CURRENCY, BALANCE, VERSION,
                            CUSTOMER_NAME, PHONE, BUSINESS_NAME)
                    .from(ACCOUNTS)
                    .join(CUSTOMERS).on(BUSINESS_ID.eq(qualified("customer", "business_id", UUID.class))
                            .and(CUSTOMER_ID.eq(qualified("customer", "id", UUID.class))))
                    .join(BUSINESSES).on(BUSINESS_ID.eq(qualified("business", "id", UUID.class)))
                    .where(BUSINESS_ID.eq(businessId.value()).and(ACCOUNT_ID.eq(accountId)))
                    .fetchOptional();
            if (account.isEmpty()) {
                return Optional.empty();
            }
            var row = account.orElseThrow();
            var entries = dsl.select(ENTRY_DIRECTION, ENTRY_AMOUNT, ENTRY_REASON, ENTRY_CREATED_AT)
                    .from(ENTRIES)
                    .where(field("business_id", UUID.class).eq(businessId.value())
                            .and(field("account_id", UUID.class).eq(accountId)))
                    .orderBy(ENTRY_CREATED_AT.asc(), ENTRY_ID.asc())
                    .fetch()
                    .map(entry -> new DebtEntryView(
                            entry.get(ENTRY_CREATED_AT).toLocalDate(),
                            displayType(entry.get(ENTRY_DIRECTION)),
                            description(entry.get(ENTRY_DIRECTION), entry.get(ENTRY_REASON)),
                            new MoneyView(row.get(CURRENCY).trim(), entry.get(ENTRY_AMOUNT))));
            var balance = row.get(BALANCE);
            var generatedAt = Instant.now();
            var statement = new DebtStatementView(row.get(BUSINESS_NAME), row.get(CUSTOMER_NAME),
                    new MoneyView(row.get(CURRENCY).trim(), balance),
                    balance.signum() == 0 ? DebtDisplayStatus.SETTLED : DebtDisplayStatus.OPEN,
                    entries, generatedAt);
            return Optional.of(new WhatsAppStatementSnapshot(businessId, row.get(ACCOUNT_ID), row.get(CUSTOMER_ID),
                    row.get(PHONE), row.get(VERSION), statement));
        } catch (RuntimeException exception) {
            throw new IllegalStateException("could not read WhatsApp statement", exception);
        }
    }

    private static DebtEntryDisplayType displayType(String direction) {
        return switch (direction) {
            case "CREDIT" -> DebtEntryDisplayType.PURCHASE;
            case "DEBIT" -> DebtEntryDisplayType.PAYMENT;
            default -> DebtEntryDisplayType.ADJUSTMENT;
        };
    }

    private static String description(String direction, String reason) {
        var normalized = reason == null ? "" : reason.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (direction) {
            case "CREDIT" -> switch (normalized) {
                case "PURCHASE" -> "Compra fiada";
                default -> "Lançamento de crédito";
            };
            case "DEBIT" -> switch (normalized) {
                case "PAYMENT" -> "Pagamento";
                default -> "Pagamento registrado";
            };
            default -> "Ajuste";
        };
    }

    private static Table<?> table(String name) {
        return DSL.table(DSL.name("public", name));
    }

    private static <T> Field<T> qualified(String table, String name, Class<T> type) {
        return DSL.field(DSL.name(table, name), type);
    }

    private static <T> Field<T> field(String name, Class<T> type) {
        return DSL.field(DSL.name(name), type);
    }
}
