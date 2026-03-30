package com.stablebridge.txrecovery.testutil;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.stablebridge.txrecovery.api.model.ErrorResponse;

@AutoConfigureMockMvc
public abstract class ControllerIntegrationTestBase extends IntegrationTestBase {

    protected static final String API_KEY_HEADER = "X-API-Key";

    @Autowired
    protected MockMvc mockMvc;

    @Value("${str.api.key}")
    protected String apiKey;

    protected MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder requestBuilder) {
        return requestBuilder.header(API_KEY_HEADER, apiKey);
    }

    protected MockHttpServletRequestBuilder authenticatedJson(MockHttpServletRequestBuilder requestBuilder,
            String body) {
        return authenticated(requestBuilder)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    protected void assertErrorResponse(MvcResult mvcResult, HttpStatus status, String errorCode, String message)
            throws Exception {
        var response = objectMapper.readValue(
                mvcResult.getResponse().getContentAsString(), ErrorResponse.class);
        var expected = ErrorResponse.builder()
                .errorCode(errorCode)
                .message(message)
                .build();
        assertThat(response)
                .usingRecursiveComparison()
                .ignoringFields("timestamp", "path", "details")
                .isEqualTo(expected);
    }

    protected void assertValidationError(MvcResult mvcResult, String... expectedFieldKeys) throws Exception {
        var response = objectMapper.readValue(
                mvcResult.getResponse().getContentAsString(), ErrorResponse.class);
        var expected = ErrorResponse.builder()
                .errorCode("STR-4000")
                .message("Validation failed")
                .build();
        assertThat(response)
                .usingRecursiveComparison()
                .ignoringFields("timestamp", "path", "details")
                .isEqualTo(expected);
        assertThat(response.details()).containsKeys(expectedFieldKeys);
    }
}
