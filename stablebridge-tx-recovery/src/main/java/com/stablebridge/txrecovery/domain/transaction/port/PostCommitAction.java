package com.stablebridge.txrecovery.domain.transaction.port;

public interface PostCommitAction {

    void executeAfterCommit(Runnable action);
}
