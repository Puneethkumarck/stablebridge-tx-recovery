package com.stablebridge.txrecovery.application.controller.status.mapper;

import java.util.List;

import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.stablebridge.txrecovery.api.model.ChainStatusDetailResponse;
import com.stablebridge.txrecovery.api.model.ChainStatusSummaryResponse;
import com.stablebridge.txrecovery.domain.status.model.ChainStatusDetail;
import com.stablebridge.txrecovery.domain.status.model.ChainStatusSummary;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface StatusControllerMapper {

    @Mapping(target = "status", expression = "java(summary.status().name())")
    ChainStatusSummaryResponse toSummaryResponse(ChainStatusSummary summary);

    List<ChainStatusSummaryResponse> toSummaryResponseList(List<ChainStatusSummary> summaries);

    @Mapping(target = "status", expression = "java(detail.status().name())")
    @Mapping(target = "addressPool", expression = "java(toAddressPoolStats(detail))")
    @Mapping(target = "nonceHealth", expression = "java(toNonceHealthStats(detail))")
    ChainStatusDetailResponse toDetailResponse(ChainStatusDetail detail);

    default ChainStatusDetailResponse.AddressPoolStats toAddressPoolStats(ChainStatusDetail detail) {
        return ChainStatusDetailResponse.AddressPoolStats.builder()
                .total(detail.addressPoolTotal())
                .active(detail.addressPoolActive())
                .draining(detail.addressPoolDraining())
                .build();
    }

    default ChainStatusDetailResponse.NonceHealthStats toNonceHealthStats(ChainStatusDetail detail) {
        return ChainStatusDetailResponse.NonceHealthStats.builder()
                .gapCount(detail.nonceGapCount())
                .inFlightTotal(detail.nonceInFlightTotal())
                .build();
    }
}
