package com.stablebridge.txrecovery.infrastructure.redis;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RedisKeyNamespace {

    public static final String NONCE_HASH = "str:nonce:%s:%s";
    public static final String NONCE_INFLIGHT_SET = "str:nonce:inflight:%s:%s";
    public static final String POOL_LOCK = "str:pool:lock:%s:%s";
    public static final String GAS_CACHE_HASH = "str:gas:cache:%s";

    public static String nonceHash(String chain, String address) {
        return NONCE_HASH.formatted(chain, address);
    }

    public static String nonceInflightSet(String chain, String address) {
        return NONCE_INFLIGHT_SET.formatted(chain, address);
    }

    public static String poolLock(String chain, String address) {
        return POOL_LOCK.formatted(chain, address);
    }

    public static String gasCacheHash(String chain) {
        return GAS_CACHE_HASH.formatted(chain);
    }
}
