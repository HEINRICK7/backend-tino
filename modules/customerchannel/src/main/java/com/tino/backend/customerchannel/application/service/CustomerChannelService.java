package com.tino.backend.customerchannel.application.service;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.customer.application.port.out.CustomerRepository;
import com.tino.backend.customer.domain.model.Customer;
import com.tino.backend.customer.domain.model.CustomerStatus;
import com.tino.backend.customerchannel.application.model.CustomerChannelViews;
import com.tino.backend.customerchannel.application.port.in.CustomerChannelPrincipal;
import com.tino.backend.customerchannel.application.port.out.CustomerChannelRepository;
import com.tino.backend.customerchannel.application.port.out.CustomerInviteDeliveryPort;
import com.tino.backend.customerchannel.domain.model.CustomerChannelStatus;
import com.tino.backend.customerchannel.application.exception.CustomerChannelAccessDeniedException;
import com.tino.backend.customerchannel.application.exception.CustomerChannelActivationConflictException;
import com.tino.backend.customerchannel.application.exception.CustomerChannelInviteConflictException;
import com.tino.backend.customerchannel.application.exception.CustomerPushDisabledException;
import com.tino.backend.customerchannel.application.exception.CustomerPushSubscriptionInvalidException;
import com.tino.backend.customerchannel.application.exception.CustomerInviteInvalidException;
import com.tino.backend.customerchannel.application.exception.CustomerInvitePhoneMissingException;
import com.tino.backend.customerchannel.application.exception.CustomerInvitePhoneInvalidException;
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
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

public class CustomerChannelService {
    private static final String INVITE_OPERATION = "CUSTOMER_CHANNEL_INVITE";
    private static final String ACTIVATION_OPERATION = "CUSTOMER_CHANNEL_ACTIVATION";
    private static final String DELIVERY_KEY_PREFIX = "customer-channel-invite-";
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
    private final CustomerPushSettings pushSettings;

    public CustomerChannelService(BusinessAuthorization authorization, CustomerRepository customers,
            CustomerChannelRepository channels, CustomerInviteDeliveryPort inviteDelivery,
            UuidGenerator ids, Clock clock, TenantContextExecutor tenants,
            @Value("${tino.customer-channel.public-base-url:http://localhost:5173}") String publicBaseUrl) {
        this(authorization, customers, channels, inviteDelivery, ids, clock, tenants, publicBaseUrl,
                CustomerPushSettings.disabled());
    }

    public CustomerChannelService(BusinessAuthorization authorization, CustomerRepository customers,
            CustomerChannelRepository channels, CustomerInviteDeliveryPort inviteDelivery,
            UuidGenerator ids, Clock clock, TenantContextExecutor tenants, String publicBaseUrl,
            CustomerPushSettings pushSettings) {
        this.authorization = authorization;
        this.customers = customers;
        this.channels = channels;
        this.inviteDelivery = inviteDelivery;
        this.ids = ids;
        this.clock = clock;
        this.tenants = tenants;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
        this.pushSettings = Objects.requireNonNull(pushSettings, "pushSettings");
    }

    public InviteResult invite(UUID userId, BusinessId businessId, UUID customerId, String idempotencyKey) {
        return invite(userId, businessId, customerId, idempotencyKey, null);
    }

    /**
     * Sends an invite for a customer. The optional customer data is used only
     * to materialize a customer that exists in the merchant app but has not
     * reached the backend yet; an existing backend customer remains
     * authoritative.
     */
    public InviteResult invite(UUID userId, BusinessId businessId, UUID customerId, String idempotencyKey,
            InviteCustomerData customerData) {
        return invite(userId, businessId, customerId, idempotencyKey, customerData, false);
    }

