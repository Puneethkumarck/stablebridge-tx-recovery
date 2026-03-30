package com.stablebridge.txrecovery.application.controller.address;

import static com.stablebridge.txrecovery.testutil.fixtures.AddressPoolControllerFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.stablebridge.txrecovery.api.model.AddressResponse;
import com.stablebridge.txrecovery.api.model.NonceSyncResponse;
import com.stablebridge.txrecovery.domain.address.AddressPoolService;
import com.stablebridge.txrecovery.domain.address.model.AddressStatus;
import com.stablebridge.txrecovery.domain.address.model.AddressTier;
import com.stablebridge.txrecovery.domain.address.model.ChainFamily;
import com.stablebridge.txrecovery.domain.exception.AddressNotFoundException;
import com.stablebridge.txrecovery.domain.exception.DuplicateAddressException;
import com.stablebridge.txrecovery.testutil.ControllerIntegrationTestBase;
import com.stablebridge.txrecovery.testutil.PgTest;

import tools.jackson.core.type.TypeReference;

@PgTest
class AddressPoolControllerIntegrationTest extends ControllerIntegrationTestBase {

    private static final String BASE_PATH = "/api/v1/addresses";

    @MockitoBean
    private AddressPoolService addressPoolService;

    @Nested
    class RegisterAddress {

        @Test
        void shouldRegisterAddressAndReturn201() throws Exception {
            given(addressPoolService.register(
                    SOME_EVM_ADDRESS, SOME_CHAIN, ChainFamily.EVM, AddressTier.HOT, SOME_SIGNER_ENDPOINT))
                    .willReturn(SOME_REGISTERED_ADDRESS);

            var result = mockMvc.perform(authenticatedJson(
                            post(BASE_PATH),
                            objectMapper.writeValueAsString(SOME_REGISTER_REQUEST)))
                    .andExpect(status().isCreated())
                    .andReturn();

            var response = objectMapper.readValue(
                    result.getResponse().getContentAsString(), AddressResponse.class);
            assertThat(response)
                    .usingRecursiveComparison()
                    .isEqualTo(SOME_ADDRESS_RESPONSE);
        }

        @Test
        void shouldReturn409WhenAddressAlreadyRegistered() throws Exception {
            given(addressPoolService.register(
                    SOME_EVM_ADDRESS, SOME_CHAIN, ChainFamily.EVM, AddressTier.HOT, SOME_SIGNER_ENDPOINT))
                    .willThrow(new DuplicateAddressException(SOME_EVM_ADDRESS, SOME_CHAIN));

            var result = mockMvc.perform(authenticatedJson(
                            post(BASE_PATH),
                            objectMapper.writeValueAsString(SOME_REGISTER_REQUEST)))
                    .andExpect(status().isConflict())
                    .andReturn();

            assertErrorResponse(result, HttpStatus.CONFLICT, "STR-4091",
                    "Address already registered: %s on chain %s".formatted(SOME_EVM_ADDRESS, SOME_CHAIN));
        }

