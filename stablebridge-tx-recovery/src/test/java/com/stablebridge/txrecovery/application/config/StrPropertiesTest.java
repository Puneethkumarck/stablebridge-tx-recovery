package com.stablebridge.txrecovery.application.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import com.stablebridge.txrecovery.application.config.StrProperties.ApiProperties;
import com.stablebridge.txrecovery.application.config.StrProperties.ChainProperties;
import com.stablebridge.txrecovery.application.config.StrProperties.ChainProperties.RpcProperties;
import com.stablebridge.txrecovery.application.config.StrProperties.ChainProperties.RpcProperties.CircuitBreakerProperties;
import com.stablebridge.txrecovery.application.config.StrProperties.EscalationProperties;
import com.stablebridge.txrecovery.application.config.StrProperties.EscalationTierProperties;
import com.stablebridge.txrecovery.application.config.StrProperties.GasBudgetProperties;
import com.stablebridge.txrecovery.application.config.StrProperties.RedisConfigProperties;
import com.stablebridge.txrecovery.application.config.StrProperties.SignerProperties;
import com.stablebridge.txrecovery.application.config.StrProperties.SignerProperties.CallbackProperties;
import com.stablebridge.txrecovery.application.config.StrProperties.SignerProperties.CallbackProperties.TlsProperties;
import com.stablebridge.txrecovery.application.config.StrProperties.SubmissionProperties;
import com.stablebridge.txrecovery.application.config.StrProperties.TemporalConfigProperties;
import com.stablebridge.txrecovery.application.config.StrProperties.TemporalConfigProperties.ActivityOptionsProperties;

class StrPropertiesTest {

    @Nested
    class DefaultValues {

        @Test
        void shouldApplyDefaultsWhenAllFieldsAreNull() {
            var properties = new StrProperties(null, null, null, null, null, null, null);

            var expected = StrProperties.builder()
                    .api(new ApiProperties(null))
                    .signer(SignerProperties.builder().build())
                    .escalation(EscalationProperties.builder().build())
                    .submission(new SubmissionProperties(null, null))
                    .temporal(TemporalConfigProperties.builder().build())
                    .redis(new RedisConfigProperties(null, 0))
                    .chains(Map.of())
                    .build();

            assertThat(properties)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }
    }

    @Nested
    class ApiPropertiesTests {

        @Test
        void shouldDefaultToEmptyKeyWhenNull() {
            var api = new ApiProperties(null);

            assertThat(api.key()).isEmpty();
        }

        @Test
        void shouldPreserveCustomKey() {
            var api = new ApiProperties("my-api-key");

            assertThat(api.key()).isEqualTo("my-api-key");
        }
    }

    @Nested
    class GasBudgetPropertiesTests {

        @Test
        void shouldApplyDefaultGasBudgetValues() {
            var gasBudget = GasBudgetProperties.builder().build();

            var expected = GasBudgetProperties.builder()
                    .percentage(new BigDecimal("0.01"))
                    .absoluteMinUsd(new BigDecimal("5"))
                    .absoluteMaxUsd(new BigDecimal("500"))
                    .build();

            assertThat(gasBudget)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }

        @Test
        void shouldThrowWhenMinExceedsMax() {
            assertThatThrownBy(() -> GasBudgetProperties.builder()
                    .absoluteMinUsd(new BigDecimal("1000"))
                    .absoluteMaxUsd(new BigDecimal("500"))
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("absoluteMinUsd must not exceed absoluteMaxUsd");
        }
    }

    @Nested
    class EscalationPropertiesTests {

        @Test
        void shouldApplyDefaultEscalationValues() {
            var escalation = EscalationProperties.builder().build();

            assertThat(escalation.highValueThresholdUsd())
                    .isEqualByComparingTo(new BigDecimal("50000"));
            assertThat(escalation.defaultTiers()).isEmpty();
            assertThat(escalation.highValueTiers()).isEmpty();
        }

        @Test
        void shouldPreserveCustomTiers() {
            var tier = EscalationTierProperties.builder()
                    .level(0)
                    .stuckThreshold(Duration.ZERO)
                    .gasMultiplier(BigDecimal.ONE)
                    .requiresHumanApproval(false)
                    .description("Initial")
                    .build();

            var escalation = EscalationProperties.builder()
                    .defaultTiers(List.of(tier))
                    .highValueTiers(List.of(tier))
                    .build();

            assertThat(escalation.defaultTiers()).hasSize(1);
            assertThat(escalation.highValueTiers()).hasSize(1);
        }
    }

