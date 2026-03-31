package com.stablebridge.txrecovery.domain.transaction.model;

import java.util.Objects;

public sealed interface SubmissionResult {

    TransactionProjection projection();

    record Created(TransactionProjection projection) implements SubmissionResult {
        public Created {
            Objects.requireNonNull(projection, "projection");
        }
    }

    record AlreadyExists(TransactionProjection projection) implements SubmissionResult {
        public AlreadyExists {
            Objects.requireNonNull(projection, "projection");
        }
    }
}
