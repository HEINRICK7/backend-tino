package com.tino.backend.keycloak;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

/**
 * Browser-flow authenticator that consumes a TINO proof and then delegates token
 * issuance to Keycloak's normal OIDC authorization-code flow.
 */
public final class TinoOtpAuthenticator implements Authenticator {
    private static final Pattern PHONE = Pattern.compile("\"phone_e164\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern TICKET = Pattern.compile("[A-Za-z0-9_-]{40,128}");
    private final KeycloakSession session;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    TinoOtpAuthenticator(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        var ticket = context.getHttpRequest().getUri().getQueryParameters().getFirst("tino_otp_ticket");
        String clientId = context.getAuthenticationSession().getClient().getClientId();
        String phone = ticket == null || !TICKET.matcher(ticket).matches()
                ? null : consumeTicket(ticket, clientId);
        if (phone == null) {
            context.failure(AuthenticationFlowError.INVALID_CREDENTIALS);
            return;
        }
        var user = findOrCreateUser(context.getRealm(), phone);
        if (user == null || !user.isEnabled()) {
            context.failure(AuthenticationFlowError.INVALID_USER);
            return;
        }
        context.setUser(user);
        context.success();
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        authenticate(context);
    }

    @Override
    public boolean requiresUser() {
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
        // OTP proof is external to Keycloak credentials and is already single-use.
    }

    @Override
    public void close() {
        // HttpClient owns no explicit closeable resource.
    }

    private String consumeTicket(String ticket, String clientId) {
        var baseUrl = env("TINO_OTP_BACKEND_URL", "http://app:8080");
        var internalToken = System.getenv("TINO_OTP_INTERNAL_TOKEN");
        if (internalToken == null || internalToken.isBlank()) {
            return null;
        }
        var endpoint = baseUrl.replaceAll("/$", "")
                + "/internal/v1/identity/otp/tickets/consume";
        try {
            var payload = "{\"ticket\":\"" + ticket + "\",\"client_id\":\"" + clientId + "\"}";
            var request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(3))
                    .header("X-Tino-Internal-Token", internalToken)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                return null;
            }
            Matcher matcher = PHONE.matcher(response.body());
            return matcher.find() ? matcher.group(1) : null;
        } catch (IOException exception) {
            return null;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private UserModel findOrCreateUser(RealmModel realm, String phone) {
        var users = session.users();
        var existing = users.searchForUserByUserAttributeStream(realm, "phone_e164", phone).findFirst();
        if (existing.isPresent()) {
            var user = existing.orElseThrow();
            user.removeRequiredAction("VERIFY_PROFILE");
            return user;
        }
        var user = users.addUser(realm, "phone:" + phone);
        user.setEnabled(true);
        user.setSingleAttribute("phone_e164", phone);
        user.setFirstName("TINO");
        user.setLastName("Account");
        user.setEmailVerified(false);
        user.removeRequiredAction("VERIFY_PROFILE");
        return user;
    }

    private static String env(String name, String fallback) {
        var value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
