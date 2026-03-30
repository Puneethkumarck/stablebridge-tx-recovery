package com.stablebridge.txrecovery.application.config;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import lombok.Builder;

@Validated
@ConfigurationProperties(prefix = "str")
@Builder(toBuilder = true)
public record StrProperties(
        @Valid ApiProperties api,
        @Valid SignerProperties signer,
        @Valid EscalationProperties escalation,
        @Valid SubmissionProperties submission,
        @Valid TemporalConfigProperties temporal,
        @NotNull @Valid RedisConfigProperties redis,
        @NotEmpty Map<String, @Valid ChainProperties> chains) {

    public StrProperties {
        api = Objects.requireNonNullElse(api, new ApiProperties(null));
        signer = Objects.requireNonNullElse(signer, SignerProperties.builder().build());
        escalation = Objects.requireNonNullElse(escalation, EscalationProperties.builder().build());
        submission = Objects.requireNonNullElse(submission, new SubmissionProperties(null, null));
        temporal = Objects.requireNonNullElse(temporal, TemporalConfigProperties.builder().build());
        redis = Objects.requireNonNullElse(redis, new RedisConfigProperties(null, 0));
        chains = Objects.requireNonNullElse(chains, Map.of());
    }

    @Builder(toBuilder = true)
    public record ApiProperties(String key) {

        public ApiProperties {
            key = Objects.requireNonNullElse(key, "");
        }
    }

    @Builder(toBuilder = true)
    public record GasBudgetProperties(
            @NotNull @DecimalMin("0") BigDecimal percentage,
            @NotNull @DecimalMin("0") BigDecimal absoluteMinUsd,
            @NotNull @DecimalMin("0") BigDecimal absoluteMaxUsd) {

        public GasBudgetProperties {
            percentage = Objects.requireNonNullElse(percentage, new BigDecimal("0.01"));
            absoluteMinUsd = Objects.requireNonNullElse(absoluteMinUsd, new BigDecimal("5"));
            absoluteMaxUsd = Objects.requireNonNullElse(absoluteMaxUsd, new BigDecimal("500"));
            if (absoluteMinUsd.compareTo(absoluteMaxUsd) > 0) {
                throw new IllegalArgumentException("absoluteMinUsd must not exceed absoluteMaxUsd");
            }
        }
    }

    @Builder(toBuilder = true)
    public record EscalationTierProperties(
            @Min(0) int level,
            @NotNull Duration stuckThreshold,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal gasMultiplier,
            boolean requiresHumanApproval,
            @NotBlank String description) {}

    @Builder(toBuilder = true)
    public record EscalationProperties(
            @NotNull @DecimalMin("0") BigDecimal highValueThresholdUsd,
            @NotNull @Valid GasBudgetProperties gasBudget,
            @NotEmpty @Valid List<EscalationTierProperties> defaultTiers,
            @NotEmpty @Valid List<EscalationTierProperties> highValueTiers) {

        public EscalationProperties {
            highValueThresholdUsd = Objects.requireNonNullElse(
                    highValueThresholdUsd, new BigDecimal("50000"));
            gasBudget = Objects.requireNonNullElse(
                    gasBudget, GasBudgetProperties.builder().build());
            defaultTiers = Objects.requireNonNullElse(defaultTiers, List.of());
            highValueTiers = Objects.requireNonNullElse(highValueTiers, List.of());
        }
    }

    @Builder(toBuilder = true)
    public record SubmissionProperties(
            @NotNull @DecimalMin("0") BigDecimal sequentialThresholdUsd,
            @NotNull @Min(1) Integer maxPipelineDepth) {

        public SubmissionProperties {
            sequentialThresholdUsd = Objects.requireNonNullElse(
                    sequentialThresholdUsd, new BigDecimal("100000"));
            maxPipelineDepth = Objects.requireNonNullElse(maxPipelineDepth, 20);
        }
    }

    @Builder(toBuilder = true)
    public record SignerProperties(
            String backend,
            String keystorePath,
            String password,
            @Valid CallbackProperties callback) {

        public SignerProperties {
            callback = Objects.requireNonNullElse(
                    callback, CallbackProperties.builder().build());
        }

        @Builder(toBuilder = true)
        public record CallbackProperties(
                String endpoint,
                Duration timeout,
                String hmacSecret,
                @Valid TlsProperties tls) {

            public CallbackProperties {
                timeout = Objects.requireNonNullElse(timeout, Duration.ofSeconds(5));
                tls = Objects.requireNonNullElse(tls, new TlsProperties(true));
            }

            @Builder(toBuilder = true)
            public record TlsProperties(boolean verify) {}
        }
    }

    @Builder(toBuilder = true)
    public record ChainProperties(
            boolean enabled,
            @NotBlank String chainFamily,
            @NotNull Long chainId,
            @Min(1) int finalityBlocks,
            @Min(1) int stuckThresholdBlocks,
            @NotNull Duration pollInterval,
            BigDecimal maxFeeCapGwei,
            List<String> tokenContracts,
            List<String> tokenMints,
            @NotNull @Valid RpcProperties rpc) {

        public ChainProperties {
            tokenContracts = Objects.requireNonNullElse(tokenContracts, List.of());
            tokenMints = Objects.requireNonNullElse(tokenMints, List.of());
            rpc = Objects.requireNonNullElse(rpc, RpcProperties.builder().build());
        }

        @Builder(toBuilder = true)
        public record RpcProperties(
                @NotEmpty List<String> urls,
                @NotNull Duration timeout,
                @Min(1) int maxRetries,
                int rateLimitRps,
                int rateLimitBurst,
                @Valid CircuitBreakerProperties circuitBreaker) {

            public RpcProperties {
                urls = Objects.requireNonNullElse(urls, List.of());
                timeout = Objects.requireNonNullElse(timeout, Duration.ofSeconds(5));
                maxRetries = maxRetries <= 0 ? 3 : maxRetries;
                rateLimitRps = rateLimitRps <= 0 ? 25 : rateLimitRps;
                rateLimitBurst = rateLimitBurst <= 0 ? 50 : rateLimitBurst;
                circuitBreaker = Objects.requireNonNullElse(
                        circuitBreaker, CircuitBreakerProperties.builder().build());
            }

            @Builder(toBuilder = true)
            public record CircuitBreakerProperties(
                    int failureRateThreshold,
                    Duration waitDurationInOpenState,
                    int slidingWindowSize) {

                public CircuitBreakerProperties {
                    failureRateThreshold = failureRateThreshold <= 0 ? 50 : failureRateThreshold;
                    waitDurationInOpenState = Objects.requireNonNullElse(
                            waitDurationInOpenState, Duration.ofSeconds(30));
                    slidingWindowSize = slidingWindowSize <= 0 ? 10 : slidingWindowSize;
                }
            }
        }
    }

    @Builder(toBuilder = true)
    public record TemporalConfigProperties(
            @NotBlank String target,
            @NotBlank String namespace,
            @NotBlank String taskQueue,
            @NotNull Duration workflowExecutionTimeout,
            @NotNull Duration workflowRunTimeout,
            List<String> nonRetryableExceptions,
            @NotNull @Valid ActivityOptionsProperties activityOptions) {

        public TemporalConfigProperties {
            target = Objects.requireNonNullElse(target, "localhost:7233");
            namespace = Objects.requireNonNullElse(namespace, "stablebridge-tx-recovery");
            taskQueue = Objects.requireNonNullElse(taskQueue, "str-transaction-lifecycle");
            workflowExecutionTimeout = Objects.requireNonNullElse(
                    workflowExecutionTimeout, Duration.ofHours(24));
            workflowRunTimeout = Objects.requireNonNullElse(
                    workflowRunTimeout, Duration.ofHours(2));
            nonRetryableExceptions = Objects.requireNonNullElse(nonRetryableExceptions, List.of(
                    "com.stablebridge.txrecovery.domain.exception.NonRetryableException",
                    "com.stablebridge.txrecovery.domain.exception.NonceTooLowException"));
            activityOptions = Objects.requireNonNullElse(
                    activityOptions, ActivityOptionsProperties.builder().build());
        }

        @Builder(toBuilder = true)
        public record ActivityOptionsProperties(
                @NotNull @Valid ActivityConfig defaultOptions,
                @NotNull @Valid ActivityConfig signing,
                @NotNull @Valid ActivityConfig confirmation,
                @NotNull @Valid ActivityConfig recoveryExecution) {

            public ActivityOptionsProperties {
                defaultOptions = Objects.requireNonNullElse(defaultOptions, ActivityConfig.builder()
                        .startToCloseTimeout(Duration.ofSeconds(30))
                        .maxAttempts(3)
                        .initialInterval(Duration.ofSeconds(1))
                        .backoffCoefficient(2.0)
                        .build());
                signing = Objects.requireNonNullElse(signing, ActivityConfig.builder()
                        .startToCloseTimeout(Duration.ofSeconds(10))
                        .maxAttempts(2)
                        .initialInterval(Duration.ofSeconds(1))
                        .backoffCoefficient(2.0)
                        .build());
                confirmation = Objects.requireNonNullElse(confirmation, ActivityConfig.builder()
                        .startToCloseTimeout(Duration.ofMinutes(5))
                        .maxAttempts(1)
                        .initialInterval(Duration.ofSeconds(1))
                        .backoffCoefficient(2.0)
                        .build());
                recoveryExecution = Objects.requireNonNullElse(recoveryExecution, ActivityConfig.builder()
                        .startToCloseTimeout(Duration.ofSeconds(60))
                        .maxAttempts(3)
                        .initialInterval(Duration.ofSeconds(1))
                        .backoffCoefficient(2.0)
                        .build());
            }
        }

        @Builder(toBuilder = true)
        public record ActivityConfig(
                @NotNull Duration startToCloseTimeout,
                @NotNull @Min(1) Integer maxAttempts,
                @NotNull Duration initialInterval,
                @NotNull Double backoffCoefficient) {

            public ActivityConfig {
                startToCloseTimeout = Objects.requireNonNullElse(
                        startToCloseTimeout, Duration.ofSeconds(30));
                maxAttempts = Objects.requireNonNullElse(maxAttempts, 3);
                initialInterval = Objects.requireNonNullElse(
                        initialInterval, Duration.ofSeconds(1));
                backoffCoefficient = Objects.requireNonNullElse(backoffCoefficient, 2.0);
            }
        }
    }

    @Builder(toBuilder = true)
    public record RedisConfigProperties(
            @NotBlank String host,
            int port) {

        public RedisConfigProperties {
            host = Objects.requireNonNullElse(host, "localhost");
            if (port <= 0) {
                port = 6379;
            }
        }
    }
}
