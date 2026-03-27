package com.stablebridge.txrecovery.infrastructure.evm;

import com.stablebridge.txrecovery.domain.address.model.AddressStatus;
import com.stablebridge.txrecovery.domain.address.model.AddressTier;
import com.stablebridge.txrecovery.domain.address.model.NonceAllocation;
import com.stablebridge.txrecovery.domain.address.port.AddressPoolRepository;
import com.stablebridge.txrecovery.domain.address.port.NonceManager;
import com.stablebridge.txrecovery.domain.address.port.PoolExhaustedAlertPublisher;
import com.stablebridge.txrecovery.domain.exception.NoAvailableAddressException;
import com.stablebridge.txrecovery.domain.transaction.model.EvmSubmissionResource;
import com.stablebridge.txrecovery.domain.transaction.model.SubmissionResource;
import com.stablebridge.txrecovery.domain.transaction.model.SubmissionStrategy;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionIntent;
import com.stablebridge.txrecovery.domain.transaction.port.SubmissionResourceManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class EvmSubmissionResourceManager implements SubmissionResourceManager {

    private final AddressPoolRepository addressPoolRepository;
    private final NonceManager nonceManager;
    private final PoolExhaustedAlertPublisher poolExhaustedAlertPublisher;
    private final int maxPipelineDepth;

    @Override
    public SubmissionResource acquire(TransactionIntent intent) {
        var chain = intent.chain();
        var tier = resolveTier(intent.strategy());

        var candidate = addressPoolRepository.findBestCandidate(
                        chain, tier, AddressStatus.ACTIVE, maxPipelineDepth)
                .orElseThrow(() -> {
                    poolExhaustedAlertPublisher.publish(chain, tier.name());
                    return new NoAvailableAddressException(chain, tier.name());
                });

        addressPoolRepository.incrementInFlightCount(candidate.address(), chain);

        NonceAllocation allocation;
        try {
            allocation = nonceManager.allocate(candidate.address(), chain);
        } catch (RuntimeException ex) {
            addressPoolRepository.decrementInFlightCount(candidate.address(), chain);
            throw ex;
        }

        log.info("Acquired resource: chain={} address={} nonce={} tier={}",
                chain, candidate.address(), allocation.nonce(), tier);

        return EvmSubmissionResource.builder()
                .chain(chain)
                .fromAddress(candidate.address())
                .nonce(allocation.nonce())
                .tier(tier)
                .build();
    }

    @Override
    public void release(SubmissionResource resource) {
        var evmResource = (EvmSubmissionResource) resource;
        var allocation = toNonceAllocation(evmResource);

        nonceManager.release(allocation);
        addressPoolRepository.decrementInFlightCount(evmResource.fromAddress(), evmResource.chain());

        log.info("Released resource: chain={} address={} nonce={}",
                evmResource.chain(), evmResource.fromAddress(), evmResource.nonce());
    }

    @Override
    public void consume(SubmissionResource resource) {
        var evmResource = (EvmSubmissionResource) resource;
        var allocation = toNonceAllocation(evmResource);

        nonceManager.confirm(allocation);
        addressPoolRepository.decrementInFlightCount(evmResource.fromAddress(), evmResource.chain());

        log.info("Consumed resource: chain={} address={} nonce={}",
                evmResource.chain(), evmResource.fromAddress(), evmResource.nonce());
    }

    static AddressTier resolveTier(SubmissionStrategy strategy) {
        return switch (strategy) {
            case SEQUENTIAL -> AddressTier.PRIORITY;
            case PIPELINED -> AddressTier.HOT;
        };
    }

    private static NonceAllocation toNonceAllocation(EvmSubmissionResource resource) {
        return NonceAllocation.builder()
                .address(resource.fromAddress())
                .chain(resource.chain())
                .nonce(resource.nonce())
                .build();
    }
}
