package com.tino.backend.device;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.device.application.port.out.DeviceInstallationRepository;
import com.tino.backend.device.application.usecase.RegisterDeviceInstallation;
import com.tino.backend.device.application.usecase.ResolveDeviceInstallation;
import com.tino.backend.shared.kernel.UuidGenerator;
import java.time.Clock;
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
}
