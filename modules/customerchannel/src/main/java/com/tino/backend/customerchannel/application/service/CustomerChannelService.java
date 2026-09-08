package com.tino.backend.customerchannel.application.service;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.customer.application.port.out.CustomerRepository;
import com.tino.backend.customer.domain.model.CustomerStatus;
import com.tino.backend.customerchannel.application.model.CustomerChannelViews;
import com.tino.backend.customerchannel.application.port.in.CustomerChannelPrincipal;
import com.tino.backend.customerchannel.application.port.out.CustomerChannelRepository;
import com.tino.backend.customerchannel.application.port.out.CustomerInviteDeliveryPort;
import com.tino.backend.customerchannel.application.exception.CustomerChannelAccessDeniedException;
import com.tino.backend.customerchannel.application.exception.CustomerInviteInvalidException;
import com.tino.backend.customerchannel.application.exception.CustomerInvitePhoneMissingException;
import com.tino.backend.customerchannel.application.exception.CustomerSessionRequiredException;
import com.tino.backend.shared.kernel.BusinessId;
import com.tino.backend.shared.kernel.TenantContextExecutor;
import com.tino.backend.shared.kernel.UuidGenerator;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

public class CustomerChannelService {
    private static final Duration INVITE_TTL = Duration.ofMinutes(15);
    private static final Duration SESSION_TTL = Duration.ofDays(30);

    private final BusinessAuthorization authorization;
    private final CustomerRepository customers;
    private final CustomerChannelRepository channels;
    private final CustomerInviteDeliveryPort inviteDelivery;
    private final UuidGenerator ids;
    private final Clock clock;
    private final TenantContextExecutor tenants;
    private final String publicBaseUrl;

