package com.tino.backend.identity.adapter.in.security;

import com.tino.backend.identity.application.port.in.AuthenticatedPrincipal;
import com.tino.backend.identity.application.port.out.UserRepository;
import com.tino.backend.identity.application.usecase.ResolveAuthenticatedUser;
import com.tino.backend.shared.kernel.UuidGenerator;
import com.tino.backend.shared.kernel.UuidV7Generator;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

/** Composition root for identity's framework-facing adapters and use case. */
@Configuration(proxyBeanMethods = false)
public class IdentitySecurityConfiguration {
    @Bean
    Clock identityClock() {
        return Clock.systemUTC();
    }

    @Bean
    UuidGenerator identityUuidGenerator() {
        return new UuidV7Generator();
    }

    @Bean
    ResolveAuthenticatedUser resolveAuthenticatedUser(
            UserRepository users, UuidGenerator ids, Clock clock) {
        return new ResolveAuthenticatedUser(users, ids, clock);
    }

    @Bean
    Converter<Jwt, AbstractAuthenticationToken> authenticatedPrincipalJwtAuthenticationConverter() {
        return new AuthenticatedPrincipalJwtAuthenticationConverter();
    }

    @Bean
    Converter<Jwt, AuthenticatedPrincipal> springSecurityPrincipalMapper() {
        return new SpringSecurityPrincipalMapper();
    }
}
