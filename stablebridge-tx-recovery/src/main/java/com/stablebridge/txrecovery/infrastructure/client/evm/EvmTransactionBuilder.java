package com.stablebridge.txrecovery.infrastructure.client.evm;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

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
    private static final int EIP_1559_TX_TYPE = 0x02;
    private static final String ZERO_VALUE = "0x0";
    private static final String TX_TYPE_HEX = "0x02";

    private final EvmRpcClient rpcClient;
    private final FeeOracle feeOracle;
    private final long chainId;

    public UnsignedTransaction build(TransactionIntent intent, EvmSubmissionResource resource) {
        var abiData = Erc20AbiEncoder.encodeTransfer(intent.toAddress(), intent.rawAmount());
        var dataHex = "0x" + HexFormat.of().formatHex(abiData);

        var feeEstimate = feeOracle.estimate(intent.chain(), FeeUrgency.MEDIUM);

        var estimatedGas = rpcClient.estimateGas(
                resource.fromAddress(), intent.tokenContractAddress(), dataHex, ZERO_VALUE);
        var gasLimit = new BigDecimal(estimatedGas)
                .multiply(GAS_LIMIT_SAFETY_MARGIN)
                .setScale(0, RoundingMode.CEILING)
                .toBigInteger();

        var maxFeePerGas = feeEstimate.maxFeePerGas().toBigInteger();
        var maxPriorityFeePerGas = feeEstimate.maxPriorityFeePerGas().toBigInteger();

        var rlpPayload = encodeEip1559Transaction(
                resource.nonce(), maxPriorityFeePerGas, maxFeePerGas, gasLimit,
                intent.tokenContractAddress(), abiData);

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

    private byte[] encodeEip1559Transaction(
            long nonce,
            BigInteger maxPriorityFeePerGas,
            BigInteger maxFeePerGas,
            BigInteger gasLimit,
            String to,
            byte[] data) {
        var toBytes = HexFormat.of().parseHex(to.startsWith("0x") ? to.substring(2) : to);

        List<Object> fields = List.of(
                BigInteger.valueOf(chainId),
                BigInteger.valueOf(nonce),
                maxPriorityFeePerGas,
                maxFeePerGas,
                gasLimit,
                toBytes,
                BigInteger.ZERO,
                data,
                List.of());

        var rlpEncoded = RlpEncoder.encode(fields);

        var output = new ByteArrayOutputStream(1 + rlpEncoded.length);
        output.write(EIP_1559_TX_TYPE);
        output.write(rlpEncoded, 0, rlpEncoded.length);
        return output.toByteArray();
    }
}
