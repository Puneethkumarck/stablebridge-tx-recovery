package com.stablebridge.txrecovery.infrastructure.db.transaction;

import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.stablebridge.txrecovery.domain.transaction.model.SubmissionStrategy;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionIntent;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
interface TransactionIntentEntityMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "strategy", expression = "java(mapStrategyToString(intent.strategy()))")
    TransactionIntentEntity toEntity(TransactionIntent intent);

    @Mapping(target = "strategy", expression = "java(mapStringToStrategy(entity.getStrategy()))")
    TransactionIntent toDomain(TransactionIntentEntity entity);

    default String mapStrategyToString(SubmissionStrategy strategy) {
        return strategy != null ? strategy.name() : null;
    }

    default SubmissionStrategy mapStringToStrategy(String strategy) {
        return strategy != null ? SubmissionStrategy.valueOf(strategy) : null;
    }
}
