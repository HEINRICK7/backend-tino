package com.tino.backend.fiscal.application.port.in;

import com.tino.backend.fiscal.application.model.NfeDocumentSnapshot;
import com.tino.backend.shared.kernel.BusinessId;
import java.util.Optional;
import java.util.UUID;

public interface NfeReader {
    Optional<NfeDocumentSnapshot> find(BusinessId businessId, UUID documentId);
}
