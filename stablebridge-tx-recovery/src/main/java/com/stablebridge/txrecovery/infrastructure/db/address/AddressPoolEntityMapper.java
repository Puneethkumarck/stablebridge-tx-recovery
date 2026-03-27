package com.stablebridge.txrecovery.infrastructure.db.address;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.stablebridge.txrecovery.domain.address.model.PooledAddress;

@Mapper(componentModel = "spring")
public interface AddressPoolEntityMapper {

    @Mapping(target = "chainFamily", expression = "java(com.stablebridge.txrecovery.domain.address.model.ChainFamily.valueOf(entity.getChainFamily()))")
    @Mapping(target = "tier", expression = "java(com.stablebridge.txrecovery.domain.address.model.AddressTier.valueOf(entity.getTier()))")
    PooledAddress toDomain(AddressPoolEntity entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "retiredAt", ignore = true)
    @Mapping(target = "chainFamily", expression = "java(pooledAddress.chainFamily().name())")
    @Mapping(target = "tier", expression = "java(pooledAddress.tier().name())")
    AddressPoolEntity toEntity(PooledAddress pooledAddress);
}
