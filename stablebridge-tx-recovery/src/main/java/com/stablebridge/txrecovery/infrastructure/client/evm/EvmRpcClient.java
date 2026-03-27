package com.stablebridge.txrecovery.infrastructure.client.evm;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.function.Function;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
public class EvmRpcClient {

    private static final String CONTENT_TYPE = "application/json";
    private static final int HTTP_OK = 200;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final List<String> rpcUrls;
    private final String chain;
    private final Duration timeout;
    private final CircuitBreaker circuitBreaker;
    private final RateLimiter rateLimiter;

    public EvmRpcClient(
            String chain,
            List<String> rpcUrls,
            Duration timeout,
            int rateLimitPerSecond,
            int rateLimitBurst,
            CircuitBreakerRegistry circuitBreakerRegistry,
            RateLimiterRegistry rateLimiterRegistry) {
        this.chain = chain;
        this.rpcUrls = List.copyOf(rpcUrls);
        this.timeout = timeout;

        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(timeout)
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build();

        this.objectMapper = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();

        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(
                "evm-rpc-" + chain,
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(50)
                        .waitDurationInOpenState(Duration.ofSeconds(30))
                        .slidingWindowSize(10)
                        .minimumNumberOfCalls(5)
                        .build());

        this.rateLimiter = rateLimiterRegistry.rateLimiter(
                "evm-rpc-" + chain,
                RateLimiterConfig.custom()
                        .limitRefreshPeriod(Duration.ofSeconds(1))
                        .limitForPeriod(rateLimitPerSecond)
                        .timeoutDuration(Duration.ofMillis(rateLimitBurst * 1000L / rateLimitPerSecond))
                        .build());
    }

    public String sendRawTransaction(String signedTxHex) {
        var request = JsonRpcRequest.create("eth_sendRawTransaction", List.of(signedTxHex));
        return executeWithResilience(request, new TypeReference<JsonRpcResponse<String>>() {});
    }

    public Optional<EvmTransaction> getTransactionByHash(String txHash) {
        var request = JsonRpcRequest.create("eth_getTransactionByHash", List.of(txHash));
        return Optional.ofNullable(
                executeWithResilience(request, new TypeReference<JsonRpcResponse<EvmTransaction>>() {}));
    }

    public Optional<EvmReceipt> getTransactionReceipt(String txHash) {
        var request = JsonRpcRequest.create("eth_getTransactionReceipt", List.of(txHash));
        return Optional.ofNullable(
                executeWithResilience(request, new TypeReference<JsonRpcResponse<EvmReceipt>>() {}));
    }

    public BigInteger getTransactionCount(String address, String blockTag) {
        var request = JsonRpcRequest.create("eth_getTransactionCount", List.of(address, blockTag));
        var hex = executeWithResilience(request, new TypeReference<JsonRpcResponse<String>>() {});
        return decodeQuantity(hex);
    }

    public BigInteger estimateGas(String from, String to, String data, String value) {
        var params = new java.util.LinkedHashMap<String, String>();
        params.put("from", from);
        params.put("to", to);
        if (data != null) {
            params.put("data", data);
        }
        if (value != null) {
            params.put("value", value);
        }
        var request = JsonRpcRequest.create("eth_estimateGas", List.of(params));
        var hex = executeWithResilience(request, new TypeReference<JsonRpcResponse<String>>() {});
        return decodeQuantity(hex);
    }

    public EvmFeeHistory feeHistory(int blockCount, String newestBlock, List<Float> rewardPercentiles) {
        var request =
                JsonRpcRequest.create("eth_feeHistory", List.of(encodeQuantity(blockCount), newestBlock, rewardPercentiles));
        return executeWithResilience(request, new TypeReference<JsonRpcResponse<EvmFeeHistory>>() {});
    }

    public EvmBlock getBlockByNumber(String blockTag, boolean fullTransactions) {
        var request = JsonRpcRequest.create("eth_getBlockByNumber", List.of(blockTag, fullTransactions));
        return executeWithResilience(request, new TypeReference<JsonRpcResponse<EvmBlock>>() {});
    }

