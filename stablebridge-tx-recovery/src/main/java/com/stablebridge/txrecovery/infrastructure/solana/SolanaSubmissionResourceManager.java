package com.stablebridge.txrecovery.infrastructure.solana;

import com.stablebridge.txrecovery.domain.address.model.AddressTier;
import com.stablebridge.txrecovery.domain.address.port.NonceAccountPoolRepository;
import com.stablebridge.txrecovery.domain.address.port.PoolExhaustedAlertPublisher;
import com.stablebridge.txrecovery.domain.exception.NoAvailableAddressException;
import com.stablebridge.txrecovery.domain.transaction.model.SolanaSubmissionResource;
import com.stablebridge.txrecovery.domain.transaction.model.SubmissionResource;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionIntent;
import com.stablebridge.txrecovery.domain.transaction.port.SubmissionResourceManager;
import com.stablebridge.txrecovery.infrastructure.client.solana.SolanaCommitment;
import com.stablebridge.txrecovery.infrastructure.client.solana.SolanaRpcClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class SolanaSubmissionResourceManager implements SubmissionResourceManager {

    private static final AddressTier NONCE_ACCOUNT_TIER = AddressTier.HOT;

    private final NonceAccountPoolRepository nonceAccountPoolRepository;
    private final SolanaRpcClient rpcClient;
    private final PoolExhaustedAlertPublisher poolExhaustedAlertPublisher;
    private final int minAvailable;

    @Override
    public SubmissionResource acquire(TransactionIntent intent) {
        var chain = intent.chain();

        var nonceAccount = nonceAccountPoolRepository.findAvailableAndMarkInUse(chain, intent.intentId())
                .orElseThrow(() -> {
                    poolExhaustedAlertPublisher.publish(chain, NONCE_ACCOUNT_TIER);
                    return new NoAvailableAddressException(chain, NONCE_ACCOUNT_TIER.name());
                });

        String nonceValue;
        try {
            nonceValue = rpcClient.getNonce(
                    nonceAccount.nonceAccountAddress(), SolanaCommitment.CONFIRMED);
        } catch (RuntimeException ex) {
            nonceAccountPoolRepository.markAvailable(nonceAccount.nonceAccountAddress(), chain);
            throw ex;
        }

        var availableCount = nonceAccountPoolRepository.countAvailableByChain(chain);
        if (availableCount < minAvailable) {
            poolExhaustedAlertPublisher.publish(chain, NONCE_ACCOUNT_TIER);
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
        var solanaResource = (SolanaSubmissionResource) resource;

        nonceAccountPoolRepository.markAvailable(
                solanaResource.nonceAccountAddress(), solanaResource.chain());

        log.info("Released nonce account: chain={} nonceAccount={}",
                solanaResource.chain(), solanaResource.nonceAccountAddress());
    }

    @Override
    public void consume(SubmissionResource resource) {
        var solanaResource = (SolanaSubmissionResource) resource;

        var newNonceValue = rpcClient.getNonce(
                solanaResource.nonceAccountAddress(), SolanaCommitment.CONFIRMED);

        nonceAccountPoolRepository.consumeAndRelease(
                solanaResource.nonceAccountAddress(), solanaResource.chain(), newNonceValue);

        log.info("Consumed nonce account: chain={} nonceAccount={} newNonce={}",
                solanaResource.chain(), solanaResource.nonceAccountAddress(), newNonceValue);
    }
}
