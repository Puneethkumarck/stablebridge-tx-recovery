package com.stablebridge.txrecovery.application.controller.transaction;

import static com.stablebridge.txrecovery.testutil.fixtures.TransactionControllerFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.github.f4b6a3.uuid.UuidCreator;
import com.stablebridge.txrecovery.api.model.BatchTransactionResponse;
import com.stablebridge.txrecovery.api.model.ErrorResponse;
import com.stablebridge.txrecovery.api.model.PagedResponse;
import com.stablebridge.txrecovery.api.model.SubmitBatchRequest;
import com.stablebridge.txrecovery.api.model.SubmitTransactionRequest;
import com.stablebridge.txrecovery.api.model.TransactionResponse;
import com.stablebridge.txrecovery.application.controller.transaction.mapper.TransactionControllerMapper;
import com.stablebridge.txrecovery.domain.exception.DuplicateIntentException;
import com.stablebridge.txrecovery.domain.exception.TransactionNotFoundException;
import com.stablebridge.txrecovery.domain.transaction.TransactionSubmissionService;
import com.stablebridge.txrecovery.domain.transaction.model.PagedResult;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionFilters;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionProjection;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionStatus;
import com.stablebridge.txrecovery.testutil.ControllerIntegrationTestBase;

import tools.jackson.core.type.TypeReference;

class TransactionControllerIntegrationTest extends ControllerIntegrationTestBase {

    private static final String BASE_PATH = "/api/v1/transactions";
    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String TEST_API_KEY = "test-api-key";

    private static final SubmitTransactionRequest SOME_SUBMIT_REQUEST = SubmitTransactionRequest.builder()
            .intentId(SOME_INTENT_ID)
            .chain(SOME_CHAIN)
            .toAddress(SOME_TO_ADDRESS)
            .amount(SOME_AMOUNT)
            .token(SOME_TOKEN)
            .tokenDecimals(6)
            .tokenContractAddress(SOME_TOKEN_CONTRACT)
            .build();

    private static final TransactionResponse SOME_TRANSACTION_RESPONSE = TransactionResponse.builder()
            .transactionId(SOME_TRANSACTION_ID)
            .intentId(SOME_INTENT_ID)
            .chain(SOME_CHAIN)
            .status("RECEIVED")
            .toAddress(SOME_TO_ADDRESS)
            .amount(SOME_AMOUNT)
            .token(SOME_TOKEN)
            .retryCount(0)
            .submittedAt(Instant.parse("2026-01-01T00:00:00Z"))
            .build();

    @MockitoBean
    private TransactionSubmissionService transactionSubmissionService;

    @MockitoBean
    private TransactionControllerMapper transactionControllerMapper;

    @Nested
    class Authentication {

