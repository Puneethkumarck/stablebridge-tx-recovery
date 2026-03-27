package com.stablebridge.txrecovery.infrastructure.client.solana;

record JsonRpcResponse<T>(String jsonrpc, long id, T result, JsonRpcError error) {

    record JsonRpcError(int code, String message, Object data) {}
}