    @Nested
    class SubmissionPropertiesTests {

        @Test
        void shouldApplyDefaultSubmissionValues() {
            var submission = new SubmissionProperties(null, null);

            assertThat(submission.sequentialThresholdUsd())
                    .isEqualByComparingTo(new BigDecimal("100000"));
            assertThat(submission.maxPipelineDepth()).isEqualTo(20);
        }

        @Test
        void shouldPreserveCustomSubmissionValues() {
            var submission = new SubmissionProperties(new BigDecimal("50000"), 10);

            assertThat(submission.sequentialThresholdUsd())
                    .isEqualByComparingTo(new BigDecimal("50000"));
            assertThat(submission.maxPipelineDepth()).isEqualTo(10);
        }
    }

    @Nested
    class SignerPropertiesTests {

        @Test
        void shouldApplyDefaultSignerCallbackValues() {
            var signer = SignerProperties.builder().build();

            assertThat(signer.callback().timeout()).isEqualTo(Duration.ofSeconds(5));
            assertThat(signer.callback().tls().verify()).isTrue();
        }

        @Test
        void shouldPreserveCustomSignerValues() {
            var tls = new TlsProperties(false);
            var callback = CallbackProperties.builder()
                    .endpoint("https://signer.example.com")
                    .timeout(Duration.ofSeconds(10))
                    .hmacSecret("secret-123")
                    .tls(tls)
                    .build();

            var signer = SignerProperties.builder()
                    .backend("callback")
                    .keystorePath("/path/to/keystore.p12")
                    .password("secret")
                    .callback(callback)
                    .build();

            assertThat(signer.backend()).isEqualTo("callback");
            assertThat(signer.callback().endpoint()).isEqualTo("https://signer.example.com");
            assertThat(signer.callback().tls().verify()).isFalse();
        }
    }

    @Nested
    class ChainPropertiesTests {

        @Test
        void shouldApplyDefaultChainPropertyValues() {
            var chain = ChainProperties.builder()
                    .enabled(true)
                    .chainFamily("EVM")
                    .chainId(1L)
                    .finalityBlocks(12)
                    .stuckThresholdBlocks(50)
                    .pollInterval(Duration.ofSeconds(12))
                    .rpc(RpcProperties.builder()
                            .urls(List.of("http://localhost:8545"))
                            .build())
                    .build();

            assertThat(chain.tokenContracts()).isEmpty();
            assertThat(chain.tokenMints()).isEmpty();
        }

        @Test
        void shouldApplyDefaultRpcValues() {
            var rpc = RpcProperties.builder()
                    .urls(List.of("http://localhost:8545"))
                    .build();

            var expected = RpcProperties.builder()
                    .urls(List.of("http://localhost:8545"))
                    .timeout(Duration.ofSeconds(5))
                    .maxRetries(3)
                    .rateLimitRps(25)
                    .rateLimitBurst(50)
                    .circuitBreaker(CircuitBreakerProperties.builder().build())
                    .build();

            assertThat(rpc)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }

        @Test
        void shouldApplyDefaultCircuitBreakerValues() {
            var cb = CircuitBreakerProperties.builder().build();

            assertThat(cb.failureRateThreshold()).isEqualTo(50);
            assertThat(cb.waitDurationInOpenState()).isEqualTo(Duration.ofSeconds(30));
            assertThat(cb.slidingWindowSize()).isEqualTo(10);
        }
    }

    @Nested
    class TemporalConfigPropertiesTests {

        @Test
        void shouldApplyDefaultTemporalValues() {
            var temporal = TemporalConfigProperties.builder().build();

            assertThat(temporal.target()).isEqualTo("localhost:7233");
            assertThat(temporal.namespace()).isEqualTo("stablebridge-tx-recovery");
            assertThat(temporal.taskQueue()).isEqualTo("str-transaction-lifecycle");
            assertThat(temporal.workflowExecutionTimeout()).isEqualTo(Duration.ofHours(24));
            assertThat(temporal.workflowRunTimeout()).isEqualTo(Duration.ofHours(2));
            assertThat(temporal.nonRetryableExceptions()).containsExactly(
                    "com.stablebridge.txrecovery.domain.exception.NonRetryableException",
                    "com.stablebridge.txrecovery.domain.exception.NonceTooLowException",
                    "com.stablebridge.txrecovery.domain.exception.UnknownChainException");
        }

