package com.tino.backend.payment;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.business.application.port.in.BusinessPixReader;
import com.tino.backend.credit.application.port.in.CreditBalanceReader;
import com.tino.backend.credit.application.port.in.payment.CreditPaymentAppender;
import com.tino.backend.payment.application.port.in.CustomerPaymentIntentCreator;
import com.tino.backend.payment.application.port.in.CustomerPaymentEvidenceReceiver;
import com.tino.backend.payment.application.port.in.MerchantPaymentIntentConfirmer;
import com.tino.backend.payment.application.port.in.MerchantPaymentEvidenceReader;
import com.tino.backend.payment.application.port.out.DebtPaymentIntentRepository;
import com.tino.backend.payment.application.port.out.PaymentEvidenceRepository;
import com.tino.backend.payment.application.usecase.ConfirmDebtPaymentIntent;
import com.tino.backend.payment.application.port.out.PaymentProvider;
import com.tino.backend.payment.application.port.out.PaymentRepository;
import com.tino.backend.payment.application.usecase.CreatePayment;
import com.tino.backend.payment.application.usecase.CreateDebtPaymentIntent;
import com.tino.backend.payment.application.usecase.GetPayment;
import com.tino.backend.payment.application.usecase.IngestDebtPaymentEvidence;
import com.tino.backend.payment.application.usecase.IngestPaymentWebhook;
import com.tino.backend.payment.application.usecase.ProcessPayment;
import com.tino.backend.payment.application.usecase.ReadPaymentEvidenceQueue;
import com.tino.backend.shared.kernel.UuidGenerator;
import com.tino.backend.shared.kernel.TenantContextExecutor;
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
    CustomerPaymentIntentCreator createDebtPaymentIntent(CreditBalanceReader credits,
            DebtPaymentIntentRepository intents,
            BusinessPixReader pix, TenantContextExecutor tenants, UuidGenerator ids, Clock clock) {
        return new CreateDebtPaymentIntent(credits, intents, pix, tenants, ids, clock);
    }

    @Bean
    CustomerPaymentEvidenceReceiver ingestDebtPaymentEvidence(BusinessAuthorization authorization,
            DebtPaymentIntentRepository intents, PaymentEvidenceRepository evidence,
            UuidGenerator ids, Clock clock) {
        return new IngestDebtPaymentEvidence(authorization, intents, evidence, ids, clock);
    }

    @Bean
    MerchantPaymentIntentConfirmer confirmDebtPaymentIntent(BusinessAuthorization authorization,
            DebtPaymentIntentRepository intents, PaymentEvidenceRepository evidence,
            CreditPaymentAppender appendCreditEntry, Clock clock) {
        return new ConfirmDebtPaymentIntent(authorization, intents, evidence, appendCreditEntry, clock);
    }

    @Bean
    MerchantPaymentEvidenceReader readPaymentEvidenceQueue(BusinessAuthorization authorization,
            PaymentEvidenceRepository evidence, DebtPaymentIntentRepository intents) {
        return new ReadPaymentEvidenceQueue(authorization, evidence, intents);
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
