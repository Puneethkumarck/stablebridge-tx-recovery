package com.stablebridge.txrecovery.domain.address;

import static com.stablebridge.txrecovery.domain.address.model.AddressStatus.ACTIVE;
import static com.stablebridge.txrecovery.domain.address.model.AddressStatus.DRAINING;
import static com.stablebridge.txrecovery.domain.address.model.AddressStatus.RETIRED;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stablebridge.txrecovery.domain.address.model.AddressStatus;
import com.stablebridge.txrecovery.domain.address.model.AddressTier;
import com.stablebridge.txrecovery.domain.address.model.ChainFamily;
import com.stablebridge.txrecovery.domain.address.model.DrainResult;
import com.stablebridge.txrecovery.domain.address.model.NonceSyncResult;
import com.stablebridge.txrecovery.domain.address.model.PooledAddress;
import com.stablebridge.txrecovery.domain.address.port.AddressPoolRepository;
import com.stablebridge.txrecovery.domain.address.port.ChainFamilyResolver;
import com.stablebridge.txrecovery.domain.address.port.OnChainNonceProvider;
import com.stablebridge.txrecovery.domain.common.model.StateMachine;
import com.stablebridge.txrecovery.domain.exception.AddressNotFoundException;
import com.stablebridge.txrecovery.domain.exception.AddressStateMachineException;
import com.stablebridge.txrecovery.domain.exception.DuplicateAddressException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AddressPoolService {

    private static final StateMachine<AddressStatus, PooledAddress> ADDRESS_STATE_MACHINE =
            StateMachine.<AddressStatus, PooledAddress>builder()
                    .withExceptionProvider(AddressStateMachineException::new)
                    .withTransition(ACTIVE, DRAINING, StateMachine.noAction())
                    .withTransition(ACTIVE, RETIRED, StateMachine.noAction())
                    .withTransition(DRAINING, RETIRED, StateMachine.noAction())
                    .build();

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
                .address(address)
                .chain(chain)
                .chainFamily(chainFamily)
                .tier(tier)
                .status(ACTIVE)
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
    public DrainResult drain(String address, String chain) {
        var pooledAddress = addressPoolRepository.findByAddressAndChain(address, chain)
                .orElseThrow(() -> new AddressNotFoundException(address, chain));

        var targetStatus = pooledAddress.inFlightCount() > 0 ? DRAINING : RETIRED;
        ADDRESS_STATE_MACHINE.transition(pooledAddress, targetStatus);

        var previousStatus = pooledAddress.status();
        var updated = pooledAddress.toBuilder()
                .status(targetStatus)
                .retiredAt(targetStatus == RETIRED ? Instant.now(clock) : null)
                .build();

        var saved = addressPoolRepository.save(updated);
        return new DrainResult(previousStatus, saved);
    }

    @Transactional
    public PooledAddress decrementInFlightCount(String address, String chain) {
        addressPoolRepository.decrementInFlightCount(address, chain);

        return addressPoolRepository.findByAddressAndChain(address, chain)
                .orElseThrow(() -> new AddressNotFoundException(address, chain));
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
