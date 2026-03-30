package com.stablebridge.txrecovery.domain.exception;

public class InvalidParameterException extends StrException {

    public InvalidParameterException(String parameterName, String value) {
        super("STR-4003", "Invalid value '%s' for parameter '%s'".formatted(value, parameterName));
    }
}
