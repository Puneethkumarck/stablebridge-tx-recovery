package com.stablebridge.txrecovery.infrastructure.client.solana;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import tools.jackson.databind.ObjectMapper;

@WireMockTest
class SolanaRpcClientTest {

    private static final byte[] SOME_SIGNED_TX = new byte[] {1, 2, 3, 4, 5};
    private static final String SOME_TX_SIGNATURE =
            "5VERv8NMvzbJMEkV8xnrLkEaWRtSz9CosKDYjCJjBRnbJLgp8uirBgmQpjKhoR4tjF3ZpRzrFmBV6UjKdiSZkQU";
    private static final String SOME_NONCE_ACCOUNT = "NonceAcct111111111111111111111111111111";
    private static final String SOME_BLOCKHASH = "EETubP5AKHgjPAhzPkA6E6Q25cUFzPSQ4Sfp1kH3Lz9N";
    private static final String SOME_ADDRESS = "Vote111111111111111111111111111111111111111";

    private SolanaRpcClient client;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmInfo) {
        var objectMapper = new ObjectMapper();
        var httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        var endpoint = URI.create(wmInfo.getHttpBaseUrl());
        var circuitBreaker = CircuitBreaker.ofDefaults("solana-test");
        var rateLimiter = RateLimiter.ofDefaults("solana-test");

        client = new SolanaRpcClient(
                httpClient, objectMapper, List.of(endpoint), circuitBreaker, rateLimiter);
    }

    @Nested
    class SendTransaction {

        @Test
        void shouldSendBase64EncodedTransactionAndReturnSignature(WireMockRuntimeInfo wmInfo) {
            // given
            var encoded = Base64.getEncoder().encodeToString(SOME_SIGNED_TX);
            var expectedRequest = """
                    {
                      "jsonrpc": "2.0",
                      "id": 1,
                      "method": "sendTransaction",
                      "params": ["%s", {"encoding": "base64", "skipPreflight": false}]
                    }"""
                    .formatted(encoded);
            var responseBody = """
                    {
                      "jsonrpc": "2.0",
                      "id": 1,
                      "result": "%s"
                    }"""
                    .formatted(SOME_TX_SIGNATURE);

            wmInfo.getWireMock()
                    .register(post(urlEqualTo("/"))
                            .withRequestBody(equalToJson(expectedRequest, true, false))
                            .willReturn(aResponse()
                                    .withStatus(200)
                                    .withHeader("Content-Type", "application/json")
                                    .withBody(responseBody)));

            // when
            var result = client.sendTransaction(SOME_SIGNED_TX);

            // then
            assertThat(result).isEqualTo(SOME_TX_SIGNATURE);
        }

        @Test
        void shouldThrowOnRpcError(WireMockRuntimeInfo wmInfo) {
            // given
            var responseBody = """
                    {
                      "jsonrpc": "2.0",
                      "id": 1,
                      "error": {
                        "code": -32002,
                        "message": "Transaction simulation failed"
                      }
                    }""";

            wmInfo.getWireMock()
                    .register(post(urlEqualTo("/"))
                            .willReturn(aResponse()
                                    .withStatus(200)
                                    .withHeader("Content-Type", "application/json")
                                    .withBody(responseBody)));

            // when/then
            assertThatThrownBy(() -> client.sendTransaction(SOME_SIGNED_TX))
                    .isInstanceOf(SolanaRpcException.class)
                    .hasMessageContaining("All Solana RPC endpoints exhausted")
                    .cause()
                    .hasMessageContaining("Transaction simulation failed");
        }
    }

    @Nested
    class GetSignatureStatuses {

        @Test
        void shouldReturnSignatureStatuses(WireMockRuntimeInfo wmInfo) {
            // given
            var signatures = List.of(SOME_TX_SIGNATURE);
            var responseBody = """
                    {
                      "jsonrpc": "2.0",
                      "id": 2,
                      "result": {
                        "value": [
                          {
                            "slot": 123456,
                            "confirmations": 10,
                            "confirmationStatus": "confirmed",
                            "err": null
                          }
                        ]
                      }
                    }""";

            wmInfo.getWireMock()
                    .register(post(urlEqualTo("/"))
                            .willReturn(aResponse()
                                    .withStatus(200)
                                    .withHeader("Content-Type", "application/json")
                                    .withBody(responseBody)));

            // when
            var result = client.getSignatureStatuses(signatures);

            // then
            assertThat(result).hasSize(1);
            var expected = new SolanaSignatureStatus(123456L, 10L, "confirmed", null);
            assertThat(result.getFirst())
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }

        @Test
        void shouldReturnNullEntryForUnknownSignature(WireMockRuntimeInfo wmInfo) {
            // given
            var responseBody = """
                    {
                      "jsonrpc": "2.0",
                      "id": 2,
                      "result": {
                        "value": [null]
                      }
                    }""";

            wmInfo.getWireMock()
                    .register(post(urlEqualTo("/"))
                            .willReturn(aResponse()
                                    .withStatus(200)
                                    .withHeader("Content-Type", "application/json")
                                    .withBody(responseBody)));

            // when
            var result = client.getSignatureStatuses(List.of("unknownSig"));

            // then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst()).isNull();
        }

        @Test
        void shouldReturnStatusWithError(WireMockRuntimeInfo wmInfo) {
            // given
            var responseBody = """
                    {
                      "jsonrpc": "2.0",
                      "id": 2,
                      "result": {
                        "value": [
                          {
                            "slot": 789,
                            "confirmations": null,
                            "confirmationStatus": "finalized",
                            "err": {"InstructionError": [0, "Custom"]}
                          }
                        ]
                      }
                    }""";

            wmInfo.getWireMock()
                    .register(post(urlEqualTo("/"))
                            .willReturn(aResponse()
                                    .withStatus(200)
                                    .withHeader("Content-Type", "application/json")
                                    .withBody(responseBody)));

            // when
            var result = client.getSignatureStatuses(List.of(SOME_TX_SIGNATURE));

            // then
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().hasError()).isTrue();
            assertThat(result.getFirst().isFinalized()).isTrue();
        }
    }

    @Nested
    class GetRecentPrioritizationFees {

        @Test
        void shouldReturnPrioritizationFees(WireMockRuntimeInfo wmInfo) {
            // given
            var responseBody = """
                    {
                      "jsonrpc": "2.0",
                      "id": 3,
                      "result": [
                        {"slot": 100, "prioritizationFee": 5000},
                        {"slot": 101, "prioritizationFee": 6000}
                      ]
                    }""";

            wmInfo.getWireMock()
                    .register(post(urlEqualTo("/"))
                            .willReturn(aResponse()
                                    .withStatus(200)
                                    .withHeader("Content-Type", "application/json")
                                    .withBody(responseBody)));

            // when
            var result = client.getRecentPrioritizationFees(List.of(SOME_ADDRESS));

            // then
            assertThat(result).hasSize(2);
            assertThat(result.getFirst())
                    .usingRecursiveComparison()
                    .isEqualTo(new SolanaPrioritizationFee(100L, 5000L));
            assertThat(result.getLast())
                    .usingRecursiveComparison()
                    .isEqualTo(new SolanaPrioritizationFee(101L, 6000L));
        }

        @Test
        void shouldReturnEmptyListWhenNoFees(WireMockRuntimeInfo wmInfo) {
            // given
            var responseBody = """
                    {
                      "jsonrpc": "2.0",
                      "id": 3,
                      "result": []
                    }""";

            wmInfo.getWireMock()
                    .register(post(urlEqualTo("/"))
                            .willReturn(aResponse()
                                    .withStatus(200)
                                    .withHeader("Content-Type", "application/json")
                                    .withBody(responseBody)));

            // when
            var result = client.getRecentPrioritizationFees(List.of());

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    class GetNonce {

        @Test
        void shouldReturnNonceDataFromAccountInfo(WireMockRuntimeInfo wmInfo) {
            // given
            var nonceData = "SGVsbG8gV29ybGQ=";
            var responseBody = """
                    {
                      "jsonrpc": "2.0",
                      "id": 4,
                      "result": {
                        "value": {
                          "data": ["%s", "base64"],
                          "owner": "11111111111111111111111111111111",
                          "lamports": 1447680,
                          "executable": false
                        }
                      }
                    }"""
                    .formatted(nonceData);

            wmInfo.getWireMock()
                    .register(post(urlEqualTo("/"))
                            .willReturn(aResponse()
                                    .withStatus(200)
                                    .withHeader("Content-Type", "application/json")
                                    .withBody(responseBody)));

            // when
            var result = client.getNonce(SOME_NONCE_ACCOUNT, SolanaCommitment.CONFIRMED);

            // then
            assertThat(result).isEqualTo(nonceData);
        }

        @Test
        void shouldThrowWhenNonceAccountNotFound(WireMockRuntimeInfo wmInfo) {
            // given
            var responseBody = """
                    {
                      "jsonrpc": "2.0",
                      "id": 4,
                      "result": {
                        "value": null
                      }
                    }""";

            wmInfo.getWireMock()
                    .register(post(urlEqualTo("/"))
                            .willReturn(aResponse()
                                    .withStatus(200)
                                    .withHeader("Content-Type", "application/json")
                                    .withBody(responseBody)));

            // when/then
            assertThatThrownBy(
                            () -> client.getNonce(SOME_NONCE_ACCOUNT, SolanaCommitment.CONFIRMED))
                    .isInstanceOf(SolanaRpcException.class)
                    .hasMessageContaining("Nonce account not found");
        }
    }

    @Nested
    class IsBlockhashValid {

        @Test
        void shouldReturnTrueForValidBlockhash(WireMockRuntimeInfo wmInfo) {
            // given
            var responseBody = """
                    {
                      "jsonrpc": "2.0",
                      "id": 5,
                      "result": {
                        "value": true
                      }
                    }""";

            wmInfo.getWireMock()
                    .register(post(urlEqualTo("/"))
                            .willReturn(aResponse()
                                    .withStatus(200)
                                    .withHeader("Content-Type", "application/json")
                                    .withBody(responseBody)));

            // when
            var result = client.isBlockhashValid(SOME_BLOCKHASH, SolanaCommitment.FINALIZED);

            // then
            assertThat(result).isTrue();
        }

        @Test
        void shouldReturnFalseForExpiredBlockhash(WireMockRuntimeInfo wmInfo) {
            // given
            var responseBody = """
                    {
                      "jsonrpc": "2.0",
                      "id": 5,
                      "result": {
                        "value": false
                      }
                    }""";

            wmInfo.getWireMock()
                    .register(post(urlEqualTo("/"))
                            .willReturn(aResponse()
                                    .withStatus(200)
                                    .withHeader("Content-Type", "application/json")
                                    .withBody(responseBody)));

            // when
            var result = client.isBlockhashValid(SOME_BLOCKHASH, SolanaCommitment.FINALIZED);

            // then
            assertThat(result).isFalse();
        }
    }

    @Nested
    class GetLatestBlockhash {

        @Test
        void shouldReturnLatestBlockhash(WireMockRuntimeInfo wmInfo) {
            // given
            var responseBody = """
                    {
                      "jsonrpc": "2.0",
                      "id": 6,
                      "result": {
                        "value": {
                          "blockhash": "%s",
                          "lastValidBlockHeight": 200000
                        }
                      }
                    }"""
                    .formatted(SOME_BLOCKHASH);

            wmInfo.getWireMock()
                    .register(post(urlEqualTo("/"))
                            .willReturn(aResponse()
                                    .withStatus(200)
                                    .withHeader("Content-Type", "application/json")
                                    .withBody(responseBody)));

            // when
            var result = client.getLatestBlockhash(SolanaCommitment.FINALIZED);

            // then
            var expected = new SolanaBlockhash(
                    new SolanaBlockhash.SolanaBlockhashValue(SOME_BLOCKHASH, 200000L));
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }
    }

    @Nested
    class GetAccountInfo {

        @Test
        void shouldReturnAccountInfo(WireMockRuntimeInfo wmInfo) {
            // given
            var responseBody = """
                    {
                      "jsonrpc": "2.0",
                      "id": 7,
                      "result": {
                        "value": {
                          "data": ["AQAAAA==", "base64"],
                          "owner": "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA",
                          "lamports": 2039280,
                          "executable": false
                        }
                      }
                    }""";

            wmInfo.getWireMock()
                    .register(post(urlEqualTo("/"))
                            .willReturn(aResponse()
                                    .withStatus(200)
                                    .withHeader("Content-Type", "application/json")
                                    .withBody(responseBody)));

            // when
            var result = client.getAccountInfo(SOME_ADDRESS, SolanaCommitment.CONFIRMED);

            // then
            assertThat(result).isNotNull();
            assertThat(result.value()).isNotNull();
            var expected = new SolanaAccountInfo(new SolanaAccountInfo.SolanaAccountValue(
                    List.of("AQAAAA==", "base64"),
                    "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA",
                    2039280L,
                    false));
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void shouldReturnNullValueForNonExistentAccount(WireMockRuntimeInfo wmInfo) {
            // given
            var responseBody = """
                    {
                      "jsonrpc": "2.0",
                      "id": 7,
                      "result": {
                        "value": null
                      }
                    }""";

            wmInfo.getWireMock()
                    .register(post(urlEqualTo("/"))
                            .willReturn(aResponse()
                                    .withStatus(200)
                                    .withHeader("Content-Type", "application/json")
                                    .withBody(responseBody)));

            // when
            var result = client.getAccountInfo(SOME_ADDRESS, SolanaCommitment.CONFIRMED);

            // then
            assertThat(result).isNotNull();
            assertThat(result.value()).isNull();
        }
    }

    @Nested
    class ErrorHandling {

        @Test
        void shouldThrowOnHttpError(WireMockRuntimeInfo wmInfo) {
            // given
            wmInfo.getWireMock()
                    .register(post(urlEqualTo("/"))
                            .willReturn(aResponse().withStatus(503)));

            // when/then
            assertThatThrownBy(() -> client.sendTransaction(SOME_SIGNED_TX))
                    .isInstanceOf(SolanaRpcException.class)
                    .hasMessageContaining("All Solana RPC endpoints exhausted")
                    .cause()
                    .hasMessageContaining("HTTP 503");
        }

        @Test
        void shouldThrowOnJsonRpcError(WireMockRuntimeInfo wmInfo) {
            // given
            var responseBody = """
                    {
                      "jsonrpc": "2.0",
                      "id": 1,
                      "error": {
                        "code": -32600,
                        "message": "Invalid request"
                      }
                    }""";

            wmInfo.getWireMock()
                    .register(post(urlEqualTo("/"))
                            .willReturn(aResponse()
                                    .withStatus(200)
                                    .withHeader("Content-Type", "application/json")
                                    .withBody(responseBody)));

            // when/then
            assertThatThrownBy(() -> client.sendTransaction(SOME_SIGNED_TX))
                    .isInstanceOf(SolanaRpcException.class)
                    .hasMessageContaining("All Solana RPC endpoints exhausted")
                    .cause()
                    .hasMessageContaining("Invalid request");
        }

        @Test
        void shouldThrowOnConnectionError() {
            // given
            var badEndpoint = URI.create("http://localhost:1");
            var httpClient = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .build();
            var objectMapper = new ObjectMapper();
            var brokenClient = new SolanaRpcClient(
                    httpClient,
                    objectMapper,
                    List.of(badEndpoint),
                    CircuitBreaker.ofDefaults("broken-test"),
                    RateLimiter.ofDefaults("broken-test"));

            // when/then
            assertThatThrownBy(() -> brokenClient.sendTransaction(SOME_SIGNED_TX))
                    .isInstanceOf(SolanaRpcException.class)
                    .hasMessageContaining("All Solana RPC endpoints exhausted")
                    .cause()
                    .hasMessageContaining("RPC call failed");
        }

        @Test
        void shouldThrowDescriptiveExceptionWhenNoEndpointsConfigured() {
            // given
            var httpClient = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .build();
            var objectMapper = new ObjectMapper();
            var emptyClient = new SolanaRpcClient(
                    httpClient,
                    objectMapper,
                    List.of(),
                    CircuitBreaker.ofDefaults("empty-test"),
                    RateLimiter.ofDefaults("empty-test"));

            // when/then
            assertThatThrownBy(() -> emptyClient.sendTransaction(SOME_SIGNED_TX))
                    .isInstanceOf(SolanaRpcException.class)
                    .hasMessageContaining("All Solana RPC endpoints exhausted");
        }

        @Test
        void shouldRestoreInterruptFlagOnInterruptedException() {
            // given
            var badEndpoint = URI.create("http://localhost:1");
            var httpClient = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .build();
            var objectMapper = new ObjectMapper();
            var interruptClient = new SolanaRpcClient(
                    httpClient,
                    objectMapper,
                    List.of(badEndpoint),
                    CircuitBreaker.ofDefaults("interrupt-test"),
                    RateLimiter.ofDefaults("interrupt-test"));

            Thread.currentThread().interrupt();

            // when/then
            assertThatThrownBy(() -> interruptClient.sendTransaction(SOME_SIGNED_TX))
                    .isInstanceOf(RuntimeException.class);
            assertThat(Thread.interrupted()).isTrue();
        }
    }

    @Nested
    class UrlFallback {

        @Test
        void shouldFallbackToSecondEndpoint(WireMockRuntimeInfo wmInfo) {
            // given
            var badEndpoint = URI.create("http://localhost:1");
            var goodEndpoint = URI.create(wmInfo.getHttpBaseUrl());
            var httpClient = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .build();
            var objectMapper = new ObjectMapper();
            var fallbackClient = new SolanaRpcClient(
                    httpClient,
                    objectMapper,
                    List.of(badEndpoint, goodEndpoint),
                    CircuitBreaker.ofDefaults("fallback-test"),
                    RateLimiter.ofDefaults("fallback-test"));

            var responseBody = """
                    {
                      "jsonrpc": "2.0",
                      "id": 1,
                      "result": "%s"
                    }"""
                    .formatted(SOME_TX_SIGNATURE);

            wmInfo.getWireMock()
                    .register(post(urlEqualTo("/"))
                            .willReturn(aResponse()
                                    .withStatus(200)
                                    .withHeader("Content-Type", "application/json")
                                    .withBody(responseBody)));

            // when
            var result = fallbackClient.sendTransaction(SOME_SIGNED_TX);

            // then
            assertThat(result).isEqualTo(SOME_TX_SIGNATURE);
        }
    }

    @Nested
    class SolanaCommitmentValues {

        @Test
        void shouldReturnLowercaseValues() {
            assertThat(SolanaCommitment.PROCESSED.value()).isEqualTo("processed");
            assertThat(SolanaCommitment.CONFIRMED.value()).isEqualTo("confirmed");
            assertThat(SolanaCommitment.FINALIZED.value()).isEqualTo("finalized");
        }
    }

    @Nested
    class SolanaSignatureStatusBehavior {

        @Test
        void shouldIdentifyConfirmedStatus() {
            // given
            var status = new SolanaSignatureStatus(100L, 5L, "confirmed", null);

            // when/then
            assertThat(status.isConfirmedOrFinalized()).isTrue();
            assertThat(status.isFinalized()).isFalse();
            assertThat(status.hasError()).isFalse();
        }

        @Test
        void shouldIdentifyFinalizedStatus() {
            // given
            var status = new SolanaSignatureStatus(100L, null, "finalized", null);

            // when/then
            assertThat(status.isConfirmedOrFinalized()).isTrue();
            assertThat(status.isFinalized()).isTrue();
        }

        @Test
        void shouldIdentifyProcessedAsNotConfirmed() {
            // given
            var status = new SolanaSignatureStatus(100L, 0L, "processed", null);

            // when/then
            assertThat(status.isConfirmedOrFinalized()).isFalse();
        }

        @Test
        void shouldDetectError() {
            // given
            var status = new SolanaSignatureStatus(100L, null, "finalized", "some error");

            // when/then
            assertThat(status.hasError()).isTrue();
        }
    }
}
