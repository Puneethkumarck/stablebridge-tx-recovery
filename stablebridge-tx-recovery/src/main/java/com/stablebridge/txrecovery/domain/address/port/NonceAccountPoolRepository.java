package com.stablebridge.txrecovery.domain.address.port;

import java.util.Optional;

import com.stablebridge.txrecovery.domain.address.model.SolanaNonceAccount;

public interface NonceAccountPoolRepository {

    Optional<SolanaNonceAccount> findAvailableByChain(String chain);

    void markInUse(String nonceAccountAddress, String chain, String allocatedToTx);

    void markAvailable(String nonceAccountAddress, String chain);

    void updateNonceValue(String nonceAccountAddress, String chain, String newNonceValue);

    void consumeAndRelease(String nonceAccountAddress, String chain, String newNonceValue);

    long countAvailableByChain(String chain);
}