    /**
     * A normal invite is one-time per customer. Reissue is an explicit escape
     * hatch for a failed or no-longer-usable invite, including recovery after
     * the customer has already activated the channel but lost the session
     * cookie (for example, after uninstalling the PWA).
     */
    public InviteResult invite(UUID userId, BusinessId businessId, UUID customerId, String idempotencyKey,
            InviteCustomerData customerData, boolean reissue) {
        validateIdempotencyKey(idempotencyKey);
        Objects.requireNonNull(businessId, "businessId");
        Objects.requireNonNull(customerId, "customerId");
        var preparedCustomerData = prepareInviteCustomerData(customerData);
        var preparation = authorization.execute(userId, businessId, authorizedBusiness -> {
            var fingerprint = inviteFingerprint(authorizedBusiness, customerId, preparedCustomerData);
            var existing = channels.findInviteIdempotency(authorizedBusiness, INVITE_OPERATION, idempotencyKey);
            if (existing.isPresent()) {
                return InvitePreparation.completed(replayInvite(existing.orElseThrow(), fingerprint));
            }

            var customer = customers.find(authorizedBusiness, customerId)
                    .orElseGet(() -> materializeCustomer(authorizedBusiness, customerId, preparedCustomerData));
            if (!authorizedBusiness.equals(customer.businessId()) || !customerId.equals(customer.id())
                    || customer.status() != CustomerStatus.ACTIVE) {
                throw new CustomerChannelAccessDeniedException();
            }
            var phone = normalizeInvitePhone(customer.phone());

            var now = Instant.now(clock);
            var channel = channels.upsertChannel(ids.next(), authorizedBusiness, customerId, now);
            if (!authorizedBusiness.equals(channel.businessId()) || !customerId.equals(channel.customerId())) {
                throw new CustomerChannelAccessDeniedException();
            }
            if (channel.status() == CustomerChannelStatus.ACTIVE && !reissue) {
                return InvitePreparation.completed(new InviteResult(channel.id(), "ACTIVE", "ALREADY_ACTIVE"));
            }

            var previousInvite = channels.findLatestInvite(authorizedBusiness, channel.id());
            if (previousInvite.isPresent() && !canReissue(previousInvite.orElseThrow(), now, reissue)) {
                return InvitePreparation.completed(new InviteResult(channel.id(), "INVITED",
                        deliveryStatus(previousInvite.orElseThrow(), now)));
            }
            if (!channels.claimInviteIdempotency(authorizedBusiness, INVITE_OPERATION, idempotencyKey,
                    fingerprint, channel.id(), now)) {
                var concurrent = channels.findInviteIdempotency(authorizedBusiness, INVITE_OPERATION, idempotencyKey)
                        .orElseThrow(CustomerChannelInviteConflictException::new);
                return InvitePreparation.completed(replayInvite(concurrent, fingerprint));
            }

            var token = CustomerChannelToken.random();
            channels.revokeOpenInvites(authorizedBusiness, channel.id(), now);
            channels.insertInvite(ids.next(), authorizedBusiness, channel.id(), CustomerChannelToken.hash(token),
                    now.plus(INVITE_TTL), now);

            // Mark the committed invite as failed until the provider acknowledges it.
            // The provider call intentionally happens after this tenant transaction
            // completes, so an unavailable provider cannot hold a database connection
            // or a row lock. A later successful completion changes it to QUEUED.
            channels.completeInviteIdempotency(authorizedBusiness, INVITE_OPERATION, idempotencyKey,
                    "INVITED", "FAILED");
            return InvitePreparation.pending(new PendingInvite(
                    channel.id(),
                    phone,
                    publicBaseUrl + "/i/" + token,
                    deliveryKey(authorizedBusiness, channel.id(), idempotencyKey)));
        });

        if (preparation.completed() != null) {
            return preparation.completed();
        }

        var pending = preparation.pending();
        try {
            inviteDelivery.send(pending.phone(), pending.text(), pending.deliveryKey());
        } catch (RuntimeException ignored) {
            // The pessimistic FAILED status is already committed and replayable.
            return new InviteResult(pending.channelId(), "INVITED", "FAILED");
        }

        // Persist the provider acknowledgement in a short, independent transaction.
        // If this update fails, the invite remains FAILED and a retry never duplicates
        // the provider call for the same idempotency key.
        return authorization.execute(userId, businessId, authorizedBusiness -> {
            channels.completeInviteIdempotency(authorizedBusiness, INVITE_OPERATION, idempotencyKey,
                    "INVITED", "QUEUED");
            return new InviteResult(pending.channelId(), "INVITED", "QUEUED");
        });
    }

    private static boolean canReissue(CustomerChannelRepository.InviteHistoryRecord previous,
            Instant now, boolean reissue) {
        if (!reissue) return false;
        return "FAILED".equals(previous.deliveryStatus())
                || previous.consumed()
                || previous.revoked()
                || !previous.expiresAt().isAfter(now);
    }

    private static String deliveryStatus(CustomerChannelRepository.InviteHistoryRecord previous, Instant now) {
        return "FAILED".equals(previous.deliveryStatus())
                || previous.consumed()
                || previous.revoked()
                || !previous.expiresAt().isAfter(now)
                ? "FAILED"
                : "QUEUED";
    }

    private Customer materializeCustomer(BusinessId businessId, UUID customerId,
            InviteCustomerData customerData) {
        if (customerData == null) {
            throw new CustomerChannelAccessDeniedException();
        }
        var now = Instant.now(clock);
        var customer = new Customer(customerId, businessId, customerData.name(), null,
                customerData.phone(), CustomerStatus.ACTIVE, now, now);
        customers.insert(customer);
        return customer;
    }

