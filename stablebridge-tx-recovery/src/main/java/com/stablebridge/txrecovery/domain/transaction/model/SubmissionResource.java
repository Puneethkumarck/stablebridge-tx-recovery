package com.stablebridge.txrecovery.domain.transaction.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = EvmSubmissionResource.class, name = "Evm"),
        @JsonSubTypes.Type(value = SolanaSubmissionResource.class, name = "Solana")
})
public sealed interface SubmissionResource
        permits EvmSubmissionResource, SolanaSubmissionResource {

    String chain();

    String fromAddress();
}
