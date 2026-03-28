package com.stablebridge.txrecovery.domain.transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import com.github.f4b6a3.uuid.UuidCreator;
import com.stablebridge.txrecovery.domain.exception.DuplicateIntentException;
import com.stablebridge.txrecovery.domain.exception.TransactionNotFoundException;
import com.stablebridge.txrecovery.domain.transaction.model.SubmissionStrategy;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionFilters;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionIntent;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionProjection;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionStatus;
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

    public TransactionProjection submitTransaction(TransactionIntent intent) {
        var effectiveIntentId = intent.intentId() != null
                ? intent.intentId()
                : UuidCreator.getTimeOrderedEpoch().toString();

        var intentWithId = intent.toBuilder()
                .intentId(effectiveIntentId)
                .build();

        checkIdempotency(intentWithId.intentId());

        var transactionId = UuidCreator.getTimeOrderedEpoch().toString();
        var strategy = intentWithId.strategy() != null
                ? intentWithId.strategy()
                : calculateStrategy(intentWithId.amount());

        var enrichedIntent = intentWithId.toBuilder()
                .strategy(strategy)
                .createdAt(Instant.now())
                .build();

        transactionIntentStore.save(enrichedIntent);

        var projection = TransactionProjection.builder()
                .transactionId(transactionId)
                .intentId(enrichedIntent.intentId())
                .chain(enrichedIntent.chain())
                .status(TransactionStatus.RECEIVED)
                .toAddress(enrichedIntent.toAddress())
                .amount(enrichedIntent.amount())
                .token(enrichedIntent.token())
                .retryCount(0)
                .submittedAt(Instant.now())
                .build();

        transactionProjectionStore.save(projection);
        transactionWorkflowStarter.startWorkflow(enrichedIntent);

        log.info("Transaction submitted: transactionId={}, intentId={}, strategy={}",
                transactionId, enrichedIntent.intentId(), strategy);

        return projection;
    }

    public List<TransactionProjection> submitBatch(List<TransactionIntent> intents, String batchId) {
        return intents.stream()
                .map(intent -> intent.toBuilder()
                        .batchId(batchId)
                        .strategy(SubmissionStrategy.PIPELINED)
                        .build())
                .map(this::submitTransaction)
                .toList();
    }

    public TransactionProjection findById(String transactionId) {
        return transactionProjectionStore.findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));
    }

    public List<TransactionProjection> findByFilters(TransactionFilters filters) {
        return transactionProjectionStore.findByFilters(filters);
    }

    private void checkIdempotency(String intentId) {
        if (intentId == null) {
            return;
        }
        transactionIntentStore.findByIntentId(intentId)
                .ifPresent(_ -> transactionProjectionStore.findByIntentId(intentId)
                        .ifPresent(projection -> {
                            throw new DuplicateIntentException(projection.transactionId());
                        }));
    }

    private SubmissionStrategy calculateStrategy(BigDecimal amount) {
        return amount.compareTo(HIGH_VALUE_THRESHOLD) > 0
                ? SubmissionStrategy.SEQUENTIAL
                : SubmissionStrategy.PIPELINED;
    }
}
