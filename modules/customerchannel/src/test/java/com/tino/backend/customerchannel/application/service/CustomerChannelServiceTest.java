package com.tino.backend.customerchannel.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.customer.application.port.out.CustomerRepository;
import com.tino.backend.customer.domain.model.Customer;
import com.tino.backend.customer.domain.model.CustomerStatus;
import com.tino.backend.customerchannel.application.port.out.CustomerChannelRepository;
import com.tino.backend.customerchannel.application.port.out.CustomerInviteDeliveryPort;
import com.tino.backend.customerchannel.domain.model.CustomerChannelStatus;
import com.tino.backend.shared.kernel.BusinessId;
import com.tino.backend.shared.kernel.TenantContextExecutor;
import com.tino.backend.shared.kernel.UuidGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class CustomerChannelServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-08T12:00:00Z");
    private static final BusinessId BUSINESS_ID = new BusinessId(UUID.randomUUID());
    private static final UUID CUSTOMER_ID = UUID.randomUUID();

    @Test
    void inviteAndActivationUseAOneTimeOpaqueTokenAndCreateACustomerSession() {
        var repository = new MemoryChannels();
        var sentText = new AtomicReference<String>();
        var service = new CustomerChannelService(
                authorizeAnyBusiness(),
                customerRepository(),
                repository,
                (CustomerInviteDeliveryPort) (phone, text, key) -> sentText.set(text),
                UUID::randomUUID,
                Clock.fixed(NOW, ZoneOffset.UTC),
                runWithoutDatabaseTenant(),
                "https://pwa.example");

        var invite = service.invite(UUID.randomUUID(), BUSINESS_ID, CUSTOMER_ID, "invite-1");

        assertThat(invite.status()).isEqualTo("INVITED");
        assertThat(invite.deliveryStatus()).isEqualTo("QUEUED");
        assertThat(sentText).hasValueSatisfying(text -> assertThat(text)
                .startsWith("https://pwa.example/i/"));

        var token = sentText.get().substring("https://pwa.example/i/".length());
        var activation = service.activate(token);

        assertThat(activation.sessionToken()).isNotBlank();
        assertThat(repository.consumed).isTrue();
        assertThat(repository.session).isNotNull();
        assertThat(repository.session.customerId()).isEqualTo(CUSTOMER_ID);
        assertThat(repository.session.businessId()).isEqualTo(BUSINESS_ID);
        assertThat(CustomerChannelToken.hash(activation.sessionToken()))
                .isEqualTo(repository.sessionTokenHash);
    }

    private static BusinessAuthorization authorizeAnyBusiness() {
        return new BusinessAuthorization() {
            @Override
            public <T> T execute(UUID userId, BusinessId businessId,
                    Function<BusinessId, T> operation) {
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

    private static CustomerRepository customerRepository() {
        var customer = new Customer(CUSTOMER_ID, BUSINESS_ID, "Maria Silva", "Mari", "55119999",
                CustomerStatus.ACTIVE, NOW, NOW);
        return new CustomerRepository() {
            @Override public Optional<Customer> find(BusinessId businessId, UUID customerId) {
                return businessId.equals(BUSINESS_ID) && customerId.equals(CUSTOMER_ID)
                        ? Optional.of(customer) : Optional.empty();
            }
            @Override public List<Customer> findActive(BusinessId businessId) { return List.of(customer); }
            @Override public void insert(Customer value) { throw unsupported(); }
            @Override public void update(Customer value) { throw unsupported(); }
            @Override public void deleteUnclaimed(Customer value) { throw unsupported(); }
            @Override public Optional<IdempotencyRecord> findIdempotency(BusinessId businessId, String key) {
                return Optional.empty();
            }
            @Override public boolean insertIdempotency(BusinessId businessId, String key,
                    String fingerprint, UUID customerId, Instant createdAt) { throw unsupported(); }
        };
    }

    private static UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException("not used by this test");
    }

    private static final class MemoryChannels implements CustomerChannelRepository {
        private ChannelRecord channel;
        private InviteRecord invite;
        private SessionRecord session;
        private String sessionTokenHash;
        private boolean consumed;

        @Override
        public ChannelRecord upsertChannel(UUID id, BusinessId businessId, UUID customerId, Instant now) {
            channel = new ChannelRecord(id, businessId, customerId, CustomerChannelStatus.INVITED, null, null);
            return channel;
        }

        @Override public Optional<ChannelRecord> findChannel(BusinessId businessId, UUID customerId) {
            return Optional.ofNullable(channel);
        }
        @Override public void revokeOpenInvites(BusinessId businessId, UUID channelId, Instant now) {}
        @Override public void insertInvite(UUID id, BusinessId businessId, UUID channelId, String tokenHash,
                Instant expiresAt, Instant createdAt) {
            invite = new InviteRecord(id, channelId, businessId, CUSTOMER_ID, expiresAt);
            this.sessionTokenHash = tokenHash;
        }
        @Override public Optional<InviteRecord> findValidInvite(String tokenHash, Instant now) {
            return !consumed && invite != null && this.sessionTokenHash.equals(tokenHash)
                    ? Optional.of(invite) : Optional.empty();
        }
        @Override public void consumeInvite(UUID inviteId, Instant now) { consumed = true; }
        @Override public void insertSession(UUID id, UUID channelId, BusinessId businessId, UUID customerId,
                String tokenHash, Instant createdAt, Instant expiresAt) {
            session = new SessionRecord(id, channelId, businessId, customerId, expiresAt);
            sessionTokenHash = tokenHash;
        }
        @Override public Optional<SessionRecord> findActiveSession(String tokenHash, Instant now) {
            return Optional.ofNullable(session);
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
    }
}
