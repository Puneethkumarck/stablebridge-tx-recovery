package com.stablebridge.txrecovery.application.controller.address.mapper;

import java.util.List;

import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.stablebridge.txrecovery.api.model.AddressResponse;
import com.stablebridge.txrecovery.api.model.DrainResponse;
import com.stablebridge.txrecovery.api.model.NonceSyncResponse;
import com.stablebridge.txrecovery.domain.address.model.AddressStatus;
import com.stablebridge.txrecovery.domain.address.model.AddressTier;
import com.stablebridge.txrecovery.domain.address.model.DrainResult;
import com.stablebridge.txrecovery.domain.address.model.NonceSyncResult;
import com.stablebridge.txrecovery.domain.address.model.PooledAddress;
import com.stablebridge.txrecovery.domain.exception.InvalidParameterException;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface AddressPoolControllerMapper {

    @Mapping(target = "id", expression = "java(pooledAddress.id().toString())")
    @Mapping(target = "chainFamily", expression = "java(pooledAddress.chainFamily().name())")
    @Mapping(target = "tier", expression = "java(pooledAddress.tier().name())")
    @Mapping(target = "status", expression = "java(pooledAddress.status().name())")
    AddressResponse toResponse(PooledAddress pooledAddress);

    List<AddressResponse> toResponseList(List<PooledAddress> addresses);

    @Mapping(target = "address", source = "address.address")
    @Mapping(target = "chain", source = "address.chain")
    @Mapping(target = "previousStatus", expression = "java(drainResult.previousStatus().name())")
    @Mapping(target = "newStatus", expression = "java(drainResult.address().status().name())")
    DrainResponse toDrainResponse(DrainResult drainResult);

    @Mapping(target = "address", source = "address.address")
    @Mapping(target = "chain", source = "address.chain")
    NonceSyncResponse toNonceSyncResponse(NonceSyncResult result);

    default AddressTier parseTier(String tier) {
        try {
            return AddressTier.valueOf(tier);
        } catch (IllegalArgumentException _) {
            throw new InvalidParameterException("tier", tier);
        }
    }

    default AddressStatus parseStatus(String status) {
        try {
            return AddressStatus.valueOf(status);
        } catch (IllegalArgumentException _) {
            throw new InvalidParameterException("status", status);
        }
    }
}
