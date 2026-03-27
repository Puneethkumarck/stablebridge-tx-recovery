package com.stablebridge.txrecovery.infrastructure.client.solana;

import static lombok.AccessLevel.PRIVATE;

import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;

import lombok.NoArgsConstructor;

@NoArgsConstructor(access = PRIVATE)
public final class SolanaFeeOracleFixtures {

    public static final String SOME_CHAIN = "solana-mainnet";
    public static final Duration SOME_BLOCK_TIME = Duration.ofMillis(400);
    public static final long SOME_MAX_PRIORITY_FEE_MICRO_LAMPORTS = 1_000_000L;

    public static final String TOKEN_PROGRAM_ID = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA";
    public static final String ATA_PROGRAM_ID = "ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL";
    public static final List<String> SOME_PROGRAM_ADDRESSES = List.of(TOKEN_PROGRAM_ID, ATA_PROGRAM_ID);

    public static final SolanaChainProperties SOME_SOLANA_PROPERTIES = SolanaChainProperties.builder()
            .chain(SOME_CHAIN)
            .maxPriorityFeeMicroLamports(SOME_MAX_PRIORITY_FEE_MICRO_LAMPORTS)
            .blockTime(SOME_BLOCK_TIME)
            .programAddresses(SOME_PROGRAM_ADDRESSES)
            .build();

    private static final List<Long> FEE_VALUES = List.of(
            100L, 200L, 300L, 500L, 800L, 1000L, 1200L, 1500L, 2000L, 2500L,
            3000L, 3500L, 4000L, 5000L, 6000L, 7000L, 8000L, 10000L, 15000L, 20000L);

    public static final List<SolanaPrioritizationFee> SOME_PRIORITIZATION_FEES = IntStream.range(0, FEE_VALUES.size())
            .mapToObj(i -> new SolanaPrioritizationFee(1000L + i, FEE_VALUES.get(i)))
            .toList();

    public static final List<SolanaPrioritizationFee> SOME_EMPTY_FEES = List.of();

    public static final List<SolanaPrioritizationFee> SOME_SINGLE_FEE =
            List.of(new SolanaPrioritizationFee(1000L, 5000L));
}
