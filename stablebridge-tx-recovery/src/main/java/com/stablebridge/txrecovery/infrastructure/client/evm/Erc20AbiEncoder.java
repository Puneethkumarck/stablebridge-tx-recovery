package com.stablebridge.txrecovery.infrastructure.client.evm;

import java.math.BigInteger;
import java.util.HexFormat;

final class Erc20AbiEncoder {

    private static final byte[] TRANSFER_SELECTOR = HexFormat.of().parseHex("a9059cbb");
    private static final int WORD_SIZE = 32;
    private static final int SELECTOR_SIZE = 4;
    private static final int ENCODED_SIZE = SELECTOR_SIZE + WORD_SIZE + WORD_SIZE;
    private static final int ADDRESS_BYTE_LENGTH = 20;

    private Erc20AbiEncoder() {}

    static byte[] encodeTransfer(String recipientAddress, BigInteger amount) {
        var result = new byte[ENCODED_SIZE];
        System.arraycopy(TRANSFER_SELECTOR, 0, result, 0, SELECTOR_SIZE);

        var addressBytes = parseAddress(recipientAddress);
        System.arraycopy(addressBytes, 0, result, SELECTOR_SIZE + WORD_SIZE - ADDRESS_BYTE_LENGTH, ADDRESS_BYTE_LENGTH);

        var amountBytes = toUint256(amount);
        System.arraycopy(amountBytes, 0, result, SELECTOR_SIZE + WORD_SIZE, WORD_SIZE);

        return result;
    }

    private static byte[] parseAddress(String address) {
        var hex = address.startsWith("0x") ? address.substring(2) : address;
        return HexFormat.of().parseHex(hex);
    }

    private static byte[] toUint256(BigInteger value) {
        var raw = value.toByteArray();
        var padded = new byte[WORD_SIZE];
        if (raw[0] == 0 && raw.length > 1) {
            System.arraycopy(raw, 1, padded, WORD_SIZE - (raw.length - 1), raw.length - 1);
        } else {
            System.arraycopy(raw, 0, padded, WORD_SIZE - raw.length, raw.length);
        }
        return padded;
    }
}
