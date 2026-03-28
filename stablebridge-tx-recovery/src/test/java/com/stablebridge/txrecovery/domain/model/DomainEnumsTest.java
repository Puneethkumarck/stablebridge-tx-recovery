package com.stablebridge.txrecovery.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.stablebridge.txrecovery.domain.address.model.AddressStatus;
import com.stablebridge.txrecovery.domain.address.model.AddressTier;
import com.stablebridge.txrecovery.domain.address.model.ChainFamily;
import com.stablebridge.txrecovery.domain.address.model.NonceAccountStatus;
import com.stablebridge.txrecovery.domain.recovery.model.ApprovalAction;
import com.stablebridge.txrecovery.domain.recovery.model.FeeUrgency;
import com.stablebridge.txrecovery.domain.recovery.model.RecoveryAction;
import com.stablebridge.txrecovery.domain.recovery.model.RecoveryOutcome;
import com.stablebridge.txrecovery.domain.recovery.model.StuckReason;
import com.stablebridge.txrecovery.domain.recovery.model.StuckSeverity;
import com.stablebridge.txrecovery.domain.transaction.model.SubmissionStrategy;
import com.stablebridge.txrecovery.domain.transaction.model.TransactionStatus;

class DomainEnumsTest {

    static Stream<Arguments> enumDefinitions() {
        return Stream.of(
                Arguments.of(TransactionStatus.class, new String[] {
                        "RECEIVED", "BUILDING", "SIGNING", "SUBMITTED", "PENDING",
                        "STUCK", "RECOVERING", "AWAITING_HUMAN", "CONFIRMED",
                        "FINALIZED", "DROPPED", "FAILED", "CANCELLED"
                }),
                Arguments.of(StuckReason.class, new String[] {
                        "UNDERPRICED", "NONCE_GAP", "NONCE_CONSUMED", "EXPIRED",
                        "MEMPOOL_DROPPED", "NOT_PROPAGATED", "NOT_SEEN", "CONFIRMING"
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

    @ParameterizedTest(name = "{0} has correct enum values")
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
        // when
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
        // when
        var nonTerminal = Arrays.stream(TransactionStatus.values())
                .filter(s -> !s.isTerminal())
                .toList();

        // then
        var expectedNonTerminalCount = TransactionStatus.values().length - 3;
        assertThat(nonTerminal).hasSize(expectedNonTerminalCount);
    }

    @ParameterizedTest(name = "{0} rejects invalid value")
    @MethodSource("allEnumClasses")
    void shouldThrowIllegalArgumentException_whenInvalidEnumValue(Class<? extends Enum<?>> enumClass) {
        // when / then
        assertThatThrownBy(() -> Enum.valueOf(enumClass.asSubclass(Enum.class), "INVALID"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    static Stream<Arguments> allEnumClasses() {
        return Stream.of(
                Arguments.of(TransactionStatus.class),
                Arguments.of(StuckReason.class),
                Arguments.of(RecoveryAction.class),
                Arguments.of(RecoveryOutcome.class),
                Arguments.of(AddressTier.class),
                Arguments.of(AddressStatus.class),
                Arguments.of(ChainFamily.class),
                Arguments.of(FeeUrgency.class),
                Arguments.of(SubmissionStrategy.class),
                Arguments.of(StuckSeverity.class),
                Arguments.of(ApprovalAction.class),
                Arguments.of(NonceAccountStatus.class));
    }
}
