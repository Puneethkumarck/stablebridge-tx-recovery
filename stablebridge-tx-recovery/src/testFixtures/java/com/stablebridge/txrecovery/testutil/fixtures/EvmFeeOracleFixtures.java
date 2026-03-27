package com.stablebridge.txrecovery.testutil.fixtures;

import static lombok.AccessLevel.PRIVATE;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import com.stablebridge.txrecovery.infrastructure.client.evm.EvmChainProperties;
import com.stablebridge.txrecovery.infrastructure.client.evm.EvmFeeHistory;
import com.stablebridge.txrecovery.infrastructure.client.evm.EvmFeeOracleFactory;
import com.stablebridge.txrecovery.infrastructure.client.evm.EvmTransaction;

import lombok.NoArgsConstructor;

@NoArgsConstructor(access = PRIVATE)
public final class EvmFeeOracleFixtures {

    public static final String SOME_CHAIN = "ethereum";
    public static final BigDecimal SOME_MAX_FEE_CAP_GWEI = new BigDecimal("200");
    public static final Duration SOME_BLOCK_TIME = Duration.ofSeconds(12);
    public static final BigDecimal SOME_SAFETY_CAP_WEI =
            SOME_MAX_FEE_CAP_GWEI.multiply(BigDecimal.valueOf(1_000_000_000L));

    public static final String SOME_BASE_FEE_HEX = "0x3b9aca00";
    public static final BigDecimal SOME_BASE_FEE_WEI = new BigDecimal("1000000000");

    public static final List<Float> SOME_REWARD_PERCENTILES = List.of(25.0f, 50.0f, 75.0f, 95.0f);

    public static final EvmChainProperties SOME_ETHEREUM_PROPERTIES = EvmChainProperties.builder()
            .chain(SOME_CHAIN)
            .maxFeeCapGwei(SOME_MAX_FEE_CAP_GWEI)
            .blockTime(SOME_BLOCK_TIME)
            .build();

    public static final EvmChainProperties SOME_BASE_PROPERTIES = EvmChainProperties.builder()
            .chain("base")
            .maxFeeCapGwei(new BigDecimal("5"))
            .blockTime(Duration.ofSeconds(2))
            .build();

    public static final EvmChainProperties SOME_POLYGON_PROPERTIES = EvmChainProperties.builder()
            .chain("polygon")
            .maxFeeCapGwei(new BigDecimal("500"))
            .blockTime(Duration.ofSeconds(2))
            .build();

    public static final EvmFeeHistory SOME_FEE_HISTORY = EvmFeeHistory.builder()
            .oldestBlock("0xa")
            .baseFeePerGas(List.of(SOME_BASE_FEE_HEX, SOME_BASE_FEE_HEX, SOME_BASE_FEE_HEX))
            .gasUsedRatio(List.of(0.5f, 0.5f))
            .reward(List.of(
                    List.of("0x59682f00", "0x77359400", "0xb2d05e00", "0x174876e800"),
                    List.of("0x59682f00", "0x77359400", "0xb2d05e00", "0x174876e800")))
            .build();

    public static final EvmFeeHistory SOME_HIGH_BASE_FEE_HISTORY = EvmFeeHistory.builder()
            .oldestBlock("0xa")
            .baseFeePerGas(List.of("0x12a05f2000", "0x12a05f2000", "0x12a05f2000"))
            .gasUsedRatio(List.of(0.8f, 0.8f))
            .reward(List.of(
                    List.of("0x59682f00", "0x77359400", "0xb2d05e00", "0x174876e800"),
                    List.of("0x59682f00", "0x77359400", "0xb2d05e00", "0x174876e800")))
            .build();

    public static final EvmFeeHistory SOME_POLYGON_FEE_HISTORY = EvmFeeHistory.builder()
            .oldestBlock("0xa")
            .baseFeePerGas(List.of("0xe8d4a51000", "0xe8d4a51000", "0xe8d4a51000"))
            .gasUsedRatio(List.of(0.9f, 0.9f))
            .reward(List.of(
                    List.of("0x174876e800", "0x2540be400", "0x3b9aca00", "0x4a817c800"),
                    List.of("0x174876e800", "0x2540be400", "0x3b9aca00", "0x4a817c800")))
            .build();

    public static final EvmTransaction SOME_EIP1559_TRANSACTION = EvmTransaction.builder()
            .hash("0xoriginal")
            .nonce("0x1")
            .blockHash("0xblock")
            .blockNumber("0xa")
            .transactionIndex("0x0")
            .from("0xsender")
            .to("0xreceiver")
            .value("0x0")
            .gas("0x5208")
            .maxFeePerGas("0x12a05f2000")
            .maxPriorityFeePerGas("0x77359400")
            .input("0x")
            .type("0x2")
            .build();

    public static final EvmTransaction SOME_LOW_FEE_TRANSACTION = EvmTransaction.builder()
            .hash("0xoriginal")
            .nonce("0x1")
            .blockHash("0xblock")
            .blockNumber("0xa")
            .transactionIndex("0x0")
            .from("0xsender")
            .to("0xreceiver")
            .value("0x0")
            .gas("0x5208")
            .maxFeePerGas("0x3b9aca00")
            .maxPriorityFeePerGas("0x59682f00")
            .input("0x")
            .type("0x2")
            .build();

    public static final EvmTransaction SOME_LEGACY_TRANSACTION = EvmTransaction.builder()
            .hash("0xlegacy")
            .nonce("0x1")
            .blockHash("0xblock")
            .blockNumber("0xa")
            .transactionIndex("0x0")
            .from("0xsender")
            .to("0xreceiver")
            .value("0x0")
            .gas("0x5208")
            .gasPrice("0x4a817c800")
            .input("0x")
            .type("0x0")
            .build();

    public static final EvmFeeOracleFactory.ChainInput SOME_ETHEREUM_CHAIN_INPUT =
            EvmFeeOracleFactory.ChainInput.builder()
                    .name("ethereum")
                    .rpcUrls(List.of("http://localhost:8545"))
                    .maxFeeCapGwei(new BigDecimal("200"))
                    .blockTime(Duration.ofSeconds(12))
                    .rpcTimeout(Duration.ofSeconds(5))
                    .rateLimitPerSecond(25)
                    .rateLimitBurst(50)
                    .build();

    public static final EvmFeeOracleFactory.ChainInput SOME_BASE_CHAIN_INPUT =
            EvmFeeOracleFactory.ChainInput.builder()
                    .name("base")
                    .rpcUrls(List.of("http://localhost:8546"))
                    .maxFeeCapGwei(new BigDecimal("5"))
                    .blockTime(Duration.ofSeconds(2))
                    .rpcTimeout(Duration.ofSeconds(5))
                    .rateLimitPerSecond(25)
                    .rateLimitBurst(50)
                    .build();

    public static final EvmFeeOracleFactory.ChainInput SOME_POLYGON_CHAIN_INPUT =
            EvmFeeOracleFactory.ChainInput.builder()
                    .name("polygon")
                    .rpcUrls(List.of("http://localhost:8547"))
                    .maxFeeCapGwei(new BigDecimal("500"))
                    .blockTime(Duration.ofSeconds(2))
                    .rpcTimeout(Duration.ofSeconds(5))
                    .rateLimitPerSecond(25)
                    .rateLimitBurst(50)
                    .build();
}
