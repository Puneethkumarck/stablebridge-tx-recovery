package com.stablebridge.txrecovery.domain.common.model;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import com.stablebridge.txrecovery.domain.address.model.ChainFamily;

import lombok.Builder;

@Builder(toBuilder = true)
public record ChainConfig(
        String chainName,
        ChainFamily chainFamily,
        long chainId,
        int finalityBlocks,
        int stuckThresholdBlocks,
        Duration pollInterval,
        BigDecimal maxFeeCapGwei,
        List<String> tokenContracts,
        List<String> tokenMints,
        List<String> rpcUrls,
        Duration rpcTimeout,
        int rateLimitRps,
        int rateLimitBurst,
        int cbFailureRateThreshold,
        Duration cbWaitDurationInOpenState,
        int cbSlidingWindowSize) {

    public ChainConfig {
        tokenContracts = tokenContracts == null ? List.of() : List.copyOf(tokenContracts);
        tokenMints = tokenMints == null ? List.of() : List.copyOf(tokenMints);
        rpcUrls = rpcUrls == null ? List.of() : List.copyOf(rpcUrls);
    }
}
