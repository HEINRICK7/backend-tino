package com.tino.backend.fiscal.application.usecase;

import com.tino.backend.fiscal.application.model.NfeRetrievalResult;
import com.tino.backend.fiscal.application.port.out.NfeRetrievalPort;
import com.tino.backend.fiscal.domain.model.NfeAccessKey;
import java.util.Objects;

/** F1 application boundary; it has no persistence or inventory side effect. */
public final class RetrieveNfe {
    private final NfeRetrievalPort retrieval;

    public RetrieveNfe(NfeRetrievalPort retrieval) {
        this.retrieval = Objects.requireNonNull(retrieval, "retrieval port");
    }

    public NfeRetrievalResult execute(String rawAccessKey) {
        return retrieval.retrieve(new NfeAccessKey(rawAccessKey));
    }
}
