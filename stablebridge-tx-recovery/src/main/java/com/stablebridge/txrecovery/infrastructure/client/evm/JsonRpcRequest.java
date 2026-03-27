package com.stablebridge.txrecovery.infrastructure.client.evm;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import lombok.Builder;

@Builder(toBuilder = true)
record JsonRpcRequest(String jsonrpc, String method, List<Object> params, long id) {

    private static final AtomicLong ID_GENERATOR = new AtomicLong(1);

    static JsonRpcRequest create(String method, List<Object> params) {
        return new JsonRpcRequest("2.0", method, params, ID_GENERATOR.getAndIncrement());
    }
}
