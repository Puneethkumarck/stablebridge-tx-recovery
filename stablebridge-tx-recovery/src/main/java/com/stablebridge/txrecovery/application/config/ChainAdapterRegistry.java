package com.stablebridge.txrecovery.application.config;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.stablebridge.txrecovery.domain.address.model.ChainFamily;
import com.stablebridge.txrecovery.domain.exception.UnknownChainException;
import com.stablebridge.txrecovery.domain.recovery.port.FeeOracle;
import com.stablebridge.txrecovery.domain.recovery.port.RecoveryStrategy;
import com.stablebridge.txrecovery.domain.transaction.port.ChainTransactionManager;
import com.stablebridge.txrecovery.domain.transaction.port.SubmissionResourceManager;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ChainAdapterRegistry {

    private final Map<String, ChainTransactionManager> transactionManagers;
    private final Map<String, SubmissionResourceManager> resourceManagers;
    private final Map<String, FeeOracle> feeOracles;
    private final Map<String, RecoveryStrategy> recoveryStrategies;
    private final Map<String, ChainFamily> chainFamilyMapping;

    public ChainTransactionManager getTransactionManager(String chain) {
        return Optional.ofNullable(transactionManagers.get(chain))
                .orElseThrow(() -> new UnknownChainException(chain));
    }

    public SubmissionResourceManager getResourceManager(String chain) {
        return Optional.ofNullable(resourceManagers.get(chain))
                .orElseThrow(() -> new UnknownChainException(chain));
    }

    public FeeOracle getFeeOracle(String chain) {
        return Optional.ofNullable(feeOracles.get(chain))
                .orElseThrow(() -> new UnknownChainException(chain));
    }

    public RecoveryStrategy getRecoveryStrategy(String chain) {
        return Optional.ofNullable(recoveryStrategies.get(chain))
                .orElseThrow(() -> new UnknownChainException(chain));
    }

    public ChainFamily getChainFamily(String chain) {
        return Optional.ofNullable(chainFamilyMapping.get(chain))
                .orElseThrow(() -> new UnknownChainException(chain));
    }

    public Set<String> enabledChains() {
        return Set.copyOf(transactionManagers.keySet());
    }
}
