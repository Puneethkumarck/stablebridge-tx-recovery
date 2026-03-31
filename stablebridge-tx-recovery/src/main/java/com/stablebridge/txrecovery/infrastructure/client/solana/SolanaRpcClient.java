package com.stablebridge.txrecovery.infrastructure.client.solana;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Slf4j
public class SolanaRpcClient {

    private static final String CONTENT_TYPE = "application/json";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final List<URI> rpcEndpoints;
    private final Duration timeout;
    private final CircuitBreaker circuitBreaker;
    private final RateLimiter rateLimiter;
    private final AtomicLong requestIdCounter;

    public SolanaRpcClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            List<URI> rpcEndpoints,
            Duration timeout,
            CircuitBreaker circuitBreaker,
            RateLimiter rateLimiter) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.rpcEndpoints = List.copyOf(rpcEndpoints);
        this.timeout = timeout;
        this.circuitBreaker = circuitBreaker;
        this.rateLimiter = rateLimiter;
        this.requestIdCounter = new AtomicLong(1);
    }

    public String sendTransaction(byte[] signedTransaction) {
        var encoded = Base64.getEncoder().encodeToString(signedTransaction);
        var config = Map.of("encoding", "base64", "skipPreflight", false);
        var params = List.<Object>of(encoded, config);
        var request = new JsonRpcRequest(nextId(), "sendTransaction", params);
        return execute(request, new TypeReference<JsonRpcResponse<String>>() {});
    }

    public List<SolanaSignatureStatus> getSignatureStatuses(List<String> signatures) {
        var config = Map.of("searchTransactionHistory", true);
        var params = List.<Object>of(signatures, config);
        var request = new JsonRpcRequest(nextId(), "getSignatureStatuses", params);
        var response = execute(
                request,
                new TypeReference<JsonRpcResponse<SolanaSignatureStatusResult>>() {});
        return response.value();
    }

    public List<SolanaPrioritizationFee> getRecentPrioritizationFees(List<String> accountKeys) {
        var params = accountKeys.isEmpty() ? List.<Object>of() : List.<Object>of(accountKeys);
        var request = new JsonRpcRequest(nextId(), "getRecentPrioritizationFees", params);
        return execute(request, new TypeReference<JsonRpcResponse<List<SolanaPrioritizationFee>>>() {});
    }

    public String getNonce(String nonceAccountAddress, SolanaCommitment commitment) {
        var config = Map.of("encoding", "base64", "commitment", commitment.value());
        var params = List.<Object>of(nonceAccountAddress, config);
        var request = new JsonRpcRequest(nextId(), "getAccountInfo", params);
        var accountInfo =
                execute(request, new TypeReference<JsonRpcResponse<SolanaAccountInfo>>() {});
        var base64Data = Optional.ofNullable(accountInfo)
                .map(SolanaAccountInfo::value)
                .map(v -> v.data().getFirst())
                .orElseThrow(() -> new SolanaRpcException(-1, "Nonce account not found: " + nonceAccountAddress));
        return extractBlockhashFromNonceAccount(base64Data);
    }

    private static String extractBlockhashFromNonceAccount(String base64Data) {
        var data = Base64.getDecoder().decode(base64Data);
        if (data.length < NONCE_BLOCKHASH_END) {
            throw new SolanaRpcException(-1,
                    "Nonce account data too short: expected >= %d bytes, got %d"
                            .formatted(NONCE_BLOCKHASH_END, data.length));
        }
        var blockhash = new byte[PUBKEY_LENGTH];
        System.arraycopy(data, NONCE_BLOCKHASH_OFFSET, blockhash, 0, PUBKEY_LENGTH);
        return encodeBase58(blockhash);
    }

    private static final int PUBKEY_LENGTH = 32;
    private static final int NONCE_BLOCKHASH_OFFSET = 40;
    private static final int NONCE_BLOCKHASH_END = 72;
    private static final String BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";

    private static String encodeBase58(byte[] input) {
        var bi = java.math.BigInteger.ZERO;
        for (var b : input) {
            bi = bi.multiply(java.math.BigInteger.valueOf(256)).add(java.math.BigInteger.valueOf(b & 0xFF));
        }
        var sb = new StringBuilder();
        while (bi.compareTo(java.math.BigInteger.ZERO) > 0) {
            var divmod = bi.divideAndRemainder(java.math.BigInteger.valueOf(58));
            sb.insert(0, BASE58_ALPHABET.charAt(divmod[1].intValue()));
            bi = divmod[0];
        }
        for (var b : input) {
            if (b == 0) {
                sb.insert(0, '1');
            } else {
                break;
            }
        }
        return sb.toString();
    }

    public boolean isBlockhashValid(String blockhash, SolanaCommitment commitment) {
        var config = Map.of("commitment", commitment.value());
        var params = List.<Object>of(blockhash, config);
        var request = new JsonRpcRequest(nextId(), "isBlockhashValid", params);
        var validity =
                execute(request, new TypeReference<JsonRpcResponse<SolanaBlockhashValidity>>() {});
        return validity.value();
    }

    public SolanaBlockhash getLatestBlockhash(SolanaCommitment commitment) {
        var config = Map.of("commitment", commitment.value());
        var params = List.<Object>of(config);
        var request = new JsonRpcRequest(nextId(), "getLatestBlockhash", params);
        return execute(request, new TypeReference<JsonRpcResponse<SolanaBlockhash>>() {});
    }

    public SolanaAccountInfo getAccountInfo(String address, SolanaCommitment commitment) {
        var config = Map.of("encoding", "base64", "commitment", commitment.value());
        var params = List.<Object>of(address, config);
        var request = new JsonRpcRequest(nextId(), "getAccountInfo", params);
        return execute(request, new TypeReference<JsonRpcResponse<SolanaAccountInfo>>() {});
    }

    private <T> T execute(JsonRpcRequest rpcRequest, TypeReference<JsonRpcResponse<T>> responseType) {
        return CircuitBreaker.decorateSupplier(
                        circuitBreaker,
                        RateLimiter.decorateSupplier(
                                rateLimiter, () -> executeWithFallback(rpcRequest, responseType)))
                .get();
    }

    private <T> T executeWithFallback(
            JsonRpcRequest rpcRequest, TypeReference<JsonRpcResponse<T>> responseType) {
        RuntimeException lastException = null;
        for (var endpoint : rpcEndpoints) {
            try {
                return executeRequest(endpoint, rpcRequest, responseType);
            } catch (RuntimeException e) {
                log.warn("RPC call to {} failed, trying next endpoint", endpoint, e);
                lastException = e;
            }
        }
        throw new SolanaRpcException(
                "All Solana RPC endpoints exhausted", lastException);
    }

    private <T> T executeRequest(
            URI endpoint,
            JsonRpcRequest rpcRequest,
            TypeReference<JsonRpcResponse<T>> responseType) {
        try {
            var body = objectMapper.writeValueAsString(rpcRequest);
            var httpRequest = HttpRequest.newBuilder()
                    .uri(endpoint)
                    .timeout(timeout)
                    .header("Content-Type", CONTENT_TYPE)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            var httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (httpResponse.statusCode() != 200) {
                throw new SolanaRpcException(
                        -1,
                        "HTTP " + httpResponse.statusCode() + " from " + endpoint);
            }

            var rpcResponse = objectMapper.readValue(httpResponse.body(), responseType);

            if (rpcResponse.error() != null) {
                throw new SolanaRpcException(
                        rpcResponse.error().code(), rpcResponse.error().message());
            }

            return rpcResponse.result();
        } catch (SolanaRpcException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SolanaRpcException("RPC call interrupted for " + endpoint, e);
        } catch (Exception e) {
            throw new SolanaRpcException("RPC call failed: " + rpcRequest.method(), e);
        }
    }

    private long nextId() {
        return requestIdCounter.getAndIncrement();
    }

    record SolanaSignatureStatusResult(List<SolanaSignatureStatus> value) {}
}
