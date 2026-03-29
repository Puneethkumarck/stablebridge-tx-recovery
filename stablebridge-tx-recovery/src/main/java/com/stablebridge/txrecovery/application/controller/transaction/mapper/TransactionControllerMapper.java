package com.stablebridge.txrecovery.application.controller.transaction.mapper;

import java.util.List;

import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.github.f4b6a3.uuid.UuidCreator;
import com.stablebridge.txrecovery.api.model.SubmitTransactionRequest;
import com.stablebridge.txrecovery.api.model.TransactionResponse;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionIntent;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionProjection;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface TransactionControllerMapper {

    @Mapping(target = "intentId", expression = "java(generateIntentId(request))")
    @Mapping(target = "rawAmount", ignore = true)
    @Mapping(target = "strategy", ignore = true)
    @Mapping(target = "batchId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    TransactionIntent toDomain(SubmitTransactionRequest request);

    @Mapping(target = "status", expression = "java(projection.status().name())")
    @Mapping(target = "createdAt", source = "submittedAt")
    @Mapping(target = "estimatedGasBudget", ignore = true)
    @Mapping(target = "submissionStrategy", ignore = true)
    TransactionResponse toResponse(TransactionProjection projection);

    List<TransactionResponse> toResponseList(List<TransactionProjection> projections);

    default String generateIntentId(SubmitTransactionRequest request) {
        return request.intentId() != null ? request.intentId() : UuidCreator.getTimeOrderedEpoch().toString();
    }
}
