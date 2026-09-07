package com.tino.backend.sync.adapter.in.android;

import static org.assertj.core.api.Assertions.assertThat;

import com.tino.backend.sync.domain.model.SyncEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AndroidSyncRelayHandlerTest {
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
                .hasSize(26)
                .doesNotHaveDuplicates();
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
