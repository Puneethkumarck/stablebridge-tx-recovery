package com.stablebridge.txrecovery.domain.recovery.port;

import java.util.Optional;

import com.stablebridge.txrecovery.domain.recovery.model.FeeEstimate;
import com.stablebridge.txrecovery.domain.recovery.model.FeeUrgency;

public interface FeeCache {

    Optional<FeeEstimate> read(String chain, FeeUrgency urgency);

    void write(String chain, FeeUrgency urgency, FeeEstimate estimate, long ttlMillis);
}
