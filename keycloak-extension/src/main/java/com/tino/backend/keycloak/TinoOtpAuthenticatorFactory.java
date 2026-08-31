package com.tino.backend.keycloak;

import java.util.List;
import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel.Requirement;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

/** Keycloak SPI factory for the TINO one-time identity proof authenticator. */
public final class TinoOtpAuthenticatorFactory implements AuthenticatorFactory {
    public static final String PROVIDER_ID = "tino-otp-ticket";
    private static final TinoOtpAuthenticatorFactory INSTANCE = new TinoOtpAuthenticatorFactory();

    public static TinoOtpAuthenticatorFactory instance() {
        return INSTANCE;
    }

    @Override
    public Authenticator create(KeycloakSession session) {
        return new TinoOtpAuthenticator(session);
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getReferenceCategory() {
        return "tino-otp";
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public Requirement[] getRequirementChoices() {
        return new Requirement[] {Requirement.REQUIRED, Requirement.ALTERNATIVE, Requirement.DISABLED};
    }

    @Override
    public String getDisplayType() {
        return "TINO OTP ticket";
    }

    @Override
    public String getHelpText() {
        return "Resolves a one-time TINO OTP proof and lets the normal OIDC flow issue tokens.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return List.of();
    }

    @Override
    public void init(Config.Scope config) {
        // Runtime endpoint and token are intentionally supplied as environment variables.
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // No global state or background worker.
    }

    @Override
    public void close() {
        // No resources owned by the factory.
    }
}
