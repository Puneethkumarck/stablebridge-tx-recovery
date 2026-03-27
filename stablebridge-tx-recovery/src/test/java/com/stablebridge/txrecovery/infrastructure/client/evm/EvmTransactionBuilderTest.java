package com.stablebridge.txrecovery.infrastructure.client.evm;

import static com.stablebridge.txrecovery.testutil.fixtures.EvmTransactionFixtures.SOME_CHAIN;
import static com.stablebridge.txrecovery.testutil.fixtures.EvmTransactionFixtures.SOME_CHAIN_ID;
import static com.stablebridge.txrecovery.testutil.fixtures.EvmTransactionFixtures.SOME_DENOMINATION;
import static com.stablebridge.txrecovery.testutil.fixtures.EvmTransactionFixtures.SOME_ESTIMATED_COST;
import static com.stablebridge.txrecovery.testutil.fixtures.EvmTransactionFixtures.SOME_ESTIMATED_GAS;
import static com.stablebridge.txrecovery.testutil.fixtures.EvmTransactionFixtures.SOME_FROM_ADDRESS;
import static com.stablebridge.txrecovery.testutil.fixtures.EvmTransactionFixtures.SOME_MAX_PRIORITY_FEE_PER_GAS;
import static com.stablebridge.txrecovery.testutil.fixtures.EvmTransactionFixtures.SOME_RAW_AMOUNT;
import static com.stablebridge.txrecovery.testutil.fixtures.EvmTransactionFixtures.SOME_TOKEN_CONTRACT;
import static com.stablebridge.txrecovery.testutil.fixtures.EvmTransactionFixtures.SOME_TO_ADDRESS;
import static com.stablebridge.txrecovery.testutil.fixtures.EvmTransactionFixtures.someEvmSubmissionResource;
import static com.stablebridge.txrecovery.testutil.fixtures.EvmTransactionFixtures.someFeeEstimate;
import static com.stablebridge.txrecovery.testutil.fixtures.EvmTransactionFixtures.someTransactionIntent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HexFormat;
import java.util.List;
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
class EvmTransactionBuilderTest {

    @Mock
    private EvmRpcClient rpcClient;

    @Mock
    private FeeOracle feeOracle;

