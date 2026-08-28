package com.tino.backend.bootstrap.application.usecase;

import com.tino.backend.bootstrap.application.exception.BootstrapAccessDeniedException;
import com.tino.backend.bootstrap.application.exception.BootstrapAuthenticationRequiredException;
import com.tino.backend.bootstrap.application.model.BootstrapBusinessSummary;
import com.tino.backend.bootstrap.application.model.BootstrapContext;
import com.tino.backend.bootstrap.application.model.BootstrapInstallationSummary;
import com.tino.backend.bootstrap.application.model.BootstrapUserSummary;
import com.tino.backend.bootstrap.domain.model.BootstrapState;
import com.tino.backend.business.application.port.in.AccessibleBusinessView;
import com.tino.backend.business.application.port.in.BusinessContextReader;
import com.tino.backend.device.application.port.in.ActiveInstallationView;
import com.tino.backend.device.application.port.in.DeviceInstallationContextReader;
import com.tino.backend.identity.application.exception.DisabledUserException;
import com.tino.backend.identity.application.exception.InvalidAuthenticatedPrincipalException;
import com.tino.backend.identity.application.port.in.AuthenticatedPrincipal;
import com.tino.backend.identity.application.port.in.AuthenticatedUserResolver;
import com.tino.backend.identity.application.port.in.AuthenticatedUserSnapshot;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Composes existing M2, M3, and M4 authorities without creating new state or
 * persistence. Identity resolves the User, Business supplies the authorized
 * list, and Device resolves only after a Business has been selected.
 */
public final class ResolveBootstrapContext {
    private final AuthenticatedUserResolver authenticatedUsers;
    private final BusinessContextReader businesses;
    private final DeviceInstallationContextReader installations;

    public ResolveBootstrapContext(
            AuthenticatedUserResolver authenticatedUsers,
            BusinessContextReader businesses,
            DeviceInstallationContextReader installations) {
        this.authenticatedUsers = Objects.requireNonNull(authenticatedUsers, "authenticatedUsers");
        this.businesses = Objects.requireNonNull(businesses, "businesses");
        this.installations = Objects.requireNonNull(installations, "installations");
    }

    public BootstrapContext execute(
            AuthenticatedPrincipal principal,
            UUID requestedBusinessId,
            String installationExternalId) {
        var user = resolveUser(principal);
        var userSummary = new BootstrapUserSummary(user.userId(), "ACTIVE");
        var accessible = businesses.listAccessibleBusinesses(user.userId());
        var businessSummaries = accessible.stream()
                .map(ResolveBootstrapContext::toBusinessSummary)
                .toList();

        if (businessSummaries.isEmpty()) {
            return new BootstrapContext(
                    BootstrapState.BUSINESS_REQUIRED, userSummary, businessSummaries, null, null);
        }

        var selected = selectBusiness(businessSummaries, requestedBusinessId);
        if (selected == null) {
            // Multiple Businesses are preserved in the response. No client
            // installation id is allowed to select a tenant implicitly.
            return new BootstrapContext(
                    BootstrapState.LOCAL_BUSINESS_LINK_REQUIRED,
                    userSummary,
                    businessSummaries,
                    null,
                    null);
        }

        if (installationExternalId == null || installationExternalId.isBlank()) {
            return new BootstrapContext(
                    BootstrapState.LOCAL_BUSINESS_LINK_REQUIRED,
                    userSummary,
                    businessSummaries,
                    selected,
                    null);
        }

        var installation = installations.resolve(
                        user.userId(), selected.id(), installationExternalId)
                .map(value -> toInstallationSummary(value, selected.id()))
                .orElse(null);
        if (installation == null) {
            return new BootstrapContext(
                    BootstrapState.LOCAL_BUSINESS_LINK_REQUIRED,
                    userSummary,
                    businessSummaries,
                    selected,
                    null);
        }

        return new BootstrapContext(
                BootstrapState.READY, userSummary, businessSummaries, selected, installation);
    }

    private AuthenticatedUserSnapshot resolveUser(AuthenticatedPrincipal principal) {
        if (principal == null) {
            throw new BootstrapAuthenticationRequiredException();
        }
        try {
            var user = authenticatedUsers.resolve(principal);
            if (!user.active()) {
                throw new BootstrapAccessDeniedException();
            }
            return user;
        } catch (InvalidAuthenticatedPrincipalException exception) {
            throw new BootstrapAuthenticationRequiredException();
        } catch (DisabledUserException exception) {
            throw new BootstrapAccessDeniedException();
        }
    }

    private static BootstrapBusinessSummary selectBusiness(
            List<BootstrapBusinessSummary> businesses, UUID requestedBusinessId) {
        if (requestedBusinessId != null) {
            return businesses.stream()
                    .filter(business -> business.id().equals(requestedBusinessId))
                    .findFirst()
                    .orElseThrow(BootstrapAccessDeniedException::new);
        }
        return businesses.size() == 1 ? businesses.getFirst() : null;
    }

    private static BootstrapBusinessSummary toBusinessSummary(AccessibleBusinessView business) {
        return new BootstrapBusinessSummary(
                business.businessId(),
                business.tradeName(),
                business.vertical(),
                business.status(),
                business.role());
    }

    private static BootstrapInstallationSummary toInstallationSummary(
            ActiveInstallationView installation, UUID selectedBusinessId) {
        if (!selectedBusinessId.equals(installation.businessId())) {
            throw new BootstrapAccessDeniedException();
        }
        return new BootstrapInstallationSummary(
                installation.installationId(),
                installation.installationExternalId(),
                installation.businessId(),
                "ACTIVE");
    }
}
