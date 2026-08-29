package com.tino.backend.messaging.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.tino.backend.messaging.domain.model.MessageStatus;
import org.junit.jupiter.api.Test;

class MessageDomainTest {
    @Test
    void deliveryStateMachineAllowsRetryButNoResurrection() {
        assertThat(MessageStatus.QUEUED.canTransitionTo(MessageStatus.PROCESSING)).isTrue();
        assertThat(MessageStatus.PROCESSING.canTransitionTo(MessageStatus.SENT)).isTrue();
        assertThat(MessageStatus.PROCESSING.canTransitionTo(MessageStatus.FAILED)).isTrue();
        assertThat(MessageStatus.FAILED.canTransitionTo(MessageStatus.PROCESSING)).isTrue();
        assertThat(MessageStatus.DEAD_LETTER.canTransitionTo(MessageStatus.SENT)).isFalse();
    }
}
