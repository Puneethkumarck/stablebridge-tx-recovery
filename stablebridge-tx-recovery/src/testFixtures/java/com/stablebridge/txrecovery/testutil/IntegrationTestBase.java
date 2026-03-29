package com.stablebridge.txrecovery.testutil;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "str.redis.enabled=true")
@ExtendWith({PostgresContainerExtension.class, KafkaContainerExtension.class, RedisContainerExtension.class})
public abstract class IntegrationTestBase {

    @Autowired
    protected ObjectMapper objectMapper;
}
