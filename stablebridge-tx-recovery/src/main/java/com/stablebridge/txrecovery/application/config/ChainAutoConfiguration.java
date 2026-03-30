package com.stablebridge.txrecovery.application.config;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.stablebridge.txrecovery.domain.address.model.ChainFamily;
import com.stablebridge.txrecovery.domain.common.model.ChainConfig;
import com.stablebridge.txrecovery.domain.common.port.ChainBeanFactory;
import com.stablebridge.txrecovery.domain.recovery.port.FeeOracle;
import com.stablebridge.txrecovery.domain.recovery.port.RecoveryStrategy;
import com.stablebridge.txrecovery.domain.transaction.port.ChainTransactionManager;
import com.stablebridge.txrecovery.domain.transaction.port.SubmissionResourceManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(StrProperties.class)
public class ChainAutoConfiguration {

    private final StrProperties strProperties;
    private final List<ChainBeanFactory> chainBeanFactories;
    private Map<ChainFamily, ChainBeanFactory> factoryByFamilyCache;

    @PostConstruct
    void initFactoryCache() {
        factoryByFamilyCache = chainBeanFactories.stream()
                .collect(Collectors.toMap(ChainBeanFactory::supportedFamily, Function.identity()));
    }

    @Bean
    Map<String, ChainConfig> chainConfigs() {
        var configs = new HashMap<String, ChainConfig>();
        enabledChainConfigs().forEach(config -> configs.put(config.chainName(), config));
        return Map.copyOf(configs);
    }

    @Bean
    Map<String, FeeOracle> chainFeeOracles() {
        var oracles = new HashMap<String, FeeOracle>();
        enabledChainConfigs().forEach(config -> {
            var factory = resolveFactory(config.chainFamily());
            oracles.put(config.chainName(), factory.createFeeOracle(config));
        });
        log.info("Registered FeeOracles for chains: {}", oracles.keySet());
        return Map.copyOf(oracles);
    }

    @Bean
    Map<String, ChainTransactionManager> chainTransactionManagers() {
        var managers = new HashMap<String, ChainTransactionManager>();
        enabledChainConfigs().forEach(config -> {
            var factory = resolveFactory(config.chainFamily());
            managers.put(config.chainName(), factory.createTransactionManager(config));
        });
        log.info("Registered ChainTransactionManagers for chains: {}", managers.keySet());
        return Map.copyOf(managers);
    }

    @Bean
    Map<String, SubmissionResourceManager> chainResourceManagers() {
        var managers = new HashMap<String, SubmissionResourceManager>();
        enabledChainConfigs().forEach(config -> {
            var factory = resolveFactory(config.chainFamily());
            managers.put(config.chainName(), factory.createResourceManager(config));
        });
        log.info("Registered SubmissionResourceManagers for chains: {}", managers.keySet());
        return Map.copyOf(managers);
    }

    @Bean
    Map<String, RecoveryStrategy> chainRecoveryStrategies(
            Map<String, SubmissionResourceManager> chainResourceManagers) {
        var strategies = new HashMap<String, RecoveryStrategy>();
        enabledChainConfigs().forEach(config -> {
            var factory = resolveFactory(config.chainFamily());
            var resourceManager = chainResourceManagers.get(config.chainName());
            strategies.put(config.chainName(), factory.createRecoveryStrategy(config, resourceManager));
        });
        log.info("Registered RecoveryStrategies for chains: {}", strategies.keySet());
        return Map.copyOf(strategies);
    }

    @Bean
    Map<String, Duration> chainPollIntervals() {
        var intervals = new HashMap<String, Duration>();
        enabledChainConfigs().forEach(config ->
                intervals.put(config.chainName(), config.pollInterval()));
        return Map.copyOf(intervals);
    }

    @Bean
    ChainAdapterRegistry chainAdapterRegistry(
            Map<String, ChainTransactionManager> chainTransactionManagers,
            Map<String, SubmissionResourceManager> chainResourceManagers,
            Map<String, RecoveryStrategy> chainRecoveryStrategies) {
        return new ChainAdapterRegistry(
                chainTransactionManagers,
                chainResourceManagers,
                chainRecoveryStrategies);
    }

    private List<ChainConfig> enabledChainConfigs() {
        return strProperties.chains().entrySet().stream()
                .filter(e -> e.getValue().enabled())
                .map(e -> toChainConfig(e.getKey(), e.getValue()))
                .toList();
    }

    private ChainBeanFactory resolveFactory(ChainFamily family) {
        var factory = factoryByFamilyCache.get(family);
        if (factory == null) {
            throw new IllegalStateException(
                    "No ChainBeanFactory registered for chain family '%s'".formatted(family));
        }
        return factory;
    }

    private static ChainConfig toChainConfig(String name, StrProperties.ChainProperties props) {
        var rpc = props.rpc();
        var chainFamily = parseChainFamily(name, props.chainFamily());
        return ChainConfig.builder()
                .chainName(name)
                .chainFamily(chainFamily)
                .chainId(props.chainId())
                .finalityBlocks(props.finalityBlocks())
                .stuckThresholdBlocks(props.stuckThresholdBlocks())
                .pollInterval(props.pollInterval())
                .maxFeeCapGwei(props.maxFeeCapGwei())
                .tokenContracts(props.tokenContracts())
                .tokenMints(props.tokenMints())
                .rpcUrls(rpc.urls())
                .rpcTimeout(rpc.timeout())
                .rateLimitRps(rpc.rateLimitRps())
                .rateLimitBurst(rpc.rateLimitBurst())
                .cbFailureRateThreshold(rpc.circuitBreaker().failureRateThreshold())
                .cbWaitDurationInOpenState(rpc.circuitBreaker().waitDurationInOpenState())
                .cbSlidingWindowSize(rpc.circuitBreaker().slidingWindowSize())
                .build();
    }

    private static ChainFamily parseChainFamily(String chainName, String chainFamilyValue) {
        try {
            return ChainFamily.valueOf(chainFamilyValue);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid chainFamily '%s' for chain '%s'".formatted(chainFamilyValue, chainName), e);
        }
    }
}
