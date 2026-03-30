package com.stablebridge.txrecovery.testutil.fixtures;

import java.time.Instant;
import java.util.UUID;

import com.stablebridge.txrecovery.api.model.AddressResponse;
import com.stablebridge.txrecovery.api.model.RegisterAddressRequest;
import com.stablebridge.txrecovery.domain.address.model.AddressStatus;
import com.stablebridge.txrecovery.domain.address.model.AddressTier;
import com.stablebridge.txrecovery.domain.address.model.ChainFamily;
import com.stablebridge.txrecovery.domain.address.model.PooledAddress;

public final class AddressPoolControllerFixtures {

    private AddressPoolControllerFixtures() {}

    public static final String SOME_EVM_ADDRESS = "0xAb5801a7D398351b8bE11C439e05C5B3259aeC9B";
    public static final String SOME_CHAIN = "ethereum";
    public static final String SOME_SIGNER_ENDPOINT = "http://signer:8080";
    public static final UUID SOME_ADDRESS_ID = UUID.fromString("019576a0-e29b-7000-a716-446655440099");
    public static final Instant SOME_REGISTERED_AT = Instant.parse("2026-01-15T10:00:00Z");

    public static final RegisterAddressRequest SOME_REGISTER_REQUEST = RegisterAddressRequest.builder()
            .address(SOME_EVM_ADDRESS)
            .chain(SOME_CHAIN)
            .chainFamily("EVM")
            .tier("HOT")
            .signerEndpoint(SOME_SIGNER_ENDPOINT)
            .build();

    public static final PooledAddress SOME_REGISTERED_ADDRESS = PooledAddress.builder()
            .id(SOME_ADDRESS_ID)
            .address(SOME_EVM_ADDRESS)
            .chain(SOME_CHAIN)
            .chainFamily(ChainFamily.EVM)
            .tier(AddressTier.HOT)
            .status(AddressStatus.ACTIVE)
            .currentNonce(42)
            .inFlightCount(0)
            .signerEndpoint(SOME_SIGNER_ENDPOINT)
            .registeredAt(SOME_REGISTERED_AT)
            .build();

    public static final PooledAddress SOME_DRAINING_ADDRESS = SOME_REGISTERED_ADDRESS.toBuilder()
            .status(AddressStatus.DRAINING)
            .inFlightCount(2)
            .build();

    public static final AddressResponse SOME_ADDRESS_RESPONSE = AddressResponse.builder()
            .id(SOME_ADDRESS_ID.toString())
            .address(SOME_EVM_ADDRESS)
            .chain(SOME_CHAIN)
            .chainFamily("EVM")
            .tier("HOT")
            .status("ACTIVE")
            .currentNonce(42)
            .inFlightCount(0)
            .signerEndpoint(SOME_SIGNER_ENDPOINT)
            .registeredAt(SOME_REGISTERED_AT)
            .build();

    public static final PooledAddress SOME_SYNCED_ADDRESS = SOME_REGISTERED_ADDRESS.toBuilder()
            .currentNonce(100)
            .build();
}
