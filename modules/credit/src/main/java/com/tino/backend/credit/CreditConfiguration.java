package com.tino.backend.credit;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.credit.application.port.in.CreditBalanceReader;
import com.tino.backend.credit.application.port.in.payment.CreditPaymentAppender;
import com.tino.backend.credit.application.port.out.CreditRepository;
import com.tino.backend.credit.application.port.out.customer.CustomerUpdateNotificationPort;
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
            UuidGenerator ids, Clock clock, CustomerUpdateNotificationPort customerUpdates) {
        return new AppendCreditEntry(authorization, credits, ids, clock, customerUpdates);
    }

    @Bean
    CreditPaymentAppender creditPaymentAppender(AppendCreditEntry appendCreditEntry) {
        return (userId, businessId, customerId, direction, amount, reason, idempotencyKey, fingerprint) -> {
            var result = appendCreditEntry.execute(userId, businessId, customerId, direction, amount,
                    reason, idempotencyKey, fingerprint);
            return new CreditPaymentAppender.Result(result.entry().id(), result.replayed());
        };
    }

    @Bean
    CompensateCreditEntry compensateCreditEntry(BusinessAuthorization authorization, CreditRepository credits,
            UuidGenerator ids, Clock clock, CustomerUpdateNotificationPort customerUpdates) {
        return new CompensateCreditEntry(authorization, credits, ids, clock, customerUpdates);
    }

    @Bean
    GetCreditBalance getCreditBalance(BusinessAuthorization authorization, CreditRepository credits) {
        return new GetCreditBalance(authorization, credits);
    }

    @Bean
    CreditBalanceReader creditBalanceReader(CreditRepository credits) {
        return (businessId, customerId) -> credits.findAccount(businessId, customerId)
                .map(account -> new CreditBalanceReader.Balance(account.balance(), account.version()));
    }
}
