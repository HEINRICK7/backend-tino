package com.tino.backend.business;

import com.tino.backend.business.application.exception.BusinessAccessDeniedException;
import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.business.application.port.in.BusinessAccess;
import com.tino.backend.business.application.port.in.BusinessAuthorizationDeniedException;
import com.tino.backend.business.application.port.in.BusinessContextReader;
import com.tino.backend.business.application.port.in.BusinessDataSourceConfiguration;
import com.tino.backend.business.application.port.in.BusinessContextUnavailableException;
import com.tino.backend.business.application.port.in.AccessibleBusinessView;
import com.tino.backend.business.application.port.out.BusinessMembershipRepository;
import com.tino.backend.business.application.port.out.BusinessPersistenceException;
import com.tino.backend.business.application.port.out.BusinessRepository;
import com.tino.backend.business.application.usecase.CreateBusiness;
import com.tino.backend.business.application.usecase.ExecuteAuthorizedBusinessOperation;
import com.tino.backend.business.application.usecase.ListUserBusinesses;
import com.tino.backend.business.application.usecase.ResolveBusinessAccess;
import com.tino.backend.business.domain.model.UserId;
import com.tino.backend.shared.kernel.TenantContextExecutor;
import com.tino.backend.shared.kernel.UuidGenerator;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Composition of M3 application use cases. */
@Configuration(proxyBeanMethods = false)
public class BusinessConfiguration {
    @Bean
    CreateBusiness createBusiness(BusinessRepository businesses, UuidGenerator ids, Clock clock) {
        return new CreateBusiness(businesses, ids, clock);
    }

    @Bean
    ListUserBusinesses listUserBusinesses(
            BusinessMembershipRepository memberships, BusinessRepository businesses) {
        return new ListUserBusinesses(memberships, businesses);
    }

    @Bean
    ResolveBusinessAccess resolveBusinessAccess(
            BusinessMembershipRepository memberships, BusinessRepository businesses) {
        return new ResolveBusinessAccess(memberships, businesses);
    }

    @Bean
    BusinessContextReader businessContextReader(ListUserBusinesses listUserBusinesses) {
        return userId -> {
            try {
                return listUserBusinesses.execute(new UserId(userId)).stream()
                        .map(BusinessConfiguration::toPublicView)
                        .toList();
            } catch (BusinessPersistenceException exception) {
                throw new BusinessContextUnavailableException(exception);
            }
        };
    }

    @Bean
    BusinessDataSourceConfiguration businessDataSourceConfiguration(BusinessRepository businesses) {
        return new BusinessDataSourceConfiguration() {
            @Override
            public String readSourceType(com.tino.backend.shared.kernel.BusinessId businessId) {
                return businesses.findById(businessId).orElseThrow(() -> new IllegalArgumentException("business not found"))
                        .dataSourceType().name();
            }

            @Override
            public void updateSourceType(com.tino.backend.shared.kernel.BusinessId businessId, String sourceType) {
                try {
                    businesses.updateDataSource(businessId,
                            com.tino.backend.business.domain.model.BusinessDataSourceType.valueOf(sourceType));
                } catch (RuntimeException exception) {
                    throw new IllegalArgumentException("unsupported business data source type", exception);
                }
            }
        };
    }

    @Bean
    ExecuteAuthorizedBusinessOperation executeAuthorizedBusinessOperation(
            ResolveBusinessAccess access, TenantContextExecutor tenantContext) {
        return new ExecuteAuthorizedBusinessOperation(access, tenantContext);
    }

    @Bean
    BusinessAuthorization businessAuthorization(ExecuteAuthorizedBusinessOperation authorized) {
        return new BusinessAuthorization() {
            @Override
            public <T> T execute(
                    java.util.UUID authenticatedUserId,
                    com.tino.backend.shared.kernel.BusinessId requestedBusinessId,
                    java.util.function.Function<com.tino.backend.shared.kernel.BusinessId, T> operation) {
                try {
                    return authorized.execute(
                            new UserId(authenticatedUserId), requestedBusinessId,
                            context -> operation.apply(context.businessId()));
                } catch (BusinessAccessDeniedException exception) {
                    throw new BusinessAuthorizationDeniedException();
                }
            }
        };
    }

    @Bean
    BusinessAccess businessAccess(ResolveBusinessAccess access) {
        return (userId, businessId) -> access.execute(new UserId(userId), businessId).businessId();
    }

    private static AccessibleBusinessView toPublicView(
            com.tino.backend.business.application.model.AccessibleBusiness accessible) {
        var business = accessible.business();
        return new AccessibleBusinessView(
                business.id().value(),
                business.tradeName().value(),
                business.vertical().name(),
                business.status().name(),
                accessible.role().name(),
                business.dataSourceType().name());
    }
}
