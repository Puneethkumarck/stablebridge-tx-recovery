package com.stablebridge.txrecovery.application.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.stablebridge.txrecovery.domain.address.port.NonceManager;
import com.stablebridge.txrecovery.testutil.RedisTest;

@RedisTest
class RedisConfigIntegrationTest {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @MockitoBean
    @SuppressWarnings("unused")
    private NonceManager nonceManager;

    @Nested
    class RedisOperations {

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
}
