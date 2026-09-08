package com.tino.backend.messaging.application.port.out;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Objects;

/** Low-cardinality WhatsApp metrics; payloads, phone numbers and money never become tags or log fields. */
public final class WhatsAppMetrics {
    private final Counter renders;
    private final Counter renderFailures;
    private final Counter sends;
    private final Counter sendFailures;
    private final Counter providerErrors;
    private final Timer sendDuration;

    public WhatsAppMetrics(MeterRegistry registry) {
        var meters = Objects.requireNonNull(registry, "registry");
        renders = Counter.builder("whatsapp_render_total").register(meters);
        renderFailures = Counter.builder("whatsapp_render_failed_total").register(meters);
        sends = Counter.builder("whatsapp_send_total").register(meters);
        sendFailures = Counter.builder("whatsapp_send_failed_total").register(meters);
        providerErrors = Counter.builder("whatsapp_provider_error_total").register(meters);
        sendDuration = Timer.builder("whatsapp_send_duration").register(meters);
    }

    public static WhatsAppMetrics noop() {
        return new WhatsAppMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
    }

    public void renderStarted() { renders.increment(); }
    public void renderFailed() { renderFailures.increment(); }
    public void sendStarted() { sends.increment(); }
    public void sendFailed() { sendFailures.increment(); }
    public void providerError() { providerErrors.increment(); }
    public Timer.Sample startSend() { return Timer.start(); }
    public void stopSend(Timer.Sample sample) { sample.stop(sendDuration); }
}
