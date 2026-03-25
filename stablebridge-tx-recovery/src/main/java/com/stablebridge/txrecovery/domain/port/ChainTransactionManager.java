package com.stablebridge.txrecovery.domain.port;

import com.stablebridge.txrecovery.domain.model.BroadcastResult;
import com.stablebridge.txrecovery.domain.model.SignedTransaction;
import com.stablebridge.txrecovery.domain.model.SubmissionResource;
import com.stablebridge.txrecovery.domain.model.TransactionIntent;
import com.stablebridge.txrecovery.domain.model.TransactionStatus;
import com.stablebridge.txrecovery.domain.model.UnsignedTransaction;

public interface ChainTransactionManager {

    UnsignedTransaction build(TransactionIntent intent, SubmissionResource resource);

    BroadcastResult broadcast(SignedTransaction signedTransaction, String chain);

    TransactionStatus checkStatus(String txHash, String chain);
}
