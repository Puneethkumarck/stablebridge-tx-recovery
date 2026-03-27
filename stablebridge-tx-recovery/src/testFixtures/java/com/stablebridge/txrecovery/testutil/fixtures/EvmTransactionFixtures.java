package com.stablebridge.txrecovery.testutil.fixtures;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;

import com.stablebridge.txrecovery.domain.address.model.AddressTier;
import com.stablebridge.txrecovery.domain.recovery.model.FeeEstimate;
import com.stablebridge.txrecovery.domain.recovery.model.FeeUrgency;
import com.stablebridge.txrecovery.domain.transaction.model.EvmSubmissionResource;
import com.stablebridge.txrecovery.domain.transaction.model.SubmissionStrategy;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionIntent;

public final class EvmTransactionFixtures {

    public static final long SOME_CHAIN_ID = 1L;
    public static final String SOME_CHAIN = "ethereum";
    public static final String SOME_FROM_ADDRESS = "0x1111111111111111111111111111111111111111";
    public static final String SOME_TO_ADDRESS = "0x2222222222222222222222222222222222222222";
    public static final String SOME_TOKEN_CONTRACT = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48";
    public static final BigDecimal SOME_AMOUNT = new BigDecimal("1000000");
    public static final BigInteger SOME_RAW_AMOUNT = BigInteger.valueOf(1000000);
    public static final long SOME_NONCE = 5L;
    public static final BigDecimal SOME_MAX_FEE_PER_GAS = new BigDecimal("30000000000");
    public static final BigDecimal SOME_MAX_PRIORITY_FEE_PER_GAS = new BigDecimal("2000000000");
    public static final BigDecimal SOME_ESTIMATED_COST = new BigDecimal("0.001");
    public static final String SOME_DENOMINATION = "ETH";
    public static final BigInteger SOME_ESTIMATED_GAS = BigInteger.valueOf(65000);

    public static TransactionIntent someTransactionIntent() {
        return TransactionIntent.builder()
                .intentId("intent-001")
                .chain(SOME_CHAIN)
                .toAddress(SOME_TO_ADDRESS)
                .amount(SOME_AMOUNT)
                .token("USDC")
                .tokenDecimals(6)
                .rawAmount(SOME_RAW_AMOUNT)
                .tokenContractAddress(SOME_TOKEN_CONTRACT)
                .strategy(SubmissionStrategy.SEQUENTIAL)
                .createdAt(Instant.parse("2026-03-27T10:00:00Z"))
                .build();
    }

    public static EvmSubmissionResource someEvmSubmissionResource() {
        return EvmSubmissionResource.builder()
                .chain(SOME_CHAIN)
                .fromAddress(SOME_FROM_ADDRESS)
                .nonce(SOME_NONCE)
                .tier(AddressTier.HOT)
                .build();
    }

    public static FeeEstimate someFeeEstimate() {
        return FeeEstimate.builder()
                .maxFeePerGas(SOME_MAX_FEE_PER_GAS)
                .maxPriorityFeePerGas(SOME_MAX_PRIORITY_FEE_PER_GAS)
                .estimatedCost(SOME_ESTIMATED_COST)
                .denomination(SOME_DENOMINATION)
                .urgency(FeeUrgency.MEDIUM)
                .build();
    }

    private EvmTransactionFixtures() {}
}
