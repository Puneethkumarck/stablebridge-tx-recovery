package com.stablebridge.txrecovery.testutil.fixtures;

import com.stablebridge.txrecovery.domain.address.model.NonceAccountStatus;
import com.stablebridge.txrecovery.domain.address.model.SolanaNonceAccount;

public final class SolanaNonceAccountFixtures {

    private SolanaNonceAccountFixtures() {}

    public static final String SOME_CHAIN = "solana-mainnet";
    public static final String SOME_NONCE_ACCOUNT_ADDRESS = "NonceAcct1111111111111111111111111111111111";
    public static final String SOME_AUTHORITY_ADDRESS = "Authority111111111111111111111111111111111";
    public static final String SOME_NONCE_VALUE = "7BmKVg3fBqR5GwMUjJ1kmVfRzHXjGNKPQsCdkEuJgRai";
    public static final String SOME_NEW_NONCE_VALUE = "9XpKVg3fBqR5GwMUjJ1kmVfRzHXjGNKPQsCdkEuJgRai";
    public static final String SOME_INTENT_ID = "intent-solana-001";

    public static final SolanaNonceAccount SOME_AVAILABLE_NONCE_ACCOUNT = SolanaNonceAccount.builder()
            .nonceAccountAddress(SOME_NONCE_ACCOUNT_ADDRESS)
            .authorityAddress(SOME_AUTHORITY_ADDRESS)
            .nonceValue(SOME_NONCE_VALUE)
            .status(NonceAccountStatus.AVAILABLE)
            .build();

    public static final SolanaNonceAccount SOME_IN_USE_NONCE_ACCOUNT = SOME_AVAILABLE_NONCE_ACCOUNT.toBuilder()
            .status(NonceAccountStatus.IN_USE)
            .build();

    public static final SolanaNonceAccount SOME_REGISTERED_NONCE_ACCOUNT = SolanaNonceAccount.builder()
            .nonceAccountAddress(SOME_NONCE_ACCOUNT_ADDRESS)
            .authorityAddress(SOME_AUTHORITY_ADDRESS)
            .nonceValue(null)
            .status(NonceAccountStatus.AVAILABLE)
            .build();
}
