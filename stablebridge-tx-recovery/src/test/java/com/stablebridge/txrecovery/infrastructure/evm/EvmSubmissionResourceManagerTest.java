package com.stablebridge.txrecovery.infrastructure.evm;

import static com.stablebridge.txrecovery.testutil.fixtures.PooledAddressFixtures.SOME_ACTIVE_HOT_ADDRESS;
import static com.stablebridge.txrecovery.testutil.fixtures.PooledAddressFixtures.SOME_ACTIVE_PRIORITY_ADDRESS;
import static com.stablebridge.txrecovery.testutil.fixtures.TransactionIntentFixtures.SOME_PIPELINED_INTENT;
import static com.stablebridge.txrecovery.testutil.fixtures.TransactionIntentFixtures.SOME_SEQUENTIAL_INTENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stablebridge.txrecovery.domain.address.model.AddressStatus;
import com.stablebridge.txrecovery.domain.address.model.AddressTier;
import com.stablebridge.txrecovery.domain.address.model.NonceAllocation;
import com.stablebridge.txrecovery.domain.address.port.AddressPoolRepository;
import com.stablebridge.txrecovery.domain.address.port.NonceManager;
import com.stablebridge.txrecovery.domain.address.port.PoolExhaustedAlertPublisher;
import com.stablebridge.txrecovery.domain.exception.NoAvailableAddressException;
import com.stablebridge.txrecovery.domain.transaction.model.EvmSubmissionResource;
import com.stablebridge.txrecovery.domain.transaction.model.SubmissionStrategy;

@ExtendWith(MockitoExtension.class)
class EvmSubmissionResourceManagerTest {

    private static final int MAX_PIPELINE_DEPTH = 20;

    @Mock
    private AddressPoolRepository addressPoolRepository;

    @Mock
    private NonceManager nonceManager;

    @Mock
    private PoolExhaustedAlertPublisher poolExhaustedAlertPublisher;

    private EvmSubmissionResourceManager resourceManager;

    @BeforeEach
    void setUp() {
        resourceManager = new EvmSubmissionResourceManager(
                addressPoolRepository, nonceManager, poolExhaustedAlertPublisher, MAX_PIPELINE_DEPTH);
    }

    @Nested
    class Acquire {

        @Test
        void shouldAcquireWithHotTierForPipelinedStrategy() {
            // given
            var intent = SOME_PIPELINED_INTENT;
            var candidate = SOME_ACTIVE_HOT_ADDRESS;
            var allocation = NonceAllocation.builder()
                    .address(candidate.address())
                    .chain(candidate.chain())
                    .nonce(42L)
                    .build();

            given(addressPoolRepository.findBestCandidate(
                    intent.chain(), AddressTier.HOT, AddressStatus.ACTIVE, MAX_PIPELINE_DEPTH))
                    .willReturn(Optional.of(candidate));
            given(nonceManager.allocate(candidate.address(), intent.chain()))
                    .willReturn(allocation);

            // when
            var result = resourceManager.acquire(intent);

            // then
            var expected = EvmSubmissionResource.builder()
                    .chain(intent.chain())
                    .fromAddress(candidate.address())
                    .nonce(42L)
                    .tier(AddressTier.HOT)
                    .build();

            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);

            then(addressPoolRepository).should()
                    .incrementInFlightCount(candidate.address(), intent.chain());
        }

        @Test
        void shouldAcquireWithPriorityTierForSequentialStrategy() {
            // given
            var intent = SOME_SEQUENTIAL_INTENT;
            var candidate = SOME_ACTIVE_PRIORITY_ADDRESS;
            var allocation = NonceAllocation.builder()
                    .address(candidate.address())
                    .chain(candidate.chain())
                    .nonce(7L)
                    .build();

            given(addressPoolRepository.findBestCandidate(
                    intent.chain(), AddressTier.PRIORITY, AddressStatus.ACTIVE, MAX_PIPELINE_DEPTH))
                    .willReturn(Optional.of(candidate));
            given(nonceManager.allocate(candidate.address(), intent.chain()))
                    .willReturn(allocation);

            // when
            var result = resourceManager.acquire(intent);

            // then
            var expected = EvmSubmissionResource.builder()
                    .chain(intent.chain())
                    .fromAddress(candidate.address())
                    .nonce(7L)
                    .tier(AddressTier.PRIORITY)
                    .build();

            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }

