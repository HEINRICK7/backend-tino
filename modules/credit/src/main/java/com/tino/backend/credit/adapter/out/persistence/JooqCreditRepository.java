package com.tino.backend.credit.adapter.out.persistence;

import com.tino.backend.credit.application.port.out.CreditPersistenceException;
import com.tino.backend.credit.application.port.out.CreditRepository;
import com.tino.backend.credit.domain.model.CreditAccount;
import com.tino.backend.credit.domain.model.CreditDirection;
import com.tino.backend.credit.domain.model.CreditLedgerEntry;
import com.tino.backend.shared.kernel.BusinessId;
import java.math.BigDecimal;
import java.time.Instant;
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
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JooqCreditRepository implements CreditRepository {
    private static final Table<?> CUSTOMERS = table("customers");
    private static final Table<?> ACCOUNTS = table("credit_accounts");
    private static final Table<?> ENTRIES = table("credit_ledger_entries");
    private static final Table<?> IDEMPOTENCY = table("credit_idempotency_keys");
    private static final Table<?> AUDIT = table("credit_audit_records");

    private static final Field<UUID> ID = field("id", UUID.class);
    private static final Field<UUID> BUSINESS_ID = field("business_id", UUID.class);
    private static final Field<UUID> CUSTOMER_ID = field("customer_id", UUID.class);
    private static final Field<UUID> ACCOUNT_ID = field("account_id", UUID.class);
    private static final Field<String> CURRENCY = field("currency", String.class);
    private static final Field<BigDecimal> BALANCE = field("balance", BigDecimal.class);
    private static final Field<Long> VERSION = field("version", Long.class);
    private static final Field<String> STATUS = field("status", String.class);
    private static final Field<String> DIRECTION = field("direction", String.class);
    private static final Field<BigDecimal> AMOUNT = field("amount", BigDecimal.class);
    private static final Field<String> REASON = field("reason", String.class);
    private static final Field<UUID> COMPENSATES_ENTRY_ID = field("compensates_entry_id", UUID.class);
    private static final Field<UUID> ACTOR_USER_ID = field("actor_user_id", UUID.class);
    private static final Field<OffsetDateTime> CREATED_AT = field("created_at", OffsetDateTime.class);
    private static final Field<OffsetDateTime> UPDATED_AT = field("updated_at", OffsetDateTime.class);
    private static final Field<String> OPERATION = field("operation", String.class);
    private static final Field<String> IDEMPOTENCY_KEY = field("idempotency_key", String.class);
    private static final Field<String> FINGERPRINT = field("request_fingerprint", String.class);
    private static final Field<UUID> ENTRY_ID = field("entry_id", UUID.class);

    private final DSLContext dsl;

    public JooqCreditRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean customerExists(BusinessId businessId, UUID customerId) {
        try {
            return dsl.selectOne().from(CUSTOMERS)
                    .where(BUSINESS_ID.eq(businessId.value()).and(ID.eq(customerId)))
                    .fetchOptional().isPresent();
        } catch (RuntimeException exception) {
            throw translate(exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CreditAccount> findAccount(BusinessId businessId, UUID customerId) {
        try {
            return dsl.select(ID, BUSINESS_ID, CUSTOMER_ID, CURRENCY, BALANCE, VERSION, CREATED_AT, UPDATED_AT)
                    .from(ACCOUNTS)
                    .where(BUSINESS_ID.eq(businessId.value()).and(CUSTOMER_ID.eq(customerId)))
                    .fetchOptional().map(JooqCreditRepository::toAccount);
        } catch (RuntimeException exception) {
            throw translate(exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CreditAccount> findAccountById(BusinessId businessId, UUID accountId, boolean lock) {
        try {
            var query = dsl.select(ID, BUSINESS_ID, CUSTOMER_ID, CURRENCY, BALANCE, VERSION, CREATED_AT, UPDATED_AT)
                    .from(ACCOUNTS)
                    .where(BUSINESS_ID.eq(businessId.value()).and(ID.eq(accountId)));
            return (lock ? query.forUpdate() : query).fetchOptional().map(JooqCreditRepository::toAccount);
        } catch (RuntimeException exception) {
            throw translate(exception);
        }
    }

    @Override
    public CreditAccount findOrCreateAccountForUpdate(
            BusinessId businessId, UUID customerId, UUID proposedAccountId, Instant now) {
        try {
            dsl.insertInto(ACCOUNTS)
                    .columns(ID, BUSINESS_ID, CUSTOMER_ID, CURRENCY, BALANCE, VERSION, STATUS, CREATED_AT, UPDATED_AT)
                    .values(proposedAccountId, businessId.value(), customerId, "BRL", BigDecimal.ZERO.setScale(2),
                            0L, "ACTIVE", toDatabaseTime(now), toDatabaseTime(now))
                    .onConflict(BUSINESS_ID, CUSTOMER_ID, CURRENCY).doNothing()
                    .execute();
            return dsl.select(ID, BUSINESS_ID, CUSTOMER_ID, CURRENCY, BALANCE, VERSION, CREATED_AT, UPDATED_AT)
                    .from(ACCOUNTS)
                    .where(BUSINESS_ID.eq(businessId.value()).and(CUSTOMER_ID.eq(customerId))
                            .and(CURRENCY.eq("BRL")))
                    .forUpdate()
                    .fetchOptional().map(JooqCreditRepository::toAccount)
                    .orElseThrow(() -> new IllegalStateException("credit account disappeared"));
        } catch (RuntimeException exception) {
            throw translate(exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CreditLedgerEntry> findEntry(
            BusinessId businessId, UUID customerId, UUID entryId, boolean lock) {
        try {
            var condition = BUSINESS_ID.eq(businessId.value()).and(CUSTOMER_ID.eq(customerId))
                    .and(ID.eq(entryId));
            var query = dsl.select(ID, BUSINESS_ID, ACCOUNT_ID, CUSTOMER_ID, DIRECTION, AMOUNT, REASON,
                            COMPENSATES_ENTRY_ID, ACTOR_USER_ID, CREATED_AT)
                    .from(ENTRIES).where(condition);
            return (lock ? query.forUpdate() : query).fetchOptional().map(JooqCreditRepository::toEntry);
        } catch (RuntimeException exception) {
            throw translate(exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CreditLedgerEntry> findCompensation(BusinessId businessId, UUID originalEntryId) {
        try {
            return dsl.select(ID, BUSINESS_ID, ACCOUNT_ID, CUSTOMER_ID, DIRECTION, AMOUNT, REASON,
                            COMPENSATES_ENTRY_ID, ACTOR_USER_ID, CREATED_AT)
                    .from(ENTRIES)
                    .where(BUSINESS_ID.eq(businessId.value()).and(COMPENSATES_ENTRY_ID.eq(originalEntryId)))
                    .fetchOptional().map(JooqCreditRepository::toEntry);
        } catch (RuntimeException exception) {
            throw translate(exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<IdempotencyRecord> findIdempotency(
            BusinessId businessId, String operation, String key) {
        try {
            return dsl.select(FINGERPRINT, ENTRY_ID).from(IDEMPOTENCY)
                    .where(BUSINESS_ID.eq(businessId.value()).and(OPERATION.eq(operation))
                            .and(IDEMPOTENCY_KEY.eq(key)))
                    .fetchOptional().map(record -> new IdempotencyRecord(
                            record.get(FINGERPRINT), record.get(ENTRY_ID)));
        } catch (RuntimeException exception) {
            throw translate(exception);
        }
    }

    @Override
    public boolean claimIdempotency(BusinessId businessId, String operation, String key,
            String fingerprint, UUID entryId, Instant createdAt) {
        try {
            return dsl.insertInto(IDEMPOTENCY)
                    .columns(BUSINESS_ID, OPERATION, IDEMPOTENCY_KEY, FINGERPRINT, ENTRY_ID, CREATED_AT)
                    .values(businessId.value(), operation, key, fingerprint, entryId, toDatabaseTime(createdAt))
                    .onConflict(BUSINESS_ID, OPERATION, IDEMPOTENCY_KEY).doNothing()
                    .execute() == 1;
        } catch (RuntimeException exception) {
            throw translate(exception);
        }
    }

    @Override
    public void insertEntry(CreditLedgerEntry entry) {
        try {
            dsl.insertInto(ENTRIES)
                    .columns(ID, BUSINESS_ID, ACCOUNT_ID, CUSTOMER_ID, DIRECTION, AMOUNT, REASON,
                            COMPENSATES_ENTRY_ID, ACTOR_USER_ID, CREATED_AT)
                    .values(entry.id(), entry.businessId().value(), entry.accountId(), entry.customerId(),
                            entry.direction().name(), entry.amount().value(), entry.reason(),
                            entry.compensatesEntryId(), entry.actorUserId(), toDatabaseTime(entry.createdAt()))
                    .execute();
        } catch (RuntimeException exception) {
            throw translate(exception);
        }
    }

    @Override
    public void insertAudit(AuditRecord audit) {
        try {
            dsl.insertInto(AUDIT)
                    .columns(ID, BUSINESS_ID, OPERATION, ENTRY_ID, IDEMPOTENCY_KEY, ACTOR_USER_ID,
                            FINGERPRINT, CREATED_AT)
                    .values(audit.id(), audit.businessId().value(), audit.operation(), audit.entryId(),
                            audit.idempotencyKey(), audit.actorUserId(), audit.requestFingerprint(),
                            toDatabaseTime(audit.createdAt()))
                    .execute();
        } catch (RuntimeException exception) {
            throw translate(exception);
        }
    }

    private static CreditAccount toAccount(Record record) {
        return new CreditAccount(record.get(ID), new BusinessId(record.get(BUSINESS_ID)), record.get(CUSTOMER_ID),
                record.get(CURRENCY).trim(), record.get(BALANCE), record.get(VERSION),
                record.get(CREATED_AT).toInstant(), record.get(UPDATED_AT).toInstant());
    }

    private static CreditLedgerEntry toEntry(Record record) {
        return new CreditLedgerEntry(record.get(ID), new BusinessId(record.get(BUSINESS_ID)),
                record.get(ACCOUNT_ID), record.get(CUSTOMER_ID), CreditDirection.valueOf(record.get(DIRECTION)),
                new com.tino.backend.credit.domain.model.CreditAmount(record.get(AMOUNT)), record.get(REASON),
                record.get(COMPENSATES_ENTRY_ID), record.get(ACTOR_USER_ID), record.get(CREATED_AT).toInstant());
    }

    private static OffsetDateTime toDatabaseTime(Instant value) {
        return value.atOffset(ZoneOffset.UTC);
    }

    private static CreditPersistenceException translate(RuntimeException exception) {
        if (exception instanceof CreditPersistenceException persistence) {
            return persistence;
        }
        return new CreditPersistenceException(exception);
    }

    private static Table<?> table(String name) {
        return DSL.table(DSL.name("public", name));
    }

    private static <T> Field<T> field(String name, Class<T> type) {
        return DSL.field(DSL.name(name), type);
    }
}
