package com.tino.backend.business.adapter.in.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tino.backend.business.application.model.AccessibleBusiness;
import com.tino.backend.business.application.model.AuthenticatedUser;
import com.tino.backend.business.application.model.CreatedBusiness;
import com.tino.backend.business.application.port.in.AuthenticatedUserResolver;
import com.tino.backend.business.application.usecase.CreateBusiness;
import com.tino.backend.business.application.usecase.ListUserBusinesses;
import com.tino.backend.business.domain.model.BusinessRole;
import com.tino.backend.business.domain.model.BusinessStatus;
import com.tino.backend.business.domain.model.BusinessVertical;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Minimal M3 HTTP adapter for Business creation and authenticated listings. */
@RestController
@RequestMapping("/api/v1/businesses")
public final class BusinessController {
    private final AuthenticatedUserResolver authenticatedUsers;
    private final CreateBusiness createBusiness;
    private final ListUserBusinesses listUserBusinesses;

    public BusinessController(
            AuthenticatedUserResolver authenticatedUsers,
            CreateBusiness createBusiness,
            ListUserBusinesses listUserBusinesses) {
        this.authenticatedUsers = authenticatedUsers;
        this.createBusiness = createBusiness;
        this.listUserBusinesses = listUserBusinesses;
    }

    @PostMapping
    public ResponseEntity<BusinessResponse> create(
            @AuthenticationPrincipal(expression = "externalSubject.value") String externalSubject,
            @Valid @RequestBody CreateBusinessRequest request) {
        var user = authenticatedUsers.resolve(externalSubject);
        var created = createBusiness.execute(user, request.tradeName(), parseVertical(request.vertical()));
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
    }

    @GetMapping
    public java.util.List<BusinessResponse> list(
            @AuthenticationPrincipal(expression = "externalSubject.value") String externalSubject) {
        AuthenticatedUser user = authenticatedUsers.resolve(externalSubject);
        return listUserBusinesses.execute(user.userId()).stream()
                .map(BusinessController::toResponse)
                .toList();
    }

    private static BusinessVertical parseVertical(String value) {
        try {
            return BusinessVertical.valueOf(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("unsupported business vertical", exception);
        }
    }

    private static BusinessResponse toResponse(CreatedBusiness created) {
        var business = created.business();
        return toResponse(
                business.id().value(),
                business.tradeName().value(),
                business.vertical(),
                business.status(),
                created.membership().role(),
                business.dataSourceType());
    }

    private static BusinessResponse toResponse(AccessibleBusiness accessible) {
        var business = accessible.business();
        return toResponse(
                business.id().value(),
                business.tradeName().value(),
                business.vertical(),
                business.status(),
                accessible.role(),
                business.dataSourceType());
    }

    private static BusinessResponse toResponse(
            UUID id, String tradeName, BusinessVertical vertical, BusinessStatus status, BusinessRole role,
            com.tino.backend.business.domain.model.BusinessDataSourceType dataSourceType) {
        return new BusinessResponse(id, tradeName, vertical, status, role, dataSourceType);
    }

    public record CreateBusinessRequest(
            @JsonProperty("trade_name") @NotBlank String tradeName,
            @NotBlank String vertical) {}

    public record BusinessResponse(
            UUID id,
            String tradeName,
            BusinessVertical vertical,
            BusinessStatus status,
            BusinessRole role,
            com.tino.backend.business.domain.model.BusinessDataSourceType dataSourceType) {}
}
