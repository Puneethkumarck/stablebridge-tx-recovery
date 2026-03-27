package com.stablebridge.txrecovery.infrastructure.client.solana;

import java.util.List;

record SolanaAccountInfo(SolanaAccountValue value) {

    record SolanaAccountValue(List<String> data, String owner, long lamports, boolean executable) {}
}
