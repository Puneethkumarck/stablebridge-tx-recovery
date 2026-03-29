package com.stablebridge.txrecovery.testutil;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
public abstract class ControllerIntegrationTestBase extends IntegrationTestBase {

    @Autowired
    protected MockMvc mockMvc;
}
