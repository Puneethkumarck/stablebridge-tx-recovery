package com.stablebridge.txrecovery.infrastructure.signer;

import java.util.Map;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.stablebridge.txrecovery.domain.transaction.port.TransactionSigner;

@Configuration
@EnableConfigurationProperties(LocalSignerProperties.class)
class LocalSignerConfig {

    @Bean
    TransactionSigner localKeystoreSigner(LocalSignerProperties properties) {
        return new LocalKeystoreSigner(Map.of());
    }
}
