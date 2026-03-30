package com.stablebridge.txrecovery.testutil.fixtures;

import java.time.Instant;
import java.util.UUID;

import com.stablebridge.txrecovery.api.model.AddressResponse;
import com.stablebridge.txrecovery.api.model.DrainResponse;
import com.stablebridge.txrecovery.api.model.NonceSyncResponse;
import com.stablebridge.txrecovery.api.model.RegisterAddressRequest;
import com.stablebridge.txrecovery.domain.address.model.AddressStatus;
import com.stablebridge.txrecovery.domain.address.model.AddressTier;
import com.stablebridge.txrecovery.domain.address.model.ChainFamily;
import com.stablebridge.txrecovery.domain.address.model.DrainResult;
import com.stablebridge.txrecovery.domain.address.model.NonceSyncResult;
import com.stablebridge.txrecovery.domain.address.model.PooledAddress;

public final class AddressPoolFixtures {

    private AddressPoolFixtures() {}

    public static final String SOME_EVM_ADDRESS = "0xAb5801a7D398351b8bE11C439e05C5B3259aeC9B";
    public static final String SOME_CHAIN = "ethereum";
    public static final String SOME_SIGNER_ENDPOINT = "http://signer:8080";
    public static final UUID SOME_ADDRESS_ID = UUID.fromString("019576a0-e29b-7000-a716-446655440099");
    public static final Instant SOME_REGISTERED_AT = Instant.parse("2026-01-15T10:00:00Z");

    public static final RegisterAddressRequest SOME_REGISTER_REQUEST = RegisterAddressRequest.builder()
            .address(SOME_EVM_ADDRESS)
            .chain(SOME_CHAIN)
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

    public static final PooledAddress SOME_RETIRED_ADDRESS = SOME_REGISTERED_ADDRESS.toBuilder()
            .status(AddressStatus.RETIRED)
            .inFlightCount(0)
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
            .registeredAt(SOME_REGISTERED_AT)
            .build();

    public static final AddressResponse SOME_DRAINING_ADDRESS_RESPONSE = AddressResponse.builder()
            .id(SOME_ADDRESS_ID.toString())
            .address(SOME_EVM_ADDRESS)
            .chain(SOME_CHAIN)
            .chainFamily("EVM")
            .tier("HOT")
            .status("DRAINING")
            .currentNonce(42)
            .inFlightCount(2)
            .registeredAt(SOME_REGISTERED_AT)
            .build();

    public static final DrainResult SOME_DRAIN_RESULT = DrainResult.builder()
            .previousStatus(AddressStatus.ACTIVE)
            .address(SOME_DRAINING_ADDRESS)
            .build();

    public static final DrainResponse SOME_DRAIN_RESPONSE = DrainResponse.builder()
            .address(SOME_EVM_ADDRESS)
            .chain(SOME_CHAIN)
            .previousStatus("ACTIVE")
            .newStatus("DRAINING")
            .build();

    public static final PooledAddress SOME_SYNCED_ADDRESS = SOME_REGISTERED_ADDRESS.toBuilder()
            .currentNonce(100)
            .build();

    public static final NonceSyncResult SOME_NONCE_SYNC_RESULT = NonceSyncResult.builder()
            .previousNonce(42)
            .currentNonce(100)
            .address(SOME_SYNCED_ADDRESS)
            .build();

    public static final NonceSyncResponse SOME_NONCE_SYNC_RESPONSE = NonceSyncResponse.builder()
            .address(SOME_EVM_ADDRESS)
            .chain(SOME_CHAIN)
            .previousNonce(42)
            .currentNonce(100)
            .build();
}
