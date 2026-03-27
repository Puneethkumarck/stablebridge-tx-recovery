package com.stablebridge.txrecovery.application.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Status;

import io.grpc.health.v1.HealthCheckResponse;
import io.temporal.serviceclient.WorkflowServiceStubs;

@ExtendWith(MockitoExtension.class)
class TemporalHealthIndicatorTest {

    @Mock
    private WorkflowServiceStubs workflowServiceStubs;

    @Mock
    private TemporalProperties temporalProperties;

    @InjectMocks
    private TemporalHealthIndicator healthIndicator;

    @Test
    void shouldReturnUpWhenTemporalIsHealthy() {
        // given
        var response = HealthCheckResponse.newBuilder()
                .setStatus(HealthCheckResponse.ServingStatus.SERVING)
                .build();
        given(workflowServiceStubs.healthCheck()).willReturn(response);
        given(temporalProperties.target()).willReturn("localhost:7233");
        given(temporalProperties.namespace()).willReturn("stablebridge-tx-recovery");
        given(temporalProperties.taskQueue()).willReturn("str-transaction-lifecycle");

        // when
        var health = healthIndicator.health();

        // then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails())
                .containsEntry("target", "localhost:7233")
                .containsEntry("namespace", "stablebridge-tx-recovery")
                .containsEntry("taskQueue", "str-transaction-lifecycle");
    }

    @Test
    void shouldReturnDownWhenTemporalIsUnhealthy() {
        // given
        var connectionException = new RuntimeException("Connection refused");
        willThrow(connectionException).given(workflowServiceStubs).healthCheck();
        given(temporalProperties.target()).willReturn("localhost:7233");

        // when
        var health = healthIndicator.health();

        // then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails())
                .containsEntry("target", "localhost:7233")
                .containsKey("error");
    }
}
