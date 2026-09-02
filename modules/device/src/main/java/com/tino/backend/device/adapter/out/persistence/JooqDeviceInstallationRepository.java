package com.tino.backend.device.adapter.out.persistence;

import com.tino.backend.device.application.exception.DeviceInstallationPersistenceException;
import com.tino.backend.device.application.port.out.DeviceInstallationRepository;
import com.tino.backend.device.domain.model.DeviceInstallation;
import com.tino.backend.device.domain.model.DeviceInstallationId;
import com.tino.backend.device.domain.model.InstallationExternalId;
import com.tino.backend.device.domain.model.InstallationStatus;
import com.tino.backend.shared.kernel.BusinessId;
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

/**
 * jOOQ-only persistence adapter.  The external id's PostgreSQL unique
 * constraint is used as the concurrency/idempotency authority.
 */
@Repository
public class JooqDeviceInstallationRepository implements DeviceInstallationRepository {
    private static final Table<?> INSTALLATIONS =
            DSL.table(DSL.name("public", "device_installations"));
    private static final Field<UUID> ID = DSL.field(DSL.name("id"), UUID.class);
    private static final Field<UUID> BUSINESS_ID =
            DSL.field(DSL.name("business_id"), UUID.class);
    private static final Field<String> EXTERNAL_ID =
            DSL.field(DSL.name("installation_external_id"), String.class);
    private static final Field<String> STATUS = DSL.field(DSL.name("status"), String.class);
    private static final Field<UUID> REGISTERED_BY_USER_ID =
            DSL.field(DSL.name("registered_by_user_id"), UUID.class);
    private static final Field<OffsetDateTime> CREATED_AT =
            DSL.field(DSL.name("created_at"), OffsetDateTime.class);
    private static final Field<OffsetDateTime> UPDATED_AT =
            DSL.field(DSL.name("updated_at"), OffsetDateTime.class);

    private final DSLContext dsl;

    public JooqDeviceInstallationRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    @Transactional
    public int insertIfAbsent(DeviceInstallation installation) {
        try {
            return dsl.insertInto(INSTALLATIONS)
                    .columns(ID, BUSINESS_ID, EXTERNAL_ID, STATUS,
                            REGISTERED_BY_USER_ID, CREATED_AT, UPDATED_AT)
                    .values(
                            installation.id().value(),
                            installation.businessId().value(),
                            installation.externalId().value(),
                            installation.status().name(),
                            installation.registeredByUserId(),
                            toDatabaseTime(installation.createdAt()),
                            toDatabaseTime(installation.updatedAt()))
                    .onConflict(EXTERNAL_ID)
                    .doNothing()
                    .execute();
        } catch (org.jooq.exception.DataAccessException exception) {
            throw new DeviceInstallationPersistenceException(exception);
        } catch (org.springframework.dao.DataAccessException exception) {
            throw new DeviceInstallationPersistenceException(exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DeviceInstallation> findByExternalId(InstallationExternalId externalId) {
        try {
            return dsl.select(ID, BUSINESS_ID, EXTERNAL_ID, STATUS,
                            REGISTERED_BY_USER_ID, CREATED_AT, UPDATED_AT)
                    .from(INSTALLATIONS)
                    .where(EXTERNAL_ID.eq(externalId.value()))
                    .fetchOptional()
                    .map(JooqDeviceInstallationRepository::toDomain);
        } catch (org.jooq.exception.DataAccessException exception) {
            throw new DeviceInstallationPersistenceException(exception);
        } catch (org.springframework.dao.DataAccessException exception) {
            throw new DeviceInstallationPersistenceException(exception);
        }
    }

    private static DeviceInstallation toDomain(Record record) {
        try {
            return new DeviceInstallation(
                    new DeviceInstallationId(record.get(ID, UUID.class)),
                    new BusinessId(record.get(BUSINESS_ID, UUID.class)),
                    new InstallationExternalId(record.get(EXTERNAL_ID, String.class)),
                    InstallationStatus.valueOf(record.get(STATUS, String.class)),
                    record.get(REGISTERED_BY_USER_ID, UUID.class),
                    toInstant(record.get(CREATED_AT, OffsetDateTime.class)),
                    toInstant(record.get(UPDATED_AT, OffsetDateTime.class)));
        } catch (RuntimeException exception) {
            throw new DeviceInstallationPersistenceException(exception);
        }
    }

    private static OffsetDateTime toDatabaseTime(Instant value) {
        return value.atOffset(ZoneOffset.UTC);
    }

    private static Instant toInstant(OffsetDateTime value) {
        if (value == null) {
            throw new IllegalStateException("device installation timestamp is null");
        }
        return value.toInstant();
    }
}
