package com.stablebridge.txrecovery.testutil;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.kafka.KafkaContainer;

public class KafkaContainerExtension implements BeforeAllCallback {

    private static final KafkaContainer CONTAINER = new KafkaContainer("apache/kafka:3.8.1");

    @Override
    public void beforeAll(ExtensionContext context) {
        if (!CONTAINER.isRunning()) {
            CONTAINER.start();
        }
        System.setProperty("spring.kafka.bootstrap-servers", CONTAINER.getBootstrapServers());
    }
}
