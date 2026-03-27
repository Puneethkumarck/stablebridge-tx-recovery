package com.stablebridge.txrecovery.infrastructure.db.nonce;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.stablebridge.txrecovery.domain.address.model.SolanaNonceAccount;

@Mapper(componentModel = "spring")
interface NonceAccountPoolEntityMapper {

    @Mapping(source = "nonceAccount", target = "nonceAccountAddress")
    @Mapping(source = "currentNonceValue", target = "nonceValue")
    SolanaNonceAccount toDomain(NonceAccountPoolEntity entity);
}
