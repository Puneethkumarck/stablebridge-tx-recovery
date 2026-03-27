package com.stablebridge.txrecovery.infrastructure.solana;

import org.springframework.transaction.annotation.Transactional;

import com.stablebridge.txrecovery.domain.address.port.NonceAccountPoolRepository;
import com.stablebridge.txrecovery.domain.address.port.PoolExhaustedAlertPublisher;
import com.stablebridge.txrecovery.domain.exception.NoAvailableAddressException;
import com.stablebridge.txrecovery.domain.transaction.model.SolanaSubmissionResource;
import com.stablebridge.txrecovery.domain.transaction.model.SubmissionResource;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionIntent;
import com.stablebridge.txrecovery.domain.transaction.port.SubmissionResourceManager;
import com.stablebridge.txrecovery.infrastructure.client.solana.SolanaCommitment;
import com.stablebridge.txrecovery.infrastructure.client.solana.SolanaRpcClient;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SolanaSubmissionResourceManager implements SubmissionResourceManager {

    private static final String NONCE_ACCOUNT = "NONCE_ACCOUNT";

    private final NonceAccountPoolRepository nonceAccountPoolRepository;
    private final SolanaRpcClient rpcClient;
    private final PoolExhaustedAlertPublisher poolExhaustedAlertPublisher;
    private final int minAvailable;

    public SolanaSubmissionResourceManager(
            NonceAccountPoolRepository nonceAccountPoolRepository,
            SolanaRpcClient rpcClient,
            PoolExhaustedAlertPublisher poolExhaustedAlertPublisher,
            int minAvailable) {
        if (minAvailable <= 0) {
            throw new IllegalArgumentException("minAvailable must be positive, got: " + minAvailable);
        }
        this.nonceAccountPoolRepository = nonceAccountPoolRepository;
        this.rpcClient = rpcClient;
        this.poolExhaustedAlertPublisher = poolExhaustedAlertPublisher;
        this.minAvailable = minAvailable;
    }

    @Override
    @Transactional
    public SubmissionResource acquire(TransactionIntent intent) {
        var chain = intent.chain();

        var nonceAccount = nonceAccountPoolRepository.findAvailableByChain(chain)
                .orElseThrow(() -> {
                    poolExhaustedAlertPublisher.publish(chain, NONCE_ACCOUNT);
                    return new NoAvailableAddressException(chain, NONCE_ACCOUNT);
                });

        nonceAccountPoolRepository.markInUse(
                nonceAccount.nonceAccountAddress(), chain, intent.intentId());

        String nonceValue;
        try {
            nonceValue = rpcClient.getNonce(
                    nonceAccount.nonceAccountAddress(), SolanaCommitment.CONFIRMED);
        } catch (RuntimeException ex) {
            try {
                nonceAccountPoolRepository.markAvailable(nonceAccount.nonceAccountAddress(), chain);
            } catch (RuntimeException rollbackEx) {
                log.error("Failed to rollback nonce account to AVAILABLE: address={} chain={}",
                        nonceAccount.nonceAccountAddress(), chain, rollbackEx);
            }
            throw ex;
        }

        var availableCount = nonceAccountPoolRepository.countAvailableByChain(chain);
        if (availableCount < minAvailable) {
            poolExhaustedAlertPublisher.publish(chain, NONCE_ACCOUNT);
        }

        log.info("Acquired nonce account: chain={} authority={} nonceAccount={} nonce={}",
                chain, nonceAccount.authorityAddress(), nonceAccount.nonceAccountAddress(), nonceValue);

        return SolanaSubmissionResource.builder()
                .chain(chain)
                .fromAddress(nonceAccount.authorityAddress())
                .nonceAccountAddress(nonceAccount.nonceAccountAddress())
                .nonceValue(nonceValue)
                .build();
    }

    @Override
    public void release(SubmissionResource resource) {
        if (!(resource instanceof SolanaSubmissionResource solanaResource)) {
            throw new IllegalArgumentException(
                    "Expected SolanaSubmissionResource, got " + resource.getClass().getSimpleName());
        }

        nonceAccountPoolRepository.markAvailable(
                solanaResource.nonceAccountAddress(), solanaResource.chain());

        log.info("Released nonce account: chain={} nonceAccount={}",
                solanaResource.chain(), solanaResource.nonceAccountAddress());
    }

    @Override
    public void consume(SubmissionResource resource) {
        if (!(resource instanceof SolanaSubmissionResource solanaResource)) {
            throw new IllegalArgumentException(
                    "Expected SolanaSubmissionResource, got " + resource.getClass().getSimpleName());
        }

        var newNonceValue = rpcClient.getNonce(
                solanaResource.nonceAccountAddress(), SolanaCommitment.CONFIRMED);

        nonceAccountPoolRepository.consumeAndRelease(
                solanaResource.nonceAccountAddress(), solanaResource.chain(), newNonceValue);

        log.info("Consumed nonce account: chain={} nonceAccount={} newNonce={}",
                solanaResource.chain(), solanaResource.nonceAccountAddress(), newNonceValue);
    }
}
