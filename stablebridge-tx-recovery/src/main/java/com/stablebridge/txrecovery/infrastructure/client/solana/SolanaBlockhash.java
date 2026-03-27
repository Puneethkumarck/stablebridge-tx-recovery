package com.stablebridge.txrecovery.infrastructure.client.solana;

record SolanaBlockhash(SolanaBlockhashValue value) {

    record SolanaBlockhashValue(String blockhash, long lastValidBlockHeight) {}
}
