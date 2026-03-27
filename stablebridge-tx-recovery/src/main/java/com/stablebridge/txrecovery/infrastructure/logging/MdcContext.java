package com.stablebridge.txrecovery.infrastructure.logging;

import java.util.Optional;

import org.slf4j.MDC;

import com.stablebridge.txrecovery.domain.transaction.model.TransactionSnapshot;

public final class MdcContext {

    static final String TRANSACTION_ID = "transactionId";
    static final String INTENT_ID = "intentId";
    static final String STATUS = "status";
    static final String TX_HASH = "txHash";
    static final String RETRY_COUNT = "retryCount";
    static final String GAS_SPENT = "gasSpent";
    static final String ESCALATION_TIER = "escalationTier";
    static final String CHAIN = "chain";
    static final String FROM_ADDRESS = "fromAddress";
    static final String TO_ADDRESS = "toAddress";
    static final String AMOUNT = "amount";
    static final String TOKEN = "token";
    static final String TRACE_ID = "traceId";
    static final String LATENCY_MS = "latencyMs";

    private MdcContext() {}

    public static void set(TransactionSnapshot snapshot) {
        MDC.put(TRANSACTION_ID, snapshot.transactionId());
        MDC.put(INTENT_ID, snapshot.intentId());
        MDC.put(STATUS, snapshot.status().name());
        putIfPresent(TX_HASH, snapshot.txHash());
        MDC.put(RETRY_COUNT, String.valueOf(snapshot.retryCount()));
        putIfPresent(GAS_SPENT, Optional.ofNullable(snapshot.gasSpent()).map(Object::toString).orElse(null));
        putIfPresent(ESCALATION_TIER,
                Optional.ofNullable(snapshot.currentTier()).map(tier -> String.valueOf(tier.level())).orElse(null));
    }

    public static void clear() {
        MDC.remove(TRANSACTION_ID);
        MDC.remove(INTENT_ID);
        MDC.remove(STATUS);
        MDC.remove(TX_HASH);
        MDC.remove(RETRY_COUNT);
        MDC.remove(GAS_SPENT);
        MDC.remove(ESCALATION_TIER);
        MDC.remove(CHAIN);
        MDC.remove(FROM_ADDRESS);
        MDC.remove(TO_ADDRESS);
        MDC.remove(AMOUNT);
        MDC.remove(TOKEN);
        MDC.remove(TRACE_ID);
        MDC.remove(LATENCY_MS);
    }

    public static void putChain(String chain) {
        putIfPresent(CHAIN, chain);
    }

    public static void putFromAddress(String fromAddress) {
        putIfPresent(FROM_ADDRESS, fromAddress);
    }

    public static void putToAddress(String toAddress) {
        putIfPresent(TO_ADDRESS, toAddress);
    }

    public static void putAmount(String amount) {
        putIfPresent(AMOUNT, amount);
    }

    public static void putToken(String token) {
        putIfPresent(TOKEN, token);
    }

    public static void putTraceId(String traceId) {
        putIfPresent(TRACE_ID, traceId);
    }

    public static void putLatencyMs(long latencyMs) {
        MDC.put(LATENCY_MS, String.valueOf(latencyMs));
    }

    private static void putIfPresent(String key, String value) {
        if (value != null) {
            MDC.put(key, value);
        }
    }
}
