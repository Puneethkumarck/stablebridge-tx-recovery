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
        @Valid RedisConfigProperties redis,
        @NotEmpty Map<String, @Valid ChainProperties> chains) {

    public StrProperties {
        api = Objects.requireNonNullElse(api, new ApiProperties(null));
        signer = Objects.requireNonNullElse(signer, SignerProperties.builder().build());
        escalation = Objects.requireNonNullElse(escalation, EscalationProperties.builder().build());
        submission = Objects.requireNonNullElse(submission, new SubmissionProperties(null, null));
        temporal = Objects.requireNonNullElse(temporal, TemporalConfigProperties.builder().build());
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
            BigDecimal sequentialThresholdUsd,
            Integer maxPipelineDepth) {

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
            @NotNull @Valid WorkflowProperties workflow,
            @NotNull @Valid ActivityProperties activity) {

        public TemporalConfigProperties {
            target = Objects.requireNonNullElse(target, "localhost:7233");
            namespace = Objects.requireNonNullElse(namespace, "stablebridge-tx-recovery");
            taskQueue = Objects.requireNonNullElse(taskQueue, "str-transaction-lifecycle");
            workflow = Objects.requireNonNullElse(
                    workflow, WorkflowProperties.builder().build());
            activity = Objects.requireNonNullElse(
                    activity, ActivityProperties.builder().build());
        }

        @Builder(toBuilder = true)
        public record WorkflowProperties(
                @NotNull Duration executionTimeout,
                @NotNull Duration runTimeout) {

            public WorkflowProperties {
                executionTimeout = Objects.requireNonNullElse(
                        executionTimeout, Duration.ofHours(24));
                runTimeout = Objects.requireNonNullElse(
                        runTimeout, Duration.ofHours(2));
            }
        }

        @Builder(toBuilder = true)
        public record ActivityProperties(
                @NotNull Duration startToCloseTimeout,
                @Min(1) int retryMaxAttempts) {

            public ActivityProperties {
                startToCloseTimeout = Objects.requireNonNullElse(
                        startToCloseTimeout, Duration.ofSeconds(30));
                retryMaxAttempts = retryMaxAttempts <= 0 ? 3 : retryMaxAttempts;
            }
        }
    }

    @Builder(toBuilder = true)
    public record RedisConfigProperties(
            String host,
            int port) {

        public RedisConfigProperties {
            if (port <= 0) {
                port = 6379;
            }
        }
    }
}
