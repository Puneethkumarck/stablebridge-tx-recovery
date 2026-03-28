package com.stablebridge.txrecovery.application.config;

import static com.stablebridge.txrecovery.testutil.TestUtils.eqIgnoring;
import static com.stablebridge.txrecovery.testutil.fixtures.TransactionIntentFixtures.*;
import static com.stablebridge.txrecovery.testutil.fixtures.WorkflowTestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.math.BigDecimal;
import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.stablebridge.txrecovery.application.workflow.TransactionLifecycleActivities;
import com.stablebridge.txrecovery.application.workflow.TransactionLifecycleWorkflow;
import com.stablebridge.txrecovery.application.workflow.TransactionLifecycleWorkflowImpl;
import com.stablebridge.txrecovery.domain.recovery.model.FeeEstimate;
import com.stablebridge.txrecovery.domain.recovery.model.FeeUrgency;
import com.stablebridge.txrecovery.domain.recovery.model.RecoveryPlan;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionStatus;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.common.converter.DataConverter;
import io.temporal.common.converter.DefaultDataConverter;
import io.temporal.common.converter.JacksonJsonPayloadConverter;
import io.temporal.testing.TestWorkflowEnvironment;

@ExtendWith(MockitoExtension.class)
class TemporalWorkerIntegrationTest {

    private static final String TASK_QUEUE = "str-transaction-lifecycle-test";

    private TestWorkflowEnvironment testEnv;
    private WorkflowClient client;

    @Mock
    private TransactionLifecycleActivities activities;

    @BeforeEach
    void setUp() {
        testEnv = TestWorkflowEnvironment.newInstance();
        var worker = testEnv.newWorker(TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(TransactionLifecycleWorkflowImpl.class);
        worker.registerActivitiesImplementations(activities);
        client = testEnv.getWorkflowClient();
        testEnv.start();
    }

    @AfterEach
    void tearDown() {
        testEnv.close();
    }

    @Nested
    class WorkerRegistration {

        @Test
        void shouldStartWorkerAndExecuteWorkflow() {
            // given
            var resource = someEvmResource();
            var unsigned = someUnsignedTransaction();
            var signed = someSignedTransaction();
            var broadcastResult = someBroadcastResult();
            var confirmation = someFinalizedConfirmation();

            given(activities.acquireResource(
                    eqIgnoring(SOME_SEQUENTIAL_INTENT, "amount", "rawAmount")))
                    .willReturn(resource);
            given(activities.build(
                    eqIgnoring(SOME_SEQUENTIAL_INTENT, "amount", "rawAmount"),
                    eqIgnoring(resource)))
                    .willReturn(unsigned);
            given(activities.sign(
                    eqIgnoring(unsigned, "payload"),
                    eqIgnoring(SOME_FROM_ADDRESS)))
                    .willReturn(signed);
            given(activities.broadcast(
                    eqIgnoring(signed, "signedPayload"),
                    eqIgnoring(SOME_CHAIN)))
                    .willReturn(broadcastResult);
            given(activities.checkStatus(SOME_TX_HASH, SOME_CHAIN))
                    .willReturn(TransactionStatus.CONFIRMED);
            given(activities.waitForFinality(SOME_TX_HASH, SOME_CHAIN))
                    .willReturn(confirmation);

            // when
            var workflow = startWorkflow(SOME_SEQUENTIAL_INTENT.intentId());
            var result = workflow.process(SOME_SEQUENTIAL_INTENT);

            // then
            assertThat(result.finalStatus()).isEqualTo(TransactionStatus.FINALIZED);
            assertThat(result.intentId()).isEqualTo(SOME_INTENT_ID);
        }
    }

    @Nested
    class DataConverterSerialization {

        private final DataConverter converter = createTestDataConverter();

        @Test
        void shouldSerializeAndDeserializeSpeedUpPlan() {
            // given
            var plan = RecoveryPlan.SpeedUp.builder()
                    .originalTxHash("0xabc123")
                    .newFee(FeeEstimate.builder()
                            .maxFeePerGas(new BigDecimal("30"))
                            .maxPriorityFeePerGas(new BigDecimal("2"))
                            .estimatedCost(new BigDecimal("0.002"))
                            .denomination("ETH")
                            .urgency(FeeUrgency.FAST)
                            .build())
                    .build();

            // when
            var payload = converter.toPayload(plan).orElseThrow();
            var deserialized = converter.fromPayload(
                    payload, RecoveryPlan.class, RecoveryPlan.class);

            // then
            assertThat(deserialized)
                    .usingRecursiveComparison()
                    .isEqualTo(plan);
        }

        @Test
        void shouldSerializeAndDeserializeCancelPlan() {
            // given
            var plan = RecoveryPlan.Cancel.builder()
                    .originalTxHash("0xdef456")
                    .build();

            // when
            var payload = converter.toPayload(plan).orElseThrow();
            var deserialized = converter.fromPayload(
                    payload, RecoveryPlan.class, RecoveryPlan.class);

            // then
            assertThat(deserialized)
                    .usingRecursiveComparison()
                    .isEqualTo(plan);
        }

        @Test
        void shouldSerializeAndDeserializeResubmitPlan() {
            // given
            var plan = RecoveryPlan.Resubmit.builder()
                    .originalTxHash("0x789ghi")
                    .build();

            // when
            var payload = converter.toPayload(plan).orElseThrow();
            var deserialized = converter.fromPayload(
                    payload, RecoveryPlan.class, RecoveryPlan.class);

            // then
            assertThat(deserialized)
                    .usingRecursiveComparison()
                    .isEqualTo(plan);
        }

        @Test
        void shouldSerializeAndDeserializeWaitPlan() {
            // given
            var plan = RecoveryPlan.Wait.builder()
                    .estimatedClearance(Duration.ofMinutes(5))
                    .reason("mempool congestion")
                    .build();

            // when
            var payload = converter.toPayload(plan).orElseThrow();
            var deserialized = converter.fromPayload(
                    payload, RecoveryPlan.class, RecoveryPlan.class);

            // then
            assertThat(deserialized)
                    .usingRecursiveComparison()
                    .isEqualTo(plan);
        }
    }

    private static DataConverter createTestDataConverter() {
        var objectMapper = new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return DefaultDataConverter.newDefaultInstance()
                .withPayloadConverterOverrides(
                        new JacksonJsonPayloadConverter(objectMapper));
    }

    private TransactionLifecycleWorkflow startWorkflow(String intentId) {
        var workflowId = TransactionLifecycleWorkflow.workflowId(intentId);
        return client.newWorkflowStub(
                TransactionLifecycleWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId(workflowId)
                        .setTaskQueue(TASK_QUEUE)
                        .build());
    }
}