    private static InviteCustomerData prepareInviteCustomerData(InviteCustomerData value) {
        if (value == null) return null;
        if (value.name() == null || value.name().isBlank() || value.name().length() > 200) {
            throw new IllegalArgumentException("customer name is required and must be at most 200 characters");
        }
        return new InviteCustomerData(value.name().trim(), normalizeInvitePhone(value.phone()));
    }

    private static String normalizeInvitePhone(String phone) {
        if (phone == null || phone.isBlank()) throw new CustomerInvitePhoneMissingException();
        var digits = phone.replaceAll("[^0-9]", "");
        if (digits.startsWith("00")) {
            digits = digits.substring(2);
        }
        if (digits.startsWith("0") && (digits.length() == 11 || digits.length() == 12)) {
            digits = digits.substring(1);
        }
        if (digits.startsWith("55")) {
            if (digits.length() == 12 && digits.charAt(4) >= '6') {
                digits = digits.substring(0, 4) + "9" + digits.substring(4);
            }
        } else if (digits.length() == 10 || digits.length() == 11) {
            digits = "55" + digits;
        } else {
            throw new CustomerInvitePhoneInvalidException();
        }
        if (!digits.matches("55[1-9][0-9](9[0-9]{8}|[2-5][0-9]{7})")) {
            throw new CustomerInvitePhoneInvalidException();
        }
        return "+" + digits;
    }

    private static String inviteFingerprint(BusinessId businessId, UUID customerId,
            InviteCustomerData customerData) {
        var dataSuffix = customerData == null ? ""
                : "\u0000" + customerData.name() + "\u0000" + customerData.phone();
        return CustomerChannelToken.hash(INVITE_OPERATION + "\u0000" + businessId.value()
                + "\u0000" + customerId + dataSuffix);
    }

    private static String deliveryKey(BusinessId businessId, UUID channelId, String idempotencyKey) {
        return DELIVERY_KEY_PREFIX + CustomerChannelToken.hash(INVITE_OPERATION + "\u0000"
                + businessId.value() + "\u0000" + channelId + "\u0000" + idempotencyKey);
    }

    private static InviteResult replayInvite(CustomerChannelRepository.InviteIdempotencyRecord record,
            String fingerprint) {
        if (!record.requestFingerprint().equals(fingerprint)
                || record.responseStatus() == null || record.deliveryStatus() == null) {
            throw new CustomerChannelInviteConflictException();
        }
        return new InviteResult(record.channelId(), record.responseStatus(), record.deliveryStatus());
    }

    private record InvitePreparation(InviteResult completed, PendingInvite pending) {
        private static InvitePreparation completed(InviteResult result) {
            return new InvitePreparation(result, null);
        }

        private static InvitePreparation pending(PendingInvite invite) {
            return new InvitePreparation(null, invite);
        }
    }

    private record PendingInvite(UUID channelId, String phone, String text, String deliveryKey) {}

    private static void validateIdempotencyKey(String value) {
        if (value == null || value.isBlank() || value.length() > 200) {
            throw new IllegalArgumentException("Idempotency-Key is required and must be at most 200 characters");
        }
    }

    public record InviteCustomerData(String name, String phone) {}

    @Transactional
    public ActivationResult activate(String token, String idempotencyKey) {
        if (token == null || token.length() < 32 || token.length() > 256) throw new CustomerInviteInvalidException();
        validateIdempotencyKey(idempotencyKey);
        var now = Instant.now(clock);
        var requestFingerprint = CustomerChannelToken.hash(token);
        if (!channels.claimActivationIdempotency(ACTIVATION_OPERATION, idempotencyKey,
                requestFingerprint, now)) {
            var existing = channels.findActivationIdempotency(ACTIVATION_OPERATION, idempotencyKey)
                    .orElseThrow(CustomerChannelActivationConflictException::new);
            if (!requestFingerprint.equals(existing.requestFingerprint())
                    || existing.businessId() == null || existing.channelId() == null
                    || existing.customerId() == null || existing.sessionId() == null) {
                throw new CustomerChannelActivationConflictException();
            }
            return replayActivation(existing, idempotencyKey, now);
        }

        var invite = channels.findValidInvite(CustomerChannelToken.hash(token), now)
                .orElseThrow(CustomerInviteInvalidException::new);
        var sessionToken = CustomerChannelToken.random();
        var sessionId = ids.next();
        tenants.execute(invite.businessId(), () -> {
            channels.consumeInvite(invite.id(), now);
            channels.insertSession(sessionId, invite.channelId(), invite.businessId(), invite.customerId(),
                    CustomerChannelToken.hash(sessionToken), now, now.plus(SESSION_TTL));
            channels.completeActivationIdempotency(ACTIVATION_OPERATION, idempotencyKey,
                    invite.businessId(), invite.channelId(), invite.customerId(), sessionId, now);
            return null;
        });
        return new ActivationResult(sessionToken);
    }

