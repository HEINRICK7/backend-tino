package com.tino.backend.customerchannel.application.port.in;

import java.util.UUID;

/** Identity resolved from the opaque customer-session cookie, never from browser input. */
public record CustomerChannelPrincipal(
        UUID sessionId,
        UUID channelId,
        UUID businessId,
        UUID customerId) implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
}
