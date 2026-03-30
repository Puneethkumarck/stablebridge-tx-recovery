package com.stablebridge.txrecovery.domain.recovery;

import static com.stablebridge.txrecovery.testutil.fixtures.GasOracleControllerFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.time.Clock;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stablebridge.txrecovery.domain.exception.ChainNotFoundException;
import com.stablebridge.txrecovery.domain.recovery.model.FeeUrgency;
import com.stablebridge.txrecovery.domain.recovery.model.GasSnapshot;
import com.stablebridge.txrecovery.domain.recovery.port.ChainConfigProvider;
import com.stablebridge.txrecovery.domain.recovery.port.FeeOracle;

@ExtendWith(MockitoExtension.class)
class GasOracleQueryServiceTest {

    @Mock
    private Map<String, FeeOracle> evmFeeOracles;

    @Mock
    private ChainConfigProvider chainConfigProvider;

    @Mock
    private Clock clock;

    @InjectMocks
    private GasOracleQueryService gasOracleQueryService;

    @Mock
    private FeeOracle feeOracle;

    @Nested
    class GetCurrentEstimates {

        @Test
        void shouldReturnEstimatesForAllUrgencyTiers() {
            // given
            given(chainConfigProvider.isChainEnabled(SOME_CHAIN)).willReturn(true);
            given(evmFeeOracles.get(SOME_CHAIN)).willReturn(feeOracle);
            given(clock.instant()).willReturn(SOME_UPDATED_AT);
            given(feeOracle.estimate(SOME_CHAIN, FeeUrgency.SLOW)).willReturn(SOME_SLOW_ESTIMATE);
            given(feeOracle.estimate(SOME_CHAIN, FeeUrgency.MEDIUM)).willReturn(SOME_MEDIUM_ESTIMATE);
            given(feeOracle.estimate(SOME_CHAIN, FeeUrgency.FAST)).willReturn(SOME_FAST_ESTIMATE);
            given(feeOracle.estimate(SOME_CHAIN, FeeUrgency.URGENT)).willReturn(SOME_URGENT_ESTIMATE);

            // when
            var result = gasOracleQueryService.getCurrentEstimates(SOME_CHAIN);

            // then
            var expected = GasSnapshot.builder()
                    .chain(SOME_CHAIN)
                    .estimates(SOME_ALL_ESTIMATES)
                    .updatedAt(SOME_UPDATED_AT)
                    .build();
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }

        @Test
        void shouldThrowChainNotFoundWhenChainNotInConfig() {
            // given
            given(chainConfigProvider.isChainEnabled(SOME_UNKNOWN_CHAIN)).willReturn(false);

            // when/then
            assertThatThrownBy(() -> gasOracleQueryService.getCurrentEstimates(SOME_UNKNOWN_CHAIN))
                    .isInstanceOf(ChainNotFoundException.class)
                    .hasMessageContaining(SOME_UNKNOWN_CHAIN);
        }

        @Test
        void shouldThrowChainNotFoundWhenChainIsDisabled() {
            // given
            given(chainConfigProvider.isChainEnabled(SOME_CHAIN)).willReturn(false);

            // when/then
            assertThatThrownBy(() -> gasOracleQueryService.getCurrentEstimates(SOME_CHAIN))
                    .isInstanceOf(ChainNotFoundException.class)
                    .hasMessageContaining(SOME_CHAIN);
        }

        @Test
        void shouldThrowChainNotFoundWhenNoOracleRegistered() {
            // given
            given(chainConfigProvider.isChainEnabled(SOME_CHAIN)).willReturn(true);
            given(evmFeeOracles.get(SOME_CHAIN)).willReturn(null);

            // when/then
            assertThatThrownBy(() -> gasOracleQueryService.getCurrentEstimates(SOME_CHAIN))
                    .isInstanceOf(ChainNotFoundException.class)
                    .hasMessageContaining(SOME_CHAIN);
        }
    }

    @Nested
    class GetHistoricalEstimates {

        @Test
        void shouldReturnEmptyListForHistory() {
            // given
            given(chainConfigProvider.isChainEnabled(SOME_CHAIN)).willReturn(true);
            given(evmFeeOracles.get(SOME_CHAIN)).willReturn(feeOracle);

            // when
            var result = gasOracleQueryService.getHistoricalEstimates(SOME_CHAIN, 24);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        void shouldThrowChainNotFoundForUnknownChainHistory() {
            // given
            given(chainConfigProvider.isChainEnabled(SOME_UNKNOWN_CHAIN)).willReturn(false);

            // when/then
            assertThatThrownBy(() -> gasOracleQueryService.getHistoricalEstimates(SOME_UNKNOWN_CHAIN, 24))
                    .isInstanceOf(ChainNotFoundException.class)
                    .hasMessageContaining(SOME_UNKNOWN_CHAIN);
        }

        @Test
        void shouldThrowForZeroHours() {
            // when/then
            assertThatThrownBy(() -> gasOracleQueryService.getHistoricalEstimates(SOME_CHAIN, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Hours must be between 1 and 168");
        }

        @Test
        void shouldThrowForNegativeHours() {
            // when/then
            assertThatThrownBy(() -> gasOracleQueryService.getHistoricalEstimates(SOME_CHAIN, -1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Hours must be between 1 and 168");
        }

        @Test
        void shouldThrowForHoursExceedingMax() {
            // when/then
            assertThatThrownBy(() -> gasOracleQueryService.getHistoricalEstimates(SOME_CHAIN, 169))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Hours must be between 1 and 168");
        }
    }
}
