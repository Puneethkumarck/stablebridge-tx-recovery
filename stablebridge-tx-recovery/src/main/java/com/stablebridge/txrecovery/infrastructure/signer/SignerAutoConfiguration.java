package com.stablebridge.txrecovery.infrastructure.signer;

import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.stablebridge.txrecovery.domain.transaction.port.TransactionSigner;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Configuration
@EnableConfigurationProperties({LocalSignerProperties.class, CallbackSignerProperties.class})
class SignerAutoConfiguration {

    @Bean
    @ConditionalOnProperty(name = "str.signer.backend", havingValue = "local-keystore")
    TransactionSigner localKeystoreSigner(LocalSignerProperties properties) {
        validateLocalKeystore(properties);
        log.info("Signer backend: local-keystore");
        return new LocalKeystoreSigner(Map.of());
    }

    @Bean
    @ConditionalOnProperty(name = "str.signer.backend", havingValue = "callback")
    TransactionSigner callbackSignerAdapter(CallbackSignerProperties properties, ObjectMapper objectMapper) {
        validateCallback(properties);
        log.info("Signer backend: callback");
        return new CallbackSignerAdapter(properties, objectMapper);
    }

    private static void validateLocalKeystore(LocalSignerProperties properties) {
        if (properties.keystorePath() == null || properties.keystorePath().isBlank()) {
            throw new SignerConfigurationException("str.signer.keystore-path must be set for local-keystore backend");
        }
        if (properties.password() == null || properties.password().isBlank()) {
            throw new SignerConfigurationException("str.signer.password must be set for local-keystore backend");
        }
    }

    private static void validateCallback(CallbackSignerProperties properties) {
        if (properties.hmacSecret() == null || properties.hmacSecret().isBlank()) {
            throw new SignerConfigurationException(
                    "str.signer.callback.hmac-secret must be set for callback backend");
        }
    }
}
