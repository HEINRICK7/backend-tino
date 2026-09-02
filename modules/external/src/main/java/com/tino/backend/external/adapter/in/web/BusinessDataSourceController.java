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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.fasterxml.jackson.annotation.JsonProperty;

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

    @PutMapping
    @Operation(summary = "Select the business data source",
            description = "Sets TINO_NATIVE or registers DOCES_SONHOS as the explicit business catalog source. No secrets are accepted.")
    public BusinessDataSource put(@AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID businessId, @RequestBody DataSourceRequest request) {
        if (principal == null) throw new IllegalArgumentException("authentication required");
        var user = users.resolve(principal);
        if (!user.active()) throw new IllegalArgumentException("authentication required");
        return source.configure(user.userId(), new BusinessId(businessId), parseSourceType(request.sourceType()), request.provider());
    }

    private static com.tino.backend.external.domain.model.ExternalDataSourceType parseSourceType(String value) {
        try {
            return com.tino.backend.external.domain.model.ExternalDataSourceType.valueOf(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("unsupported business data source type", exception);
        }
    }

    public record DataSourceRequest(
            @JsonProperty("source_type")
            @io.swagger.v3.oas.annotations.media.Schema(allowableValues = {"TINO_NATIVE", "EXTERNAL_API"}, example = "EXTERNAL_API")
            String sourceType,
            @io.swagger.v3.oas.annotations.media.Schema(allowableValues = {"DOCES_SONHOS"}, example = "DOCES_SONHOS")
            String provider) {}
}
