package com.stablebridge.txrecovery.testutil.fixtures;

import static lombok.AccessLevel.PRIVATE;

import com.stablebridge.txrecovery.domain.transaction.model.SignedTransaction;

import lombok.NoArgsConstructor;

@NoArgsConstructor(access = PRIVATE)
public final class SolanaChainTransactionManagerFixtures {

    public static final String SOME_CHAIN = "solana";
    public static final String SOME_SIGNER_ADDRESS = "7EcDhSYGxXyscszYEp35KHN8vvw3svAuLKTzXwCFLtV";
    public static final String SOME_TX_HASH =
            "5VERv8NMvzbJMEkV8xnrLkEaWRtSz9CosKDYjCJjBRnbJLgp8uirBgmQpjKhoR4tjF3ZpRzrFmBV6UjKdiSZkQUW";
    public static final String SOME_BROADCAST_TX_HASH =
            "2jg2vFhLRGMfMfeChGuKBbKSFL2kFqSHjnXPgmBJpQnr5S8LxoJKVPXpZ5oEqJ7nGHWMqTpKCcgEkqAHdVYsCfy";
    public static final long SOME_STUCK_THRESHOLD_SECONDS = 30L;

    private static final byte[] SOME_SIGNED_PAYLOAD = new byte[] {0x01, 0x02, 0x03};

    public static byte[] someSignedPayload() {
        return SOME_SIGNED_PAYLOAD.clone();
    }

    public static final SignedTransaction SOME_SIGNED_TRANSACTION = SignedTransaction.builder()
            .intentId("solana-intent-001")
            .chain(SOME_CHAIN)
            .signedPayload(someSignedPayload())
            .signerAddress(SOME_SIGNER_ADDRESS)
            .build();
}
