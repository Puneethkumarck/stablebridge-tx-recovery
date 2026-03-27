package com.stablebridge.txrecovery.domain.recovery.port;

import com.stablebridge.txrecovery.domain.address.model.ChainFamily;
import com.stablebridge.txrecovery.domain.recovery.model.RecoveryPlan;
import com.stablebridge.txrecovery.domain.recovery.model.RecoveryResult;
import com.stablebridge.txrecovery.domain.recovery.model.StuckAssessment;
import com.stablebridge.txrecovery.domain.transaction.model.SubmittedTransaction;
import com.stablebridge.txrecovery.domain.transaction.port.TransactionSigner;

public interface RecoveryStrategy {

    boolean appliesTo(ChainFamily chainFamily);

    StuckAssessment assess(SubmittedTransaction transaction);

    RecoveryResult execute(RecoveryPlan plan, TransactionSigner signer);
}
