package com.tino.backend.identity.adapter.in.security;

import com.tino.backend.identity.application.port.in.AuthenticatedPrincipal;
import com.tino.backend.identity.application.port.in.AuthenticatedUserResolver;
import com.tino.backend.identity.application.port.in.AuthenticatedUserSnapshot;
import com.tino.backend.identity.application.port.in.PhoneChangeOtpGateway;
import com.tino.backend.identity.application.port.in.PhoneNumberNormalizer;
import com.tino.backend.identity.application.port.out.UserRepository;
import com.tino.backend.identity.application.usecase.ResolveAuthenticatedUser;
import com.tino.backend.identity.application.usecase.ConsumeOtpVerificationTicket;
import com.tino.backend.identity.application.usecase.RequestOtp;
import com.tino.backend.identity.domain.model.UserStatus;
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
    AuthenticatedUserResolver authenticatedUserResolver(ResolveAuthenticatedUser users) {
        return principal -> {
            var user = users.execute(principal);
            return new AuthenticatedUserSnapshot(
                    user.id().value(), user.status() == UserStatus.ACTIVE);
        };
    }

    @Bean
    PhoneChangeOtpGateway phoneChangeOtpGateway(
            RequestOtp requestOtp,
            ConsumeOtpVerificationTicket consumeTicket,
            PhoneNumberNormalizer phoneNumbers) {
        return new PhoneChangeOtpGateway() {
            @Override
            public PhoneChangeChallenge request(String phoneE164, String requestOrigin, java.util.UUID businessId) {
                var issued = requestOtp.executePhoneChange(phoneE164, requestOrigin, businessId);
                return new PhoneChangeChallenge(
                        issued.challengeId(), issued.expiresInSeconds(), issued.resendAvailableInSeconds(),
                        issued.deliveryChannel().name());
            }

            @Override
            public void consume(
                    java.util.UUID challengeId,
                    String verificationTicket,
                    String expectedPhoneE164,
                    Runnable beforeConsume) {
                consumeTicket.execute(verificationTicket, "tino-android", proof -> {
                    if (!proof.challengeId().equals(challengeId)
                            || !proof.phone().e164().equals(phoneNumbers.normalize(expectedPhoneE164))) {
                        throw new IllegalArgumentException("verification ticket does not match phone change");
                    }
                    beforeConsume.run();
                });
            }
        };
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
