package com.tino.backend.credit.adapter.in.web;

import com.tino.backend.credit.application.exception.CreditUnauthenticatedException;
import com.tino.backend.credit.application.model.CreditBalanceView;
import com.tino.backend.credit.application.model.CreditOperationResult;
import com.tino.backend.credit.application.usecase.AppendCreditEntry;
import com.tino.backend.credit.application.usecase.CompensateCreditEntry;
import com.tino.backend.credit.application.usecase.GetCreditBalance;
import com.tino.backend.credit.domain.model.CreditDirection;
import com.tino.backend.identity.application.exception.DisabledUserException;
import com.tino.backend.identity.application.exception.InvalidAuthenticatedPrincipalException;
import com.tino.backend.identity.application.port.in.AuthenticatedPrincipal;
import com.tino.backend.identity.application.port.in.AuthenticatedUserResolver;
import com.tino.backend.shared.kernel.BusinessId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/businesses/{businessId}/customers/{customerId}/credit")
public final class CreditController {
    private final AuthenticatedUserResolver authenticatedUsers;
    private final AppendCreditEntry appendCreditEntry;
    private final CompensateCreditEntry compensateCreditEntry;
    private final GetCreditBalance getCreditBalance;

    public CreditController(AuthenticatedUserResolver authenticatedUsers, AppendCreditEntry appendCreditEntry,
            CompensateCreditEntry compensateCreditEntry, GetCreditBalance getCreditBalance) {
        this.authenticatedUsers = authenticatedUsers;
        this.appendCreditEntry = appendCreditEntry;
        this.compensateCreditEntry = compensateCreditEntry;
        this.getCreditBalance = getCreditBalance;
    }

    @PostMapping("/entries")
    public ResponseEntity<CreditEntryResponse> append(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID businessId,
            @PathVariable UUID customerId,
            @RequestHeader(name = "Idempotency-Key", required = true) String idempotencyKey,
            @Valid @RequestBody CreditEntryRequest request) {
        var user = resolve(principal);
        var result = appendCreditEntry.execute(user.userId(), new BusinessId(businessId), customerId,
                request.direction(), request.amount(), request.reason(), idempotencyKey, fingerprint(request));
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(toEntryResponse(result));
    }

    @PostMapping("/entries/{entryId}/compensation")
    public ResponseEntity<CreditEntryResponse> compensate(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID businessId,
            @PathVariable UUID customerId,
            @PathVariable UUID entryId,
            @RequestHeader(name = "Idempotency-Key", required = true) String idempotencyKey) {
        var user = resolve(principal);
        var result = compensateCreditEntry.execute(user.userId(), new BusinessId(businessId), customerId,
                entryId, idempotencyKey, fingerprint(entryId));
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(toEntryResponse(result));
    }

    @GetMapping
    public CreditBalanceResponse balance(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID businessId,
            @PathVariable UUID customerId) {
        var user = resolve(principal);
        return toBalanceResponse(getCreditBalance.execute(user.userId(), new BusinessId(businessId), customerId));
    }

    private com.tino.backend.identity.application.port.in.AuthenticatedUserSnapshot resolve(
            AuthenticatedPrincipal principal) {
        if (principal == null) {
            throw new CreditUnauthenticatedException();
        }
        try {
            var user = authenticatedUsers.resolve(principal);
            if (!user.active()) {
                throw new CreditUnauthenticatedException();
            }
            return user;
        } catch (DisabledUserException exception) {
            throw new CreditUnauthenticatedException();
        } catch (InvalidAuthenticatedPrincipalException exception) {
            throw new CreditUnauthenticatedException();
        }
    }

    private static String fingerprint(CreditEntryRequest request) {
        return digest(request.direction().name() + "\u0000" + request.amount().toPlainString()
                + "\u0000" + request.reason());
    }

    private static String fingerprint(UUID entryId) {
        return digest(entryId.toString());
    }

    private static String digest(String canonical) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static CreditEntryResponse toEntryResponse(CreditOperationResult result) {
        var entry = result.entry();
        return new CreditEntryResponse(entry.id(), entry.direction(), entry.amount().value(), entry.reason(),
                entry.compensatesEntryId(), result.account().id(), result.account().currency(),
                result.account().balance(), result.account().version(), entry.createdAt(), result.replayed());
    }

    private static CreditBalanceResponse toBalanceResponse(CreditBalanceView view) {
        return new CreditBalanceResponse(view.businessId().value(), view.customerId(), view.accountId(),
                view.currency(), view.balance(), view.version());
    }

    public record CreditEntryRequest(
            @NotNull CreditDirection direction,
            @NotNull @Digits(integer = 17, fraction = 2) @DecimalMin("0.01")
            @DecimalMax("99999999999999999.99") BigDecimal amount,
            @NotBlank @Size(max = 64) String reason) {}

    public record CreditEntryResponse(UUID entryId, CreditDirection direction, BigDecimal amount, String reason,
            UUID compensatesEntryId, UUID accountId, String currency, BigDecimal balance, long version,
            java.time.Instant createdAt, boolean replayed) {}

    public record CreditBalanceResponse(UUID businessId, UUID customerId, UUID accountId, String currency,
            BigDecimal balance, long version) {}
}
