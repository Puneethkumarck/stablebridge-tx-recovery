package com.stablebridge.txrecovery.domain.port;

import com.stablebridge.txrecovery.domain.model.ChainFamily;
import com.stablebridge.txrecovery.domain.model.RecoveryPlan;
import com.stablebridge.txrecovery.domain.model.RecoveryResult;
import com.stablebridge.txrecovery.domain.model.StuckAssessment;
import com.stablebridge.txrecovery.domain.model.SubmittedTransaction;

public interface RecoveryStrategy {

    boolean appliesTo(ChainFamily chainFamily);

    StuckAssessment assess(SubmittedTransaction transaction);

    RecoveryResult execute(RecoveryPlan plan, TransactionSigner signer);
}
