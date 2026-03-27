package com.stablebridge.txrecovery.infrastructure.client.evm;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

import com.stablebridge.txrecovery.domain.address.model.ChainFamily;
import com.stablebridge.txrecovery.domain.recovery.model.FeeUrgency;
import com.stablebridge.txrecovery.domain.recovery.model.RecoveryOutcome;
import com.stablebridge.txrecovery.domain.recovery.model.RecoveryPlan;
import com.stablebridge.txrecovery.domain.recovery.model.RecoveryResult;
import com.stablebridge.txrecovery.domain.recovery.model.StuckAssessment;
import com.stablebridge.txrecovery.domain.recovery.model.StuckReason;
import com.stablebridge.txrecovery.domain.recovery.model.StuckSeverity;
import com.stablebridge.txrecovery.domain.recovery.port.FeeOracle;
import com.stablebridge.txrecovery.domain.recovery.port.RecoveryStrategy;
import com.stablebridge.txrecovery.domain.transaction.model.SubmittedTransaction;
import com.stablebridge.txrecovery.domain.transaction.model.UnsignedTransaction;
import com.stablebridge.txrecovery.domain.transaction.port.TransactionSigner;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
class EvmRecoveryStrategy implements RecoveryStrategy {

    private static final long CANCEL_GAS_LIMIT = 21_000L;
    private static final Duration DEFAULT_WAIT_DURATION = Duration.ofMinutes(5);

    private final EvmRpcClient rpcClient;
    private final FeeOracle feeOracle;
    private final long chainId;

    @Override
    public boolean appliesTo(ChainFamily chainFamily) {
        return chainFamily == ChainFamily.EVM;
    }

    @Override
    public StuckAssessment assess(SubmittedTransaction transaction) {
        var receipt = rpcClient.getTransactionReceipt(transaction.txHash());
        if (receipt.isPresent()) {
            return assessConfirmedTransaction();
        }

        var mempoolTx = rpcClient.getTransactionByHash(transaction.txHash());
        if (mempoolTx.isEmpty()) {
            return assessDroppedTransaction(transaction);
        }

        var tx = mempoolTx.get();
        return assessNonceGap(transaction, tx)
                .orElseGet(() -> assessGasPrice(transaction, tx));
    }

    @Override
    public RecoveryResult execute(RecoveryPlan plan, TransactionSigner signer) {
        return switch (plan) {
            case RecoveryPlan.SpeedUp speedUp -> executeSpeedUp(speedUp, signer);
            case RecoveryPlan.Cancel cancel -> executeCancel(cancel, signer);
            case RecoveryPlan.Resubmit _ -> throw new IllegalStateException(
                    "EVM does not support Resubmit — use SpeedUp (RBF) instead");
            case RecoveryPlan.Wait wait -> RecoveryResult.builder()
                    .outcome(RecoveryOutcome.WAITING)
                    .details("Waiting estimated %s: %s".formatted(wait.estimatedClearance(), wait.reason()))
                    .build();
        };
    }

    private StuckAssessment assessConfirmedTransaction() {
        return StuckAssessment.builder()
                .reason(StuckReason.NOT_SEEN)
                .severity(StuckSeverity.LOW)
                .recommendedPlan(RecoveryPlan.Wait.builder()
                        .estimatedClearance(DEFAULT_WAIT_DURATION)
                        .reason("Transaction has receipt, may be confirming")
                        .build())
                .explanation("Transaction already has receipt on chain")
                .build();
    }

    private StuckAssessment assessDroppedTransaction(SubmittedTransaction transaction) {
        var urgentFee = feeOracle.estimate(transaction.chain(), FeeUrgency.URGENT);
        return StuckAssessment.builder()
                .reason(StuckReason.MEMPOOL_DROPPED)
                .severity(StuckSeverity.HIGH)
                .recommendedPlan(RecoveryPlan.SpeedUp.builder()
                        .originalTxHash(transaction.txHash())
                        .newFee(urgentFee)
                        .build())
                .explanation("Transaction not found in mempool and no receipt — likely dropped")
                .build();
    }

