package com.tino.backend.identity.adapter.out.persistence;

import com.tino.backend.identity.application.port.out.OtpPhoneAuthorization;
import com.tino.backend.identity.application.exception.OtpAuthorizationUnavailableException;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL adapter for the phone identity used by the pre-authentication policy. */
@Repository
public class JooqOtpPhoneAuthorization implements OtpPhoneAuthorization {
    private static final Table<?> IDENTITIES = DSL.table(DSL.name("public", "identity_phone_bindings"));
    private static final Field<String> PHONE_HASH = DSL.field(DSL.name("phone_hash"), String.class);
    private static final Field<String> EXTERNAL_SUBJECT = DSL.field(DSL.name("external_subject"), String.class);
    private static final Field<OffsetDateTime> CREATED_AT = DSL.field(DSL.name("created_at"), OffsetDateTime.class);
    private static final Field<OffsetDateTime> UPDATED_AT = DSL.field(DSL.name("updated_at"), OffsetDateTime.class);
    private static final Pattern SUBJECT_JSON = Pattern.compile("\"external_subject\"\\s*:\\s*\"([^\"]+)\"");

    private final DSLContext dsl;
    private final HttpClient http;
    private final String keycloakUrl;
    private final String internalToken;

    public JooqOtpPhoneAuthorization(
            DSLContext dsl,
            @org.springframework.beans.factory.annotation.Qualifier("otpHttpClient") HttpClient http,
            @Value("${tino.identity.otp.keycloak-url:http://keycloak:8080}") String keycloakUrl,
            @Value("${tino.identity.otp.internal-token:}") String internalToken) {
        this.dsl = dsl;
        this.http = http;
        this.keycloakUrl = keycloakUrl;
        this.internalToken = internalToken;
    }

    @Override
    public boolean isAuthorized(String phoneE164, String phoneHash, UUID businessId) {
        if (hasAuthorizedBinding(phoneHash, businessId)) {
            return true;
        }
        var externalSubject = findKeycloakSubject(phoneE164);
        if (externalSubject == null) {
            return false;
        }
        if (!hasAuthorizedSubject(externalSubject, businessId)) {
            return false;
        }
        bind(phoneHash, externalSubject);
        return true;
    }

    private boolean hasAuthorizedBinding(String phoneHash, UUID businessId) {
        var identities = IDENTITIES.as("identity_phone");
        var users = DSL.table(DSL.name("public", "users")).as("identity_user");
        var memberships = DSL.table(DSL.name("public", "business_memberships")).as("membership");
        var identityPhoneHash = DSL.field(DSL.name("identity_phone", "phone_hash"), String.class);
        var identitySubject = DSL.field(DSL.name("identity_phone", "external_subject"), String.class);
        var userSubject = DSL.field(DSL.name("identity_user", "external_subject"), String.class);
        var userId = DSL.field(DSL.name("identity_user", "id"), UUID.class);
        var membershipUserId = DSL.field(DSL.name("membership", "user_id"), UUID.class);
        var membershipBusinessId = DSL.field(DSL.name("membership", "business_id"), UUID.class);
        var membershipStatus = DSL.field(DSL.name("membership", "status"), String.class);
        return dsl.fetchExists(DSL.selectOne()
                .from(identities)
                .join(users).on(identitySubject.eq(userSubject))
                .join(memberships).on(membershipUserId.eq(userId))
                .where(identityPhoneHash.eq(phoneHash)
                        .and(membershipBusinessId.eq(businessId))
                        .and(membershipStatus.eq("ACTIVE"))));
    }

    private boolean hasAuthorizedSubject(String externalSubject, UUID businessId) {
        var users = DSL.table(DSL.name("public", "users")).as("identity_user");
        var memberships = DSL.table(DSL.name("public", "business_memberships")).as("membership");
        var userSubject = DSL.field(DSL.name("identity_user", "external_subject"), String.class);
        var userId = DSL.field(DSL.name("identity_user", "id"), UUID.class);
        var membershipUserId = DSL.field(DSL.name("membership", "user_id"), UUID.class);
        var membershipBusinessId = DSL.field(DSL.name("membership", "business_id"), UUID.class);
        var membershipStatus = DSL.field(DSL.name("membership", "status"), String.class);
        return dsl.fetchExists(DSL.selectOne()
                .from(users)
                .join(memberships).on(membershipUserId.eq(userId))
                .where(userSubject.eq(externalSubject)
                        .and(membershipBusinessId.eq(businessId))
                        .and(membershipStatus.eq("ACTIVE"))));
    }

    private String findKeycloakSubject(String phoneE164) {
        if (internalToken == null || internalToken.isBlank()) {
            return null;
        }
        var endpoint = keycloakUrl.replaceAll("/$", "")
                + "/realms/tino/tino-otp/phone-subject?phone="
                + URLEncoder.encode(phoneE164, StandardCharsets.UTF_8);
        try {
            var response = http.send(HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(java.time.Duration.ofSeconds(2))
                    .header("X-Tino-Internal-Token", internalToken)
                    .header("Accept", "application/json")
                    .GET()
                    .build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 404) {
                return null;
            }
            if (response.statusCode() != 200) {
                throw new OtpAuthorizationUnavailableException(
                        new IllegalStateException("Keycloak phone lookup returned " + response.statusCode()));
            }
            var matcher = SUBJECT_JSON.matcher(response.body());
            return matcher.find() ? matcher.group(1) : null;
        } catch (IOException exception) {
            throw new OtpAuthorizationUnavailableException(exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new OtpAuthorizationUnavailableException(exception);
        }
    }

    @Override
    @Transactional
    public void bind(String phoneHash, String externalSubject) {
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var updated = dsl.update(IDENTITIES)
                .set(EXTERNAL_SUBJECT, externalSubject)
                .set(UPDATED_AT, now)
                .where(PHONE_HASH.eq(phoneHash).and(EXTERNAL_SUBJECT.eq(externalSubject)))
                .execute();
        if (updated > 0) {
            return;
        }
        try {
            dsl.insertInto(IDENTITIES)
                    .columns(PHONE_HASH, EXTERNAL_SUBJECT, CREATED_AT, UPDATED_AT)
                    .values(phoneHash, externalSubject, now, now)
                    .execute();
        } catch (org.jooq.exception.DataAccessException exception) {
            throw new IllegalStateException("phone identity is already bound", exception);
        }
    }
}
