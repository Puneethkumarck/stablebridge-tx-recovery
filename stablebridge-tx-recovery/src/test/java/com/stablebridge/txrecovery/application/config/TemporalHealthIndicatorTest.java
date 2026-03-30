package com.stablebridge.txrecovery.application.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import com.stablebridge.txrecovery.application.config.StrProperties.TemporalConfigProperties;

import io.grpc.health.v1.HealthCheckResponse;
import io.temporal.serviceclient.WorkflowServiceStubs;

@ExtendWith(MockitoExtension.class)
class TemporalHealthIndicatorTest {

    private static final String TARGET = "localhost:7233";
    private static final String NAMESPACE = "stablebridge-tx-recovery";
    private static final String TASK_QUEUE = "str-transaction-lifecycle";

    @Mock
    private WorkflowServiceStubs workflowServiceStubs;

    @Mock
    private StrProperties strProperties;

    @InjectMocks
    private TemporalHealthIndicator healthIndicator;

    @Nested
    class WhenTemporalIsHealthy {

        @Test
        void shouldReturnUpWithConnectionDetails() {
            // given
            var temporal = TemporalConfigProperties.builder()
                    .target(TARGET)
                    .namespace(NAMESPACE)
                    .taskQueue(TASK_QUEUE)
                    .build();
            given(strProperties.temporal()).willReturn(temporal);
            var response = HealthCheckResponse.newBuilder()
                    .setStatus(HealthCheckResponse.ServingStatus.SERVING)
                    .build();
            given(workflowServiceStubs.healthCheck()).willReturn(response);

            // when
            var health = healthIndicator.health();

            // then
            var expected = Health.up()
                    .withDetail("target", TARGET)
                    .withDetail("namespace", NAMESPACE)
                    .withDetail("taskQueue", TASK_QUEUE)
                    .build();

            assertThat(health)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }
    }

    @Nested
    class WhenTemporalIsUnhealthy {

        @Test
        void shouldReturnDegradedWithException() {
            // given
            var temporal = TemporalConfigProperties.builder()
                    .target(TARGET)
                    .build();
            given(strProperties.temporal()).willReturn(temporal);
            var connectionException = new RuntimeException("Connection refused");
            willThrow(connectionException).given(workflowServiceStubs).healthCheck();

            // when
            var health = healthIndicator.health();

            // then
            var expected = Health.status(new Status("DEGRADED"))
                    .withDetail("target", TARGET)
                    .withException(connectionException)
                    .build();

            assertThat(health)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }
    }
}
