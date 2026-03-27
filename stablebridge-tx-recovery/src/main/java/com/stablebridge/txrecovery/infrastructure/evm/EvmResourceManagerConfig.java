package com.stablebridge.txrecovery.infrastructure.evm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.stablebridge.txrecovery.domain.address.port.AddressPoolRepository;
import com.stablebridge.txrecovery.domain.address.port.NonceManager;
import com.stablebridge.txrecovery.domain.address.port.PoolExhaustedAlertPublisher;

@Configuration
@ConditionalOnBean(NonceManager.class)
class EvmResourceManagerConfig {

    static final int DEFAULT_MAX_PIPELINE_DEPTH = 20;

    @Bean
    EvmSubmissionResourceManager evmSubmissionResourceManager(
            AddressPoolRepository addressPoolRepository,
            NonceManager nonceManager,
            PoolExhaustedAlertPublisher poolExhaustedAlertPublisher,
            @Value("${str.evm.max-pipeline-depth:" + DEFAULT_MAX_PIPELINE_DEPTH + "}") int maxPipelineDepth) {
        return new EvmSubmissionResourceManager(
                addressPoolRepository, nonceManager, poolExhaustedAlertPublisher, maxPipelineDepth);
    }
}
