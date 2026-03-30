package com.stablebridge.txrecovery.domain.address;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stablebridge.txrecovery.domain.address.model.AddressStatus;
import com.stablebridge.txrecovery.domain.address.model.AddressTier;
import com.stablebridge.txrecovery.domain.address.model.ChainFamily;
import com.stablebridge.txrecovery.domain.address.model.NonceSyncResult;
import com.stablebridge.txrecovery.domain.address.model.PooledAddress;
import com.stablebridge.txrecovery.domain.address.port.AddressPoolRepository;
import com.stablebridge.txrecovery.domain.address.port.ChainFamilyResolver;
import com.stablebridge.txrecovery.domain.address.port.OnChainNonceProvider;
import com.stablebridge.txrecovery.domain.exception.AddressNotFoundException;
import com.stablebridge.txrecovery.domain.exception.DuplicateAddressException;
import com.stablebridge.txrecovery.domain.exception.InvalidAddressStateException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AddressPoolService {

    private final AddressPoolRepository addressPoolRepository;
    private final OnChainNonceProvider onChainNonceProvider;
    private final ChainFamilyResolver chainFamilyResolver;
    private final Clock clock;

    @Transactional
    public PooledAddress register(String address, String chain, AddressTier tier, String signerEndpoint) {
        addressPoolRepository.findByAddressAndChain(address, chain)
                .ifPresent(_ -> {
                    throw new DuplicateAddressException(address, chain);
                });

        var chainFamily = chainFamilyResolver.resolve(chain);
        var initialNonce = resolveInitialNonce(address, chain, chainFamily);

        var pooledAddress = PooledAddress.builder()
                .id(UUID.randomUUID())
                .address(address)
                .chain(chain)
                .chainFamily(chainFamily)
                .tier(tier)
                .status(AddressStatus.ACTIVE)
                .currentNonce(initialNonce)
                .inFlightCount(0)
                .signerEndpoint(signerEndpoint)
                .registeredAt(Instant.now(clock))
                .build();

        return addressPoolRepository.save(pooledAddress);
    }

    @Transactional(readOnly = true)
    public List<PooledAddress> list(String chain, AddressTier tier, AddressStatus status) {
        return addressPoolRepository.findByFilters(chain, tier, status);
    }

    @Transactional
    public PooledAddress drain(String address, String chain) {
        var pooledAddress = addressPoolRepository.findByAddressAndChain(address, chain)
                .orElseThrow(() -> new AddressNotFoundException(address, chain));

        if (pooledAddress.status() != AddressStatus.ACTIVE) {
            throw new InvalidAddressStateException(address, chain, pooledAddress.status(), AddressStatus.DRAINING);
        }

        var newStatus = pooledAddress.inFlightCount() == 0 ? AddressStatus.RETIRED : AddressStatus.DRAINING;
        var draining = pooledAddress.toBuilder()
                .status(newStatus)
                .retiredAt(newStatus == AddressStatus.RETIRED ? Instant.now(clock) : null)
                .build();

        return addressPoolRepository.save(draining);
    }

    @Transactional
    public NonceSyncResult syncNonce(String address, String chain) {
        var pooledAddress = addressPoolRepository.findByAddressAndChain(address, chain)
                .orElseThrow(() -> new AddressNotFoundException(address, chain));

        var previousNonce = pooledAddress.currentNonce();
        var onChainNonce = onChainNonceProvider.getTransactionCount(address, chain);

        var synced = pooledAddress.toBuilder()
                .currentNonce(onChainNonce.longValueExact())
                .build();

        var saved = addressPoolRepository.save(synced);
        return new NonceSyncResult(previousNonce, saved.currentNonce(), saved);
    }

    private long resolveInitialNonce(String address, String chain, ChainFamily chainFamily) {
        if (chainFamily == ChainFamily.EVM) {
            var count = onChainNonceProvider.getTransactionCount(address, chain);
            log.info("Fetched on-chain nonce for address={} chain={} nonce={}", address, chain, count);
            return count.longValueExact();
        }
        return 0;
    }
}
