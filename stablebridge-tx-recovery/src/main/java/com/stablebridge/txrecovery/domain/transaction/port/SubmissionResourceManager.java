package com.stablebridge.txrecovery.domain.transaction.port;

import com.stablebridge.txrecovery.domain.transaction.model.SubmissionResource;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionIntent;

public interface SubmissionResourceManager {

    SubmissionResource acquire(TransactionIntent intent);

    void release(SubmissionResource resource);

    void consume(SubmissionResource resource);
}