        @Test
        void shouldApplyDefaultActivityOptionsValues() {
            var activityOptions = ActivityOptionsProperties.builder().build();

            assertThat(activityOptions.defaultOptions().startToCloseTimeout())
                    .isEqualTo(Duration.ofSeconds(30));
            assertThat(activityOptions.defaultOptions().maxAttempts()).isEqualTo(3);
            assertThat(activityOptions.signing().startToCloseTimeout())
                    .isEqualTo(Duration.ofSeconds(10));
            assertThat(activityOptions.signing().maxAttempts()).isEqualTo(2);
            assertThat(activityOptions.confirmation().startToCloseTimeout())
                    .isEqualTo(Duration.ofMinutes(5));
            assertThat(activityOptions.confirmation().maxAttempts()).isEqualTo(1);
            assertThat(activityOptions.recoveryExecution().startToCloseTimeout())
                    .isEqualTo(Duration.ofSeconds(60));
            assertThat(activityOptions.recoveryExecution().maxAttempts()).isEqualTo(3);
        }
    }

    @Nested
    class RedisConfigPropertiesTests {

        @Test
        void shouldApplyDefaultHostAndPort() {
            var redis = new RedisConfigProperties(null, 0);

            assertThat(redis.host()).isEqualTo("localhost");
            assertThat(redis.port()).isEqualTo(6379);
        }

        @Test
        void shouldPreserveCustomValues() {
            var redis = new RedisConfigProperties("redis.prod", 6380);

            assertThat(redis.host()).isEqualTo("redis.prod");
            assertThat(redis.port()).isEqualTo(6380);
        }
    }

    @Nested
    class YamlBinding {

