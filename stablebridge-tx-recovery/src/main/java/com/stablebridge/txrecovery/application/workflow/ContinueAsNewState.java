package com.stablebridge.txrecovery.application.workflow;

import java.math.BigDecimal;

import com.stablebridge.txrecovery.domain.recovery.model.CancelRequest;
import com.stablebridge.txrecovery.domain.recovery.model.EscalationTier;
import com.stablebridge.txrecovery.domain.recovery.model.HumanApproval;
import com.stablebridge.txrecovery.domain.transaction.model.SubmissionResource;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionStatus;

import lombok.Builder;

@Builder(toBuilder = true)
public record ContinueAsNewState(
        String transactionId,
        String intentId,
        String chain,
        TransactionStatus currentState,
        String txHash,
        int retryCount,
        BigDecimal totalGasSpent,
        BigDecimal gasBudget,
        String gasDenomination,
        EscalationTier currentTier,
        SubmissionResource currentResource,
        HumanApproval pendingApproval,
        boolean cancelRequested,
        CancelRequest cancelRequest) {}