        @Test
        void shouldThrowAndPublishAlertWhenPoolExhausted() {
            // given
            var intent = SOME_PIPELINED_INTENT;

            given(addressPoolRepository.findBestCandidate(
                    intent.chain(), AddressTier.HOT, AddressStatus.ACTIVE, MAX_PIPELINE_DEPTH))
                    .willReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> resourceManager.acquire(intent))
                    .isInstanceOf(NoAvailableAddressException.class)
                    .hasMessageContaining(intent.chain())
                    .hasMessageContaining("HOT");

            then(poolExhaustedAlertPublisher).should().publish(intent.chain(), "HOT");
        }

        @Test
        void shouldDecrementInFlightCountWhenNonceAllocationFails() {
            // given
            var intent = SOME_PIPELINED_INTENT;
            var candidate = SOME_ACTIVE_HOT_ADDRESS;

            given(addressPoolRepository.findBestCandidate(
                    intent.chain(), AddressTier.HOT, AddressStatus.ACTIVE, MAX_PIPELINE_DEPTH))
                    .willReturn(Optional.of(candidate));
            willThrow(new RuntimeException("Redis unavailable"))
                    .given(nonceManager).allocate(candidate.address(), intent.chain());

            // when / then
            assertThatThrownBy(() -> resourceManager.acquire(intent))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Redis unavailable");

            then(addressPoolRepository).should()
                    .incrementInFlightCount(candidate.address(), intent.chain());
            then(addressPoolRepository).should()
                    .decrementInFlightCount(candidate.address(), intent.chain());
        }
    }

    @Nested
    class Release {

        @Test
        void shouldReleaseNonceAndDecrementInFlight() {
            // given
            var resource = EvmSubmissionResource.builder()
                    .chain("ethereum")
                    .fromAddress("0xaddress")
                    .nonce(10L)
                    .tier(AddressTier.HOT)
                    .build();

            // when
            resourceManager.release(resource);

            // then
            var expectedAllocation = NonceAllocation.builder()
                    .address("0xaddress")
                    .chain("ethereum")
                    .nonce(10L)
                    .build();

            then(nonceManager).should().release(expectedAllocation);
            then(addressPoolRepository).should().decrementInFlightCount("0xaddress", "ethereum");
        }
    }

    @Nested
    class Consume {

        @Test
        void shouldConfirmNonceAndDecrementInFlight() {
            // given
            var resource = EvmSubmissionResource.builder()
                    .chain("ethereum")
                    .fromAddress("0xaddress")
                    .nonce(10L)
                    .tier(AddressTier.HOT)
                    .build();

            // when
            resourceManager.consume(resource);

            // then
            var expectedAllocation = NonceAllocation.builder()
                    .address("0xaddress")
                    .chain("ethereum")
                    .nonce(10L)
                    .build();

            then(nonceManager).should().confirm(expectedAllocation);
            then(addressPoolRepository).should().decrementInFlightCount("0xaddress", "ethereum");
        }
    }

    @Nested
    class ResolveTier {

        @Test
        void shouldResolvePriorityForSequential() {
            assertThat(EvmSubmissionResourceManager.resolveTier(SubmissionStrategy.SEQUENTIAL))
                    .isEqualTo(AddressTier.PRIORITY);
        }

        @Test
        void shouldResolveHotForPipelined() {
            assertThat(EvmSubmissionResourceManager.resolveTier(SubmissionStrategy.PIPELINED))
                    .isEqualTo(AddressTier.HOT);
        }
    }
}
