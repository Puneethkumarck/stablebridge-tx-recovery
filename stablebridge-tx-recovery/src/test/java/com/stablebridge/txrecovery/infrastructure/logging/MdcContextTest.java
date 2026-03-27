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
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import com.stablebridge.txrecovery.domain.recovery.model.EscalationTier;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionSnapshot;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionStatus;

class MdcContextTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void shouldPopulateMdcFieldsFromTransactionSnapshot() {
        // given
        var snapshot = TransactionSnapshot.builder()
                .transactionId("tx-001")
                .intentId("intent-001")
                .status(TransactionStatus.SUBMITTED)
                .txHash("0xabc123")
                .retryCount(3)
                .gasSpent(new BigDecimal("0.005"))
                .currentTier(EscalationTier.builder()
                        .level(2)
                        .stuckThreshold(Duration.ofMinutes(10))
                        .gasMultiplier(new BigDecimal("1.5"))
                        .build())
                .updatedAt(Instant.now())
                .build();

        // when
        MdcContext.set(snapshot);

        // then
        assertThat(MDC.getCopyOfContextMap())
                .containsEntry(TRANSACTION_ID, "tx-001")
                .containsEntry(INTENT_ID, "intent-001")
                .containsEntry(STATUS, "SUBMITTED")
                .containsEntry(TX_HASH, "0xabc123")
                .containsEntry(RETRY_COUNT, "3")
                .containsEntry(GAS_SPENT, "0.005")
                .containsEntry(ESCALATION_TIER, "2");
    }

    @Test
    void shouldHandleNullOptionalFieldsInSnapshot() {
        // given
        var snapshot = TransactionSnapshot.builder()
                .transactionId("tx-002")
                .intentId("intent-002")
                .status(TransactionStatus.PENDING)
                .retryCount(0)
                .build();

        // when
        MdcContext.set(snapshot);

        // then
        assertThat(MDC.getCopyOfContextMap())
                .containsEntry(TRANSACTION_ID, "tx-002")
                .containsEntry(INTENT_ID, "intent-002")
                .containsEntry(STATUS, "PENDING")
                .containsEntry(RETRY_COUNT, "0")
                .doesNotContainKey(TX_HASH)
                .doesNotContainKey(GAS_SPENT)
                .doesNotContainKey(ESCALATION_TIER);
    }

    @Test
    void shouldClearAllMdcFields() {
        // given
        var snapshot = TransactionSnapshot.builder()
                .transactionId("tx-003")
                .intentId("intent-003")
                .status(TransactionStatus.RECOVERING)
                .txHash("0xdef456")
                .retryCount(1)
                .gasSpent(new BigDecimal("0.01"))
                .currentTier(EscalationTier.builder()
                        .level(1)
                        .stuckThreshold(Duration.ofMinutes(5))
                        .gasMultiplier(new BigDecimal("1.2"))
                        .build())
                .updatedAt(Instant.now())
                .build();
        MdcContext.set(snapshot);
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
