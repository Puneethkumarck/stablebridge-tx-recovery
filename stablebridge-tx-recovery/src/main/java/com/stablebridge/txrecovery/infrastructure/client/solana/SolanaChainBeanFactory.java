package com.stablebridge.txrecovery.infrastructure.client.solana;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.stablebridge.txrecovery.domain.address.model.ChainFamily;
import com.stablebridge.txrecovery.domain.address.port.NonceAccountPoolRepository;
import com.stablebridge.txrecovery.domain.address.port.PoolExhaustedAlertPublisher;
import com.stablebridge.txrecovery.domain.common.model.ChainConfig;
import com.stablebridge.txrecovery.domain.common.port.ChainBeanFactory;
import com.stablebridge.txrecovery.domain.recovery.port.FeeOracle;
import com.stablebridge.txrecovery.domain.recovery.port.RecoveryStrategy;
import com.stablebridge.txrecovery.domain.transaction.port.ChainTransactionManager;
import com.stablebridge.txrecovery.domain.transaction.port.SubmissionResourceManager;
import com.stablebridge.txrecovery.domain.transaction.port.TransactionIntentStore;
import com.stablebridge.txrecovery.infrastructure.redis.RedisFeeCache;
import com.stablebridge.txrecovery.infrastructure.solana.SolanaSubmissionResourceManager;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class SolanaChainBeanFactory implements ChainBeanFactory {

    private static final int DEFAULT_MIN_AVAILABLE = 3;
    private static final long DEFAULT_MAX_PRIORITY_FEE_MICRO_LAMPORTS = 10_000_000L;
    private static final Duration DEFAULT_BLOCK_TIME = Duration.ofMillis(400);
    private static final int DEFAULT_COMPUTE_UNIT_LIMIT = 200_000;

    private final ObjectMapper objectMapper;
    private final RedisFeeCache feeCache;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RateLimiterRegistry rateLimiterRegistry;
    private final NonceAccountPoolRepository nonceAccountPoolRepository;
    private final PoolExhaustedAlertPublisher poolExhaustedAlertPublisher;
    private final TransactionIntentStore transactionIntentStore;
    private final Clock clock;

    private final Map<String, SolanaRpcClient> rpcClientCache = new ConcurrentHashMap<>();

    @Override
    public ChainFamily supportedFamily() {
        return ChainFamily.SOLANA;
    }

    public SolanaRpcClient createRpcClient(ChainConfig config) {
        return rpcClientCache.computeIfAbsent(config.chainName(), _ -> {
            var endpoints = config.rpcUrls().stream().map(URI::create).toList();
            var cb = createCircuitBreaker(config);
            var rl = createRateLimiter(config);
            var httpClient = HttpClient.newBuilder()
                    .connectTimeout(config.rpcTimeout())
                    .build();
            return new SolanaRpcClient(httpClient, objectMapper, endpoints, config.rpcTimeout(), cb, rl);
        });
    }

    @Override
    public FeeOracle createFeeOracle(ChainConfig config) {
        var rpcClient = createRpcClient(config);
        var chainProperties = SolanaChainProperties.builder()
                .chain(config.chainName())
                .maxPriorityFeeMicroLamports(DEFAULT_MAX_PRIORITY_FEE_MICRO_LAMPORTS)
                .blockTime(DEFAULT_BLOCK_TIME)
                .programAddresses(config.tokenMints())
                .build();
        return new SolanaFeeOracle(rpcClient, chainProperties, feeCache);
    }

    @Override
    public ChainTransactionManager createTransactionManager(ChainConfig config) {
        var rpcClient = createRpcClient(config);
        var feeOracle = createFeeOracle(config);
        var txBuilder = new SolanaTransactionBuilder(rpcClient, feeOracle, DEFAULT_COMPUTE_UNIT_LIMIT);
        var stuckThresholdSeconds = (long) config.stuckThresholdBlocks() * config.pollInterval().toSeconds();
        return new SolanaChainTransactionManager(rpcClient, txBuilder, config.chainName(),
                stuckThresholdSeconds, clock);
    }

    @Override
    public RecoveryStrategy createRecoveryStrategy(ChainConfig config, SubmissionResourceManager resourceManager) {
        var rpcClient = createRpcClient(config);
        var feeOracle = createFeeOracle(config);
        var txBuilder = new SolanaTransactionBuilder(rpcClient, feeOracle, DEFAULT_COMPUTE_UNIT_LIMIT);
        return new SolanaRecoveryStrategy(rpcClient, txBuilder, resourceManager, transactionIntentStore);
    }

    @Override
    public SubmissionResourceManager createResourceManager(ChainConfig config) {
        var rpcClient = createRpcClient(config);
        return new SolanaSubmissionResourceManager(
                nonceAccountPoolRepository, rpcClient, poolExhaustedAlertPublisher, DEFAULT_MIN_AVAILABLE);
    }

    private io.github.resilience4j.circuitbreaker.CircuitBreaker createCircuitBreaker(ChainConfig config) {
        var cbConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(config.cbFailureRateThreshold())
                .waitDurationInOpenState(config.cbWaitDurationInOpenState())
                .slidingWindowSize(config.cbSlidingWindowSize())
                .build();
        return circuitBreakerRegistry.circuitBreaker("solana-rpc-" + config.chainName(), cbConfig);
    }

    private io.github.resilience4j.ratelimiter.RateLimiter createRateLimiter(ChainConfig config) {
        var rlConfig = RateLimiterConfig.custom()
                .limitForPeriod(config.rateLimitRps())
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .timeoutDuration(config.rpcTimeout())
                .build();
        return rateLimiterRegistry.rateLimiter("solana-rpc-" + config.chainName(), rlConfig);
    }
}
