package com.stablebridge.txrecovery.infrastructure.db.transaction;

import java.util.UUID;

import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import com.stablebridge.txrecovery.domain.transaction.model.TransactionProjection;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionStatus;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
interface TransactionProjectionEntityMapper {

    @Mapping(target = "transactionId", source = "id", qualifiedByName = "uuidToString")
    @Mapping(target = "intentId", source = "intentId", qualifiedByName = "uuidToString")
    @Mapping(target = "status", expression = "java(mapStringToStatus(entity.getStatus()))")
    @Mapping(target = "txHash", source = "transactionHash")
    @Mapping(target = "token", source = "asset")
    @Mapping(target = "retryCount", source = "recoveryCount")
    @Mapping(target = "gasSpent", ignore = true)
    @Mapping(target = "submittedAt", source = "createdAt")
    TransactionProjection toDomain(TransactionProjectionEntity entity);

    @Mapping(target = "id", source = "transactionId", qualifiedByName = "stringToUuid")
    @Mapping(target = "intentId", source = "intentId", qualifiedByName = "stringToUuid")
    @Mapping(target = "status", expression = "java(projection.status().name())")
    @Mapping(target = "transactionHash", source = "txHash")
    @Mapping(target = "asset", source = "token")
    @Mapping(target = "recoveryCount", source = "retryCount")
    @Mapping(target = "createdAt", source = "submittedAt")
    @Mapping(target = "updatedAt", expression = "java(java.time.Instant.now())")
    @Mapping(target = "chainFamily", ignore = true)
    @Mapping(target = "nonce", ignore = true)
    @Mapping(target = "gasPrice", ignore = true)
    @Mapping(target = "gasLimit", ignore = true)
    @Mapping(target = "gasUsed", ignore = true)
    @Mapping(target = "submissionStrategy", ignore = true)
    @Mapping(target = "currentRecoveryAction", ignore = true)
    @Mapping(target = "lastRecoveryAt", ignore = true)
    @Mapping(target = "metadata", ignore = true)
    @Mapping(target = "version", ignore = true)
    TransactionProjectionEntity toEntity(TransactionProjection projection);

    @Named("uuidToString")
    default String uuidToString(UUID uuid) {
        return uuid != null ? uuid.toString() : null;
    }

    @Named("stringToUuid")
    default UUID stringToUuid(String str) {
        return str != null ? UUID.fromString(str) : null;
    }

    default TransactionStatus mapStringToStatus(String status) {
        return TransactionStatus.valueOf(status);
    }
}
