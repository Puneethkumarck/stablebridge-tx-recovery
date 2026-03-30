package com.stablebridge.txrecovery.infrastructure.health;

import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.health.contributor.CompositeHealthContributor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthContributor;
import org.springframework.boot.health.contributor.HealthContributors;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import com.stablebridge.txrecovery.infrastructure.client.evm.EvmRpcClient;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@ConditionalOnBean(name = "evmRpcClients")
public class RpcHealthIndicator implements CompositeHealthContributor {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    private final Map<String, HealthContributor> chainIndicators;

    public RpcHealthIndicator(Map<String, EvmRpcClient> evmRpcClients, Map<String, Duration> chainRpcTimeouts) {
        this.chainIndicators = evmRpcClients.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> createChainIndicator(
                                entry.getKey(), entry.getValue(),
                                chainRpcTimeouts.getOrDefault(entry.getKey(), DEFAULT_TIMEOUT))));
    }

    @Override
    public HealthContributor getContributor(String name) {
        return chainIndicators.get(name);
    }

    @Override
    public Stream<HealthContributors.Entry> stream() {
        return chainIndicators.entrySet().stream()
                .map(entry -> new HealthContributors.Entry(entry.getKey(), entry.getValue()));
    }

    private HealthContributor createChainIndicator(String chain, EvmRpcClient client, Duration timeout) {
        return (HealthIndicator) () -> checkChainHealth(chain, client, timeout);
    }

    private Health checkChainHealth(String chain, EvmRpcClient client, Duration timeout) {
        try {
            var start = System.nanoTime();
            var block = client.getBlockByNumber("latest", false);
            var latencyMs = (System.nanoTime() - start) / 1_000_000;
            var timeoutMs = timeout.toMillis();

            var builder = latencyMs <= timeoutMs ? Health.up() : Health.down();
            return builder
                    .withDetail("latencyMs", latencyMs)
                    .withDetail("blockNumber", block.number())
                    .build();
        } catch (Exception e) {
            log.warn("RPC health check failed for chain {}", chain, e);
            return Health.down()
                    .withException(e)
                    .build();
        }
    }
}
