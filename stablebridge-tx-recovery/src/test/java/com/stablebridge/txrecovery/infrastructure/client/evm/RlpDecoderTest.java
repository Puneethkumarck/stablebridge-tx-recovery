package com.stablebridge.txrecovery.infrastructure.client.evm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RlpDecoderTest {

    @Nested
    class DecodeList {

        @Test
        void shouldDecodeEmptyList() {
            // given
            var encoded = RlpEncoder.encode(List.of());

            // when
            var result = RlpDecoder.decodeList(encoded);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        void shouldDecodeSingleElementList() {
            // given
            var encoded = RlpEncoder.encode(List.of(new byte[]{0x42}));

            // when
            var result = RlpDecoder.decodeList(encoded);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst()).isEqualTo(new byte[]{0x42});
        }

        @Test
        void shouldDecodeMultipleElements() {
            // given
            var encoded = RlpEncoder.encode(List.of(
                    BigInteger.valueOf(1),
                    BigInteger.valueOf(255),
                    new byte[]{0x01, 0x02, 0x03}));

            // when
            var result = RlpDecoder.decodeList(encoded);

            // then
            assertThat(result).hasSize(3);
            assertThat(new BigInteger(1, result.get(0))).isEqualTo(BigInteger.ONE);
            assertThat(new BigInteger(1, result.get(1))).isEqualTo(BigInteger.valueOf(255));
            assertThat(result.get(2)).isEqualTo(new byte[]{0x01, 0x02, 0x03});
        }

        @Test
        void shouldDecodeEmptyByteArrayElement() {
            // given
            var encoded = RlpEncoder.encode(List.of(
                    BigInteger.ZERO,
                    new byte[0]));

            // when
            var result = RlpDecoder.decodeList(encoded);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0)).isEmpty();
            assertThat(result.get(1)).isEmpty();
        }

        @Test
        void shouldRoundTripNumericFields() {
            // given
            var chainId = BigInteger.valueOf(11155111);
            var nonce = BigInteger.valueOf(42);
            var maxPriorityFee = BigInteger.valueOf(1_500_000_000L);
            var maxFee = BigInteger.valueOf(30_000_000_000L);
            var gasLimit = BigInteger.valueOf(65_000);
            var to = new byte[20];
            to[19] = 0x01;
            var data = new byte[]{0x01, 0x02, 0x03, 0x04};

            var fields = List.<Object>of(
                    chainId, nonce, maxPriorityFee, maxFee, gasLimit,
                    to, BigInteger.ZERO, data);

            var encoded = RlpEncoder.encode(fields);

            // when
            var decoded = RlpDecoder.decodeList(encoded);

            // then
            assertThat(decoded).hasSize(8);
            assertThat(new BigInteger(1, decoded.get(0))).isEqualTo(chainId);
            assertThat(new BigInteger(1, decoded.get(1))).isEqualTo(nonce);
            assertThat(new BigInteger(1, decoded.get(2))).isEqualTo(maxPriorityFee);
            assertThat(new BigInteger(1, decoded.get(3))).isEqualTo(maxFee);
            assertThat(new BigInteger(1, decoded.get(4))).isEqualTo(gasLimit);
            assertThat(decoded.get(5)).isEqualTo(to);
            assertThat(decoded.get(6)).isEmpty();
            assertThat(decoded.get(7)).isEqualTo(data);
        }

        @Test
        void shouldThrowWhenNotAList() {
            // given
            var encoded = RlpEncoder.encode(new byte[]{0x42});

            // when/then
            assertThatThrownBy(() -> RlpDecoder.decodeList(encoded))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Expected RLP list");
        }
    }
}
