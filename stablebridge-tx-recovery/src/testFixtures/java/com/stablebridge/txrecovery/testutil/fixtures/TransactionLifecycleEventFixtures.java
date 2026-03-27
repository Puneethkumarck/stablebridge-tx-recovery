package com.stablebridge.txrecovery.testutil.fixtures;

import static com.stablebridge.txrecovery.domain.transaction.model.TransactionStatus.SUBMITTED;

import java.time.Instant;

import com.stablebridge.txrecovery.domain.transaction.event.TransactionLifecycleEvent;

public final class TransactionLifecycleEventFixtures {

    public static final String SOME_EVENT_ID = "evt-001";
    public static final String SOME_INTENT_ID = "intent-001";
    public static final String SOME_TO_ADDRESS = "0xrecipient";
    public static final String SOME_CHAIN = "ethereum_mainnet";
    public static final String SOME_CHAIN_UPPER = "SOLANA_MAINNET";
    public static final String SOME_PAYLOAD = "{\"json\":true}";

    public static final TransactionLifecycleEvent SOME_EVENT = buildEvent(SOME_TO_ADDRESS);

    public static final TransactionLifecycleEvent SOME_EVENT_WITHOUT_TO_ADDRESS = buildEvent(null);

    public static final TransactionLifecycleEvent SOME_EVENT_UPPER_CHAIN = TransactionLifecycleEvent.builder()
            .eventId(SOME_EVENT_ID)
            .intentId(SOME_INTENT_ID)
            .chain(SOME_CHAIN_UPPER)
            .status(SUBMITTED)
            .timestamp(Instant.now())
            .build();

    public static TransactionLifecycleEvent buildEvent(String toAddress) {
        return TransactionLifecycleEvent.builder()
                .eventId(SOME_EVENT_ID)
                .intentId(SOME_INTENT_ID)
                .toAddress(toAddress)
                .chain(SOME_CHAIN)
                .status(SUBMITTED)
                .timestamp(Instant.now())
                .build();
    }

    private TransactionLifecycleEventFixtures() {}
}
