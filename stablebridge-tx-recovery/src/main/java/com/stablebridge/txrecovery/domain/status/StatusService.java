package com.stablebridge.txrecovery.domain.status;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.stablebridge.txrecovery.domain.address.model.AddressStatus;
import com.stablebridge.txrecovery.domain.address.model.PooledAddress;
import com.stablebridge.txrecovery.domain.address.port.AddressPoolRepository;
import com.stablebridge.txrecovery.domain.address.port.NonceManager;
import com.stablebridge.txrecovery.domain.exception.ChainNotFoundException;
import com.stablebridge.txrecovery.domain.recovery.port.ChainConfigProvider;
import com.stablebridge.txrecovery.domain.status.model.ChainStatusDetail;
import com.stablebridge.txrecovery.domain.status.model.ChainStatusSummary;
import com.stablebridge.txrecovery.domain.status.model.HealthStatus;
import com.stablebridge.txrecovery.domain.status.port.RpcHealthProvider;
import com.stablebridge.txrecovery.domain.status.port.TransactionCountProvider;
import com.stablebridge.txrecovery.domain.status.port.WorkflowHealthProvider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatusService {

    private final ChainConfigProvider chainConfigProvider;
    private final TransactionCountProvider transactionCountProvider;
    private final RpcHealthProvider rpcHealthProvider;
    private final WorkflowHealthProvider workflowHealthProvider;
    private final AddressPoolRepository addressPoolRepository;
    private final NonceManager nonceManager;

    public List<ChainStatusSummary> getAllChainStatuses() {
        var temporalHealthy = workflowHealthProvider.isHealthy();
        return chainConfigProvider.enabledChains().stream()
                .sorted()
                .map(chain -> buildSummary(chain, temporalHealthy))
                .toList();
    }

    public ChainStatusDetail getChainStatus(String chain) {
        if (!chainConfigProvider.isChainEnabled(chain)) {
            throw new ChainNotFoundException(chain);
        }
        var temporalHealthy = workflowHealthProvider.isHealthy();
        return buildDetail(chain, temporalHealthy);
    }

    private ChainStatusSummary buildSummary(String chain, boolean temporalHealthy) {
        var pendingCount = transactionCountProvider.countPendingByChain(chain);
        var stuckCount = transactionCountProvider.countStuckByChain(chain);
        var avgConfirmationMs = transactionCountProvider.averageConfirmationTimeMs(chain);
        var lastBlockSeen = getLastBlockSafely(chain);
        var rpcLatencyMs = measureLatencySafely(chain);
        var status = determineStatus(rpcLatencyMs, temporalHealthy);

        return ChainStatusSummary.builder()
                .chain(chain)
                .healthy(status == HealthStatus.UP)
                .pendingCount(pendingCount)
                .stuckCount(stuckCount)
                .avgConfirmationMs(avgConfirmationMs)
                .lastBlockSeen(lastBlockSeen)
                .rpcLatencyMs(rpcLatencyMs)
                .status(status)
                .build();
    }

    private ChainStatusDetail buildDetail(String chain, boolean temporalHealthy) {
        var pendingCount = transactionCountProvider.countPendingByChain(chain);
        var stuckCount = transactionCountProvider.countStuckByChain(chain);
        var avgConfirmationMs = transactionCountProvider.averageConfirmationTimeMs(chain);
        var lastBlockSeen = getLastBlockSafely(chain);
        var rpcLatencyMs = measureLatencySafely(chain);
        var status = determineStatus(rpcLatencyMs, temporalHealthy);

        var addresses = addressPoolRepository.findByFilters(chain, null, null);
        var activeCount = addresses.stream().filter(a -> a.status() == AddressStatus.ACTIVE).count();
        var drainingCount = addresses.stream().filter(a -> a.status() == AddressStatus.DRAINING).count();

        var gapCount = addresses.stream()
                .filter(a -> a.status() != AddressStatus.RETIRED)
                .mapToLong(a -> detectGapsSafely(a).size())
                .sum();
        var inFlightTotal = addresses.stream()
                .filter(a -> a.status() != AddressStatus.RETIRED)
                .mapToInt(PooledAddress::inFlightCount)
                .sum();

        return ChainStatusDetail.builder()
                .chain(chain)
                .healthy(status == HealthStatus.UP)
                .pendingCount(pendingCount)
                .stuckCount(stuckCount)
                .avgConfirmationMs(avgConfirmationMs)
                .lastBlockSeen(lastBlockSeen)
                .rpcLatencyMs(rpcLatencyMs)
                .status(status)
                .addressPoolTotal(addresses.size())
                .addressPoolActive(activeCount)
                .addressPoolDraining(drainingCount)
                .nonceGapCount(gapCount)
                .nonceInFlightTotal(inFlightTotal)
                .build();
    }

    private long getLastBlockSafely(String chain) {
        try {
            return rpcHealthProvider.getLastBlockNumber(chain);
        } catch (Exception e) {
            log.warn("Failed to get last block for chain {}: {}", chain, e.getMessage());
            return -1;
        }
    }

    private long measureLatencySafely(String chain) {
        try {
            return rpcHealthProvider.measureRpcLatencyMs(chain);
        } catch (Exception e) {
            log.warn("Failed to measure RPC latency for chain {}: {}", chain, e.getMessage());
            return -1;
        }
    }

    private Set<Long> detectGapsSafely(PooledAddress address) {
        try {
            return nonceManager.detectGaps(address.address(), address.chain());
        } catch (Exception e) {
            log.warn("Failed to detect nonce gaps for {}: {}", address.address(), e.getMessage());
            return Set.of();
        }
    }

    private HealthStatus determineStatus(long rpcLatencyMs, boolean temporalHealthy) {
        if (rpcLatencyMs < 0) {
            return HealthStatus.DOWN;
        }
        if (!temporalHealthy) {
            return HealthStatus.DEGRADED;
        }
        return HealthStatus.UP;
    }
}
