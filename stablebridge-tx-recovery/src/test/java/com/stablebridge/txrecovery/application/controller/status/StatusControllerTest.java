package com.stablebridge.txrecovery.application.controller.status;

import static com.stablebridge.txrecovery.testutil.fixtures.StatusControllerFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stablebridge.txrecovery.api.model.ChainStatusDetailResponse;
import com.stablebridge.txrecovery.api.model.ChainStatusSummaryResponse;
import com.stablebridge.txrecovery.application.controller.GlobalExceptionHandler;
import com.stablebridge.txrecovery.application.controller.status.mapper.StatusControllerMapper;
import com.stablebridge.txrecovery.domain.exception.ChainNotFoundException;
import com.stablebridge.txrecovery.domain.status.StatusService;

@ExtendWith(MockitoExtension.class)
class StatusControllerTest {

    @Mock
    private StatusService statusService;

    @Mock
    private StatusControllerMapper mapper;

    @InjectMocks
    private StatusController statusController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        mockMvc = MockMvcBuilders.standaloneSetup(statusController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Nested
    class GetAllStatuses {

        @Test
        void shouldReturnAllChainStatuses() throws Exception {
            given(statusService.getAllChainStatuses()).willReturn(SOME_CHAIN_STATUS_SUMMARIES);
            given(mapper.toSummaryResponseList(SOME_CHAIN_STATUS_SUMMARIES))
                    .willReturn(List.of(SOME_CHAIN_STATUS_SUMMARY_RESPONSE));

            var result = mockMvc.perform(get("/api/v1/status"))
                    .andExpect(status().isOk())
                    .andReturn();

            var response = objectMapper.readValue(
                    result.getResponse().getContentAsString(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, ChainStatusSummaryResponse.class));
            assertThat(response)
                    .usingRecursiveComparison()
                    .isEqualTo(List.of(SOME_CHAIN_STATUS_SUMMARY_RESPONSE));
        }
    }

    @Nested
    class GetChainStatus {

        @Test
        void shouldReturnDetailedChainStatus() throws Exception {
            given(statusService.getChainStatus(SOME_CHAIN)).willReturn(SOME_CHAIN_STATUS_DETAIL);
            given(mapper.toDetailResponse(SOME_CHAIN_STATUS_DETAIL))
                    .willReturn(SOME_CHAIN_STATUS_DETAIL_RESPONSE);

            var result = mockMvc.perform(get("/api/v1/status/{chain}", SOME_CHAIN))
                    .andExpect(status().isOk())
                    .andReturn();

            var response = objectMapper.readValue(
                    result.getResponse().getContentAsString(), ChainStatusDetailResponse.class);
            assertThat(response)
                    .usingRecursiveComparison()
                    .isEqualTo(SOME_CHAIN_STATUS_DETAIL_RESPONSE);
        }

        @Test
        void shouldReturn404ForUnknownChain() throws Exception {
            given(statusService.getChainStatus(SOME_UNKNOWN_CHAIN))
                    .willThrow(new ChainNotFoundException(SOME_UNKNOWN_CHAIN));

            mockMvc.perform(get("/api/v1/status/{chain}", SOME_UNKNOWN_CHAIN))
                    .andExpect(status().isNotFound());
        }
    }
}
