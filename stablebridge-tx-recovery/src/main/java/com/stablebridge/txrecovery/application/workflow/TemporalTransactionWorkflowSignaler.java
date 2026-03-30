package com.stablebridge.txrecovery.application.workflow;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.stablebridge.txrecovery.domain.recovery.model.CancelRequest;
import com.stablebridge.txrecovery.domain.recovery.model.HumanApproval;
import com.stablebridge.txrecovery.domain.transaction.port.TransactionWorkflowSignaler;

import io.temporal.client.WorkflowClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "str.temporal", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TemporalTransactionWorkflowSignaler implements TransactionWorkflowSignaler {

    private final WorkflowClient workflowClient;

    @Override
    public void signalApproveRecovery(String transactionId, HumanApproval approval) {
        var workflowId = TransactionLifecycleWorkflow.workflowId(transactionId);
        var workflow = workflowClient.newWorkflowStub(TransactionLifecycleWorkflow.class, workflowId);
        workflow.approveRecovery(approval);
        log.info("Sent approveRecovery signal to workflow: workflowId={}", workflowId);
    }

    @Override
    public void signalCancelTransaction(String transactionId, CancelRequest request) {
        var workflowId = TransactionLifecycleWorkflow.workflowId(transactionId);
        var workflow = workflowClient.newWorkflowStub(TransactionLifecycleWorkflow.class, workflowId);
        workflow.cancelTransaction(request);
        log.info("Sent cancelTransaction signal to workflow: workflowId={}", workflowId);
    }
}
