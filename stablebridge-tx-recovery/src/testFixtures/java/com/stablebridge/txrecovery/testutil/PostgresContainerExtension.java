package com.stablebridge.txrecovery.testutil;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.PostgreSQLContainer;

public class PostgresContainerExtension implements BeforeAllCallback {

    private static final PostgreSQLContainer<?> CONTAINER =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("str_test")
                    .withUsername("test")
                    .withPassword("test");

    @Override
    public void beforeAll(ExtensionContext context) {
        if (!CONTAINER.isRunning()) {
            CONTAINER.start();
        }
        System.setProperty("spring.datasource.url", CONTAINER.getJdbcUrl());
        System.setProperty("spring.datasource.username", CONTAINER.getUsername());
        System.setProperty("spring.datasource.password", CONTAINER.getPassword());
    }
}
