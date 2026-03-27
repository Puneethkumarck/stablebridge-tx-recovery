package com.stablebridge.txrecovery.infrastructure.client.evm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stablebridge.txrecovery.domain.address.model.AddressTier;
import com.stablebridge.txrecovery.domain.recovery.model.FeeEstimate;
import com.stablebridge.txrecovery.domain.recovery.model.FeeUrgency;
import com.stablebridge.txrecovery.domain.recovery.port.FeeOracle;
import com.stablebridge.txrecovery.domain.transaction.model.EvmSubmissionResource;
import com.stablebridge.txrecovery.domain.transaction.model.SubmissionStrategy;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionIntent;
import com.stablebridge.txrecovery.domain.transaction.model.UnsignedTransaction;

@ExtendWith(MockitoExtension.class)
class EvmTransactionBuilderTest {

    private static final long CHAIN_ID = 1L;
    private static final String CHAIN = "ethereum";
    private static final String FROM_ADDRESS = "0x1111111111111111111111111111111111111111";
    private static final String TO_ADDRESS = "0x2222222222222222222222222222222222222222";
    private static final String TOKEN_CONTRACT = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48";
    private static final BigDecimal AMOUNT = new BigDecimal("1000000");
    private static final BigInteger RAW_AMOUNT = BigInteger.valueOf(1000000);
    private static final long NONCE = 5L;
    private static final BigDecimal MAX_FEE_PER_GAS = new BigDecimal("30000000000");
    private static final BigDecimal MAX_PRIORITY_FEE_PER_GAS = new BigDecimal("2000000000");
    private static final BigDecimal ESTIMATED_COST = new BigDecimal("0.001");
    private static final String DENOMINATION = "ETH";
    private static final BigInteger ESTIMATED_GAS = BigInteger.valueOf(65000);


    @Mock
    private EvmRpcClient rpcClient;

    @Mock
    private FeeOracle feeOracle;

    private EvmTransactionBuilder builder;

    private TransactionIntent intent;
    private EvmSubmissionResource resource;
    private FeeEstimate feeEstimate;

    @BeforeEach
    void setUp() {
        builder = new EvmTransactionBuilder(rpcClient, feeOracle, CHAIN_ID);

        intent = TransactionIntent.builder()
                .intentId("intent-001")
                .chain(CHAIN)
                .toAddress(TO_ADDRESS)
                .amount(AMOUNT)
                .token("USDC")
                .tokenDecimals(6)
                .rawAmount(RAW_AMOUNT)
                .tokenContractAddress(TOKEN_CONTRACT)
                .strategy(SubmissionStrategy.SEQUENTIAL)
                .createdAt(Instant.parse("2026-03-27T10:00:00Z"))
                .build();

        resource = EvmSubmissionResource.builder()
                .chain(CHAIN)
                .fromAddress(FROM_ADDRESS)
                .nonce(NONCE)
                .tier(AddressTier.HOT)
                .build();

        feeEstimate = FeeEstimate.builder()
                .maxFeePerGas(MAX_FEE_PER_GAS)
                .maxPriorityFeePerGas(MAX_PRIORITY_FEE_PER_GAS)
                .estimatedCost(ESTIMATED_COST)
                .denomination(DENOMINATION)
                .urgency(FeeUrgency.MEDIUM)
                .build();
    }

    private void stubDependencies() {
        var abiData = Erc20AbiEncoder.encodeTransfer(TO_ADDRESS, RAW_AMOUNT);
        var dataHex = "0x" + HexFormat.of().formatHex(abiData);

        given(feeOracle.estimate(CHAIN, FeeUrgency.MEDIUM)).willReturn(feeEstimate);
        given(rpcClient.estimateGas(FROM_ADDRESS, TOKEN_CONTRACT, dataHex, "0x0"))
                .willReturn(ESTIMATED_GAS);
    }

    @Nested
    class Build {

        @Test
        void shouldBuildErc20TransferTransactionWithCorrectFields() {
            // given
            stubDependencies();

            var expected = UnsignedTransaction.builder()
                    .intentId("intent-001")
                    .chain(CHAIN)
                    .fromAddress(FROM_ADDRESS)
                    .toAddress(TOKEN_CONTRACT)
                    .payload(new byte[0])
                    .feeEstimate(feeEstimate)
                    .metadata(Map.of(
                            "nonce", "5",
                            "gasLimit", "78000",
                            "type", "0x02",
                            "chainId", "1"))
                    .build();

            // when
            var result = builder.build(intent, resource);

            // then
            assertThat(result)
                    .usingRecursiveComparison()
                    .ignoringFields("payload")
                    .isEqualTo(expected);
        }

