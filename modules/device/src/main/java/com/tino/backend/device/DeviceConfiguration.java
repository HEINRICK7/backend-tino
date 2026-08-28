package com.tino.backend.device;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.device.application.exception.DeviceInstallationAccessDeniedException;
import com.tino.backend.device.application.exception.DeviceInstallationPersistenceException;
import com.tino.backend.device.application.exception.RevokedDeviceInstallationException;
import com.tino.backend.device.application.port.in.ActiveInstallationView;
import com.tino.backend.device.application.port.in.DeviceContextUnavailableException;
import com.tino.backend.device.application.port.in.DeviceInstallationContextReader;
import com.tino.backend.device.application.port.out.DeviceInstallationRepository;
import com.tino.backend.device.application.usecase.RegisterDeviceInstallation;
import com.tino.backend.device.application.usecase.ResolveDeviceInstallation;
import com.tino.backend.shared.kernel.UuidGenerator;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Composition root for the M4 installation use cases. */
@Configuration(proxyBeanMethods = false)
public class DeviceConfiguration {
    @Bean
    RegisterDeviceInstallation registerDeviceInstallation(
            BusinessAuthorization businessAuthorization,
            DeviceInstallationRepository installations,
            UuidGenerator ids,
            Clock clock) {
        return new RegisterDeviceInstallation(
                businessAuthorization, installations, ids, clock);
    }

    @Bean
    ResolveDeviceInstallation resolveDeviceInstallation(
            BusinessAuthorization businessAuthorization,
            DeviceInstallationRepository installations) {
        return new ResolveDeviceInstallation(businessAuthorization, installations);
    }

    @Bean
    DeviceInstallationContextReader deviceInstallationContextReader(
            ResolveDeviceInstallation resolveDeviceInstallation) {
        return (authenticatedUserId, requestedBusinessId, installationExternalId) -> {
            try {
                var context = resolveDeviceInstallation.execute(
                        authenticatedUserId,
                        new com.tino.backend.shared.kernel.BusinessId(requestedBusinessId),
                        installationExternalId);
                return Optional.of(new ActiveInstallationView(
                        context.installationId().value(),
                        context.installationExternalId().value(),
                        context.businessId().value()));
            } catch (DeviceInstallationAccessDeniedException
                    | RevokedDeviceInstallationException exception) {
                return Optional.empty();
            } catch (DeviceInstallationPersistenceException exception) {
                throw new DeviceContextUnavailableException(exception);
            }
        };
    }
}
