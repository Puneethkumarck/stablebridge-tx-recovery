package com.stablebridge.txrecovery.domain.common.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.stablebridge.txrecovery.domain.exception.StateMachineException;

public class StateMachine<S, T extends StateProvider<S>> {

    private final Map<TransitionKey<S>, Function<T, StateChangedEvent<S>>> transitions;
    private final BiFunction<S, S, ? extends StateMachineException> exceptionProvider;

    private StateMachine(Map<TransitionKey<S>, Function<T, StateChangedEvent<S>>> transitions,
            BiFunction<S, S, ? extends StateMachineException> exceptionProvider) {
        this.transitions = Map.copyOf(transitions);
        this.exceptionProvider = Objects.requireNonNull(exceptionProvider);
    }

    public StateChangedEvent<S> transition(T entity, S targetState) {
        var currentState = entity.state();
        var key = new TransitionKey<>(currentState, targetState);
        var action = transitions.get(key);
        if (action == null) {
            throw exceptionProvider.apply(currentState, targetState);
        }
        return action.apply(entity);
    }

    public static <S, T extends StateProvider<S>> Builder<S, T> builder() {
        return new Builder<>();
    }

    public static <S, T extends StateProvider<S>> Function<T, StateChangedEvent<S>> noAction() {
        return _ -> null;
    }

    public static class Builder<S, T extends StateProvider<S>> {

        private final Map<TransitionKey<S>, Function<T, StateChangedEvent<S>>> transitions = new HashMap<>();
        private BiFunction<S, S, ? extends StateMachineException> exceptionProvider;

        public Builder<S, T> withExceptionProvider(
                BiFunction<S, S, ? extends StateMachineException> exceptionProvider) {
            this.exceptionProvider = exceptionProvider;
            return this;
        }

        public Builder<S, T> withTransition(S from, S to, Function<T, StateChangedEvent<S>> action) {
            var key = new TransitionKey<>(from, to);
            if (transitions.containsKey(key)) {
                throw new IllegalArgumentException("Duplicate transition: %s -> %s".formatted(from, to));
            }
            transitions.put(key, action);
            return this;
        }

        public Builder<S, T> withTransitionsFrom(S from, Set<S> toStates,
                Function<T, StateChangedEvent<S>> action) {
            toStates.forEach(to -> withTransition(from, to, action));
            return this;
        }

        public StateMachine<S, T> build() {
            Objects.requireNonNull(exceptionProvider, "exceptionProvider is required");
            return new StateMachine<>(transitions, exceptionProvider);
        }
    }

    private record TransitionKey<S>(S from, S to) {}
}
