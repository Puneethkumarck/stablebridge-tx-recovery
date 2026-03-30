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
import org.springframework.kafka.core.KafkaAdmin;

@ExtendWith(MockitoExtension.class)
class KafkaHealthIndicatorTest {

    private static final String CLUSTER_ID = "test-cluster-id";

    @Mock
    private KafkaAdmin kafkaAdmin;

    @InjectMocks
    private KafkaHealthIndicator healthIndicator;

    @Nested
    class WhenKafkaIsUp {

        @Test
        void shouldReturnUpWithClusterId() {
            // given
            given(kafkaAdmin.clusterId()).willReturn(CLUSTER_ID);

            // when
            var health = healthIndicator.health();

            // then
            var expected = Health.up()
                    .withDetail("clusterId", CLUSTER_ID)
                    .build();
            assertThat(health)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }
    }

    @Nested
    class WhenKafkaIsDown {

        @Test
        void shouldReturnDownWithException() {
            // given
            var exception = new RuntimeException("Connection refused");
            given(kafkaAdmin.clusterId()).willThrow(exception);

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
