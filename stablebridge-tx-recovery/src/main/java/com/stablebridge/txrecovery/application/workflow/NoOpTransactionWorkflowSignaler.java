package com.stablebridge.txrecovery.application.workflow;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.stablebridge.txrecovery.domain.recovery.model.CancelRequest;
import com.stablebridge.txrecovery.domain.recovery.model.HumanApproval;
import com.stablebridge.txrecovery.domain.transaction.port.TransactionWorkflowSignaler;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "str.temporal", name = "enabled", havingValue = "false")
public class NoOpTransactionWorkflowSignaler implements TransactionWorkflowSignaler {

    @Override
    public void signalApproveRecovery(String transactionId, HumanApproval approval) {
        log.warn("Temporal disabled: approveRecovery signal not sent for transactionId={}", transactionId);
    }

    @Override
    public void signalCancelTransaction(String transactionId, CancelRequest request) {
        log.warn("Temporal disabled: cancelTransaction signal not sent for transactionId={}", transactionId);
    }
}
