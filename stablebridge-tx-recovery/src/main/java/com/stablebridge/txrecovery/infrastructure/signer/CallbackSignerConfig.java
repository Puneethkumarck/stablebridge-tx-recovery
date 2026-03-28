package com.stablebridge.txrecovery.infrastructure.signer;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CallbackSignerProperties.class)
class CallbackSignerConfig {
}
