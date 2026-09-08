package com.tino.backend.customerchannel;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.customer.application.port.out.CustomerRepository;
import com.tino.backend.customerchannel.adapter.out.provider.WhatsAppCustomerInviteDeliveryAdapter;
import com.tino.backend.customerchannel.application.port.out.CustomerChannelRepository;
import com.tino.backend.customerchannel.application.port.out.CustomerInviteDeliveryPort;
import com.tino.backend.customerchannel.application.service.CustomerChannelService;
import com.tino.backend.messaging.application.port.out.WhatsAppDeliveryPort;
import com.tino.backend.shared.kernel.UuidGenerator;
import com.tino.backend.shared.kernel.TenantContextExecutor;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CustomerChannelConfiguration {
    @Bean
    CustomerInviteDeliveryPort customerInviteDeliveryPort(WhatsAppDeliveryPort delivery) {
        return new WhatsAppCustomerInviteDeliveryAdapter(delivery);
    }

    @Bean
    CustomerChannelService customerChannelService(BusinessAuthorization authorization,
            CustomerRepository customers, CustomerChannelRepository channels,
            CustomerInviteDeliveryPort inviteDelivery, UuidGenerator ids, Clock clock,
            TenantContextExecutor tenants,
            @Value("${tino.customer-channel.public-base-url:http://localhost:5173}") String publicBaseUrl) {
        return new CustomerChannelService(authorization, customers, channels, inviteDelivery, ids, clock, tenants, publicBaseUrl);
    }
}
