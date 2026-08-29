package com.tino.backend.credit;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.credit.application.port.out.CreditRepository;
import com.tino.backend.credit.application.usecase.AppendCreditEntry;
import com.tino.backend.credit.application.usecase.CompensateCreditEntry;
import com.tino.backend.credit.application.usecase.GetCreditBalance;
import com.tino.backend.shared.kernel.UuidGenerator;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CreditConfiguration {
    @Bean
    AppendCreditEntry appendCreditEntry(BusinessAuthorization authorization, CreditRepository credits,
            UuidGenerator ids, Clock clock) {
        return new AppendCreditEntry(authorization, credits, ids, clock);
    }

    @Bean
    CompensateCreditEntry compensateCreditEntry(BusinessAuthorization authorization, CreditRepository credits,
            UuidGenerator ids, Clock clock) {
        return new CompensateCreditEntry(authorization, credits, ids, clock);
    }

    @Bean
    GetCreditBalance getCreditBalance(BusinessAuthorization authorization, CreditRepository credits) {
        return new GetCreditBalance(authorization, credits);
    }
}
