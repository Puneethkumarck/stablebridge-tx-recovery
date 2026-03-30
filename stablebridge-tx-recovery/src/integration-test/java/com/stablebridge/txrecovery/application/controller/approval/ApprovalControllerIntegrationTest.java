package com.stablebridge.txrecovery.application.controller.approval;

import static com.stablebridge.txrecovery.testutil.fixtures.ApprovalControllerFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.stablebridge.txrecovery.api.model.ApproveTransactionResponse;
import com.stablebridge.txrecovery.api.model.CancelTransactionResponse;
import com.stablebridge.txrecovery.domain.transaction.port.TransactionProjectionStore;
import com.stablebridge.txrecovery.domain.transaction.port.TransactionWorkflowSignaler;
import com.stablebridge.txrecovery.testutil.ControllerIntegrationTestBase;
import com.stablebridge.txrecovery.testutil.PgTest;

@PgTest
class ApprovalControllerIntegrationTest extends ControllerIntegrationTestBase {

    private static final String APPROVE_PATH = "/api/v1/transactions/%s/approve";
    private static final String CANCEL_PATH = "/api/v1/transactions/%s/cancel";

    @MockitoBean
    private TransactionProjectionStore transactionProjectionStore;

    @MockitoBean
    private TransactionWorkflowSignaler transactionWorkflowSignaler;

    @Nested
    class Approve {

        @Test
        void shouldApproveTransactionAndReturn200() throws Exception {
            given(transactionProjectionStore.findById(SOME_APPROVAL_TRANSACTION_ID))
                    .willReturn(Optional.of(AWAITING_HUMAN_PROJECTION));

            var result = mockMvc.perform(authenticatedJson(
                            post(APPROVE_PATH.formatted(SOME_APPROVAL_TRANSACTION_ID)),
                            objectMapper.writeValueAsString(SOME_APPROVE_REQUEST)))
                    .andExpect(status().isOk())
                    .andReturn();

            var response = objectMapper.readValue(
                    result.getResponse().getContentAsString(), ApproveTransactionResponse.class);
            assertThat(response.transactionId()).isEqualTo(SOME_APPROVAL_TRANSACTION_ID);
            assertThat(response.status()).isEqualTo("AWAITING_HUMAN");
            assertThat(response.action()).isEqualTo("RETRY");
            assertThat(response.approvedAt()).isNotNull();
        }

        @Test
        void shouldReturn409WhenTransactionNotInAwaitingHumanStatus() throws Exception {
            given(transactionProjectionStore.findById(SOME_APPROVAL_TRANSACTION_ID))
                    .willReturn(Optional.of(PENDING_PROJECTION));

            var result = mockMvc.perform(authenticatedJson(
                            post(APPROVE_PATH.formatted(SOME_APPROVAL_TRANSACTION_ID)),
                            objectMapper.writeValueAsString(SOME_APPROVE_REQUEST)))
                    .andExpect(status().isConflict())
                    .andReturn();

            assertErrorResponse(result, HttpStatus.CONFLICT, "STR-4092",
                    "Transaction %s is in PENDING status, expected AWAITING_HUMAN".formatted(
                            SOME_APPROVAL_TRANSACTION_ID));
        }

        @Test
        void shouldReturn404WhenTransactionNotFound() throws Exception {
            given(transactionProjectionStore.findById("non-existent-tx"))
                    .willReturn(Optional.empty());

            var result = mockMvc.perform(authenticatedJson(
                            post(APPROVE_PATH.formatted("non-existent-tx")),
                            objectMapper.writeValueAsString(SOME_APPROVE_REQUEST)))
                    .andExpect(status().isNotFound())
                    .andReturn();

            assertErrorResponse(result, HttpStatus.NOT_FOUND, "STR-4041",
                    "Transaction not found: non-existent-tx");
        }

        @Test
        void shouldReturn400WhenRequestBodyIsMissing() throws Exception {
            var result = mockMvc.perform(authenticated(
                            post(APPROVE_PATH.formatted(SOME_APPROVAL_TRANSACTION_ID)))
                            .contentType("application/json"))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertErrorResponse(result, HttpStatus.BAD_REQUEST, "STR-4000", "Malformed request body");
        }

        @Test
        void shouldReturn400WhenReasonIsBlank() throws Exception {
            var invalidRequest = SOME_APPROVE_REQUEST.toBuilder().reason("").build();

            var result = mockMvc.perform(authenticatedJson(
                            post(APPROVE_PATH.formatted(SOME_APPROVAL_TRANSACTION_ID)),
                            objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertValidationError(result, "reason");
        }
    }

    @Nested
    class Cancel {

        @Test
        void shouldCancelTransactionFromPendingStatus() throws Exception {
            given(transactionProjectionStore.findById(SOME_APPROVAL_TRANSACTION_ID))
                    .willReturn(Optional.of(PENDING_PROJECTION));

            var result = mockMvc.perform(authenticatedJson(
                            post(CANCEL_PATH.formatted(SOME_APPROVAL_TRANSACTION_ID)),
                            objectMapper.writeValueAsString(SOME_CANCEL_REQUEST)))
                    .andExpect(status().isOk())
                    .andReturn();

            var response = objectMapper.readValue(
                    result.getResponse().getContentAsString(), CancelTransactionResponse.class);
            var expected = CancelTransactionResponse.builder()
                    .transactionId(SOME_APPROVAL_TRANSACTION_ID)
                    .status("CANCELLING")
                    .message("Cancellation requested for transaction %s".formatted(SOME_APPROVAL_TRANSACTION_ID))
                    .build();
            assertThat(response)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }

        @Test
        void shouldReturn409WhenTransactionIsInTerminalStatus() throws Exception {
            given(transactionProjectionStore.findById(SOME_APPROVAL_TRANSACTION_ID))
                    .willReturn(Optional.of(FINALIZED_PROJECTION));

            var result = mockMvc.perform(authenticatedJson(
                            post(CANCEL_PATH.formatted(SOME_APPROVAL_TRANSACTION_ID)),
                            objectMapper.writeValueAsString(SOME_CANCEL_REQUEST)))
                    .andExpect(status().isConflict())
                    .andReturn();

            assertErrorResponse(result, HttpStatus.CONFLICT, "STR-4093",
                    "Transaction %s is in terminal status FINALIZED and cannot be cancelled".formatted(
                            SOME_APPROVAL_TRANSACTION_ID));
        }

        @Test
        void shouldReturn404WhenTransactionNotFound() throws Exception {
            given(transactionProjectionStore.findById("non-existent-tx"))
                    .willReturn(Optional.empty());

            var result = mockMvc.perform(authenticatedJson(
                            post(CANCEL_PATH.formatted("non-existent-tx")),
                            objectMapper.writeValueAsString(SOME_CANCEL_REQUEST)))
                    .andExpect(status().isNotFound())
                    .andReturn();

            assertErrorResponse(result, HttpStatus.NOT_FOUND, "STR-4041",
                    "Transaction not found: non-existent-tx");
        }

        @Test
        void shouldReturn400WhenReasonIsBlank() throws Exception {
            var invalidRequest = SOME_CANCEL_REQUEST.toBuilder().reason("").build();

            var result = mockMvc.perform(authenticatedJson(
                            post(CANCEL_PATH.formatted(SOME_APPROVAL_TRANSACTION_ID)),
                            objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertValidationError(result, "reason");
        }
    }
}
