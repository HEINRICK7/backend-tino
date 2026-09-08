package com.tino.backend.customerchannel.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.customer.application.port.out.CustomerRepository;
import com.tino.backend.customer.domain.model.Customer;
import com.tino.backend.customer.domain.model.CustomerStatus;
import com.tino.backend.customerchannel.application.exception.CustomerChannelAccessDeniedException;
import com.tino.backend.customerchannel.application.exception.CustomerChannelInviteConflictException;
import com.tino.backend.customerchannel.application.exception.CustomerInviteInvalidException;
import com.tino.backend.customerchannel.application.exception.CustomerInvitePhoneInvalidException;
import com.tino.backend.customerchannel.application.port.out.CustomerChannelRepository;
import com.tino.backend.customerchannel.application.port.out.CustomerInviteDeliveryPort;
import com.tino.backend.customerchannel.domain.model.CustomerChannelStatus;
import com.tino.backend.shared.kernel.BusinessId;
import com.tino.backend.shared.kernel.TenantContextExecutor;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class CustomerChannelServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-08T12:00:00Z");
    private static final BusinessId BUSINESS_ID = new BusinessId(UUID.randomUUID());
    private static final BusinessId OTHER_BUSINESS_ID = new BusinessId(UUID.randomUUID());
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID OTHER_CUSTOMER_ID = UUID.randomUUID();

    @Test
    void firstInviteUsesNormalizedPhoneAndOpaqueToken() {
        var repository = new MemoryChannels();
        var delivery = new RecordingDelivery();
        var service = service(authorize(BUSINESS_ID), customerRepository(
                customer(CUSTOMER_ID, BUSINESS_ID, CustomerStatus.ACTIVE, "(86) 99592-2924")),
                repository, delivery, Clock.fixed(NOW, ZoneOffset.UTC));

        var invite = service.invite(UUID.randomUUID(), BUSINESS_ID, CUSTOMER_ID, "invite-1");

        assertThat(invite.status()).isEqualTo("INVITED");
        assertThat(invite.deliveryStatus()).isEqualTo("QUEUED");
        assertThat(delivery.items).hasSize(1);
        assertThat(delivery.items.get(0).phone()).isEqualTo("+5586995922924");
        assertThat(delivery.items.get(0).text()).startsWith("https://pwa.example/i/");
        assertThat(delivery.items.get(0).key()).startsWith("customer-channel-invite-");
        assertThat(repository.inviteCount).isEqualTo(1);
    }

    @Test
    void missingBackendCustomerIsMaterializedFromInviteData() {
        var repository = new MemoryChannels();
        var delivery = new RecordingDelivery();
        var customerRepository = customerRepository();
        var service = service(authorize(BUSINESS_ID), customerRepository,
                repository, delivery, Clock.fixed(NOW, ZoneOffset.UTC));

        var invite = service.invite(UUID.randomUUID(), BUSINESS_ID, CUSTOMER_ID, "materialize",
                new CustomerChannelService.InviteCustomerData("  João da Silva  ", "(86) 99592-2924"));

        assertThat(invite.status()).isEqualTo("INVITED");
        assertThat(delivery.items.get(0).phone()).isEqualTo("+5586995922924");
        assertThat(customerRepository.find(BUSINESS_ID, CUSTOMER_ID)).get()
                .satisfies(customer -> {
                    assertThat(customer.name()).isEqualTo("João da Silva");
                    assertThat(customer.phone()).isEqualTo("+5586995922924");
                    assertThat(customer.status()).isEqualTo(CustomerStatus.ACTIVE);
                });
    }

    @Test
    void sameKeyReplaysOriginalResultWithoutCreatingOrRevokingAnotherInvite() {
        var repository = new MemoryChannels();
        var delivery = new RecordingDelivery();
        var service = service(authorize(BUSINESS_ID), customerRepository(
                customer(CUSTOMER_ID, BUSINESS_ID, CustomerStatus.ACTIVE, "+5586995922924")),
                repository, delivery, Clock.fixed(NOW, ZoneOffset.UTC));

        var first = service.invite(UUID.randomUUID(), BUSINESS_ID, CUSTOMER_ID, "invite-replay");
        var replay = service.invite(UUID.randomUUID(), BUSINESS_ID, CUSTOMER_ID, "invite-replay");

        assertThat(replay).isEqualTo(first);
        assertThat(delivery.items).hasSize(1);
        assertThat(repository.inviteCount).isEqualTo(1);
        assertThat(repository.revokeCount).isEqualTo(1);
    }

    @Test
    void differentKeyCreatesNewTokenAndRevokesPreviousOpenInvite() {
        var repository = new MemoryChannels();
        var delivery = new RecordingDelivery();
        var service = service(authorize(BUSINESS_ID), customerRepository(
                customer(CUSTOMER_ID, BUSINESS_ID, CustomerStatus.ACTIVE, "+5586995922924")),
                repository, delivery, Clock.fixed(NOW, ZoneOffset.UTC));

        var first = service.invite(UUID.randomUUID(), BUSINESS_ID, CUSTOMER_ID, "invite-1");
        var firstToken = delivery.items.get(0).token();
        var second = service.invite(UUID.randomUUID(), BUSINESS_ID, CUSTOMER_ID, "invite-2");
        var secondToken = delivery.items.get(1).token();

        assertThat(second.channelId()).isEqualTo(first.channelId());
        assertThat(second.status()).isEqualTo("INVITED");
        assertThat(secondToken).isNotEqualTo(firstToken);
        assertThat(repository.revokeCount).isEqualTo(2);
        assertThat(repository.findValidInvite(CustomerChannelToken.hash(firstToken), NOW)).isEmpty();
        assertThat(repository.findValidInvite(CustomerChannelToken.hash(secondToken), NOW)).isPresent();
    }

    @Test
    void conflictingKeyIsTypedAndDoesNotCreateAnotherInvite() {
        var repository = new MemoryChannels();
        var delivery = new RecordingDelivery();
        var service = service(authorize(BUSINESS_ID), customerRepository(
                customer(CUSTOMER_ID, BUSINESS_ID, CustomerStatus.ACTIVE, "+5586995922924"),
                customer(OTHER_CUSTOMER_ID, BUSINESS_ID, CustomerStatus.ACTIVE, "+5586995922925")),
                repository, delivery, Clock.fixed(NOW, ZoneOffset.UTC));

        service.invite(UUID.randomUUID(), BUSINESS_ID, CUSTOMER_ID, "same-key");

        assertThatThrownBy(() -> service.invite(UUID.randomUUID(), BUSINESS_ID,
                OTHER_CUSTOMER_ID, "same-key"))
                .isInstanceOf(CustomerChannelInviteConflictException.class);
        assertThat(delivery.items).hasSize(1);
        assertThat(repository.inviteCount).isEqualTo(1);
    }

    @Test
    void explicitReinviteUsesANewDeliveryKeyAndNewInvite() {
        var repository = new MemoryChannels();
        var delivery = new RecordingDelivery();
        var service = service(authorize(BUSINESS_ID), customerRepository(
                customer(CUSTOMER_ID, BUSINESS_ID, CustomerStatus.ACTIVE, "+5586995922924")),
                repository, delivery, Clock.fixed(NOW, ZoneOffset.UTC));

        service.invite(UUID.randomUUID(), BUSINESS_ID, CUSTOMER_ID, "invite-first");
        service.invite(UUID.randomUUID(), BUSINESS_ID, CUSTOMER_ID, "invite-second");

        assertThat(delivery.items).hasSize(2);
        assertThat(delivery.items.get(0).key()).isNotEqualTo(delivery.items.get(1).key());
        assertThat(delivery.items.get(0).token()).isNotEqualTo(delivery.items.get(1).token());
        assertThat(repository.inviteCount).isEqualTo(2);
        assertThat(repository.revokeCount).isEqualTo(2);
    }

    @Test
    void providerFailureIsReturnedAndReplayedAsFailedWithoutFakeSuccess() {
        var repository = new MemoryChannels();
        var delivery = new RecordingDelivery();
        delivery.fail = true;
        var service = service(authorize(BUSINESS_ID), customerRepository(
                customer(CUSTOMER_ID, BUSINESS_ID, CustomerStatus.ACTIVE, "+5586995922924")),
                repository, delivery, Clock.fixed(NOW, ZoneOffset.UTC));

        var first = service.invite(UUID.randomUUID(), BUSINESS_ID, CUSTOMER_ID, "provider-failure");
        var replay = service.invite(UUID.randomUUID(), BUSINESS_ID, CUSTOMER_ID, "provider-failure");

        assertThat(first.deliveryStatus()).isEqualTo("FAILED");
        assertThat(replay).isEqualTo(first);
        assertThat(delivery.items).hasSize(1);
        assertThat(repository.inviteCount).isEqualTo(1);
    }

    @Test
    void activationConsumesInviteOnceAndCreatesHashedSessionToken() {
        var repository = new MemoryChannels();
        var delivery = new RecordingDelivery();
        var service = service(authorize(BUSINESS_ID), customerRepository(
                customer(CUSTOMER_ID, BUSINESS_ID, CustomerStatus.ACTIVE, "+5586995922924")),
                repository, delivery, Clock.fixed(NOW, ZoneOffset.UTC));
        service.invite(UUID.randomUUID(), BUSINESS_ID, CUSTOMER_ID, "activation");

        var activation = service.activate(delivery.items.get(0).token(), "activation-key");

        assertThat(activation.sessionToken()).isNotBlank();
        assertThat(repository.consumed).hasSize(1);
        assertThat(repository.session).isNotNull();
        assertThat(repository.session.customerId()).isEqualTo(CUSTOMER_ID);
        assertThat(repository.session.businessId()).isEqualTo(BUSINESS_ID);
        assertThat(repository.sessionTokenHash).isEqualTo(CustomerChannelToken.hash(activation.sessionToken()));
        var retry = service.activate(delivery.items.get(0).token(), "activation-key");

        assertThat(retry.sessionToken()).isNotEqualTo(activation.sessionToken());
        assertThat(repository.sessionTokenHash).isEqualTo(CustomerChannelToken.hash(retry.sessionToken()));
        assertThatThrownBy(() -> service.activate(delivery.items.get(0).token(), "another-key"))
                .isInstanceOf(CustomerInviteInvalidException.class);
    }

    @Test
    void expiredInviteCannotBeActivated() {
        var repository = new MemoryChannels();
        var delivery = new RecordingDelivery();
        var service = service(authorize(BUSINESS_ID), customerRepository(
                customer(CUSTOMER_ID, BUSINESS_ID, CustomerStatus.ACTIVE, "+5586995922924")),
                repository, delivery, Clock.fixed(NOW, ZoneOffset.UTC));
        service.invite(UUID.randomUUID(), BUSINESS_ID, CUSTOMER_ID, "expires");
        var expiredService = service(authorize(BUSINESS_ID), customerRepository(
                customer(CUSTOMER_ID, BUSINESS_ID, CustomerStatus.ACTIVE, "+5586995922924")),
                repository, new RecordingDelivery(), Clock.fixed(NOW.plusSeconds(901), ZoneOffset.UTC));

        assertThatThrownBy(() -> expiredService.activate(delivery.items.get(0).token(), "expired-key"))
                .isInstanceOf(CustomerInviteInvalidException.class);
    }

    @Test
    void foreignBusinessIsRejectedBeforeCustomerOrIdempotencyLookup() {
        var repository = new MemoryChannels();
        var delivery = new RecordingDelivery();
        var service = service(authorize(BUSINESS_ID), customerRepository(
                customer(CUSTOMER_ID, OTHER_BUSINESS_ID, CustomerStatus.ACTIVE, "+5586995922924")),
                repository, delivery, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.invite(UUID.randomUUID(), OTHER_BUSINESS_ID,
                CUSTOMER_ID, "foreign"))
                .isInstanceOf(CustomerChannelAccessDeniedException.class);
        assertThat(delivery.items).isEmpty();
        assertThat(repository.inviteCount).isZero();
    }

    @Test
    void invalidPhoneAndForeignChannelOwnershipAreRejected() {
        var invalidPhoneService = service(authorize(BUSINESS_ID), customerRepository(
                customer(CUSTOMER_ID, BUSINESS_ID, CustomerStatus.ACTIVE, "55119999")),
                new MemoryChannels(), new RecordingDelivery(), Clock.fixed(NOW, ZoneOffset.UTC));
        assertThatThrownBy(() -> invalidPhoneService.invite(UUID.randomUUID(), BUSINESS_ID,
                CUSTOMER_ID, "invalid-phone"))
                .isInstanceOf(CustomerInvitePhoneInvalidException.class);

        var foreignChannelRepository = new MemoryChannels();
        foreignChannelRepository.returnForeignChannel = true;
        var ownershipService = service(authorize(BUSINESS_ID), customerRepository(
                customer(CUSTOMER_ID, BUSINESS_ID, CustomerStatus.ACTIVE, "+5586995922924")),
                foreignChannelRepository, new RecordingDelivery(), Clock.fixed(NOW, ZoneOffset.UTC));
        assertThatThrownBy(() -> ownershipService.invite(UUID.randomUUID(), BUSINESS_ID,
                CUSTOMER_ID, "foreign-channel"))
                .isInstanceOf(CustomerChannelAccessDeniedException.class);
    }

    private static CustomerChannelService service(BusinessAuthorization authorization,
            CustomerRepository customers, MemoryChannels channels, RecordingDelivery delivery, Clock clock) {
        return new CustomerChannelService(authorization, customers, channels, delivery, UUID::randomUUID,
                clock, runWithoutDatabaseTenant(), "https://pwa.example");
    }

    private static Customer customer(UUID id, BusinessId businessId, CustomerStatus status, String phone) {
        return new Customer(id, businessId, "Maria Silva", "Mari", phone, status, NOW, NOW);
    }

    private static BusinessAuthorization authorize(BusinessId allowedBusiness) {
        return new BusinessAuthorization() {
            @Override
            public <T> T execute(UUID userId, BusinessId businessId,
                    Function<BusinessId, T> operation) {
                if (!allowedBusiness.equals(businessId)) throw new CustomerChannelAccessDeniedException();
                return operation.apply(businessId);
            }
        };
    }

    private static TenantContextExecutor runWithoutDatabaseTenant() {
        return new TenantContextExecutor() {
            @Override
            public <T> T execute(BusinessId businessId, Supplier<T> operation) {
                return operation.get();
            }
        };
    }

    private static CustomerRepository customerRepository(Customer... values) {
        var customers = new HashMap<UUID, Customer>();
        for (var value : values) customers.put(value.id(), value);
        return new CustomerRepository() {
            @Override
            public Optional<Customer> find(BusinessId businessId, UUID customerId) {
                return Optional.ofNullable(customers.get(customerId))
                        .filter(customer -> customer.businessId().equals(businessId));
            }

            @Override
            public List<Customer> findActive(BusinessId businessId) {
                return customers.values().stream()
                        .filter(customer -> customer.businessId().equals(businessId)
                                && customer.status() == CustomerStatus.ACTIVE)
                        .toList();
            }

            @Override public void insert(Customer value) { customers.put(value.id(), value); }
            @Override public void update(Customer value) { throw unsupported(); }
            @Override public void deleteUnclaimed(Customer value) { throw unsupported(); }
            @Override public Optional<CustomerRepository.IdempotencyRecord> findIdempotency(
                    BusinessId businessId, String key) { return Optional.empty(); }
            @Override public boolean insertIdempotency(BusinessId businessId, String key,
                    String fingerprint, UUID customerId, Instant createdAt) { throw unsupported(); }
        };
    }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("not used by this test");
    }

    private static final class RecordingDelivery implements CustomerInviteDeliveryPort {
        private final List<Delivery> items = new ArrayList<>();
        private boolean fail;

        @Override
        public void send(String recipientPhone, String text, String idempotencyKey) {
            var token = text.substring("https://pwa.example/i/".length());
            items.add(new Delivery(recipientPhone, text, idempotencyKey, token));
            if (fail) throw new IllegalStateException("provider unavailable");
        }

        private record Delivery(String phone, String text, String key, String token) {}
    }

    private static final class MemoryChannels implements CustomerChannelRepository {
        private final Map<String, ChannelRecord> channels = new HashMap<>();
        private final Map<UUID, InviteRecord> invites = new HashMap<>();
        private final Map<UUID, String> inviteTokenHashes = new HashMap<>();
        private final Map<String, InviteIdempotencyRecord> idempotencies = new HashMap<>();
        private final Map<String, ActivationIdempotencyRecord> activationIdempotencies = new HashMap<>();
        private final Set<UUID> consumed = new HashSet<>();
        private final Set<UUID> revoked = new HashSet<>();
        private SessionRecord session;
        private String sessionTokenHash;
        private int inviteCount;
        private int revokeCount;
        private boolean returnForeignChannel;

        @Override
        public ChannelRecord upsertChannel(UUID id, BusinessId businessId, UUID customerId, Instant now) {
            if (returnForeignChannel) {
                return new ChannelRecord(id, OTHER_BUSINESS_ID, OTHER_CUSTOMER_ID,
                        CustomerChannelStatus.INVITED, null, null);
            }
            return channels.computeIfAbsent(channelKey(businessId, customerId), ignored ->
                    new ChannelRecord(id, businessId, customerId, CustomerChannelStatus.INVITED, null, null));
        }

        @Override
        public Optional<ChannelRecord> findChannel(BusinessId businessId, UUID customerId) {
            return Optional.ofNullable(channels.get(channelKey(businessId, customerId)));
        }

        @Override
        public Optional<InviteIdempotencyRecord> findInviteIdempotency(BusinessId businessId,
                String operation, String idempotencyKey) {
            return Optional.ofNullable(idempotencies.get(idempotencyKey(businessId, operation, idempotencyKey)));
        }

        @Override
        public boolean claimInviteIdempotency(BusinessId businessId, String operation,
                String idempotencyKey, String requestFingerprint, UUID channelId, Instant createdAt) {
            var key = idempotencyKey(businessId, operation, idempotencyKey);
            if (idempotencies.containsKey(key)) return false;
            idempotencies.put(key, new InviteIdempotencyRecord(requestFingerprint, channelId, null, null));
            return true;
        }

        @Override
        public void completeInviteIdempotency(BusinessId businessId, String operation,
                String idempotencyKey, String responseStatus, String deliveryStatus) {
            var key = idempotencyKey(businessId, operation, idempotencyKey);
            var current = idempotencies.get(key);
            if (current == null) throw new IllegalStateException("idempotency claim is missing");
            idempotencies.put(key, new InviteIdempotencyRecord(current.requestFingerprint(),
                    current.channelId(), responseStatus, deliveryStatus));
        }

        @Override
        public Optional<ActivationIdempotencyRecord> findActivationIdempotency(String operation,
                String idempotencyKey) {
            return Optional.ofNullable(activationIdempotencies.get(operation + ":" + idempotencyKey));
        }

        @Override
        public boolean claimActivationIdempotency(String operation, String idempotencyKey,
                String requestFingerprint, Instant createdAt) {
            var key = operation + ":" + idempotencyKey;
            if (activationIdempotencies.containsKey(key)) return false;
            activationIdempotencies.put(key, new ActivationIdempotencyRecord(requestFingerprint,
                    null, null, null, null));
            return true;
        }

        @Override
        public void completeActivationIdempotency(String operation, String idempotencyKey,
                BusinessId businessId, UUID channelId, UUID customerId, UUID sessionId,
                Instant completedAt) {
            var key = operation + ":" + idempotencyKey;
            var current = activationIdempotencies.get(key);
            if (current == null) throw new IllegalStateException("activation idempotency claim is missing");
            activationIdempotencies.put(key, new ActivationIdempotencyRecord(
                    current.requestFingerprint(), businessId, channelId, customerId, sessionId));
        }

        @Override
        public void revokeOpenInvites(BusinessId businessId, UUID channelId, Instant now) {
            revokeCount++;
            invites.values().stream()
                    .filter(invite -> invite.businessId().equals(businessId)
                            && invite.channelId().equals(channelId))
                    .filter(invite -> !consumed.contains(invite.id()) && !revoked.contains(invite.id()))
                    .forEach(invite -> revoked.add(invite.id()));
        }

        @Override
        public void insertInvite(UUID id, BusinessId businessId, UUID channelId, String tokenHash,
                Instant expiresAt, Instant createdAt) {
            var channel = channels.values().stream()
                    .filter(value -> value.id().equals(channelId)).findFirst().orElseThrow();
            inviteCount++;
            invites.put(id, new InviteRecord(id, channelId, businessId, channel.customerId(), expiresAt));
            inviteTokenHashes.put(id, tokenHash);
        }

        @Override
        public Optional<InviteRecord> findValidInvite(String tokenHash, Instant now) {
            return invites.values().stream()
                    .filter(invite -> tokenHash.equals(inviteTokenHashes.get(invite.id())))
                    .filter(invite -> !consumed.contains(invite.id()) && !revoked.contains(invite.id()))
                    .filter(invite -> invite.expiresAt().isAfter(now))
                    .findFirst();
        }

        @Override public void consumeInvite(UUID inviteId, Instant now) { consumed.add(inviteId); }

        @Override
        public void insertSession(UUID id, UUID channelId, BusinessId businessId, UUID customerId,
                String tokenHash, Instant createdAt, Instant expiresAt) {
            session = new SessionRecord(id, channelId, businessId, customerId, expiresAt);
            sessionTokenHash = tokenHash;
            var key = channelKey(businessId, customerId);
            var current = channels.get(key);
            channels.put(key, new ChannelRecord(current.id(), current.businessId(), current.customerId(),
                    CustomerChannelStatus.ACTIVE, createdAt, createdAt));
        }

        @Override
        public Optional<SessionRecord> findActiveSession(String tokenHash, Instant now) {
            return Optional.ofNullable(session).filter(value -> value.expiresAt().isAfter(now));
        }

        @Override public void revokeSession(UUID sessionId, Instant now) {}
        @Override public void touchChannel(UUID channelId, Instant now) {}
        @Override public Optional<HomeRecord> findHome(BusinessId businessId, UUID channelId, UUID customerId) {
            return Optional.empty();
        }
        @Override public List<ActivityRecord> listActivity(BusinessId businessId, UUID customerId,
                Instant beforeAt, UUID beforeId, int limit) { return List.of(); }
        @Override public Optional<ActivityRecord> findActivity(BusinessId businessId, UUID customerId,
                UUID activityId) { return Optional.empty(); }

        private static String channelKey(BusinessId businessId, UUID customerId) {
            return businessId.value() + ":" + customerId;
        }

        private static String idempotencyKey(BusinessId businessId, String operation, String key) {
            return businessId.value() + ":" + operation + ":" + key;
        }
    }
}
