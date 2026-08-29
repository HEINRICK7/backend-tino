package com.tino.backend;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tino.backend.identity.adapter.in.security.AuthenticatedPrincipalAuthenticationToken;
import com.tino.backend.identity.application.port.in.AuthenticatedPrincipal;
import com.tino.backend.identity.application.usecase.ResolveAuthenticatedUser;
import com.tino.backend.identity.domain.model.ExternalSubject;
import com.tino.backend.business.adapter.out.persistence.JooqBusinessMembershipRepository;
import com.tino.backend.business.adapter.out.persistence.JooqBusinessRepository;
import com.tino.backend.business.application.exception.BusinessAccessDeniedException;
import com.tino.backend.business.application.port.out.BusinessMembershipRepository;
import com.tino.backend.business.application.port.out.BusinessRepository;
import com.tino.backend.business.application.usecase.ExecuteAuthorizedBusinessOperation;
import com.tino.backend.business.domain.model.BusinessMembership;
import com.tino.backend.business.domain.model.BusinessRole;
import com.tino.backend.business.domain.model.BusinessStatus;
import com.tino.backend.business.domain.model.MembershipStatus;
import com.tino.backend.business.domain.model.UserId;
import com.tino.backend.shared.kernel.BusinessId;
import com.tino.backend.shared.kernel.TenantContextExecutor;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
@Import(M3BusinessHttpApiTest.ProbeConfiguration.class)
class M3BusinessHttpApiTest {
    @Container
    static final M2PostgresTestContainer POSTGRES = new M2PostgresTestContainer();

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private List<String> authorizationEvents;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> M2PostgresTestContainer.APP);
        registry.add("spring.datasource.password", POSTGRES::appPassword);
        registry.add("spring.flyway.user", () -> M2PostgresTestContainer.MIGRATOR);
        registry.add("spring.flyway.password", POSTGRES::migratorPassword);
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> "http://127.0.0.1:65535/realms/test");
    }

    @BeforeEach
    void clearData() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        authorizationEvents.clear();
        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), M2PostgresTestContainer.MIGRATOR,
                POSTGRES.migratorPassword());
                var statement = connection.createStatement()) {
            statement.execute("TRUNCATE TABLE public.payment_provider_events, public.payment_outbox, "
                    + "public.payment_idempotency_keys, public.payments, public.credit_audit_records, public.credit_idempotency_keys, "
                    + "public.credit_ledger_entries, public.credit_accounts, public.customer_idempotency_keys, public.customers, "
                    + "public.sync_event_rejections, public.sync_outbox, "
                    + "public.sync_changes, public.sync_event_claims, public.device_installations, "
                    + "public.business_memberships, public.businesses, public.users");
        }
    }

    @AfterEach
    void clearTrace() {
        authorizationEvents.clear();
    }

    @Test
    void testM3_031_unauthenticatedCreateReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/businesses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trade_name\":\"Unauthenticated\",\"vertical\":\"OTHER\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testM3_032_authenticatedCreateAssignsOwnerToAuthenticatedUserAndRejectsOwnerUserId()
            throws Exception {
        var principal = principal("m3-http-owner");

        mockMvc.perform(post("/api/v1/businesses")
                        .with(authentication(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trade_name\":\"Owner Business\",\"vertical\":\"RETAIL\","
                                + "\"owner_user_id\":\"00000000-0000-7000-8000-000000000001\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/businesses")
                        .with(authentication(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trade_name\":\"Owner Business\",\"vertical\":\"RETAIL\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("OWNER"));

        assertOwnerForSubject("m3-http-owner");
    }

    @Test
    void testM3_033_listReturnsOnlyAuthenticatedUsersBusinesses() throws Exception {
        var userA = principal("m3-http-user-a");
        var userB = principal("m3-http-user-b");

        mockMvc.perform(post("/api/v1/businesses")
                        .with(authentication(userA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trade_name\":\"Business A\",\"vertical\":\"STORE\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/businesses")
                        .with(authentication(userB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trade_name\":\"Business B\",\"vertical\":\"STORE\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/businesses").with(authentication(userA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].trade_name").value("Business A"))
                .andExpect(jsonPath("$[0].role").value("OWNER"));
    }

    @Test
    void testM3_026_crossBusinessRequestedIdIsDeniedWithoutDisclosure() throws Exception {
        createBusiness(principal("m3-http-foreign"), "Foreign Business");
        var foreignId = businessIdForSubject("m3-http-foreign");

        mockMvc.perform(get("/m3-test/businesses/{businessId}", foreignId)
                        .with(authentication(principal("m3-http-other"))))
                .andExpect(status().isForbidden())
                .andExpect(content().string(""));
        org.assertj.core.api.Assertions.assertThat(authorizationEvents)
                .containsExactly("membership");
    }

    @Test
    void testM3_027_clientBusinessIdIsOnlyARequestedTarget() throws Exception {
        createBusiness(principal("m3-http-target-owner"), "Target Business");
        var target = businessIdForSubject("m3-http-target-owner");

        mockMvc.perform(get("/m3-test/businesses/{businessId}", target)
                        .with(authentication(principal("m3-http-requester"))))
                .andExpect(status().isForbidden());
        org.assertj.core.api.Assertions.assertThat(authorizationEvents)
                .containsExactly("membership");
    }

    @Test
    void testM3_028_storeIdIsRejectedAndCannotCreateBusinessAuthority() throws Exception {
        mockMvc.perform(post("/api/v1/businesses")
                        .with(authentication(principal("m3-http-store-id")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trade_name\":\"Invalid store target\",\"vertical\":\"OTHER\","
                                + "\"store_id\":\"store-from-client\"}"))
                .andExpect(status().isBadRequest());
        org.assertj.core.api.Assertions.assertThat(count("businesses")).isZero();
    }

    @Test
    void disabledMembershipIsDeniedBeforeTenantContext() throws Exception {
        createBusiness(principal("m3-http-disabled-membership"), "Disabled Membership");
        var businessId = businessIdForSubject("m3-http-disabled-membership");
        setStatus("business_memberships", membershipIdForSubject("m3-http-disabled-membership"), "DISABLED");

        mockMvc.perform(get("/m3-test/businesses/{businessId}", businessId)
                        .with(authentication(principal("m3-http-disabled-membership"))))
                .andExpect(status().isForbidden());
        org.assertj.core.api.Assertions.assertThat(authorizationEvents)
                .containsExactly("membership");
    }

    @Test
    void disabledBusinessIsDeniedBeforeTenantContext() throws Exception {
        createBusiness(principal("m3-http-disabled-business"), "Disabled Business");
        var businessId = businessIdForSubject("m3-http-disabled-business");
        setStatus("businesses", businessId, "DISABLED");

        mockMvc.perform(get("/m3-test/businesses/{businessId}", businessId)
                        .with(authentication(principal("m3-http-disabled-business"))))
                .andExpect(status().isForbidden());
        org.assertj.core.api.Assertions.assertThat(authorizationEvents)
                .containsExactly("membership", "business");
    }

    @Test
    void authorizationCompletesBeforeTenantContext() throws Exception {
        createBusiness(principal("m3-http-order"), "Order Business");
        var businessId = businessIdForSubject("m3-http-order");

        mockMvc.perform(get("/m3-test/businesses/{businessId}", businessId)
                        .with(authentication(principal("m3-http-order"))))
                .andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(authorizationEvents)
                .containsExactly("membership", "business", "tenant-context", "operation");
    }

    private static Authentication principal(String subject) {
        return new AuthenticatedPrincipalAuthenticationToken(
                new AuthenticatedPrincipal(new ExternalSubject(subject)), List.of());
    }

    private void createBusiness(Authentication principal, String tradeName) throws Exception {
        mockMvc.perform(post("/api/v1/businesses")
                        .with(authentication(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trade_name\":\"" + tradeName + "\",\"vertical\":\"OTHER\"}"))
                .andExpect(status().isCreated());
    }

    private static UUID businessIdForSubject(String subject) throws Exception {
        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), M2PostgresTestContainer.MIGRATOR,
                POSTGRES.migratorPassword());
                var statement = connection.prepareStatement(
                        "SELECT b.id FROM public.businesses b "
                                + "JOIN public.business_memberships bm ON bm.business_id = b.id "
                                + "JOIN public.users u ON u.id = bm.user_id "
                                + "WHERE u.external_subject = ?")) {
            statement.setString(1, subject);
            try (var result = statement.executeQuery()) {
                org.assertj.core.api.Assertions.assertThat(result.next()).isTrue();
                return result.getObject(1, UUID.class);
            }
        }
    }

    private static UUID membershipIdForSubject(String subject) throws Exception {
        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), M2PostgresTestContainer.MIGRATOR,
                POSTGRES.migratorPassword());
                var statement = connection.prepareStatement(
                        "SELECT bm.id FROM public.business_memberships bm "
                                + "JOIN public.users u ON u.id = bm.user_id "
                                + "WHERE u.external_subject = ?")) {
            statement.setString(1, subject);
            try (var result = statement.executeQuery()) {
                org.assertj.core.api.Assertions.assertThat(result.next()).isTrue();
                return result.getObject(1, UUID.class);
            }
        }
    }

    private static void setStatus(String table, UUID id, String status) throws Exception {
        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), M2PostgresTestContainer.MIGRATOR,
                POSTGRES.migratorPassword());
                var statement = connection.prepareStatement(
                        "UPDATE public." + table + " SET status = ? WHERE id = ?")) {
            statement.setString(1, status);
            statement.setObject(2, id);
            statement.executeUpdate();
        }
    }

    private static int count(String table) throws Exception {
        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), M2PostgresTestContainer.MIGRATOR,
                POSTGRES.migratorPassword());
                var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT count(*) FROM public." + table)) {
            result.next();
            return result.getInt(1);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeConfiguration {
        @Bean
        List<String> authorizationEvents() {
            return new ArrayList<>();
        }

        @Bean
        @org.springframework.context.annotation.Primary
        BusinessMembershipRepository tracedMembership(
                JooqBusinessMembershipRepository delegate, List<String> events) {
            return new TracedMembershipRepository(delegate, events);
        }

        @Bean
        @org.springframework.context.annotation.Primary
        BusinessRepository tracedBusiness(JooqBusinessRepository delegate, List<String> events) {
            return new TracedBusinessRepository(delegate, events);
        }

        @Bean
        @org.springframework.context.annotation.Primary
        TenantContextExecutor tracedTenant(List<String> events) {
            return new TenantContextExecutor() {
                @Override
                public <T> T execute(BusinessId businessId, Supplier<T> operation) {
                    events.add("tenant-context");
                    return operation.get();
                }
            };
        }

        @Bean
        BusinessAuthorizationProbe businessAuthorizationProbe(
                ResolveAuthenticatedUser identityUsers,
                ExecuteAuthorizedBusinessOperation authorizedOperation,
                List<String> events) {
            return new BusinessAuthorizationProbe(identityUsers, authorizedOperation, events);
        }
    }

    @RestController
    static final class BusinessAuthorizationProbe {
        private final ResolveAuthenticatedUser identityUsers;
        private final ExecuteAuthorizedBusinessOperation authorizedOperation;
        private final List<String> events;

        private BusinessAuthorizationProbe(
                ResolveAuthenticatedUser identityUsers,
                ExecuteAuthorizedBusinessOperation authorizedOperation,
                List<String> events) {
            this.identityUsers = identityUsers;
            this.authorizedOperation = authorizedOperation;
            this.events = events;
        }

        @GetMapping("/m3-test/businesses/{businessId}")
        ResponseEntity<Void> resolve(Authentication authentication, @PathVariable UUID businessId) {
            if (!(authentication.getPrincipal() instanceof AuthenticatedPrincipal principal)) {
                return ResponseEntity.status(403).build();
            }
            try {
                var user = identityUsers.execute(principal);
                authorizedOperation.execute(
                        new UserId(user.id().value()), new BusinessId(businessId), ignored -> {
                            events.add("operation");
                            return null;
                        });
                return ResponseEntity.ok().build();
            } catch (BusinessAccessDeniedException | com.tino.backend.identity.application.exception.DisabledUserException exception) {
                return ResponseEntity.status(403).build();
            }
        }
    }

    private static final class TracedMembershipRepository implements BusinessMembershipRepository {
        private final BusinessMembershipRepository delegate;
        private final List<String> events;

        private TracedMembershipRepository(BusinessMembershipRepository delegate, List<String> events) {
            this.delegate = delegate;
            this.events = events;
        }

        @Override
        public void insert(BusinessMembership membership) {
            delegate.insert(membership);
        }

        @Override
        public Optional<BusinessMembership> findByUserAndBusiness(UserId userId, BusinessId businessId) {
            events.add("membership");
            return delegate.findByUserAndBusiness(userId, businessId);
        }

        @Override
        public List<BusinessMembership> findActiveByUser(UserId userId) {
            return delegate.findActiveByUser(userId);
        }
    }

    private static final class TracedBusinessRepository implements BusinessRepository {
        private final BusinessRepository delegate;
        private final List<String> events;

        private TracedBusinessRepository(BusinessRepository delegate, List<String> events) {
            this.delegate = delegate;
            this.events = events;
        }

        @Override
        public void createWithOwner(com.tino.backend.business.domain.model.Business business,
                BusinessMembership owner) {
            delegate.createWithOwner(business, owner);
        }

        @Override
        public Optional<com.tino.backend.business.domain.model.Business> findById(BusinessId businessId) {
            events.add("business");
            return delegate.findById(businessId);
        }

        @Override
        public List<com.tino.backend.business.domain.model.Business> findByIds(
                Collection<BusinessId> businessIds) {
            return delegate.findByIds(businessIds);
        }
    }

    private static void assertOwnerForSubject(String subject) throws Exception {
        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), M2PostgresTestContainer.MIGRATOR,
                POSTGRES.migratorPassword());
                var userStatement = connection.prepareStatement(
                        "SELECT id FROM public.users WHERE external_subject = ?");
                var membershipStatement = connection.prepareStatement(
                        "SELECT bm.role FROM public.business_memberships bm "
                                + "JOIN public.users u ON u.id = bm.user_id "
                                + "WHERE u.external_subject = ?")) {
            userStatement.setString(1, subject);
            try (var users = userStatement.executeQuery()) {
                org.assertj.core.api.Assertions.assertThat(users.next()).isTrue();
            }
            membershipStatement.setString(1, subject);
            try (var memberships = membershipStatement.executeQuery()) {
                org.assertj.core.api.Assertions.assertThat(memberships.next()).isTrue();
                org.assertj.core.api.Assertions.assertThat(memberships.getString(1)).isEqualTo("OWNER");
            }
        }
    }
}
