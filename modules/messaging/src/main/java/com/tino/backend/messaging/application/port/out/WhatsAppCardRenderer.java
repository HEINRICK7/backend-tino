package com.tino.backend.messaging.application.port.out;

import com.tino.backend.messaging.application.model.MessageCardModel;
import com.tino.backend.messaging.application.model.RenderedMedia;

public interface WhatsAppCardRenderer {
    RenderedMedia render(MessageCardModel model);
}
