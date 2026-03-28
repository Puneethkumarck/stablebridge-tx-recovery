package com.stablebridge.txrecovery.domain.transaction.port;

import com.stablebridge.txrecovery.domain.transaction.model.TransactionIntent;

public interface TransactionWorkflowStarter {

    void startWorkflow(TransactionIntent intent);
}
