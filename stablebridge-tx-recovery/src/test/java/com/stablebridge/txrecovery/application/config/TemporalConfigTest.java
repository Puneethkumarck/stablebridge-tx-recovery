package com.stablebridge.txrecovery.application.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.stablebridge.txrecovery.application.config.StrProperties.TemporalConfigProperties;
import com.stablebridge.txrecovery.application.config.StrProperties.TemporalConfigProperties.ActivityConfig;
import com.stablebridge.txrecovery.application.config.StrProperties.TemporalConfigProperties.ActivityOptionsProperties;

import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import io.temporal.client.WorkflowOptions;

class TemporalConfigTest {

    private final TemporalConfig config = new TemporalConfig();

    private StrProperties strPropertiesWithTemporal(TemporalConfigProperties temporal) {
        return StrProperties.builder()
                .temporal(temporal)
                .chains(Map.of())
                .build();
    }

    @Nested
    class WorkflowOptionsBean {

        @Test
        void shouldCreateWorkflowOptionsWithTimeouts() {
            // given
            var strProperties = strPropertiesWithTemporal(
                    TemporalConfigProperties.builder()
                            .target("localhost:7233")
                            .build());

            // when
            var result = config.workflowOptions(strProperties);

            // then
            var expected = WorkflowOptions.newBuilder()
                    .setTaskQueue("str-transaction-lifecycle")
                    .setWorkflowExecutionTimeout(Duration.ofHours(24))
                    .setWorkflowRunTimeout(Duration.ofHours(2))
                    .setWorkflowIdReusePolicy(
                            WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_ALLOW_DUPLICATE_FAILED_ONLY)
                    .build();

            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }

        @Test
        void shouldCreateWorkflowOptionsWithCustomTimeouts() {
            // given
            var strProperties = strPropertiesWithTemporal(
                    TemporalConfigProperties.builder()
                            .target("localhost:7233")
                            .taskQueue("custom-queue")
                            .workflowExecutionTimeout(Duration.ofHours(48))
                            .workflowRunTimeout(Duration.ofHours(4))
                            .build());

            // when
            var result = config.workflowOptions(strProperties);

            // then
            var expected = WorkflowOptions.newBuilder()
                    .setTaskQueue("custom-queue")
                    .setWorkflowExecutionTimeout(Duration.ofHours(48))
                    .setWorkflowRunTimeout(Duration.ofHours(4))
                    .setWorkflowIdReusePolicy(
                            WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_ALLOW_DUPLICATE_FAILED_ONLY)
                    .build();

            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }

        @Test
        void shouldSetWorkflowIdReusePolicyToAllowDuplicateFailedOnly() {
            // given
            var strProperties = strPropertiesWithTemporal(
                    TemporalConfigProperties.builder().build());

            // when
            var result = config.workflowOptions(strProperties);

            // then
            assertThat(result.getWorkflowIdReusePolicy())
                    .isEqualTo(WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_ALLOW_DUPLICATE_FAILED_ONLY);
        }
    }

    @Nested
    class DataConverterBean {

        @Test
        void shouldCreateDataConverterWithJacksonPayloadConverter() {
            // when
            var result = config.dataConverter();

            // then
            assertThat(result).isNotNull();
            assertThat(result.toPayload("test")).isPresent();
        }

        @Test
        void shouldCreateDataConverterThatSerializesWithJackson() {
            // when
            var result = config.dataConverter();

            // then
            var payload = result.toPayload("hello-world").orElseThrow();
            var deserialized = result.fromPayload(payload, String.class, String.class);
            assertThat(deserialized).isEqualTo("hello-world");
        }
    }

    @Nested
    class WorkflowImplementationOptionsBean {

        @Test
        void shouldCreateWorkflowImplementationOptionsWithDefaultActivityOptions() {
            // given
            var strProperties = strPropertiesWithTemporal(
                    TemporalConfigProperties.builder().build());

            // when
            var result = config.workflowImplementationOptions(strProperties);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getDefaultActivityOptions()).isNotNull();
            assertThat(result.getDefaultActivityOptions().getStartToCloseTimeout())
                    .isEqualTo(Duration.ofSeconds(30));
            assertThat(result.getActivityOptions()).containsOnlyKeys(
                    "sign", "waitForFinality", "executeRecovery");
        }

        @Test
        void shouldMapSigningConfigToSignMethod() {
            // given
            var strProperties = strPropertiesWithTemporal(
                    TemporalConfigProperties.builder().build());

            // when
            var result = config.workflowImplementationOptions(strProperties);

            // then
            var signOptions = result.getActivityOptions().get("sign");
            assertThat(signOptions.getStartToCloseTimeout())
                    .isEqualTo(Duration.ofSeconds(10));
            assertThat(signOptions.getRetryOptions().getMaximumAttempts())
                    .isEqualTo(2);
        }

        @Test
        void shouldMapConfirmationConfigToWaitForFinalityMethod() {
            // given
            var strProperties = strPropertiesWithTemporal(
                    TemporalConfigProperties.builder().build());

            // when
            var result = config.workflowImplementationOptions(strProperties);

            // then
            var confirmationOptions = result.getActivityOptions().get("waitForFinality");
            assertThat(confirmationOptions.getStartToCloseTimeout())
                    .isEqualTo(Duration.ofMinutes(5));
            assertThat(confirmationOptions.getRetryOptions().getMaximumAttempts())
                    .isEqualTo(1);
        }

        @Test
        void shouldMapRecoveryConfigToExecuteRecoveryMethod() {
            // given
            var strProperties = strPropertiesWithTemporal(
                    TemporalConfigProperties.builder().build());

            // when
            var result = config.workflowImplementationOptions(strProperties);

            // then
            var recoveryOptions = result.getActivityOptions().get("executeRecovery");
            assertThat(recoveryOptions.getStartToCloseTimeout())
                    .isEqualTo(Duration.ofSeconds(60));
            assertThat(recoveryOptions.getRetryOptions().getMaximumAttempts())
                    .isEqualTo(3);
        }

        @Test
        void shouldCreateWorkflowImplementationOptionsWithCustomActivityOptions() {
            // given
            var customDefault = new ActivityConfig(
                    Duration.ofSeconds(45), 5, Duration.ofSeconds(2), 3.0);
            var customSigning = new ActivityConfig(
                    Duration.ofSeconds(15), 3, Duration.ofSeconds(2), 2.5);
            var customConfirmation = new ActivityConfig(
                    Duration.ofSeconds(600), 2, Duration.ofSeconds(5), 3.0);
            var customRecovery = new ActivityConfig(
                    Duration.ofSeconds(120), 4, Duration.ofSeconds(3), 2.5);
            var activityOptions = new ActivityOptionsProperties(
                    customDefault, customSigning, customConfirmation, customRecovery);
            var nonRetryable = List.of(
                    "com.stablebridge.txrecovery.domain.exception.NonRetryableException");
            var strProperties = strPropertiesWithTemporal(
                    TemporalConfigProperties.builder()
                            .activityOptions(activityOptions)
                            .nonRetryableExceptions(nonRetryable)
                            .build());

            // when
            var result = config.workflowImplementationOptions(strProperties);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getActivityOptions().get("sign").getStartToCloseTimeout())
                    .isEqualTo(Duration.ofSeconds(15));
            assertThat(result.getActivityOptions().get("waitForFinality").getStartToCloseTimeout())
                    .isEqualTo(Duration.ofSeconds(600));
            assertThat(result.getActivityOptions().get("executeRecovery").getStartToCloseTimeout())
                    .isEqualTo(Duration.ofSeconds(120));
        }
    }
}
