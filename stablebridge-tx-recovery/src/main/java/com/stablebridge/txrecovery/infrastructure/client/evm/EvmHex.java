package com.stablebridge.txrecovery.infrastructure.client.evm;

import java.math.BigInteger;
import java.util.HexFormat;

final class EvmHex {

    private EvmHex() {}

    static BigInteger decodeQuantity(String hex) {
        if (hex == null) {
            throw new EvmRpcException("Cannot decode null hex quantity");
        }
        var stripped = hex.startsWith("0x") ? hex.substring(2) : hex;
        return new BigInteger(stripped, 16);
    }

    static String encodeQuantity(long value) {
        return "0x" + Long.toHexString(value);
    }

    static byte[] parseEvmAddress(String address) {
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("EVM address must not be null or blank");
        }
        var hex = address.startsWith("0x") ? address.substring(2) : address;
        var bytes = HexFormat.of().parseHex(hex);
        if (bytes.length != 20) {
            throw new IllegalArgumentException("EVM address must be 20 bytes, got " + bytes.length);
        }
        return bytes;
    }
}
