package com.stablebridge.txrecovery.infrastructure.client.evm;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.util.HexFormat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class EvmEncodingTest {

    private static final String SOME_ADDRESS = "0x742d35Cc6634C0532925a3b844Bc9e7595f2bD18";

    @Nested
    class EncodeEip1559Transaction {

        @Test
        void shouldProduceEip1559TypePrefix() {
            // when
            var result = EvmEncoding.encodeEip1559Transaction(
                    1L,
                    0L,
                    BigInteger.valueOf(2_000_000_000L),
                    BigInteger.valueOf(30_000_000_000L),
                    BigInteger.valueOf(21_000L),
                    SOME_ADDRESS,
                    BigInteger.ZERO,
                    new byte[0]);

            // then
            assertThat(result[0]).isEqualTo((byte) 0x02);
        }

        @Test
        void shouldProduceDeterministicOutputForSameInputs() {
            // when
            var result1 = EvmEncoding.encodeEip1559Transaction(
                    1L, 5L,
                    BigInteger.valueOf(1_000_000_000L),
                    BigInteger.valueOf(20_000_000_000L),
                    BigInteger.valueOf(21_000L),
                    SOME_ADDRESS,
                    BigInteger.TEN,
                    new byte[]{0x01, 0x02});

            var result2 = EvmEncoding.encodeEip1559Transaction(
                    1L, 5L,
                    BigInteger.valueOf(1_000_000_000L),
                    BigInteger.valueOf(20_000_000_000L),
                    BigInteger.valueOf(21_000L),
                    SOME_ADDRESS,
                    BigInteger.TEN,
                    new byte[]{0x01, 0x02});

            // then
            assertThat(result1).isEqualTo(result2);
        }

        @Test
        void shouldProduceDifferentOutputForDifferentChainIds() {
            // when
            var result1 = EvmEncoding.encodeEip1559Transaction(
                    1L, 0L,
                    BigInteger.ONE, BigInteger.TEN, BigInteger.valueOf(21_000L),
                    SOME_ADDRESS, BigInteger.ZERO, new byte[0]);

            var result137 = EvmEncoding.encodeEip1559Transaction(
                    137L, 0L,
                    BigInteger.ONE, BigInteger.TEN, BigInteger.valueOf(21_000L),
                    SOME_ADDRESS, BigInteger.ZERO, new byte[0]);

            // then
            assertThat(result1).isNotEqualTo(result137);
        }
    }

    @Nested
    class DecodeData {

        @Test
        void shouldReturnEmptyArrayForNull() {
            // when
            var result = EvmEncoding.decodeData(null);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        void shouldReturnEmptyArrayForZeroX() {
            // when
            var result = EvmEncoding.decodeData("0x");

            // then
            assertThat(result).isEmpty();
        }

        @Test
        void shouldReturnEmptyArrayForEmptyString() {
            // when
            var result = EvmEncoding.decodeData("");

            // then
            assertThat(result).isEmpty();
        }

        @Test
        void shouldDecodeHexWithPrefix() {
            // when
            var result = EvmEncoding.decodeData("0xdeadbeef");

            // then
            assertThat(HexFormat.of().formatHex(result)).isEqualTo("deadbeef");
        }

        @Test
        void shouldDecodeHexWithoutPrefix() {
            // when
            var result = EvmEncoding.decodeData("cafebabe");

            // then
            assertThat(HexFormat.of().formatHex(result)).isEqualTo("cafebabe");
        }
    }
}
