package com.tino.backend.customer;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.customer.application.port.out.CustomerRepository;
import com.tino.backend.customer.application.usecase.CreateCustomer;
import com.tino.backend.customer.application.usecase.GetCustomer;
import com.tino.backend.customer.application.usecase.ListCustomers;
import com.tino.backend.customer.application.usecase.UpdateCustomer;
import com.tino.backend.shared.kernel.UuidGenerator;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CustomerConfiguration {
    @Bean
    CreateCustomer createCustomer(BusinessAuthorization authorization, CustomerRepository customers,
            UuidGenerator ids, Clock clock) {
        return new CreateCustomer(authorization, customers, ids, clock);
    }

    @Bean
    ListCustomers listCustomers(BusinessAuthorization authorization, CustomerRepository customers) {
        return new ListCustomers(authorization, customers);
    }

    @Bean
    GetCustomer getCustomer(BusinessAuthorization authorization, CustomerRepository customers) {
        return new GetCustomer(authorization, customers);
    }

    @Bean
    UpdateCustomer updateCustomer(BusinessAuthorization authorization, CustomerRepository customers,
            Clock clock) {
        return new UpdateCustomer(authorization, customers, clock);
    }
}
