package com.stablebridge.txrecovery.application.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class ApiKeyAuthFilterTest {

    static final String VALID_API_KEY = "test-secret-key-12345";
    static final String API_ENDPOINT = "/api/v1/test";
    static final String ACTUATOR_ENDPOINT = "/actuator/health";

    @RestController
    static class TestController {

        @GetMapping("/api/v1/test")
        ResponseEntity<String> apiEndpoint() {
            return ResponseEntity.ok("{\"status\": \"ok\"}");
        }

        @GetMapping("/actuator/health")
        ResponseEntity<String> actuatorEndpoint() {
            return ResponseEntity.ok("{\"status\": \"UP\"}");
        }
    }

    private MockMvc buildMockMvc(String apiKey) {
        return MockMvcBuilders.standaloneSetup(new TestController())
                .addFilter(new ApiKeyAuthFilter(apiKey), "/api/v1/*")
                .build();
    }

    @Nested
    class WhenApiKeyConfigured {

        private final MockMvc mockMvc = buildMockMvc(VALID_API_KEY);

        @Test
        void shouldAllowRequestWithValidApiKey() throws Exception {
            // when / then
            mockMvc.perform(get(API_ENDPOINT).header(ApiKeyAuthFilter.API_KEY_HEADER, VALID_API_KEY))
                    .andExpect(status().isOk())
                    .andExpect(content().string("{\"status\": \"ok\"}"));
        }

        @Test
        void shouldRejectRequestWithInvalidApiKey() throws Exception {
            // when / then
            mockMvc.perform(get(API_ENDPOINT).header(ApiKeyAuthFilter.API_KEY_HEADER, "wrong-key"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
                    .andExpect(jsonPath("$.error").value(ApiKeyAuthFilter.ERROR_CODE))
                    .andExpect(jsonPath("$.message").value(ApiKeyAuthFilter.ERROR_MESSAGE));
        }

        @Test
        void shouldRejectRequestWithMissingApiKey() throws Exception {
            // when / then
            mockMvc.perform(get(API_ENDPOINT))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
                    .andExpect(jsonPath("$.error").value(ApiKeyAuthFilter.ERROR_CODE))
                    .andExpect(jsonPath("$.message").value(ApiKeyAuthFilter.ERROR_MESSAGE));
        }

        @Test
        void shouldAllowActuatorWithoutApiKey() throws Exception {
            // when / then
            mockMvc.perform(get(ACTUATOR_ENDPOINT))
                    .andExpect(status().isOk())
                    .andExpect(content().string("{\"status\": \"UP\"}"));
        }
    }

    @Nested
    class WhenApiKeyNotConfigured {

        private final MockMvc mockMvc = buildMockMvc("");

        @Test
        void shouldRejectRequestWhenApiKeyNotConfigured() throws Exception {
            // when / then
            mockMvc.perform(get(API_ENDPOINT).header(ApiKeyAuthFilter.API_KEY_HEADER, "any-key"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
                    .andExpect(jsonPath("$.error").value(ApiKeyAuthFilter.ERROR_CODE))
                    .andExpect(jsonPath("$.message").value(ApiKeyAuthFilter.ERROR_MESSAGE));
        }
    }

    @Nested
    class WhenApiKeyNull {

        private final MockMvc mockMvc = buildMockMvc(null);

        @Test
        void shouldRejectRequestWhenApiKeyIsNull() throws Exception {
            // when / then
            mockMvc.perform(get(API_ENDPOINT).header(ApiKeyAuthFilter.API_KEY_HEADER, "any-key"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
                    .andExpect(jsonPath("$.error").value(ApiKeyAuthFilter.ERROR_CODE))
                    .andExpect(jsonPath("$.message").value(ApiKeyAuthFilter.ERROR_MESSAGE));
        }
    }
}
