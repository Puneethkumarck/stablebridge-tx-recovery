package com.stablebridge.txrecovery.infrastructure.metrics;

import java.time.Duration;
import java.util.Objects;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TransactionMetrics {

    private static final String SUBMITTED_TOTAL_METRIC = "str.transactions.submitted.total";
    private static final String CONFIRMED_TOTAL_METRIC = "str.transactions.confirmed.total";
    private static final String STUCK_TOTAL_METRIC = "str.transactions.stuck.total";
    private static final String RECOVERED_TOTAL_METRIC = "str.transactions.recovered.total";
    private static final String FAILED_TOTAL_METRIC = "str.transactions.failed.total";
    private static final String CANCELLED_TOTAL_METRIC = "str.transactions.cancelled.total";
    private static final String CONFIRMATION_DURATION_METRIC =
            "str.transaction.confirmation.duration.seconds";
    private static final String STUCK_DURATION_METRIC =
            "str.transaction.stuck.duration.seconds";

    private final MeterRegistry registry;

    public void recordSubmitted(String chain, SubmissionStrategy strategy) {
        Objects.requireNonNull(strategy, "strategy must not be null");
        Counter.builder(SUBMITTED_TOTAL_METRIC)
                .tag("chain", sanitizeTag(chain))
                .tag("strategy", strategy.getTagValue())
                .register(registry)
                .increment();
    }

    public void recordConfirmed(String chain) {
        Counter.builder(CONFIRMED_TOTAL_METRIC)
                .tag("chain", sanitizeTag(chain))
                .register(registry)
                .increment();
    }

    public void recordStuck(String chain) {
        Counter.builder(STUCK_TOTAL_METRIC)
                .tag("chain", sanitizeTag(chain))
                .register(registry)
                .increment();
    }

    public void recordRecovered(String chain, RecoveryAction action) {
        Objects.requireNonNull(action, "action must not be null");
        Counter.builder(RECOVERED_TOTAL_METRIC)
                .tag("chain", sanitizeTag(chain))
                .tag("action", action.getTagValue())
                .register(registry)
                .increment();
    }

    public void recordFailed(String chain, FailureReason reason) {
        Objects.requireNonNull(reason, "reason must not be null");
        Counter.builder(FAILED_TOTAL_METRIC)
                .tag("chain", sanitizeTag(chain))
                .tag("reason", reason.getTagValue())
                .register(registry)
                .increment();
    }

    public void recordCancelled(String chain) {
        Counter.builder(CANCELLED_TOTAL_METRIC)
                .tag("chain", sanitizeTag(chain))
                .register(registry)
                .increment();
    }

    public void recordConfirmationDuration(String chain, Duration duration) {
        Objects.requireNonNull(duration, "duration must not be null");
        Timer.builder(CONFIRMATION_DURATION_METRIC)
                .tag("chain", sanitizeTag(chain))
                .publishPercentileHistogram()
                .register(registry)
                .record(duration);
    }

    public void recordStuckDuration(String chain, Duration duration) {
        Objects.requireNonNull(duration, "duration must not be null");
        Timer.builder(STUCK_DURATION_METRIC)
                .tag("chain", sanitizeTag(chain))
                .publishPercentileHistogram()
                .register(registry)
                .record(duration);
    }

    private static String sanitizeTag(String value) {
        return (value == null || value.isBlank()) ? "unknown" : value;
    }

    @Getter
    @RequiredArgsConstructor
    public enum SubmissionStrategy {
        SEQUENTIAL("SEQUENTIAL"),
        PARALLEL("PARALLEL"),
        BATCH("BATCH");

        private final String tagValue;
    }

    @Getter
    @RequiredArgsConstructor
    public enum RecoveryAction {
        SPEEDUP("SPEEDUP"),
        CANCEL("CANCEL"),
        RESUBMIT("RESUBMIT"),
        REPLACE("REPLACE");

        private final String tagValue;
    }

    @Getter
    @RequiredArgsConstructor
    public enum FailureReason {
        INSUFFICIENT_GAS("INSUFFICIENT_GAS"),
        NONCE_TOO_LOW("NONCE_TOO_LOW"),
        REVERTED("REVERTED"),
        TIMEOUT("TIMEOUT"),
        DROPPED("DROPPED"),
        UNKNOWN("UNKNOWN");

        private final String tagValue;
    }
}
