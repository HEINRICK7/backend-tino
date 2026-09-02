package com.tino.backend.businessunderstanding;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.businessunderstanding.application.usecase.ConfirmItemPurpose;
import com.tino.backend.businessunderstanding.application.usecase.GetBusinessUnderstanding;
import com.tino.backend.businessunderstanding.application.usecase.GetActivityCatalog;
import com.tino.backend.businessunderstanding.application.usecase.ReplaceBusinessActivities;
import com.tino.backend.businessunderstanding.application.usecase.ReplaceOperatingModes;
import com.tino.backend.businessunderstanding.application.usecase.ResolveItemPurpose;
import com.tino.backend.businessunderstanding.application.port.out.BusinessUnderstandingRepository;
import com.tino.backend.businessunderstanding.application.port.in.BusinessUnderstandingReader;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class BusinessUnderstandingConfiguration {
    @Bean
    GetActivityCatalog getActivityCatalog() {
        return new GetActivityCatalog();
    }

    @Bean
    GetBusinessUnderstanding getBusinessUnderstanding(
            BusinessAuthorization authorization, BusinessUnderstandingRepository repository) {
        return new GetBusinessUnderstanding(authorization, repository);
    }

    @Bean
    BusinessUnderstandingReader businessUnderstandingReader(GetBusinessUnderstanding reader) {
        return (userId, businessId) ->
                com.tino.backend.businessunderstanding.application.model.BusinessUnderstandingView
                        .from(reader.execute(userId, businessId));
    }

    @Bean
    ReplaceBusinessActivities replaceBusinessActivities(
            BusinessAuthorization authorization, BusinessUnderstandingRepository repository,
            Clock clock) {
        return new ReplaceBusinessActivities(authorization, repository, clock);
    }

    @Bean
    ReplaceOperatingModes replaceOperatingModes(
            BusinessAuthorization authorization, BusinessUnderstandingRepository repository,
            Clock clock) {
        return new ReplaceOperatingModes(authorization, repository, clock);
    }

    @Bean
    ResolveItemPurpose resolveItemPurpose(
            BusinessAuthorization authorization, BusinessUnderstandingRepository repository) {
        return new ResolveItemPurpose(authorization, repository);
    }

    @Bean
    ConfirmItemPurpose confirmItemPurpose(
            BusinessAuthorization authorization, BusinessUnderstandingRepository repository,
            Clock clock) {
        return new ConfirmItemPurpose(authorization, repository, clock);
    }
}
