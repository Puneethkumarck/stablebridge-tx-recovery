package com.stablebridge.txrecovery.infrastructure.client.solana;

public class SolanaRpcException extends RuntimeException {

    private final int rpcErrorCode;

    SolanaRpcException(int rpcErrorCode, String message) {
        super(message);
        this.rpcErrorCode = rpcErrorCode;
    }

    SolanaRpcException(String message, Throwable cause) {
        super(message, cause);
        this.rpcErrorCode = -1;
    }

    public int rpcErrorCode() {
        return rpcErrorCode;
    }
}
