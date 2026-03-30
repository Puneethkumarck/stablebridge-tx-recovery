package com.stablebridge.txrecovery.domain.recovery;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.stablebridge.txrecovery.domain.exception.ChainNotFoundException;
import com.stablebridge.txrecovery.domain.recovery.model.FeeEstimate;
import com.stablebridge.txrecovery.domain.recovery.model.FeeUrgency;
import com.stablebridge.txrecovery.domain.recovery.model.GasSnapshot;
import com.stablebridge.txrecovery.domain.recovery.port.ChainConfigProvider;
import com.stablebridge.txrecovery.domain.recovery.port.FeeOracle;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class GasOracleQueryService {

    private final Map<String, FeeOracle> evmFeeOracles;
    private final ChainConfigProvider chainConfigProvider;
    private final Clock clock;

    public GasSnapshot getCurrentEstimates(String chain) {
        validateChainExists(chain);
        var oracle = resolveOracle(chain);

        var estimates = Arrays.stream(FeeUrgency.values())
                .map(urgency -> oracle.estimate(chain, urgency))
                .toList();

        return GasSnapshot.builder()
                .chain(chain)
                .estimates(estimates)
                .updatedAt(Instant.now(clock))
                .build();
    }

    public List<FeeEstimate> getHistoricalEstimates(String chain, int hours) {
        if (hours < 1 || hours > 168) {
            throw new IllegalArgumentException("Hours must be between 1 and 168");
        }
        validateChainExists(chain);
        resolveOracle(chain);
        return List.of();
    }

    private void validateChainExists(String chain) {
        if (chain == null || chain.isBlank()) {
            throw new IllegalArgumentException("Chain must not be null or blank");
        }
        if (!chainConfigProvider.isChainEnabled(chain)) {
            throw new ChainNotFoundException(chain);
        }
    }

    private FeeOracle resolveOracle(String chain) {
        var oracle = evmFeeOracles.get(chain);
        if (oracle == null) {
            throw new ChainNotFoundException(chain);
        }
        return oracle;
    }
}
