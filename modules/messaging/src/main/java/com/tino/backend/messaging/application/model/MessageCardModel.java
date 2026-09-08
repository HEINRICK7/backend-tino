package com.tino.backend.messaging.application.model;

import com.tino.backend.messaging.domain.model.DebtStatementView;
import com.tino.backend.messaging.domain.model.WhatsAppMessageType;

public record MessageCardModel(WhatsAppMessageType messageType, DebtStatementView statement) {
    public MessageCardModel {
        if (messageType == null || statement == null) {
            throw new IllegalArgumentException("message card model is incomplete");
        }
    }
}
