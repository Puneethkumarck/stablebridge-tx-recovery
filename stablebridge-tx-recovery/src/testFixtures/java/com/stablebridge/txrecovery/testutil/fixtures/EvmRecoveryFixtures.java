package com.stablebridge.txrecovery.testutil.fixtures;

import static lombok.AccessLevel.PRIVATE;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.stablebridge.txrecovery.domain.recovery.model.FeeEstimate;
import com.stablebridge.txrecovery.domain.recovery.model.FeeUrgency;
import com.stablebridge.txrecovery.domain.transaction.model.SubmittedTransaction;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionStatus;
import com.stablebridge.txrecovery.infrastructure.client.evm.EvmReceipt;
import com.stablebridge.txrecovery.infrastructure.client.evm.EvmTransaction;

import lombok.NoArgsConstructor;

@NoArgsConstructor(access = PRIVATE)
public final class EvmRecoveryFixtures {

    public static final String SOME_TX_HASH = "0xabc123def456";
    public static final String SOME_FROM_ADDRESS = "0x1111111111111111111111111111111111111111";
    public static final String SOME_TO_ADDRESS = "0x2222222222222222222222222222222222222222";
    public static final String SOME_CHAIN = "ethereum";
    public static final long SOME_CHAIN_ID = 1L;
    public static final String SOME_REPLACEMENT_TX_HASH = "0xnew789abc";
    public static final BigDecimal SOME_GAS_BUDGET = new BigDecimal("0.01");

    public static final FeeEstimate SOME_URGENT_FEE = FeeEstimate.builder()
            .maxFeePerGas(new BigDecimal("50000000000"))
            .maxPriorityFeePerGas(new BigDecimal("5000000000"))
            .estimatedCost(new BigDecimal("50000000000"))
            .denomination("wei")
            .urgency(FeeUrgency.URGENT)
            .details(Map.of("source", "urgent-estimate"))
            .build();

    public static final FeeEstimate SOME_FAST_FEE = FeeEstimate.builder()
            .maxFeePerGas(new BigDecimal("30000000000"))
            .maxPriorityFeePerGas(new BigDecimal("3000000000"))
            .estimatedCost(new BigDecimal("30000000000"))
            .denomination("wei")
            .urgency(FeeUrgency.FAST)
            .details(Map.of("source", "fast-estimate"))
            .build();

    public static final FeeEstimate SOME_LOW_FAST_FEE = FeeEstimate.builder()
            .maxFeePerGas(new BigDecimal("5000000000"))
            .maxPriorityFeePerGas(new BigDecimal("1000000000"))
            .estimatedCost(new BigDecimal("5000000000"))
            .denomination("wei")
            .urgency(FeeUrgency.FAST)
            .details(Map.of("source", "low-fast-estimate"))
            .build();

    public static final FeeEstimate SOME_REPLACEMENT_FEE = FeeEstimate.builder()
            .maxFeePerGas(new BigDecimal("55000000000"))
            .maxPriorityFeePerGas(new BigDecimal("5500000000"))
            .estimatedCost(new BigDecimal("55000000000"))
            .denomination("wei")
            .urgency(FeeUrgency.URGENT)
            .details(Map.of("source", "replacement-estimate"))
            .build();

    public static final EvmTransaction SOME_MEMPOOL_TRANSACTION = EvmTransaction.builder()
            .hash(SOME_TX_HASH)
            .nonce("0x5")
            .from(SOME_FROM_ADDRESS)
            .to(SOME_TO_ADDRESS)
            .value("0x0")
            .gas("0xfde8")
            .maxFeePerGas("0x6fc23ac00")
            .maxPriorityFeePerGas("0x77359400")
            .input("0xa9059cbb")
            .type("0x2")
            .build();

    public static final EvmTransaction SOME_LOW_FEE_MEMPOOL_TRANSACTION = EvmTransaction.builder()
            .hash(SOME_TX_HASH)
            .nonce("0x5")
            .from(SOME_FROM_ADDRESS)
            .to(SOME_TO_ADDRESS)
            .value("0x0")
            .gas("0xfde8")
            .maxFeePerGas("0x12a05f200")
            .maxPriorityFeePerGas("0x3b9aca00")
            .input("0xa9059cbb")
            .type("0x2")
            .build();

    public static final EvmReceipt SOME_RECEIPT = EvmReceipt.builder()
            .transactionHash(SOME_TX_HASH)
            .blockNumber("0xa")
            .status("0x1")
            .gasUsed("0x5208")
            .effectiveGasPrice("0x6fc23ac00")
            .build();

    public static SubmittedTransaction someStuckTransaction() {
        return SubmittedTransaction.builder()
                .transactionId("tx-001")
                .intentId("intent-001")
                .chain(SOME_CHAIN)
                .txHash(SOME_TX_HASH)
                .fromAddress(SOME_FROM_ADDRESS)
                .status(TransactionStatus.STUCK)
                .retryCount(0)
                .gasSpent(BigDecimal.ZERO)
                .gasDenomination("wei")
                .gasBudget(SOME_GAS_BUDGET)
                .recoveryHistory(List.of())
                .submittedAt(Instant.parse("2026-03-27T10:00:00Z"))
                .stuckSince(Instant.parse("2026-03-27T10:05:00Z"))
                .build();
    }
}
