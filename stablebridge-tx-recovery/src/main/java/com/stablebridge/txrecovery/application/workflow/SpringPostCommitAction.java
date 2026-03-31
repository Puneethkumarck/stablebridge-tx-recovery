package com.stablebridge.txrecovery.application.workflow;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.stablebridge.txrecovery.domain.transaction.port.PostCommitAction;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class SpringPostCommitAction implements PostCommitAction {

    @Override
    public void executeAfterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    action.run();
                } catch (Exception ex) {
                    log.error("Post-commit callback failed", ex);
                }
            }
        });
    }
}
