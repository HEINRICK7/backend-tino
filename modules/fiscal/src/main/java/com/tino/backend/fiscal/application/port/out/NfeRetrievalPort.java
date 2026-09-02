package com.tino.backend.fiscal.application.port.out;

import com.tino.backend.fiscal.application.model.NfeRetrievalResult;
import com.tino.backend.fiscal.domain.model.NfeAccessKey;

public interface NfeRetrievalPort {
    NfeRetrievalResult retrieve(NfeAccessKey accessKey);
}
