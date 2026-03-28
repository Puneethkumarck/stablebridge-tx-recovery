package com.stablebridge.txrecovery.testutil.fixtures;

import static lombok.AccessLevel.PRIVATE;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.stablebridge.txrecovery.domain.recovery.model.FeeEstimate;
import com.stablebridge.txrecovery.domain.recovery.model.FeeUrgency;
import com.stablebridge.txrecovery.domain.transaction.model.SolanaSubmissionResource;
import com.stablebridge.txrecovery.domain.transaction.model.SubmittedTransaction;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionIntent;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionStatus;

import lombok.NoArgsConstructor;

@NoArgsConstructor(access = PRIVATE)
public final class SolanaRecoveryFixtures {

    public static final String SOME_TX_HASH =
            "5VERv8NMvzbJMEkV8xnrLkEaWRtSz9CosKDYjCJjBRnbJLgp8uirBgmQpjKhoR4tjF3ZpRzrFmBV6UjKdiSZkQU";
    public static final String SOME_FROM_ADDRESS = "9WzDXwBbmkg8ZTbNMqUxvQRAyrZzDsGYdLVL9zYtAWWM";
    public static final String SOME_TO_ADDRESS = "FZkNMvEFbTBMFGdBJMEg7MG7kSCjdWP2VcGp7P3Y4Vzu";
    public static final String SOME_CHAIN = "solana";
    public static final String SOME_NONCE_ACCOUNT = "DZnkkTmCiFWfYTfT41X3Rd1kDgozqzxWaHqsw6W4x2oe";
    public static final String SOME_NONCE_VALUE = "GHtXQBpokJPcruKsNkQfW9TVvSVkRNTJTHx7XUgJsmR3";
    public static final String SOME_NEW_NONCE_VALUE = "J7oBm3RDHd3XAcEAq2rjGKAHp9tNUk1K7SxtWMFvQvZT";
    public static final String SOME_REPLACEMENT_TX_HASH =
            "2jg1X5k3cZfqYvYChXQWWEfJuNsqGMTgh6CjM89sKABe1TCaEhSXnkPLu2tKCj2GpLdAoTbgHmAW1YpVdPthNJd";
    public static final BigDecimal SOME_GAS_BUDGET = new BigDecimal("0.005");

    public static final FeeEstimate SOME_URGENT_FEE = FeeEstimate.builder()
            .computeUnitPrice(new BigDecimal("50000"))
            .estimatedCost(new BigDecimal("0.001"))
            .denomination("SOL")
            .urgency(FeeUrgency.URGENT)
            .details(Map.of("source", "urgent-estimate"))
            .build();

    public static SubmittedTransaction someStuckSolanaTransaction() {
        return SubmittedTransaction.builder()
                .transactionId("sol-tx-001")
                .intentId("sol-intent-001")
                .chain(SOME_CHAIN)
                .txHash(SOME_TX_HASH)
                .fromAddress(SOME_FROM_ADDRESS)
                .resource(SolanaSubmissionResource.builder()
                        .chain(SOME_CHAIN)
                        .fromAddress(SOME_FROM_ADDRESS)
                        .nonceAccountAddress(SOME_NONCE_ACCOUNT)
                        .nonceValue(SOME_NONCE_VALUE)
                        .build())
                .status(TransactionStatus.STUCK)
                .retryCount(0)
                .gasSpent(BigDecimal.ZERO)
                .gasDenomination("SOL")
                .gasBudget(SOME_GAS_BUDGET)
                .recoveryHistory(List.of())
                .submittedAt(Instant.parse("2026-03-27T10:00:00Z"))
                .stuckSince(Instant.parse("2026-03-27T10:05:00Z"))
                .build();
    }

    public static TransactionIntent someSolanaTransactionIntent() {
        return TransactionIntent.builder()
                .intentId("recovery-resubmit-" + SOME_TX_HASH)
                .chain(SOME_CHAIN)
                .toAddress(SOME_FROM_ADDRESS)
                .amount(BigDecimal.ZERO)
                .token("SOL")
                .tokenDecimals(6)
                .rawAmount(BigInteger.ZERO)
                .tokenContractAddress("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v")
                .build();
    }
}
