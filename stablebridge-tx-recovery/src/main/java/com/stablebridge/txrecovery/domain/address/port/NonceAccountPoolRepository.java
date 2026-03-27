package com.stablebridge.txrecovery.domain.address.port;

import java.util.Optional;

import com.stablebridge.txrecovery.domain.address.model.SolanaNonceAccount;

public interface NonceAccountPoolRepository {

    Optional<SolanaNonceAccount> findAvailableAndMarkInUse(String chain, String allocatedToTx);

    void markAvailable(String nonceAccountAddress, String chain);

    void consumeAndRelease(String nonceAccountAddress, String chain, String newNonceValue);

    long countAvailableByChain(String chain);
}
