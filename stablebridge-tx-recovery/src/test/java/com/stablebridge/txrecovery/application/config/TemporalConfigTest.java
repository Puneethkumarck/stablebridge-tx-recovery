package com.stablebridge.txrecovery.application.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.temporal.client.WorkflowOptions;

class TemporalConfigTest {

    private final TemporalConfig config = new TemporalConfig();

    @Nested
    class WorkflowOptionsBean {

        @Test
        void shouldCreateWorkflowOptionsWithTimeouts() {
            // given
            var properties = new TemporalProperties(
                    "localhost:7233", null, null, null, null);

            // when
            var result = config.workflowOptions(properties);

            // then
            var expected = WorkflowOptions.newBuilder()
                    .setTaskQueue("str-transaction-lifecycle")
                    .setWorkflowExecutionTimeout(Duration.ofHours(24))
                    .setWorkflowRunTimeout(Duration.ofHours(2))
                    .build();

            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }

        @Test
        void shouldCreateWorkflowOptionsWithCustomTimeouts() {
            // given
            var properties = new TemporalProperties(
                    "localhost:7233", null, "custom-queue",
                    Duration.ofHours(48), Duration.ofHours(4));

            // when
            var result = config.workflowOptions(properties);

            // then
            var expected = WorkflowOptions.newBuilder()
                    .setTaskQueue("custom-queue")
                    .setWorkflowExecutionTimeout(Duration.ofHours(48))
                    .setWorkflowRunTimeout(Duration.ofHours(4))
                    .build();

            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }
    }
}
