package com.stablebridge.txrecovery.infrastructure.client.evm;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.stablebridge.txrecovery.testutil.stubs.EvmRpcStubs.stubJsonRpcResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import tools.jackson.core.type.TypeReference;

class EvmRpcClientTest {

    private static final String CHAIN = "ethereum";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final int RATE_LIMIT_PER_SECOND = 100;
    private static final int RATE_LIMIT_BURST = 100;

    private WireMockServer wireMockServer;
    private EvmRpcClient client;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();

        client = new EvmRpcClient(
                CHAIN,
                List.of(wireMockServer.baseUrl()),
                TIMEOUT,
                RATE_LIMIT_PER_SECOND,
                RATE_LIMIT_BURST,
                CircuitBreakerRegistry.ofDefaults(),
                RateLimiterRegistry.ofDefaults());
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Nested
    class SendRawTransaction {

        @Test
        void shouldSendRawTransactionAndReturnTxHash() {
            // given
            var signedTx = "0xf86c0a8502540be40082520894...";
            var expectedHash = "0xabc123def456";
            stubJsonRpcResponse(wireMockServer, "eth_sendRawTransaction", "\"" + expectedHash + "\"");

            // when
            var result = client.sendRawTransaction(signedTx);

            // then
            assertThat(result).isEqualTo(expectedHash);
            wireMockServer.verify(
                    postRequestedFor(urlEqualTo("/")).withRequestBody(containing("eth_sendRawTransaction")));
        }
    }

    @Nested
    class GetTransactionByHash {

