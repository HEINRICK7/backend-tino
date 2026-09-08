package com.tino.backend.messaging;
import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.business.application.port.in.BusinessContextReader;
import com.tino.backend.messaging.adapter.out.provider.SandboxMessageProvider;
import com.tino.backend.messaging.adapter.out.provider.SandboxWhatsAppDeliveryAdapter;
import com.tino.backend.messaging.adapter.out.provider.WaEvolutionWhatsAppDeliveryAdapter;
import com.tino.backend.messaging.application.port.out.MessageProvider;
import com.tino.backend.messaging.application.port.out.MessagingRepository;
import com.tino.backend.messaging.application.port.out.WhatsAppCardRenderer;
import com.tino.backend.messaging.application.port.out.WhatsAppDeliveryPort;
import com.tino.backend.messaging.application.port.out.WhatsAppMessageRepository;
import com.tino.backend.messaging.application.port.out.WhatsAppMetrics;
import com.tino.backend.messaging.application.port.out.WhatsAppStatementDataSource;
import com.tino.backend.messaging.application.service.RenderWhatsAppMessage;
import com.tino.backend.messaging.application.service.ResolveWhatsAppBusiness;
import com.tino.backend.messaging.application.service.WhatsAppTextComposer;
import com.tino.backend.messaging.application.usecase.CreateWhatsAppPreview;
import com.tino.backend.messaging.application.usecase.GetWhatsAppMessageStatus;
import com.tino.backend.messaging.application.usecase.GetWhatsAppPreviewMedia;
import com.tino.backend.messaging.application.usecase.ProcessWhatsAppMessage;
import com.tino.backend.messaging.application.usecase.SendWhatsAppMessage;
import com.tino.backend.messaging.application.usecase.GetMessage;
import com.tino.backend.messaging.application.usecase.ProcessMessage;
import com.tino.backend.messaging.application.usecase.QueueMessage;
import com.tino.backend.messaging.application.usecase.SetConsent;
import com.tino.backend.shared.kernel.UuidGenerator;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;

@Configuration(proxyBeanMethods = false)
public class MessagingConfiguration {
    @Bean SetConsent setConsent(BusinessAuthorization a, MessagingRepository r, UuidGenerator i, Clock c){return new SetConsent(a,r,i,c);}
    @Bean QueueMessage queueMessage(BusinessAuthorization a, MessagingRepository r, UuidGenerator i, Clock c){return new QueueMessage(a,r,i,c);}
    @Bean GetMessage getMessage(BusinessAuthorization a, MessagingRepository r){return new GetMessage(a,r);}
    @Bean ProcessMessage processMessage(BusinessAuthorization a, MessagingRepository r, MessageProvider p, Clock c){return new ProcessMessage(a,r,p,c);}

    @Bean
    MessageProvider sandboxMessageProvider() {
        return new SandboxMessageProvider();
    }

    @Bean
    WhatsAppMetrics whatsAppMetrics(MeterRegistry registry) {
        return new WhatsAppMetrics(registry);
    }

    @Bean
    WhatsAppTextComposer whatsAppTextComposer() {
        return new WhatsAppTextComposer();
    }

    @Bean
    RenderWhatsAppMessage renderWhatsAppMessage(WhatsAppTextComposer text, WhatsAppCardRenderer cards,
            WhatsAppMetrics metrics) {
        return new RenderWhatsAppMessage(text, cards, metrics);
    }

    @Bean
    ResolveWhatsAppBusiness resolveWhatsAppBusiness(BusinessContextReader businesses, BusinessAuthorization authorization,
            WhatsAppStatementDataSource statements, WhatsAppMessageRepository messages) {
        return new ResolveWhatsAppBusiness(businesses, authorization, statements, messages);
    }

    @Bean
    CreateWhatsAppPreview createWhatsAppPreview(BusinessAuthorization authorization, WhatsAppStatementDataSource statements,
            WhatsAppMessageRepository messages, RenderWhatsAppMessage renderer, UuidGenerator ids, Clock clock) {
        return new CreateWhatsAppPreview(authorization, statements, messages, renderer, ids, clock);
    }

    @Bean
    SendWhatsAppMessage sendWhatsAppMessage(BusinessAuthorization authorization, WhatsAppStatementDataSource statements,
            WhatsAppMessageRepository messages, UuidGenerator ids, Clock clock) {
        return new SendWhatsAppMessage(authorization, statements, messages, ids, clock);
    }

    @Bean
    GetWhatsAppMessageStatus getWhatsAppMessageStatus(BusinessAuthorization authorization, WhatsAppMessageRepository messages) {
        return new GetWhatsAppMessageStatus(authorization, messages);
    }

    @Bean
    GetWhatsAppPreviewMedia getWhatsAppPreviewMedia(BusinessAuthorization authorization, WhatsAppMessageRepository messages,
            Clock clock) {
        return new GetWhatsAppPreviewMedia(authorization, messages, clock);
    }

    @Bean
    ProcessWhatsAppMessage processWhatsAppMessage(BusinessAuthorization authorization, WhatsAppMessageRepository messages,
            WhatsAppStatementDataSource statements, WhatsAppDeliveryPort delivery, UuidGenerator ids, Clock clock,
            WhatsAppMetrics metrics) {
        return new ProcessWhatsAppMessage(authorization, messages, statements, delivery, ids, clock, metrics);
    }

    @Bean(name = "whatsappHttpClient")
    HttpClient whatsappHttpClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }

    @Bean
    WhatsAppDeliveryPort whatsAppDeliveryPort(
            @Value("${tino.whatsapp.delivery.enabled:false}") boolean enabled,
            @Value("${tino.whatsapp.delivery.provider:WA_EVOLUTION}") String provider,
            @Value("${tino.whatsapp.delivery.base-url:http://otp-delivery:8080}") String baseUrl,
            @Value("${tino.whatsapp.delivery.path:/internal/v1/messages/whatsapp}") String path,
            @Value("${tino.whatsapp.delivery.internal-token:}") String token,
            @Value("${tino.whatsapp.delivery.timeout:PT10S}") Duration timeout,
            @Qualifier("whatsappHttpClient") HttpClient http,
            ObjectMapper mapper) {
        if (!enabled) {
            return new SandboxWhatsAppDeliveryAdapter();
        }
        if (!"WA_EVOLUTION".equalsIgnoreCase(provider)) {
            throw new IllegalStateException("unsupported WhatsApp delivery provider");
        }
        if (token.isBlank()) {
            throw new IllegalStateException("TINO_WHATSAPP_DELIVERY_INTERNAL_TOKEN is required when WhatsApp delivery is enabled");
        }
        var normalizedPath = path.startsWith("/") ? path : "/" + path;
        var endpoint = URI.create(baseUrl.endsWith("/")
                ? baseUrl + normalizedPath.substring(1) : baseUrl + normalizedPath);
        return new WaEvolutionWhatsAppDeliveryAdapter(endpoint, token, timeout, http, mapper);
    }
}
