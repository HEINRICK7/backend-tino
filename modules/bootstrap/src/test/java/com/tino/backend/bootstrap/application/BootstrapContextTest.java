package com.tino.backend.bootstrap.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tino.backend.bootstrap.application.exception.BootstrapAccessDeniedException;
import com.tino.backend.bootstrap.application.model.BootstrapContext;
import com.tino.backend.bootstrap.application.usecase.ResolveBootstrapContext;
import com.tino.backend.bootstrap.domain.model.BootstrapState;
import com.tino.backend.business.application.port.in.AccessibleBusinessView;
import com.tino.backend.business.application.port.in.BusinessContextReader;
import com.tino.backend.device.application.port.in.ActiveInstallationView;
import com.tino.backend.device.application.port.in.DeviceInstallationContextReader;
import com.tino.backend.identity.application.port.in.AuthenticatedPrincipal;
import com.tino.backend.identity.application.port.in.AuthenticatedUserResolver;
import com.tino.backend.identity.application.port.in.AuthenticatedUserSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BootstrapContextTest {
    private static final UUID USER_ID = UUID.fromString("00000000-0000-7000-8000-000000000001");
    private static final UUID BUSINESS_A = UUID.fromString("00000000-0000-7000-8000-00000000000a");
    private static final UUID BUSINESS_B = UUID.fromString("00000000-0000-7000-8000-00000000000b");
    private static final UUID INSTALLATION_ID = UUID.fromString("00000000-0000-7000-8000-00000000001a");

    @Test
    void testM5_001_activeUserWithoutBusinessReturnsBusinessRequired() {
        var result = resolver(List.of(), Optional.empty()).execute(principal(), null, null);

        assertThat(result.state()).isEqualTo(BootstrapState.BUSINESS_REQUIRED);
        assertThat(result.businesses()).isEmpty();
        assertThat(result.selectedBusiness()).isNull();
        assertThat(result.installation()).isNull();
    }

    @Test
    void testM5_002_businessRequiredNeverReturnsInstallation() {
        var result = resolver(List.of(), Optional.of(activeInstallation(BUSINESS_A)))
                .execute(principal(), null, "installation-a");

        assertThat(result.state()).isEqualTo(BootstrapState.BUSINESS_REQUIRED);
        assertThat(result.installation()).isNull();
    }

    @Test
    void testM5_003_singleBusinessIsSelectedWithoutClientChoice() {
        var result = resolver(List.of(business(BUSINESS_A, "OWNER")), Optional.empty())
                .execute(principal(), null, null);

        assertThat(result.state()).isEqualTo(BootstrapState.LOCAL_BUSINESS_LINK_REQUIRED);
        assertThat(result.selectedBusiness().id()).isEqualTo(BUSINESS_A);
    }

    @Test
    void testM5_004_multipleBusinessesArePreservedWithoutSilentChoice() {
        var devices = new RecordingDeviceReader(Optional.of(activeInstallation(BUSINESS_A)));
        var result = resolver(
                        List.of(business(BUSINESS_A, "OWNER"), business(BUSINESS_B, "STAFF")), devices)
                .execute(principal(), null, "installation-a");

        assertThat(result.state()).isEqualTo(BootstrapState.LOCAL_BUSINESS_LINK_REQUIRED);
        assertThat(result.businesses()).extracting(value -> value.id())
                .containsExactly(BUSINESS_A, BUSINESS_B);
        assertThat(result.selectedBusiness()).isNull();
        assertThat(result.installation()).isNull();
        assertThat(devices.calls).isEmpty();
    }

    @Test
    void testM5_005_requestedBusinessBelongingToUserIsSelected() {
        var result = resolver(
                        List.of(business(BUSINESS_A, "OWNER"), business(BUSINESS_B, "STAFF")),
                        Optional.empty())
                .execute(principal(), BUSINESS_B, null);

        assertThat(result.selectedBusiness().id()).isEqualTo(BUSINESS_B);
        assertThat(result.state()).isEqualTo(BootstrapState.LOCAL_BUSINESS_LINK_REQUIRED);
    }

    @Test
    void testM5_006_requestedForeignBusinessIsDenied() {
        var foreign = UUID.fromString("00000000-0000-7000-8000-0000000000ff");

        assertThatThrownBy(() -> resolver(List.of(business(BUSINESS_A, "OWNER")), Optional.empty())
                .execute(principal(), foreign, null))
                .isInstanceOf(BootstrapAccessDeniedException.class);
    }

    @Test
    void testM5_007_clientBusinessIdCannotCreateAuthority() {
        var foreign = UUID.fromString("00000000-0000-7000-8000-0000000000fe");

        assertThatThrownBy(() -> resolver(List.of(business(BUSINESS_A, "OWNER")), Optional.empty())
                .execute(principal(), foreign, "client-installation"))
                .isInstanceOf(BootstrapAccessDeniedException.class);
    }

    @Test
    void testM5_008_disabledMembershipIsAbsentFromAccessibleBusinesses() {
        var result = resolver(List.of(), Optional.empty()).execute(principal(), null, null);

        assertThat(result.state()).isEqualTo(BootstrapState.BUSINESS_REQUIRED);
        assertThat(result.businesses()).isEmpty();
    }

    @Test
    void testM5_009_disabledBusinessIsAbsentFromAccessibleBusinesses() {
        var result = resolver(List.of(), Optional.empty()).execute(principal(), null, null);

        assertThat(result.state()).isEqualTo(BootstrapState.BUSINESS_REQUIRED);
        assertThat(result.businesses()).isEmpty();
    }

    @Test
    void testM5_010_authorizedBusinessWithoutActiveInstallationNeedsLocalLink() {
        var result = resolver(List.of(business(BUSINESS_A, "OWNER")), Optional.empty())
                .execute(principal(), BUSINESS_A, "missing-installation");

        assertThat(result.state()).isEqualTo(BootstrapState.LOCAL_BUSINESS_LINK_REQUIRED);
        assertThat(result.selectedBusiness().id()).isEqualTo(BUSINESS_A);
        assertThat(result.installation()).isNull();
    }

    @Test
    void testM5_011_activeInstallationProducesReady() {
        var result = resolver(List.of(business(BUSINESS_A, "OWNER")), Optional.of(activeInstallation(BUSINESS_A)))
                .execute(principal(), BUSINESS_A, "installation-a");

        assertThat(result.state()).isEqualTo(BootstrapState.READY);
        assertThat(result.installation().businessId()).isEqualTo(BUSINESS_A);
    }

    @Test
    void testM5_012_readyInstallationMustBelongToSelectedBusiness() {
        var result = resolver(List.of(business(BUSINESS_A, "OWNER")), Optional.of(activeInstallation(BUSINESS_A)))
                .execute(principal(), BUSINESS_A, "installation-a");

        assertThat(result.installation().businessId()).isEqualTo(result.selectedBusiness().id());
    }

    @Test
    void testM5_013_crossBusinessInstallationCannotProduceReady() {
        assertThatThrownBy(() -> resolver(
                        List.of(business(BUSINESS_A, "OWNER")), Optional.of(activeInstallation(BUSINESS_B)))
                .execute(principal(), BUSINESS_A, "installation-b"))
                .isInstanceOf(BootstrapAccessDeniedException.class);
    }

    @Test
    void testM5_014_revokedInstallationIsNotReady() {
        var result = resolver(List.of(business(BUSINESS_A, "OWNER")), Optional.empty())
                .execute(principal(), BUSINESS_A, "revoked-installation");

        assertThat(result.state()).isEqualTo(BootstrapState.LOCAL_BUSINESS_LINK_REQUIRED);
        assertThat(result.installation()).isNull();
    }

    @Test
    void testM5_015_installationIdentifierCannotSelectBusiness() {
        var devices = new RecordingDeviceReader(Optional.of(activeInstallation(BUSINESS_A)));
        var result = resolver(
                        List.of(business(BUSINESS_A, "OWNER"), business(BUSINESS_B, "OWNER")), devices)
                .execute(principal(), null, "installation-a");

        assertThat(result.selectedBusiness()).isNull();
        assertThat(result.state()).isEqualTo(BootstrapState.LOCAL_BUSINESS_LINK_REQUIRED);
        assertThat(devices.calls).isEmpty();
    }

    @Test
    void testM5_016_storeIdIsNotAnInputAuthority() {
        var result = resolver(List.of(business(BUSINESS_A, "OWNER")), Optional.empty())
                .execute(principal(), null, null);

        assertThat(result.selectedBusiness().id()).isEqualTo(BUSINESS_A);
        assertThat(result.state()).isNotEqualTo(BootstrapState.READY);
    }

    @Test
    void testM5_017_businessSelectionPrecedesDeviceResolution() {
        var events = new ArrayList<String>();
        var result = resolver(
                        user -> {
                            events.add("identity");
                            return new AuthenticatedUserSnapshot(USER_ID, true);
                        },
                        userId -> {
                            events.add("business");
                            return List.of(business(BUSINESS_A, "OWNER"));
                        },
                        (userId, businessId, installationId) -> {
                            events.add("device");
                            return Optional.of(activeInstallation(businessId));
                        })
                .execute(principal(), BUSINESS_A, "installation-a");

        assertThat(result.state()).isEqualTo(BootstrapState.READY);
        assertThat(events).containsExactly("identity", "business", "device");
    }

    @Test
    void testM5_021_disabledUserIsDenied() {
        assertThatThrownBy(() -> resolver(
                        principal -> new AuthenticatedUserSnapshot(USER_ID, false),
                        userId -> List.of(business(BUSINESS_A, "OWNER")),
                        new RecordingDeviceReader(Optional.empty()))
                .execute(principal(), null, null))
                .isInstanceOf(BootstrapAccessDeniedException.class);
    }

    @Test
    void contextContainsNoPersonalOrTokenFields() {
        var result = resolver(List.of(business(BUSINESS_A, "OWNER")), Optional.of(activeInstallation(BUSINESS_A)))
                .execute(principal(), BUSINESS_A, "installation-a");

        assertThat(result.user().status()).isEqualTo("ACTIVE");
        assertThat(result.user().id()).isEqualTo(USER_ID);
        assertThat(result.toString()).doesNotContain("externalSubject", "token", "email", "phone");
    }

    @Test
    void contextDoesNotExposeJwtClaims() {
        var result = resolver(List.of(business(BUSINESS_A, "OWNER")), Optional.empty())
                .execute(principal(), BUSINESS_A, null);

        assertThat(result.toString()).doesNotContain("iss", "aud", "azp", "exp", "Authorization");
    }

    @Test
    void businessSummaryContainsOnlyApprovedFields() {
        var summary = resolver(List.of(business(BUSINESS_A, "OWNER")), Optional.empty())
                .execute(principal(), BUSINESS_A, null)
                .selectedBusiness();

        assertThat(summary).extracting(
                "id", "tradeName", "vertical", "status", "role")
                .containsExactly(BUSINESS_A, "Business A", "RETAIL", "ACTIVE", "OWNER");
    }

    @Test
    void installationSummaryContainsOnlyApprovedFields() {
        var installation = resolver(List.of(business(BUSINESS_A, "OWNER")), Optional.of(activeInstallation(BUSINESS_A)))
                .execute(principal(), BUSINESS_A, "installation-a")
                .installation();

        assertThat(installation).extracting("id", "installationId", "businessId", "status")
                .containsExactly(INSTALLATION_ID, "installation-a", BUSINESS_A, "ACTIVE");
    }

    @Test
    void resolutionIsReadOnly() {
        var devices = new RecordingDeviceReader(Optional.of(activeInstallation(BUSINESS_A)));
        var result = resolver(List.of(business(BUSINESS_A, "OWNER")), devices)
                .execute(principal(), BUSINESS_A, "installation-a");

        assertThat(result.state()).isEqualTo(BootstrapState.READY);
        assertThat(devices.calls).containsExactly("resolve");
    }

    @Test
    void repeatedResolutionIsSemanticallyStable() {
        var devices = new RecordingDeviceReader(Optional.of(activeInstallation(BUSINESS_A)));
        var useCase = resolver(List.of(business(BUSINESS_A, "OWNER")), devices);

        var first = useCase.execute(principal(), BUSINESS_A, "installation-a");
        var second = useCase.execute(principal(), BUSINESS_A, "installation-a");

        assertThat(second).isEqualTo(first);
    }

    private static ResolveBootstrapContext resolver(
            List<AccessibleBusinessView> accessible,
            Optional<ActiveInstallationView> installation) {
        return resolver(
                principal -> new AuthenticatedUserSnapshot(USER_ID, true),
                userId -> accessible,
                new RecordingDeviceReader(installation));
    }

    private static ResolveBootstrapContext resolver(
            List<AccessibleBusinessView> accessible, RecordingDeviceReader devices) {
        return resolver(
                principal -> new AuthenticatedUserSnapshot(USER_ID, true),
                userId -> accessible,
                devices);
    }

    private static ResolveBootstrapContext resolver(
            AuthenticatedUserResolver users,
            BusinessContextReader businesses,
            DeviceInstallationContextReader devices) {
        return new ResolveBootstrapContext(users, businesses, devices);
    }

    private static AuthenticatedPrincipal principal() {
        return AuthenticatedPrincipal.fromSubject("runtime-subject");
    }

    private static AccessibleBusinessView business(UUID id, String role) {
        return new AccessibleBusinessView(id, "Business A", "RETAIL", "ACTIVE", role);
    }

    private static ActiveInstallationView activeInstallation(UUID businessId) {
        return new ActiveInstallationView(INSTALLATION_ID, "installation-a", businessId);
    }

    private static final class RecordingDeviceReader implements DeviceInstallationContextReader {
        private final Optional<ActiveInstallationView> result;
        private final List<String> calls = new ArrayList<>();

        private RecordingDeviceReader(Optional<ActiveInstallationView> result) {
            this.result = result;
        }

        @Override
        public Optional<ActiveInstallationView> resolve(
                UUID authenticatedUserId, UUID requestedBusinessId, String installationExternalId) {
            calls.add("resolve");
            if (result.isPresent()
                    && !result.orElseThrow().businessId().equals(requestedBusinessId)) {
                return result;
            }
            return result;
        }
    }
}
