package com.stablebridge.txrecovery.domain.port;

import com.stablebridge.txrecovery.domain.model.NonceAllocation;

public interface NonceManager {

    NonceAllocation allocate(String address, String chain);

    void release(NonceAllocation allocation);

    void confirm(NonceAllocation allocation);

    void syncFromChain(String address, String chain);
}
