package com.stablebridge.txrecovery.application.workflow;

import com.stablebridge.txrecovery.domain.recovery.model.CancelRequest;
import com.stablebridge.txrecovery.domain.recovery.model.HumanApproval;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionIntent;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionResult;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionSnapshot;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface TransactionLifecycleWorkflow {

    String WORKFLOW_ID_PREFIX = "str-tx-";

    static String workflowId(String intentId) {
        return WORKFLOW_ID_PREFIX + intentId;
    }

    @WorkflowMethod
    TransactionResult process(TransactionIntent intent);

    @SignalMethod
    void approveRecovery(HumanApproval approval);

    @SignalMethod
    void cancelTransaction(CancelRequest request);

    @QueryMethod
    TransactionSnapshot getStatus();
}