        @Test
        void shouldEncodeAbiDataForErc20Transfer() {
            // given
            stubDependencies();

            // when
            var result = builder.build(intent, resource);

            // then
            var payload = result.payload();
            var abiDataStart = findAbiDataInPayload(payload);
            assertThat(abiDataStart).isNotEmpty();
        }

        @Test
        void shouldApplyGasLimitSafetyMarginOf1Point2x() {
            // given
            stubDependencies();

            // when
            var result = builder.build(intent, resource);

            // then
            assertThat(result.metadata().get("gasLimit")).isEqualTo("78000");
        }

        @Test
        void shouldUseMediumFeeUrgencyFromOracle() {
            // given
            stubDependencies();

            // when
            builder.build(intent, resource);

            // then
            then(feeOracle).should().estimate(CHAIN, FeeUrgency.MEDIUM);
        }

        @Test
        void shouldPrefixPayloadWithEip1559TypeByte() {
            // given
            stubDependencies();

            // when
            var result = builder.build(intent, resource);

            // then
            assertThat(result.payload()[0]).isEqualTo((byte) 0x02);
        }

        @Test
        void shouldRecordFeeEstimateInTransaction() {
            // given
            stubDependencies();

            // when
            var result = builder.build(intent, resource);

            // then
            assertThat(result.feeEstimate())
                    .usingRecursiveComparison()
                    .isEqualTo(feeEstimate);
        }

        private java.util.Optional<Integer> findAbiDataInPayload(byte[] payload) {
            var selector = HexFormat.of().parseHex("a9059cbb");
            for (var i = 1; i < payload.length - 3; i++) {
                if (payload[i] == selector[0]
                        && payload[i + 1] == selector[1]
                        && payload[i + 2] == selector[2]
                        && payload[i + 3] == selector[3]) {
                    return java.util.Optional.of(i);
                }
            }
            return java.util.Optional.empty();
        }
    }

    @Nested
    class AbiEncoding {

        @Test
        void shouldEncodeTransferWithCorrectFunctionSelector() {
            // given
            var expectedSelector = HexFormat.of().parseHex("a9059cbb");

            // when
            var result = Erc20AbiEncoder.encodeTransfer(TO_ADDRESS, RAW_AMOUNT);

            // then
            var selector = new byte[4];
            System.arraycopy(result, 0, selector, 0, 4);
            assertThat(selector).isEqualTo(expectedSelector);
        }

        @Test
        void shouldEncodeRecipientAddressIn32Bytes() {
            // when
            var result = Erc20AbiEncoder.encodeTransfer(TO_ADDRESS, RAW_AMOUNT);

            // then
            var addressWord = new byte[32];
            System.arraycopy(result, 4, addressWord, 0, 32);
            var addressHex = HexFormat.of().formatHex(addressWord);
            assertThat(addressHex).isEqualTo("0000000000000000000000002222222222222222222222222222222222222222");
        }

        @Test
        void shouldEncodeAmountIn32Bytes() {
            // when
            var result = Erc20AbiEncoder.encodeTransfer(TO_ADDRESS, RAW_AMOUNT);

            // then
            var amountWord = new byte[32];
            System.arraycopy(result, 36, amountWord, 0, 32);
            var decoded = new BigInteger(1, amountWord);
            assertThat(decoded).isEqualTo(RAW_AMOUNT);
        }

        @Test
        void shouldProduceSixtyEightBytesTotal() {
            // when
            var result = Erc20AbiEncoder.encodeTransfer(TO_ADDRESS, RAW_AMOUNT);

            // then
            assertThat(result).hasSize(68);
        }
    }

    @Nested
    class RlpEncoding {

        @Test
        void shouldEncodeSingleByteBelowThreshold() {
            // given
            var input = new byte[] {0x42};

            // when
            var result = RlpEncoder.encode(input);

            // then
            assertThat(result).isEqualTo(new byte[] {0x42});
        }

        @Test
        void shouldEncodeShortByteArray() {
            // given
            var input = new byte[] {(byte) 0x80, (byte) 0x90};

            // when
            var result = RlpEncoder.encode(input);

            // then
            assertThat(result).isEqualTo(new byte[] {(byte) 0x82, (byte) 0x80, (byte) 0x90});
        }

        @Test
        void shouldEncodeEmptyBytesForZeroBigInteger() {
            // when
            var result = RlpEncoder.encode(BigInteger.ZERO);

            // then
            assertThat(result).isEqualTo(new byte[] {(byte) 0x80});
        }

        @Test
        void shouldEncodeListOfElements() {
            // given
            List<Object> items = List.of(
                    BigInteger.valueOf(1),
                    BigInteger.valueOf(2));

            // when
            var result = RlpEncoder.encode(items);

            // then
            assertThat(result).isEqualTo(new byte[] {(byte) 0xc2, 0x01, 0x02});
        }
    }
}
