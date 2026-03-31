package com.stablebridge.txrecovery.infrastructure.client.evm;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.HexFormat;
import java.util.List;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EvmEncoding {

    private static final int EIP_1559_TX_TYPE = 0x02;
    private static final int UNSIGNED_FIELD_COUNT = 9;
    private static final int SIGNATURE_R_OFFSET = 0;
    private static final int SIGNATURE_S_OFFSET = 32;
    private static final int SIGNATURE_V_OFFSET = 64;
    private static final int KEY_SIZE = 32;
    private static final int ETHEREUM_V_OFFSET = 27;

    public static byte[] encodeEip1559Transaction(
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

    public static byte[] assembleSignedEip1559(byte[] unsignedPayload, byte[] signature) {
        if (unsignedPayload[0] != EIP_1559_TX_TYPE) {
            throw new EvmRpcException("Expected EIP-1559 type prefix (0x02)", false);
        }

        var r = new BigInteger(1, signature, SIGNATURE_R_OFFSET, KEY_SIZE);
        var s = new BigInteger(1, signature, SIGNATURE_S_OFFSET, KEY_SIZE);
        int yParity = (signature[SIGNATURE_V_OFFSET] & 0xff) - ETHEREUM_V_OFFSET;

        var unsignedListContent = extractRlpListContent(unsignedPayload, 1);

        var encodedYParity = RlpEncoder.encode(BigInteger.valueOf(yParity));
        var encodedR = RlpEncoder.encode(r);
        var encodedS = RlpEncoder.encode(s);

        var signedContent = new ByteArrayOutputStream(
                unsignedListContent.length + encodedYParity.length + encodedR.length + encodedS.length);
        signedContent.write(unsignedListContent, 0, unsignedListContent.length);
        signedContent.write(encodedYParity, 0, encodedYParity.length);
        signedContent.write(encodedR, 0, encodedR.length);
        signedContent.write(encodedS, 0, encodedS.length);

        var signedListPayload = signedContent.toByteArray();
        var signedList = wrapAsList(signedListPayload);

        var result = new ByteArrayOutputStream(1 + signedList.length);
        result.write(EIP_1559_TX_TYPE);
        result.write(signedList, 0, signedList.length);
        return result.toByteArray();
    }

    private static byte[] extractRlpListContent(byte[] data, int offset) {
        int prefix = data[offset] & 0xff;
        int headerSize;
        int contentLength;

        if (prefix <= 0xf7) {
            contentLength = prefix - 0xc0;
            headerSize = 1;
        } else {
            int lengthOfLength = prefix - 0xf7;
            contentLength = 0;
            for (int i = 0; i < lengthOfLength; i++) {
                contentLength = (contentLength << 8) | (data[offset + 1 + i] & 0xff);
            }
            headerSize = 1 + lengthOfLength;
        }

        var content = new byte[contentLength];
        System.arraycopy(data, offset + headerSize, content, 0, contentLength);
        return content;
    }

    private static byte[] wrapAsList(byte[] content) {
        if (content.length <= 55) {
            var result = new byte[1 + content.length];
            result[0] = (byte) (0xc0 + content.length);
            System.arraycopy(content, 0, result, 1, content.length);
            return result;
        }
        var lengthBytes = toMinimalBytes(content.length);
        var result = new byte[1 + lengthBytes.length + content.length];
        result[0] = (byte) (0xf7 + lengthBytes.length);
        System.arraycopy(lengthBytes, 0, result, 1, lengthBytes.length);
        System.arraycopy(content, 0, result, 1 + lengthBytes.length, content.length);
        return result;
    }

    private static byte[] toMinimalBytes(int value) {
        var bigEndian = BigInteger.valueOf(value).toByteArray();
        if (bigEndian[0] == 0 && bigEndian.length > 1) {
            var trimmed = new byte[bigEndian.length - 1];
            System.arraycopy(bigEndian, 1, trimmed, 0, trimmed.length);
            return trimmed;
        }
        return bigEndian;
    }

    static byte[] decodeData(String input) {
        if (input == null || "0x".equals(input) || input.isEmpty()) {
            return new byte[0];
        }
        var hex = input.startsWith("0x") ? input.substring(2) : input;
        return HexFormat.of().parseHex(hex);
    }
}
