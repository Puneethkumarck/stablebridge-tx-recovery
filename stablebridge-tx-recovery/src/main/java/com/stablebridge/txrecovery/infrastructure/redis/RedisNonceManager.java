package com.stablebridge.txrecovery.infrastructure.redis;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import com.stablebridge.txrecovery.domain.address.model.NonceAllocation;
import com.stablebridge.txrecovery.domain.address.port.NonceManager;
import com.stablebridge.txrecovery.domain.address.port.OnChainNonceProvider;
import com.stablebridge.txrecovery.domain.exception.NonceConcurrencyException;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class RedisNonceManager implements NonceManager {

    static final String FIELD_ALLOCATED = "allocated";
    static final String FIELD_CONFIRMED = "confirmed";
    static final String GAPS_COUNTER_NAME = "str_nonce_gaps_detected_total";

    private static final DefaultRedisScript<Long> CONFIRM_SCRIPT = new DefaultRedisScript<>(
            """
            local confirmed = redis.call('HGET', KEYS[1], 'confirmed')
            local current = confirmed and tonumber(confirmed) or -1
            local nonce = tonumber(ARGV[1])
            if nonce > current then
                redis.call('HSET', KEYS[1], 'confirmed', tostring(nonce))
            end
            redis.call('SREM', KEYS[2], ARGV[1])
            return 1
            """,
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final OnChainNonceProvider onChainNonceProvider;
    private final MeterRegistry meterRegistry;

    @Override
    public NonceAllocation allocate(String address, String chain) {
        var hashKey = RedisKeyNamespace.nonceHash(chain, address);
        var inflightKey = RedisKeyNamespace.nonceInflightSet(chain, address);
        var nonceHolder = new long[1];

        var results = redisTemplate.execute(new SessionCallback<List<Object>>() {
            @Override
            @SuppressWarnings("unchecked")
            public List<Object> execute(RedisOperations operations) throws DataAccessException {
                operations.watch(hashKey);

                var allocatedStr = (String) operations.opsForHash().get(hashKey, FIELD_ALLOCATED);
                var onChainNonce =
                        onChainNonceProvider.getTransactionCount(address, chain).longValue();

                var nextNonce = allocatedStr == null
                        ? onChainNonce
                        : Math.max(Long.parseLong(allocatedStr) + 1, onChainNonce);
                nonceHolder[0] = nextNonce;

                operations.multi();
                operations.opsForHash().put(hashKey, FIELD_ALLOCATED, String.valueOf(nextNonce));
                operations.opsForSet().add(inflightKey, String.valueOf(nextNonce));

                return operations.exec();
            }
        });

        if (results == null || results.isEmpty()) {
            throw new NonceConcurrencyException(address, chain);
        }

        var nonce = nonceHolder[0];
        log.info("Allocated nonce {} for address {} on chain {}", nonce, address, chain);
        return NonceAllocation.builder()
                .address(address)
                .chain(chain)
                .nonce(nonce)
                .build();
    }

    @Override
    public void release(NonceAllocation allocation) {
        var inflightKey = RedisKeyNamespace.nonceInflightSet(allocation.chain(), allocation.address());
        redisTemplate.opsForSet().remove(inflightKey, String.valueOf(allocation.nonce()));
        log.info(
                "Released nonce {} for address {} on chain {}",
                allocation.nonce(),
                allocation.address(),
                allocation.chain());
    }

    @Override
    public void confirm(NonceAllocation allocation) {
        var hashKey = RedisKeyNamespace.nonceHash(allocation.chain(), allocation.address());
        var inflightKey = RedisKeyNamespace.nonceInflightSet(allocation.chain(), allocation.address());

        redisTemplate.execute(
                CONFIRM_SCRIPT, List.of(hashKey, inflightKey), String.valueOf(allocation.nonce()));

        log.info(
                "Confirmed nonce {} for address {} on chain {}",
                allocation.nonce(),
                allocation.address(),
                allocation.chain());
    }

    @Override
    public void syncFromChain(String address, String chain) {
        var hashKey = RedisKeyNamespace.nonceHash(chain, address);
        var inflightKey = RedisKeyNamespace.nonceInflightSet(chain, address);

        var onChainNonce =
                onChainNonceProvider.getTransactionCount(address, chain).longValue();
        var resetValue = onChainNonce - 1;

        redisTemplate.opsForHash().put(hashKey, FIELD_ALLOCATED, String.valueOf(resetValue));
        redisTemplate.opsForHash().put(hashKey, FIELD_CONFIRMED, String.valueOf(resetValue));
        redisTemplate.delete(inflightKey);

        log.warn(
                "Synced nonce state from chain for address {} on chain {}: onChainNonce={}, reset to {}",
                address,
                chain,
                onChainNonce,
                resetValue);
    }

    @Override
    public Set<Long> detectGaps(String address, String chain) {
        var inflightKey = RedisKeyNamespace.nonceInflightSet(chain, address);
        var onChainNonce =
                onChainNonceProvider.getTransactionCount(address, chain).longValue();

        var inflightMembers = redisTemplate.opsForSet().members(inflightKey);
        if (inflightMembers == null || inflightMembers.isEmpty()) {
            return Set.of();
        }

        var gaps = inflightMembers.stream()
                .map(Long::parseLong)
                .filter(nonce -> nonce < onChainNonce)
                .collect(Collectors.toUnmodifiableSet());

        if (!gaps.isEmpty()) {
            log.warn("Detected {} gap nonce(s) for address {} on chain {}: {}", gaps.size(), address, chain, gaps);
            meterRegistry.counter(GAPS_COUNTER_NAME).increment(gaps.size());
        }

        return gaps;
    }
}
