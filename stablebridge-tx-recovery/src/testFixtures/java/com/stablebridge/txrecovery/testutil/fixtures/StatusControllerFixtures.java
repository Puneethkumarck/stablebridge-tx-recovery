package com.stablebridge.txrecovery.testutil.fixtures;

import static lombok.AccessLevel.PRIVATE;

import java.util.List;

import com.stablebridge.txrecovery.api.model.ChainStatusDetailResponse;
import com.stablebridge.txrecovery.api.model.ChainStatusSummaryResponse;
import com.stablebridge.txrecovery.domain.status.model.ChainStatusDetail;
import com.stablebridge.txrecovery.domain.status.model.ChainStatusSummary;
import com.stablebridge.txrecovery.domain.status.model.HealthStatus;

import lombok.NoArgsConstructor;

@NoArgsConstructor(access = PRIVATE)
public final class StatusControllerFixtures {

    public static final String SOME_CHAIN = "ethereum_mainnet";
    public static final String SOME_UNKNOWN_CHAIN = "unknown_chain";

    public static final ChainStatusSummary SOME_CHAIN_STATUS_SUMMARY = ChainStatusSummary.builder()
            .chain(SOME_CHAIN)
            .healthy(true)
            .pendingCount(5)
            .stuckCount(1)
            .rpcLatencyMs(42)
            .status(HealthStatus.UP)
            .build();

    public static final List<ChainStatusSummary> SOME_CHAIN_STATUS_SUMMARIES =
            List.of(SOME_CHAIN_STATUS_SUMMARY);

    public static final ChainStatusDetail SOME_CHAIN_STATUS_DETAIL = ChainStatusDetail.builder()
            .chain(SOME_CHAIN)
            .healthy(true)
            .pendingCount(5)
            .stuckCount(1)
            .rpcLatencyMs(42)
            .status(HealthStatus.UP)
            .addressPoolTotal(3)
            .addressPoolActive(2)
            .addressPoolDraining(1)
            .nonceGapCount(0)
            .nonceInFlightTotal(4)
            .build();

    public static final ChainStatusSummaryResponse SOME_CHAIN_STATUS_SUMMARY_RESPONSE =
            ChainStatusSummaryResponse.builder()
                    .chain(SOME_CHAIN)
                    .healthy(true)
                    .pendingCount(5)
                    .stuckCount(1)
                    .rpcLatencyMs(42)
                    .status("UP")
                    .build();

    public static final ChainStatusDetailResponse SOME_CHAIN_STATUS_DETAIL_RESPONSE =
            ChainStatusDetailResponse.builder()
                    .chain(SOME_CHAIN)
                    .healthy(true)
                    .pendingCount(5)
                    .stuckCount(1)
                    .rpcLatencyMs(42)
                    .status("UP")
                    .addressPool(ChainStatusDetailResponse.AddressPoolStats.builder()
                            .total(3)
                            .active(2)
                            .draining(1)
                            .build())
                    .nonceHealth(ChainStatusDetailResponse.NonceHealthStats.builder()
                            .gapCount(0)
                            .inFlightTotal(4)
                            .build())
                    .build();
}
