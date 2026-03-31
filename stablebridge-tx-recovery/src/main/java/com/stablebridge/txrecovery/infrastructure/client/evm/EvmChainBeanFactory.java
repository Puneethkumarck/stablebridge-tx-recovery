package com.stablebridge.txrecovery.infrastructure.client.evm;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.stablebridge.txrecovery.domain.address.model.ChainFamily;
import com.stablebridge.txrecovery.domain.common.model.ChainConfig;
import com.stablebridge.txrecovery.domain.common.port.ChainBeanFactory;
import com.stablebridge.txrecovery.domain.recovery.port.FeeCache;
import com.stablebridge.txrecovery.domain.recovery.port.FeeOracle;
import com.stablebridge.txrecovery.domain.recovery.port.RecoveryStrategy;
import com.stablebridge.txrecovery.domain.transaction.port.ChainTransactionManager;
import com.stablebridge.txrecovery.domain.transaction.port.SubmissionResourceManager;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class EvmChainBeanFactory implements ChainBeanFactory {

    private final FeeCache feeCache;
    private final ObjectMapper objectMapper;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RateLimiterRegistry rateLimiterRegistry;
    private final SubmissionResourceManager evmSubmissionResourceManager;
    private final Clock clock;

    private final Map<String, EvmRpcClient> rpcClientCache = new ConcurrentHashMap<>();
    private final Map<String, FeeOracle> feeOracleCache = new ConcurrentHashMap<>();

    @Override
    public ChainFamily supportedFamily() {
        return ChainFamily.EVM;
    }

    public EvmRpcClient createRpcClient(ChainConfig config) {
        return rpcClientCache.computeIfAbsent(config.chainName(), _ ->
                new EvmRpcClient(
                        config.chainName(),
                        config.rpcUrls(),
                        config.rpcTimeout(),
                        config.rateLimitRps(),
                        config.rateLimitBurst(),
                        circuitBreakerRegistry,
                        rateLimiterRegistry,
                        objectMapper));
    }

    @Override
    public FeeOracle createFeeOracle(ChainConfig config) {
        return feeOracleCache.computeIfAbsent(config.chainName(), _ -> {
            var rpcClient = createRpcClient(config);
            var chainProperties = EvmChainProperties.builder()
                    .chain(config.chainName())
                    .maxFeeCapGwei(config.maxFeeCapGwei())
                    .blockTime(config.pollInterval())
                    .build();
            return new EvmFeeOracle(rpcClient, chainProperties, feeCache);
        });
    }

    @Override
    public ChainTransactionManager createTransactionManager(ChainConfig config) {
        var rpcClient = createRpcClient(config);
        var feeOracle = createFeeOracle(config);
        var txBuilder = new EvmTransactionBuilder(rpcClient, feeOracle, config.chainId());
        return new EvmChainTransactionManager(
                rpcClient, txBuilder, config.finalityBlocks(), config.stuckThresholdBlocks(), clock);
    }

    @Override
    public RecoveryStrategy createRecoveryStrategy(ChainConfig config, SubmissionResourceManager resourceManager) {
        var rpcClient = createRpcClient(config);
        var feeOracle = createFeeOracle(config);
        return new EvmRecoveryStrategy(rpcClient, feeOracle, config.chainId());
    }

    @Override
    public SubmissionResourceManager createResourceManager(ChainConfig config) {
        return evmSubmissionResourceManager;
    }
}