        @Test
        void shouldReturn401WhenApiKeyIsMissing() throws Exception {
            // when / then
            mockMvc.perform(post(BASE_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(SOME_SUBMIT_REQUEST)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void shouldReturn401WhenApiKeyIsInvalid() throws Exception {
            // when / then
            mockMvc.perform(post(BASE_PATH)
                            .header(API_KEY_HEADER, "wrong-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(SOME_SUBMIT_REQUEST)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class SubmitTransaction {

        @Test
        void shouldSubmitTransactionAndReturn201() throws Exception {
            // given
            given(transactionControllerMapper.toDomain(SOME_SUBMIT_REQUEST))
                    .willReturn(SOME_TRANSACTION_INTENT);
            given(transactionSubmissionService.submitTransaction(SOME_TRANSACTION_INTENT))
                    .willReturn(SOME_TRANSACTION_PROJECTION);
            given(transactionControllerMapper.toResponse(SOME_TRANSACTION_PROJECTION))
                    .willReturn(SOME_TRANSACTION_RESPONSE);

            // when
            var result = mockMvc.perform(post(BASE_PATH)
                            .header(API_KEY_HEADER, TEST_API_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(SOME_SUBMIT_REQUEST)))
                    .andExpect(status().isCreated())
                    .andReturn();

            // then
            var response = objectMapper.readValue(
                    result.getResponse().getContentAsString(), TransactionResponse.class);
            assertThat(response)
                    .usingRecursiveComparison()
                    .isEqualTo(SOME_TRANSACTION_RESPONSE);
        }

        @Test
        void shouldReturnExistingTransactionWhenDuplicateIntentId() throws Exception {
            // given
            var existingProjection = SOME_TRANSACTION_PROJECTION.toBuilder()
                    .transactionId("existing-tx-001")
                    .build();
            var existingResponse = SOME_TRANSACTION_RESPONSE.toBuilder()
                    .transactionId("existing-tx-001")
                    .build();

            given(transactionControllerMapper.toDomain(SOME_SUBMIT_REQUEST))
                    .willReturn(SOME_TRANSACTION_INTENT);
            given(transactionSubmissionService.submitTransaction(SOME_TRANSACTION_INTENT))
                    .willThrow(new DuplicateIntentException("existing-tx-001"));
            given(transactionSubmissionService.findById("existing-tx-001"))
                    .willReturn(existingProjection);
            given(transactionControllerMapper.toResponse(existingProjection))
                    .willReturn(existingResponse);

            // when
            var result = mockMvc.perform(post(BASE_PATH)
                            .header(API_KEY_HEADER, TEST_API_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(SOME_SUBMIT_REQUEST)))
                    .andExpect(status().isOk())
                    .andReturn();

            // then
            var response = objectMapper.readValue(
                    result.getResponse().getContentAsString(), TransactionResponse.class);
            assertThat(response)
                    .usingRecursiveComparison()
                    .isEqualTo(existingResponse);
        }

        @Test
        void shouldReturn400WhenChainIsBlank() throws Exception {
            // given
            var invalidRequest = SOME_SUBMIT_REQUEST.toBuilder()
                    .chain("")
                    .build();

            // when
            var result = mockMvc.perform(post(BASE_PATH)
                            .header(API_KEY_HEADER, TEST_API_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            // then
            var response = objectMapper.readValue(
                    result.getResponse().getContentAsString(), ErrorResponse.class);
            var expected = ErrorResponse.builder()
                    .errorCode("STR-4000")
                    .message("Validation failed")
                    .details(response.details())
                    .build();
            assertThat(response)
                    .usingRecursiveComparison()
                    .ignoringFields("timestamp", "path")
                    .isEqualTo(expected);
            assertThat(response.details()).containsKey("chain");
        }

        @Test
        void shouldReturn400WhenAmountIsNegative() throws Exception {
            // given
            var invalidRequest = SOME_SUBMIT_REQUEST.toBuilder()
                    .amount(new BigDecimal("-10.00"))
                    .build();

            // when
            var result = mockMvc.perform(post(BASE_PATH)
                            .header(API_KEY_HEADER, TEST_API_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            // then
            var response = objectMapper.readValue(
                    result.getResponse().getContentAsString(), ErrorResponse.class);
            var expected = ErrorResponse.builder()
                    .errorCode("STR-4000")
                    .message("Validation failed")
                    .details(response.details())
                    .build();
            assertThat(response)
                    .usingRecursiveComparison()
                    .ignoringFields("timestamp", "path")
                    .isEqualTo(expected);
            assertThat(response.details()).containsKey("amount");
        }

        @Test
        void shouldReturn400WhenToAddressIsBlank() throws Exception {
            // given
            var invalidRequest = SOME_SUBMIT_REQUEST.toBuilder()
                    .toAddress("")
                    .build();

            // when
            var result = mockMvc.perform(post(BASE_PATH)
                            .header(API_KEY_HEADER, TEST_API_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            // then
            var response = objectMapper.readValue(
                    result.getResponse().getContentAsString(), ErrorResponse.class);
            var expected = ErrorResponse.builder()
                    .errorCode("STR-4000")
                    .message("Validation failed")
                    .details(response.details())
                    .build();
            assertThat(response)
                    .usingRecursiveComparison()
                    .ignoringFields("timestamp", "path")
                    .isEqualTo(expected);
            assertThat(response.details()).containsKey("toAddress");
        }

        @Test
        void shouldReturn400WhenRequestBodyIsMissing() throws Exception {
            // when
            var result = mockMvc.perform(post(BASE_PATH)
                            .header(API_KEY_HEADER, TEST_API_KEY)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            // then
            var response = objectMapper.readValue(
                    result.getResponse().getContentAsString(), ErrorResponse.class);
            var expected = ErrorResponse.builder()
                    .errorCode("STR-4000")
                    .message("Malformed request body")
                    .build();
            assertThat(response)
                    .usingRecursiveComparison()
                    .ignoringFields("timestamp", "path")
                    .isEqualTo(expected);
        }
    }

    @Nested
    class SubmitBatch {

        @Test
        void shouldSubmitBatchAndReturn201() throws Exception {
            // given
            var batchRequest = SubmitBatchRequest.builder()
                    .transactions(List.of(
                            SOME_SUBMIT_REQUEST,
                            SOME_SUBMIT_REQUEST.toBuilder()
                                    .intentId(SOME_SECOND_INTENT_ID)
                                    .build()))
                    .build();

            var secondIntent = SOME_TRANSACTION_INTENT.toBuilder()
                    .intentId(SOME_SECOND_INTENT_ID)
                    .build();
            var secondProjection = SOME_TRANSACTION_PROJECTION.toBuilder()
                    .transactionId("tx-67890")
                    .intentId(SOME_SECOND_INTENT_ID)
                    .build();
            var secondResponse = SOME_TRANSACTION_RESPONSE.toBuilder()
                    .transactionId("tx-67890")
                    .intentId(SOME_SECOND_INTENT_ID)
                    .build();

            given(transactionControllerMapper.toDomain(SOME_SUBMIT_REQUEST))
                    .willReturn(SOME_TRANSACTION_INTENT);
            given(transactionControllerMapper.toDomain(SOME_SUBMIT_REQUEST.toBuilder()
                    .intentId(SOME_SECOND_INTENT_ID).build()))
                    .willReturn(secondIntent);
            given(transactionSubmissionService.submitBatch(
                    org.mockito.ArgumentMatchers.argThat(intents ->
                            intents != null && intents.size() == 2),
                    org.mockito.ArgumentMatchers.argThat(batchId ->
                            batchId != null && !batchId.isBlank())))
                    .willReturn(List.of(SOME_TRANSACTION_PROJECTION, secondProjection));
            given(transactionControllerMapper.toResponseList(
                    List.of(SOME_TRANSACTION_PROJECTION, secondProjection)))
                    .willReturn(List.of(SOME_TRANSACTION_RESPONSE, secondResponse));

            // when
            var result = mockMvc.perform(post(BASE_PATH + "/batch")
                            .header(API_KEY_HEADER, TEST_API_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(batchRequest)))
                    .andExpect(status().isCreated())
                    .andReturn();

            // then
            var response = objectMapper.readValue(
                    result.getResponse().getContentAsString(), BatchTransactionResponse.class);
            assertThat(response.transactions()).hasSize(2);
            assertThat(response.batchId()).isNotBlank();
        }

        @Test
        void shouldReturn400WhenBatchExceedsMaxSize() throws Exception {
            // given
            var tooManyTransactions = IntStream.rangeClosed(1, 101)
                    .mapToObj(_ -> SOME_SUBMIT_REQUEST.toBuilder()
                            .intentId(UuidCreator.getTimeOrderedEpoch().toString())
                            .build())
                    .toList();
            var oversizedBatch = SubmitBatchRequest.builder()
                    .transactions(tooManyTransactions)
                    .build();

            // when
            var result = mockMvc.perform(post(BASE_PATH + "/batch")
                            .header(API_KEY_HEADER, TEST_API_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(oversizedBatch)))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            // then
            var response = objectMapper.readValue(
                    result.getResponse().getContentAsString(), ErrorResponse.class);
            assertThat(response.errorCode()).isEqualTo("STR-4000");
        }

        @Test
        void shouldReturn400WhenBatchIsEmpty() throws Exception {
            // given
            var emptyBatch = SubmitBatchRequest.builder()
                    .transactions(List.of())
                    .build();

            // when
            var result = mockMvc.perform(post(BASE_PATH + "/batch")
                            .header(API_KEY_HEADER, TEST_API_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(emptyBatch)))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            // then
            var response = objectMapper.readValue(
                    result.getResponse().getContentAsString(), ErrorResponse.class);
            assertThat(response.errorCode()).isEqualTo("STR-4000");
        }
    }

    @Nested
    class GetTransaction {

        @Test
        void shouldReturnTransactionById() throws Exception {
            // given
            given(transactionSubmissionService.findById(SOME_TRANSACTION_ID))
                    .willReturn(SOME_TRANSACTION_PROJECTION);
            given(transactionControllerMapper.toResponse(SOME_TRANSACTION_PROJECTION))
                    .willReturn(SOME_TRANSACTION_RESPONSE);

            // when
            var result = mockMvc.perform(get(BASE_PATH + "/{transactionId}", SOME_TRANSACTION_ID)
                            .header(API_KEY_HEADER, TEST_API_KEY))
                    .andExpect(status().isOk())
                    .andReturn();

            // then
            var response = objectMapper.readValue(
                    result.getResponse().getContentAsString(), TransactionResponse.class);
            assertThat(response)
                    .usingRecursiveComparison()
                    .isEqualTo(SOME_TRANSACTION_RESPONSE);
        }

        @Test
        void shouldReturn404WhenTransactionNotFound() throws Exception {
            // given
            given(transactionSubmissionService.findById("non-existent-tx"))
                    .willThrow(new TransactionNotFoundException("non-existent-tx"));

            // when
            var result = mockMvc.perform(get(BASE_PATH + "/{transactionId}", "non-existent-tx")
                            .header(API_KEY_HEADER, TEST_API_KEY))
                    .andExpect(status().isNotFound())
                    .andReturn();

            // then
            var response = objectMapper.readValue(
                    result.getResponse().getContentAsString(), ErrorResponse.class);
            var expected = ErrorResponse.builder()
                    .errorCode("STR-4041")
                    .message("Transaction not found: non-existent-tx")
                    .build();
            assertThat(response)
                    .usingRecursiveComparison()
                    .ignoringFields("timestamp", "path")
                    .isEqualTo(expected);
        }
    }

    @Nested
    class ListTransactions {

        @Test
        void shouldListTransactionsWithFilters() throws Exception {
            // given
            var expectedFilters = TransactionFilters.builder()
                    .chain(SOME_CHAIN)
                    .status(TransactionStatus.RECEIVED)
                    .fromAddress("0xsender001")
                    .toAddress(SOME_TO_ADDRESS)
                    .build();
            var pagedResult = PagedResult.<TransactionProjection>builder()
                    .content(List.of(SOME_TRANSACTION_PROJECTION))
                    .totalElements(1)
                    .totalPages(1)
                    .build();

            given(transactionSubmissionService.findByFilters(expectedFilters, 0, 10))
                    .willReturn(pagedResult);
            given(transactionControllerMapper.toResponseList(List.of(SOME_TRANSACTION_PROJECTION)))
                    .willReturn(List.of(SOME_TRANSACTION_RESPONSE));

            // when
            var result = mockMvc.perform(get(BASE_PATH)
                            .header(API_KEY_HEADER, TEST_API_KEY)
                            .param("chain", SOME_CHAIN)
                            .param("status", "RECEIVED")
                            .param("fromAddress", "0xsender001")
                            .param("toAddress", SOME_TO_ADDRESS)
                            .param("page", "0")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andReturn();

            // then
            var response = objectMapper.readValue(
                    result.getResponse().getContentAsString(),
                    new TypeReference<PagedResponse<TransactionResponse>>() {});
            var expected = PagedResponse.<TransactionResponse>builder()
                    .content(List.of(SOME_TRANSACTION_RESPONSE))
                    .page(0)
                    .size(10)
                    .totalElements(1)
                    .totalPages(1)
                    .build();
            assertThat(response)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }

        @Test
        void shouldListTransactionsWithDefaultPagination() throws Exception {
            // given
            var defaultFilters = TransactionFilters.builder().build();
            var pagedResult = PagedResult.<TransactionProjection>builder()
                    .content(List.of(SOME_TRANSACTION_PROJECTION))
                    .totalElements(1)
                    .totalPages(1)
                    .build();

            given(transactionSubmissionService.findByFilters(defaultFilters, 0, 20))
                    .willReturn(pagedResult);
            given(transactionControllerMapper.toResponseList(List.of(SOME_TRANSACTION_PROJECTION)))
                    .willReturn(List.of(SOME_TRANSACTION_RESPONSE));

            // when
            var result = mockMvc.perform(get(BASE_PATH)
                            .header(API_KEY_HEADER, TEST_API_KEY))
                    .andExpect(status().isOk())
                    .andReturn();

            // then
            var response = objectMapper.readValue(
                    result.getResponse().getContentAsString(),
                    new TypeReference<PagedResponse<TransactionResponse>>() {});
            var expected = PagedResponse.<TransactionResponse>builder()
                    .content(List.of(SOME_TRANSACTION_RESPONSE))
                    .page(0)
                    .size(20)
                    .totalElements(1)
                    .totalPages(1)
                    .build();
            assertThat(response)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }

        @Test
        void shouldReturn400WhenStatusFilterIsInvalid() throws Exception {
            // when
            var result = mockMvc.perform(get(BASE_PATH)
                            .header(API_KEY_HEADER, TEST_API_KEY)
                            .param("status", "INVALID_STATUS"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            // then
            var response = objectMapper.readValue(
                    result.getResponse().getContentAsString(), ErrorResponse.class);
            var expected = ErrorResponse.builder()
                    .errorCode("STR-4000")
                    .message("Invalid status filter: INVALID_STATUS")
                    .build();
            assertThat(response)
                    .usingRecursiveComparison()
                    .ignoringFields("timestamp", "path")
                    .isEqualTo(expected);
        }
    }
}
