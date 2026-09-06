package com.tino.backend.keycloak;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.regex.Pattern;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.UserModel;
import org.keycloak.services.resource.RealmResourceProvider;

/** Internal Keycloak resource; it never exposes phone data without the shared service token. */
public final class TinoOtpPhoneLookupProvider implements RealmResourceProvider {
    private static final Pattern PHONE = Pattern.compile("\\+55[1-9][0-9](9[0-9]{8}|[2-5][0-9]{7})");
    private final KeycloakSession session;

    TinoOtpPhoneLookupProvider(KeycloakSession session) {
        this.session = session;
    }

    @GET
    @Path("phone-subject")
    @Produces(MediaType.APPLICATION_JSON)
    public Response findSubject(
            @HeaderParam("X-Tino-Internal-Token") String suppliedToken,
            @QueryParam("phone") String phone) {
        if (!matchesToken(suppliedToken) || phone == null || !PHONE.matcher(phone).matches()) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        var realm = session.getContext().getRealm();
        var user = session.users().searchForUserByUserAttributeStream(realm, "phone_e164", phone)
                .filter(UserModel::isEnabled)
                .findFirst();
        return user.map(value -> Response.ok(Map.of("external_subject", value.getId())).build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
    }

    private static boolean matchesToken(String suppliedToken) {
        var configured = System.getenv("TINO_OTP_INTERNAL_TOKEN");
        return configured != null && !configured.isBlank()
                && suppliedToken != null
                && MessageDigest.isEqual(
                        configured.getBytes(StandardCharsets.UTF_8), suppliedToken.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Object getResource() {
        return this;
    }

    @Override
    public void close() {}
}
