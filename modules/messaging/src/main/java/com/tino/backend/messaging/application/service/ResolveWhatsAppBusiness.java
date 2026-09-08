package com.tino.backend.messaging.application.service;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.business.application.port.in.BusinessContextReader;
import com.tino.backend.messaging.application.exception.WhatsAppMessageNotFoundException;
import com.tino.backend.messaging.application.exception.WhatsAppPreviewNotFoundException;
import com.tino.backend.messaging.application.port.out.WhatsAppMessageRepository;
import com.tino.backend.messaging.application.port.out.WhatsAppStatementDataSource;
import com.tino.backend.shared.kernel.BusinessId;
import java.util.UUID;

public final class ResolveWhatsAppBusiness {
    private final BusinessContextReader businesses;
    private final BusinessAuthorization authorization;
    private final WhatsAppStatementDataSource statements;
    private final WhatsAppMessageRepository messages;

    public ResolveWhatsAppBusiness(BusinessContextReader businesses, BusinessAuthorization authorization,
            WhatsAppStatementDataSource statements, WhatsAppMessageRepository messages) {
        this.businesses = businesses;
        this.authorization = authorization;
        this.statements = statements;
        this.messages = messages;
    }

    public BusinessId forAccount(UUID userId, UUID accountId) {
        for (var business : businesses.listAccessibleBusinesses(userId)) {
            var found = authorization.execute(userId, new BusinessId(business.businessId()),
                    tenant -> statements.find(tenant, accountId).isPresent());
            if (found) {
                return new BusinessId(business.businessId());
            }
        }
        throw new WhatsAppMessageNotFoundException();
    }

    public BusinessId forPreview(UUID userId, UUID previewId) {
        for (var business : businesses.listAccessibleBusinesses(userId)) {
            var found = authorization.execute(userId, new BusinessId(business.businessId()),
                    tenant -> messages.findPreview(tenant, previewId).isPresent());
            if (found) {
                return new BusinessId(business.businessId());
            }
        }
        throw new WhatsAppPreviewNotFoundException();
    }

    public BusinessId forMessage(UUID userId, UUID messageId) {
        for (var business : businesses.listAccessibleBusinesses(userId)) {
            var found = authorization.execute(userId, new BusinessId(business.businessId()),
                    tenant -> messages.findDelivery(tenant, messageId).isPresent());
            if (found) {
                return new BusinessId(business.businessId());
            }
        }
        throw new com.tino.backend.messaging.application.exception.WhatsAppMessageNotFoundException();
    }
}
