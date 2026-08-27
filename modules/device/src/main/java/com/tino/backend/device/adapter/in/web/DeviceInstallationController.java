package com.tino.backend.device.adapter.in.web;

import com.tino.backend.device.application.exception.UnauthenticatedDeviceRequestException;
import com.tino.backend.device.application.exception.DeviceInstallationAccessDeniedException;
import com.tino.backend.device.application.usecase.RegisterDeviceInstallation;
import com.tino.backend.device.domain.model.DeviceInstallation;
import com.tino.backend.device.domain.model.InstallationStatus;
import com.tino.backend.identity.application.exception.DisabledUserException;
import com.tino.backend.identity.application.exception.InvalidAuthenticatedPrincipalException;
import com.tino.backend.identity.application.port.in.AuthenticatedPrincipal;
import com.tino.backend.identity.application.port.in.AuthenticatedUserResolver;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Minimal authenticated adapter for registering a logical app installation. */
@RestController
@RequestMapping("/api/v1/businesses/{businessId}/installations")
public final class DeviceInstallationController {
    private final AuthenticatedUserResolver authenticatedUsers;
    private final RegisterDeviceInstallation registerInstallation;

    public DeviceInstallationController(
            AuthenticatedUserResolver authenticatedUsers,
            RegisterDeviceInstallation registerInstallation) {
        this.authenticatedUsers = authenticatedUsers;
        this.registerInstallation = registerInstallation;
    }

    @PostMapping
    public ResponseEntity<InstallationResponse> register(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID businessId,
            @Valid @RequestBody RegisterInstallationRequest request) {
        if (principal == null) {
            throw new UnauthenticatedDeviceRequestException();
        }
        try {
            var user = authenticatedUsers.resolve(principal);
            if (!user.active()) {
                throw new UnauthenticatedDeviceRequestException();
            }
            var installation = registerInstallation.execute(
                    user.userId(),
                    new com.tino.backend.shared.kernel.BusinessId(businessId),
                    request.installationId());
            return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(installation));
        } catch (DisabledUserException exception) {
            throw new DeviceInstallationAccessDeniedException();
        } catch (InvalidAuthenticatedPrincipalException exception) {
            throw new UnauthenticatedDeviceRequestException();
        }
    }

    private static InstallationResponse toResponse(DeviceInstallation installation) {
        return new InstallationResponse(
                installation.id().value(),
                installation.externalId().value(),
                installation.businessId().value(),
                installation.status());
    }

    public record RegisterInstallationRequest(@NotBlank String installationId) {}

    public record InstallationResponse(
            UUID id,
            String installationId,
            UUID businessId,
            InstallationStatus status) {}
}
