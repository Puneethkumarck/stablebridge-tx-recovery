package com.stablebridge.txrecovery.testutil.fixtures;

import static com.stablebridge.txrecovery.testutil.fixtures.TransactionIntentFixtures.SOME_CHAIN;
import static com.stablebridge.txrecovery.testutil.fixtures.TransactionIntentFixtures.SOME_INTENT_ID;
import static com.stablebridge.txrecovery.testutil.fixtures.TransactionIntentFixtures.SOME_TO_ADDRESS;

import java.math.BigDecimal;
import java.time.Instant;

import com.stablebridge.txrecovery.domain.address.model.AddressTier;
import com.stablebridge.txrecovery.domain.recovery.model.FeeEstimate;
import com.stablebridge.txrecovery.domain.recovery.model.FeeUrgency;
import com.stablebridge.txrecovery.domain.recovery.model.RecoveryPlan;
import com.stablebridge.txrecovery.domain.transaction.model.BroadcastResult;
import com.stablebridge.txrecovery.domain.transaction.model.ConfirmationStatus;
import com.stablebridge.txrecovery.domain.transaction.model.EvmSubmissionResource;
import com.stablebridge.txrecovery.domain.transaction.model.SignedTransaction;
import com.stablebridge.txrecovery.domain.transaction.model.UnsignedTransaction;

public final class WorkflowTestFixtures {

    private WorkflowTestFixtures() {}

    public static final String SOME_TX_HASH = "0xabc123def456";
    public static final String SOME_FROM_ADDRESS = "0xsender0000000000000000000000000000000001";

    public static EvmSubmissionResource someEvmResource() {
        return EvmSubmissionResource.builder()
                .chain(SOME_CHAIN)
                .fromAddress(SOME_FROM_ADDRESS)
                .nonce(1L)
                .tier(AddressTier.HOT)
                .build();
    }

    public static UnsignedTransaction someUnsignedTransaction() {
        return UnsignedTransaction.builder()
                .intentId(SOME_INTENT_ID)
                .chain(SOME_CHAIN)
                .fromAddress(SOME_FROM_ADDRESS)
                .toAddress(SOME_TO_ADDRESS)
                .payload(new byte[]{0x01, 0x02})
                .feeEstimate(someFeeEstimate())
                .build();
    }

    public static SignedTransaction someSignedTransaction() {
        return SignedTransaction.builder()
                .intentId(SOME_INTENT_ID)
                .chain(SOME_CHAIN)
                .signedPayload(new byte[]{0x01, 0x02, 0x03})
                .signerAddress(SOME_FROM_ADDRESS)
                .build();
    }

    public static BroadcastResult someBroadcastResult() {
        return BroadcastResult.builder()
                .txHash(SOME_TX_HASH)
                .chain(SOME_CHAIN)
                .broadcastedAt(Instant.parse("2026-01-01T00:00:30Z"))
                .build();
    }

    public static ConfirmationStatus someFinalizedConfirmation() {
        return ConfirmationStatus.builder()
                .txHash(SOME_TX_HASH)
                .chain(SOME_CHAIN)
                .confirmations(12)
                .requiredConfirmations(12)
                .finalized(true)
                .build();
    }

    public static ConfirmationStatus someNotFinalizedConfirmation() {
        return ConfirmationStatus.builder()
                .txHash(SOME_TX_HASH)
                .chain(SOME_CHAIN)
                .confirmations(4)
                .requiredConfirmations(12)
                .finalized(false)
                .build();
    }

    public static FeeEstimate someFeeEstimate() {
        return FeeEstimate.builder()
                .maxFeePerGas(new BigDecimal("20"))
                .maxPriorityFeePerGas(new BigDecimal("1"))
                .estimatedCost(new BigDecimal("0.001"))
                .denomination("ETH")
                .urgency(FeeUrgency.MEDIUM)
                .build();
    }

    public static RecoveryPlan.SpeedUp someSpeedUpPlan(String originalTxHash) {
        return RecoveryPlan.SpeedUp.builder()
                .originalTxHash(originalTxHash)
                .newFee(FeeEstimate.builder()
                        .maxFeePerGas(new BigDecimal("30"))
                        .maxPriorityFeePerGas(new BigDecimal("2"))
                        .estimatedCost(new BigDecimal("0.002"))
                        .denomination("ETH")
                        .urgency(FeeUrgency.FAST)
                        .build())
                .build();
    }
}
