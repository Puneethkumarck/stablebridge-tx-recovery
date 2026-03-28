package com.stablebridge.txrecovery.application.controller.transaction;

import static com.stablebridge.txrecovery.testutil.TestUtils.eqIgnoring;
import static com.stablebridge.txrecovery.testutil.fixtures.TransactionControllerFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stablebridge.txrecovery.api.model.BatchTransactionResponse;
import com.stablebridge.txrecovery.api.model.ErrorResponse;
import com.stablebridge.txrecovery.api.model.PagedResponse;
import com.stablebridge.txrecovery.api.model.SubmitBatchRequest;
import com.stablebridge.txrecovery.api.model.SubmitTransactionRequest;
import com.stablebridge.txrecovery.api.model.TransactionResponse;
import com.stablebridge.txrecovery.application.controller.GlobalExceptionHandler;
import com.stablebridge.txrecovery.application.controller.transaction.mapper.TransactionControllerMapper;
import com.stablebridge.txrecovery.domain.exception.DuplicateIntentException;
import com.stablebridge.txrecovery.domain.exception.TransactionNotFoundException;
import com.stablebridge.txrecovery.domain.transaction.TransactionSubmissionService;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionFilters;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionStatus;

@ExtendWith(MockitoExtension.class)
class TransactionControllerTest {

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

    private static final SubmitBatchRequest SOME_BATCH_REQUEST = SubmitBatchRequest.builder()
            .transactions(List.of(
                    SOME_SUBMIT_REQUEST,
                    SOME_SUBMIT_REQUEST.toBuilder()
                            .intentId(SOME_SECOND_INTENT_ID)
                            .build()))
            .build();

    @Mock
    private TransactionSubmissionService transactionSubmissionService;

    @Mock
    private TransactionControllerMapper transactionControllerMapper;

