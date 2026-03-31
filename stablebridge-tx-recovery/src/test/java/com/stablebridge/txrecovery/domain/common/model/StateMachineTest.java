package com.stablebridge.txrecovery.domain.common.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.stablebridge.txrecovery.domain.exception.StateMachineException;

class StateMachineTest {

    enum Status {
        CREATED, PENDING, APPROVED, REJECTED
    }

    record Entity(Status status) implements StateProvider<Status> {
        @Override
        public Status state() {
            return status;
        }
    }

    private static final StateMachine<Status, Entity> STATE_MACHINE =
            StateMachine.<Status, Entity>builder()
                    .withExceptionProvider((from, to) -> new StateMachineException("TEST-0001", from, to))
                    .withTransition(Status.CREATED, Status.PENDING, StateMachine.noAction())
                    .withTransitionsFrom(Status.PENDING, Set.of(Status.APPROVED, Status.REJECTED),
                            StateMachine.noAction())
                    .build();

    @Nested
    class ValidTransitions {

        @Test
        void shouldAllowCreatedToPending() {
            // given
            var entity = new Entity(Status.CREATED);

            // when
            var event = STATE_MACHINE.transition(entity, Status.PENDING);

            // then
            assertThat(event).isNull();
        }

        @Test
        void shouldAllowPendingToApproved() {
            // given
            var entity = new Entity(Status.PENDING);

            // when
            var event = STATE_MACHINE.transition(entity, Status.APPROVED);

            // then
            assertThat(event).isNull();
        }

        @Test
        void shouldAllowPendingToRejected() {
            // given
            var entity = new Entity(Status.PENDING);

            // when
            var event = STATE_MACHINE.transition(entity, Status.REJECTED);

            // then
            assertThat(event).isNull();
        }
    }

    @Nested
    class InvalidTransitions {

        @Test
        void shouldThrowForCreatedToApproved() {
            // given
            var entity = new Entity(Status.CREATED);

            // when/then
            assertThatThrownBy(() -> STATE_MACHINE.transition(entity, Status.APPROVED))
                    .isInstanceOf(StateMachineException.class)
                    .hasMessageContaining("CREATED")
                    .hasMessageContaining("APPROVED");
        }

        @Test
        void shouldThrowForApprovedToPending() {
            // given
            var entity = new Entity(Status.APPROVED);

            // when/then
            assertThatThrownBy(() -> STATE_MACHINE.transition(entity, Status.PENDING))
                    .isInstanceOf(StateMachineException.class)
                    .hasMessageContaining("APPROVED")
                    .hasMessageContaining("PENDING");
        }

        @Test
        void shouldThrowForRejectedToApproved() {
            // given
            var entity = new Entity(Status.REJECTED);

            // when/then
            assertThatThrownBy(() -> STATE_MACHINE.transition(entity, Status.APPROVED))
                    .isInstanceOf(StateMachineException.class)
                    .hasMessageContaining("REJECTED")
                    .hasMessageContaining("APPROVED");
        }
    }

    @Nested
    class TransitionActions {

        @Test
        void shouldReturnStateChangedEventFromAction() {
            // given
            var sm = StateMachine.<Status, Entity>builder()
                    .withExceptionProvider((from, to) -> new StateMachineException("TEST-0001", from, to))
                    .withTransition(Status.CREATED, Status.PENDING,
                            entity -> new StateChangedEvent<>(entity.state(), Status.PENDING))
                    .build();
            var entity = new Entity(Status.CREATED);

            // when
            var event = sm.transition(entity, Status.PENDING);

            // then
            var expected = new StateChangedEvent<>(Status.CREATED, Status.PENDING);
            assertThat(event)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }
    }

    @Nested
    class BuilderValidation {

        @Test
        void shouldThrowWhenExceptionProviderMissing() {
            // when/then
            assertThatThrownBy(() -> StateMachine.<Status, Entity>builder().build())
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("exceptionProvider");
        }

        @Test
        void shouldThrowOnDuplicateTransition() {
            // when/then
            assertThatThrownBy(() -> StateMachine.<Status, Entity>builder()
                    .withExceptionProvider((from, to) -> new StateMachineException("TEST-0001", from, to))
                    .withTransition(Status.CREATED, Status.PENDING, StateMachine.noAction())
                    .withTransition(Status.CREATED, Status.PENDING, StateMachine.noAction())
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("CREATED")
                    .hasMessageContaining("PENDING");
        }
    }
}
