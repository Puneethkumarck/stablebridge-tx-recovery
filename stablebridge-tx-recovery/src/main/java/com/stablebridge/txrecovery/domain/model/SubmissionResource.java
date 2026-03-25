package com.stablebridge.txrecovery.domain.model;

public sealed interface SubmissionResource
        permits EvmSubmissionResource, SolanaSubmissionResource {

    String chain();

    String fromAddress();
}
