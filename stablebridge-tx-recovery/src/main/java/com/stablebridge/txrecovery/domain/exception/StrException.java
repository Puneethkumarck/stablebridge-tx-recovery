package com.stablebridge.txrecovery.domain.exception;

import java.util.Objects;

import lombok.Getter;

@Getter
public abstract class StrException extends RuntimeException {

    private final String errorCode;

    protected StrException(String errorCode, String message) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode);
    }

    protected StrException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = Objects.requireNonNull(errorCode);
    }
}
