package com.tino.backend.bootstrap;

import com.tino.backend.bootstrap.application.usecase.ResolveBootstrapContext;
import com.tino.backend.businessunderstanding.application.port.in.BusinessUnderstandingReader;
import com.tino.backend.business.application.port.in.BusinessContextReader;
import com.tino.backend.device.application.port.in.DeviceInstallationContextReader;
import com.tino.backend.identity.application.port.in.AuthenticatedUserResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Composition root for the read-only M5 Bootstrap Context. */
@Configuration(proxyBeanMethods = false)
public class BootstrapConfiguration {
    @Bean
    ResolveBootstrapContext resolveBootstrapContext(
            AuthenticatedUserResolver authenticatedUsers,
            BusinessContextReader businesses,
            DeviceInstallationContextReader installations,
            BusinessUnderstandingReader understanding) {
        return new ResolveBootstrapContext(authenticatedUsers, businesses, installations, understanding);
    }
}