    @InjectMocks
    private TransactionController transactionController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        mockMvc = MockMvcBuilders.standaloneSetup(transactionController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Nested
    class SubmitTransaction {

        @Test
        void shouldSubmitTransactionAndReturn201() throws Exception {
            // given
            given(transactionControllerMapper.toDomain(eqIgnoring(SOME_SUBMIT_REQUEST)))
                    .willReturn(SOME_TRANSACTION_INTENT);
            given(transactionSubmissionService.submitTransaction(SOME_TRANSACTION_INTENT))
                    .willReturn(SOME_TRANSACTION_PROJECTION);
            given(transactionControllerMapper.toResponse(SOME_TRANSACTION_PROJECTION))
                    .willReturn(SOME_TRANSACTION_RESPONSE);

            // when
            var result = mockMvc.perform(post("/api/v1/transactions")
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

            given(transactionControllerMapper.toDomain(eqIgnoring(SOME_SUBMIT_REQUEST)))
                    .willReturn(SOME_TRANSACTION_INTENT);
            given(transactionSubmissionService.submitTransaction(SOME_TRANSACTION_INTENT))
                    .willThrow(new DuplicateIntentException("existing-tx-001"));
            given(transactionSubmissionService.findById("existing-tx-001"))
                    .willReturn(existingProjection);
            given(transactionControllerMapper.toResponse(existingProjection))
                    .willReturn(existingResponse);

            // when
            var result = mockMvc.perform(post("/api/v1/transactions")
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
            var result = mockMvc.perform(post("/api/v1/transactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            // then
            var response = objectMapper.readValue(
                    result.getResponse().getContentAsString(), ErrorResponse.class);
            assertThat(response.errorCode()).isEqualTo("STR-4000");
            assertThat(response.details()).containsKey("chain");
        }

        @Test
        void shouldReturn400WhenAmountIsNegative() throws Exception {
            // given
            var invalidRequest = SOME_SUBMIT_REQUEST.toBuilder()
                    .amount(new BigDecimal("-10.00"))
                    .build();

            // when
            var result = mockMvc.perform(post("/api/v1/transactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            // then
            var response = objectMapper.readValue(
                    result.getResponse().getContentAsString(), ErrorResponse.class);
            assertThat(response.errorCode()).isEqualTo("STR-4000");
            assertThat(response.details()).containsKey("amount");
        }

        @Test
        void shouldReturn400WhenToAddressIsBlank() throws Exception {
            // given
            var invalidRequest = SOME_SUBMIT_REQUEST.toBuilder()
                    .toAddress("")
                    .build();

            // when
            var result = mockMvc.perform(post("/api/v1/transactions")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            // then
            var response = objectMapper.readValue(
                    result.getResponse().getContentAsString(), ErrorResponse.class);
            assertThat(response.errorCode()).isEqualTo("STR-4000");
            assertThat(response.details()).containsKey("toAddress");
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
            var secondResponse = SOME_TRANSACTION_RESPONSE.toBuilder()
                    .transactionId("tx-67890")
                    .intentId(SOME_SECOND_INTENT_ID)
                    .build();

            given(transactionControllerMapper.toDomain(eqIgnoring(SOME_SUBMIT_REQUEST)))
                    .willReturn(SOME_TRANSACTION_INTENT);
            given(transactionControllerMapper.toDomain(eqIgnoring(SOME_SUBMIT_REQUEST.toBuilder()
                    .intentId(SOME_SECOND_INTENT_ID).build())))
                    .willReturn(secondIntent);
            given(transactionSubmissionService.submitBatch(
                    eqIgnoring(List.of(SOME_TRANSACTION_INTENT, secondIntent)),
                    argThat(batchId -> batchId != null && !batchId.isBlank())))
                    .willReturn(List.of(SOME_TRANSACTION_PROJECTION, secondProjection));
            given(transactionControllerMapper.toResponseList(
                    List.of(SOME_TRANSACTION_PROJECTION, secondProjection)))
                    .willReturn(List.of(SOME_TRANSACTION_RESPONSE, secondResponse));

            // when
            var result = mockMvc.perform(post("/api/v1/transactions/batch")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(SOME_BATCH_REQUEST)))
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
                    .mapToObj(i -> SOME_SUBMIT_REQUEST.toBuilder()
                            .intentId("550e8400-e29b-41d4-a716-4466554400%02d".formatted(i))
                            .build())
                    .toList();
            var oversizedBatch = SubmitBatchRequest.builder()
                    .transactions(tooManyTransactions)
                    .build();

            // when / then
            mockMvc.perform(post("/api/v1/transactions/batch")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(oversizedBatch)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void shouldReturn400WhenBatchIsEmpty() throws Exception {
            // given
            var emptyBatch = SubmitBatchRequest.builder()
                    .transactions(List.of())
                    .build();

            // when / then
            mockMvc.perform(post("/api/v1/transactions/batch")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(emptyBatch)))
                    .andExpect(status().isBadRequest());
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
            var result = mockMvc.perform(get("/api/v1/transactions/{transactionId}", SOME_TRANSACTION_ID))
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
            var result = mockMvc.perform(get("/api/v1/transactions/{transactionId}", "non-existent-tx"))
                    .andExpect(status().isNotFound())
                    .andReturn();

            // then
            var response = objectMapper.readValue(
                    result.getResponse().getContentAsString(), ErrorResponse.class);
            assertThat(response.errorCode()).isEqualTo("STR-4041");
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

            given(transactionSubmissionService.findByFilters(eqIgnoring(expectedFilters)))
                    .willReturn(List.of(SOME_TRANSACTION_PROJECTION));
            given(transactionControllerMapper.toResponseList(List.of(SOME_TRANSACTION_PROJECTION)))
                    .willReturn(List.of(SOME_TRANSACTION_RESPONSE));

            // when
            var result = mockMvc.perform(get("/api/v1/transactions")
                            .param("chain", SOME_CHAIN)
                            .param("status", "RECEIVED")
                            .param("fromAddress", "0xsender001")
                            .param("toAddress", SOME_TO_ADDRESS)
                            .param("page", "0")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andReturn();

            // then
            var pagedResponse = objectMapper.readValue(
                    result.getResponse().getContentAsString(),
                    new TypeReference<PagedResponse<TransactionResponse>>() {});
            assertThat(pagedResponse.content()).hasSize(1);
            assertThat(pagedResponse.page()).isZero();
            assertThat(pagedResponse.size()).isEqualTo(10);
            then(transactionSubmissionService).should().findByFilters(eqIgnoring(expectedFilters));
        }

        @Test
        void shouldListTransactionsWithDefaultPagination() throws Exception {
            // given
            var defaultFilters = TransactionFilters.builder().build();

            given(transactionSubmissionService.findByFilters(eqIgnoring(defaultFilters)))
                    .willReturn(List.of(SOME_TRANSACTION_PROJECTION));
            given(transactionControllerMapper.toResponseList(List.of(SOME_TRANSACTION_PROJECTION)))
                    .willReturn(List.of(SOME_TRANSACTION_RESPONSE));

            // when
            var result = mockMvc.perform(get("/api/v1/transactions"))
                    .andExpect(status().isOk())
                    .andReturn();

            // then
            var pagedResponse = objectMapper.readValue(
                    result.getResponse().getContentAsString(),
                    new TypeReference<PagedResponse<TransactionResponse>>() {});
            assertThat(pagedResponse.page()).isZero();
            assertThat(pagedResponse.size()).isEqualTo(20);
            then(transactionSubmissionService).should().findByFilters(eqIgnoring(defaultFilters));
        }
    }
}
