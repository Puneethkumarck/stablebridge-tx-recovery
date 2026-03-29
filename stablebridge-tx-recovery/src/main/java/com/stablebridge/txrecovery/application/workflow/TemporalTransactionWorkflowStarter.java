package com.stablebridge.txrecovery.application.workflow;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.stablebridge.txrecovery.application.config.TemporalProperties;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionIntent;
import com.stablebridge.txrecovery.domain.transaction.port.TransactionWorkflowStarter;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "str.temporal", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TemporalTransactionWorkflowStarter implements TransactionWorkflowStarter {

    private final WorkflowClient workflowClient;
    private final TemporalProperties temporalProperties;

    @Override
    public void startWorkflow(TransactionIntent intent) {
        var options = WorkflowOptions.newBuilder()
                .setWorkflowId(TransactionLifecycleWorkflow.workflowId(intent.intentId()))
                .setTaskQueue(temporalProperties.taskQueue())
                .build();
        var workflow = workflowClient.newWorkflowStub(TransactionLifecycleWorkflow.class, options);
        WorkflowClient.start(workflow::process, intent, null);

        log.info("Started Temporal workflow for intentId={}", intent.intentId());
    }
}
