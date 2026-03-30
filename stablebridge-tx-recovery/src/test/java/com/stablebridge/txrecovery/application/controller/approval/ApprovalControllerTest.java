package com.stablebridge.txrecovery.application.controller.approval;

import static com.stablebridge.txrecovery.application.controller.approval.ApprovalController.OPERATOR_IDENTITY_ATTR;
import static com.stablebridge.txrecovery.testutil.fixtures.ApprovalControllerFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stablebridge.txrecovery.api.model.ApprovalActionDto;
import com.stablebridge.txrecovery.api.model.ApproveTransactionResponse;
import com.stablebridge.txrecovery.api.model.CancelTransactionResponse;
import com.stablebridge.txrecovery.api.model.ErrorResponse;
import com.stablebridge.txrecovery.application.controller.GlobalExceptionHandler;
import com.stablebridge.txrecovery.application.controller.approval.mapper.ApprovalControllerMapper;
import com.stablebridge.txrecovery.domain.exception.InvalidTransactionStateException;
import com.stablebridge.txrecovery.domain.exception.TerminalTransactionException;
import com.stablebridge.txrecovery.domain.exception.TransactionNotFoundException;
import com.stablebridge.txrecovery.domain.recovery.model.ApprovalAction;
import com.stablebridge.txrecovery.domain.transaction.TransactionApprovalService;

@ExtendWith(MockitoExtension.class)
class ApprovalControllerTest {

    private static final String SOME_OPERATOR = "test-operator";

    @Mock
    private TransactionApprovalService transactionApprovalService;

    @Mock
    private ApprovalControllerMapper approvalControllerMapper;

    @InjectMocks
    private ApprovalController approvalController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        mockMvc = MockMvcBuilders.standaloneSetup(approvalController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Nested
    class Approve {

        @Test
        void shouldApproveTransactionAndReturn200() throws Exception {
            given(approvalControllerMapper.toDomain(ApprovalActionDto.RETRY))
                    .willReturn(ApprovalAction.RETRY);
            given(transactionApprovalService.approveTransaction(
                    SOME_APPROVAL_TRANSACTION_ID, ApprovalAction.RETRY, SOME_APPROVAL_REASON, SOME_OPERATOR))
                    .willReturn(SOME_APPROVAL_RESULT);
            given(approvalControllerMapper.toApproveResponse(SOME_APPROVAL_RESULT))
                    .willReturn(SOME_APPROVE_RESPONSE);

            var result = mockMvc.perform(post("/api/v1/transactions/{transactionId}/approve",
                            SOME_APPROVAL_TRANSACTION_ID)
                            .requestAttr(OPERATOR_IDENTITY_ATTR, SOME_OPERATOR)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(SOME_APPROVE_REQUEST)))
                    .andExpect(status().isOk())
                    .andReturn();

            var response = objectMapper.readValue(
                    result.getResponse().getContentAsString(), ApproveTransactionResponse.class);
            assertThat(response)
                    .usingRecursiveComparison()
                    .isEqualTo(SOME_APPROVE_RESPONSE);
        }

        @Test
        void shouldFallBackToSystemWhenOperatorIdentityNotSet() throws Exception {
            given(approvalControllerMapper.toDomain(ApprovalActionDto.RETRY))
                    .willReturn(ApprovalAction.RETRY);
            given(transactionApprovalService.approveTransaction(
                    SOME_APPROVAL_TRANSACTION_ID, ApprovalAction.RETRY, SOME_APPROVAL_REASON, "system"))
                    .willReturn(SOME_APPROVAL_RESULT);
            given(approvalControllerMapper.toApproveResponse(SOME_APPROVAL_RESULT))
                    .willReturn(SOME_APPROVE_RESPONSE);

            var result = mockMvc.perform(post("/api/v1/transactions/{transactionId}/approve",
                            SOME_APPROVAL_TRANSACTION_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(SOME_APPROVE_REQUEST)))
                    .andExpect(status().isOk())
                    .andReturn();

            var response = objectMapper.readValue(
                    result.getResponse().getContentAsString(), ApproveTransactionResponse.class);
            assertThat(response)
                    .usingRecursiveComparison()
                    .isEqualTo(SOME_APPROVE_RESPONSE);
        }

        @Test
        void shouldReturn409WhenTransactionNotInAwaitingHumanStatus() throws Exception {
            given(approvalControllerMapper.toDomain(ApprovalActionDto.RETRY))
                    .willReturn(ApprovalAction.RETRY);
            given(transactionApprovalService.approveTransaction(
                    SOME_APPROVAL_TRANSACTION_ID, ApprovalAction.RETRY, SOME_APPROVAL_REASON, SOME_OPERATOR))
                    .willThrow(new InvalidTransactionStateException(SOME_APPROVAL_TRANSACTION_ID, "PENDING"));

            var result = mockMvc.perform(post("/api/v1/transactions/{transactionId}/approve",
                            SOME_APPROVAL_TRANSACTION_ID)
                            .requestAttr(OPERATOR_IDENTITY_ATTR, SOME_OPERATOR)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(SOME_APPROVE_REQUEST)))
                    .andExpect(status().isConflict())
                    .andReturn();

            var response = objectMapper.readValue(
                    result.getResponse().getContentAsString(), ErrorResponse.class);
            assertThat(response.errorCode()).isEqualTo("STR-4092");
            assertThat(response.message()).contains("PENDING").contains("AWAITING_HUMAN");
        }

