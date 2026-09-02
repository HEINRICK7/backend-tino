package com.tino.backend.fiscal.adapter.out.serpro;

import com.tino.backend.fiscal.application.model.NfeRetrievalResult;
import com.tino.backend.fiscal.application.port.out.NfeParser;
import com.tino.backend.fiscal.application.port.out.NfeRetrievalPort;
import com.tino.backend.fiscal.domain.model.NfeAccessKey;
import com.tino.backend.fiscal.domain.model.RawNfePayload;
import com.tino.backend.fiscal.domain.model.RetrievalStatus;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Deterministic local-only provider for the official sanitized Trial fixture. */
public final class TrialFixtureNfeAdapter implements NfeRetrievalPort {
    public static final String TRIAL_KEY = "53160911510448000171550010000106771000187760";
    private static final String FIXTURE = "/serpro/consulta-nfe-trial-official-sanitized.json";

    private final NfeParser parser;

    public TrialFixtureNfeAdapter(NfeParser parser) {
        this.parser = Objects.requireNonNull(parser);
    }

    @Override
    public NfeRetrievalResult retrieve(NfeAccessKey accessKey) {
        if (!TRIAL_KEY.equals(accessKey.value())) {
            return NfeRetrievalResult.failure(RetrievalStatus.NOT_FOUND, "TRIAL_FIXTURE_NOT_FOUND", null);
        }
        var raw = readFixture();
        try {
            return NfeRetrievalResult.success(raw, parser.parse(raw.json(), accessKey));
        } catch (RuntimeException exception) {
            return NfeRetrievalResult.failure(RetrievalStatus.FAILED, "INVALID_TRIAL_FIXTURE", raw);
        }
    }

    private static RawNfePayload readFixture() {
        try (InputStream stream = TrialFixtureNfeAdapter.class.getResourceAsStream(FIXTURE)) {
            if (stream == null) throw new IllegalStateException("Trial fixture is missing");
            return new RawNfePayload(new String(stream.readAllBytes(), StandardCharsets.UTF_8), "trial-fixture", "official-sanitized-v1");
        } catch (IOException exception) {
            throw new IllegalStateException("Trial fixture could not be read", exception);
        }
    }
}
