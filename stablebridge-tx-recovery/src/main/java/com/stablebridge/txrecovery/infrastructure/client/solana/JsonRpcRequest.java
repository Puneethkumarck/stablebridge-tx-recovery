package com.stablebridge.txrecovery.infrastructure.client.solana;

import java.util.List;

import lombok.Builder;

@Builder(toBuilder = true)
record JsonRpcRequest(String jsonrpc, long id, String method, List<Object> params) {

    JsonRpcRequest(long id, String method, List<Object> params) {
        this("2.0", id, method, params);
    }
}
