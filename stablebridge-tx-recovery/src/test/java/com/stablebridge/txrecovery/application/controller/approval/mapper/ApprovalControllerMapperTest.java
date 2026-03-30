package com.stablebridge.txrecovery.application.controller.approval.mapper;

import static com.stablebridge.txrecovery.testutil.fixtures.ApprovalControllerFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.stablebridge.txrecovery.api.model.ApprovalActionDto;
import com.stablebridge.txrecovery.api.model.ApproveTransactionResponse;
import com.stablebridge.txrecovery.api.model.CancelTransactionResponse;
import com.stablebridge.txrecovery.domain.recovery.model.ApprovalAction;

class ApprovalControllerMapperTest {

    private final ApprovalControllerMapper mapper = Mappers.getMapper(ApprovalControllerMapper.class);

    @Test
    void shouldMapRetryActionToDomain() {
        // when
        var result = mapper.toDomain(ApprovalActionDto.RETRY);

        // then
        assertThat(result).isEqualTo(ApprovalAction.RETRY);
    }

    @Test
    void shouldMapCancelActionToDomain() {
        // when
        var result = mapper.toDomain(ApprovalActionDto.CANCEL);

        // then
        assertThat(result).isEqualTo(ApprovalAction.CANCEL);
    }

    @Test
    void shouldMapAbortActionToDomain() {
        // when
        var result = mapper.toDomain(ApprovalActionDto.ABORT);

        // then
        assertThat(result).isEqualTo(ApprovalAction.ABORT);
    }

    @Test
    void shouldMapApprovalResultToResponse() {
        // when
        var result = mapper.toApproveResponse(SOME_APPROVAL_RESULT);

        // then
        var expected = ApproveTransactionResponse.builder()
                .transactionId(SOME_APPROVAL_TRANSACTION_ID)
                .status("AWAITING_HUMAN")
                .action("RETRY")
                .approvedAt(SOME_APPROVED_AT)
                .build();
        assertThat(result)
                .usingRecursiveComparison()
                .isEqualTo(expected);
    }

    @Test
    void shouldMapCancellationResultToResponse() {
        // when
        var result = mapper.toCancelResponse(SOME_CANCELLATION_RESULT);

        // then
        var expected = CancelTransactionResponse.builder()
                .transactionId(SOME_APPROVAL_TRANSACTION_ID)
                .status("CANCELLING")
                .message("Cancellation requested for transaction %s".formatted(SOME_APPROVAL_TRANSACTION_ID))
                .build();
        assertThat(result)
                .usingRecursiveComparison()
                .isEqualTo(expected);
    }
}
