package com.tino.backend.bootstrap.adapter.in.web;

import com.tino.backend.bootstrap.application.exception.BootstrapAuthenticationRequiredException;
import com.tino.backend.bootstrap.application.model.BootstrapContext;
import com.tino.backend.bootstrap.application.usecase.ResolveBootstrapContext;
import com.tino.backend.identity.application.port.in.AuthenticatedPrincipal;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated, idempotent HTTP adapter for the composed Bootstrap Context. */
@RestController
@RequestMapping("/api/v1/bootstrap")
public final class BootstrapController {
    private final ResolveBootstrapContext resolveBootstrapContext;

    public BootstrapController(ResolveBootstrapContext resolveBootstrapContext) {
        this.resolveBootstrapContext = resolveBootstrapContext;
    }

    @PostMapping
    public ResponseEntity<BootstrapContext> resolve(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @RequestBody(required = false) BootstrapRequest request) {
        if (principal == null) {
            throw new BootstrapAuthenticationRequiredException();
        }
        var input = request == null ? new BootstrapRequest(null, null) : request;
        return ResponseEntity.ok(resolveBootstrapContext.execute(
                principal, input.requestedBusinessId(), input.installationExternalId()));
    }

    public record BootstrapRequest(UUID requestedBusinessId, String installationExternalId) {}
}
