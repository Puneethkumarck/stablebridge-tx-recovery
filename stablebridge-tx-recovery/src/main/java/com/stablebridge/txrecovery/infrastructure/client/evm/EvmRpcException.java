package com.stablebridge.txrecovery.infrastructure.client.evm;

import com.stablebridge.txrecovery.domain.exception.StrException;

public class EvmRpcException extends StrException {

    private static final String ERROR_CODE = "STR-5010";

    private final boolean retryable;

    public EvmRpcException(String message) {
        super(ERROR_CODE, message);
        this.retryable = true;
    }

    public EvmRpcException(String message, boolean retryable) {
        super(ERROR_CODE, message);
        this.retryable = retryable;
    }

    public EvmRpcException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
        this.retryable = true;
    }

    boolean isRetryable() {
        return retryable;
    }
}