    private Optional<StuckAssessment> assessNonceGap(SubmittedTransaction transaction, EvmTransaction tx) {
        var onChainNonce = rpcClient.getTransactionCount(transaction.fromAddress(), "latest");
        var txNonce = EvmHex.decodeQuantity(tx.nonce()).longValue();

        if (onChainNonce.longValue() >= txNonce) {
            return Optional.empty();
        }

        var replacementFee = feeOracle.estimateReplacement(
                transaction.chain(), transaction.txHash(), transaction.retryCount() + 1);
        return Optional.of(StuckAssessment.builder()
                .reason(StuckReason.NONCE_GAP)
                .severity(StuckSeverity.HIGH)
                .recommendedPlan(RecoveryPlan.SpeedUp.builder()
                        .originalTxHash(transaction.txHash())
                        .newFee(replacementFee)
                        .build())
                .explanation("Nonce gap detected: on-chain nonce=%d, transaction nonce=%d"
                        .formatted(onChainNonce.longValue(), txNonce))
                .build());
    }

    private StuckAssessment assessGasPrice(SubmittedTransaction transaction, EvmTransaction tx) {
        var currentFee = feeOracle.estimate(transaction.chain(), FeeUrgency.FAST);
        var originalMaxFee = decodeMaxFee(tx);
        var currentMaxFee = currentFee.maxFeePerGas();

        if (currentMaxFee.compareTo(originalMaxFee) > 0) {
            var replacementFee = feeOracle.estimateReplacement(
                    transaction.chain(), transaction.txHash(), transaction.retryCount() + 1);
            var ratio = originalMaxFee.compareTo(BigDecimal.ZERO) > 0
                    ? currentMaxFee.divide(originalMaxFee, 2, RoundingMode.HALF_UP)
                    : currentMaxFee;
            return StuckAssessment.builder()
                    .reason(StuckReason.UNDERPRICED)
                    .severity(StuckSeverity.MEDIUM)
                    .recommendedPlan(RecoveryPlan.SpeedUp.builder()
                            .originalTxHash(transaction.txHash())
                            .newFee(replacementFee)
                            .build())
                    .explanation("Transaction underpriced: current fast=%s wei, submission=%s wei, ratio=%s"
                            .formatted(currentMaxFee.toPlainString(), originalMaxFee.toPlainString(),
                                    ratio.toPlainString()))
                    .build();
        }

        return StuckAssessment.builder()
                .reason(StuckReason.NOT_PROPAGATED)
                .severity(StuckSeverity.LOW)
                .recommendedPlan(RecoveryPlan.Wait.builder()
                        .estimatedClearance(DEFAULT_WAIT_DURATION)
                        .reason("Transaction in mempool with adequate fee, waiting for inclusion")
                        .build())
                .explanation("Transaction in mempool with adequate gas price, pending network inclusion")
                .build();
    }

