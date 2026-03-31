package com.stablebridge.txrecovery.testutil.fixtures;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;

import com.stablebridge.txrecovery.api.model.SubmitBatchRequest;
import com.stablebridge.txrecovery.api.model.SubmitTransactionRequest;
import com.stablebridge.txrecovery.api.model.TransactionResponse;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionIntent;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionProjection;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionStatus;

public final class TransactionControllerFixtures {

    private TransactionControllerFixtures() {}

    public static final String SOME_TRANSACTION_ID = "tx-12345";
    public static final String SOME_INTENT_ID = "019576a0-e29b-7000-a716-446655440000";
    public static final String SOME_SECOND_INTENT_ID = "019576a0-e29b-7000-a716-446655440001";
    public static final String SOME_CHAIN = "ethereum";
    public static final String SOME_TO_ADDRESS = "0xrecipient0000000000000000000000000000001";
    public static final BigDecimal SOME_AMOUNT = new BigDecimal("100.00");
    public static final String SOME_TOKEN = "USDC";
    public static final String SOME_TOKEN_CONTRACT = "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48";

    public static final TransactionIntent SOME_TRANSACTION_INTENT = TransactionIntent.builder()
            .intentId(SOME_INTENT_ID)
            .chain(SOME_CHAIN)
            .toAddress(SOME_TO_ADDRESS)
            .amount(SOME_AMOUNT)
            .token(SOME_TOKEN)
            .tokenDecimals(6)
            .rawAmount(new BigInteger("100000000"))
            .tokenContractAddress(SOME_TOKEN_CONTRACT)
            .build();

    public static final TransactionProjection SOME_TRANSACTION_PROJECTION = TransactionProjection.builder()
            .transactionId(SOME_TRANSACTION_ID)
            .intentId(SOME_INTENT_ID)
            .chain(SOME_CHAIN)
            .status(TransactionStatus.RECEIVED)
            .toAddress(SOME_TO_ADDRESS)
            .amount(SOME_AMOUNT)
            .token(SOME_TOKEN)
            .retryCount(0)
            .submittedAt(Instant.parse("2026-01-01T00:00:00Z"))
            .build();

    public static final SubmitTransactionRequest SOME_SUBMIT_REQUEST = SubmitTransactionRequest.builder()
            .intentId(SOME_INTENT_ID)
            .chain(SOME_CHAIN)
            .toAddress(SOME_TO_ADDRESS)
            .amount(SOME_AMOUNT)
            .token(SOME_TOKEN)
            .tokenDecimals(6)
            .tokenContractAddress(SOME_TOKEN_CONTRACT)
            .build();

    public static final TransactionResponse SOME_TRANSACTION_RESPONSE = TransactionResponse.builder()
            .transactionId(SOME_TRANSACTION_ID)
            .intentId(SOME_INTENT_ID)
            .chain(SOME_CHAIN)
            .status("RECEIVED")
            .toAddress(SOME_TO_ADDRESS)
            .amount(SOME_AMOUNT)
            .token(SOME_TOKEN)
            .retryCount(0)
            .submittedAt(Instant.parse("2026-01-01T00:00:00Z"))
            .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
            .build();

    public static final SubmitBatchRequest SOME_BATCH_REQUEST = SubmitBatchRequest.builder()
            .transactions(List.of(
                    SOME_SUBMIT_REQUEST,
                    SOME_SUBMIT_REQUEST.toBuilder()
                            .intentId(SOME_SECOND_INTENT_ID)
                            .build()))
            .build();
}
