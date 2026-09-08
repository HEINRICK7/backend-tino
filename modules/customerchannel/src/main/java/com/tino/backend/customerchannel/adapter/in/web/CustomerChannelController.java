package com.tino.backend.customerchannel.adapter.in.web;

import com.tino.backend.customerchannel.application.exception.CustomerSessionRequiredException;
import com.tino.backend.customerchannel.application.model.CustomerChannelViews;
import com.tino.backend.customerchannel.application.port.in.CustomerChannelPrincipal;
import com.tino.backend.customerchannel.application.service.CustomerChannelService;
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
    private final AuthenticatedUserResolver users;
    private final String cookieName;
    private final boolean secureCookie;

    public CustomerChannelController(CustomerChannelService channels, AuthenticatedUserResolver users,
            @Value("${tino.customer-channel.session.cookie-name:tino_customer_session}") String cookieName,
            @Value("${tino.customer-channel.session.secure:true}") boolean secureCookie) {
        this.channels = channels;
        this.users = users;
        this.cookieName = cookieName;
        this.secureCookie = secureCookie;
    }

    @PostMapping("/businesses/{businessId}/customers/{customerId}/customer-channel/invite")
    public ResponseEntity<CustomerChannelViews.InviteResponse> invite(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID businessId, @PathVariable UUID customerId,
            @RequestHeader(name = "Idempotency-Key", required = true) String idempotencyKey,
            @RequestBody(required = false) InviteRequest request) {
        var user = resolveMerchant(principal);
        var customerData = request == null ? null
                : new CustomerChannelService.InviteCustomerData(request.name(), request.phone());
        var result = channels.invite(user.userId(), new BusinessId(businessId), customerId, idempotencyKey,
                customerData);
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
}
