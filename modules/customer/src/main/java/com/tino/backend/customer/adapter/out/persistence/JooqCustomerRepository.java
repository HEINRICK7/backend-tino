package com.tino.backend.customer.adapter.out.persistence;

import com.tino.backend.customer.application.port.out.CustomerRepository;
import com.tino.backend.customer.application.port.out.CustomerPersistenceException;
import com.tino.backend.customer.domain.model.Customer;
import com.tino.backend.customer.domain.model.CustomerStatus;
import com.tino.backend.shared.kernel.BusinessId;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
public class JooqCustomerRepository implements CustomerRepository {
    private static final Table<?> CUSTOMERS = table("customers");
    private static final Table<?> KEYS = table("customer_idempotency_keys");
    private static final Field<UUID> ID = field("id", UUID.class);
    private static final Field<UUID> BUSINESS_ID = field("business_id", UUID.class);
    private static final Field<String> NAME = field("name", String.class);
    private static final Field<String> NICKNAME = field("nickname", String.class);
    private static final Field<String> PHONE = field("phone", String.class);
    private static final Field<String> STATUS = field("status", String.class);
    private static final Field<OffsetDateTime> CREATED_AT = field("created_at", OffsetDateTime.class);
    private static final Field<OffsetDateTime> UPDATED_AT = field("updated_at", OffsetDateTime.class);
    private static final Field<String> IDEMPOTENCY_KEY = field("idempotency_key", String.class);
    private static final Field<String> FINGERPRINT = field("request_fingerprint", String.class);
    private static final Field<UUID> KEY_CUSTOMER_ID = field("customer_id", UUID.class);

    private final DSLContext dsl;

    public JooqCustomerRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<Customer> find(BusinessId businessId, UUID customerId) {
        try {
            return dsl.select(ID, BUSINESS_ID, NAME, NICKNAME, PHONE, STATUS, CREATED_AT, UPDATED_AT)
                    .from(CUSTOMERS)
                    .where(BUSINESS_ID.eq(businessId.value()).and(ID.eq(customerId)))
                    .fetchOptional().map(JooqCustomerRepository::toCustomer);
        } catch (RuntimeException exception) {
            throw new CustomerPersistenceException(exception);
        }
    }

    @Override
    public List<Customer> findActive(BusinessId businessId) {
        try {
            return dsl.select(ID, BUSINESS_ID, NAME, NICKNAME, PHONE, STATUS, CREATED_AT, UPDATED_AT)
                    .from(CUSTOMERS)
                    .where(BUSINESS_ID.eq(businessId.value()).and(STATUS.eq(CustomerStatus.ACTIVE.name())))
                    .orderBy(UPDATED_AT.asc(), ID.asc())
                    .fetch().map(JooqCustomerRepository::toCustomer);
        } catch (RuntimeException exception) {
            throw new CustomerPersistenceException(exception);
        }
    }

    @Override
    public void insert(Customer customer) {
        try {
            dsl.insertInto(CUSTOMERS)
                    .columns(ID, BUSINESS_ID, NAME, NICKNAME, PHONE, STATUS, CREATED_AT, UPDATED_AT)
                    .values(customer.id(), customer.businessId().value(), customer.name(), customer.nickname(),
                            customer.phone(), customer.status().name(), toDatabaseTime(customer.createdAt()),
                            toDatabaseTime(customer.updatedAt()))
                    .execute();
        } catch (RuntimeException exception) {
            throw new CustomerPersistenceException(exception);
        }
    }

    @Override
    public void update(Customer customer) {
        try {
            dsl.update(CUSTOMERS)
                    .set(NAME, customer.name()).set(NICKNAME, customer.nickname()).set(PHONE, customer.phone())
                    .set(STATUS, customer.status().name()).set(UPDATED_AT, toDatabaseTime(customer.updatedAt()))
                    .where(BUSINESS_ID.eq(customer.businessId().value()).and(ID.eq(customer.id())))
                    .execute();
        } catch (RuntimeException exception) {
            throw new CustomerPersistenceException(exception);
        }
    }

    @Override
    public void deleteUnclaimed(Customer customer) {
        try {
            dsl.deleteFrom(CUSTOMERS)
                    .where(BUSINESS_ID.eq(customer.businessId().value()).and(ID.eq(customer.id())))
                    .execute();
        } catch (RuntimeException exception) {
            throw new CustomerPersistenceException(exception);
        }
    }

    @Override
    public Optional<IdempotencyRecord> findIdempotency(BusinessId businessId, String key) {
        try {
            return dsl.select(FINGERPRINT, KEY_CUSTOMER_ID).from(KEYS)
                    .where(BUSINESS_ID.eq(businessId.value()).and(IDEMPOTENCY_KEY.eq(key)))
                    .fetchOptional().map(record -> new IdempotencyRecord(
                            record.get(FINGERPRINT), record.get(KEY_CUSTOMER_ID)));
        } catch (RuntimeException exception) {
            throw new CustomerPersistenceException(exception);
        }
    }

    @Override
    public boolean insertIdempotency(BusinessId businessId, String key, String fingerprint,
            UUID customerId, Instant createdAt) {
        try {
            return dsl.insertInto(KEYS).columns(BUSINESS_ID, IDEMPOTENCY_KEY, FINGERPRINT, KEY_CUSTOMER_ID, CREATED_AT)
                    .values(businessId.value(), key, fingerprint, customerId, toDatabaseTime(createdAt))
                    .onConflict(BUSINESS_ID, IDEMPOTENCY_KEY).doNothing().execute() == 1;
        } catch (RuntimeException exception) {
            throw new CustomerPersistenceException(exception);
        }
    }

    private static Customer toCustomer(Record record) {
        return new Customer(record.get(ID), new BusinessId(record.get(BUSINESS_ID)), record.get(NAME),
                record.get(NICKNAME), record.get(PHONE), CustomerStatus.valueOf(record.get(STATUS)),
                record.get(CREATED_AT).toInstant(), record.get(UPDATED_AT).toInstant());
    }

    private static Table<?> table(String name) {
        return DSL.table(DSL.name("public", name));
    }

    private static <T> Field<T> field(String name, Class<T> type) {
        return DSL.field(DSL.name(name), type);
    }

    private static OffsetDateTime toDatabaseTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
