package com.stablebridge.txrecovery.domain.exception;

import com.stablebridge.txrecovery.domain.address.model.AddressStatus;

public class AddressStateMachineException extends StateMachineException {

    public AddressStateMachineException(AddressStatus currentState, AddressStatus targetState) {
        super("STR-4002", currentState, targetState);
    }
}
