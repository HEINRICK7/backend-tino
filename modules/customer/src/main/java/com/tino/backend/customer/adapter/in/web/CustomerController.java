package com.tino.backend.customer.adapter.in.web;

import com.tino.backend.customer.application.exception.CustomerAccessDeniedException;
import com.tino.backend.customer.application.exception.CustomerUnauthenticatedException;
import com.tino.backend.customer.application.model.CustomerView;
import com.tino.backend.customer.application.usecase.CreateCustomer;
import com.tino.backend.customer.application.usecase.GetCustomer;
import com.tino.backend.customer.application.usecase.ListCustomers;
import com.tino.backend.customer.application.usecase.UpdateCustomer;
import com.tino.backend.identity.application.exception.DisabledUserException;
import com.tino.backend.identity.application.exception.InvalidAuthenticatedPrincipalException;
import com.tino.backend.identity.application.port.in.AuthenticatedPrincipal;
import com.tino.backend.identity.application.port.in.AuthenticatedUserResolver;
import com.tino.backend.shared.kernel.BusinessId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/businesses/{businessId}/customers")
public final class CustomerController {
    private final AuthenticatedUserResolver authenticatedUsers;
    private final CreateCustomer createCustomer;
    private final ListCustomers listCustomers;
    private final GetCustomer getCustomer;
    private final UpdateCustomer updateCustomer;

    public CustomerController(AuthenticatedUserResolver authenticatedUsers, CreateCustomer createCustomer,
            ListCustomers listCustomers, GetCustomer getCustomer, UpdateCustomer updateCustomer) {
        this.authenticatedUsers = authenticatedUsers;
        this.createCustomer = createCustomer;
        this.listCustomers = listCustomers;
        this.getCustomer = getCustomer;
        this.updateCustomer = updateCustomer;
    }

    @PostMapping
    public ResponseEntity<CustomerResponse> create(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID businessId,
            @RequestHeader(name = "Idempotency-Key", required = true) String idempotencyKey,
            @Valid @RequestBody CustomerRequest request) {
        var user = resolve(principal);
        var result = createCustomer.execute(user.userId(), new BusinessId(businessId), request.name(),
                request.nickname(), request.phone(), validateKey(idempotencyKey), fingerprint(request));
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(toResponse(result.customer()));
    }

    @GetMapping
    public List<CustomerResponse> list(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID businessId) {
        var user = resolve(principal);
        return listCustomers.execute(user.userId(), new BusinessId(businessId)).stream()
                .map(CustomerController::toResponse).toList();
    }

    @GetMapping("/{customerId}")
    public CustomerResponse get(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID businessId,
            @PathVariable UUID customerId) {
        var user = resolve(principal);
        return toResponse(getCustomer.execute(user.userId(), new BusinessId(businessId), customerId));
    }

    @PutMapping("/{customerId}")
    public CustomerResponse update(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID businessId,
            @PathVariable UUID customerId,
            @Valid @RequestBody CustomerRequest request) {
        var user = resolve(principal);
        return toResponse(updateCustomer.execute(user.userId(), new BusinessId(businessId), customerId,
                request.name(), request.nickname(), request.phone()));
    }

    private com.tino.backend.identity.application.port.in.AuthenticatedUserSnapshot resolve(
            AuthenticatedPrincipal principal) {
        if (principal == null) {
            throw new CustomerUnauthenticatedException();
        }
        try {
            var user = authenticatedUsers.resolve(principal);
            if (!user.active()) {
                throw new CustomerAccessDeniedException();
            }
            return user;
        } catch (DisabledUserException exception) {
            throw new CustomerAccessDeniedException();
        } catch (InvalidAuthenticatedPrincipalException exception) {
            throw new CustomerUnauthenticatedException();
        }
    }

    private static String validateKey(String key) {
        if (key == null || key.isBlank() || key.length() > 200) {
            throw new IllegalArgumentException("Idempotency-Key must be non-blank and at most 200 characters");
        }
        return key;
    }

    private static String fingerprint(CustomerRequest request) {
        var canonical = String.join("\u0000", request.name(), nullable(request.nickname()),
                nullable(request.phone()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String nullable(String value) {
        return value == null ? "<null>" : value;
    }

    private static CustomerResponse toResponse(CustomerView customer) {
        return new CustomerResponse(customer.id(), customer.businessId(), customer.name(), customer.nickname(),
                customer.phone(), customer.status(), customer.createdAt(), customer.updatedAt());
    }

    public record CustomerRequest(@NotBlank String name, String nickname, String phone) {}

    public record CustomerResponse(UUID id, UUID businessId, String name, String nickname, String phone,
            com.tino.backend.customer.domain.model.CustomerStatus status,
            java.time.Instant createdAt, java.time.Instant updatedAt) {}
}