    public CustomerChannelService(BusinessAuthorization authorization, CustomerRepository customers,
            CustomerChannelRepository channels, CustomerInviteDeliveryPort inviteDelivery,
            UuidGenerator ids, Clock clock, TenantContextExecutor tenants,
            @Value("${tino.customer-channel.public-base-url:http://localhost:5173}") String publicBaseUrl) {
        this.authorization = authorization;
        this.customers = customers;
        this.channels = channels;
        this.inviteDelivery = inviteDelivery;
        this.ids = ids;
        this.clock = clock;
        this.tenants = tenants;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    @Transactional
    public InviteResult invite(UUID userId, BusinessId businessId, UUID customerId, String idempotencyKey) {
        return authorization.execute(userId, businessId, authorizedBusiness -> {
            var customer = customers.find(authorizedBusiness, customerId)
                    .orElseThrow(CustomerChannelAccessDeniedException::new);
            if (customer.status() != CustomerStatus.ACTIVE) throw new CustomerChannelAccessDeniedException();
            if (customer.phone() == null || customer.phone().isBlank()) throw new CustomerInvitePhoneMissingException();

            var now = Instant.now(clock);
            var channel = channels.upsertChannel(ids.next(), authorizedBusiness, customerId, now);
            var token = CustomerChannelToken.random();
            channels.revokeOpenInvites(authorizedBusiness, channel.id(), now);
            channels.insertInvite(ids.next(), authorizedBusiness, channel.id(), CustomerChannelToken.hash(token),
                    now.plus(INVITE_TTL), now);

            var deliveryStatus = "FAILED";
            try {
                inviteDelivery.send(customer.phone(), publicBaseUrl + "/i/" + token,
                        idempotencyKey + ":" + channel.id());
                deliveryStatus = "QUEUED";
            } catch (RuntimeException ignored) {
                // The invite remains persisted and can be safely resent after the provider recovers.
            }
            return new InviteResult(channel.id(), channel.status().name(), deliveryStatus);
        });
    }

    @Transactional
    public ActivationResult activate(String token) {
        if (token == null || token.length() < 32 || token.length() > 256) throw new CustomerInviteInvalidException();
        var now = Instant.now(clock);
        var invite = channels.findValidInvite(CustomerChannelToken.hash(token), now)
                .orElseThrow(CustomerInviteInvalidException::new);
        var sessionToken = CustomerChannelToken.random();
        tenants.execute(invite.businessId(), () -> {
            channels.consumeInvite(invite.id(), now);
            channels.insertSession(ids.next(), invite.channelId(), invite.businessId(), invite.customerId(),
                    CustomerChannelToken.hash(sessionToken), now, now.plus(SESSION_TTL));
            return null;
        });
        return new ActivationResult(sessionToken);
    }

    @Transactional
    public CustomerChannelViews.HomeResponse home(CustomerChannelPrincipal principal) {
        var businessId = new BusinessId(principal.businessId());
        var home = tenants.execute(businessId, () -> channels.findHome(businessId, principal.channelId(), principal.customerId()))
                .orElseThrow(CustomerSessionRequiredException::new);
        if (home.channelStatus().name().equals("SUSPENDED") || home.channelStatus().name().equals("REVOKED")) {
            throw new CustomerChannelAccessDeniedException();
        }
        tenants.execute(businessId, () -> {
            channels.touchChannel(principal.channelId(), Instant.now(clock));
            return null;
        });
        return new CustomerChannelViews.HomeResponse(
                new CustomerChannelViews.Channel(home.channelStatus().name()),
                new CustomerChannelViews.Customer(home.customerName()),
                new CustomerChannelViews.Business(home.businessName()),
                new CustomerChannelViews.Account(home.accountStatus(),
                        new CustomerChannelViews.Balance(toMinor(home.balance()), home.currency()),
                        home.version(), home.asOf()),
                new CustomerChannelViews.Features(false, false, false),
                new CustomerChannelViews.Push(0));
    }

    @Transactional(readOnly = true)
    public CustomerChannelViews.ActivityPage activity(CustomerChannelPrincipal principal, int limit, String cursor) {
        if (limit < 1 || limit > 50) throw new IllegalArgumentException("limit must be between 1 and 50");
        var parsed = decodeCursor(cursor);
        var businessId = new BusinessId(principal.businessId());
        var records = tenants.execute(businessId, () -> channels.listActivity(businessId, principal.customerId(),
                parsed == null ? null : parsed.at(), parsed == null ? null : parsed.id(), limit + 1));
        var hasMore = records.size() > limit;
        var visible = hasMore ? records.subList(0, limit) : records;
        var next = hasMore ? encodeCursor(visible.get(visible.size() - 1)) : null;
        return new CustomerChannelViews.ActivityPage(visible.stream().map(CustomerChannelViews::activity).toList(),
                next, Instant.now(clock));
    }

    @Transactional(readOnly = true)
    public CustomerChannelViews.Activity activity(CustomerChannelPrincipal principal, UUID activityId) {
        var businessId = new BusinessId(principal.businessId());
        return tenants.execute(businessId, () -> channels.findActivity(businessId, principal.customerId(), activityId))
                .map(CustomerChannelViews::activity)
                .orElseThrow(CustomerChannelAccessDeniedException::new);
    }

    @Transactional
    public void logout(CustomerChannelPrincipal principal) {
        channels.revokeSession(principal.sessionId(), Instant.now(clock));
    }

    private static long toMinor(BigDecimal amount) {
        return amount.movePointRight(2).longValueExact();
    }

    private static String encodeCursor(CustomerChannelRepository.ActivityRecord item) {
        var raw = item.occurredAt() + "|" + item.id();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static Cursor decodeCursor(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            var raw = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            var separator = raw.lastIndexOf('|');
            return new Cursor(Instant.parse(raw.substring(0, separator)), UUID.fromString(raw.substring(separator + 1)));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("cursor is invalid", exception);
        }
    }

    private record Cursor(Instant at, UUID id) {}
    public record InviteResult(UUID channelId, String status, String deliveryStatus) {}
    public record ActivationResult(String sessionToken) {}
}
