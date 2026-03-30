package com.stablebridge.txrecovery.application.controller.approval.mapper;

import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;

import com.stablebridge.txrecovery.api.model.ApprovalActionDto;
import com.stablebridge.txrecovery.api.model.ApproveTransactionResponse;
import com.stablebridge.txrecovery.api.model.CancelTransactionResponse;
import com.stablebridge.txrecovery.domain.recovery.model.ApprovalAction;
import com.stablebridge.txrecovery.domain.transaction.model.ApprovalResult;
import com.stablebridge.txrecovery.domain.transaction.model.CancellationResult;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionStatus;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface ApprovalControllerMapper {

    ApprovalAction toDomain(ApprovalActionDto dto);

    ApproveTransactionResponse toApproveResponse(ApprovalResult result);

    CancelTransactionResponse toCancelResponse(CancellationResult result);

    default String map(TransactionStatus status) {
        return status == null ? null : status.name();
    }

    default String map(ApprovalAction action) {
        return action == null ? null : action.name();
    }
}
