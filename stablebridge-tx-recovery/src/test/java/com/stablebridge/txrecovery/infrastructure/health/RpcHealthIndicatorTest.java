package com.stablebridge.txrecovery.infrastructure.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthContributors;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.stablebridge.txrecovery.infrastructure.client.evm.EvmBlock;
import com.stablebridge.txrecovery.infrastructure.client.evm.EvmRpcClient;

@ExtendWith(MockitoExtension.class)
class RpcHealthIndicatorTest {

    private static final String CHAIN = "ethereum_mainnet";
    private static final String BLOCK_NUMBER = "0x1234";
    private static final Duration RPC_TIMEOUT = Duration.ofSeconds(5);

    @Mock
    private EvmRpcClient evmRpcClient;

    private RpcHealthIndicator rpcHealthIndicator;

    @BeforeEach
    void setUp() {
        rpcHealthIndicator = new RpcHealthIndicator(
                Map.of(CHAIN, evmRpcClient),
                Map.of(CHAIN, RPC_TIMEOUT));
    }

    @Nested
    class WhenChainIsHealthy {

        @Test
        void shouldReturnUpWithLatencyAndBlockNumber() {
            // given
            var block = EvmBlock.builder()
                    .number(BLOCK_NUMBER)
                    .build();
            given(evmRpcClient.getBlockByNumber("latest", false)).willReturn(block);

            // when
            var contributor = rpcHealthIndicator.getContributor(CHAIN);
            var health = ((HealthIndicator) contributor).health();

            // then
            var expected = Health.up()
                    .withDetail("latencyMs", 0L)
                    .withDetail("blockNumber", BLOCK_NUMBER)
                    .build();
            assertThat(health)
                    .usingRecursiveComparison()
                    .ignoringFields("details.latencyMs")
                    .isEqualTo(expected);
        }
    }

    @Nested
    class WhenChainIsDown {

        @Test
        void shouldReturnDownWithException() {
            // given
            var exception = new RuntimeException("Connection refused");
            given(evmRpcClient.getBlockByNumber("latest", false)).willThrow(exception);

            // when
            var contributor = rpcHealthIndicator.getContributor(CHAIN);
            var health = ((HealthIndicator) contributor).health();

            // then
            var expected = Health.down()
                    .withException(exception)
                    .build();
            assertThat(health)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }
    }

    @Nested
    class WhenContributorNotFound {

        @Test
        void shouldReturnNullForUnknownChain() {
            // when
            var contributor = rpcHealthIndicator.getContributor("unknown_chain");

            // then
            assertThat(contributor).isNull();
        }
    }

    @Nested
    class CompositeStream {

        @Test
        void shouldStreamAllChainContributors() {
            // when
            var entries = rpcHealthIndicator.stream().toList();

            // then
            assertThat(entries)
                    .extracting(HealthContributors.Entry::name)
                    .containsExactly(CHAIN);
        }
    }
}
