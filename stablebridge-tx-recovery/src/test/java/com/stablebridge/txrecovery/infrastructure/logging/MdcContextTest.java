package com.stablebridge.txrecovery.infrastructure.logging;

import static com.stablebridge.txrecovery.infrastructure.logging.MdcContext.AMOUNT;
import static com.stablebridge.txrecovery.infrastructure.logging.MdcContext.CHAIN;
import static com.stablebridge.txrecovery.infrastructure.logging.MdcContext.ESCALATION_TIER;
import static com.stablebridge.txrecovery.infrastructure.logging.MdcContext.FROM_ADDRESS;
import static com.stablebridge.txrecovery.infrastructure.logging.MdcContext.GAS_SPENT;
import static com.stablebridge.txrecovery.infrastructure.logging.MdcContext.INTENT_ID;
import static com.stablebridge.txrecovery.infrastructure.logging.MdcContext.LATENCY_MS;
import static com.stablebridge.txrecovery.infrastructure.logging.MdcContext.RETRY_COUNT;
import static com.stablebridge.txrecovery.infrastructure.logging.MdcContext.STATUS;
import static com.stablebridge.txrecovery.infrastructure.logging.MdcContext.TOKEN;
import static com.stablebridge.txrecovery.infrastructure.logging.MdcContext.TO_ADDRESS;
import static com.stablebridge.txrecovery.infrastructure.logging.MdcContext.TRACE_ID;
import static com.stablebridge.txrecovery.infrastructure.logging.MdcContext.TRANSACTION_ID;
import static com.stablebridge.txrecovery.infrastructure.logging.MdcContext.TX_HASH;
import static com.stablebridge.txrecovery.testutil.fixtures.TransactionSnapshotFixtures.SOME_MINIMAL_SNAPSHOT;
import static com.stablebridge.txrecovery.testutil.fixtures.TransactionSnapshotFixtures.SOME_SNAPSHOT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class MdcContextTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Nested
    class SetFromSnapshot {

        @Test
        void shouldPopulateAllMdcFieldsFromFullSnapshot() {
            // when
            MdcContext.set(SOME_SNAPSHOT);

            // then
            assertThat(MDC.getCopyOfContextMap())
                    .hasSize(7)
                    .containsOnly(
                            entry(TRANSACTION_ID, "tx-001"),
                            entry(INTENT_ID, "intent-001"),
                            entry(STATUS, "SUBMITTED"),
                            entry(TX_HASH, "0xabc123"),
                            entry(RETRY_COUNT, "3"),
                            entry(GAS_SPENT, "0.005"),
                            entry(ESCALATION_TIER, "2"));
        }

        @Test
        void shouldSkipNullOptionalFieldsInSnapshot() {
            // when
            MdcContext.set(SOME_MINIMAL_SNAPSHOT);

            // then
            assertThat(MDC.getCopyOfContextMap())
                    .hasSize(4)
                    .containsOnly(
                            entry(TRANSACTION_ID, "tx-001"),
                            entry(INTENT_ID, "intent-001"),
                            entry(STATUS, "PENDING"),
                            entry(RETRY_COUNT, "0"));
        }
    }

    @Nested
    class Clear {

        @Test
        void shouldClearAllMdcFields() {
            // given
            MdcContext.set(SOME_SNAPSHOT);
            MdcContext.putChain("ethereum");
            MdcContext.putFromAddress("0xSender");
            MdcContext.putToAddress("0xReceiver");
            MdcContext.putAmount("100.50");
            MdcContext.putToken("USDC");
            MdcContext.putTraceId("trace-abc");
            MdcContext.putLatencyMs(250L);

            // when
            MdcContext.clear();

            // then
            assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
        }
    }

    @Nested
    class IndividualFieldSetters {

        @Test
        void shouldPopulateChainField() {
            // when
            MdcContext.putChain("ethereum");

            // then
            assertThat(MDC.get(CHAIN)).isEqualTo("ethereum");
        }

        @Test
        void shouldPopulateFromAddressField() {
            // when
            MdcContext.putFromAddress("0x742d35Cc6634C0532925a3b844Bc9e7595f2bD18");

            // then
            assertThat(MDC.get(FROM_ADDRESS)).isEqualTo("0x742d35Cc6634C0532925a3b844Bc9e7595f2bD18");
        }

        @Test
        void shouldPopulateToAddressField() {
            // when
            MdcContext.putToAddress("0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48");

            // then
            assertThat(MDC.get(TO_ADDRESS)).isEqualTo("0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48");
        }

        @Test
        void shouldPopulateAmountField() {
            // when
            MdcContext.putAmount("1000.50");

            // then
            assertThat(MDC.get(AMOUNT)).isEqualTo("1000.50");
        }

        @Test
        void shouldPopulateTokenField() {
            // when
            MdcContext.putToken("USDC");

            // then
            assertThat(MDC.get(TOKEN)).isEqualTo("USDC");
        }

        @Test
        void shouldPopulateTraceIdField() {
            // when
            MdcContext.putTraceId("trace-123-abc");

            // then
            assertThat(MDC.get(TRACE_ID)).isEqualTo("trace-123-abc");
        }

        @Test
        void shouldPopulateLatencyMsField() {
            // when
            MdcContext.putLatencyMs(150L);

            // then
            assertThat(MDC.get(LATENCY_MS)).isEqualTo("150");
        }
    }

    @Nested
    class NullHandling {

        @Test
        void shouldNotSetMdcFieldWhenNullChainProvided() {
            // when
            MdcContext.putChain(null);

            // then
            assertThat(MDC.get(CHAIN)).isNull();
        }

        @Test
        void shouldNotSetMdcFieldWhenNullFromAddressProvided() {
            // when
            MdcContext.putFromAddress(null);

            // then
            assertThat(MDC.get(FROM_ADDRESS)).isNull();
        }

        @Test
        void shouldNotSetMdcFieldWhenNullTraceIdProvided() {
            // when
            MdcContext.putTraceId(null);

            // then
            assertThat(MDC.get(TRACE_ID)).isNull();
        }
    }
}
