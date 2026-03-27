package com.stablebridge.txrecovery.testutil;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.GenericContainer;

public class RedisContainerExtension implements BeforeAllCallback {

    private static final int REDIS_PORT = 6379;

    @SuppressWarnings("resource")
    private static final GenericContainer<?> CONTAINER =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(REDIS_PORT);

    @Override
    public void beforeAll(ExtensionContext context) {
        if (!CONTAINER.isRunning()) {
            CONTAINER.start();
        }
        System.setProperty("str.redis.host", CONTAINER.getHost());
        System.setProperty("str.redis.port", String.valueOf(CONTAINER.getMappedPort(REDIS_PORT)));
    }
}
