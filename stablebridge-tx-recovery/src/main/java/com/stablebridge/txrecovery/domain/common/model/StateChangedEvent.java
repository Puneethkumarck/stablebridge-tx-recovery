package com.stablebridge.txrecovery.domain.common.model;

public record StateChangedEvent<S>(S previousState, S newState) {}
