package com.tino.backend.business.adapter.out.persistence;

import com.tino.backend.business.application.port.out.BusinessPixConfigurationRepository;
import com.tino.backend.business.application.port.out.BusinessPixPersistenceException;
import com.tino.backend.business.domain.model.BusinessPixConfiguration;
import com.tino.backend.business.domain.model.PixKey;
import com.tino.backend.business.domain.model.PixKeyType;
import com.tino.backend.shared.kernel.BusinessId;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JooqBusinessPixConfigurationRepository implements BusinessPixConfigurationRepository {
    private final DSLContext dsl;

    public JooqBusinessPixConfigurationRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BusinessPixConfiguration> find(BusinessId businessId) {
        try {
            var row = dsl.fetchOne("""
                    SELECT business_id, pix_key_type, pix_key, copy_paste,
                           merchant_name, merchant_city, enabled, created_at, updated_at
                      FROM public.business_pix_configurations
                     WHERE business_id = ?
                    """, businessId.value());
            if (row == null) return Optional.empty();
            return Optional.of(toConfiguration(row));
        } catch (RuntimeException exception) {
            throw new BusinessPixPersistenceException(exception);
        }
    }

    @Override
    @Transactional
    public void upsert(BusinessPixConfiguration configuration) {
        try {
            dsl.execute("""
                    INSERT INTO public.business_pix_configurations
                        (business_id, pix_key_type, pix_key, copy_paste,
                         merchant_name, merchant_city, enabled, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS TIMESTAMPTZ), CAST(? AS TIMESTAMPTZ))
                    ON CONFLICT (business_id) DO UPDATE SET
                        pix_key_type = EXCLUDED.pix_key_type,
                        pix_key = EXCLUDED.pix_key,
                        copy_paste = EXCLUDED.copy_paste,
                        merchant_name = EXCLUDED.merchant_name,
                        merchant_city = EXCLUDED.merchant_city,
                        enabled = EXCLUDED.enabled,
                        updated_at = EXCLUDED.updated_at
                    """, configuration.businessId().value(), configuration.key().type().name(),
                    configuration.key().value(), configuration.copyPaste(), configuration.merchantName(),
                    configuration.merchantCity(), configuration.enabled(), time(configuration.createdAt()),
                    time(configuration.updatedAt()));
        } catch (RuntimeException exception) {
            throw new BusinessPixPersistenceException(exception);
        }
    }

    @Override
    @Transactional
    public void delete(BusinessId businessId) {
        try {
            dsl.execute("DELETE FROM public.business_pix_configurations WHERE business_id = ?", businessId.value());
        } catch (RuntimeException exception) {
            throw new BusinessPixPersistenceException(exception);
        }
    }

    private static BusinessPixConfiguration toConfiguration(org.jooq.Record row) {
        return new BusinessPixConfiguration(
                new BusinessId(row.get("business_id", java.util.UUID.class)),
                new PixKey(PixKeyType.valueOf(row.get("pix_key_type", String.class)),
                        row.get("pix_key", String.class)),
                row.get("copy_paste", String.class),
                row.get("merchant_name", String.class),
                row.get("merchant_city", String.class),
                Boolean.TRUE.equals(row.get("enabled", Boolean.class)),
                instant(row.get("created_at", OffsetDateTime.class)),
                instant(row.get("updated_at", OffsetDateTime.class)));
    }

    private static String time(Instant value) {
        return value.atOffset(ZoneOffset.UTC).toString();
    }

    private static Instant instant(OffsetDateTime value) {
        if (value == null) throw new IllegalStateException("Pix timestamp is null");
        return value.toInstant();
    }
}
