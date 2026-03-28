package com.stablebridge.txrecovery.infrastructure.client.evm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class EvmHexTest {

    @Nested
    class DecodeQuantity {

        @Test
        void shouldDecodeHexWithPrefix() {
            // when
            var result = EvmHex.decodeQuantity("0x3b9aca00");

            // then
            assertThat(result).isEqualTo(BigInteger.valueOf(1_000_000_000L));
        }

        @Test
        void shouldDecodeHexWithoutPrefix() {
            // when
            var result = EvmHex.decodeQuantity("3b9aca00");

            // then
            assertThat(result).isEqualTo(BigInteger.valueOf(1_000_000_000L));
        }

        @Test
        void shouldDecodeZero() {
            // when
            var result = EvmHex.decodeQuantity("0x0");

            // then
            assertThat(result).isEqualTo(BigInteger.ZERO);
        }

        @Test
        void shouldDecodeLargeValue() {
            // when
            var result = EvmHex.decodeQuantity("0xde0b6b3a7640000");

            // then
            assertThat(result).isEqualTo(new BigInteger("1000000000000000000"));
        }

        @Test
        void shouldThrowOnNull() {
            // when/then
            assertThatThrownBy(() -> EvmHex.decodeQuantity(null))
                    .isInstanceOf(EvmRpcException.class)
                    .hasMessageContaining("null");
        }
    }

    @Nested
    class EncodeQuantity {

        @Test
        void shouldEncodeZero() {
            // when
            var result = EvmHex.encodeQuantity(0);

            // then
            assertThat(result).isEqualTo("0x0");
        }

        @Test
        void shouldEncodePositiveValue() {
            // when
            var result = EvmHex.encodeQuantity(255);

            // then
            assertThat(result).isEqualTo("0xff");
        }

        @Test
        void shouldEncodeLargeValue() {
            // when
            var result = EvmHex.encodeQuantity(1_000_000L);

            // then
            assertThat(result).isEqualTo("0xf4240");
        }
    }

    @Nested
    class ParseEvmAddress {

        @Test
        void shouldParseAddressWithPrefix() {
            // when
            var result = EvmHex.parseEvmAddress("0x742d35Cc6634C0532925a3b844Bc9e7595f2bD18");

            // then
            assertThat(result).hasSize(20);
        }

        @Test
        void shouldParseAddressWithoutPrefix() {
            // when
            var result = EvmHex.parseEvmAddress("742d35Cc6634C0532925a3b844Bc9e7595f2bD18");

            // then
            assertThat(result).hasSize(20);
        }

        @Test
        void shouldThrowOnNullAddress() {
            // when/then
            assertThatThrownBy(() -> EvmHex.parseEvmAddress(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("null or blank");
        }

        @Test
        void shouldThrowOnBlankAddress() {
            // when/then
            assertThatThrownBy(() -> EvmHex.parseEvmAddress("   "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("null or blank");
        }

        @Test
        void shouldThrowOnWrongLengthAddress() {
            // when/then
            assertThatThrownBy(() -> EvmHex.parseEvmAddress("0xabcdef"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("20 bytes");
        }
    }
}
