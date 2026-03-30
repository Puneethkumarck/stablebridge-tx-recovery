package com.stablebridge.txrecovery.application.controller.gas;

import static com.stablebridge.txrecovery.testutil.fixtures.GasOracleControllerFixtures.*;
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
import com.stablebridge.txrecovery.api.model.GasEstimateResponse;
import com.stablebridge.txrecovery.api.model.GasHistoryResponse;
import com.stablebridge.txrecovery.application.controller.GlobalExceptionHandler;
import com.stablebridge.txrecovery.application.controller.gas.mapper.GasOracleControllerMapper;
import com.stablebridge.txrecovery.domain.exception.ChainNotFoundException;
import com.stablebridge.txrecovery.domain.recovery.GasOracleQueryService;

@ExtendWith(MockitoExtension.class)
class GasOracleControllerTest {

    @Mock
    private GasOracleQueryService gasOracleQueryService;

    @Mock
    private GasOracleControllerMapper mapper;

    @InjectMocks
    private GasOracleController gasOracleController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        mockMvc = MockMvcBuilders.standaloneSetup(gasOracleController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Nested
    class GetCurrentEstimates {

        @Test
        void shouldReturnCurrentFeeEstimatesForChain() throws Exception {
            given(gasOracleQueryService.getCurrentEstimates(SOME_CHAIN))
                    .willReturn(SOME_GAS_SNAPSHOT);
            given(mapper.toResponse(SOME_GAS_SNAPSHOT))
                    .willReturn(SOME_GAS_ESTIMATE_RESPONSE);

            var result = mockMvc.perform(get("/api/v1/gas/{chain}", SOME_CHAIN))
                    .andExpect(status().isOk())
                    .andReturn();

            var response = objectMapper.readValue(
                    result.getResponse().getContentAsString(), GasEstimateResponse.class);
            assertThat(response)
                    .usingRecursiveComparison()
                    .isEqualTo(SOME_GAS_ESTIMATE_RESPONSE);
        }

        @Test
        void shouldReturn404ForUnknownChain() throws Exception {
            given(gasOracleQueryService.getCurrentEstimates(SOME_UNKNOWN_CHAIN))
                    .willThrow(new ChainNotFoundException(SOME_UNKNOWN_CHAIN));

            mockMvc.perform(get("/api/v1/gas/{chain}", SOME_UNKNOWN_CHAIN))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class GetHistory {

        @Test
        void shouldReturnHistoryWithDefaultHours() throws Exception {
            var historyResponse = GasHistoryResponse.builder()
                    .chain(SOME_CHAIN)
                    .hours(24)
                    .entries(List.of())
                    .build();

            given(gasOracleQueryService.getHistoricalEstimates(SOME_CHAIN, 24))
                    .willReturn(List.of());
            given(mapper.toHistoryResponse(SOME_CHAIN, 24, List.of()))
                    .willReturn(historyResponse);

            var result = mockMvc.perform(get("/api/v1/gas/{chain}/history", SOME_CHAIN))
                    .andExpect(status().isOk())
                    .andReturn();

            var response = objectMapper.readValue(
                    result.getResponse().getContentAsString(), GasHistoryResponse.class);
            assertThat(response)
                    .usingRecursiveComparison()
                    .isEqualTo(historyResponse);
        }

        @Test
        void shouldReturnHistoryWithCustomHours() throws Exception {
            var historyResponse = GasHistoryResponse.builder()
                    .chain(SOME_CHAIN)
                    .hours(48)
                    .entries(List.of())
                    .build();

            given(gasOracleQueryService.getHistoricalEstimates(SOME_CHAIN, 48))
                    .willReturn(List.of());
            given(mapper.toHistoryResponse(SOME_CHAIN, 48, List.of()))
                    .willReturn(historyResponse);

            var result = mockMvc.perform(get("/api/v1/gas/{chain}/history", SOME_CHAIN)
                            .param("hours", "48"))
                    .andExpect(status().isOk())
                    .andReturn();

            var response = objectMapper.readValue(
                    result.getResponse().getContentAsString(), GasHistoryResponse.class);
            assertThat(response)
                    .usingRecursiveComparison()
                    .isEqualTo(historyResponse);
        }

        @Test
        void shouldReturn404ForUnknownChainHistory() throws Exception {
            given(gasOracleQueryService.getHistoricalEstimates(SOME_UNKNOWN_CHAIN, 24))
                    .willThrow(new ChainNotFoundException(SOME_UNKNOWN_CHAIN));

            mockMvc.perform(get("/api/v1/gas/{chain}/history", SOME_UNKNOWN_CHAIN))
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldReturn400ForNegativeHours() throws Exception {
            given(gasOracleQueryService.getHistoricalEstimates(SOME_CHAIN, -1))
                    .willThrow(new IllegalArgumentException("Hours must be between 1 and 168"));

            mockMvc.perform(get("/api/v1/gas/{chain}/history", SOME_CHAIN)
                            .param("hours", "-1"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void shouldReturn400ForZeroHours() throws Exception {
            given(gasOracleQueryService.getHistoricalEstimates(SOME_CHAIN, 0))
                    .willThrow(new IllegalArgumentException("Hours must be between 1 and 168"));

            mockMvc.perform(get("/api/v1/gas/{chain}/history", SOME_CHAIN)
                            .param("hours", "0"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void shouldReturn400ForHoursExceedingMax() throws Exception {
            given(gasOracleQueryService.getHistoricalEstimates(SOME_CHAIN, 169))
                    .willThrow(new IllegalArgumentException("Hours must be between 1 and 168"));

            mockMvc.perform(get("/api/v1/gas/{chain}/history", SOME_CHAIN)
                            .param("hours", "169"))
                    .andExpect(status().isBadRequest());
        }
    }
}
