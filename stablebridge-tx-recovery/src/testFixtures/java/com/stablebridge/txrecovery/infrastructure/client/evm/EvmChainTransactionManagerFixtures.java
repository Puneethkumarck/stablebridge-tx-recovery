package com.stablebridge.txrecovery.infrastructure.client.evm;

import static lombok.AccessLevel.PRIVATE;

import lombok.NoArgsConstructor;

@NoArgsConstructor(access = PRIVATE)
public final class EvmChainTransactionManagerFixtures {

    public static final String SOME_CHAIN = "ethereum";
    public static final long SOME_CHAIN_ID = 1L;
    public static final long SOME_FINALITY_BLOCKS = 12L;
    public static final long SOME_STUCK_THRESHOLD_BLOCKS = 50L;
    public static final String SOME_TX_HASH = "0xabc123def456789";
    public static final String SOME_BROADCAST_TX_HASH = "0xbroadcast789";

    public static final EvmReceipt SOME_SUCCESS_RECEIPT = EvmReceipt.builder()
            .transactionHash(SOME_TX_HASH)
            .blockNumber("0x64")
            .status("0x1")
            .gasUsed("0x5208")
            .effectiveGasPrice("0x6fc23ac00")
            .build();

    public static final EvmReceipt SOME_FAILED_RECEIPT = EvmReceipt.builder()
            .transactionHash(SOME_TX_HASH)
            .blockNumber("0x64")
            .status("0x0")
            .gasUsed("0x5208")
            .effectiveGasPrice("0x6fc23ac00")
            .build();

    public static final EvmBlock SOME_LATEST_BLOCK_CONFIRMED = EvmBlock.builder()
            .number("0x6e")
            .hash("0xblockhash")
            .parentHash("0xparenthash")
            .timestamp("0x60000000")
            .gasLimit("0x1c9c380")
            .gasUsed("0xe4e1c0")
            .baseFeePerGas("0x3b9aca00")
            .build();

    public static final EvmBlock SOME_LATEST_BLOCK_FINALIZED = EvmBlock.builder()
            .number("0x70")
            .hash("0xblockhash")
            .parentHash("0xparenthash")
            .timestamp("0x60000000")
            .gasLimit("0x1c9c380")
            .gasUsed("0xe4e1c0")
            .baseFeePerGas("0x3b9aca00")
            .build();

    public static final EvmBlock SOME_CURRENT_BLOCK_FOR_PENDING = EvmBlock.builder()
            .number("0xc8")
            .hash("0xblockhash")
            .parentHash("0xparenthash")
            .timestamp("0x60000000")
            .gasLimit("0x1c9c380")
            .gasUsed("0xe4e1c0")
            .baseFeePerGas("0x3b9aca00")
            .build();

    public static final EvmBlock SOME_CURRENT_BLOCK_FOR_STUCK = EvmBlock.builder()
            .number("0x12c")
            .hash("0xblockhash")
            .parentHash("0xparenthash")
            .timestamp("0x60000000")
            .gasLimit("0x1c9c380")
            .gasUsed("0xe4e1c0")
            .baseFeePerGas("0x3b9aca00")
            .build();

    public static final EvmTransaction SOME_MEMPOOL_TX = EvmTransaction.builder()
            .hash(SOME_TX_HASH)
            .nonce("0x5")
            .from("0x1111111111111111111111111111111111111111")
            .to("0x2222222222222222222222222222222222222222")
            .value("0x0")
            .gas("0xfde8")
            .maxFeePerGas("0x6fc23ac00")
            .maxPriorityFeePerGas("0x77359400")
            .input("0xa9059cbb")
            .type("0x2")
            .build();
}
