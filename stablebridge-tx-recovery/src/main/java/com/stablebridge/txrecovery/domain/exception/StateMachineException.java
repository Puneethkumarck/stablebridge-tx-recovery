package com.stablebridge.txrecovery.domain.exception;

public class StateMachineException extends StrException {

    public <S> StateMachineException(String errorCode, S currentState, S targetState) {
        super(errorCode, "Invalid state transition from %s to %s".formatted(currentState, targetState));
    }
}
