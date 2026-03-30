package com.stablebridge.txrecovery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.TestPropertySource;

import com.stablebridge.txrecovery.testutil.IntegrationTestBase;
import com.stablebridge.txrecovery.testutil.KafkaContainerExtension;
import com.stablebridge.txrecovery.testutil.RedisContainerExtension;

@ExtendWith({KafkaContainerExtension.class, RedisContainerExtension.class})
@TestPropertySource(properties = "str.redis.enabled=true")
class ApplicationTest extends IntegrationTestBase {

    @Test
    void contextLoads() {}
}
