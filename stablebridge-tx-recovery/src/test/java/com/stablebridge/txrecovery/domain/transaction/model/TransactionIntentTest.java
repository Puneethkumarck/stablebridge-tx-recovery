package com.stablebridge.txrecovery.domain.transaction.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class TransactionIntentTest {

    @Test
    void shouldModifyChainViaToBuilder() {
        // given
        var intent = TransactionIntent.builder()
                .intentId("intent-001")
                .chain("ethereum")
                .toAddress("0x742d35Cc6634C0532925a3b844Bc9e7595f2bD18")
                .amount(new BigDecimal("100.50"))
                .token("USDC")
                .tokenDecimals(6)
                .rawAmount(BigInteger.valueOf(100500000L))
                .tokenContractAddress("0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48")
                .strategy(SubmissionStrategy.SEQUENTIAL)
                .metadata(Map.of("orderId", "ORD-123"))
                .batchId("batch-001")
                .createdAt(Instant.parse("2026-03-27T10:00:00Z"))
                .build();

        // when
        var modified = intent.toBuilder().chain("polygon").build();

        // then
        var expected = TransactionIntent.builder()
                .intentId("intent-001")
                .chain("polygon")
                .toAddress("0x742d35Cc6634C0532925a3b844Bc9e7595f2bD18")
                .amount(new BigDecimal("100.50"))
                .token("USDC")
                .tokenDecimals(6)
                .rawAmount(BigInteger.valueOf(100500000L))
                .tokenContractAddress("0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48")
                .strategy(SubmissionStrategy.SEQUENTIAL)
                .metadata(Map.of("orderId", "ORD-123"))
                .batchId("batch-001")
                .createdAt(Instant.parse("2026-03-27T10:00:00Z"))
                .build();
        assertThat(modified).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldDefaultOptionalFieldsToNullOrEmptyMap() {
        // when
        var intent = TransactionIntent.builder()
                .intentId("intent-002")
                .chain("base")
                .toAddress("0xabc")
                .amount(new BigDecimal("50"))
                .token("ETH")
                .build();

        // then
        assertThat(intent.tokenDecimals()).isZero();
        assertThat(intent.rawAmount()).isNull();
        assertThat(intent.tokenContractAddress()).isNull();
        assertThat(intent.strategy()).isNull();
        assertThat(intent.metadata()).isEmpty();
        assertThat(intent.batchId()).isNull();
        assertThat(intent.createdAt()).isNull();
    }

    @Test
    void shouldDefensivelyCopyMetadata() {
        // given
        var mutableMap = new HashMap<String, String>();
        mutableMap.put("key", "value");

        // when
        var intent = TransactionIntent.builder()
                .intentId("intent-003")
                .chain("ethereum")
                .toAddress("0xabc")
                .amount(new BigDecimal("10"))
                .token("USDC")
                .metadata(mutableMap)
                .build();

        mutableMap.put("key2", "value2");

        // then
        assertThat(intent.metadata()).hasSize(1).containsEntry("key", "value");
    }

    @Test
    void shouldThrowNullPointerException_whenIntentIdIsNull() {
        // when / then
        assertThatThrownBy(() -> TransactionIntent.builder()
                .intentId(null)
                .chain("ethereum")
                .toAddress("0xabc")
                .amount(new BigDecimal("10"))
                .token("USDC")
                .build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowNullPointerException_whenChainIsNull() {
        // when / then
        assertThatThrownBy(() -> TransactionIntent.builder()
                .intentId("intent-001")
                .chain(null)
                .toAddress("0xabc")
                .amount(new BigDecimal("10"))
                .token("USDC")
                .build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowNullPointerException_whenToAddressIsNull() {
        // when / then
        assertThatThrownBy(() -> TransactionIntent.builder()
                .intentId("intent-001")
                .chain("ethereum")
                .toAddress(null)
                .amount(new BigDecimal("10"))
                .token("USDC")
                .build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowNullPointerException_whenAmountIsNull() {
        // when / then
        assertThatThrownBy(() -> TransactionIntent.builder()
                .intentId("intent-001")
                .chain("ethereum")
                .toAddress("0xabc")
                .amount(null)
                .token("USDC")
                .build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowNullPointerException_whenTokenIsNull() {
        // when / then
        assertThatThrownBy(() -> TransactionIntent.builder()
                .intentId("intent-001")
                .chain("ethereum")
                .toAddress("0xabc")
                .amount(new BigDecimal("10"))
                .token(null)
                .build())
                .isInstanceOf(NullPointerException.class);
    }
}
