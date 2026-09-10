package com.tino.backend.business.application.usecase;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.business.application.port.in.BusinessPixReader;
import com.tino.backend.business.application.port.in.BusinessPixUnavailableException;
import com.tino.backend.business.application.port.out.BusinessPixConfigurationRepository;
import com.tino.backend.business.application.port.out.BusinessPixPersistenceException;
import com.tino.backend.business.application.port.out.BusinessRepository;
import com.tino.backend.business.domain.model.BusinessPixConfiguration;
import com.tino.backend.business.domain.model.BusinessStatus;
import com.tino.backend.business.domain.model.PixCodeGenerator;
import com.tino.backend.business.domain.model.PixKey;
import com.tino.backend.shared.kernel.BusinessId;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Merchant-owned Pix configuration with authorization before tenant I/O. */
public final class ManageBusinessPix implements BusinessPixReader {
    private final BusinessAuthorization authorization;
    private final BusinessRepository businesses;
    private final BusinessPixConfigurationRepository configurations;
    private final Clock clock;
    private final String merchantCity;

    public ManageBusinessPix(
            BusinessAuthorization authorization,
            BusinessRepository businesses,
            BusinessPixConfigurationRepository configurations,
            Clock clock,
            String merchantCity) {
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.businesses = Objects.requireNonNull(businesses, "businesses");
        this.configurations = Objects.requireNonNull(configurations, "configurations");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.merchantCity = PixCodeGenerator.merchantField(merchantCity, 15, "merchant city");
    }

    public Optional<BusinessPixConfiguration> get(UUID userId, BusinessId businessId) {
        return authorization.execute(userId, businessId, configurations::find);
    }

    public Optional<BusinessPixConfiguration> update(
            UUID userId, BusinessId businessId, boolean enabled, String rawKey) {
        return authorization.execute(userId, businessId, authorizedBusiness -> {
            if (!enabled) {
                configurations.delete(authorizedBusiness);
                return Optional.empty();
            }
            var key = PixKey.parse(rawKey);
            var business = businesses.findById(authorizedBusiness)
                    .filter(value -> value.status() == BusinessStatus.ACTIVE)
                    .orElseThrow(() -> new IllegalArgumentException("business not found"));
            var now = Instant.now(clock);
            var configuration = new BusinessPixConfiguration(
                    authorizedBusiness,
                    key,
                    PixCodeGenerator.generate(key, business.tradeName().value(), merchantCity),
                    PixCodeGenerator.merchantField(business.tradeName().value(), 25, "merchant name"),
                    merchantCity,
                    true,
                    configurations.find(authorizedBusiness).map(BusinessPixConfiguration::createdAt).orElse(now),
                    now);
            configurations.upsert(configuration);
            return Optional.of(configuration);
        });
    }

    @Override
    public Optional<BusinessPixReader.PixView> read(BusinessId businessId) {
        return readForAmount(businessId, null);
    }

    @Override
    public Optional<BusinessPixReader.PixView> readForAmount(BusinessId businessId, BigDecimal amount) {
        try {
            return configurations.find(businessId)
                    .map(configuration -> new BusinessPixReader.PixView(configuration.enabled(),
                            configuration.key().value(), copyPaste(configuration, amount)));
        } catch (BusinessPixPersistenceException exception) {
            throw new BusinessPixUnavailableException(exception);
        }
    }

    private static String copyPaste(BusinessPixConfiguration configuration, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) return configuration.copyPaste();
        return PixCodeGenerator.generate(configuration.key(), configuration.merchantName(),
                configuration.merchantCity(), amount);
    }
}