        @Test
        void shouldReturn404WhenTransactionNotFound() throws Exception {
            given(approvalControllerMapper.toDomain(ApprovalActionDto.RETRY))
                    .willReturn(ApprovalAction.RETRY);
            given(transactionApprovalService.approveTransaction(
                    "non-existent-tx", ApprovalAction.RETRY, SOME_APPROVAL_REASON, SOME_OPERATOR))
                    .willThrow(new TransactionNotFoundException("non-existent-tx"));

            mockMvc.perform(post("/api/v1/transactions/{transactionId}/approve", "non-existent-tx")
                            .requestAttr(OPERATOR_IDENTITY_ATTR, SOME_OPERATOR)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(SOME_APPROVE_REQUEST)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldReturn400WhenActionIsNull() throws Exception {
            var invalidRequest = SOME_APPROVE_REQUEST.toBuilder().action(null).build();

            mockMvc.perform(post("/api/v1/transactions/{transactionId}/approve",
                            SOME_APPROVAL_TRANSACTION_ID)
                            .requestAttr(OPERATOR_IDENTITY_ATTR, SOME_OPERATOR)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void shouldReturn400WhenReasonIsBlank() throws Exception {
            var invalidRequest = SOME_APPROVE_REQUEST.toBuilder().reason("").build();

            mockMvc.perform(post("/api/v1/transactions/{transactionId}/approve",
                            SOME_APPROVAL_TRANSACTION_ID)
                            .requestAttr(OPERATOR_IDENTITY_ATTR, SOME_OPERATOR)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class Cancel {

        @Test
        void shouldCancelTransactionAndReturn200() throws Exception {
            given(transactionApprovalService.cancelTransaction(
                    SOME_APPROVAL_TRANSACTION_ID, SOME_CANCEL_REASON, SOME_OPERATOR))
                    .willReturn(SOME_CANCELLATION_RESULT);
            given(approvalControllerMapper.toCancelResponse(SOME_CANCELLATION_RESULT))
                    .willReturn(SOME_CANCEL_RESPONSE);

            var result = mockMvc.perform(post("/api/v1/transactions/{transactionId}/cancel",
                            SOME_APPROVAL_TRANSACTION_ID)
                            .requestAttr(OPERATOR_IDENTITY_ATTR, SOME_OPERATOR)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(SOME_CANCEL_REQUEST)))
                    .andExpect(status().isOk())
                    .andReturn();

            var response = objectMapper.readValue(
                    result.getResponse().getContentAsString(), CancelTransactionResponse.class);
            assertThat(response)
                    .usingRecursiveComparison()
                    .isEqualTo(SOME_CANCEL_RESPONSE);
        }

        @Test
        void shouldFallBackToSystemWhenOperatorIdentityNotSet() throws Exception {
            given(transactionApprovalService.cancelTransaction(
                    SOME_APPROVAL_TRANSACTION_ID, SOME_CANCEL_REASON, "system"))
                    .willReturn(SOME_CANCELLATION_RESULT);
            given(approvalControllerMapper.toCancelResponse(SOME_CANCELLATION_RESULT))
                    .willReturn(SOME_CANCEL_RESPONSE);

            var result = mockMvc.perform(post("/api/v1/transactions/{transactionId}/cancel",
                            SOME_APPROVAL_TRANSACTION_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(SOME_CANCEL_REQUEST)))
                    .andExpect(status().isOk())
                    .andReturn();

            var response = objectMapper.readValue(
                    result.getResponse().getContentAsString(), CancelTransactionResponse.class);
            assertThat(response)
                    .usingRecursiveComparison()
                    .isEqualTo(SOME_CANCEL_RESPONSE);
        }

        @Test
        void shouldReturn409WhenTransactionIsInTerminalStatus() throws Exception {
            given(transactionApprovalService.cancelTransaction(
                    SOME_APPROVAL_TRANSACTION_ID, SOME_CANCEL_REASON, SOME_OPERATOR))
                    .willThrow(new TerminalTransactionException(SOME_APPROVAL_TRANSACTION_ID, "FINALIZED"));

            mockMvc.perform(post("/api/v1/transactions/{transactionId}/cancel",
                            SOME_APPROVAL_TRANSACTION_ID)
                            .requestAttr(OPERATOR_IDENTITY_ATTR, SOME_OPERATOR)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(SOME_CANCEL_REQUEST)))
                    .andExpect(status().isConflict());
        }

        @Test
        void shouldReturn404WhenTransactionNotFound() throws Exception {
            given(transactionApprovalService.cancelTransaction(
                    "non-existent-tx", SOME_CANCEL_REASON, SOME_OPERATOR))
                    .willThrow(new TransactionNotFoundException("non-existent-tx"));

            mockMvc.perform(post("/api/v1/transactions/{transactionId}/cancel", "non-existent-tx")
                            .requestAttr(OPERATOR_IDENTITY_ATTR, SOME_OPERATOR)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(SOME_CANCEL_REQUEST)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldReturn400WhenReasonIsBlank() throws Exception {
            var invalidRequest = SOME_CANCEL_REQUEST.toBuilder().reason("").build();

            mockMvc.perform(post("/api/v1/transactions/{transactionId}/cancel",
                            SOME_APPROVAL_TRANSACTION_ID)
                            .requestAttr(OPERATOR_IDENTITY_ATTR, SOME_OPERATOR)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidRequest)))
                    .andExpect(status().isBadRequest());
        }
    }
}
