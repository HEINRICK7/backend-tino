package com.tino.backend.payment;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.payment.application.port.out.PaymentProvider;
import com.tino.backend.payment.application.port.out.PaymentRepository;
import com.tino.backend.payment.application.usecase.CreatePayment;
import com.tino.backend.payment.application.usecase.GetPayment;
import com.tino.backend.payment.application.usecase.IngestPaymentWebhook;
import com.tino.backend.payment.application.usecase.ProcessPayment;
import com.tino.backend.shared.kernel.UuidGenerator;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class PaymentConfiguration {
    @Bean
    CreatePayment createPayment(BusinessAuthorization authorization, PaymentRepository payments,
            UuidGenerator ids, Clock clock) {
        return new CreatePayment(authorization, payments, ids, clock);
    }

    @Bean
    GetPayment getPayment(BusinessAuthorization authorization, PaymentRepository payments) {
        return new GetPayment(authorization, payments);
    }

    @Bean
    ProcessPayment processPayment(BusinessAuthorization authorization, PaymentRepository payments,
            PaymentProvider provider, Clock clock) {
        return new ProcessPayment(authorization, payments, provider, clock);
    }

    @Bean
    IngestPaymentWebhook ingestPaymentWebhook(PaymentRepository payments, PaymentProvider provider,
            Clock clock) {
        return new IngestPaymentWebhook(payments, provider, clock);
    }
}
