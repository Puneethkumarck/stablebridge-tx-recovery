package com.stablebridge.txrecovery.domain.transaction.port;

import com.stablebridge.txrecovery.domain.recovery.model.CancelRequest;
import com.stablebridge.txrecovery.domain.recovery.model.HumanApproval;

public interface TransactionWorkflowSignaler {

    void signalApproveRecovery(String transactionId, HumanApproval approval);

    void signalCancelTransaction(String transactionId, CancelRequest request);
}
