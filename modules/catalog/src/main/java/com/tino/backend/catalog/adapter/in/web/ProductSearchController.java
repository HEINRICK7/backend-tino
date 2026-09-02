package com.tino.backend.catalog.adapter.in.web;

import com.tino.backend.catalog.application.model.ProductSearchItem;
import com.tino.backend.catalog.application.usecase.SearchProducts;
import com.tino.backend.identity.application.port.in.AuthenticatedPrincipal;
import com.tino.backend.identity.application.port.in.AuthenticatedUserResolver;
import com.tino.backend.shared.kernel.BusinessId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Small tenant-scoped catalog search used by explicit mobile product selection. */
@RestController
@RequestMapping("/api/v1/businesses/{businessId}/products")
public final class ProductSearchController {
    private final AuthenticatedUserResolver users;
    private final SearchProducts searchProducts;

    public ProductSearchController(AuthenticatedUserResolver users, SearchProducts searchProducts) {
        this.users = users;
        this.searchProducts = searchProducts;
    }

    @GetMapping
    @Operation(summary = "Search active products for explicit goods receipt selection")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tenant-scoped product matches"),
        @ApiResponse(responseCode = "400", description = "Invalid search", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Business access denied", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public List<ProductSearchItem> search(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID businessId,
            @RequestParam(name = "q", required = false) String text,
            @RequestParam(required = false) String gtin,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        var user = users.resolve(requirePrincipal(principal));
        return searchProducts.execute(user.userId(), new BusinessId(businessId), text, gtin, limit, offset);
    }

    private static AuthenticatedPrincipal requirePrincipal(AuthenticatedPrincipal principal) {
        if (principal == null) throw new IllegalArgumentException("authentication required");
        return principal;
    }

    public record ErrorResponse(String code, String message, boolean retryable,
            @com.fasterxml.jackson.annotation.JsonProperty("correlation_id") String correlationId) {}
}
