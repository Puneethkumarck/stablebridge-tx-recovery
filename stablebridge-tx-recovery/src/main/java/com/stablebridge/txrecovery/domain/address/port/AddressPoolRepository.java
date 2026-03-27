package com.stablebridge.txrecovery.domain.address.port;

import java.util.Optional;

import com.stablebridge.txrecovery.domain.address.model.AddressStatus;
import com.stablebridge.txrecovery.domain.address.model.AddressTier;
import com.stablebridge.txrecovery.domain.address.model.PooledAddress;

public interface AddressPoolRepository {

    Optional<PooledAddress> findBestCandidate(String chain, AddressTier tier, AddressStatus status, int maxInFlight);

    void incrementInFlightCount(String address, String chain);

    void decrementInFlightCount(String address, String chain);

    PooledAddress save(PooledAddress pooledAddress);
}
