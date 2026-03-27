package com.stablebridge.txrecovery.infrastructure.client.evm;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.List;

final class RlpEncoder {

    private static final int SINGLE_BYTE_THRESHOLD = 0x7f;
    private static final int SHORT_STRING_OFFSET = 0x80;
    private static final int LONG_STRING_OFFSET = 0xb7;
    private static final int SHORT_LIST_OFFSET = 0xc0;
    private static final int LONG_LIST_OFFSET = 0xf7;
    private static final int SHORT_LENGTH_LIMIT = 55;

    private RlpEncoder() {}

    static byte[] encode(Object item) {
        return switch (item) {
            case byte[] bytes -> encodeBytes(bytes);
            case BigInteger bigInt -> encodeBigInteger(bigInt);
            case Long longVal -> encodeLong(longVal);
            case List<?> list -> encodeList(list);
            default -> throw new IllegalArgumentException("Unsupported RLP type: " + item.getClass());
        };
    }

    private static byte[] encodeBytes(byte[] data) {
        if (data.length == 1 && (data[0] & 0xff) <= SINGLE_BYTE_THRESHOLD) {
            return data;
        }
        return encodeLengthPrefixed(data, SHORT_STRING_OFFSET, LONG_STRING_OFFSET);
    }

    private static byte[] encodeBigInteger(BigInteger value) {
        if (value.signum() == 0) {
            return encodeBytes(new byte[0]);
        }
        var bytes = value.toByteArray();
        if (bytes[0] == 0) {
            var trimmed = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, trimmed, 0, trimmed.length);
            return encodeBytes(trimmed);
        }
        return encodeBytes(bytes);
    }

    private static byte[] encodeLong(long value) {
        if (value == 0) {
            return encodeBytes(new byte[0]);
        }
        return encodeBigInteger(BigInteger.valueOf(value));
    }

    private static byte[] encodeList(List<?> items) {
        var output = new ByteArrayOutputStream();
        items.forEach(item -> {
            var encoded = encode(item);
            output.write(encoded, 0, encoded.length);
        });
        var payload = output.toByteArray();
        return encodeLengthPrefixed(payload, SHORT_LIST_OFFSET, LONG_LIST_OFFSET);
    }

    private static byte[] encodeLengthPrefixed(byte[] data, int shortOffset, int longOffset) {
        if (data.length <= SHORT_LENGTH_LIMIT) {
            var result = new byte[1 + data.length];
            result[0] = (byte) (shortOffset + data.length);
            System.arraycopy(data, 0, result, 1, data.length);
            return result;
        }
        var lengthBytes = toMinimalBytes(data.length);
        var result = new byte[1 + lengthBytes.length + data.length];
        result[0] = (byte) (longOffset + lengthBytes.length);
        System.arraycopy(lengthBytes, 0, result, 1, lengthBytes.length);
        System.arraycopy(data, 0, result, 1 + lengthBytes.length, data.length);
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
}
