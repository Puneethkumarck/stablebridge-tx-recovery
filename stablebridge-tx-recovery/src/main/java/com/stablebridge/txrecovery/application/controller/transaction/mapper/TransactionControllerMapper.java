package com.stablebridge.txrecovery.application.controller.transaction.mapper;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;

import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.github.f4b6a3.uuid.UuidCreator;
import com.stablebridge.txrecovery.api.model.BatchTransactionResponse;
import com.stablebridge.txrecovery.api.model.SubmitTransactionRequest;
import com.stablebridge.txrecovery.api.model.TransactionResponse;
import com.stablebridge.txrecovery.domain.exception.InvalidParameterException;
import com.stablebridge.txrecovery.domain.transaction.model.BatchSubmissionResult;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionFilters;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionIntent;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionProjection;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionStatus;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface TransactionControllerMapper {

    @Mapping(target = "intentId", expression = "java(generateIntentId(request))")
    @Mapping(target = "rawAmount", expression = "java(computeRawAmount(request))")
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

    default TransactionFilters toFilters(String chain, String status, String fromAddress,
            String toAddress, String token, Instant fromDate, Instant toDate) {
        return TransactionFilters.builder()
                .chain(chain)
                .status(parseStatus(status))
                .fromAddress(fromAddress)
                .toAddress(toAddress)
                .token(token)
                .fromDate(fromDate)
                .toDate(toDate)
                .build();
    }

    default BatchTransactionResponse toBatchResponse(BatchSubmissionResult result) {
        return BatchTransactionResponse.builder()
                .batchId(result.batchId())
                .transactions(toResponseList(result.projections()))
                .createdAt(result.createdAt())
                .build();
    }

    default String generateIntentId(SubmitTransactionRequest request) {
        return request.intentId() != null ? request.intentId() : UuidCreator.getTimeOrderedEpoch().toString();
    }

    default BigInteger computeRawAmount(SubmitTransactionRequest request) {
        var shifted = request.amount().movePointRight(request.tokenDecimals());
        if (shifted.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException(
                    "Amount precision (%d decimals) exceeds token decimals (%d)"
                            .formatted(request.amount().scale(), request.tokenDecimals()));
        }
        return shifted.toBigInteger();
    }

    default TransactionStatus parseStatus(String status) {
        if (status == null) {
            return null;
        }
        try {
            return TransactionStatus.valueOf(status);
        } catch (IllegalArgumentException _) {
            throw new InvalidParameterException("status", status);
        }
    }
}
