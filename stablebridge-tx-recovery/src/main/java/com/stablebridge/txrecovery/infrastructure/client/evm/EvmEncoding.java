package com.stablebridge.txrecovery.infrastructure.client.evm;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.HexFormat;
import java.util.List;

final class EvmEncoding {

    private static final int EIP_1559_TX_TYPE = 0x02;

    private EvmEncoding() {}

    static byte[] encodeEip1559Transaction(
            long chainId,
            long nonce,
            BigInteger maxPriorityFeePerGas,
            BigInteger maxFeePerGas,
            BigInteger gasLimit,
            String to,
            BigInteger value,
            byte[] data) {
        var toBytes = EvmHex.parseEvmAddress(to);

        List<Object> fields = List.of(
                BigInteger.valueOf(chainId),
                BigInteger.valueOf(nonce),
                maxPriorityFeePerGas,
                maxFeePerGas,
                gasLimit,
                toBytes,
                value,
                data,
                List.of());

        var rlpEncoded = RlpEncoder.encode(fields);

        var output = new ByteArrayOutputStream(1 + rlpEncoded.length);
        output.write(EIP_1559_TX_TYPE);
        output.write(rlpEncoded, 0, rlpEncoded.length);
        return output.toByteArray();
    }

    static byte[] decodeData(String input) {
        if (input == null || "0x".equals(input) || input.isEmpty()) {
            return new byte[0];
        }
        var hex = input.startsWith("0x") ? input.substring(2) : input;
        return HexFormat.of().parseHex(hex);
    }
}
