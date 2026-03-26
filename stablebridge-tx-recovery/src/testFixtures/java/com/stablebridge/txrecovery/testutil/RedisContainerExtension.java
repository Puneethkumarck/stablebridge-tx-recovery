package com.stablebridge.txrecovery.testutil;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.GenericContainer;

public class RedisContainerExtension implements BeforeAllCallback {

    private static final GenericContainer<?> CONTAINER =
            new GenericContainer<>("redis:7.2-alpine").withExposedPorts(6379);

    @Override
    public void beforeAll(ExtensionContext context) {
        if (!CONTAINER.isRunning()) {
            CONTAINER.start();
        }
        System.setProperty("spring.data.redis.host", CONTAINER.getHost());
        System.setProperty("spring.data.redis.port", String.valueOf(CONTAINER.getMappedPort(6379)));
    }
}
