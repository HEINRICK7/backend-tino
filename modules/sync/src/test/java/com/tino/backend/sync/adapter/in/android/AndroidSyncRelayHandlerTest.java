package com.tino.backend.sync.adapter.in.android;

import static org.assertj.core.api.Assertions.assertThat;

import com.tino.backend.sync.application.port.in.SyncEventHandler;
import com.tino.backend.sync.application.usecase.SyncEventHandlerRegistry;
import com.tino.backend.sync.domain.model.SyncEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class AndroidSyncRelayHandlerTest {
    private static final List<String> PROJECTED_CREDIT_EVENT_TYPES = List.of(
            "credit.receivable.created",
            "credit.receivable.reversed",
            "credit.sale.created",
            "credit.payment.received",
            "credit.payment.reversed",
            "credit.adjustment.created",
            "credit.adjustment.reversed",
            "credit.settled",
            "credit.settled.reversed",
            "credit.sale.reversed");

    @Test
    void approvedEventIsRelayedWithTheCanonicalPayload() {
        var handler = new AndroidSyncRelayHandler("product.created");
        var event = event("product.created", "{\"name\":\"Café\"}");

        var effects = handler.handle(event);

        assertThat(effects.changePayloadJson()).isEqualTo(event.payloadJson());
        assertThat(effects.outboxPayloadJson()).isEqualTo(event.payloadJson());
        assertThat(AndroidSyncRelayConfiguration.SUPPORTED_EVENT_TYPES)
                .contains(event.eventType());
    }

    @Test
    void eachApprovedTypeUsesSchemaVersionOne() {
        assertThat(AndroidSyncRelayConfiguration.SUPPORTED_EVENT_TYPES)
                .hasSize(29)
                .doesNotHaveDuplicates();
    }

    @Test
    void everyProjectedCreditTypeHasAnAndroidRelayAtSchemaVersionOne() {
        try (var context = new AnnotationConfigApplicationContext(AndroidSyncRelayConfiguration.class)) {
            var handlers = List.copyOf(context.getBeansOfType(SyncEventHandler.class).values());
            var registry = new SyncEventHandlerRegistry(handlers);

            assertThat(handlers)
                    .hasSize(AndroidSyncRelayConfiguration.SUPPORTED_EVENT_TYPES.size())
                    .extracting(SyncEventHandler::eventType)
                    .containsExactlyInAnyOrderElementsOf(
                            AndroidSyncRelayConfiguration.SUPPORTED_EVENT_TYPES);
            for (var eventType : PROJECTED_CREDIT_EVENT_TYPES) {
                assertThat(registry.find(eventType, 1)).as(eventType).isNotNull();
            }
        }
    }

    private static SyncEvent event(String type, String payload) {
        return new SyncEvent(
                UUID.fromString("00000000-0000-7000-8000-000000000001"),
                "store-1",
                "device-1",
                "aggregate-1",
                type,
                1,
                Instant.parse("2026-09-06T12:00:00Z"),
                payload);
    }
}
