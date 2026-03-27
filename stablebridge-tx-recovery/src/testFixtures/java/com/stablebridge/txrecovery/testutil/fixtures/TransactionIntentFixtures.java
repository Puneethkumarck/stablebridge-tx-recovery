package com.stablebridge.txrecovery.testutil.fixtures;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;

import com.stablebridge.txrecovery.domain.transaction.model.SubmissionStrategy;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionIntent;

public final class TransactionIntentFixtures {

    private TransactionIntentFixtures() {}


    public static final String SOME_INTENT_ID = "intent-001";
    public static final String SOME_CHAIN = "ethereum";
    public static final String SOME_TO_ADDRESS = "0xrecipient0000000000000000000000000000001";
    public static final String SOME_TOKEN = "USDC";
    public static final String SOME_TOKEN_CONTRACT = "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48";

    public static final TransactionIntent SOME_SEQUENTIAL_INTENT = TransactionIntent.builder()
            .intentId(SOME_INTENT_ID)
            .chain(SOME_CHAIN)
            .toAddress(SOME_TO_ADDRESS)
            .amount(new BigDecimal("100.00"))
            .token(SOME_TOKEN)
            .tokenDecimals(6)
            .rawAmount(BigInteger.valueOf(100_000_000L))
            .tokenContractAddress(SOME_TOKEN_CONTRACT)
            .strategy(SubmissionStrategy.SEQUENTIAL)
            .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
            .build();

    public static final TransactionIntent SOME_PIPELINED_INTENT = SOME_SEQUENTIAL_INTENT.toBuilder()
            .intentId("intent-002")
            .strategy(SubmissionStrategy.PIPELINED)
            .build();

    public static final TransactionIntent SOME_SOLANA_SEQUENTIAL_INTENT = TransactionIntent.builder()
            .intentId("intent-solana-001")
            .chain("solana-mainnet")
            .toAddress("RecipientSol11111111111111111111111111111111")
            .amount(new BigDecimal("100.00"))
            .token("USDC")
            .tokenDecimals(6)
            .rawAmount(BigInteger.valueOf(100_000_000L))
            .tokenContractAddress("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v")
            .strategy(SubmissionStrategy.SEQUENTIAL)
            .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
            .build();
}
