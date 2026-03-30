package com.stablebridge.txrecovery.infrastructure.metrics;

import static org.assertj.core.api.Assertions.assertThat;

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
            // given
            var chain = "ethereum";
            var strategy = "SEQUENTIAL";

            // when
            transactionMetrics.recordSubmitted(chain, strategy);

            // then
            var count = registry.get("str.transactions.submitted.total")
                    .tags("chain", "ethereum", "strategy", "SEQUENTIAL")
                    .counter()
                    .count();
            assertThat(count).isEqualTo(1.0);
        }

        @Test
        void shouldRecordConfirmedTransaction() {
            // given
            var chain = "ethereum";

            // when
            transactionMetrics.recordConfirmed(chain);

            // then
            var count = registry.get("str.transactions.confirmed.total")
                    .tags("chain", "ethereum")
                    .counter()
                    .count();
            assertThat(count).isEqualTo(1.0);
        }

        @Test
        void shouldRecordStuckTransaction() {
            // given
            var chain = "ethereum";

            // when
            transactionMetrics.recordStuck(chain);

            // then
            var count = registry.get("str.transactions.stuck.total")
                    .tags("chain", "ethereum")
                    .counter()
                    .count();
            assertThat(count).isEqualTo(1.0);
        }

        @Test
        void shouldRecordRecoveredTransaction() {
            // given
            var chain = "ethereum";
            var action = "SPEEDUP";

            // when
            transactionMetrics.recordRecovered(chain, action);

            // then
            var count = registry.get("str.transactions.recovered.total")
                    .tags("chain", "ethereum", "action", "SPEEDUP")
                    .counter()
                    .count();
            assertThat(count).isEqualTo(1.0);
        }

        @Test
        void shouldRecordFailedTransaction() {
            // given
            var chain = "ethereum";
            var reason = "INSUFFICIENT_GAS";

            // when
            transactionMetrics.recordFailed(chain, reason);

            // then
            var count = registry.get("str.transactions.failed.total")
                    .tags("chain", "ethereum", "reason", "INSUFFICIENT_GAS")
                    .counter()
                    .count();
            assertThat(count).isEqualTo(1.0);
        }

        @Test
        void shouldRecordCancelledTransaction() {
            // given
            var chain = "ethereum";

            // when
            transactionMetrics.recordCancelled(chain);

            // then
            var count = registry.get("str.transactions.cancelled.total")
                    .tags("chain", "ethereum")
                    .counter()
                    .count();
            assertThat(count).isEqualTo(1.0);
        }

        @Test
        void shouldIncrementSubmittedCounterMultipleTimes() {
            // given
            var chain = "ethereum";
            var strategy = "SEQUENTIAL";

            // when
            transactionMetrics.recordSubmitted(chain, strategy);
            transactionMetrics.recordSubmitted(chain, strategy);

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
            var chain = "ethereum";
            var duration = Duration.ofSeconds(30);

            // when
            transactionMetrics.recordConfirmationDuration(chain, duration);

            // then
            var timer = registry.get("str.transaction.confirmation.duration.seconds")
                    .tags("chain", "ethereum")
                    .timer();
            assertThat(timer.count()).isEqualTo(1);
            assertThat(timer.totalTime(TimeUnit.SECONDS)).isGreaterThan(0);
        }

        @Test
        void shouldRecordStuckDuration() {
            // given
            var chain = "ethereum";
            var duration = Duration.ofMinutes(5);

            // when
            transactionMetrics.recordStuckDuration(chain, duration);

            // then
            var timer = registry.get("str.transaction.stuck.duration.seconds")
                    .tags("chain", "ethereum")
                    .timer();
            assertThat(timer.count()).isEqualTo(1);
            assertThat(timer.totalTime(TimeUnit.SECONDS)).isGreaterThan(0);
        }
    }
}
