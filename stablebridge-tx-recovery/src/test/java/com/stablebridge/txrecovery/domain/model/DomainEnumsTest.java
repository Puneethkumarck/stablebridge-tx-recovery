package com.stablebridge.txrecovery.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DomainEnumsTest {

    static <E extends Enum<E>> void assertEnumContains(Class<E> enumClass, String... expectedNames) {
        var values = enumClass.getEnumConstants();
        assertThat(values).hasSize(expectedNames.length);
        for (String name : expectedNames) {
            assertThat(Enum.valueOf(enumClass, name)).isNotNull();
        }
    }

    static Stream<Arguments> enumDefinitions() {
        return Stream.of(
                Arguments.of(TransactionStatus.class, new String[] {
                        "RECEIVED", "BUILDING", "SIGNING", "SUBMITTED", "PENDING",
                        "STUCK", "RECOVERING", "AWAITING_HUMAN", "CONFIRMED",
                        "FINALIZED", "DROPPED", "FAILED", "CANCELLED"
                }),
                Arguments.of(StuckReason.class, new String[] {
                        "UNDERPRICED", "NONCE_GAP", "NONCE_CONSUMED", "EXPIRED",
                        "MEMPOOL_DROPPED", "NOT_PROPAGATED", "NOT_SEEN"
                }),
                Arguments.of(RecoveryAction.class, new String[] {
                        "SPEED_UP", "CANCEL", "RESUBMIT", "WAIT"
                }),
                Arguments.of(RecoveryOutcome.class, new String[] {
                        "REPLACEMENT_SUBMITTED", "CONFIRMED", "FAILED", "ESCALATED", "WAITING"
                }),
                Arguments.of(AddressTier.class, new String[] {
                        "HOT", "PRIORITY", "COLD"
                }),
                Arguments.of(AddressStatus.class, new String[] {
                        "ACTIVE", "DRAINING", "RETIRED"
                }),
                Arguments.of(ChainFamily.class, new String[] {
                        "EVM", "SOLANA"
                }),
                Arguments.of(FeeUrgency.class, new String[] {
                        "SLOW", "MEDIUM", "FAST", "URGENT"
                }),
                Arguments.of(SubmissionStrategy.class, new String[] {
                        "SEQUENTIAL", "PIPELINED"
                }),
                Arguments.of(StuckSeverity.class, new String[] {
                        "LOW", "MEDIUM", "HIGH"
                }),
                Arguments.of(ApprovalAction.class, new String[] {
                        "RETRY", "CANCEL", "ABORT"
                }),
                Arguments.of(NonceAccountStatus.class, new String[] {
                        "AVAILABLE", "IN_USE", "EXHAUSTED"
                }));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("enumDefinitions")
    void shouldContainExpectedValues(Class<? extends Enum<?>> enumClass, String[] expectedNames) {
        // when
        var values = enumClass.getEnumConstants();

        // then
        assertThat(values).hasSize(expectedNames.length);
        var actualNames = Arrays.stream(values).map(Enum::name).toList();
        assertThat(actualNames).containsExactlyInAnyOrder(expectedNames);
    }

    @Test
    void shouldIdentifyTerminalTransactionStatuses() {
        // given
        var terminalStatuses = Arrays.stream(TransactionStatus.values())
                .filter(TransactionStatus::isTerminal)
                .toList();

        // then
        assertThat(terminalStatuses).containsExactlyInAnyOrder(
                TransactionStatus.FINALIZED,
                TransactionStatus.FAILED,
                TransactionStatus.CANCELLED);
    }

    @Test
    void shouldIdentifyNonTerminalTransactionStatuses() {
        // given
        var nonTerminal = Arrays.stream(TransactionStatus.values())
                .filter(s -> !s.isTerminal())
                .toList();

        // then
        assertThat(nonTerminal).hasSize(10);
    }

    @Test
    void shouldThrowIllegalArgumentException_whenInvalidEnumValue() {
        // when / then
        assertThatThrownBy(() -> TransactionStatus.valueOf("INVALID"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StuckReason.valueOf("INVALID"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RecoveryAction.valueOf("INVALID"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
