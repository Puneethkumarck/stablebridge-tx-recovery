package com.stablebridge.txrecovery.domain.port;

import com.stablebridge.txrecovery.domain.model.FeeEstimate;
import com.stablebridge.txrecovery.domain.model.FeeUrgency;

public interface FeeOracle {

    FeeEstimate estimate(String chain, FeeUrgency urgency);

    FeeEstimate estimateReplacement(String chain, String originalTxHash, int attemptNumber);
}