        @Test
        void shouldReturnTransactionWhenFound() {
            // given
            var txHash = "0xabc123";
            stubJsonRpcResponse(
                    wireMockServer,
                    "eth_getTransactionByHash",
                    """
                    {
                        "hash": "0xabc123",
                        "nonce": "0x1",
                        "blockHash": "0xblock",
                        "blockNumber": "0xa",
                        "transactionIndex": "0x0",
                        "from": "0xsender",
                        "to": "0xreceiver",
                        "value": "0xde0b6b3a7640000",
                        "gas": "0x5208",
                        "gasPrice": "0x4a817c800",
                        "input": "0x",
                        "type": "0x2"
                    }
                    """);

            // when
            var result = client.getTransactionByHash(txHash);

            // then
            var expected = new EvmTransaction(
                    "0xabc123",
                    "0x1",
                    "0xblock",
                    "0xa",
                    "0x0",
                    "0xsender",
                    "0xreceiver",
                    "0xde0b6b3a7640000",
                    "0x5208",
                    "0x4a817c800",
                    null,
                    null,
                    "0x",
                    "0x2");
            assertThat(result).isPresent();
            assertThat(result.get()).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void shouldReturnEmptyWhenTransactionNotFound() {
            // given
            var txHash = "0xnonexistent";
            stubJsonRpcResponse(wireMockServer, "eth_getTransactionByHash", "null");

            // when
            var result = client.getTransactionByHash(txHash);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    class GetTransactionReceipt {

        @Test
        void shouldReturnReceiptWhenFound() {
            // given
            var txHash = "0xabc123";
            stubJsonRpcResponse(
                    wireMockServer,
                    "eth_getTransactionReceipt",
                    """
                    {
                        "transactionHash": "0xabc123",
                        "transactionIndex": "0x0",
                        "blockHash": "0xblock",
                        "blockNumber": "0xa",
                        "from": "0xsender",
                        "to": "0xreceiver",
                        "cumulativeGasUsed": "0x5208",
                        "gasUsed": "0x5208",
                        "effectiveGasPrice": "0x4a817c800",
                        "status": "0x1",
                        "logs": [],
                        "type": "0x2"
                    }
                    """);

            // when
            var result = client.getTransactionReceipt(txHash);

            // then
            var expected = new EvmReceipt(
                    "0xabc123",
                    "0x0",
                    "0xblock",
                    "0xa",
                    "0xsender",
                    "0xreceiver",
                    "0x5208",
                    "0x5208",
                    "0x4a817c800",
                    "0x1",
                    List.of(),
                    null,
                    "0x2");
            assertThat(result).isPresent();
            assertThat(result.get()).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void shouldReturnEmptyWhenReceiptNotFound() {
            // given
            var txHash = "0xnonexistent";
            stubJsonRpcResponse(wireMockServer, "eth_getTransactionReceipt", "null");

            // when
            var result = client.getTransactionReceipt(txHash);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    class GetTransactionCount {

        @Test
        void shouldReturnNonce() {
            // given
            var address = "0xsender";
            stubJsonRpcResponse(wireMockServer, "eth_getTransactionCount", "\"0xa\"");

            // when
            var result = client.getTransactionCount(address, "latest");

            // then
            assertThat(result).isEqualTo(BigInteger.TEN);
        }
    }

    @Nested
    class EstimateGas {

        @Test
        void shouldReturnEstimatedGas() {
            // given
            stubJsonRpcResponse(wireMockServer, "eth_estimateGas", "\"0x5208\"");

            // when
            var result = client.estimateGas("0xfrom", "0xto", "0x", null);

            // then
            assertThat(result).isEqualTo(BigInteger.valueOf(21000));
        }

        @Test
        void shouldEstimateGasWithValueParameter() {
            // given
            stubJsonRpcResponse(wireMockServer, "eth_estimateGas", "\"0x5208\"");

            // when
            var result = client.estimateGas("0xfrom", "0xto", null, "0xde0b6b3a7640000");

            // then
            assertThat(result).isEqualTo(BigInteger.valueOf(21000));
        }
    }

    @Nested
    class FeeHistory {

        @Test
        void shouldReturnFeeHistory() {
            // given
            stubJsonRpcResponse(
                    wireMockServer,
                    "eth_feeHistory",
                    """
                    {
                        "oldestBlock": "0xa",
                        "baseFeePerGas": ["0x10", "0x12", "0x14"],
                        "gasUsedRatio": [0.5, 0.6],
                        "reward": [["0x1", "0x2"], ["0x3", "0x4"]]
                    }
                    """);

            // when
            var result = client.feeHistory(2, "latest", List.of(25.0f, 75.0f));

            // then
            var expected = new EvmFeeHistory(
                    "0xa",
                    List.of("0x10", "0x12", "0x14"),
                    List.of(0.5f, 0.6f),
                    List.of(List.of("0x1", "0x2"), List.of("0x3", "0x4")));
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }
    }

    @Nested
    class GetBlockByNumber {

        @Test
        void shouldReturnBlock() {
            // given
            stubJsonRpcResponse(
                    wireMockServer,
                    "eth_getBlockByNumber",
                    """
                    {
                        "number": "0xa",
                        "hash": "0xblockhash",
                        "parentHash": "0xparenthash",
                        "timestamp": "0x5f5e100",
                        "gasLimit": "0x1c9c380",
                        "gasUsed": "0x5208",
                        "baseFeePerGas": "0x3b9aca00",
                        "miner": "0xminer",
                        "transactions": []
                    }
                    """);

            // when
            var result = client.getBlockByNumber("latest", false);

            // then
            var expected = new EvmBlock(
                    "0xa",
                    "0xblockhash",
                    "0xparenthash",
                    "0x5f5e100",
                    "0x1c9c380",
                    "0x5208",
                    "0x3b9aca00",
                    "0xminer",
                    List.of());
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }
    }

    @Nested
    class GasPrice {

        @Test
        void shouldReturnGasPrice() {
            // given
            stubJsonRpcResponse(wireMockServer, "eth_gasPrice", "\"0x3b9aca00\"");

            // when
            var result = client.gasPrice();

            // then
            assertThat(result).isEqualTo(BigInteger.valueOf(1_000_000_000L));
        }
    }

    @Nested
    class GetBaseFee {

        @Test
        void shouldReturnBaseFeeFromLatestBlock() {
            // given
            stubJsonRpcResponse(
                    wireMockServer,
                    "eth_getBlockByNumber",
                    """
                    {
                        "number": "0xa",
                        "hash": "0xblockhash",
                        "parentHash": "0xparenthash",
                        "timestamp": "0x5f5e100",
                        "gasLimit": "0x1c9c380",
                        "gasUsed": "0x5208",
                        "baseFeePerGas": "0x3b9aca00",
                        "miner": "0xminer",
                        "transactions": []
                    }
                    """);

            // when
            var result = client.getBaseFee();

            // then
            assertThat(result).isEqualTo(BigInteger.valueOf(1_000_000_000L));
        }

        @Test
        void shouldThrowWhenBaseFeeNotAvailable() {
            // given
            stubJsonRpcResponse(
                    wireMockServer,
                    "eth_getBlockByNumber",
                    """
                    {
                        "number": "0xa",
                        "hash": "0xblockhash",
                        "parentHash": "0xparenthash",
                        "timestamp": "0x5f5e100",
                        "gasLimit": "0x1c9c380",
                        "gasUsed": "0x5208",
                        "miner": "0xminer",
                        "transactions": []
                    }
                    """);

            // when/then
            assertThatThrownBy(() -> client.getBaseFee())
                    .isInstanceOf(EvmRpcException.class)
                    .hasMessageContaining("Base fee not available");
        }
    }

    @Nested
    class BatchRequests {

        @Test
        void shouldSendBatchRequestAndReturnMultipleResponses() {
            // given
            var batchResponse =
                    """
                    [
                        {"jsonrpc": "2.0", "id": 1, "result": "0x3b9aca00"},
                        {"jsonrpc": "2.0", "id": 2, "result": "0xa"}
                    ]
                    """;
            wireMockServer.stubFor(post(urlEqualTo("/"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(batchResponse)));

            var requests = List.of(
                    JsonRpcRequest.create("eth_gasPrice", List.of()),
                    JsonRpcRequest.create("eth_blockNumber", List.of()));

            // when
            var results = client.executeBatch(requests, new TypeReference<List<JsonRpcResponse<String>>>() {});

            // then
            var expectedFirst = new JsonRpcResponse<>("2.0", 1L, "0x3b9aca00", null);
            var expectedSecond = new JsonRpcResponse<>("2.0", 2L, "0xa", null);
            assertThat(results).hasSize(2);
            assertThat(results.get(0)).usingRecursiveComparison().isEqualTo(expectedFirst);
            assertThat(results.get(1)).usingRecursiveComparison().isEqualTo(expectedSecond);
        }
    }

    @Nested
    class ErrorHandling {

        @Test
        void shouldThrowOnJsonRpcError() {
            // given
            var errorResponse =
                    """
                    {
                        "jsonrpc": "2.0",
                        "id": 1,
                        "error": {
                            "code": -32000,
                            "message": "insufficient funds"
                        }
                    }
                    """;
            wireMockServer.stubFor(post(urlEqualTo("/"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(errorResponse)));

            // when/then
            assertThatThrownBy(() -> client.sendRawTransaction("0xsigned"))
                    .isInstanceOf(EvmRpcException.class)
                    .hasMessageContaining("insufficient funds")
                    .hasMessageContaining("-32000");
        }

        @Test
        void shouldThrowOnHttpError() {
            // given
            wireMockServer.stubFor(post(urlEqualTo("/"))
                    .willReturn(aResponse().withStatus(500).withBody("Internal Server Error")));

            // when/then
            assertThatThrownBy(() -> client.gasPrice())
                    .isInstanceOf(EvmRpcException.class)
                    .hasMessageContaining("All RPC URLs exhausted")
                    .cause()
                    .hasMessageContaining("HTTP 500");
        }

        @Test
        void shouldThrowWhenAllUrlsExhausted() {
            // given
            wireMockServer.stubFor(
                    post(urlEqualTo("/")).willReturn(aResponse().withStatus(503).withBody("Service Unavailable")));

            // when/then
            assertThatThrownBy(() -> client.gasPrice())
                    .isInstanceOf(EvmRpcException.class)
                    .hasMessageContaining("All RPC URLs exhausted");
        }
    }

    @Nested
    class UrlFallback {

        @Test
        void shouldFallbackToSecondaryUrl() {
            // given
            var primaryServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
            var secondaryServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
            primaryServer.start();
            secondaryServer.start();

            try {
                primaryServer.stubFor(
                        post(urlEqualTo("/")).willReturn(aResponse().withStatus(503).withBody("Primary down")));
                secondaryServer.stubFor(post(urlEqualTo("/"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"0x3b9aca00\"}")));

                var fallbackClient = new EvmRpcClient(
                        CHAIN,
                        List.of(primaryServer.baseUrl(), secondaryServer.baseUrl()),
                        TIMEOUT,
                        RATE_LIMIT_PER_SECOND,
                        RATE_LIMIT_BURST,
                        CircuitBreakerRegistry.ofDefaults(),
                        RateLimiterRegistry.ofDefaults());

                // when
                var result = fallbackClient.gasPrice();

                // then
                assertThat(result).isEqualTo(BigInteger.valueOf(1_000_000_000L));
            } finally {
                primaryServer.stop();
                secondaryServer.stop();
            }
        }
    }

    @Nested
    class ResilienceConfiguration {

        @Test
        void shouldCreateCircuitBreakerWithChainName() {
            // when
            var cb = client.getCircuitBreaker();

            // then
            assertThat(cb.getName()).isEqualTo("evm-rpc-" + CHAIN);
            assertThat(cb.getCircuitBreakerConfig())
                    .extracting(
                            config -> config.getFailureRateThreshold(),
                            config -> config.getSlidingWindowSize(),
                            config -> config.getMinimumNumberOfCalls())
                    .containsExactly(50f, 10, 5);
        }

        @Test
        void shouldCreateRateLimiterWithChainName() {
            // when
            var rl = client.getRateLimiter();

            // then
            assertThat(rl.getName()).isEqualTo("evm-rpc-" + CHAIN);
            assertThat(rl.getRateLimiterConfig())
                    .extracting(
                            config -> config.getLimitForPeriod(),
                            config -> config.getLimitRefreshPeriod())
                    .containsExactly(RATE_LIMIT_PER_SECOND, Duration.ofSeconds(1));
        }
    }

    @Nested
    class ReceiptWithLogs {

        @Test
        void shouldDeserializeReceiptWithLogs() {
            // given
            var txHash = "0xabc123";
            stubJsonRpcResponse(
                    wireMockServer,
                    "eth_getTransactionReceipt",
                    """
                    {
                        "transactionHash": "0xabc123",
                        "transactionIndex": "0x0",
                        "blockHash": "0xblock",
                        "blockNumber": "0xa",
                        "from": "0xsender",
                        "to": "0xcontract",
                        "cumulativeGasUsed": "0xb000",
                        "gasUsed": "0x7000",
                        "effectiveGasPrice": "0x4a817c800",
                        "status": "0x1",
                        "logs": [
                            {
                                "address": "0xcontract",
                                "topics": ["0xtopic1", "0xtopic2"],
                                "data": "0xdata",
                                "blockNumber": "0xa",
                                "blockHash": "0xblock",
                                "transactionHash": "0xabc123",
                                "transactionIndex": "0x0",
                                "logIndex": "0x0",
                                "removed": false
                            }
                        ],
                        "type": "0x2"
                    }
                    """);

            // when
            var result = client.getTransactionReceipt(txHash);

            // then
            var expectedLog = new EvmLog(
                    "0xcontract",
                    List.of("0xtopic1", "0xtopic2"),
                    "0xdata",
                    "0xa",
                    "0xblock",
                    "0xabc123",
                    "0x0",
                    "0x0",
                    false);
            var expected = new EvmReceipt(
                    "0xabc123",
                    "0x0",
                    "0xblock",
                    "0xa",
                    "0xsender",
                    "0xcontract",
                    "0xb000",
                    "0x7000",
                    "0x4a817c800",
                    "0x1",
                    List.of(expectedLog),
                    null,
                    "0x2");
            assertThat(result).isPresent();
            assertThat(result.get()).usingRecursiveComparison().isEqualTo(expected);
        }
    }
}
