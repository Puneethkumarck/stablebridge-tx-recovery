package com.stablebridge.txrecovery.infrastructure.client.evm;

import java.util.ArrayList;
import java.util.List;

final class RlpDecoder {

    private static final int SINGLE_BYTE_THRESHOLD = 0x7f;
    private static final int SHORT_STRING_OFFSET = 0x80;
    private static final int LONG_STRING_OFFSET = 0xb7;
    private static final int SHORT_LIST_OFFSET = 0xc0;
    private static final int LONG_LIST_OFFSET = 0xf7;

    private RlpDecoder() {}

    static List<byte[]> decodeList(byte[] data) {
        var decoded = decode(data, 0);
        if (!(decoded.value() instanceof List<?> list)) {
            throw new IllegalArgumentException("Expected RLP list at top level");
        }
        return list.stream()
                .map(item -> {
                    if (item instanceof byte[] bytes) {
                        return bytes;
                    }
                    throw new IllegalArgumentException("Expected byte[] elements in RLP list");
                })
                .toList();
    }

    private static DecodeResult decode(byte[] data, int offset) {
        int prefix = data[offset] & 0xff;

        if (prefix <= SINGLE_BYTE_THRESHOLD) {
            return new DecodeResult(new byte[]{data[offset]}, offset + 1);
        }

        if (prefix <= LONG_STRING_OFFSET) {
            int length = prefix - SHORT_STRING_OFFSET;
            var value = new byte[length];
            System.arraycopy(data, offset + 1, value, 0, length);
            return new DecodeResult(value, offset + 1 + length);
        }

        if (prefix < SHORT_LIST_OFFSET) {
            int lengthOfLength = prefix - LONG_STRING_OFFSET;
            int length = readLength(data, offset + 1, lengthOfLength);
            var value = new byte[length];
            System.arraycopy(data, offset + 1 + lengthOfLength, value, 0, length);
            return new DecodeResult(value, offset + 1 + lengthOfLength + length);
        }

        if (prefix <= LONG_LIST_OFFSET) {
            int listLength = prefix - SHORT_LIST_OFFSET;
            return decodeListItems(data, offset + 1, listLength);
        }

        int lengthOfLength = prefix - LONG_LIST_OFFSET;
        int listLength = readLength(data, offset + 1, lengthOfLength);
        return decodeListItems(data, offset + 1 + lengthOfLength, listLength);
    }

    private static DecodeResult decodeListItems(byte[] data, int offset, int listLength) {
        var items = new ArrayList<>();
        int end = offset + listLength;
        int pos = offset;
        while (pos < end) {
            var result = decode(data, pos);
            items.add(result.value());
            pos = result.nextOffset();
        }
        return new DecodeResult(items, end);
    }

    private static int readLength(byte[] data, int offset, int lengthOfLength) {
        int length = 0;
        for (int i = 0; i < lengthOfLength; i++) {
            length = (length << 8) | (data[offset + i] & 0xff);
        }
        return length;
    }

    private record DecodeResult(Object value, int nextOffset) {}
}
