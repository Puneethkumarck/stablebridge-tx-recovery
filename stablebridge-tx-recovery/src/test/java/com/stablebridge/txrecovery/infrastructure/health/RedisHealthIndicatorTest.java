package com.stablebridge.txrecovery.infrastructure.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Health;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class RedisHealthIndicatorTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private RedisConnectionFactory connectionFactory;

    @Mock
    private RedisConnection connection;

    @InjectMocks
    private RedisHealthIndicator healthIndicator;

    @Nested
    class WhenRedisIsUp {

        @Test
        void shouldReturnUpWithPongDetail() {
            // given
            given(stringRedisTemplate.getConnectionFactory()).willReturn(connectionFactory);
            given(connectionFactory.getConnection()).willReturn(connection);
            given(connection.ping()).willReturn("PONG");

            // when
            var health = healthIndicator.health();

            // then
            var expected = Health.up()
                    .withDetail("ping", "PONG")
                    .build();
            assertThat(health)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }
    }

    @Nested
    class WhenRedisIsDown {

        @Test
        void shouldReturnDownWithException() {
            // given
            var exception = new RuntimeException("Connection refused");
            given(stringRedisTemplate.getConnectionFactory()).willThrow(exception);

            // when
            var health = healthIndicator.health();

            // then
            var expected = Health.down()
                    .withException(exception)
                    .build();
            assertThat(health)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }
    }
}
