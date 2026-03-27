package com.stablebridge.txrecovery.infrastructure.db.address;

import org.mapstruct.Mapper;

import com.stablebridge.txrecovery.domain.address.model.PooledAddress;

@Mapper(componentModel = "spring")
interface AddressPoolEntityMapper {

    PooledAddress toDomain(AddressPoolEntity entity);

    AddressPoolEntity toEntity(PooledAddress pooledAddress);
}
