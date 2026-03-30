package com.stablebridge.txrecovery.infrastructure.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class TransactionMetricsTest {

    private MeterRegistry registry;
    private TransactionMetrics transactionMetrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        transactionMetrics = new TransactionMetrics(registry);
    }

    @Nested
    class Counters {

        @Test
        void shouldRecordSubmittedTransaction() {
            // when
            transactionMetrics.recordSubmitted("ethereum", TransactionMetrics.SubmissionStrategy.SEQUENTIAL);

            // then
            var count = registry.get("str.transactions.submitted.total")
                    .tags("chain", "ethereum", "strategy", "SEQUENTIAL")
                    .counter()
                    .count();
            assertThat(count).isEqualTo(1.0);
        }

        @Test
        void shouldRecordConfirmedTransaction() {
            // when
            transactionMetrics.recordConfirmed("ethereum");

            // then
            var count = registry.get("str.transactions.confirmed.total")
                    .tags("chain", "ethereum")
                    .counter()
                    .count();
            assertThat(count).isEqualTo(1.0);
        }

        @Test
        void shouldRecordStuckTransaction() {
            // when
            transactionMetrics.recordStuck("ethereum");

            // then
            var count = registry.get("str.transactions.stuck.total")
                    .tags("chain", "ethereum")
                    .counter()
                    .count();
            assertThat(count).isEqualTo(1.0);
        }

        @Test
        void shouldRecordRecoveredTransaction() {
            // when
            transactionMetrics.recordRecovered("ethereum", TransactionMetrics.RecoveryAction.SPEEDUP);

            // then
            var count = registry.get("str.transactions.recovered.total")
                    .tags("chain", "ethereum", "action", "SPEEDUP")
                    .counter()
                    .count();
            assertThat(count).isEqualTo(1.0);
        }

        @Test
        void shouldRecordFailedTransaction() {
            // when
            transactionMetrics.recordFailed("ethereum", TransactionMetrics.FailureReason.INSUFFICIENT_GAS);

            // then
            var count = registry.get("str.transactions.failed.total")
                    .tags("chain", "ethereum", "reason", "INSUFFICIENT_GAS")
                    .counter()
                    .count();
            assertThat(count).isEqualTo(1.0);
        }

        @Test
        void shouldRecordCancelledTransaction() {
            // when
            transactionMetrics.recordCancelled("ethereum");

            // then
            var count = registry.get("str.transactions.cancelled.total")
                    .tags("chain", "ethereum")
                    .counter()
                    .count();
            assertThat(count).isEqualTo(1.0);
        }

        @Test
        void shouldIncrementSubmittedCounterMultipleTimes() {
            // when
            transactionMetrics.recordSubmitted("ethereum", TransactionMetrics.SubmissionStrategy.SEQUENTIAL);
            transactionMetrics.recordSubmitted("ethereum", TransactionMetrics.SubmissionStrategy.SEQUENTIAL);

            // then
            var count = registry.get("str.transactions.submitted.total")
                    .tags("chain", "ethereum", "strategy", "SEQUENTIAL")
                    .counter()
                    .count();
            assertThat(count).isEqualTo(2.0);
        }
    }

    @Nested
    class Timers {

        @Test
        void shouldRecordConfirmationDuration() {
            // given
            var duration = Duration.ofSeconds(30);

            // when
            transactionMetrics.recordConfirmationDuration("ethereum", duration);

            // then
            var timer = registry.get("str.transaction.confirmation.duration.seconds")
                    .tags("chain", "ethereum")
                    .timer();
            assertThat(timer.count()).isEqualTo(1);
            assertThat(timer.totalTime(TimeUnit.SECONDS)).isEqualTo(30.0);
        }

        @Test
        void shouldRecordStuckDuration() {
            // given
            var duration = Duration.ofMinutes(5);

            // when
            transactionMetrics.recordStuckDuration("ethereum", duration);

            // then
            var timer = registry.get("str.transaction.stuck.duration.seconds")
                    .tags("chain", "ethereum")
                    .timer();
            assertThat(timer.count()).isEqualTo(1);
            assertThat(timer.totalTime(TimeUnit.SECONDS)).isEqualTo(300.0);
        }
    }

    @Nested
    class TagSanitization {

        @Test
        void shouldSanitizeNullChainToUnknown() {
            // when
            transactionMetrics.recordConfirmed(null);

            // then
            var count = registry.get("str.transactions.confirmed.total")
                    .tags("chain", "unknown")
                    .counter()
                    .count();
            assertThat(count).isEqualTo(1.0);
        }

        @Test
        void shouldSanitizeBlankChainToUnknown() {
            // when
            transactionMetrics.recordConfirmed("  ");

            // then
            var count = registry.get("str.transactions.confirmed.total")
                    .tags("chain", "unknown")
                    .counter()
                    .count();
            assertThat(count).isEqualTo(1.0);
        }

        @Test
        void shouldRejectNullStrategy() {
            assertThatThrownBy(() -> transactionMetrics.recordSubmitted("ethereum", null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void shouldRejectNullDuration() {
            assertThatThrownBy(() -> transactionMetrics.recordConfirmationDuration("ethereum", null))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
