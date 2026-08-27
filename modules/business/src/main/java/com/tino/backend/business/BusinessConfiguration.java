package com.tino.backend.business;

import com.tino.backend.business.application.port.out.BusinessMembershipRepository;
import com.tino.backend.business.application.port.out.BusinessRepository;
import com.tino.backend.business.application.usecase.CreateBusiness;
import com.tino.backend.business.application.usecase.ExecuteAuthorizedBusinessOperation;
import com.tino.backend.business.application.usecase.ListUserBusinesses;
import com.tino.backend.business.application.usecase.ResolveBusinessAccess;
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
    ExecuteAuthorizedBusinessOperation executeAuthorizedBusinessOperation(
            ResolveBusinessAccess access, TenantContextExecutor tenantContext) {
        return new ExecuteAuthorizedBusinessOperation(access, tenantContext);
    }
}
