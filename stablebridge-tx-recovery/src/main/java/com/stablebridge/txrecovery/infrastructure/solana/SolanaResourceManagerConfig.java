package com.stablebridge.txrecovery.infrastructure.solana;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.stablebridge.txrecovery.domain.address.port.NonceAccountPoolRepository;
import com.stablebridge.txrecovery.domain.address.port.PoolExhaustedAlertPublisher;
import com.stablebridge.txrecovery.infrastructure.client.solana.SolanaRpcClient;

@Configuration
@ConditionalOnBean(SolanaRpcClient.class)
class SolanaResourceManagerConfig {

    static final int DEFAULT_MIN_AVAILABLE = 3;

    @Bean
    SolanaSubmissionResourceManager solanaSubmissionResourceManager(
            NonceAccountPoolRepository nonceAccountPoolRepository,
            SolanaRpcClient rpcClient,
            PoolExhaustedAlertPublisher poolExhaustedAlertPublisher,
            @Value("${str.solana.min-available:" + DEFAULT_MIN_AVAILABLE + "}") int minAvailable) {
        return new SolanaSubmissionResourceManager(
                nonceAccountPoolRepository, rpcClient, poolExhaustedAlertPublisher, minAvailable);
    }
}
