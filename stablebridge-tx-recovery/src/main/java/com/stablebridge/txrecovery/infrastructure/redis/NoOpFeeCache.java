package com.stablebridge.txrecovery.infrastructure.redis;

import java.util.Optional;

import com.stablebridge.txrecovery.domain.recovery.model.FeeEstimate;
import com.stablebridge.txrecovery.domain.recovery.model.FeeUrgency;
import com.stablebridge.txrecovery.domain.recovery.port.FeeCache;

class NoOpFeeCache implements FeeCache {

    @Override
    public Optional<FeeEstimate> read(String chain, FeeUrgency urgency) {
        return Optional.empty();
    }

    @Override
    public void write(String chain, FeeUrgency urgency, FeeEstimate estimate, long ttlMillis) {
    }
}
