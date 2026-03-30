package com.stablebridge.txrecovery.application.controller.approval.mapper;

import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.stablebridge.txrecovery.api.model.ApprovalActionDto;
import com.stablebridge.txrecovery.api.model.ApproveTransactionResponse;
import com.stablebridge.txrecovery.api.model.CancelTransactionResponse;
import com.stablebridge.txrecovery.domain.recovery.model.ApprovalAction;
import com.stablebridge.txrecovery.domain.transaction.model.ApprovalResult;
import com.stablebridge.txrecovery.domain.transaction.model.CancellationResult;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface ApprovalControllerMapper {

    ApprovalAction toDomain(ApprovalActionDto dto);

    @Mapping(target = "status", expression = "java(result.status().name())")
    @Mapping(target = "action", expression = "java(result.action().name())")
    ApproveTransactionResponse toApproveResponse(ApprovalResult result);

    @Mapping(target = "status", expression = "java(result.status().name())")
    CancelTransactionResponse toCancelResponse(CancellationResult result);
}
