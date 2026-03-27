package com.stablebridge.txrecovery.infrastructure.client.solana;

import static com.stablebridge.txrecovery.testutil.fixtures.SolanaTransactionFixtures.SOME_COMPUTE_UNIT_LIMIT;
import static com.stablebridge.txrecovery.testutil.fixtures.SolanaTransactionFixtures.SOME_COMPUTE_UNIT_PRICE;
import static com.stablebridge.txrecovery.testutil.fixtures.SolanaTransactionFixtures.SOME_DESTINATION_ADDRESS;
import static com.stablebridge.txrecovery.testutil.fixtures.SolanaTransactionFixtures.SOME_NONCE_ACCOUNT;
import static com.stablebridge.txrecovery.testutil.fixtures.SolanaTransactionFixtures.SOME_NONCE_VALUE;
import static com.stablebridge.txrecovery.testutil.fixtures.SolanaTransactionFixtures.SOME_SOLANA_CHAIN;
import static com.stablebridge.txrecovery.testutil.fixtures.SolanaTransactionFixtures.SOME_WALLET_ADDRESS;
import static com.stablebridge.txrecovery.testutil.fixtures.SolanaTransactionFixtures.someSolanaFeeEstimate;
import static com.stablebridge.txrecovery.testutil.fixtures.SolanaTransactionFixtures.someSolanaSubmissionResource;
import static com.stablebridge.txrecovery.testutil.fixtures.SolanaTransactionFixtures.someSolanaTransactionIntent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.math.BigDecimal;
import java.util.HexFormat;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stablebridge.txrecovery.domain.recovery.model.FeeEstimate;
import com.stablebridge.txrecovery.domain.recovery.model.FeeUrgency;
import com.stablebridge.txrecovery.domain.recovery.port.FeeOracle;
import com.stablebridge.txrecovery.domain.transaction.model.UnsignedTransaction;

@ExtendWith(MockitoExtension.class)
class SolanaTransactionBuilderTest {

    @Mock
    private FeeOracle feeOracle;

