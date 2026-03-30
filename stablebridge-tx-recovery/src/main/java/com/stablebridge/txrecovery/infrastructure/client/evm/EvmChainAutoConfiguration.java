package com.stablebridge.txrecovery.infrastructure.client.evm;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.stablebridge.txrecovery.domain.address.model.ChainFamily;
import com.stablebridge.txrecovery.domain.address.port.OnChainNonceProvider;
import com.stablebridge.txrecovery.domain.common.model.ChainConfig;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@ConditionalOnBean(EvmChainBeanFactory.class)
class EvmChainAutoConfiguration {

    @Bean
    Map<String, EvmRpcClient> evmRpcClients(
            EvmChainBeanFactory factory,
            Map<String, ChainConfig> chainConfigs) {
        var clients = new HashMap<String, EvmRpcClient>();
        chainConfigs.forEach((name, config) -> {
            if (config.chainFamily() == ChainFamily.EVM) {
                clients.put(name, factory.createRpcClient(config));
            }
        });
        log.info("Created EVM RPC clients for chains: {}", clients.keySet());
        return Map.copyOf(clients);
    }

    @Bean
    OnChainNonceProvider onChainNonceProvider(Map<String, EvmRpcClient> evmRpcClients) {
        return new EvmOnChainNonceProvider(evmRpcClients);
    }
}
