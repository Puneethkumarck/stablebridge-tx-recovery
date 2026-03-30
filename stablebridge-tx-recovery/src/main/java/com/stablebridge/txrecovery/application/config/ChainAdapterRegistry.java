package com.stablebridge.txrecovery.application.config;

import java.util.Map;
import java.util.Optional;

import com.stablebridge.txrecovery.domain.exception.UnknownChainException;
import com.stablebridge.txrecovery.domain.recovery.port.RecoveryStrategy;
import com.stablebridge.txrecovery.domain.transaction.port.ChainTransactionManager;
import com.stablebridge.txrecovery.domain.transaction.port.SubmissionResourceManager;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ChainAdapterRegistry {

    private final Map<String, ChainTransactionManager> transactionManagers;
    private final Map<String, SubmissionResourceManager> resourceManagers;
    private final Map<String, RecoveryStrategy> recoveryStrategies;

    public ChainTransactionManager getTransactionManager(String chain) {
        return Optional.ofNullable(transactionManagers.get(chain))
                .orElseThrow(() -> new UnknownChainException(chain));
    }

    public SubmissionResourceManager getResourceManager(String chain) {
        return Optional.ofNullable(resourceManagers.get(chain))
                .orElseThrow(() -> new UnknownChainException(chain));
    }

    public RecoveryStrategy getRecoveryStrategy(String chain) {
        return Optional.ofNullable(recoveryStrategies.get(chain))
                .orElseThrow(() -> new UnknownChainException(chain));
    }
}
