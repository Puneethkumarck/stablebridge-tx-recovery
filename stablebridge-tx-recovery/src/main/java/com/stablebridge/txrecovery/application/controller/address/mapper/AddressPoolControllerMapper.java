package com.stablebridge.txrecovery.application.controller.address.mapper;

import java.util.List;

import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.stablebridge.txrecovery.api.model.AddressResponse;
import com.stablebridge.txrecovery.domain.address.model.PooledAddress;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface AddressPoolControllerMapper {

    @Mapping(target = "id", expression = "java(pooledAddress.id().toString())")
    @Mapping(target = "chainFamily", expression = "java(pooledAddress.chainFamily().name())")
    @Mapping(target = "tier", expression = "java(pooledAddress.tier().name())")
    @Mapping(target = "status", expression = "java(pooledAddress.status().name())")
    AddressResponse toResponse(PooledAddress pooledAddress);

    List<AddressResponse> toResponseList(List<PooledAddress> addresses);
}
