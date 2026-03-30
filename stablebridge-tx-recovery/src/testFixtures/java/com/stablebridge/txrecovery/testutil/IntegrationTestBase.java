package com.stablebridge.txrecovery.testutil;

import org.springframework.beans.factory.annotation.Autowired;

import tools.jackson.databind.ObjectMapper;

@PgTest
public abstract class IntegrationTestBase {

    @Autowired
    protected ObjectMapper objectMapper;
}
