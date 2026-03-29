package com.stablebridge.txrecovery.application.workflow;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.stablebridge.txrecovery.domain.transaction.model.TransactionIntent;
import com.stablebridge.txrecovery.domain.transaction.port.TransactionWorkflowStarter;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "str.temporal", name = "enabled", havingValue = "false")
public class NoOpTransactionWorkflowStarter implements TransactionWorkflowStarter {

    @Override
    public void startWorkflow(TransactionIntent intent) {
        log.warn("Temporal is disabled — workflow not started for intentId={}", intent.intentId());
    }
}