    private ActivationResult replayActivation(CustomerChannelRepository.ActivationIdempotencyRecord record,
            String idempotencyKey, Instant now) {
        var sessionToken = CustomerChannelToken.random();
        var sessionId = ids.next();
        tenants.execute(record.businessId(), () -> {
            channels.revokeSession(record.sessionId(), now);
            channels.insertSession(sessionId, record.channelId(), record.businessId(), record.customerId(),
                    CustomerChannelToken.hash(sessionToken), now, now.plus(SESSION_TTL));
            channels.completeActivationIdempotency(ACTIVATION_OPERATION, idempotencyKey,
                    record.businessId(), record.channelId(), record.customerId(), sessionId, now);
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
        var activeSubscriptions = tenants.execute(businessId,
                () -> channels.countActivePushSubscriptions(businessId, principal.customerId()));
        return CustomerChannelViews.home(home, pushSettings.enabled(), activeSubscriptions);
    }

    public CustomerChannelViews.PushConfigResponse pushConfig() {
        return new CustomerChannelViews.PushConfigResponse(
                pushSettings.enabled(), pushSettings.enabled() ? pushSettings.vapidPublicKey() : null);
    }

    @Transactional
    public CustomerChannelViews.PushSubscriptionResponse registerPushSubscription(
            CustomerChannelPrincipal principal, String endpoint, String p256dhKey, String authKey) {
        requirePushEnabled();
        validatePushSubscription(endpoint, p256dhKey, authKey);
        var businessId = new BusinessId(principal.businessId());
        var subscriptionId = tenants.execute(businessId, () -> channels.upsertPushSubscription(
                ids.next(), businessId, principal.channelId(), principal.customerId(), endpoint,
                p256dhKey, authKey, Instant.now(clock)));
        return new CustomerChannelViews.PushSubscriptionResponse(subscriptionId, "ACTIVE");
    }

    @Transactional
    public void removePushSubscription(CustomerChannelPrincipal principal, String endpoint) {
        var businessId = new BusinessId(principal.businessId());
        validateEndpoint(endpoint);
        tenants.execute(businessId, () -> {
            channels.revokePushSubscriptionByEndpoint(
                    businessId, principal.channelId(), principal.customerId(), endpoint, Instant.now(clock));
            return null;
        });
    }

    private void requirePushEnabled() {
        if (!pushSettings.enabled()) throw new CustomerPushDisabledException();
    }

    private static void validatePushSubscription(String endpoint, String p256dhKey, String authKey) {
        validateEndpoint(endpoint);
        if (!isBase64UrlKey(p256dhKey, 65) || !isBase64UrlKey(authKey, 16)) {
            throw new CustomerPushSubscriptionInvalidException();
        }
        try {
            var publicKey = Base64.getUrlDecoder().decode(p256dhKey);
            if (publicKey.length != 65 || publicKey[0] != 4) {
                throw new CustomerPushSubscriptionInvalidException();
            }
            if (Base64.getUrlDecoder().decode(authKey).length != 16) {
                throw new CustomerPushSubscriptionInvalidException();
            }
        } catch (IllegalArgumentException exception) {
            throw new CustomerPushSubscriptionInvalidException();
        }
    }

    private static boolean isBase64UrlKey(String value, int decodedLength) {
        if (value == null || value.isBlank() || value.length() > 100 || !value.matches("[A-Za-z0-9_-]+")) {
            return false;
        }
        try {
            return Base64.getUrlDecoder().decode(value).length == decodedLength;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static void validateEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank() || endpoint.length() > 2048
                || !endpoint.startsWith("https://") || endpoint.contains("@")) {
            throw new CustomerPushSubscriptionInvalidException();
        }
        try {
            var uri = java.net.URI.create(endpoint);
            if (!uri.isAbsolute() || uri.getHost() == null || uri.getRawQuery() != null
                    && uri.getRawQuery().length() > 1024) {
                throw new CustomerPushSubscriptionInvalidException();
            }
        } catch (IllegalArgumentException exception) {
            throw new CustomerPushSubscriptionInvalidException();
        }
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
