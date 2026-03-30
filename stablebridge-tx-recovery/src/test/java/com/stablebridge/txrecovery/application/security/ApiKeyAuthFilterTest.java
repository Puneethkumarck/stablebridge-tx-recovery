package com.stablebridge.txrecovery.application.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class ApiKeyAuthFilterTest {

    static final String OPS_LEAD_KEY = "key-for-ops-lead";
    static final String COMPLIANCE_KEY = "key-for-compliance";
    static final String API_ENDPOINT = "/api/v1/test";
    static final String ACTUATOR_ENDPOINT = "/actuator/health";

    static final Map<String, String> OPERATOR_KEY_MAP = Map.of(
            OPS_LEAD_KEY, "ops-lead",
            COMPLIANCE_KEY, "compliance-officer");

    @RestController
    static class TestController {

        @GetMapping("/api/v1/test")
        ResponseEntity<String> apiEndpoint(HttpServletRequest request) {
            var operator = request.getAttribute(ApiKeyAuthFilter.OPERATOR_IDENTITY_ATTRIBUTE);
            return ResponseEntity.ok("{\"operator\": \"" + operator + "\"}");
        }

        @GetMapping("/actuator/health")
        ResponseEntity<String> actuatorEndpoint() {
            return ResponseEntity.ok("{\"status\": \"UP\"}");
        }
    }

    private MockMvc buildMockMvc(Map<String, String> keyToOperator) {
        return MockMvcBuilders.standaloneSetup(new TestController())
                .addFilter(new ApiKeyAuthFilter(keyToOperator), "/api/v1/*")
                .build();
    }

    @Nested
    class WhenOperatorsConfigured {

        private final MockMvc mockMvc = buildMockMvc(OPERATOR_KEY_MAP);

        @Test
        void shouldAllowRequestWithValidApiKey() throws Exception {
            mockMvc.perform(get(API_ENDPOINT).header(ApiKeyAuthFilter.API_KEY_HEADER, OPS_LEAD_KEY))
                    .andExpect(status().isOk());
        }

        @Test
        void shouldSetOperatorIdentityForOpsLead() throws Exception {
            var result = mockMvc.perform(
                            get(API_ENDPOINT).header(ApiKeyAuthFilter.API_KEY_HEADER, OPS_LEAD_KEY))
                    .andExpect(status().isOk())
                    .andReturn();

            assertThat(result.getRequest().getAttribute(ApiKeyAuthFilter.OPERATOR_IDENTITY_ATTRIBUTE))
                    .isEqualTo("ops-lead");
        }

        @Test
        void shouldSetOperatorIdentityForComplianceOfficer() throws Exception {
            var result = mockMvc.perform(
                            get(API_ENDPOINT).header(ApiKeyAuthFilter.API_KEY_HEADER, COMPLIANCE_KEY))
                    .andExpect(status().isOk())
                    .andReturn();

            assertThat(result.getRequest().getAttribute(ApiKeyAuthFilter.OPERATOR_IDENTITY_ATTRIBUTE))
                    .isEqualTo("compliance-officer");
        }

        @Test
        void shouldReturnOperatorInResponseBody() throws Exception {
            mockMvc.perform(get(API_ENDPOINT).header(ApiKeyAuthFilter.API_KEY_HEADER, OPS_LEAD_KEY))
                    .andExpect(status().isOk())
                    .andExpect(content().string("{\"operator\": \"ops-lead\"}"));
        }

        @Test
        void shouldRejectRequestWithInvalidApiKey() throws Exception {
            mockMvc.perform(get(API_ENDPOINT).header(ApiKeyAuthFilter.API_KEY_HEADER, "wrong-key"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
                    .andExpect(jsonPath("$.error").value(ApiKeyAuthFilter.ERROR_CODE))
                    .andExpect(jsonPath("$.message").value(ApiKeyAuthFilter.ERROR_MESSAGE));
        }

        @Test
        void shouldRejectRequestWithMissingApiKey() throws Exception {
            mockMvc.perform(get(API_ENDPOINT))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
                    .andExpect(jsonPath("$.error").value(ApiKeyAuthFilter.ERROR_CODE))
                    .andExpect(jsonPath("$.message").value(ApiKeyAuthFilter.ERROR_MESSAGE));
        }

        @Test
        void shouldAllowActuatorWithoutApiKey() throws Exception {
            mockMvc.perform(get(ACTUATOR_ENDPOINT))
                    .andExpect(status().isOk())
                    .andExpect(content().string("{\"status\": \"UP\"}"));
        }
    }

    @Nested
    class WhenSingleKeyFallback {

        private final MockMvc mockMvc = buildMockMvc(Map.of("legacy-key", "system"));

        @Test
        void shouldAllowRequestWithLegacyKey() throws Exception {
            mockMvc.perform(get(API_ENDPOINT).header(ApiKeyAuthFilter.API_KEY_HEADER, "legacy-key"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("{\"operator\": \"system\"}"));
        }

        @Test
        void shouldSetSystemOperatorIdentity() throws Exception {
            var result = mockMvc.perform(
                            get(API_ENDPOINT).header(ApiKeyAuthFilter.API_KEY_HEADER, "legacy-key"))
                    .andExpect(status().isOk())
                    .andReturn();

            assertThat(result.getRequest().getAttribute(ApiKeyAuthFilter.OPERATOR_IDENTITY_ATTRIBUTE))
                    .isEqualTo("system");
        }
    }

    @Nested
    class WhenNoKeysConfigured {

        private final MockMvc mockMvc = buildMockMvc(Map.of());

        @Test
        void shouldRejectRequestWhenNoKeysConfigured() throws Exception {
            mockMvc.perform(get(API_ENDPOINT).header(ApiKeyAuthFilter.API_KEY_HEADER, "any-key"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
                    .andExpect(jsonPath("$.error").value(ApiKeyAuthFilter.ERROR_CODE))
                    .andExpect(jsonPath("$.message").value(ApiKeyAuthFilter.ERROR_MESSAGE));
        }
    }
}
