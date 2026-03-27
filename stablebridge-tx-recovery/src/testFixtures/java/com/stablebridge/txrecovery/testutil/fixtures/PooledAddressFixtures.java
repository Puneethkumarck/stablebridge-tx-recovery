package com.stablebridge.txrecovery.testutil.fixtures;

import java.time.Instant;

import com.stablebridge.txrecovery.domain.address.model.AddressStatus;
import com.stablebridge.txrecovery.domain.address.model.AddressTier;
import com.stablebridge.txrecovery.domain.address.model.ChainFamily;
import com.stablebridge.txrecovery.domain.address.model.PooledAddress;

public final class PooledAddressFixtures {

    private PooledAddressFixtures() {}


    public static final String SOME_ADDRESS = "0x1234567890abcdef1234567890abcdef12345678";
    public static final String SOME_CHAIN = "ethereum";
    public static final String SOME_SIGNER_ENDPOINT = "http://signer:8080";

    public static final PooledAddress SOME_ACTIVE_HOT_ADDRESS = PooledAddress.builder()
            .address(SOME_ADDRESS)
            .chain(SOME_CHAIN)
            .chainFamily(ChainFamily.EVM)
            .tier(AddressTier.HOT)
            .status(AddressStatus.ACTIVE)
            .currentNonce(0)
            .inFlightCount(0)
            .signerEndpoint(SOME_SIGNER_ENDPOINT)
            .registeredAt(Instant.parse("2026-01-01T00:00:00Z"))
            .build();

    public static final PooledAddress SOME_ACTIVE_PRIORITY_ADDRESS = SOME_ACTIVE_HOT_ADDRESS.toBuilder()
            .address("0xpriority000000000000000000000000000000001")
            .tier(AddressTier.PRIORITY)
            .build();
}
