package com.stablebridge.txrecovery.application.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.stablebridge.txrecovery.testutil.PostgresContainerExtension;
import com.stablebridge.txrecovery.testutil.RedisTest;

@RedisTest
@ExtendWith(PostgresContainerExtension.class)
@ImportAutoConfiguration(DataRedisAutoConfiguration.class)
class RedisConfigIntegrationTest {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void shouldConnectToRedisAndPerformOperations() {
        // given
        var key = "str:test:integration";
        var value = "stablebridge-tx-recovery";

        // when
        stringRedisTemplate.opsForValue().set(key, value);
        var result = stringRedisTemplate.opsForValue().get(key);

        // then
        assertThat(result).isEqualTo(value);
    }
}
