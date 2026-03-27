package com.stablebridge.txrecovery.infrastructure.db.address;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.stablebridge.txrecovery.domain.address.model.PooledAddress;

@Mapper(componentModel = "spring")
interface AddressPoolEntityMapper {

    PooledAddress toDomain(AddressPoolEntity entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "retiredAt", ignore = true)
    AddressPoolEntity toEntity(PooledAddress pooledAddress);
}
