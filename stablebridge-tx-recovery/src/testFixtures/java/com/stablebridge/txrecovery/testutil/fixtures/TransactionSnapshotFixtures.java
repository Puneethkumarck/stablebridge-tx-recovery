package com.stablebridge.txrecovery.testutil.fixtures;

import static com.stablebridge.txrecovery.domain.transaction.model.TransactionStatus.PENDING;
import static com.stablebridge.txrecovery.domain.transaction.model.TransactionStatus.SUBMITTED;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import com.stablebridge.txrecovery.domain.recovery.model.EscalationTier;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionSnapshot;

public final class TransactionSnapshotFixtures {

    public static final String SOME_TRANSACTION_ID = "tx-001";
    public static final String SOME_INTENT_ID = "intent-001";
    public static final String SOME_TX_HASH = "0xabc123";
    public static final int SOME_RETRY_COUNT = 3;
    public static final BigDecimal SOME_GAS_SPENT = new BigDecimal("0.005");

    public static final EscalationTier SOME_ESCALATION_TIER = EscalationTier.builder()
            .level(2)
            .stuckThreshold(Duration.ofMinutes(10))
            .gasMultiplier(new BigDecimal("1.5"))
            .build();

    public static final TransactionSnapshot SOME_SNAPSHOT = TransactionSnapshot.builder()
            .transactionId(SOME_TRANSACTION_ID)
            .intentId(SOME_INTENT_ID)
            .status(SUBMITTED)
            .txHash(SOME_TX_HASH)
            .retryCount(SOME_RETRY_COUNT)
            .gasSpent(SOME_GAS_SPENT)
            .currentTier(SOME_ESCALATION_TIER)
            .updatedAt(Instant.now())
            .build();

    public static final TransactionSnapshot SOME_MINIMAL_SNAPSHOT = TransactionSnapshot.builder()
            .transactionId(SOME_TRANSACTION_ID)
            .intentId(SOME_INTENT_ID)
            .status(PENDING)
            .retryCount(0)
            .build();

    private TransactionSnapshotFixtures() {}
}
