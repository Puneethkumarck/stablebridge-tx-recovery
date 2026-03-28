package com.stablebridge.txrecovery.application.workflow;

import com.stablebridge.txrecovery.domain.recovery.model.RecoveryPlan;
import com.stablebridge.txrecovery.domain.recovery.model.RecoveryResult;
import com.stablebridge.txrecovery.domain.recovery.model.StuckAssessment;
import com.stablebridge.txrecovery.domain.transaction.event.TransactionLifecycleEvent;
import com.stablebridge.txrecovery.domain.transaction.model.BroadcastResult;
import com.stablebridge.txrecovery.domain.transaction.model.ConfirmationStatus;
import com.stablebridge.txrecovery.domain.transaction.model.SignedTransaction;
import com.stablebridge.txrecovery.domain.transaction.model.SubmissionResource;
import com.stablebridge.txrecovery.domain.transaction.model.SubmittedTransaction;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionIntent;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionStatus;
import com.stablebridge.txrecovery.domain.transaction.model.UnsignedTransaction;

import io.temporal.activity.ActivityInterface;

@ActivityInterface
public interface TransactionLifecycleActivities {

    SubmissionResource acquireResource(TransactionIntent intent);

    void releaseResource(SubmissionResource resource);

    void consumeResource(SubmissionResource resource);

    UnsignedTransaction build(TransactionIntent intent, SubmissionResource resource);

    SignedTransaction sign(UnsignedTransaction transaction, String fromAddress);

    BroadcastResult broadcast(SignedTransaction signedTransaction, String chain);

    TransactionStatus checkStatus(String txHash, String chain);

    ConfirmationStatus waitForFinality(String txHash, String chain);

    StuckAssessment assessStuck(SubmittedTransaction transaction);

    RecoveryResult executeRecovery(RecoveryPlan plan);

    void publishEvent(TransactionLifecycleEvent event);
}
