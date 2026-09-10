package com.tino.backend.customerchannel.adapter.in.web;

import com.tino.backend.customerchannel.application.exception.CustomerSessionRequiredException;
import com.tino.backend.customerchannel.application.model.CustomerChannelViews;
import com.tino.backend.customerchannel.application.port.in.CustomerChannelPrincipal;
import com.tino.backend.customerchannel.application.service.CustomerChannelService;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tino.backend.payment.application.port.in.CustomerPaymentIntentCreator;
import com.tino.backend.identity.application.exception.DisabledUserException;
import com.tino.backend.identity.application.exception.InvalidAuthenticatedPrincipalException;
import com.tino.backend.identity.application.port.in.AuthenticatedPrincipal;
import com.tino.backend.identity.application.port.in.AuthenticatedUserResolver;
import com.tino.backend.shared.kernel.BusinessId;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public final class CustomerChannelController {
    private final CustomerChannelService channels;
    private final CustomerPaymentIntentCreator createDebtPaymentIntent;
    private final AuthenticatedUserResolver users;
    private final String cookieName;
    private final boolean secureCookie;

    public CustomerChannelController(CustomerChannelService channels, CustomerPaymentIntentCreator createDebtPaymentIntent,
            AuthenticatedUserResolver users,
            @Value("${tino.customer-channel.session.cookie-name:tino_customer_session}") String cookieName,
            @Value("${tino.customer-channel.session.secure:true}") boolean secureCookie) {
        this.channels = channels;
        this.createDebtPaymentIntent = createDebtPaymentIntent;
        this.users = users;
        this.cookieName = cookieName;
        this.secureCookie = secureCookie;
    }

    @PostMapping("/businesses/{businessId}/customers/{customerId}/customer-channel/invite")
    public ResponseEntity<CustomerChannelViews.InviteResponse> invite(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID businessId, @PathVariable UUID customerId,
            @RequestHeader(name = "Idempotency-Key", required = true) String idempotencyKey,
            @RequestHeader(name = "X-Tino-Invite-Reissue", defaultValue = "false") boolean reissue,
            @RequestBody(required = false) InviteRequest request) {
        var user = resolveMerchant(principal);
        var customerData = request == null ? null
                : new CustomerChannelService.InviteCustomerData(request.name(), request.phone());
        var result = channels.invite(user.userId(), new BusinessId(businessId), customerId, idempotencyKey,
                customerData, reissue);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CustomerChannelViews.InviteResponse(result.channelId(), result.status(), result.deliveryStatus()));
    }

    @PostMapping("/customer-channel/activation")
    public ResponseEntity<CustomerChannelViews.ActivationResponse> activate(
            @RequestBody(required = false) ActivationRequest request,
            @RequestHeader(name = "Idempotency-Key", required = true) String idempotencyKey,
            HttpServletResponse response) {
        if (request == null || request.token() == null) throw new IllegalArgumentException("token is required");
        var result = channels.activate(request.token(), idempotencyKey);
        var cookie = ResponseCookie.from(cookieName, result.sessionToken())
                .httpOnly(true).secure(secureCookie).sameSite("Lax").path("/")
                .maxAge(Duration.ofDays(30)).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return ResponseEntity.ok(new CustomerChannelViews.ActivationResponse("ACTIVE"));
    }

    @GetMapping("/me")
    public ResponseEntity<CustomerChannelViews.HomeResponse> me(
            @AuthenticationPrincipal CustomerChannelPrincipal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(channels.home(requireCustomer(principal)));
    }

    @GetMapping("/me/activity")
    public ResponseEntity<CustomerChannelViews.ActivityPage> activity(
            @AuthenticationPrincipal CustomerChannelPrincipal principal,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String cursor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(channels.activity(requireCustomer(principal), limit, cursor));
    }

    @GetMapping("/me/activity/{activityId}")
    public ResponseEntity<CustomerChannelViews.Activity> activity(
            @AuthenticationPrincipal CustomerChannelPrincipal principal,
            @PathVariable UUID activityId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(channels.activity(requireCustomer(principal), activityId));
    }

    @PostMapping("/me/payment-intents")
    public ResponseEntity<DebtPaymentIntentResponse> createPaymentIntent(
            @AuthenticationPrincipal CustomerChannelPrincipal principal,
            @RequestHeader(name = "Idempotency-Key", required = true) String idempotencyKey,
            @RequestBody PaymentIntentRequest request) {
        var customer = requireCustomer(principal);
        if (request == null || request.amountMinor() == null || request.amountMinor() <= 0) {
            throw new IllegalArgumentException("amount_minor must be positive");
        }
        var amount = java.math.BigDecimal.valueOf(request.amountMinor(), 2);
        var fingerprint = digest(request.amountMinor().toString());
        var result = createDebtPaymentIntent.execute(new BusinessId(customer.businessId()),
                customer.customerId(), amount, idempotencyKey, fingerprint);
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(new DebtPaymentIntentResponse(new PaymentIntentPayload(result.id(), result.customerId(),
                        result.amountMinor(), result.currency(), result.pixTxid(), result.pixKey(),
                        result.copyPaste(), result.status(), result.createdAt(), result.expiresAt(),
                        result.updatedAt()), result.replayed()));
    }

    @GetMapping("/me/push-config")
    public ResponseEntity<CustomerChannelViews.PushConfigResponse> pushConfig(
            @AuthenticationPrincipal CustomerChannelPrincipal principal) {
        requireCustomer(principal);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(channels.pushConfig());
    }

    @PostMapping("/me/push-subscriptions")
    public ResponseEntity<CustomerChannelViews.PushSubscriptionResponse> registerPushSubscription(
            @AuthenticationPrincipal CustomerChannelPrincipal principal,
            @RequestBody(required = false) PushSubscriptionRequest request) {
        var customer = requireCustomer(principal);
        if (request == null || request.keys() == null) throw new IllegalArgumentException("push subscription is required");
        var result = channels.registerPushSubscription(customer, request.endpoint(),
                request.keys().p256dh(), request.keys().auth());
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/me/push-subscriptions")
    public ResponseEntity<Void> removePushSubscription(
            @AuthenticationPrincipal CustomerChannelPrincipal principal,
            @RequestBody(required = false) PushSubscriptionDeleteRequest request) {
        var customer = requireCustomer(principal);
        if (request == null) throw new IllegalArgumentException("push subscription is required");
        channels.removePushSubscription(customer, request.endpoint());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/me/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal CustomerChannelPrincipal principal,
            HttpServletResponse response) {
        var customer = requireCustomer(principal);
        channels.logout(customer);
        var cookie = ResponseCookie.from(cookieName, "").httpOnly(true).secure(secureCookie)
                .sameSite("Lax").path("/").maxAge(Duration.ZERO).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return ResponseEntity.noContent().build();
    }

    private com.tino.backend.identity.application.port.in.AuthenticatedUserSnapshot resolveMerchant(
            AuthenticatedPrincipal principal) {
        if (principal == null) throw new IllegalArgumentException("authentication required");
        try {
            var user = users.resolve(principal);
            if (!user.active()) throw new IllegalArgumentException("authentication required");
            return user;
        } catch (DisabledUserException | InvalidAuthenticatedPrincipalException exception) {
            throw new IllegalArgumentException("authentication required");
        }
    }

    private static CustomerChannelPrincipal requireCustomer(CustomerChannelPrincipal principal) {
        if (principal == null) throw new CustomerSessionRequiredException();
        return principal;
    }

    public record ActivationRequest(String token) {}

    public record InviteRequest(String name, String phone) {}

    public record PushSubscriptionRequest(String endpoint, PushSubscriptionKeys keys) {}

    public record PushSubscriptionKeys(String p256dh, String auth) {}

    public record PushSubscriptionDeleteRequest(String endpoint) {}

    public record PaymentIntentRequest(@JsonProperty("amount_minor") Long amountMinor) {}

    public record DebtPaymentIntentResponse(
            PaymentIntentPayload paymentIntent, boolean replayed) {}

    public record PaymentIntentPayload(
            UUID id, UUID customerId, long amountMinor, String currency, String pixTxid,
            String pixKey, String copyPaste, String status, java.time.Instant createdAt,
            java.time.Instant expiresAt, java.time.Instant updatedAt) {}

    private static String digest(String value) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
