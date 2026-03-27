package com.stablebridge.txrecovery.infrastructure.client.solana;

import lombok.Builder;

@Builder(toBuilder = true)
record JsonRpcResponse<T>(String jsonrpc, long id, T result, JsonRpcError error) {

    record JsonRpcError(int code, String message, Object data) {}
}
