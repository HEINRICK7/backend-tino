package com.tino.backend.fiscal.application.usecase;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.fiscal.application.model.NfeDocumentSnapshot;
import com.tino.backend.fiscal.application.exception.NfeDocumentNotFoundException;
import com.tino.backend.fiscal.application.port.out.NfeDocumentRepository;
import com.tino.backend.shared.kernel.BusinessId;
import java.util.Objects;
import java.util.UUID;

public final class GetNfeDocument {
    private final BusinessAuthorization authorization; private final NfeDocumentRepository documents;
    public GetNfeDocument(BusinessAuthorization authorization, NfeDocumentRepository documents) { this.authorization = Objects.requireNonNull(authorization); this.documents = Objects.requireNonNull(documents); }
    public NfeDocumentSnapshot execute(UUID userId, BusinessId businessId, UUID documentId) { return authorization.execute(userId, businessId, authorized -> documents.find(authorized, documentId).orElseThrow(NfeDocumentNotFoundException::new)); }
}
