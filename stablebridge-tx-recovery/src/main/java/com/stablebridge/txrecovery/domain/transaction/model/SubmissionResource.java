package com.stablebridge.txrecovery.domain.transaction.model;

public sealed interface SubmissionResource
        permits EvmSubmissionResource, SolanaSubmissionResource {

    String chain();

    String fromAddress();
}
