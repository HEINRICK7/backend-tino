package com.tino.backend.business.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tino.backend.business.application.exception.BusinessAccessDeniedException;
import com.tino.backend.business.application.exception.InactiveAuthenticatedUserException;
import com.tino.backend.business.application.model.AuthenticatedUser;
import com.tino.backend.business.application.port.out.BusinessMembershipRepository;
import com.tino.backend.business.application.port.out.BusinessRepository;
import com.tino.backend.business.application.usecase.CreateBusiness;
import com.tino.backend.business.application.usecase.ExecuteAuthorizedBusinessOperation;
import com.tino.backend.business.application.usecase.ListUserBusinesses;
import com.tino.backend.business.application.usecase.ResolveBusinessAccess;
import com.tino.backend.business.domain.model.Business;
import com.tino.backend.business.domain.model.BusinessMembership;
import com.tino.backend.business.domain.model.BusinessName;
import com.tino.backend.business.domain.model.BusinessRole;
import com.tino.backend.business.domain.model.BusinessStatus;
import com.tino.backend.business.domain.model.BusinessVertical;
import com.tino.backend.business.domain.model.MembershipId;
import com.tino.backend.business.domain.model.MembershipStatus;
import com.tino.backend.business.domain.model.UserId;
import com.tino.backend.shared.kernel.BusinessId;
import com.tino.backend.shared.kernel.TenantContextExecutor;
import com.tino.backend.shared.kernel.UuidV7Generator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class BusinessUseCaseTest {
    private static final Instant NOW = Instant.parse("2026-08-27T12:00:00.123456Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void domainCreateBusinessActive() {
        var businesses = new InMemoryBusinessRepository();
        var created = createBusiness(businesses).execute(
                activeUser(), "  Padaria Central  ", BusinessVertical.BAKERY);

        assertThat(created.business().status()).isEqualTo(BusinessStatus.ACTIVE);
        assertThat(created.business().tradeName().value()).isEqualTo("Padaria Central");
        assertThat(created.business().vertical()).isEqualTo(BusinessVertical.BAKERY);
        assertThat(businesses.createdBusiness).isEqualTo(created.business());
    }

    @Test
    void domainCreatorBecomesOwner() {
        var businesses = new InMemoryBusinessRepository();
        var user = activeUser();
        var created = createBusiness(businesses).execute(
                user, "Mercadinho", BusinessVertical.RETAIL);

        assertThat(created.membership().userId()).isEqualTo(user.userId());
        assertThat(created.membership().businessId()).isEqualTo(created.business().id());
        assertThat(created.membership().role()).isEqualTo(BusinessRole.OWNER);
        assertThat(created.membership().status()).isEqualTo(MembershipStatus.ACTIVE);
    }

    @Test
    void domainCreationSendsBusinessAndOwnerThroughOneAtomicPortOperation() {
        var businesses = new InMemoryBusinessRepository();
        var created = createBusiness(businesses).execute(
                activeUser(), "Loja", BusinessVertical.STORE);

        assertThat(businesses.atomicCalls).isEqualTo(1);
        assertThat(businesses.createdOwner.businessId()).isEqualTo(created.business().id());
    }

    @Test
    void domainListOwnBusinesses() {
        var user = activeUser();
        var first = business(BusinessStatus.ACTIVE);
        var second = business(BusinessStatus.ACTIVE);
        var memberships = new InMemoryMembershipRepository();
        memberships.put(owner(first, user, MembershipStatus.ACTIVE));
        memberships.put(new BusinessMembership(
                new MembershipId(new UuidV7Generator().next()),
                second.id(), user.userId(), BusinessRole.STAFF, MembershipStatus.ACTIVE, NOW, NOW));
        var businesses = new InMemoryBusinessRepository(first, second);

        var result = new ListUserBusinesses(memberships, businesses).execute(user.userId());

        assertThat(result).extracting(item -> item.business().id()).containsExactly(first.id(), second.id());
        assertThat(result).extracting(item -> item.role()).containsExactly(BusinessRole.OWNER, BusinessRole.STAFF);
    }

    @Test
    void domainListDoesNotExposeForeignBusinesses() {
        var user = activeUser();
        var otherUser = new AuthenticatedUser(new UserId(new UuidV7Generator().next()), true);
        var mine = business(BusinessStatus.ACTIVE);
        var foreign = business(BusinessStatus.ACTIVE);
        var memberships = new InMemoryMembershipRepository();
        memberships.put(owner(mine, user, MembershipStatus.ACTIVE));
        memberships.put(owner(foreign, otherUser, MembershipStatus.ACTIVE));

        var result = new ListUserBusinesses(memberships, new InMemoryBusinessRepository(mine, foreign))
                .execute(user.userId());

        assertThat(result).extracting(item -> item.business().id()).containsExactly(mine.id());
    }

    @Test
    void domainActiveMembershipAndBusinessAuthorizeContext() {
        var user = activeUser();
        var business = business(BusinessStatus.ACTIVE);
        var memberships = new InMemoryMembershipRepository();
        memberships.put(owner(business, user, MembershipStatus.ACTIVE));

        var context = new ResolveBusinessAccess(
                memberships, new InMemoryBusinessRepository(business)).execute(user.userId(), business.id());

        assertThat(context.userId()).isEqualTo(user.userId());
        assertThat(context.businessId()).isEqualTo(business.id());
        assertThat(context.role()).isEqualTo(BusinessRole.OWNER);
    }

    @Test
    void domainMissingMembershipIsDenied() {
        var user = activeUser();
        var business = business(BusinessStatus.ACTIVE);

        assertThatThrownBy(() -> new ResolveBusinessAccess(
                new InMemoryMembershipRepository(), new InMemoryBusinessRepository(business))
                .execute(user.userId(), business.id()))
                .isInstanceOf(BusinessAccessDeniedException.class);
    }

    @Test
    void domainDisabledMembershipIsDenied() {
        var user = activeUser();
        var business = business(BusinessStatus.ACTIVE);
        var memberships = new InMemoryMembershipRepository();
        memberships.put(owner(business, user, MembershipStatus.DISABLED));

        assertThatThrownBy(() -> new ResolveBusinessAccess(
                memberships, new InMemoryBusinessRepository(business)).execute(user.userId(), business.id()))
                .isInstanceOf(BusinessAccessDeniedException.class);
    }

    @Test
    void domainDisabledBusinessIsDenied() {
        var user = activeUser();
        var business = business(BusinessStatus.DISABLED);
        var memberships = new InMemoryMembershipRepository();
        memberships.put(owner(business, user, MembershipStatus.ACTIVE));

        assertThatThrownBy(() -> new ResolveBusinessAccess(
                memberships, new InMemoryBusinessRepository(business)).execute(user.userId(), business.id()))
                .isInstanceOf(BusinessAccessDeniedException.class);
    }

    @Test
    void domainUserCanHaveMultipleBusinesses() {
        var user = activeUser();
        var first = business(BusinessStatus.ACTIVE);
        var second = business(BusinessStatus.ACTIVE);
        var memberships = new InMemoryMembershipRepository();
        memberships.put(owner(first, user, MembershipStatus.ACTIVE));
        memberships.put(owner(second, user, MembershipStatus.ACTIVE));

        assertThat(new ListUserBusinesses(memberships, new InMemoryBusinessRepository(first, second))
                .execute(user.userId())).hasSize(2);
    }

    @Test
    void domainBusinessNameIsTrimmedNonBlankAndBounded() {
        assertThat(new BusinessName("  Loja  ").value()).isEqualTo("Loja");
        assertThatThrownBy(() -> new BusinessName("  "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BusinessName("x".repeat(BusinessName.MAX_LENGTH + 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void domainVocabularyIsExactlyTheApprovedM3Set() {
        assertThat(BusinessVertical.values()).containsExactly(
                BusinessVertical.RETAIL,
                BusinessVertical.BAKERY,
                BusinessVertical.RESTAURANT,
                BusinessVertical.STORE,
                BusinessVertical.OTHER);
        assertThat(BusinessStatus.values()).containsExactly(
                BusinessStatus.ACTIVE, BusinessStatus.DISABLED);
        assertThat(BusinessRole.values()).containsExactly(BusinessRole.OWNER, BusinessRole.STAFF);
        assertThat(MembershipStatus.values()).containsExactly(
                MembershipStatus.ACTIVE, MembershipStatus.DISABLED);
    }

    @Test
    void domainApplicationUsesSpecificPortsAndReturnsFrameworkIndependentContext() {
        var user = activeUser();
        var business = business(BusinessStatus.ACTIVE);
        var memberships = new InMemoryMembershipRepository();
        memberships.put(owner(business, user, MembershipStatus.ACTIVE));

        var context = new ResolveBusinessAccess(
                memberships, new InMemoryBusinessRepository(business)).execute(user.userId(), business.id());

        assertThat(context.getClass().getPackageName())
                .isEqualTo("com.tino.backend.business.application.model");
        assertThat(context.toString()).doesNotContain("Jwt", "DSLContext", "Authentication");
    }

    @Test
    void domainIdentityInputIsOnlyAnOpaqueInternalUserSnapshot() {
        var first = activeUser();
        var second = activeUser();

        assertThat(first.userId()).isNotEqualTo(second.userId());
        assertThat(first.active()).isTrue();
    }

    @Test
    void domainListingFiltersDisabledBusiness() {
        var user = activeUser();
        var active = business(BusinessStatus.ACTIVE);
        var disabled = business(BusinessStatus.DISABLED);
        var memberships = new InMemoryMembershipRepository();
        memberships.put(owner(active, user, MembershipStatus.ACTIVE));
        memberships.put(owner(disabled, user, MembershipStatus.ACTIVE));

        var listed = new ListUserBusinesses(
                memberships, new InMemoryBusinessRepository(active, disabled)).execute(user.userId());

        assertThat(listed).extracting(item -> item.business().id()).containsExactly(active.id());
    }

    @Test
    void domainBusinessAndMembershipIdentifiersAreUuidV7() {
        var created = createBusiness(new InMemoryBusinessRepository()).execute(
                activeUser(), "Restaurante", BusinessVertical.RESTAURANT);

        assertThat(created.business().id().value().version()).isEqualTo(7);
        assertThat(created.membership().id().value().version()).isEqualTo(7);
    }

    @Test
    void domainInactiveAuthenticatedUserCannotCreate() {
        var inactive = new AuthenticatedUser(new UserId(new UuidV7Generator().next()), false);

        assertThatThrownBy(() -> createBusiness(new InMemoryBusinessRepository())
                .execute(inactive, "Nope", BusinessVertical.OTHER))
                .isInstanceOf(InactiveAuthenticatedUserException.class);
    }

    @Test
    void domainMembershipAuthorizationPrecedesTenantContext() {
        var events = new ArrayList<String>();
        var user = activeUser();
        var business = business(BusinessStatus.ACTIVE);
        var memberships = new InMemoryMembershipRepository(events);
        memberships.put(owner(business, user, MembershipStatus.ACTIVE));
        var businesses = new InMemoryBusinessRepository(events, business);
        var tenant = new TenantContextExecutor() {
            @Override
            public <T> T execute(BusinessId businessId, Supplier<T> operation) {
                events.add("tenant-context");
                return operation.get();
            }
        };

        var result = new ExecuteAuthorizedBusinessOperation(
                new ResolveBusinessAccess(memberships, businesses), tenant)
                .execute(user.userId(), business.id(), ignored -> {
                    events.add("operation");
                    return "done";
                });

        assertThat(result).isEqualTo("done");
        assertThat(events).containsExactly("membership", "business", "tenant-context", "operation");
    }

    private static CreateBusiness createBusiness(BusinessRepository businesses) {
        return new CreateBusiness(businesses, new UuidV7Generator(), CLOCK);
    }

    private static AuthenticatedUser activeUser() {
        return new AuthenticatedUser(new UserId(new UuidV7Generator().next()), true);
    }

    private static Business business(BusinessStatus status) {
        return new Business(
                new BusinessId(new UuidV7Generator().next()),
                new BusinessName("Business " + UUID.randomUUID()),
                BusinessVertical.OTHER,
                status,
                NOW,
                NOW);
    }

    private static BusinessMembership owner(
            Business business, AuthenticatedUser user, MembershipStatus status) {
        return new BusinessMembership(
                new MembershipId(new UuidV7Generator().next()),
                business.id(),
                user.userId(),
                BusinessRole.OWNER,
                status,
                NOW,
                NOW);
    }

    private static final class InMemoryBusinessRepository implements BusinessRepository {
        private final Map<BusinessId, Business> values = new LinkedHashMap<>();
        private final List<String> events;
        private Business createdBusiness;
        private BusinessMembership createdOwner;
        private int atomicCalls;

        private InMemoryBusinessRepository(Business... businesses) {
            this(new ArrayList<>(), businesses);
        }

        private InMemoryBusinessRepository(List<String> events, Business... businesses) {
            this.events = events;
            for (var business : businesses) {
                values.put(business.id(), business);
            }
        }

        @Override
        public void createWithOwner(Business business, BusinessMembership owner) {
            atomicCalls++;
            createdBusiness = business;
            createdOwner = owner;
            values.put(business.id(), business);
        }

        @Override
        public Optional<Business> findById(BusinessId businessId) {
            events.add("business");
            return Optional.ofNullable(values.get(businessId));
        }

        @Override
        public List<Business> findByIds(Collection<BusinessId> businessIds) {
            return businessIds.stream().map(values::get).filter(value -> value != null).toList();
        }
    }

    private static final class InMemoryMembershipRepository implements BusinessMembershipRepository {
        private final Map<String, BusinessMembership> values = new LinkedHashMap<>();
        private final List<String> events;

        private InMemoryMembershipRepository() {
            this(new ArrayList<>());
        }

        private InMemoryMembershipRepository(List<String> events) {
            this.events = events;
        }

        @Override
        public void insert(BusinessMembership membership) {
            values.put(key(membership.userId(), membership.businessId()), membership);
        }

        @Override
        public Optional<BusinessMembership> findByUserAndBusiness(
                UserId userId, BusinessId businessId) {
            events.add("membership");
            return Optional.ofNullable(values.get(key(userId, businessId)));
        }

        @Override
        public List<BusinessMembership> findActiveByUser(UserId userId) {
            return values.values().stream()
                    .filter(value -> value.userId().equals(userId))
                    .filter(value -> value.status() == MembershipStatus.ACTIVE)
                    .toList();
        }

        private static String key(UserId userId, BusinessId businessId) {
            return userId.value() + ":" + businessId.value();
        }

        private void put(BusinessMembership membership) {
            values.put(key(membership.userId(), membership.businessId()), membership);
        }
    }
}
