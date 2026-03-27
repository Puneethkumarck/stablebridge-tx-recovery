package com.stablebridge.txrecovery.infrastructure.client.solana;

import com.stablebridge.txrecovery.domain.exception.StrException;

public class SolanaRpcException extends StrException {

    private static final String ERROR_CODE = "STR-5030";

    private final int rpcErrorCode;

    SolanaRpcException(int rpcErrorCode, String message) {
        super(ERROR_CODE, message);
        this.rpcErrorCode = rpcErrorCode;
    }

    SolanaRpcException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
        this.rpcErrorCode = -1;
    }

    public int rpcErrorCode() {
        return rpcErrorCode;
    }
}
