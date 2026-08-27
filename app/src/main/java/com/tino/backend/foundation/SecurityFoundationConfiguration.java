package com.tino.backend.foundation;

import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
public class SecurityFoundationConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http, Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter)
            throws Exception {
        return http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/openapi/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                        .jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .build();
    }

    /**
     * Composes signature, issuer, timestamp, subject, and client-contract
     * validation. Discovery is lazy so public health can start without an IdP.
     */
    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}") String jwkSetUri,
            @Value("${tino.security.oidc.client-id:tino-android}") String clientId) {
        return new SupplierJwtDecoder(() -> {
            NimbusJwtDecoder decoder = StringUtils.hasText(jwkSetUri)
                    ? NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build()
                    : NimbusJwtDecoder.withIssuerLocation(issuer).build();
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    JwtValidators.createDefaultWithIssuer(issuer),
                    new JwtClaimValidator<Instant>("exp", value -> value != null),
                    new JwtClaimValidator<String>("sub", value -> value != null && !value.isBlank()),
                    new AudienceOrAuthorizedPartyValidator(clientId)));
            return decoder;
        });
    }

    /** Explicit client policy: configured client is in {@code aud} OR equals {@code azp}. */
    static final class AudienceOrAuthorizedPartyValidator implements OAuth2TokenValidator<Jwt> {
        private final String expectedClientId;

        AudienceOrAuthorizedPartyValidator(String expectedClientId) {
            this.expectedClientId = expectedClientId;
        }

        @Override
        public OAuth2TokenValidatorResult validate(Jwt token) {
            if (token == null || !StringUtils.hasText(expectedClientId)) {
                return invalidClientContract();
            }
            List<String> audience = token.getAudience();
            Object authorizedParty = token.getClaims().get("azp");
            boolean audienceMatches = audience != null && audience.contains(expectedClientId);
            boolean authorizedPartyMatches = authorizedParty instanceof String value
                    && expectedClientId.equals(value);
            return audienceMatches || authorizedPartyMatches
                    ? OAuth2TokenValidatorResult.success()
                    : invalidClientContract();
        }

        private OAuth2TokenValidatorResult invalidClientContract() {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    "invalid_token", "token is not intended for the configured client", null));
        }
    }
}
