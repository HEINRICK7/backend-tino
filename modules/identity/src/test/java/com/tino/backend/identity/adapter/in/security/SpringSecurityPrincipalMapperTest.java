package com.tino.backend.identity.adapter.in.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;

class SpringSecurityPrincipalMapperTest {
    private final SpringSecurityPrincipalMapper mapper = new SpringSecurityPrincipalMapper();

    @Test
    void mapsOnlyTheOpaqueSubjectFromAValidatedJwt() {
        var principal = mapper.convert(jwt("opaque-subject"));

        assertThat(principal).isNotNull();
        assertThat(principal.externalSubject().value()).isEqualTo("opaque-subject");
    }

    @Test
    void missingSubjectFailsClosedWithoutFallback() {
        assertThat(mapper.convert(jwt(null))).isNull();
        assertThatThrownBy(() -> new AuthenticatedPrincipalJwtAuthenticationConverter().convert(jwt(null)))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void authenticationTokenDoesNotRetainJwtCredentials() {
        var authentication = new AuthenticatedPrincipalJwtAuthenticationConverter().convert(jwt("s"));

        assertThat(authentication.getPrincipal()).isInstanceOf(
                com.tino.backend.identity.application.port.in.AuthenticatedPrincipal.class);
        assertThat(authentication.getCredentials()).isNull();
    }

    private static Jwt jwt(String subject) {
        var builder = Jwt.withTokenValue(UUID.randomUUID().toString())
                .header("alg", "none")
                .issuer("https://issuer.example.test")
                .audience(List.of("tino-android"))
                .issuedAt(Instant.parse("2026-08-26T12:00:00Z"))
                .expiresAt(Instant.parse("2026-08-26T13:00:00Z"));
        if (subject != null) {
            builder.subject(subject);
        }
        return builder.build();
    }
}