        @Test
        void shouldBindFromPropertyValues() {
            var runner = new ApplicationContextRunner()
                    .withUserConfiguration(TestConfig.class)
                    .withPropertyValues(
                            "str.api.key=test-key",
                            "str.signer.backend=local-keystore",
                            "str.signer.keystore-path=/tmp/test.p12",
                            "str.signer.password=test-pass",
                            "str.signer.callback.hmac-secret=hmac-test",
                            "str.signer.callback.timeout=PT10S",
                            "str.signer.callback.tls.verify=false",
                            "str.submission.sequential-threshold-usd=50000",
                            "str.submission.max-pipeline-depth=10",
                            "str.escalation.high-value-threshold-usd=75000",
                            "str.escalation.gas-budget.percentage=0.02",
                            "str.escalation.gas-budget.absolute-min-usd=10",
                            "str.escalation.gas-budget.absolute-max-usd=1000",
                            "str.escalation.default-tiers[0].level=0",
                            "str.escalation.default-tiers[0].stuck-threshold=PT0S",
                            "str.escalation.default-tiers[0].gas-multiplier=1.0",
                            "str.escalation.default-tiers[0].requires-human-approval=false",
                            "str.escalation.default-tiers[0].description=Initial",
                            "str.escalation.high-value-tiers[0].level=0",
                            "str.escalation.high-value-tiers[0].stuck-threshold=PT0S",
                            "str.escalation.high-value-tiers[0].gas-multiplier=1.0",
                            "str.escalation.high-value-tiers[0].requires-human-approval=false",
                            "str.escalation.high-value-tiers[0].description=Initial HV",
                            "str.temporal.target=temporal-test:7233",
                            "str.temporal.namespace=test-ns",
                            "str.temporal.task-queue=test-queue",
                            "str.temporal.workflow-execution-timeout=PT48H",
                            "str.temporal.workflow-run-timeout=PT4H",
                            "str.temporal.non-retryable-exceptions[0]=com.example.TestException",
                            "str.temporal.activity-options.default-options.start-to-close-timeout=PT60S",
                            "str.temporal.activity-options.default-options.max-attempts=5",
                            "str.temporal.activity-options.default-options.initial-interval=PT2S",
                            "str.temporal.activity-options.default-options.backoff-coefficient=3.0",
                            "str.temporal.activity-options.signing.start-to-close-timeout=PT15S",
                            "str.temporal.activity-options.signing.max-attempts=3",
                            "str.temporal.activity-options.signing.initial-interval=PT1S",
                            "str.temporal.activity-options.signing.backoff-coefficient=2.0",
                            "str.temporal.activity-options.confirmation.start-to-close-timeout=PT300S",
                            "str.temporal.activity-options.confirmation.max-attempts=1",
                            "str.temporal.activity-options.confirmation.initial-interval=PT1S",
                            "str.temporal.activity-options.confirmation.backoff-coefficient=2.0",
                            "str.temporal.activity-options.recovery-execution.start-to-close-timeout=PT60S",
                            "str.temporal.activity-options.recovery-execution.max-attempts=3",
                            "str.temporal.activity-options.recovery-execution.initial-interval=PT1S",
                            "str.temporal.activity-options.recovery-execution.backoff-coefficient=2.0",
                            "str.redis.host=redis-test",
                            "str.redis.port=6380",
                            "str.chains.ethereum_mainnet.enabled=true",
                            "str.chains.ethereum_mainnet.chain-family=EVM",
                            "str.chains.ethereum_mainnet.chain-id=1",
                            "str.chains.ethereum_mainnet.finality-blocks=12",
                            "str.chains.ethereum_mainnet.stuck-threshold-blocks=50",
                            "str.chains.ethereum_mainnet.poll-interval=PT12S",
                            "str.chains.ethereum_mainnet.max-fee-cap-gwei=200",
                            "str.chains.ethereum_mainnet.token-contracts[0]=0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48",
                            "str.chains.ethereum_mainnet.rpc.urls[0]=http://localhost:8545",
                            "str.chains.ethereum_mainnet.rpc.timeout=PT5S",
                            "str.chains.ethereum_mainnet.rpc.max-retries=3",
                            "str.chains.ethereum_mainnet.rpc.rate-limit-rps=25",
                            "str.chains.ethereum_mainnet.rpc.rate-limit-burst=50",
                            "str.chains.ethereum_mainnet.rpc.circuit-breaker.failure-rate-threshold=50",
                            "str.chains.ethereum_mainnet.rpc.circuit-breaker.wait-duration-in-open-state=PT30S",
                            "str.chains.ethereum_mainnet.rpc.circuit-breaker.sliding-window-size=10");

            runner.run(context -> {
                assertThat(context).hasSingleBean(StrProperties.class);

                var props = context.getBean(StrProperties.class);
                assertThat(props.api().key()).isEqualTo("test-key");
                assertThat(props.signer().backend()).isEqualTo("local-keystore");
                assertThat(props.signer().callback().hmacSecret()).isEqualTo("hmac-test");
                assertThat(props.signer().callback().timeout()).isEqualTo(Duration.ofSeconds(10));
                assertThat(props.signer().callback().tls().verify()).isFalse();

                assertThat(props.submission().sequentialThresholdUsd())
                        .isEqualByComparingTo(new BigDecimal("50000"));
                assertThat(props.submission().maxPipelineDepth()).isEqualTo(10);

                assertThat(props.escalation().highValueThresholdUsd())
                        .isEqualByComparingTo(new BigDecimal("75000"));
                assertThat(props.escalation().defaultTiers()).hasSize(1);
                assertThat(props.escalation().highValueTiers()).hasSize(1);

                assertThat(props.temporal().target()).isEqualTo("temporal-test:7233");
                assertThat(props.temporal().namespace()).isEqualTo("test-ns");
                assertThat(props.temporal().workflowExecutionTimeout())
                        .isEqualTo(Duration.ofHours(48));
                assertThat(props.temporal().workflowRunTimeout())
                        .isEqualTo(Duration.ofHours(4));
                assertThat(props.temporal().nonRetryableExceptions())
                        .containsExactly("com.example.TestException");
                assertThat(props.temporal().activityOptions().defaultOptions().maxAttempts())
                        .isEqualTo(5);
                assertThat(props.temporal().activityOptions().signing().startToCloseTimeout())
                        .isEqualTo(Duration.ofSeconds(15));

                assertThat(props.redis().host()).isEqualTo("redis-test");
                assertThat(props.redis().port()).isEqualTo(6380);

                assertThat(props.chains()).containsKey("ethereum_mainnet");
                var eth = props.chains().get("ethereum_mainnet");
                assertThat(eth.enabled()).isTrue();
                assertThat(eth.chainFamily()).isEqualTo("EVM");
                assertThat(eth.chainId()).isEqualTo(1L);
                assertThat(eth.finalityBlocks()).isEqualTo(12);
                assertThat(eth.rpc().urls()).containsExactly("http://localhost:8545");
                assertThat(eth.rpc().circuitBreaker().failureRateThreshold()).isEqualTo(50);
            });
        }

        @Configuration
        @EnableConfigurationProperties(StrProperties.class)
        static class TestConfig {}
    }
}
