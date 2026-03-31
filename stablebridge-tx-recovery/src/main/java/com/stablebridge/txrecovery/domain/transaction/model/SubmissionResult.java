package com.stablebridge.txrecovery.domain.transaction.model;

public sealed interface SubmissionResult {

    TransactionProjection projection();

    record Created(TransactionProjection projection) implements SubmissionResult {}

    record AlreadyExists(TransactionProjection projection) implements SubmissionResult {}
}
