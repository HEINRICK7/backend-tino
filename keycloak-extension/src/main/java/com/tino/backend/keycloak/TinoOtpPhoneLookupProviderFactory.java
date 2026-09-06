package com.tino.backend.keycloak;

import java.util.List;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.services.resource.RealmResourceProvider;
import org.keycloak.services.resource.RealmResourceProviderFactory;

/** Exposes the minimal internal phone-to-subject lookup needed by the backend pre-auth policy. */
public final class TinoOtpPhoneLookupProviderFactory implements RealmResourceProviderFactory {
    public static final String ID = "tino-otp";
    private static final TinoOtpPhoneLookupProviderFactory INSTANCE = new TinoOtpPhoneLookupProviderFactory();

    public static TinoOtpPhoneLookupProviderFactory instance() {
        return INSTANCE;
    }

    @Override
    public RealmResourceProvider create(KeycloakSession session) {
        return new TinoOtpPhoneLookupProvider(session);
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public void init(Config.Scope config) {}

    @Override
    public void postInit(KeycloakSessionFactory factory) {}

    @Override
    public void close() {}

    @Override
    public List<ProviderConfigProperty> getConfigMetadata() {
        return List.of();
    }
}
