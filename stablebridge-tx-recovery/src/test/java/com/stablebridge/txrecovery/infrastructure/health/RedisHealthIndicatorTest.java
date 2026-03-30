package com.stablebridge.txrecovery.infrastructure.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Health;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class RedisHealthIndicatorTest {

    @Mock
    private RedisConnectionFactory connectionFactory;

    @Mock
    private RedisConnection connection;

    private RedisHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        var stringRedisTemplate = new StringRedisTemplate();
        stringRedisTemplate.setConnectionFactory(connectionFactory);
        stringRedisTemplate.afterPropertiesSet();
        healthIndicator = new RedisHealthIndicator(stringRedisTemplate);
    }

    @Nested
    class WhenRedisIsUp {

        @Test
        void shouldReturnUpWithPongDetail() {
            // given
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
            var exception = new RedisConnectionFailureException("Connection refused");
            given(connectionFactory.getConnection()).willThrow(exception);

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