    private EvmTransactionBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new EvmTransactionBuilder(rpcClient, feeOracle, SOME_CHAIN_ID);
    }

    private void stubDependencies() {
        var abiData = Erc20AbiEncoder.encodeTransfer(SOME_TO_ADDRESS, SOME_RAW_AMOUNT);
        var dataHex = "0x" + HexFormat.of().formatHex(abiData);

        given(feeOracle.estimate(SOME_CHAIN, FeeUrgency.MEDIUM)).willReturn(someFeeEstimate());
        given(rpcClient.estimateGas(SOME_FROM_ADDRESS, SOME_TOKEN_CONTRACT, dataHex, "0x0"))
                .willReturn(SOME_ESTIMATED_GAS);
    }

    @Nested
    class Build {

        @Test
        void shouldBuildErc20TransferTransactionWithCorrectFields() {
            stubDependencies();

            var expected = UnsignedTransaction.builder()
                    .intentId("intent-001")
                    .chain(SOME_CHAIN)
                    .fromAddress(SOME_FROM_ADDRESS)
                    .toAddress(SOME_TOKEN_CONTRACT)
                    .payload(new byte[0])
                    .feeEstimate(someFeeEstimate())
                    .metadata(Map.of(
                            "nonce", "5",
                            "gasLimit", "78000",
                            "type", "0x02",
                            "chainId", "1"))
                    .build();

            var result = builder.build(someTransactionIntent(), someEvmSubmissionResource());

            assertThat(result)
                    .usingRecursiveComparison()
                    .ignoringFields("payload")
                    .isEqualTo(expected);
        }

        @Test
        void shouldEncodeAbiDataForErc20Transfer() {
            stubDependencies();

            var result = builder.build(someTransactionIntent(), someEvmSubmissionResource());

            var payloadHex = HexFormat.of().formatHex(result.payload());
            assertThat(payloadHex).contains("a9059cbb");
        }

        @Test
        void shouldApplyGasLimitSafetyMarginOf1Point2x() {
            stubDependencies();

            var result = builder.build(someTransactionIntent(), someEvmSubmissionResource());

            assertThat(result.metadata().get("gasLimit")).isEqualTo("78000");
        }

        @Test
        void shouldUseMediumFeeUrgencyFromOracle() {
            stubDependencies();

            builder.build(someTransactionIntent(), someEvmSubmissionResource());

            then(feeOracle).should().estimate(SOME_CHAIN, FeeUrgency.MEDIUM);
        }

        @Test
        void shouldPrefixPayloadWithEip1559TypeByte() {
            stubDependencies();

            var result = builder.build(someTransactionIntent(), someEvmSubmissionResource());

            assertThat(result.payload()[0]).isEqualTo((byte) 0x02);
        }

        @Test
        void shouldRecordFeeEstimateInTransaction() {
            stubDependencies();

            var result = builder.build(someTransactionIntent(), someEvmSubmissionResource());

            assertThat(result.feeEstimate())
                    .usingRecursiveComparison()
                    .isEqualTo(someFeeEstimate());
        }
    }

    @Nested
    class AbiEncoding {

        @Test
        void shouldEncodeTransferWithCorrectFunctionSelector() {
            var expectedSelector = HexFormat.of().parseHex("a9059cbb");

            var result = Erc20AbiEncoder.encodeTransfer(SOME_TO_ADDRESS, SOME_RAW_AMOUNT);

            var selector = new byte[4];
            System.arraycopy(result, 0, selector, 0, 4);
            assertThat(selector).isEqualTo(expectedSelector);
        }

        @Test
        void shouldEncodeRecipientAddressIn32Bytes() {
            var result = Erc20AbiEncoder.encodeTransfer(SOME_TO_ADDRESS, SOME_RAW_AMOUNT);

            var addressWord = new byte[32];
            System.arraycopy(result, 4, addressWord, 0, 32);
            var addressHex = HexFormat.of().formatHex(addressWord);
            assertThat(addressHex).isEqualTo("0000000000000000000000002222222222222222222222222222222222222222");
        }

        @Test
        void shouldEncodeAmountIn32Bytes() {
            var result = Erc20AbiEncoder.encodeTransfer(SOME_TO_ADDRESS, SOME_RAW_AMOUNT);

            var amountWord = new byte[32];
            System.arraycopy(result, 36, amountWord, 0, 32);
            var decoded = new BigInteger(1, amountWord);
            assertThat(decoded).isEqualTo(SOME_RAW_AMOUNT);
        }

        @Test
        void shouldProduceSixtyEightBytesTotal() {
            var result = Erc20AbiEncoder.encodeTransfer(SOME_TO_ADDRESS, SOME_RAW_AMOUNT);

            assertThat(result).hasSize(68);
        }
    }

    @Nested
    class RlpEncoding {

        @Test
        void shouldEncodeSingleByteBelowThreshold() {
            var input = new byte[] {0x42};

            var result = RlpEncoder.encode(input);

            assertThat(result).isEqualTo(new byte[] {0x42});
        }

        @Test
        void shouldEncodeShortByteArray() {
            var input = new byte[] {(byte) 0x80, (byte) 0x90};

            var result = RlpEncoder.encode(input);

            assertThat(result).isEqualTo(new byte[] {(byte) 0x82, (byte) 0x80, (byte) 0x90});
        }

        @Test
        void shouldEncodeEmptyBytesForZeroBigInteger() {
            var result = RlpEncoder.encode(BigInteger.ZERO);

            assertThat(result).isEqualTo(new byte[] {(byte) 0x80});
        }

        @Test
        void shouldEncodeListOfElements() {
            List<Object> items = List.of(
                    BigInteger.valueOf(1),
                    BigInteger.valueOf(2));

            var result = RlpEncoder.encode(items);

            assertThat(result).isEqualTo(new byte[] {(byte) 0xc2, 0x01, 0x02});
        }
    }

    @Nested
    class InputValidation {

        @Test
        void shouldRejectAddressShorterThan20Bytes() {
            // given
            var shortAddress = "0x1234567890abcdef1234567890abcdef1234"; // 19 bytes

            // when / then
            assertThatThrownBy(() -> Erc20AbiEncoder.encodeTransfer(shortAddress, SOME_RAW_AMOUNT))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("20 bytes");
        }

        @Test
        void shouldRejectAddressLongerThan20Bytes() {
            // given
            var longAddress = "0x1234567890abcdef1234567890abcdef1234567890aa"; // 21 bytes

            // when / then
            assertThatThrownBy(() -> Erc20AbiEncoder.encodeTransfer(longAddress, SOME_RAW_AMOUNT))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("20 bytes");
        }

        @Test
        void shouldRejectNegativeAmount() {
            // given
            var negativeAmount = BigInteger.valueOf(-1);

            // when / then
            assertThatThrownBy(() -> Erc20AbiEncoder.encodeTransfer(SOME_TO_ADDRESS, negativeAmount))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("non-negative");
        }

        @Test
        void shouldRejectAmountExceedingUint256() {
            // given
            var tooLarge = BigInteger.TWO.pow(256);

            // when / then
            assertThatThrownBy(() -> Erc20AbiEncoder.encodeTransfer(SOME_TO_ADDRESS, tooLarge))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("uint256");
        }

        @Test
        void shouldRejectNullMaxFeePerGas() {
            // given
            var badFeeEstimate = FeeEstimate.builder()
                    .maxFeePerGas(null)
                    .maxPriorityFeePerGas(SOME_MAX_PRIORITY_FEE_PER_GAS)
                    .estimatedCost(SOME_ESTIMATED_COST)
                    .denomination(SOME_DENOMINATION)
                    .urgency(FeeUrgency.MEDIUM)
                    .build();

            given(feeOracle.estimate(SOME_CHAIN, FeeUrgency.MEDIUM)).willReturn(badFeeEstimate);

            // when / then
            assertThatThrownBy(() -> builder.build(someTransactionIntent(), someEvmSubmissionResource()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("maxFeePerGas");
        }

        @Test
        void shouldRejectPriorityFeeExceedingMaxFee() {
            // given
            var badFeeEstimate = FeeEstimate.builder()
                    .maxFeePerGas(new BigDecimal("1000000000"))
                    .maxPriorityFeePerGas(new BigDecimal("2000000000"))
                    .estimatedCost(SOME_ESTIMATED_COST)
                    .denomination(SOME_DENOMINATION)
                    .urgency(FeeUrgency.MEDIUM)
                    .build();

            given(feeOracle.estimate(SOME_CHAIN, FeeUrgency.MEDIUM)).willReturn(badFeeEstimate);

            // when / then
            assertThatThrownBy(() -> builder.build(someTransactionIntent(), someEvmSubmissionResource()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("exceeds maxFeePerGas");
        }
    }
}
