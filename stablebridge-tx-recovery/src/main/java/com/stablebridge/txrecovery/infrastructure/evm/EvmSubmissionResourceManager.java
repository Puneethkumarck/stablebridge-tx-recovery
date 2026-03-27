package com.stablebridge.txrecovery.infrastructure.evm;

import com.stablebridge.txrecovery.domain.address.model.AddressStatus;
import com.stablebridge.txrecovery.domain.address.model.AddressTier;
import com.stablebridge.txrecovery.domain.address.port.AddressPoolRepository;
import com.stablebridge.txrecovery.domain.address.port.NonceManager;
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

    private static final int DEFAULT_MAX_PIPELINE_DEPTH = 20;

    private final AddressPoolRepository addressPoolRepository;
    private final NonceManager nonceManager;
    private final int maxPipelineDepth;

    public EvmSubmissionResourceManager(
            AddressPoolRepository addressPoolRepository,
            NonceManager nonceManager) {
        this(addressPoolRepository, nonceManager, DEFAULT_MAX_PIPELINE_DEPTH);
    }

    @Override
    public SubmissionResource acquire(TransactionIntent intent) {
        var chain = intent.chain();
        var tier = resolveTier(intent.strategy());

        var candidate = addressPoolRepository.findBestCandidate(
                        chain, tier, AddressStatus.ACTIVE, maxPipelineDepth)
                .orElseThrow(() -> {
                    log.error("Pool exhausted for chain={} tier={}", chain, tier);
                    return new NoAvailableAddressException(chain, tier.name());
                });

        addressPoolRepository.incrementInFlightCount(candidate.address(), chain);

        var allocation = nonceManager.allocate(candidate.address(), chain);

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
        var allocation = com.stablebridge.txrecovery.domain.address.model.NonceAllocation.builder()
                .address(evmResource.fromAddress())
                .chain(evmResource.chain())
                .nonce(evmResource.nonce())
                .build();

        nonceManager.release(allocation);
        addressPoolRepository.decrementInFlightCount(evmResource.fromAddress(), evmResource.chain());

        log.info("Released resource: chain={} address={} nonce={}",
                evmResource.chain(), evmResource.fromAddress(), evmResource.nonce());
    }

    @Override
    public void consume(SubmissionResource resource) {
        var evmResource = (EvmSubmissionResource) resource;
        var allocation = com.stablebridge.txrecovery.domain.address.model.NonceAllocation.builder()
                .address(evmResource.fromAddress())
                .chain(evmResource.chain())
                .nonce(evmResource.nonce())
                .build();

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
}
