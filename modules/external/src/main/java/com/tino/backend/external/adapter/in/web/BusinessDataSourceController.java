package com.tino.backend.external.adapter.in.web;

import com.tino.backend.external.application.model.BusinessDataSource;
import com.tino.backend.external.application.usecase.ManageExternalBusinessDataSource;
import com.tino.backend.identity.application.port.in.AuthenticatedPrincipal;
import com.tino.backend.identity.application.port.in.AuthenticatedUserResolver;
import com.tino.backend.shared.kernel.BusinessId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/businesses/{businessId}/data-source")
@Tag(name = "External business data", description = "Business catalog source selection")
public final class BusinessDataSourceController {
    private final AuthenticatedUserResolver users;
    private final ManageExternalBusinessDataSource source;

    public BusinessDataSourceController(AuthenticatedUserResolver users, ManageExternalBusinessDataSource source) {
        this.users = users;
        this.source = source;
    }

    @GetMapping
    @Operation(summary = "Get the active business data source",
            description = "Returns DOCES_SONHOS when an external connection is configured, otherwise TINO_NATIVE.")
    public BusinessDataSource get(@AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable UUID businessId) {
        if (principal == null) throw new IllegalArgumentException("authentication required");
        var user = users.resolve(principal);
        if (!user.active()) throw new IllegalArgumentException("authentication required");
        return source.source(user.userId(), new BusinessId(businessId));
    }
}
