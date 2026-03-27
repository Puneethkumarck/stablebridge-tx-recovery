package com.stablebridge.txrecovery.domain.address.port;

import java.util.Set;

import com.stablebridge.txrecovery.domain.address.model.NonceAllocation;

public interface NonceManager {

    NonceAllocation allocate(String address, String chain);

    void release(NonceAllocation allocation);

    void confirm(NonceAllocation allocation);

    void syncFromChain(String address, String chain);

    Set<Long> detectGaps(String address, String chain);
}
