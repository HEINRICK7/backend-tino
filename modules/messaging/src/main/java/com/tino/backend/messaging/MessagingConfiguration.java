package com.tino.backend.messaging;
import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.messaging.application.port.out.*;
import com.tino.backend.messaging.application.usecase.*;
import com.tino.backend.shared.kernel.UuidGenerator;
import java.time.Clock;
import org.springframework.context.annotation.*;
@Configuration(proxyBeanMethods = false)
public class MessagingConfiguration {
    @Bean SetConsent setConsent(BusinessAuthorization a, MessagingRepository r, UuidGenerator i, Clock c){return new SetConsent(a,r,i,c);}
    @Bean QueueMessage queueMessage(BusinessAuthorization a, MessagingRepository r, UuidGenerator i, Clock c){return new QueueMessage(a,r,i,c);}
    @Bean GetMessage getMessage(BusinessAuthorization a, MessagingRepository r){return new GetMessage(a,r);}
    @Bean ProcessMessage processMessage(BusinessAuthorization a, MessagingRepository r, MessageProvider p, Clock c){return new ProcessMessage(a,r,p,c);}
}
