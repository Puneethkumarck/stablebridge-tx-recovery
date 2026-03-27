package com.stablebridge.txrecovery.testutil.fixtures;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;

import com.stablebridge.txrecovery.domain.recovery.model.FeeEstimate;
import com.stablebridge.txrecovery.domain.recovery.model.FeeUrgency;
import com.stablebridge.txrecovery.domain.transaction.model.SolanaSubmissionResource;
import com.stablebridge.txrecovery.domain.transaction.model.SubmissionStrategy;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionIntent;

public final class SolanaTransactionFixtures {

    public static final String SOME_SOLANA_CHAIN = "solana";
    public static final String SOME_WALLET_ADDRESS = "7EcDhSYGxXyscszYEp35KHN8vvw3svAuLKTzXwCFLtV";
    public static final String SOME_DESTINATION_ADDRESS = "9WzDXwBbmkg8ZTbNMqUxvQRAyrZzDsGYdLVL9zYtAWWM";
    public static final String SOME_TOKEN_MINT = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v";
    public static final String SOME_NONCE_ACCOUNT = "6DPjNEshHxbo8YJbZbRMNsQn3tkKHvj7SucoETN2Y77d";
    public static final String SOME_NONCE_VALUE = "GkR4MQFjVbfvGqP5SLQKLMGM1dGfAsnHcNqJHEJPQeaj";
    public static final int SOME_COMPUTE_UNIT_LIMIT = 200_000;
    public static final long SOME_COMPUTE_UNIT_PRICE = 50_000L;

    public static final BigDecimal SOME_AMOUNT = new BigDecimal("100.00");
    public static final BigInteger SOME_RAW_AMOUNT = BigInteger.valueOf(100_000_000L);

    private SolanaTransactionFixtures() {}

    public static SolanaSubmissionResource someSolanaSubmissionResource() {
        return SolanaSubmissionResource.builder()
                .chain(SOME_SOLANA_CHAIN)
                .fromAddress(SOME_WALLET_ADDRESS)
                .nonceAccountAddress(SOME_NONCE_ACCOUNT)
                .nonceValue(SOME_NONCE_VALUE)
                .build();
    }

    public static TransactionIntent someSolanaTransactionIntent() {
        return TransactionIntent.builder()
                .intentId("solana-intent-001")
                .chain(SOME_SOLANA_CHAIN)
                .toAddress(SOME_DESTINATION_ADDRESS)
                .amount(SOME_AMOUNT)
                .token("USDC")
                .tokenDecimals(6)
                .rawAmount(SOME_RAW_AMOUNT)
                .tokenContractAddress(SOME_TOKEN_MINT)
                .strategy(SubmissionStrategy.SEQUENTIAL)
                .createdAt(Instant.parse("2026-03-27T10:00:00Z"))
                .build();
    }

    public static FeeEstimate someSolanaFeeEstimate() {
        return FeeEstimate.builder()
                .computeUnitPrice(BigDecimal.valueOf(SOME_COMPUTE_UNIT_PRICE))
                .estimatedCost(new BigDecimal("0.000005"))
                .denomination("SOL")
                .urgency(FeeUrgency.MEDIUM)
                .build();
    }
}
