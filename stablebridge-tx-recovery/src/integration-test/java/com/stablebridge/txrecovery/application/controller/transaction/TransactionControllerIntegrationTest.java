package com.stablebridge.txrecovery.application.controller.transaction;

import static com.stablebridge.txrecovery.testutil.TestUtils.eqIgnoring;
import static com.stablebridge.txrecovery.testutil.fixtures.TransactionControllerFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.github.f4b6a3.uuid.UuidCreator;
import com.stablebridge.txrecovery.api.model.BatchTransactionResponse;
import com.stablebridge.txrecovery.api.model.PagedResponse;
import com.stablebridge.txrecovery.api.model.SubmitBatchRequest;
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

    @MockitoBean
    private TransactionSubmissionService transactionSubmissionService;

    @MockitoSpyBean
    private TransactionControllerMapper transactionControllerMapper;

    @Nested
    class Authentication {

        @Test
        void shouldReturn401WhenApiKeyIsMissing() throws Exception {
            // when / then
            mockMvc.perform(post(BASE_PATH)
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(SOME_SUBMIT_REQUEST)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void shouldReturn401WhenApiKeyIsInvalid() throws Exception {
            // when / then
            mockMvc.perform(post(BASE_PATH)
                            .header(API_KEY_HEADER, "wrong-key")
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(SOME_SUBMIT_REQUEST)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class SubmitTransaction {

        @Test
        void shouldSubmitTransactionAndReturn201() throws Exception {
            // given
            given(transactionSubmissionService.submitTransaction(eqIgnoring(SOME_TRANSACTION_INTENT)))
                    .willReturn(SOME_TRANSACTION_PROJECTION);

            // when
            var result = mockMvc.perform(authenticatedJson(
                            post(BASE_PATH),
                            objectMapper.writeValueAsString(SOME_SUBMIT_REQUEST)))
                    .andExpect(status().isCreated())
                    .andReturn();

            // then
            var response = objectMapper.readValue(
                    result.getResponse().getContentAsString(), TransactionResponse.class);
            assertThat(response)
                    .usingRecursiveComparison()
                    .ignoringFields("estimatedGasBudget", "submissionStrategy")
                    .isEqualTo(SOME_TRANSACTION_RESPONSE);
        }

        @Test
        void shouldReturnExistingTransactionWhenDuplicateIntentId() throws Exception {
            // given
            var existingProjection = SOME_TRANSACTION_PROJECTION.toBuilder()
                    .transactionId("existing-tx-001")
                    .build();

            given(transactionSubmissionService.submitTransaction(eqIgnoring(SOME_TRANSACTION_INTENT)))
                    .willThrow(new DuplicateIntentException("existing-tx-001"));
            given(transactionSubmissionService.findById("existing-tx-001"))
                    .willReturn(existingProjection);

            // when
            var result = mockMvc.perform(authenticatedJson(
                            post(BASE_PATH),
                            objectMapper.writeValueAsString(SOME_SUBMIT_REQUEST)))
                    .andExpect(status().isOk())
                    .andReturn();

            // then
            var response = objectMapper.readValue(
                    result.getResponse().getContentAsString(), TransactionResponse.class);
            var expected = SOME_TRANSACTION_RESPONSE.toBuilder()
                    .transactionId("existing-tx-001")
                    .build();
            assertThat(response)
                    .usingRecursiveComparison()
                    .ignoringFields("estimatedGasBudget", "submissionStrategy")
                    .isEqualTo(expected);
        }

        @Test
        void shouldReturn400WhenChainIsBlank() throws Exception {
            // given
            var invalidRequest = SOME_SUBMIT_REQUEST.toBuilder()
                    .chain("")
                    .build();

            // when
            var result = mockMvc.perform(authenticatedJson(
                            post(BASE_PATH),
                            objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            // then
            assertValidationError(result, "chain");
        }

        @Test
        void shouldReturn400WhenAmountIsNegative() throws Exception {
            // given
            var invalidRequest = SOME_SUBMIT_REQUEST.toBuilder()
                    .amount(new BigDecimal("-10.00"))
                    .build();

            // when
            var result = mockMvc.perform(authenticatedJson(
                            post(BASE_PATH),
                            objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            // then
            assertValidationError(result, "amount");
        }

        @Test
        void shouldReturn400WhenToAddressIsBlank() throws Exception {
            // given
            var invalidRequest = SOME_SUBMIT_REQUEST.toBuilder()
                    .toAddress("")
                    .build();

            // when
            var result = mockMvc.perform(authenticatedJson(
                            post(BASE_PATH),
                            objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            // then
            assertValidationError(result, "toAddress");
        }

        @Test
        void shouldReturn400WhenRequestBodyIsMissing() throws Exception {
            // when
            var result = mockMvc.perform(authenticated(
                            post(BASE_PATH))
                            .contentType("application/json"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            // then
            assertErrorResponse(result, HttpStatus.BAD_REQUEST, "STR-4000", "Malformed request body");
        }
    }

    @Nested
    class SubmitBatch {

        @Test
        void shouldSubmitBatchAndReturn201() throws Exception {
            // given
            var secondIntent = SOME_TRANSACTION_INTENT.toBuilder()
                    .intentId(SOME_SECOND_INTENT_ID)
                    .build();
            var secondProjection = SOME_TRANSACTION_PROJECTION.toBuilder()
                    .transactionId("tx-67890")
                    .intentId(SOME_SECOND_INTENT_ID)
                    .build();

            given(transactionSubmissionService.submitBatch(
                    eqIgnoring(List.of(SOME_TRANSACTION_INTENT, secondIntent)),
                    argThat(batchId -> batchId != null && !batchId.isBlank())))
                    .willReturn(List.of(SOME_TRANSACTION_PROJECTION, secondProjection));

            // when
            var result = mockMvc.perform(authenticatedJson(
                            post(BASE_PATH + "/batch"),
                            objectMapper.writeValueAsString(SOME_BATCH_REQUEST)))
                    .andExpect(status().isCreated())
                    .andReturn();

            // then
            var response = objectMapper.readValue(
                    result.getResponse().getContentAsString(), BatchTransactionResponse.class);
            var secondResponse = SOME_TRANSACTION_RESPONSE.toBuilder()
                    .transactionId("tx-67890")
                    .intentId(SOME_SECOND_INTENT_ID)
                    .build();
            var expected = BatchTransactionResponse.builder()
                    .batchId(response.batchId())
                    .transactions(List.of(SOME_TRANSACTION_RESPONSE, secondResponse))
                    .createdAt(response.createdAt())
                    .build();
            assertThat(response)
                    .usingRecursiveComparison()
                    .ignoringFields("batchId", "createdAt",
                            "transactions.estimatedGasBudget", "transactions.submissionStrategy")
                    .isEqualTo(expected);
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
            var result = mockMvc.perform(authenticatedJson(
                            post(BASE_PATH + "/batch"),
                            objectMapper.writeValueAsString(oversizedBatch)))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            // then
            assertValidationError(result, "transactions");
        }

        @Test
        void shouldReturn400WhenBatchIsEmpty() throws Exception {
            // given
            var emptyBatch = SubmitBatchRequest.builder()
                    .transactions(List.of())
                    .build();

            // when
            var result = mockMvc.perform(authenticatedJson(
                            post(BASE_PATH + "/batch"),
                            objectMapper.writeValueAsString(emptyBatch)))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            // then
            assertValidationError(result, "transactions");
        }
    }

    @Nested
    class GetTransaction {

        @Test
        void shouldReturnTransactionById() throws Exception {
            // given
            given(transactionSubmissionService.findById(SOME_TRANSACTION_ID))
                    .willReturn(SOME_TRANSACTION_PROJECTION);

            // when
            var result = mockMvc.perform(authenticated(
                            get(BASE_PATH + "/{transactionId}", SOME_TRANSACTION_ID)))
                    .andExpect(status().isOk())
                    .andReturn();

            // then
            var response = objectMapper.readValue(
                    result.getResponse().getContentAsString(), TransactionResponse.class);
            assertThat(response)
                    .usingRecursiveComparison()
                    .ignoringFields("estimatedGasBudget", "submissionStrategy")
                    .isEqualTo(SOME_TRANSACTION_RESPONSE);
        }

        @Test
        void shouldReturn404WhenTransactionNotFound() throws Exception {
            // given
            given(transactionSubmissionService.findById("non-existent-tx"))
                    .willThrow(new TransactionNotFoundException("non-existent-tx"));

            // when
            var result = mockMvc.perform(authenticated(
                            get(BASE_PATH + "/{transactionId}", "non-existent-tx")))
                    .andExpect(status().isNotFound())
                    .andReturn();

            // then
            assertErrorResponse(result, HttpStatus.NOT_FOUND, "STR-4041",
                    "Transaction not found: non-existent-tx");
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

            // when
            var result = mockMvc.perform(authenticated(get(BASE_PATH))
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
                    .ignoringFields("content.estimatedGasBudget", "content.submissionStrategy")
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

            // when
            var result = mockMvc.perform(authenticated(get(BASE_PATH)))
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
                    .ignoringFields("content.estimatedGasBudget", "content.submissionStrategy")
                    .isEqualTo(expected);
        }

        @Test
        void shouldReturn400WhenStatusFilterIsInvalid() throws Exception {
            // when
            var result = mockMvc.perform(authenticated(get(BASE_PATH))
                            .param("status", "INVALID_STATUS"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            // then
            assertErrorResponse(result, HttpStatus.BAD_REQUEST, "STR-4000",
                    "Invalid status filter: INVALID_STATUS");
        }
    }
}
