package com.stablebridge.txrecovery.domain.recovery.port;

import com.stablebridge.txrecovery.domain.recovery.model.FeeEstimate;
import com.stablebridge.txrecovery.domain.recovery.model.FeeUrgency;

public interface FeeOracle {

    FeeEstimate estimate(String chain, FeeUrgency urgency);

    FeeEstimate estimateReplacement(String chain, String originalTxHash, int attemptNumber);
}
