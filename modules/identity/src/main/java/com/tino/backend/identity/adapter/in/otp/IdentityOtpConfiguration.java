package com.tino.backend.identity.adapter.in.otp;

import com.tino.backend.identity.adapter.out.crypto.HmacOtpSecretHasher;
import com.tino.backend.identity.adapter.out.crypto.SecureOtpGenerator;
import com.tino.backend.identity.adapter.out.delivery.DisabledOtpDeliveryAdapter;
import com.tino.backend.identity.adapter.out.delivery.WaEvolutionOtpDeliveryAdapter;
import com.tino.backend.identity.application.port.out.OtpChallengeRepository;
import com.tino.backend.identity.application.port.out.OtpDeliveryPort;
import com.tino.backend.identity.application.port.out.OtpGenerator;
import com.tino.backend.identity.application.port.out.OtpSecretHasher;
import com.tino.backend.identity.application.port.out.OtpVerificationEventRepository;
import com.tino.backend.identity.application.usecase.ConsumeOtpVerificationTicket;
import com.tino.backend.identity.application.usecase.ConfirmOtpFromWhatsApp;
import com.tino.backend.identity.application.usecase.GetOtpChallengeStatus;
import com.tino.backend.identity.application.usecase.IssueOtpVerificationTicket;
import com.tino.backend.identity.application.usecase.RequestOtp;
import com.tino.backend.identity.application.usecase.VerifyOtp;
import com.tino.backend.shared.kernel.UuidGenerator;
import java.net.URI;
import java.net.http.HttpClient;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Composition root for the provider-neutral OTP application. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class IdentityOtpConfiguration {
    @Bean
    OtpGenerator otpGenerator() {
        return new SecureOtpGenerator(new SecureRandom());
    }

    @Bean
    OtpSecretHasher otpSecretHasher(
            @Value("${tino.identity.otp.enabled:false}") boolean enabled,
            @Value("${tino.identity.otp.hash-secret:}") String secret) {
        if (enabled && secret.isBlank()) {
            throw new IllegalStateException("TINO_OTP_HASH_SECRET is required when OTP is enabled");
        }
        var effective = secret.isBlank()
                ? java.util.UUID.randomUUID().toString() + java.util.UUID.randomUUID()
                : secret;
        return new HmacOtpSecretHasher(effective);
    }

    @Bean
    HttpClient otpHttpClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    }

    @Bean
    OtpDeliveryPort otpDeliveryPort(
            @Value("${tino.identity.otp.enabled:false}") boolean enabled,
            @Value("${tino.identity.otp.delivery.provider:WA_EVOLUTION}") String provider,
            @Value("${tino.identity.otp.delivery.base-url:http://otp-delivery:8080}") String baseUrl,
            @Value("${tino.identity.otp.delivery.path:/internal/v1/messages/otp}") String path,
            @Value("${tino.identity.otp.delivery.internal-token:}") String token,
            @Value("${tino.identity.otp.delivery.timeout:PT5S}") Duration timeout,
            @Qualifier("otpHttpClient") HttpClient http) {
        if (!enabled) {
            return new DisabledOtpDeliveryAdapter();
        }
        if (!"WA_EVOLUTION".equalsIgnoreCase(provider)) {
            throw new IllegalStateException("unsupported OTP delivery provider");
        }
        var normalizedPath = path.startsWith("/") ? path : "/" + path;
        return new WaEvolutionOtpDeliveryAdapter(
                URI.create(baseUrl.endsWith("/") ? baseUrl + normalizedPath.substring(1) : baseUrl + normalizedPath),
                token,
                timeout,
                http);
    }

    @Bean
    RequestOtp requestOtp(
            OtpChallengeRepository challenges,
            OtpDeliveryPort delivery,
            OtpGenerator generator,
            OtpSecretHasher hasher,
            UuidGenerator ids,
            Clock clock) {
        return new RequestOtp(challenges, delivery, generator, hasher, ids, clock);
    }

    @Bean
    VerifyOtp verifyOtp(
            OtpChallengeRepository challenges,
            OtpGenerator generator,
            OtpSecretHasher hasher,
            Clock clock) {
        return new VerifyOtp(challenges, generator, hasher, clock);
    }

    @Bean
    ConsumeOtpVerificationTicket consumeOtpVerificationTicket(
            OtpChallengeRepository challenges, OtpSecretHasher hasher, Clock clock,
            @Value("${tino.security.oidc.client-id:tino-android}") String clientId) {
        return new ConsumeOtpVerificationTicket(challenges, hasher, clock, clientId);
    }

    @Bean
    OtpChallengeCleanup otpChallengeCleanup(OtpChallengeRepository challenges, Clock clock) {
        return new OtpChallengeCleanup(challenges, clock);
    }

    @Bean
    GetOtpChallengeStatus getOtpChallengeStatus(
            OtpChallengeRepository challenges, OtpVerificationEventRepository events, Clock clock) {
        return new GetOtpChallengeStatus(challenges, events, clock);
    }

    @Bean
    ConfirmOtpFromWhatsApp confirmOtpFromWhatsApp(
            OtpChallengeRepository challenges, OtpVerificationEventRepository events, Clock clock) {
        return new ConfirmOtpFromWhatsApp(challenges, events, clock);
    }

    @Bean
    IssueOtpVerificationTicket issueOtpVerificationTicket(
            OtpChallengeRepository challenges, OtpVerificationEventRepository events,
            OtpGenerator generator, OtpSecretHasher hasher, Clock clock) {
        return new IssueOtpVerificationTicket(challenges, events, generator, hasher, clock);
    }
}
