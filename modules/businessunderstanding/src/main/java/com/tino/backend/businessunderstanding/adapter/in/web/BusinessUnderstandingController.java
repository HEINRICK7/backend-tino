package com.tino.backend.businessunderstanding.adapter.in.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tino.backend.business.application.port.in.BusinessAuthorizationDeniedException;
import com.tino.backend.businessunderstanding.application.exception.InvalidBusinessUnderstandingException;
import com.tino.backend.businessunderstanding.application.model.ItemPurposeResolution;
import com.tino.backend.businessunderstanding.application.usecase.ConfirmItemPurpose;
import com.tino.backend.businessunderstanding.application.usecase.GetActivityCatalog;
import com.tino.backend.businessunderstanding.application.usecase.GetBusinessUnderstanding;
import com.tino.backend.businessunderstanding.application.usecase.ReplaceBusinessActivities;
import com.tino.backend.businessunderstanding.application.usecase.ReplaceOperatingModes;
import com.tino.backend.businessunderstanding.application.usecase.ResolveItemPurpose;
import com.tino.backend.businessunderstanding.domain.model.ActivityCode;
import com.tino.backend.businessunderstanding.domain.model.BusinessActivity;
import com.tino.backend.businessunderstanding.domain.model.BusinessUnderstandingSnapshot;
import com.tino.backend.businessunderstanding.domain.model.ItemPurpose;
import com.tino.backend.businessunderstanding.domain.model.ItemPurposeHint;
import com.tino.backend.businessunderstanding.domain.model.OperatingMode;
import com.tino.backend.businessunderstanding.domain.model.UsageContext;
import com.tino.backend.identity.application.port.in.AuthenticatedPrincipal;
import com.tino.backend.identity.application.port.in.AuthenticatedUserResolver;
import com.tino.backend.shared.kernel.BusinessId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class BusinessUnderstandingController {
    private final AuthenticatedUserResolver users;
    private final GetActivityCatalog getActivityCatalog;
    private final GetBusinessUnderstanding getBusinessUnderstanding;
    private final ReplaceBusinessActivities replaceBusinessActivities;
    private final ReplaceOperatingModes replaceOperatingModes;
    private final ResolveItemPurpose resolveItemPurpose;
    private final ConfirmItemPurpose confirmItemPurpose;

    public BusinessUnderstandingController(AuthenticatedUserResolver users, GetActivityCatalog getActivityCatalog,
            GetBusinessUnderstanding getBusinessUnderstanding, ReplaceBusinessActivities replaceBusinessActivities,
            ReplaceOperatingModes replaceOperatingModes, ResolveItemPurpose resolveItemPurpose,
            ConfirmItemPurpose confirmItemPurpose) {
        this.users = users;
        this.getActivityCatalog = getActivityCatalog;
        this.getBusinessUnderstanding = getBusinessUnderstanding;
        this.replaceBusinessActivities = replaceBusinessActivities;
        this.replaceOperatingModes = replaceOperatingModes;
        this.resolveItemPurpose = resolveItemPurpose;
        this.confirmItemPurpose = confirmItemPurpose;
    }

    @GetMapping("/api/v1/business-understanding/activities")
    public List<ActivityResponse> activities() {
        return getActivityCatalog.execute().stream()
                .map(code -> new ActivityResponse(code.name(), code.label())).toList();
    }

    @GetMapping("/api/v1/businesses/{businessId}/business-understanding")
    public UnderstandingResponse get(@AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID businessId) {
        var user = user(principal);
        return response(getBusinessUnderstanding.execute(user, new BusinessId(businessId)));
    }

    @PutMapping("/api/v1/businesses/{businessId}/business-understanding/activities")
    public UnderstandingResponse replaceActivities(@AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID businessId, @Valid @RequestBody ActivitiesRequest request) {
        var user = user(principal);
        replaceBusinessActivities.execute(user, new BusinessId(businessId), parseActivities(request));
        return response(getBusinessUnderstanding.execute(user, new BusinessId(businessId)));
    }

    @PutMapping("/api/v1/businesses/{businessId}/business-understanding/operating-modes")
    public UnderstandingResponse replaceModes(@AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID businessId, @Valid @RequestBody OperatingModesRequest request) {
        var user = user(principal);
        replaceOperatingModes.execute(user, new BusinessId(businessId), parseModes(request));
        return response(getBusinessUnderstanding.execute(user, new BusinessId(businessId)));
    }

    @PostMapping("/api/v1/businesses/{businessId}/business-understanding/item-purpose/resolve")
    public ItemPurposeResolution resolvePurpose(@AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID businessId, @Valid @RequestBody ResolvePurposeRequest request) {
        return resolveItemPurpose.execute(user(principal), new BusinessId(businessId), request.productId(),
                request.description(), UsageContext.orLegacy(request.usageContext()), parseHints(request.semanticHints()),
                request.source());
    }

    @PostMapping("/api/v1/businesses/{businessId}/business-understanding/item-purpose/confirm")
    public void confirmPurpose(@AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID businessId, @Valid @RequestBody ConfirmPurposeRequest request) {
        confirmItemPurpose.execute(user(principal), new BusinessId(businessId), request.productId(),
                UsageContext.orLegacy(request.usageContext()), parsePurpose(request.purpose()), request.reason());
    }

    private UUID user(AuthenticatedPrincipal principal) {
        if (principal == null) {
            throw new IllegalArgumentException("authentication required");
        }
        var snapshot = users.resolve(principal);
        if (!snapshot.active()) {
            throw new BusinessAuthorizationDeniedException();
        }
        return snapshot.userId();
    }

    private static List<BusinessActivity> parseActivities(ActivitiesRequest request) {
        if (request.activities() == null) {
            throw invalid("INVALID_ACTIVITY");
        }
        try {
            return request.activities().stream().map(item -> new BusinessActivity(
                    ActivityCode.parse(item.code()), item.customLabel())).toList();
        } catch (RuntimeException exception) {
            throw invalid("INVALID_ACTIVITY");
        }
    }

    private static List<OperatingMode> parseModes(OperatingModesRequest request) {
        if (request.modes() == null) {
            throw invalid("INVALID_OPERATING_MODE");
        }
        try {
            return request.modes().stream().map(OperatingMode::parse).toList();
        } catch (RuntimeException exception) {
            throw invalid("INVALID_OPERATING_MODE");
        }
    }

    private static ItemPurpose parsePurpose(String value) {
        try {
            return ItemPurpose.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw invalid("INVALID_ITEM_PURPOSE");
        }
    }

    private static List<ItemPurposeHint> parseHints(List<SemanticHintRequest> requests) {
        if (requests == null) {
            return List.of();
        }
        try {
            return requests.stream().map(request -> new ItemPurposeHint(
                    parsePurpose(request.purpose()), request.source(), request.reason())).toList();
        } catch (RuntimeException exception) {
            throw invalid("INVALID_ITEM_PURPOSE_HINT");
        }
    }

    private static InvalidBusinessUnderstandingException invalid(String code) {
        return new InvalidBusinessUnderstandingException(code);
    }

    private static UnderstandingResponse response(BusinessUnderstandingSnapshot snapshot) {
        return new UnderstandingResponse(snapshot.status().name(),
                snapshot.activities().stream().map(item -> new ActivityResponse(item.code().name(),
                        item.code() == ActivityCode.OTHER ? item.customLabel() : item.code().label())).toList(),
                snapshot.operatingModes().stream().map(item -> item.mode().name()).toList(),
                snapshot.nextAction().name());
    }

    public record ActivityResponse(String code, String label) {}

    public record ActivitiesRequest(@NotNull List<ActivityRequest> activities) {}

    public record ActivityRequest(String code, @JsonProperty("custom_label") String customLabel) {}

    public record OperatingModesRequest(@NotNull List<String> modes) {}

    public record ResolvePurposeRequest(@JsonProperty("product_id") UUID productId, String description,
            @JsonProperty("usage_context") String usageContext,
            @JsonProperty("semantic_hints") List<SemanticHintRequest> semanticHints, String source) {}

    public record SemanticHintRequest(String purpose, String source, String reason) {}

    public record ConfirmPurposeRequest(@JsonProperty("product_id") @NotNull UUID productId, String purpose,
            @JsonProperty("usage_context") String usageContext, String reason) {}

    public record UnderstandingResponse(String status, List<ActivityResponse> activities,
            @JsonProperty("operating_modes") List<String> operatingModes,
            @JsonProperty("next_action") String nextAction) {}
}
