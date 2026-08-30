package com.tino.backend.fiscal;

import com.tino.backend.fiscal.adapter.out.serpro.SerproNfeAdapter;
import com.tino.backend.fiscal.adapter.out.serpro.SerproNfeParser;
import com.tino.backend.fiscal.adapter.out.serpro.SerproOAuthClient;
import com.tino.backend.fiscal.adapter.out.serpro.NfeMetrics;
import com.tino.backend.fiscal.adapter.out.serpro.TrialFixtureNfeAdapter;
import com.tino.backend.fiscal.application.port.out.NfeRetrievalPort;
import com.tino.backend.fiscal.application.port.out.NfeDocumentRepository;
import com.tino.backend.fiscal.application.port.out.NfeParser;
import com.tino.backend.fiscal.application.port.in.NfeReader;
import com.tino.backend.fiscal.application.usecase.RetrieveAndPersistNfe;
import com.tino.backend.fiscal.application.usecase.GetNfeDocument;
import com.tino.backend.fiscal.application.usecase.ReprocessNfe;
import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.business.application.port.in.BusinessAccess;
import com.tino.backend.shared.kernel.TenantContextExecutor;
import com.tino.backend.shared.kernel.UuidGenerator;
import com.tino.backend.fiscal.application.usecase.RetrieveNfe;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;

@Configuration(proxyBeanMethods = false)
public class FiscalConfiguration {
    @Bean
    HttpClient serproHttpClient() { return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(); }

    @Bean
    SerproOAuthClient serproOAuthClient(HttpClient client, ObjectMapper mapper, Clock clock,
            @Value("${tino.fiscal.serpro.token-url:https://gateway.apiserpro.serpro.gov.br/token}") String tokenUrl,
            @Value("${tino.fiscal.serpro.consumer-key:}") String key,
            @Value("${tino.fiscal.serpro.consumer-secret:}") String secret,
            @Value("${tino.fiscal.serpro.timeout:PT10S}") Duration timeout) {
        return new SerproOAuthClient(client, mapper, URI.create(tokenUrl), key, secret, timeout, clock);
    }

    @Bean
    NfeParser serproNfeParser(ObjectMapper mapper) { return new SerproNfeParser(mapper); }

    @Bean
    NfeMetrics nfeMetrics(MeterRegistry registry) { return new NfeMetrics(registry); }

    @Bean
    NfeRetrievalPort nfeRetrievalPort(HttpClient client, SerproOAuthClient oauth, NfeParser parser, NfeMetrics metrics,
            @Value("${tino.fiscal.serpro.base-url:https://gateway.apiserpro.serpro.gov.br/consulta-nfe-df-trial/api/v1}") String baseUrl,
            @Value("${tino.fiscal.serpro.request-tag:tino-nfe}") String requestTag,
            @Value("${tino.fiscal.serpro.timeout:PT10S}") Duration timeout,
            @Value("${tino.fiscal.mode:serpro}") String mode) {
        if ("fixture".equalsIgnoreCase(mode)) return new TrialFixtureNfeAdapter(parser);
        return new SerproNfeAdapter(client, oauth, parser, URI.create(baseUrl), timeout, requestTag,
                SerproNfeAdapter.RetryDelayer.production(), metrics);
    }

    @Bean
    RetrieveNfe retrieveNfe(NfeRetrievalPort retrieval) { return new RetrieveNfe(retrieval); }

    @Bean
    RetrieveAndPersistNfe retrieveAndPersistNfe(BusinessAccess access, TenantContextExecutor tenants,
            NfeRetrievalPort provider, NfeDocumentRepository documents, UuidGenerator ids, Clock clock) {
        return new RetrieveAndPersistNfe(access, tenants, provider, documents, ids, clock);
    }

    @Bean
    NfeReader nfeReader(NfeDocumentRepository documents) { return documents::find; }

    @Bean
    GetNfeDocument getNfeDocument(BusinessAuthorization authorization, NfeDocumentRepository documents) {
        return new GetNfeDocument(authorization, documents);
    }

    @Bean
    ReprocessNfe reprocessNfe(BusinessAuthorization authorization, NfeDocumentRepository documents,
            NfeParser parser, Clock clock) {
        return new ReprocessNfe(authorization, documents, parser, clock);
    }
}
