package com.tino.backend.fiscal.adapter.out.serpro;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Objects;

/** Metrics contain only low-cardinality technical outcomes; no key, document or person data. */
public final class NfeMetrics {
    private final Counter calls; private final Counter billableSuccesses; private final Counter nonBillableErrors;
    private final Timer duration;
    public NfeMetrics(MeterRegistry registry) {
        var meters = Objects.requireNonNull(registry);
        calls = Counter.builder("tino_nfe_serpro_external_calls_total").register(meters);
        billableSuccesses = Counter.builder("tino_nfe_serpro_billable_success_total").register(meters);
        nonBillableErrors = Counter.builder("tino_nfe_serpro_non_billable_error_total").register(meters);
        duration = Timer.builder("tino_nfe_request_duration").register(meters);
    }
    public static NfeMetrics noop() { return new NfeMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()); }
    public void callStarted() { calls.increment(); }
    public void success() { billableSuccesses.increment(); }
    public void providerError() { nonBillableErrors.increment(); }
    public Timer.Sample start() { return Timer.start(); }
    public void stop(Timer.Sample sample) { sample.stop(duration); }
}