    private SolanaTransactionBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new SolanaTransactionBuilder(null, feeOracle, SOME_COMPUTE_UNIT_LIMIT);
    }

    private void stubFeeOracle() {
        given(feeOracle.estimate(SOME_SOLANA_CHAIN, FeeUrgency.MEDIUM))
                .willReturn(someSolanaFeeEstimate());
    }

    @Nested
    class Build {

        @Test
        void shouldBuildSplTransferTransactionWithCorrectFields() {
            // given
            stubFeeOracle();
            var expected = UnsignedTransaction.builder()
                    .intentId("solana-intent-001")
                    .chain(SOME_SOLANA_CHAIN)
                    .fromAddress(SOME_WALLET_ADDRESS)
                    .toAddress(SOME_DESTINATION_ADDRESS)
                    .payload(new byte[0])
                    .feeEstimate(someSolanaFeeEstimate())
                    .metadata(Map.of(
                            "nonceAccountAddress", SOME_NONCE_ACCOUNT,
                            "nonceValue", SOME_NONCE_VALUE,
                            "computeUnitLimit", String.valueOf(SOME_COMPUTE_UNIT_LIMIT),
                            "computeUnitPrice", String.valueOf(SOME_COMPUTE_UNIT_PRICE)))
                    .build();

            // when
            var result = builder.build(someSolanaTransactionIntent(), someSolanaSubmissionResource());

            // then
            assertThat(result)
                    .usingRecursiveComparison()
                    .ignoringFields("payload")
                    .isEqualTo(expected);
        }

        @Test
        void shouldProduceNonEmptyPayload() {
            // given
            stubFeeOracle();

            // when
            var result = builder.build(someSolanaTransactionIntent(), someSolanaSubmissionResource());

            // then
            assertThat(result.payload()).isNotEmpty();
        }

        @Test
        void shouldUseMediumFeeUrgencyFromOracle() {
            // given
            stubFeeOracle();

            // when
            builder.build(someSolanaTransactionIntent(), someSolanaSubmissionResource());

            // then
            then(feeOracle).should().estimate(SOME_SOLANA_CHAIN, FeeUrgency.MEDIUM);
        }

        @Test
        void shouldRecordFeeEstimateInTransaction() {
            // given
            stubFeeOracle();

            // when
            var result = builder.build(someSolanaTransactionIntent(), someSolanaSubmissionResource());

            // then
            assertThat(result.feeEstimate())
                    .usingRecursiveComparison()
                    .isEqualTo(someSolanaFeeEstimate());
        }

        @Test
        void shouldProduceDeterministicPayloadForSameInputs() {
            // given
            stubFeeOracle();

            // when
            var result1 = builder.build(someSolanaTransactionIntent(), someSolanaSubmissionResource());
            var result2 = builder.build(someSolanaTransactionIntent(), someSolanaSubmissionResource());

            // then
            assertThat(result1.payload()).isEqualTo(result2.payload());
        }
    }

    @Nested
    class MessageSerialization {

        @Test
        void shouldStartPayloadWithSignatureCountHeader() {
            // given
            stubFeeOracle();

            // when
            var result = builder.build(someSolanaTransactionIntent(), someSolanaSubmissionResource());

            // then
            assertThat(result.payload()[0]).isGreaterThan((byte) 0);
        }

        @Test
        void shouldContainRecentBlockhashInPayload() {
            // given
            stubFeeOracle();
            var nonceHex = HexFormat.of().formatHex(
                    SolanaTransactionBuilder.decodeBase58(SOME_NONCE_VALUE));

            // when
            var result = builder.build(someSolanaTransactionIntent(), someSolanaSubmissionResource());

            // then
            var payloadHex = HexFormat.of().formatHex(result.payload());
            assertThat(payloadHex).contains(nonceHex);
        }
    }

    @Nested
    class Base58Decoding {

        @Test
        void shouldDecode32BytePublicKey() {
            // when
            var result = SolanaTransactionBuilder.decodeBase58(SOME_WALLET_ADDRESS);

            // then
            assertThat(result).hasSize(32);
        }

        @Test
        void shouldDecodeSystemProgramToAllZeros() {
            // when
            var result = SolanaTransactionBuilder.decodeBase58("11111111111111111111111111111111");

            // then
            assertThat(result).isEqualTo(new byte[32]);
        }

        @Test
        void shouldRejectNullInput() {
            // when/then
            assertThatThrownBy(() -> SolanaTransactionBuilder.decodeBase58(null))
                    .isInstanceOf(SolanaRpcException.class)
                    .hasMessageContaining("null or empty");
        }

        @Test
        void shouldRejectEmptyInput() {
            // when/then
            assertThatThrownBy(() -> SolanaTransactionBuilder.decodeBase58(""))
                    .isInstanceOf(SolanaRpcException.class)
                    .hasMessageContaining("null or empty");
        }

        @Test
        void shouldRejectInvalidBase58Characters() {
            // when/then
            assertThatThrownBy(() -> SolanaTransactionBuilder.decodeBase58("InvalidBase580Address"))
                    .isInstanceOf(SolanaRpcException.class)
                    .hasMessageContaining("Invalid Base58 character");
        }
    }

    @Nested
    class CompactU16Encoding {

        @Test
        void shouldEncodeSingleByteForZero() {
            // when
            var result = SolanaTransactionBuilder.encodeCompactU16(0);

            // then
            assertThat(result).isEqualTo(new byte[] {0x00});
        }

        @Test
        void shouldEncodeSingleByteForMaxSingleByteValue() {
            // when
            var result = SolanaTransactionBuilder.encodeCompactU16(127);

            // then
            assertThat(result).isEqualTo(new byte[] {0x7F});
        }

        @Test
        void shouldEncodeTwoBytesForOneHundredTwentyEight() {
            // when
            var result = SolanaTransactionBuilder.encodeCompactU16(128);

            // then
            assertThat(result).isEqualTo(new byte[] {(byte) 0x80, 0x01});
        }

        @Test
        void shouldEncodeTwoBytesForMaxTwoByteValue() {
            // when
            var result = SolanaTransactionBuilder.encodeCompactU16(16383);

            // then
            assertThat(result).isEqualTo(new byte[] {(byte) 0xFF, 0x7F});
        }

        @Test
        void shouldEncodeThreeBytesForSixteenThreeEightyFour() {
            // when
            var result = SolanaTransactionBuilder.encodeCompactU16(16384);

            // then
            assertThat(result).isEqualTo(new byte[] {(byte) 0x80, (byte) 0x80, 0x01});
        }

        @Test
        void shouldRejectNegativeValues() {
            // when/then
            assertThatThrownBy(() -> SolanaTransactionBuilder.encodeCompactU16(-1))
                    .isInstanceOf(SolanaRpcException.class)
                    .hasMessageContaining("range 0..65535");
        }

        @Test
        void shouldRejectValuesAboveU16Max() {
            // when/then
            assertThatThrownBy(() -> SolanaTransactionBuilder.encodeCompactU16(65536))
                    .isInstanceOf(SolanaRpcException.class)
                    .hasMessageContaining("range 0..65535");
        }

        @Test
        void shouldEncodeMaxU16Value() {
            // when
            var result = SolanaTransactionBuilder.encodeCompactU16(65535);

            // then
            assertThat(result).isEqualTo(new byte[] {(byte) 0xFF, (byte) 0xFF, 0x03});
        }
    }

    @Nested
    class AtaDerivation {

        @Test
        void shouldDerive32ByteAta() {
            // given
            var wallet = SolanaTransactionBuilder.decodeBase58(SOME_WALLET_ADDRESS);
            var mint = SolanaTransactionBuilder.decodeBase58(
                    "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v");

            // when
            var result = SolanaTransactionBuilder.deriveAta(wallet, mint);

            // then
            assertThat(result).hasSize(32);
        }

        @Test
        void shouldProduceDeterministicAta() {
            // given
            var wallet = SolanaTransactionBuilder.decodeBase58(SOME_WALLET_ADDRESS);
            var mint = SolanaTransactionBuilder.decodeBase58(
                    "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v");

            // when
            var result1 = SolanaTransactionBuilder.deriveAta(wallet, mint);
            var result2 = SolanaTransactionBuilder.deriveAta(wallet, mint);

            // then
            assertThat(result1).isEqualTo(result2);
        }

        @Test
        void shouldProduceDifferentAtasForDifferentWallets() {
            // given
            var wallet1 = SolanaTransactionBuilder.decodeBase58(SOME_WALLET_ADDRESS);
            var wallet2 = SolanaTransactionBuilder.decodeBase58(SOME_DESTINATION_ADDRESS);
            var mint = SolanaTransactionBuilder.decodeBase58(
                    "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v");

            // when
            var ata1 = SolanaTransactionBuilder.deriveAta(wallet1, mint);
            var ata2 = SolanaTransactionBuilder.deriveAta(wallet2, mint);

            // then
            assertThat(ata1).isNotEqualTo(ata2);
        }
    }

    @Nested
    class InputValidation {

        @Test
        void shouldRejectNullTransactionIntent() {
            // when/then
            assertThatThrownBy(() -> builder.build(null, someSolanaSubmissionResource()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("TransactionIntent");
        }

        @Test
        void shouldRejectNullSubmissionResource() {
            // when/then
            assertThatThrownBy(() -> builder.build(someSolanaTransactionIntent(), null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("SolanaSubmissionResource");
        }

        @Test
        void shouldRejectNullComputeUnitPrice() {
            // given
            var badFeeEstimate = FeeEstimate.builder()
                    .computeUnitPrice(null)
                    .estimatedCost(new BigDecimal("0.000005"))
                    .denomination("SOL")
                    .urgency(FeeUrgency.MEDIUM)
                    .build();
            given(feeOracle.estimate(SOME_SOLANA_CHAIN, FeeUrgency.MEDIUM))
                    .willReturn(badFeeEstimate);

            // when/then
            assertThatThrownBy(() -> builder.build(someSolanaTransactionIntent(), someSolanaSubmissionResource()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("computeUnitPrice");
        }

        @Test
        void shouldRejectNegativeComputeUnitPrice() {
            // given
            var badFeeEstimate = FeeEstimate.builder()
                    .computeUnitPrice(BigDecimal.valueOf(-100))
                    .estimatedCost(new BigDecimal("0.000005"))
                    .denomination("SOL")
                    .urgency(FeeUrgency.MEDIUM)
                    .build();
            given(feeOracle.estimate(SOME_SOLANA_CHAIN, FeeUrgency.MEDIUM))
                    .willReturn(badFeeEstimate);

            // when/then
            assertThatThrownBy(() -> builder.build(someSolanaTransactionIntent(), someSolanaSubmissionResource()))
                    .isInstanceOf(SolanaRpcException.class)
                    .hasMessageContaining("non-negative");
        }
    }
}
