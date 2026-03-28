package com.stablebridge.txrecovery.infrastructure.stream;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "str.kafka")
public record KafkaProperties(List<String> enabledChains, int topicReplicas) {

    public KafkaProperties {
        if (enabledChains == null) {
            enabledChains = List.of();
        }
        if (topicReplicas <= 0) {
            topicReplicas = 1;
        }
    }
}
