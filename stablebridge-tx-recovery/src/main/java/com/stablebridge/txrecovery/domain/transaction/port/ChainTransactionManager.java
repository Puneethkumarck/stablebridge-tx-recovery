package com.stablebridge.txrecovery.domain.transaction.port;

import com.stablebridge.txrecovery.domain.transaction.model.BroadcastResult;
import com.stablebridge.txrecovery.domain.transaction.model.ConfirmationStatus;
import com.stablebridge.txrecovery.domain.transaction.model.SignedTransaction;
import com.stablebridge.txrecovery.domain.transaction.model.SubmissionResource;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionIntent;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionStatus;
import com.stablebridge.txrecovery.domain.transaction.model.UnsignedTransaction;

public interface ChainTransactionManager {

    UnsignedTransaction build(TransactionIntent intent, SubmissionResource resource);

    BroadcastResult broadcast(SignedTransaction signedTransaction, String chain);

    TransactionStatus checkStatus(String txHash, String chain);

    default ConfirmationStatus getConfirmationStatus(String txHash, String chain) {
        var status = checkStatus(txHash, chain);
        var finalized = status == TransactionStatus.FINALIZED;
        var confirmed = status == TransactionStatus.CONFIRMED || finalized;
        return ConfirmationStatus.builder()
                .txHash(txHash)
                .chain(chain)
                .confirmations(confirmed ? 1 : 0)
                .requiredConfirmations(1)
                .finalized(finalized)
                .build();
    }
}
