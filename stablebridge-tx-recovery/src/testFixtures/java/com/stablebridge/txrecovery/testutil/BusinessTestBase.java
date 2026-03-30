package com.stablebridge.txrecovery.testutil;

import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
public abstract class BusinessTestBase extends IntegrationTestBase {}
