package com.stablebridge.txrecovery.domain.transaction;

import static com.stablebridge.txrecovery.testutil.TestUtils.eqIgnoring;
import static com.stablebridge.txrecovery.testutil.fixtures.TransactionControllerFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stablebridge.txrecovery.domain.exception.BatchValidationException;
import com.stablebridge.txrecovery.domain.exception.DuplicateIntentException;
import com.stablebridge.txrecovery.domain.exception.TransactionNotFoundException;
import com.stablebridge.txrecovery.domain.transaction.model.PagedResult;
import com.stablebridge.txrecovery.domain.transaction.model.SubmissionStrategy;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionFilters;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionStatus;
import com.stablebridge.txrecovery.domain.transaction.port.TransactionIntentStore;
import com.stablebridge.txrecovery.domain.transaction.port.TransactionProjectionStore;
import com.stablebridge.txrecovery.domain.transaction.port.TransactionWorkflowStarter;

@ExtendWith(MockitoExtension.class)
class TransactionSubmissionServiceTest {

    @Mock
    private TransactionIntentStore transactionIntentStore;

    @Mock
    private TransactionProjectionStore transactionProjectionStore;

    @Mock
    private TransactionWorkflowStarter transactionWorkflowStarter;

    @InjectMocks
    private TransactionSubmissionService transactionSubmissionService;

    @Nested
    class SubmitTransaction {

        @Test
        void shouldSubmitTransactionWithReceivedStatus() {
            // given
            given(transactionIntentStore.findByIntentId(SOME_INTENT_ID))
                    .willReturn(Optional.empty());

            // when
            var result = transactionSubmissionService.submitTransaction(SOME_TRANSACTION_INTENT);

            // then
            assertThat(result.status()).isEqualTo(TransactionStatus.RECEIVED);
            assertThat(result.intentId()).isEqualTo(SOME_INTENT_ID);
            assertThat(result.chain()).isEqualTo(SOME_CHAIN);
            assertThat(result.token()).isEqualTo(SOME_TOKEN);
            assertThat(result.retryCount()).isZero();
            then(transactionIntentStore).should().save(eqIgnoring(SOME_TRANSACTION_INTENT.toBuilder()
                    .strategy(SubmissionStrategy.PIPELINED).build()));
        }

        @Test
        void shouldAssignSequentialStrategyForHighValueTransactions() {
            // given
            var highValueIntent = SOME_TRANSACTION_INTENT.toBuilder()
                    .amount(new BigDecimal("15000.00"))
                    .build();
            given(transactionIntentStore.findByIntentId(SOME_INTENT_ID))
                    .willReturn(Optional.empty());

            // when
            var result = transactionSubmissionService.submitTransaction(highValueIntent);

            // then
            assertThat(result.status()).isEqualTo(TransactionStatus.RECEIVED);
            then(transactionIntentStore).should().save(eqIgnoring(highValueIntent.toBuilder()
                    .strategy(SubmissionStrategy.SEQUENTIAL).build()));
        }

        @Test
        void shouldAssignPipelinedStrategyForRoutineTransactions() {
            // given
            given(transactionIntentStore.findByIntentId(SOME_INTENT_ID))
                    .willReturn(Optional.empty());

            // when
            transactionSubmissionService.submitTransaction(SOME_TRANSACTION_INTENT);

            // then
            then(transactionIntentStore).should().save(eqIgnoring(SOME_TRANSACTION_INTENT.toBuilder()
                    .strategy(SubmissionStrategy.PIPELINED).build()));
        }

        @Test
        void shouldThrowDuplicateIntentExceptionWhenIntentAlreadyExists() {
            // given
            given(transactionIntentStore.findByIntentId(SOME_INTENT_ID))
                    .willReturn(Optional.of(SOME_TRANSACTION_INTENT));
            given(transactionProjectionStore.findByIntentId(SOME_INTENT_ID))
                    .willReturn(Optional.of(SOME_TRANSACTION_PROJECTION));

            // when/then
            assertThatThrownBy(() -> transactionSubmissionService.submitTransaction(SOME_TRANSACTION_INTENT))
                    .isInstanceOf(DuplicateIntentException.class)
                    .hasMessageContaining(SOME_TRANSACTION_ID);
        }

        @Test
        void shouldPropagateDuplicateIntentExceptionFromAdapter() {
            // given
            given(transactionIntentStore.findByIntentId(SOME_INTENT_ID))
                    .willReturn(Optional.empty());
            org.mockito.BDDMockito.willThrow(new DuplicateIntentException(SOME_INTENT_ID))
                    .given(transactionIntentStore).save(eqIgnoring(SOME_TRANSACTION_INTENT.toBuilder()
                            .strategy(SubmissionStrategy.PIPELINED).build()));

            // when/then
            assertThatThrownBy(() -> transactionSubmissionService.submitTransaction(SOME_TRANSACTION_INTENT))
                    .isInstanceOf(DuplicateIntentException.class);
        }
    }

