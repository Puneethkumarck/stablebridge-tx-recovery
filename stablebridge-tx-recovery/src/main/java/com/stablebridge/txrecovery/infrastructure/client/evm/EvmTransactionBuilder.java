package com.stablebridge.txrecovery.infrastructure.client.evm;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;

import com.stablebridge.txrecovery.domain.recovery.model.FeeEstimate;
import com.stablebridge.txrecovery.domain.recovery.model.FeeUrgency;
import com.stablebridge.txrecovery.domain.recovery.port.FeeOracle;
import com.stablebridge.txrecovery.domain.transaction.model.EvmSubmissionResource;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionIntent;
import com.stablebridge.txrecovery.domain.transaction.model.UnsignedTransaction;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
class EvmTransactionBuilder {

    private static final BigDecimal GAS_LIMIT_SAFETY_MARGIN = new BigDecimal("1.2");
    private static final String ZERO_VALUE = "0x0";
    private static final String TX_TYPE_HEX = "0x02";

    private final EvmRpcClient rpcClient;
    private final FeeOracle feeOracle;
    private final long chainId;

    public UnsignedTransaction build(TransactionIntent intent, EvmSubmissionResource resource) {
        var abiData = Erc20AbiEncoder.encodeTransfer(intent.toAddress(), intent.rawAmount());
        var dataHex = "0x" + HexFormat.of().formatHex(abiData);

        var feeEstimate = feeOracle.estimate(intent.chain(), FeeUrgency.MEDIUM);
        validateFeeEstimate(feeEstimate);

        var estimatedGas = rpcClient.estimateGas(
                resource.fromAddress(), intent.tokenContractAddress(), dataHex, ZERO_VALUE);
        var gasLimit = new BigDecimal(estimatedGas)
                .multiply(GAS_LIMIT_SAFETY_MARGIN)
                .setScale(0, RoundingMode.CEILING)
                .toBigInteger();

        var maxFeePerGas = feeEstimate.maxFeePerGas().toBigIntegerExact();
        var maxPriorityFeePerGas = feeEstimate.maxPriorityFeePerGas().toBigIntegerExact();

        var rlpPayload = EvmEncoding.encodeEip1559Transaction(
                chainId, resource.nonce(), maxPriorityFeePerGas, maxFeePerGas, gasLimit,
                intent.tokenContractAddress(), BigInteger.ZERO, abiData);

        var metadata = Map.of(
                "nonce", String.valueOf(resource.nonce()),
                "gasLimit", gasLimit.toString(),
                "type", TX_TYPE_HEX,
                "chainId", String.valueOf(chainId));

        return UnsignedTransaction.builder()
                .intentId(intent.intentId())
                .chain(intent.chain())
                .fromAddress(resource.fromAddress())
                .toAddress(intent.tokenContractAddress())
                .payload(rlpPayload)
                .feeEstimate(feeEstimate)
                .metadata(metadata)
                .build();
    }

    private static void validateFeeEstimate(FeeEstimate feeEstimate) {
        Objects.requireNonNull(feeEstimate, "FeeEstimate must not be null");
        var maxFee = Objects.requireNonNull(feeEstimate.maxFeePerGas(), "FeeEstimate.maxFeePerGas must not be null");
        var maxPriorityFee = Objects.requireNonNull(
                feeEstimate.maxPriorityFeePerGas(), "FeeEstimate.maxPriorityFeePerGas must not be null");
        if (maxFee.signum() < 0 || maxPriorityFee.signum() < 0) {
            throw new IllegalArgumentException("EIP-1559 fee values must be non-negative");
        }
        if (maxPriorityFee.compareTo(maxFee) > 0) {
            throw new IllegalArgumentException(
                    "maxPriorityFeePerGas (%s) exceeds maxFeePerGas (%s)".formatted(maxPriorityFee, maxFee));
        }
    }
}
