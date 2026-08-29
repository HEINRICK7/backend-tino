package com.tino.backend.messaging.application.port.out;
import com.tino.backend.messaging.domain.model.Message;
public interface MessageProvider {
    String name();
    Delivery deliver(Message message);
    record Delivery(String eventId, String providerMessageId) {}
}
