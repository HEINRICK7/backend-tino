package com.tino.backend.fiscal.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.fiscal.adapter.out.serpro.SerproNfeParser;
import com.tino.backend.fiscal.application.model.NfeDocumentSnapshot;
import com.tino.backend.fiscal.application.port.out.NfeDocumentRepository;
import com.tino.backend.fiscal.domain.model.NfeAccessKey;
import com.tino.backend.fiscal.domain.model.RawNfePayload;
import com.tino.backend.fiscal.domain.model.RetrievalStatus;
import com.tino.backend.shared.kernel.BusinessId;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ReprocessNfeTest {
    private static final UUID USER = UUID.fromString("00000000-0000-7000-8000-000000000001");
    private static final BusinessId BUSINESS = new BusinessId(UUID.fromString("00000000-0000-7000-8000-000000000002"));
    private static final UUID DOCUMENT = UUID.fromString("00000000-0000-7000-8000-000000000003");
    private static final String KEY = "53160911510448000171550010000106771000187760";

    @Test
    void reprocessesPersistedRawWithoutProviderAndPersistsNewCanonicalVersion() throws Exception {
        var rawJson = new String(getClass().getResourceAsStream("/serpro/consulta-nfe-trial-official-sanitized.json").readAllBytes());
        var raw = new RawNfePayload(rawJson, "serpro", "consulta-nfe");
        var parser = new SerproNfeParser(new ObjectMapper());
        var canonical = parser.parse(rawJson, new NfeAccessKey(KEY));
        var current = new NfeDocumentSnapshot(DOCUMENT, new NfeAccessKey(KEY), RetrievalStatus.SUCCESS,
                canonical.fiscalStatus(), canonical, raw, null, 1L, Instant.EPOCH);
        var repository = mock(NfeDocumentRepository.class);
        var authorization = authorize();
        when(repository.find(BUSINESS, DOCUMENT)).thenReturn(Optional.of(current));
        when(repository.findIdempotency(BUSINESS, "reprocess-1")).thenReturn(Optional.empty());
        when(repository.claimIdempotency(BUSINESS, "reprocess-1", KEY, DOCUMENT, Instant.EPOCH)).thenReturn(true);
        when(repository.save(eq(BUSINESS), eq(DOCUMENT), eq(new NfeAccessKey(KEY)), any(), eq(Instant.EPOCH)))
                .thenReturn(current);

        var result = new ReprocessNfe(authorization, repository, parser, Clock.fixed(Instant.EPOCH, java.time.ZoneOffset.UTC))
                .execute(USER, BUSINESS, DOCUMENT, "reprocess-1");

        assertThat(result.document()).isEqualTo(canonical);
        verify(repository).claimIdempotency(BUSINESS, "reprocess-1", KEY, DOCUMENT, Instant.EPOCH);
        verify(repository).save(eq(BUSINESS), eq(DOCUMENT), eq(new NfeAccessKey(KEY)), any(), eq(Instant.EPOCH));
    }

    @Test
    void repeatedIdempotencyKeyDoesNotCreateAnotherVersion() {
        var repository = mock(NfeDocumentRepository.class);
        var current = mock(NfeDocumentSnapshot.class);
        when(repository.find(BUSINESS, DOCUMENT)).thenReturn(Optional.of(current));
        when(repository.findIdempotency(BUSINESS, "reprocess-1"))
                .thenReturn(Optional.of(new NfeDocumentRepository.RetrievalIdempotency(KEY, DOCUMENT)));

        var result = new ReprocessNfe(authorize(), repository, mock(com.tino.backend.fiscal.application.port.out.NfeParser.class), Clock.systemUTC())
                .execute(USER, BUSINESS, DOCUMENT, "reprocess-1");

        assertThat(result).isSameAs(current);
    }

    private static BusinessAuthorization authorize() {
        var authorization = mock(BusinessAuthorization.class);
        when(authorization.execute(eq(USER), eq(BUSINESS), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            var operation = (Function<BusinessId, NfeDocumentSnapshot>) invocation.getArgument(2);
            return operation.apply(BUSINESS);
        });
        return authorization;
    }
}
