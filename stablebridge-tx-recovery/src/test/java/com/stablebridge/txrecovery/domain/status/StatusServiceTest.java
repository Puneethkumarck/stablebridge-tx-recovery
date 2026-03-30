package com.stablebridge.txrecovery.domain.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stablebridge.txrecovery.domain.address.model.AddressStatus;
import com.stablebridge.txrecovery.domain.address.model.AddressTier;
import com.stablebridge.txrecovery.domain.address.model.ChainFamily;
import com.stablebridge.txrecovery.domain.address.model.PooledAddress;
import com.stablebridge.txrecovery.domain.address.port.AddressPoolRepository;
import com.stablebridge.txrecovery.domain.address.port.NonceManager;
import com.stablebridge.txrecovery.domain.exception.ChainNotFoundException;
import com.stablebridge.txrecovery.domain.recovery.port.ChainConfigProvider;
import com.stablebridge.txrecovery.domain.status.model.ChainStatusDetail;
import com.stablebridge.txrecovery.domain.status.model.ChainStatusSummary;
import com.stablebridge.txrecovery.domain.status.model.HealthStatus;
import com.stablebridge.txrecovery.domain.status.port.RpcHealthProvider;
import com.stablebridge.txrecovery.domain.status.port.TransactionCountProvider;
import com.stablebridge.txrecovery.domain.status.port.WorkflowHealthProvider;

@ExtendWith(MockitoExtension.class)
class StatusServiceTest {

    private static final String SOME_CHAIN = "ethereum_mainnet";

    @Mock
    private ChainConfigProvider chainConfigProvider;

    @Mock
    private TransactionCountProvider transactionCountProvider;

    @Mock
    private RpcHealthProvider rpcHealthProvider;

    @Mock
    private WorkflowHealthProvider workflowHealthProvider;

    @Mock
    private AddressPoolRepository addressPoolRepository;

    @Mock
    private NonceManager nonceManager;

    private StatusService statusService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        statusService = new StatusService(
                chainConfigProvider, transactionCountProvider, rpcHealthProvider,
                workflowHealthProvider, addressPoolRepository, java.util.Optional.of(nonceManager));
    }

    @Nested
    class GetAllChainStatuses {

        @Test
        void shouldReturnStatusSummaryForAllEnabledChains() {
            // given
            given(chainConfigProvider.enabledChains()).willReturn(Set.of(SOME_CHAIN));
            given(workflowHealthProvider.isHealthy()).willReturn(true);
            given(transactionCountProvider.countPendingByChain(SOME_CHAIN)).willReturn(5L);
            given(transactionCountProvider.countStuckByChain(SOME_CHAIN)).willReturn(1L);
            given(rpcHealthProvider.measureRpcLatencyMs(SOME_CHAIN)).willReturn(42L);

            // when
            var result = statusService.getAllChainStatuses();

            // then
            var expected = List.of(ChainStatusSummary.builder()
                    .chain(SOME_CHAIN)
                    .healthy(true)
                    .pendingCount(5)
                    .stuckCount(1)
                    .rpcLatencyMs(42)
                    .status(HealthStatus.UP)
                    .build());
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }

        @Test
        void shouldReportDegradedWhenTemporalIsUnhealthy() {
            // given
            given(chainConfigProvider.enabledChains()).willReturn(Set.of(SOME_CHAIN));
            given(workflowHealthProvider.isHealthy()).willReturn(false);
            given(transactionCountProvider.countPendingByChain(SOME_CHAIN)).willReturn(0L);
            given(transactionCountProvider.countStuckByChain(SOME_CHAIN)).willReturn(0L);
            given(rpcHealthProvider.measureRpcLatencyMs(SOME_CHAIN)).willReturn(50L);

            // when
            var result = statusService.getAllChainStatuses();

            // then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().status()).isEqualTo(HealthStatus.DEGRADED);
            assertThat(result.getFirst().healthy()).isFalse();
        }

        @Test
        void shouldReportDownWhenRpcFails() {
            // given
            given(chainConfigProvider.enabledChains()).willReturn(Set.of(SOME_CHAIN));
            given(workflowHealthProvider.isHealthy()).willReturn(true);
            given(transactionCountProvider.countPendingByChain(SOME_CHAIN)).willReturn(0L);
            given(transactionCountProvider.countStuckByChain(SOME_CHAIN)).willReturn(0L);
            given(rpcHealthProvider.measureRpcLatencyMs(SOME_CHAIN))
                    .willThrow(new RuntimeException("RPC timeout"));

            // when
            var result = statusService.getAllChainStatuses();

            // then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().status()).isEqualTo(HealthStatus.DOWN);
        }
    }

    @Nested
    class GetChainStatus {

        @Test
        void shouldReturnDetailedStatusWithAddressPoolAndNonceHealth() {
            // given
            given(chainConfigProvider.isChainEnabled(SOME_CHAIN)).willReturn(true);
            given(workflowHealthProvider.isHealthy()).willReturn(true);
            given(transactionCountProvider.countPendingByChain(SOME_CHAIN)).willReturn(5L);
            given(transactionCountProvider.countStuckByChain(SOME_CHAIN)).willReturn(1L);
            given(rpcHealthProvider.measureRpcLatencyMs(SOME_CHAIN)).willReturn(42L);

            var activeAddress = PooledAddress.builder()
                    .id(UUID.randomUUID())
                    .address("0xactive")
                    .chain(SOME_CHAIN)
                    .chainFamily(ChainFamily.EVM)
                    .tier(AddressTier.HOT)
                    .status(AddressStatus.ACTIVE)
                    .currentNonce(10)
                    .inFlightCount(3)
                    .build();
            var drainingAddress = PooledAddress.builder()
                    .id(UUID.randomUUID())
                    .address("0xdraining")
                    .chain(SOME_CHAIN)
                    .chainFamily(ChainFamily.EVM)
                    .tier(AddressTier.HOT)
                    .status(AddressStatus.DRAINING)
                    .currentNonce(5)
                    .inFlightCount(1)
                    .build();
            given(addressPoolRepository.findByFilters(SOME_CHAIN, null, null))
                    .willReturn(List.of(activeAddress, drainingAddress));
            given(nonceManager.detectGaps("0xactive", SOME_CHAIN)).willReturn(Set.of());
            given(nonceManager.detectGaps("0xdraining", SOME_CHAIN)).willReturn(Set.of(7L));

            // when
            var result = statusService.getChainStatus(SOME_CHAIN);

            // then
            var expected = ChainStatusDetail.builder()
                    .chain(SOME_CHAIN)
                    .healthy(true)
                    .pendingCount(5)
                    .stuckCount(1)
                    .rpcLatencyMs(42)
                    .status(HealthStatus.UP)
                    .addressPoolTotal(2)
                    .addressPoolActive(1)
                    .addressPoolDraining(1)
                    .nonceGapCount(1)
                    .nonceInFlightTotal(4)
                    .build();
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }

        @Test
        void shouldThrowChainNotFoundForDisabledChain() {
            // given
            given(chainConfigProvider.isChainEnabled("disabled_chain")).willReturn(false);

            // when/then
            assertThatThrownBy(() -> statusService.getChainStatus("disabled_chain"))
                    .isInstanceOf(ChainNotFoundException.class)
                    .hasMessageContaining("disabled_chain");
        }
    }
}
