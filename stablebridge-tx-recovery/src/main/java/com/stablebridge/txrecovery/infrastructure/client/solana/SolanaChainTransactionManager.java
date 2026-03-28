package com.stablebridge.txrecovery.infrastructure.client.solana;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import com.stablebridge.txrecovery.domain.transaction.model.BroadcastResult;
import com.stablebridge.txrecovery.domain.transaction.model.SignedTransaction;
import com.stablebridge.txrecovery.domain.transaction.model.SolanaSubmissionResource;
import com.stablebridge.txrecovery.domain.transaction.model.SubmissionResource;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionIntent;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionStatus;
import com.stablebridge.txrecovery.domain.transaction.model.UnsignedTransaction;
import com.stablebridge.txrecovery.domain.transaction.port.ChainTransactionManager;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class SolanaChainTransactionManager implements ChainTransactionManager {

    private static final long PENDING_MAP_MAX_SIZE = 10_000;
    private static final long PENDING_MAP_EVICTION_SLOTS = 300_000L;

    private final SolanaRpcClient rpcClient;
    private final SolanaTransactionBuilder transactionBuilder;
    private final String chain;
    private final long stuckThresholdSeconds;
    private final Clock clock;
    private final ConcurrentHashMap<String, Instant> pendingFirstSeen = new ConcurrentHashMap<>();

    SolanaChainTransactionManager(
            SolanaRpcClient rpcClient,
            SolanaTransactionBuilder transactionBuilder,
            String chain,
            long stuckThresholdSeconds) {
        this(rpcClient, transactionBuilder, chain, stuckThresholdSeconds, Clock.systemUTC());
    }

    SolanaChainTransactionManager(
            SolanaRpcClient rpcClient,
            SolanaTransactionBuilder transactionBuilder,
            String chain,
            long stuckThresholdSeconds,
            Clock clock) {
        this.rpcClient = rpcClient;
        this.transactionBuilder = transactionBuilder;
        this.chain = chain;
        this.stuckThresholdSeconds = stuckThresholdSeconds;
        this.clock = clock;
    }

    @Override
    public UnsignedTransaction build(TransactionIntent intent, SubmissionResource resource) {
        if (!(resource instanceof SolanaSubmissionResource solanaResource)) {
            throw new SolanaRpcException(
                    -1, "Expected SolanaSubmissionResource but got " + resource.getClass().getSimpleName());
        }
        return transactionBuilder.build(intent, solanaResource);
    }

    @Override
    public BroadcastResult broadcast(SignedTransaction signedTransaction, String chain) {
        validateChain(chain);
        var signature = rpcClient.sendTransaction(signedTransaction.signedPayload());
        return BroadcastResult.builder()
                .txHash(signature)
                .chain(chain)
                .broadcastedAt(clock.instant())
                .build();
    }

    @Override
    public TransactionStatus checkStatus(String txHash, String chain) {
        validateChain(chain);
        var statuses = rpcClient.getSignatureStatuses(List.of(txHash));
        var status = statuses.isEmpty() ? null : statuses.getFirst();

        if (status == null) {
            return TransactionStatus.DROPPED;
        }

        if (status.hasError()) {
            return TransactionStatus.FAILED;
        }

        if (status.isFinalized()) {
            pendingFirstSeen.remove(txHash);
            return TransactionStatus.FINALIZED;
        }

        if (status.isConfirmedOrFinalized()) {
            pendingFirstSeen.remove(txHash);
            return TransactionStatus.CONFIRMED;
        }

        return classifyPendingTransaction(txHash);
    }

    private TransactionStatus classifyPendingTransaction(String txHash) {
        evictStaleEntries();
        var firstSeen = pendingFirstSeen.computeIfAbsent(txHash, _ -> clock.instant());
        var elapsed = Duration.between(firstSeen, clock.instant());

        if (elapsed.toSeconds() > stuckThresholdSeconds) {
            return TransactionStatus.STUCK;
        }

        return TransactionStatus.PENDING;
    }

    private void evictStaleEntries() {
        if (pendingFirstSeen.size() <= PENDING_MAP_MAX_SIZE) {
            return;
        }
        var cutoff = clock.instant().minusSeconds(PENDING_MAP_EVICTION_SLOTS);
        pendingFirstSeen.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
    }

    private void validateChain(String chain) {
        if (!this.chain.equals(chain)) {
            throw new SolanaRpcException(
                    -1, "Manager for chain %s cannot serve chain %s".formatted(this.chain, chain));
        }
    }
}
