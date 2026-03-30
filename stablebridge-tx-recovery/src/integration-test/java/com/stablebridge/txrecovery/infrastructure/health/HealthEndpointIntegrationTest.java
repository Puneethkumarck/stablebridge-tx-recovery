package com.stablebridge.txrecovery.infrastructure.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.stablebridge.txrecovery.testutil.RedisTest;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@RedisTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "management.server.port=")
class HealthEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Nested
    class HealthEndpoint {

        @Test
        void shouldReturnHealthWithDatabaseAndRedisComponents() throws Exception {
            // when
            var mvcResult = mockMvc.perform(get("/actuator/health"))
                    .andReturn();

            // then
            var body = objectMapper.readValue(
                    mvcResult.getResponse().getContentAsString(),
                    new TypeReference<Map<String, Object>>() {});
            assertThat(body).containsKey("status");
            assertThat(body).containsKey("components");

            @SuppressWarnings("unchecked")
            var components = (Map<String, Object>) body.get("components");
            assertThat(components).containsKey("db");
            assertThat(components).containsKey("redis");
        }

        @Test
        void shouldReturnRedisHealthUp() throws Exception {
            // when
            var mvcResult = mockMvc.perform(get("/actuator/health/redis"))
                    .andExpect(status().isOk())
                    .andReturn();

            // then
            var body = objectMapper.readValue(
                    mvcResult.getResponse().getContentAsString(),
                    new TypeReference<Map<String, Object>>() {});
            assertThat(body)
                    .containsEntry("status", "UP");
            assertThat(body).containsKey("details");
        }

        @Test
        void shouldReturnDatabaseHealthUp() throws Exception {
            // when
            var mvcResult = mockMvc.perform(get("/actuator/health/db"))
                    .andExpect(status().isOk())
                    .andReturn();

            // then
            var body = objectMapper.readValue(
                    mvcResult.getResponse().getContentAsString(),
                    new TypeReference<Map<String, Object>>() {});
            assertThat(body)
                    .containsEntry("status", "UP");
        }
    }
}
