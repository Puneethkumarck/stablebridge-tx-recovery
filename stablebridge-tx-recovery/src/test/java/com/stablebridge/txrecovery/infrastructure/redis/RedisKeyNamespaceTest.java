package com.stablebridge.txrecovery.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RedisKeyNamespaceTest {

    @Test
    void shouldFormatNonceHashKey() {
        // when
        var result = RedisKeyNamespace.nonceHash("ethereum_mainnet", "0xabc123");

        // then
        assertThat(result).isEqualTo("str:nonce:ethereum_mainnet:0xabc123");
    }

    @Test
    void shouldFormatNonceInflightSetKey() {
        // when
        var result = RedisKeyNamespace.nonceInflightSet("polygon_mainnet", "0xdef456");

        // then
        assertThat(result).isEqualTo("str:nonce:inflight:polygon_mainnet:0xdef456");
    }

    @Test
    void shouldFormatPoolLockKey() {
        // when
        var result = RedisKeyNamespace.poolLock("ethereum_mainnet", "0xabc123");

        // then
        assertThat(result).isEqualTo("str:pool:lock:ethereum_mainnet:0xabc123");
    }

    @Test
    void shouldFormatGasCacheHashKey() {
        // when
        var result = RedisKeyNamespace.gasCacheHash("ethereum_mainnet");

        // then
        assertThat(result).isEqualTo("str:gas:cache:ethereum_mainnet");
    }
}
