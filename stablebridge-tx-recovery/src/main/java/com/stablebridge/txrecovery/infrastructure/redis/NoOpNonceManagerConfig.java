package com.stablebridge.txrecovery.infrastructure.redis;

import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.stablebridge.txrecovery.domain.address.model.NonceAllocation;
import com.stablebridge.txrecovery.domain.address.port.NonceManager;
import com.stablebridge.txrecovery.domain.exception.NonceManagementDisabledException;

@Configuration
class NoOpNonceManagerConfig {

    @Bean
    @ConditionalOnMissingBean(NonceManager.class)
    NonceManager noOpNonceManager() {
        return new NonceManager() {

            @Override
            public NonceAllocation allocate(String address, String chain) {
                throw new NonceManagementDisabledException();
            }

            @Override
            public void release(NonceAllocation allocation) {}

            @Override
            public void confirm(NonceAllocation allocation) {}

            @Override
            public void syncFromChain(String address, String chain) {}

            @Override
            public Set<Long> detectGaps(String address, String chain) {
                return Set.of();
            }
        };
    }
}
