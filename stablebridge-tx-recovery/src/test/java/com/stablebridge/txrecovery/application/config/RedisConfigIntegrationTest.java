package com.stablebridge.txrecovery.application.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.stablebridge.txrecovery.testutil.PostgresContainerExtension;
import com.stablebridge.txrecovery.testutil.RedisTest;

@RedisTest
@ExtendWith(PostgresContainerExtension.class)
class RedisConfigIntegrationTest {

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void shouldInjectRedisConnectionFactory() {
        // when / then
        assertThat(redisConnectionFactory).isNotNull();
    }

    @Test
    void shouldInjectStringRedisTemplate() {
        // when / then
        assertThat(stringRedisTemplate).isNotNull();
    }

    @Test
    void shouldPerformRedisSetAndGet() {
        // given
        var key = "str:test:connectivity";
        var value = "connected";

        // when
        stringRedisTemplate.opsForValue().set(key, value);
        var result = stringRedisTemplate.opsForValue().get(key);

        // then
        assertThat(result).isEqualTo(value);
    }

    @Test
    void shouldPerformRedisHashOperations() {
        // given
        var hashKey = "str:nonce:ethereum_mainnet:0xabc123";
        var field = "current";
        var value = "42";

        // when
        stringRedisTemplate.opsForHash().put(hashKey, field, value);
        var result = stringRedisTemplate.opsForHash().get(hashKey, field);

        // then
        assertThat(result).isEqualTo(value);
    }

    @Test
    void shouldPerformRedisSetOperations() {
        // given
        var setKey = "str:nonce:inflight:ethereum_mainnet:0xabc123";

        // when
        stringRedisTemplate.opsForSet().add(setKey, "10", "11", "12");
        var members = stringRedisTemplate.opsForSet().members(setKey);

        // then
        assertThat(members).containsExactlyInAnyOrder("10", "11", "12");
    }
}