    public BigInteger gasPrice() {
        var request = JsonRpcRequest.create("eth_gasPrice", List.of());
        var hex = executeWithResilience(request, new TypeReference<JsonRpcResponse<String>>() {});
        return decodeQuantity(hex);
    }

    public BigInteger getBaseFee() {
        var block = getBlockByNumber("latest", false);
        if (block == null || block.baseFeePerGas() == null) {
            throw new EvmRpcException("Base fee not available for chain " + chain);
        }
        return decodeQuantity(block.baseFeePerGas());
    }

    public <T> List<JsonRpcResponse<T>> executeBatch(
            List<JsonRpcRequest> requests, TypeReference<List<JsonRpcResponse<T>>> typeRef) {
        return executeWithFallback(url -> {
            var decorated = CircuitBreaker.decorateSupplier(
                    circuitBreaker,
                    RateLimiter.decorateSupplier(rateLimiter, () -> sendBatch(url, requests, typeRef)));
            return decorated.get();
        });
    }

    private <T> T executeWithResilience(JsonRpcRequest request, TypeReference<JsonRpcResponse<T>> typeRef) {
        return executeWithFallback(url -> {
            var decorated = CircuitBreaker.decorateSupplier(
                    circuitBreaker,
                    RateLimiter.decorateSupplier(rateLimiter, () -> sendSingle(url, request, typeRef)));
            return decorated.get();
        });
    }

    private <T> T executeWithFallback(Function<String, T> rpcCall) {
        Exception lastException = null;
        for (var i = 0; i < rpcUrls.size(); i++) {
            var url = rpcUrls.get(i);
            try {
                if (i > 0) {
                    log.info("Attempting fallback RPC URL index {} for chain {}", i, chain);
                }
                return rpcCall.apply(url);
            } catch (EvmRpcException e) {
                if (!e.isRetryable()) {
                    throw e;
                }
                lastException = e;
                log.warn("RPC URL index {} failed for chain {}: {}", i, chain, e.getMessage());
            } catch (Exception e) {
                lastException = e;
                log.warn("RPC URL index {} failed for chain {}: {}", i, chain, e.getMessage());
            }
        }
        throw new EvmRpcException("All RPC URLs exhausted for chain " + chain, lastException);
    }

    private <T> T sendSingle(String url, JsonRpcRequest request, TypeReference<JsonRpcResponse<T>> typeRef) {
        var body = objectMapper.writeValueAsString(request);
        var responseBody = doPost(url, body);
        var response = objectMapper.readValue(responseBody, typeRef);
        if (response.hasError()) {
            throw new EvmRpcException(
                    "JSON-RPC error [" + response.error().code() + "]: " + response.error().message(), false);
        }
        return response.result();
    }

    private <T> List<JsonRpcResponse<T>> sendBatch(
            String url, List<JsonRpcRequest> requests, TypeReference<List<JsonRpcResponse<T>>> typeRef) {
        var body = objectMapper.writeValueAsString(requests);
        var responseBody = doPost(url, body);
        return objectMapper.readValue(responseBody, typeRef);
    }

    private String doPost(String url, String body) {
        try {
            var httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(timeout)
                    .header("Content-Type", CONTENT_TYPE)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            var httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (httpResponse.statusCode() != HTTP_OK) {
                throw new EvmRpcException(
                        "HTTP " + httpResponse.statusCode() + " from " + url + ": " + httpResponse.body());
            }
            return httpResponse.body();
        } catch (EvmRpcException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EvmRpcException("RPC call interrupted for " + url, e);
        } catch (Exception e) {
            throw new EvmRpcException("RPC call failed for " + url, e);
        }
    }

    private static BigInteger decodeQuantity(String hex) {
        if (hex == null) {
            throw new EvmRpcException("Cannot decode null hex quantity");
        }
        var stripped = hex.startsWith("0x") ? hex.substring(2) : hex;
        return new BigInteger(stripped, 16);
    }

    private static String encodeQuantity(long value) {
        return "0x" + Long.toHexString(value);
    }

    String getChain() {
        return chain;
    }

    CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

    RateLimiter getRateLimiter() {
        return rateLimiter;
    }
}
