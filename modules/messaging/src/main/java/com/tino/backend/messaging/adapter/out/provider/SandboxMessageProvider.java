package com.tino.backend.messaging.adapter.out.provider;
import com.tino.backend.messaging.application.port.out.MessageProvider;
import com.tino.backend.messaging.domain.model.Message;
import org.springframework.stereotype.Component;
@Component
public final class SandboxMessageProvider implements MessageProvider {
    @Override public String name() { return "sandbox"; }
    @Override public Delivery deliver(Message message) {
        return new Delivery("sandbox-delivery-" + message.id(), "sandbox-message-" + message.id());
    }
}