    @Nested
    class SubmitBatch {

        @Test
        void shouldSubmitAllIntentsInBatchWithPipelinedStrategy() {
            // given
            var secondIntent = SOME_TRANSACTION_INTENT.toBuilder()
                    .intentId(SOME_SECOND_INTENT_ID)
                    .build();
            var intents = List.of(SOME_TRANSACTION_INTENT, secondIntent);

            given(transactionIntentStore.findByIntentId(SOME_INTENT_ID))
                    .willReturn(Optional.empty());
            given(transactionIntentStore.findByIntentId(SOME_SECOND_INTENT_ID))
                    .willReturn(Optional.empty());

            // when
            var results = transactionSubmissionService.submitBatch(intents, "batch-001");

            // then
            assertThat(results).hasSize(2);
            assertThat(results).allMatch(p -> p.status() == TransactionStatus.RECEIVED);
        }

        @Test
        void shouldRejectBatchWithMixedChains() {
            // given
            var solanaIntent = SOME_TRANSACTION_INTENT.toBuilder()
                    .intentId(SOME_SECOND_INTENT_ID)
                    .chain("solana")
                    .build();
            var intents = List.of(SOME_TRANSACTION_INTENT, solanaIntent);

            // when/then
            assertThatThrownBy(() -> transactionSubmissionService.submitBatch(intents, "batch-001"))
                    .isInstanceOf(BatchValidationException.class)
                    .hasMessageContaining("same chain and token");
        }

        @Test
        void shouldRejectBatchWithMixedTokens() {
            // given
            var differentTokenIntent = SOME_TRANSACTION_INTENT.toBuilder()
                    .intentId(SOME_SECOND_INTENT_ID)
                    .token("USDT")
                    .build();
            var intents = List.of(SOME_TRANSACTION_INTENT, differentTokenIntent);

            // when/then
            assertThatThrownBy(() -> transactionSubmissionService.submitBatch(intents, "batch-001"))
                    .isInstanceOf(BatchValidationException.class)
                    .hasMessageContaining("same chain and token");
        }
    }

    @Nested
    class FindById {

        @Test
        void shouldReturnProjectionWhenFound() {
            // given
            given(transactionProjectionStore.findById(SOME_TRANSACTION_ID))
                    .willReturn(Optional.of(SOME_TRANSACTION_PROJECTION));

            // when
            var result = transactionSubmissionService.findById(SOME_TRANSACTION_ID);

            // then
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(SOME_TRANSACTION_PROJECTION);
        }

        @Test
        void shouldThrowWhenNotFound() {
            // given
            given(transactionProjectionStore.findById("non-existent"))
                    .willReturn(Optional.empty());

            // when/then
            assertThatThrownBy(() -> transactionSubmissionService.findById("non-existent"))
                    .isInstanceOf(TransactionNotFoundException.class)
                    .hasMessageContaining("non-existent");
        }
    }

    @Nested
    class FindByFilters {

        @Test
        void shouldDelegateToProjectionStore() {
            // given
            var filters = TransactionFilters.builder()
                    .chain(SOME_CHAIN)
                    .status(TransactionStatus.RECEIVED)
                    .build();
            var pagedResult = PagedResult.<com.stablebridge.txrecovery.domain.transaction.model.TransactionProjection>builder()
                    .content(List.of(SOME_TRANSACTION_PROJECTION))
                    .totalElements(1)
                    .totalPages(1)
                    .build();
            given(transactionProjectionStore.findByFilters(filters, 0, 20))
                    .willReturn(pagedResult);

            // when
            var result = transactionSubmissionService.findByFilters(filters, 0, 20);

            // then
            assertThat(result.content()).hasSize(1);
            assertThat(result.totalElements()).isEqualTo(1);
        }
    }

    @Nested
    class CalculateStrategy {

        @Test
        void shouldReturnSequentialForAmountAboveThreshold() {
            // when
            var result = transactionSubmissionService.calculateStrategy(new BigDecimal("10001"));

            // then
            assertThat(result).isEqualTo(SubmissionStrategy.SEQUENTIAL);
        }

        @Test
        void shouldReturnPipelinedForAmountAtThreshold() {
            // when
            var result = transactionSubmissionService.calculateStrategy(new BigDecimal("10000"));

            // then
            assertThat(result).isEqualTo(SubmissionStrategy.PIPELINED);
        }

        @Test
        void shouldReturnPipelinedForAmountBelowThreshold() {
            // when
            var result = transactionSubmissionService.calculateStrategy(new BigDecimal("9999.99"));

            // then
            assertThat(result).isEqualTo(SubmissionStrategy.PIPELINED);
        }
    }
}
