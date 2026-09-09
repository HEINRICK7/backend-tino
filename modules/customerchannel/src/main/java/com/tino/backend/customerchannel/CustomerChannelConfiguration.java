package com.tino.backend.customerchannel;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.business.application.port.in.BusinessPixReader;
import com.tino.backend.customer.application.port.out.CustomerRepository;
import com.tino.backend.customerchannel.adapter.out.provider.WhatsAppCustomerInviteDeliveryAdapter;
import com.tino.backend.customerchannel.application.port.out.CustomerChannelRepository;
import com.tino.backend.customerchannel.application.port.out.CustomerInviteDeliveryPort;
import com.tino.backend.customerchannel.application.service.CustomerChannelService;
import com.tino.backend.customerchannel.application.service.CustomerPushSettings;
import com.tino.backend.credit.application.port.out.customer.CustomerUpdateNotificationPort;
import com.tino.backend.messaging.application.port.out.WhatsAppDeliveryPort;
import com.tino.backend.shared.kernel.UuidGenerator;
import com.tino.backend.shared.kernel.TenantContextExecutor;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CustomerChannelConfiguration {
    @Bean
    CustomerPushSettings customerPushSettings(
            @Value("${tino.customer-channel.push.enabled:false}") boolean enabled,
            @Value("${tino.customer-channel.push.vapid-public-key:}") String publicKey,
            @Value("${tino.customer-channel.push.vapid-private-key:}") String privateKey,
            @Value("${tino.customer-channel.push.vapid-subject:mailto:suporte@tino.otimizanegocio.com}") String subject,
            @Value("${tino.customer-channel.push.dispatch-interval-ms:5000}") long intervalMs,
            @Value("${tino.customer-channel.push.dispatch-initial-delay-ms:10000}") long initialDelayMs,
            @Value("${tino.customer-channel.push.dispatch-timeout-ms:5000}") long timeoutMs) {
        return new CustomerPushSettings(enabled, publicKey, privateKey, subject,
                Duration.ofMillis(intervalMs), Duration.ofMillis(initialDelayMs), Duration.ofMillis(timeoutMs));
    }

    @Bean
    CustomerInviteDeliveryPort customerInviteDeliveryPort(WhatsAppDeliveryPort delivery) {
        return new WhatsAppCustomerInviteDeliveryAdapter(delivery);
    }

    @Bean
    CustomerChannelService customerChannelService(BusinessAuthorization authorization,
            CustomerRepository customers, CustomerChannelRepository channels,
            CustomerInviteDeliveryPort inviteDelivery, UuidGenerator ids, Clock clock,
            TenantContextExecutor tenants,
            @Value("${tino.customer-channel.public-base-url:http://localhost:5173}") String publicBaseUrl,
            CustomerPushSettings pushSettings,
            BusinessPixReader pixReader) {
        return new CustomerChannelService(authorization, customers, channels, inviteDelivery, ids, clock,
                tenants, publicBaseUrl, pushSettings, pixReader);
    }

    @Bean
    CustomerUpdateNotificationPort customerUpdateNotificationPort(
            CustomerChannelRepository channels, UuidGenerator ids, CustomerPushSettings pushSettings) {
        return update -> {
            if (!pushSettings.enabled()) return;
            channels.enqueuePushNotification(ids.next(), update.businessId(), update.customerId(),
                    update.activityId(), update.kind(), update.occurredAt());
        };
    }
}