    private RecoveryResult executeSpeedUp(RecoveryPlan.SpeedUp speedUp, TransactionSigner signer) {
        var originalTx = rpcClient.getTransactionByHash(speedUp.originalTxHash())
                .orElseThrow(() -> new EvmRpcException(
                        "Original transaction not found for speed-up: " + speedUp.originalTxHash(), false));

        var nonce = EvmHex.decodeQuantity(originalTx.nonce()).longValue();
        var toAddress = originalTx.to();
        var value = EvmHex.decodeQuantity(originalTx.value());
        var data = EvmEncoding.decodeData(originalTx.input());
        var gasLimit = EvmHex.decodeQuantity(originalTx.gas());

        var maxFeePerGas = speedUp.newFee().maxFeePerGas().toBigInteger();
        var maxPriorityFeePerGas = speedUp.newFee().maxPriorityFeePerGas().toBigInteger();

        var rlpPayload = EvmEncoding.encodeEip1559Transaction(
                chainId, nonce, maxPriorityFeePerGas, maxFeePerGas, gasLimit, toAddress, value, data);

        var unsignedTx = UnsignedTransaction.builder()
                .intentId("recovery-speedup-" + speedUp.originalTxHash())
                .chain(rpcClient.getChain())
                .fromAddress(originalTx.from())
                .toAddress(toAddress)
                .payload(rlpPayload)
                .feeEstimate(speedUp.newFee())
                .metadata(Map.of(
                        "nonce", String.valueOf(nonce),
                        "gasLimit", gasLimit.toString(),
                        "type", "0x02",
                        "chainId", String.valueOf(chainId),
                        "recoveryAction", "SPEED_UP",
                        "originalTxHash", speedUp.originalTxHash()))
                .build();

        var signedTx = signer.sign(unsignedTx, originalTx.from());
        var txHash = rpcClient.sendRawTransaction(
                "0x" + HexFormat.of().formatHex(signedTx.signedPayload()));

        return RecoveryResult.builder()
                .outcome(RecoveryOutcome.REPLACEMENT_SUBMITTED)
                .replacementTxHash(txHash)
                .gasCost(speedUp.newFee().estimatedCost())
                .details("Speed-up replacement submitted with nonce=%d, maxFeePerGas=%s wei"
                        .formatted(nonce, maxFeePerGas))
                .build();
    }

    private RecoveryResult executeCancel(RecoveryPlan.Cancel cancel, TransactionSigner signer) {
        var originalTx = rpcClient.getTransactionByHash(cancel.originalTxHash())
                .orElseThrow(() -> new EvmRpcException(
                        "Original transaction not found for cancel: " + cancel.originalTxHash(), false));

        var nonce = EvmHex.decodeQuantity(originalTx.nonce()).longValue();
        var fromAddress = originalTx.from();

        var cancelFee = feeOracle.estimate(rpcClient.getChain(), FeeUrgency.FAST);
        var maxFeePerGas = cancelFee.maxFeePerGas().toBigInteger();
        var maxPriorityFeePerGas = cancelFee.maxPriorityFeePerGas().toBigInteger();

        var rlpPayload = EvmEncoding.encodeEip1559Transaction(
                chainId, nonce, maxPriorityFeePerGas, maxFeePerGas, BigInteger.valueOf(CANCEL_GAS_LIMIT),
                fromAddress, BigInteger.ZERO, new byte[0]);

        var unsignedTx = UnsignedTransaction.builder()
                .intentId("recovery-cancel-" + cancel.originalTxHash())
                .chain(rpcClient.getChain())
                .fromAddress(fromAddress)
                .toAddress(fromAddress)
                .payload(rlpPayload)
                .feeEstimate(cancelFee)
                .metadata(Map.of(
                        "nonce", String.valueOf(nonce),
                        "gasLimit", String.valueOf(CANCEL_GAS_LIMIT),
                        "type", "0x02",
                        "chainId", String.valueOf(chainId),
                        "recoveryAction", "CANCEL",
                        "originalTxHash", cancel.originalTxHash()))
                .build();

        var signedTx = signer.sign(unsignedTx, fromAddress);
        var txHash = rpcClient.sendRawTransaction(
                "0x" + HexFormat.of().formatHex(signedTx.signedPayload()));

        return RecoveryResult.builder()
                .outcome(RecoveryOutcome.REPLACEMENT_SUBMITTED)
                .replacementTxHash(txHash)
                .gasCost(cancelFee.estimatedCost())
                .details("Cancel self-transfer submitted with nonce=%d, gasLimit=%d"
                        .formatted(nonce, CANCEL_GAS_LIMIT))
                .build();
    }

    private static BigDecimal decodeMaxFee(EvmTransaction tx) {
        if (tx.maxFeePerGas() != null) {
            return new BigDecimal(EvmHex.decodeQuantity(tx.maxFeePerGas()));
        }
        if (tx.gasPrice() != null) {
            return new BigDecimal(EvmHex.decodeQuantity(tx.gasPrice()));
        }
        return BigDecimal.ZERO;
    }
}
