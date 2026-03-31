package com.stablebridge.txrecovery.domain.transaction;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.github.f4b6a3.uuid.UuidCreator;
import com.stablebridge.txrecovery.domain.exception.BatchValidationException;
import com.stablebridge.txrecovery.domain.exception.DuplicateIntentException;
import com.stablebridge.txrecovery.domain.exception.TransactionNotFoundException;
import com.stablebridge.txrecovery.domain.transaction.model.BatchSubmissionResult;
import com.stablebridge.txrecovery.domain.transaction.model.PagedResult;
import com.stablebridge.txrecovery.domain.transaction.model.SubmissionResult;
import com.stablebridge.txrecovery.domain.transaction.model.SubmissionStrategy;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionFilters;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionIntent;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionProjection;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionStatus;
import com.stablebridge.txrecovery.domain.transaction.port.PostCommitAction;
import com.stablebridge.txrecovery.domain.transaction.port.TransactionIntentStore;
import com.stablebridge.txrecovery.domain.transaction.port.TransactionProjectionStore;
import com.stablebridge.txrecovery.domain.transaction.port.TransactionWorkflowStarter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionSubmissionService {

    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("10000");

    private final TransactionIntentStore transactionIntentStore;
    private final TransactionProjectionStore transactionProjectionStore;
    private final TransactionWorkflowStarter transactionWorkflowStarter;
    private final PostCommitAction postCommitAction;
    private final Clock clock;

    @Transactional
    public SubmissionResult submitTransaction(TransactionIntent intent) {
        var existing = transactionIntentStore.findByIntentId(intent.intentId());
        if (existing.isPresent()) {
            var projection = transactionProjectionStore.findByIntentId(intent.intentId())
                    .orElseThrow();
            return new SubmissionResult.AlreadyExists(projection);
        }

        var enrichedIntent = enrichIntent(intent);
        var projection = persistTransaction(enrichedIntent);

        postCommitAction.executeAfterCommit(() -> transactionWorkflowStarter.startWorkflow(enrichedIntent));

        return new SubmissionResult.Created(projection);
    }

    @Transactional
    public BatchSubmissionResult submitBatch(List<TransactionIntent> intents) {
        var batchId = UuidCreator.getTimeOrderedEpoch().toString();
        validateBatchConsistency(intents);

        var projections = intents.stream()
                .map(intent -> intent.toBuilder()
                        .batchId(batchId)
                        .strategy(SubmissionStrategy.PIPELINED)
                        .build())
                .map(this::createBatchTransaction)
                .toList();

        return new BatchSubmissionResult(batchId, projections, Instant.now(clock));
    }

    @Transactional(readOnly = true)
    public TransactionProjection findById(String transactionId) {
        return transactionProjectionStore.findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));
    }

    @Transactional(readOnly = true)
    public PagedResult<TransactionProjection> findByFilters(TransactionFilters filters, int page, int size) {
        return transactionProjectionStore.findByFilters(filters, page, size);
    }

    SubmissionStrategy calculateStrategy(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount must not be null");
        return amount.compareTo(HIGH_VALUE_THRESHOLD) > 0
                ? SubmissionStrategy.SEQUENTIAL
                : SubmissionStrategy.PIPELINED;
    }

    private TransactionIntent enrichIntent(TransactionIntent intent) {
        var strategy = intent.strategy() != null
                ? intent.strategy()
                : calculateStrategy(intent.amount());
        return intent.toBuilder()
                .strategy(strategy)
                .createdAt(Instant.now(clock))
                .build();
    }

    private TransactionProjection persistTransaction(TransactionIntent enrichedIntent) {
        transactionIntentStore.save(enrichedIntent);

        var transactionId = UuidCreator.getTimeOrderedEpoch().toString();
        var projection = TransactionProjection.builder()
                .transactionId(transactionId)
                .intentId(enrichedIntent.intentId())
                .chain(enrichedIntent.chain())
                .status(TransactionStatus.RECEIVED)
                .toAddress(enrichedIntent.toAddress())
                .amount(enrichedIntent.amount())
                .token(enrichedIntent.token())
                .retryCount(0)
                .submittedAt(Instant.now(clock))
                .build();

        transactionProjectionStore.save(projection);

        log.info("Transaction submitted: transactionId={}, intentId={}, strategy={}",
                transactionId, enrichedIntent.intentId(), enrichedIntent.strategy());

        return projection;
    }

    private TransactionProjection createBatchTransaction(TransactionIntent intent) {
        checkIdempotency(intent.intentId());
        var enrichedIntent = enrichIntent(intent);
        return persistTransaction(enrichedIntent);
    }

    private void checkIdempotency(String intentId) {
        transactionIntentStore.findByIntentId(intentId)
                .ifPresent(_ -> {
                    throw lookupDuplicate(intentId);
                });
    }

    private DuplicateIntentException lookupDuplicate(String intentId) {
        return transactionProjectionStore.findByIntentId(intentId)
                .map(projection -> new DuplicateIntentException(projection.transactionId()))
                .orElseGet(() -> new DuplicateIntentException(intentId));
    }

    private void validateBatchConsistency(List<TransactionIntent> intents) {
        var firstChain = intents.getFirst().chain();
        var firstToken = intents.getFirst().token();

        var allConsistent = intents.stream()
                .allMatch(tx -> firstChain.equals(tx.chain()) && firstToken.equals(tx.token()));

        if (!allConsistent) {
            throw new BatchValidationException("all transactions in a batch must have the same chain and token");
        }
    }
}
