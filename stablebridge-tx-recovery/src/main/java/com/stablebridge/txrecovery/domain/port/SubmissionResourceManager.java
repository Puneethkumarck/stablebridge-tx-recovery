package com.stablebridge.txrecovery.domain.port;

import com.stablebridge.txrecovery.domain.model.SubmissionResource;
import com.stablebridge.txrecovery.domain.model.TransactionIntent;

public interface SubmissionResourceManager {

    SubmissionResource acquire(TransactionIntent intent);

    void release(SubmissionResource resource);

    void consume(SubmissionResource resource);
}
