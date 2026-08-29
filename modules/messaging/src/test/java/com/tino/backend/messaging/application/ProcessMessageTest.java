package com.tino.backend.messaging.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tino.backend.business.application.port.in.BusinessAuthorization;
import com.tino.backend.messaging.application.port.out.MessageProvider;
import com.tino.backend.messaging.application.port.out.MessagingRepository;
import com.tino.backend.messaging.application.usecase.ProcessMessage;
import com.tino.backend.messaging.domain.model.Message;
import com.tino.backend.messaging.domain.model.MessageChannel;
import com.tino.backend.messaging.domain.model.MessagePurpose;
import com.tino.backend.messaging.domain.model.MessageStatus;
import com.tino.backend.messaging.domain.model.MessageTemplate;
import com.tino.backend.shared.kernel.BusinessId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProcessMessageTest {
    private static final UUID USER = UUID.randomUUID();
    private static final BusinessId BUSINESS = new BusinessId(UUID.randomUUID());
    private static final UUID MESSAGE_ID = UUID.randomUUID();
    private static final UUID OUTBOX_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-08-29T12:00:00Z");

    @Test
    void providerFailureIsRetryableBeforeThirdAttempt() {
        var repository = mock(MessagingRepository.class);
        var message = message(MessageStatus.QUEUED);
        when(repository.find(BUSINESS, MESSAGE_ID)).thenReturn(Optional.of(message));
        when(repository.claimOutbox(eq(BUSINESS), eq(MESSAGE_ID), any())).thenReturn(Optional.of(command(1)));
        var failed = message(MessageStatus.FAILED);
        when(repository.applyFailure(eq(BUSINESS), eq(MESSAGE_ID), eq("sandbox"), eq("failure-" + MESSAGE_ID + "-1"),
                eq("unavailable"), eq(false), any())).thenReturn(failed);
        var result = new ProcessMessage(authorize(), repository, failingProvider(), fixedClock()).execute(USER, BUSINESS, MESSAGE_ID);
        assertThat(result.message().status()).isEqualTo("FAILED");
        verify(repository).failOutbox(eq(BUSINESS), eq(OUTBOX_ID), any(), eq("IllegalStateException"), eq(false));
    }

    @Test
    void thirdProviderFailureDeadLettersMessageAndOutbox() {
        var repository = mock(MessagingRepository.class);
        var message = message(MessageStatus.FAILED);
        when(repository.find(BUSINESS, MESSAGE_ID)).thenReturn(Optional.of(message));
        when(repository.claimOutbox(eq(BUSINESS), eq(MESSAGE_ID), any())).thenReturn(Optional.of(command(3)));
        var dead = message(MessageStatus.DEAD_LETTER);
        when(repository.applyFailure(eq(BUSINESS), eq(MESSAGE_ID), eq("sandbox"), eq("failure-" + MESSAGE_ID + "-3"),
                eq("unavailable"), eq(true), any())).thenReturn(dead);
        var result = new ProcessMessage(authorize(), repository, failingProvider(), fixedClock()).execute(USER, BUSINESS, MESSAGE_ID);
        assertThat(result.message().status()).isEqualTo("DEAD_LETTER");
        verify(repository).failOutbox(eq(BUSINESS), eq(OUTBOX_ID), any(), eq("IllegalStateException"), eq(true));
    }

    private static BusinessAuthorization authorize() {
        return new BusinessAuthorization() {
            @Override public <T> T execute(UUID user, BusinessId business,
                    java.util.function.Function<BusinessId, T> operation) {
                return operation.apply(business);
            }
        };
    }
    private static MessageProvider failingProvider() {
        return new MessageProvider() {
            @Override public String name() { return "sandbox"; }
            @Override public Delivery deliver(Message message) { throw new IllegalStateException("provider down"); }
        };
    }
    private static Message message(MessageStatus status) {
        return new Message(MESSAGE_ID, BUSINESS, UUID.randomUUID(), MessageChannel.WHATSAPP,
                MessagePurpose.TRANSACTIONAL, MessageTemplate.PAYMENT_UPDATE, "a".repeat(64), "key", "b".repeat(64),
                status, "sandbox", null, 0, NOW, NOW);
    }
    private static MessagingRepository.OutboxCommand command(int attempt) {
        return new MessagingRepository.OutboxCommand(OUTBOX_ID, BUSINESS, MESSAGE_ID, "DELIVER_MESSAGE",
                "PROCESSING", attempt, NOW, NOW);
    }
    private static Clock fixedClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
}