        @Test
        void shouldReturn400WhenAddressIsBlank() throws Exception {
            var invalidRequest = SOME_REGISTER_REQUEST.toBuilder().address("").build();

            var result = mockMvc.perform(authenticatedJson(
                            post(BASE_PATH),
                            objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertValidationError(result, "address");
        }

        @Test
        void shouldReturn400WhenRequestBodyIsMissing() throws Exception {
            var result = mockMvc.perform(authenticated(
                            post(BASE_PATH))
                            .contentType("application/json"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertErrorResponse(result, HttpStatus.BAD_REQUEST, "STR-4000", "Malformed request body");
        }
    }

    @Nested
    class ListAddresses {

        @Test
        void shouldListAddressesWithFilters() throws Exception {
            given(addressPoolService.list(SOME_CHAIN, AddressTier.HOT, AddressStatus.ACTIVE))
                    .willReturn(List.of(SOME_REGISTERED_ADDRESS));

            var result = mockMvc.perform(authenticated(get(BASE_PATH))
                            .param("chain", SOME_CHAIN)
                            .param("tier", "HOT")
                            .param("status", "ACTIVE"))
                    .andExpect(status().isOk())
                    .andReturn();

            var response = objectMapper.readValue(
                    result.getResponse().getContentAsString(),
                    new TypeReference<List<AddressResponse>>() {});
            assertThat(response).hasSize(1);
            assertThat(response.getFirst())
                    .usingRecursiveComparison()
                    .isEqualTo(SOME_ADDRESS_RESPONSE);
        }

        @Test
        void shouldListAllAddressesWithoutFilters() throws Exception {
            given(addressPoolService.list(null, null, null))
                    .willReturn(List.of(SOME_REGISTERED_ADDRESS, SOME_DRAINING_ADDRESS));

            var result = mockMvc.perform(authenticated(get(BASE_PATH)))
                    .andExpect(status().isOk())
                    .andReturn();

            var response = objectMapper.readValue(
                    result.getResponse().getContentAsString(),
                    new TypeReference<List<AddressResponse>>() {});
            assertThat(response).hasSize(2);
        }
    }

    @Nested
    class DrainAddress {

        @Test
        void shouldDrainAddressAndReturn200() throws Exception {
            given(addressPoolService.drain(SOME_EVM_ADDRESS, SOME_CHAIN))
                    .willReturn(SOME_DRAINING_ADDRESS);

            var result = mockMvc.perform(authenticated(
                            delete(BASE_PATH + "/{address}", SOME_EVM_ADDRESS))
                            .param("chain", SOME_CHAIN))
                    .andExpect(status().isOk())
                    .andReturn();

            var response = objectMapper.readValue(
                    result.getResponse().getContentAsString(), AddressResponse.class);
            assertThat(response.status()).isEqualTo("DRAINING");
        }

        @Test
        void shouldReturn404WhenAddressNotFound() throws Exception {
            given(addressPoolService.drain("0xUnknown", SOME_CHAIN))
                    .willThrow(new AddressNotFoundException("0xUnknown", SOME_CHAIN));

            var result = mockMvc.perform(authenticated(
                            delete(BASE_PATH + "/{address}", "0xUnknown"))
                            .param("chain", SOME_CHAIN))
                    .andExpect(status().isNotFound())
                    .andReturn();

            assertErrorResponse(result, HttpStatus.NOT_FOUND, "STR-4042",
                    "Address not found: 0xUnknown on chain %s".formatted(SOME_CHAIN));
        }

        @Test
        void shouldReturn400WhenChainParamIsMissing() throws Exception {
            mockMvc.perform(authenticated(
                            delete(BASE_PATH + "/{address}", SOME_EVM_ADDRESS)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class SyncNonce {

        @Test
        void shouldSyncNonceAndReturn200() throws Exception {
            given(addressPoolService.list(SOME_CHAIN, null, null))
                    .willReturn(List.of(SOME_REGISTERED_ADDRESS));
            given(addressPoolService.syncNonce(SOME_EVM_ADDRESS, SOME_CHAIN))
                    .willReturn(SOME_SYNCED_ADDRESS);

            var result = mockMvc.perform(authenticated(
                            post(BASE_PATH + "/{address}/nonces/sync", SOME_EVM_ADDRESS))
                            .param("chain", SOME_CHAIN))
                    .andExpect(status().isOk())
                    .andReturn();

            var response = objectMapper.readValue(
                    result.getResponse().getContentAsString(), NonceSyncResponse.class);
            assertThat(response.previousNonce()).isEqualTo(42);
            assertThat(response.currentNonce()).isEqualTo(100);
        }

        @Test
        void shouldReturn404WhenSyncingUnknownAddress() throws Exception {
            given(addressPoolService.list(SOME_CHAIN, null, null))
                    .willReturn(List.of());
            given(addressPoolService.syncNonce("0xUnknown", SOME_CHAIN))
                    .willThrow(new AddressNotFoundException("0xUnknown", SOME_CHAIN));

            var result = mockMvc.perform(authenticated(
                            post(BASE_PATH + "/{address}/nonces/sync", "0xUnknown"))
                            .param("chain", SOME_CHAIN))
                    .andExpect(status().isNotFound())
                    .andReturn();

            assertErrorResponse(result, HttpStatus.NOT_FOUND, "STR-4042",
                    "Address not found: 0xUnknown on chain %s".formatted(SOME_CHAIN));
        }
    }
}
