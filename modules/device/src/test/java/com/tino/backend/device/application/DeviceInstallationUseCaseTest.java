package com.tino.backend.device.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.device.application.exception.DeviceInstallationAccessDeniedException;
import com.tino.backend.device.application.exception.RevokedDeviceInstallationException;
import com.tino.backend.device.application.model.ActiveDeviceInstallationContext;
import com.tino.backend.device.application.port.out.DeviceInstallationRepository;
import com.tino.backend.device.application.usecase.RegisterDeviceInstallation;
import com.tino.backend.device.application.usecase.ResolveDeviceInstallation;
import com.tino.backend.device.domain.model.DeviceInstallation;
import com.tino.backend.device.domain.model.DeviceInstallationId;
import com.tino.backend.device.domain.model.InstallationExternalId;
import com.tino.backend.device.domain.model.InstallationStatus;
import com.tino.backend.shared.kernel.BusinessId;
import com.tino.backend.shared.kernel.UuidV7Generator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class DeviceInstallationUseCaseTest {
    private static final UUID USER_A = uuidV7(1);
    private static final UUID USER_B = uuidV7(2);
    private static final BusinessId BUSINESS_A = new BusinessId(uuidV7(3));
    private static final BusinessId BUSINESS_B = new BusinessId(uuidV7(4));
    private static final Instant NOW = Instant.parse("2026-08-27T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void installationExternalIdIsOpaqueBoundedAndNormalized() {
        assertThat(new InstallationExternalId("  generated-installation  ").value())
                .isEqualTo("generated-installation");
        assertThatThrownBy(() -> new InstallationExternalId(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InstallationExternalId("x".repeat(201)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InstallationExternalId("bad\nvalue"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void domainModelsOnlyAllowActiveAndRevokedInstallationStatuses() {
        assertThat(InstallationStatus.values())
                .containsExactly(InstallationStatus.ACTIVE, InstallationStatus.REVOKED);
        var active = DeviceInstallation.active(
                new DeviceInstallationId(uuidV7(5)), BUSINESS_A,
                new InstallationExternalId("unit-status"), USER_A, NOW, NOW);
        assertThat(active.status()).isEqualTo(InstallationStatus.ACTIVE);
        assertThat(new DeviceInstallation(
                active.id(), active.businessId(), active.externalId(),
                InstallationStatus.REVOKED, active.registeredByUserId(), NOW, NOW)
                .status()).isEqualTo(InstallationStatus.REVOKED);
    }

    @Test
    void registerCreatesActiveUuidV7InstallationForAuthorizedBusiness() {
        var repository = new InMemoryRepository();
        var register = register(repository, allow(BUSINESS_A));

        var created = register.execute(USER_A, BUSINESS_A, "unit-create");

        assertThat(created.status()).isEqualTo(InstallationStatus.ACTIVE);
        assertThat(created.id().value().version()).isEqualTo(7);
        assertThat(created.businessId()).isEqualTo(BUSINESS_A);
        assertThat(created.registeredByUserId()).isEqualTo(USER_A);
    }

    @Test
    void registerIsIdempotentForSameBusinessAndExternalId() {
        var repository = new InMemoryRepository();
        var register = register(repository, allow(BUSINESS_A));

        var first = register.execute(USER_A, BUSINESS_A, "unit-idempotent");
        var second = register.execute(USER_A, BUSINESS_A, "unit-idempotent");

        assertThat(second).isEqualTo(first);
        assertThat(repository.values()).hasSize(1);
    }

    @Test
    void registrationCannotReassignExternalIdAcrossBusinesses() {
        var repository = new InMemoryRepository();
        var register = register(repository, allow(BUSINESS_A));
        register.execute(USER_A, BUSINESS_A, "unit-cross-business");

        var otherBusiness = new RegisterDeviceInstallation(
                allow(BUSINESS_B), repository, new UuidV7Generator(), CLOCK);
        assertThatThrownBy(() -> otherBusiness.execute(USER_B, BUSINESS_B, "unit-cross-business"))
                .isInstanceOf(DeviceInstallationAccessDeniedException.class);
    }

    @Test
    void revokedInstallationIsDeniedAndNeverReactivated() {
        var repository = new InMemoryRepository();
        var register = register(repository, allow(BUSINESS_A));
        var active = register.execute(USER_A, BUSINESS_A, "unit-revoked");
        repository.put(new DeviceInstallation(
                active.id(), active.businessId(), active.externalId(), InstallationStatus.REVOKED,
                active.registeredByUserId(), active.createdAt(), active.updatedAt()));

        assertThatThrownBy(() -> register.execute(USER_A, BUSINESS_A, "unit-revoked"))
                .isInstanceOf(RevokedDeviceInstallationException.class);
        assertThat(repository.findByExternalId(active.externalId()).orElseThrow().status())
                .isEqualTo(InstallationStatus.REVOKED);
    }

    @Test
    void resolverReturnsOnlyActiveInstallationInAuthorizedBusiness() {
        var repository = new InMemoryRepository();
        var register = register(repository, allow(BUSINESS_A));
        var installation = register.execute(USER_A, BUSINESS_A, "unit-resolve");
        var resolver = new ResolveDeviceInstallation(allow(BUSINESS_A), repository);

        ActiveDeviceInstallationContext resolved = resolver.execute(
                USER_A, BUSINESS_A, "unit-resolve");

        assertThat(resolved.installationId()).isEqualTo(installation.id());
        assertThat(resolved.installationExternalId()).isEqualTo(installation.externalId());
        assertThat(resolved.businessId()).isEqualTo(BUSINESS_A);
    }

    @Test
    void resolverRejectsMissingInstallationAndForeignBusiness() {
        var repository = new InMemoryRepository();
        var register = register(repository, allow(BUSINESS_A));
        register.execute(USER_A, BUSINESS_A, "unit-foreign");

        var foreignResolver = new ResolveDeviceInstallation(allow(BUSINESS_B), repository);
        assertThatThrownBy(() -> foreignResolver.execute(USER_B, BUSINESS_B, "unit-foreign"))
                .isInstanceOf(DeviceInstallationAccessDeniedException.class);
        assertThatThrownBy(() -> new ResolveDeviceInstallation(allow(BUSINESS_A), repository)
                .execute(USER_A, BUSINESS_A, "unit-missing"))
                .isInstanceOf(DeviceInstallationAccessDeniedException.class);
    }

    @Test
    void authorizationRunsBeforeInstallationRepositoryAccess() {
        var events = new ArrayList<String>();
        var repository = new InMemoryRepository(events);
        var register = register(repository, new BusinessAuthorization() {
            @Override
            public <T> T execute(UUID userId, BusinessId businessId,
                    Function<BusinessId, T> operation) {
                events.add("business-authorization");
                return operation.apply(businessId);
            }
        });

        register.execute(USER_A, BUSINESS_A, "unit-order");

        assertThat(events).startsWith("business-authorization");
        assertThat(events).contains("repository");
    }

    @Test
    void concurrentWinnerReturnedWhenInsertBecomesNoOp() {
        var repository = new InMemoryRepository();
        var winner = DeviceInstallation.active(
                new DeviceInstallationId(uuidV7(6)), BUSINESS_A,
                new InstallationExternalId("unit-race"), USER_A, NOW, NOW);
        repository.raceWinner = winner;
        var register = register(repository, allow(BUSINESS_A));

        var resolved = register.execute(USER_A, BUSINESS_A, "unit-race");

        assertThat(resolved).isEqualTo(winner);
        assertThat(repository.values()).hasSize(1);
    }

    @Test
    void oneBusinessMayHaveMultipleInstallationsAndUserMayUseAnotherBusiness() {
        var repository = new InMemoryRepository();
        var registerA = register(repository, allow(BUSINESS_A));
        var first = registerA.execute(USER_A, BUSINESS_A, "unit-one");
        var second = registerA.execute(USER_A, BUSINESS_A, "unit-two");
        var registerB = register(repository, allow(BUSINESS_B));
        var third = registerB.execute(USER_A, BUSINESS_B, "unit-three");

        assertThat(repository.values()).containsExactlyInAnyOrder(first, second, third);
        assertThat(first.businessId()).isEqualTo(BUSINESS_A);
        assertThat(third.businessId()).isEqualTo(BUSINESS_B);
    }

    private static RegisterDeviceInstallation register(
            InMemoryRepository repository, BusinessAuthorization authorization) {
        return new RegisterDeviceInstallation(
                authorization, repository, new UuidV7Generator(), CLOCK);
    }

    private static BusinessAuthorization allow(BusinessId expectedBusiness) {
        return new BusinessAuthorization() {
            @Override
            public <T> T execute(UUID userId, BusinessId requestedBusinessId,
                    Function<BusinessId, T> operation) {
                assertThat(userId).isNotNull();
                assertThat(requestedBusinessId).isEqualTo(expectedBusiness);
                return operation.apply(requestedBusinessId);
            }
        };
    }

    private static UUID uuidV7(int suffix) {
        return UUID.fromString("018f0b8e-5e2d-7abc-8a01-00000000000" + suffix);
    }

    private static final class InMemoryRepository implements DeviceInstallationRepository {
        private final Map<String, DeviceInstallation> values = new HashMap<>();
        private final ArrayList<String> events;
        private DeviceInstallation raceWinner;

        private InMemoryRepository() {
            this(new ArrayList<>());
        }

        private InMemoryRepository(ArrayList<String> events) {
            this.events = events;
        }

        @Override
        public int insertIfAbsent(DeviceInstallation installation) {
            events.add("repository");
            if (raceWinner != null) {
                values.put(raceWinner.externalId().value(), raceWinner);
                raceWinner = null;
                return 0;
            }
            if (values.putIfAbsent(installation.externalId().value(), installation) != null) {
                return 0;
            }
            return 1;
        }

        @Override
        public java.util.Optional<DeviceInstallation> findByExternalId(InstallationExternalId externalId) {
            events.add("repository");
            return java.util.Optional.ofNullable(values.get(externalId.value()));
        }

        private void put(DeviceInstallation installation) {
            values.put(installation.externalId().value(), installation);
        }

        private java.util.Collection<DeviceInstallation> values() {
            return values.values();
        }
    }
}
