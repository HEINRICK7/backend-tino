package com.tino.backend.customer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.customer.application.exception.CustomerConflictException;
import com.tino.backend.customer.application.port.out.CustomerRepository;
import com.tino.backend.customer.application.usecase.CreateCustomer;
import com.tino.backend.customer.application.usecase.UpdateCustomer;
import com.tino.backend.customer.domain.model.Customer;
import com.tino.backend.shared.kernel.BusinessId;
import com.tino.backend.shared.kernel.UuidGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class CustomerUseCaseTest {
    private static final UUID USER_ID = UUID.fromString("00000000-0000-7000-8000-000000000601");
    private static final UUID BUSINESS_ID = UUID.fromString("00000000-0000-7000-8000-00000000060a");
    private static final Instant NOW = Instant.parse("2026-08-29T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void createIsIdempotentForTheSameKeyAndFingerprint() {
        var repository = new MemoryRepository();
        var useCase = new CreateCustomer(authorization(), repository, new SequenceIds(), CLOCK);

        var first = useCase.execute(USER_ID, new BusinessId(BUSINESS_ID), "Maria", "Mari", "55119999",
                "request-1", "fingerprint-1");
        var replay = useCase.execute(USER_ID, new BusinessId(BUSINESS_ID), "Maria", "Mari", "55119999",
                "request-1", "fingerprint-1");

        assertThat(first.replayed()).isFalse();
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.customer().id()).isEqualTo(first.customer().id());
        assertThat(repository.customers).hasSize(1);
    }

    @Test
    void reusingKeyWithDifferentPayloadIsRejected() {
        var repository = new MemoryRepository();
        var useCase = new CreateCustomer(authorization(), repository, new SequenceIds(), CLOCK);
        useCase.execute(USER_ID, new BusinessId(BUSINESS_ID), "Maria", null, null, "request-1", "a");

        assertThatThrownBy(() -> useCase.execute(USER_ID, new BusinessId(BUSINESS_ID), "Joao", null, null,
                "request-1", "b")).isInstanceOf(CustomerConflictException.class);
    }

    @Test
    void updateKeepsIdentityAndCreationTime() {
        var repository = new MemoryRepository();
        var create = new CreateCustomer(authorization(), repository, new SequenceIds(), CLOCK);
        var update = new UpdateCustomer(authorization(), repository, CLOCK);
        var created = create.execute(USER_ID, new BusinessId(BUSINESS_ID), "Maria", null, null, "request-1", "a");

        var changed = update.execute(USER_ID, new BusinessId(BUSINESS_ID), created.customer().id(), "Maria Silva",
                "Mari", "55118888");

        assertThat(changed.id()).isEqualTo(created.customer().id());
        assertThat(changed.createdAt()).isEqualTo(created.customer().createdAt());
        assertThat(changed.name()).isEqualTo("Maria Silva");
    }

    private static BusinessAuthorization authorization() {
        return new BusinessAuthorization() {
            @Override
            public <T> T execute(UUID userId, BusinessId businessId, Function<BusinessId, T> operation) {
                return operation.apply(businessId);
            }
        };
    }

    private static final class SequenceIds implements UuidGenerator {
        private int sequence;

        @Override
        public UUID next() {
            return UUID.nameUUIDFromBytes(("customer-" + (++sequence))
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private static final class MemoryRepository implements CustomerRepository {
        private final HashMap<UUID, Customer> customers = new HashMap<>();
        private final HashMap<String, IdempotencyRecord> keys = new HashMap<>();

        @Override public Optional<Customer> find(BusinessId businessId, UUID id) {
            return Optional.ofNullable(customers.get(id));
        }
        @Override public List<Customer> findActive(BusinessId businessId) {
            return customers.values().stream().toList();
        }
        @Override public void insert(Customer customer) { customers.put(customer.id(), customer); }
        @Override public void update(Customer customer) { customers.put(customer.id(), customer); }
        @Override public void deleteUnclaimed(Customer customer) { customers.remove(customer.id()); }
        @Override public Optional<IdempotencyRecord> findIdempotency(BusinessId businessId, String key) {
            return Optional.ofNullable(keys.get(key));
        }
        @Override public boolean insertIdempotency(BusinessId businessId, String key, String fingerprint,
                UUID customerId, Instant createdAt) {
            return keys.putIfAbsent(key, new IdempotencyRecord(fingerprint, customerId)) == null;
        }
    }
}
