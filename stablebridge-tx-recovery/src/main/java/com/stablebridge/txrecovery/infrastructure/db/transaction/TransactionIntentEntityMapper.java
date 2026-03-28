package com.stablebridge.txrecovery.infrastructure.db.transaction;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.stablebridge.txrecovery.domain.transaction.model.TransactionIntent;

@Mapper(componentModel = "spring")
interface TransactionIntentEntityMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "strategy", expression = "java(intent.strategy() != null ? intent.strategy().name() : null)")
    TransactionIntentEntity toEntity(TransactionIntent intent);

    @Mapping(target = "strategy",
            expression = "java(entity.getStrategy() != null ? com.stablebridge.txrecovery.domain.transaction.model.SubmissionStrategy.valueOf(entity.getStrategy()) : null)")
    TransactionIntent toDomain(TransactionIntentEntity entity);
}
