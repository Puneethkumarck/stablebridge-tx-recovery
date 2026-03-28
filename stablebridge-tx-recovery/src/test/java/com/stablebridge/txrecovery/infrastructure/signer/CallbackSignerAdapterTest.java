package com.stablebridge.txrecovery.infrastructure.signer;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.stablebridge.txrecovery.testutil.fixtures.SignerFixtures.SOME_EVM_ADDRESS;
import static com.stablebridge.txrecovery.testutil.fixtures.SignerFixtures.SOME_EVM_CHAIN;
import static com.stablebridge.txrecovery.testutil.fixtures.SignerFixtures.SOME_HMAC_SECRET;
import static com.stablebridge.txrecovery.testutil.fixtures.SignerFixtures.SOME_INTENT_ID;
import static com.stablebridge.txrecovery.testutil.fixtures.SignerFixtures.someUnsignedTransactionWithEndpoint;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.HexFormat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.stablebridge.txrecovery.domain.exception.CallbackSignerException;
import com.stablebridge.txrecovery.domain.transaction.model.SignedTransaction;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

class CallbackSignerAdapterTest {

    private static final HexFormat HEX = HexFormat.of();
    private static final byte[] SIGNED_BYTES = {0x0A, 0x0B, 0x0C, 0x0D};
    private static final String SIGNED_HEX = HEX.formatHex(SIGNED_BYTES);
    private static final String SOME_TX_HASH = "0xabc123def456";
    private static final String SOME_SIGNATURE = "sigvalue";

    private WireMockServer wireMockServer;
    private CallbackSignerAdapter adapter;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();

        var properties = CallbackSignerProperties.builder()
                .hmacSecret(SOME_HMAC_SECRET)
                .timeout(Duration.ofSeconds(5))
                .tls(CallbackSignerProperties.TlsProperties.builder().verify(false).build())
                .build();

        var objectMapper = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();

        adapter = new CallbackSignerAdapter(properties, objectMapper);
    }

    @AfterEach
    void tearDown() {
        adapter.close();
        wireMockServer.stop();
    }

    @Nested
    class SuccessfulSigning {

        @Test
        void shouldSignTransactionViaCallbackEndpoint() {
            // given
            var responseJson = """
                    {"signedTransactionBytes":"%s","transactionHash":"%s","signature":"%s"}"""
                    .formatted(SIGNED_HEX, SOME_TX_HASH, SOME_SIGNATURE);

            wireMockServer.stubFor(post(urlEqualTo("/sign"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(responseJson)));

            var signerEndpoint = wireMockServer.baseUrl() + "/sign";
            var transaction = someUnsignedTransactionWithEndpoint(
                    SOME_INTENT_ID, SOME_EVM_CHAIN, signerEndpoint);

            // when
            var result = adapter.sign(transaction, SOME_EVM_ADDRESS);

            // then
            var expected = SignedTransaction.builder()
                    .intentId(SOME_INTENT_ID)
                    .chain(SOME_EVM_CHAIN)
                    .signedPayload(SIGNED_BYTES)
                    .signerAddress(SOME_EVM_ADDRESS)
                    .build();
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void shouldSendHmacSignatureInRequest() {
            // given
            var responseJson = """
                    {"signedTransactionBytes":"%s","transactionHash":"%s","signature":"%s"}"""
                    .formatted(SIGNED_HEX, SOME_TX_HASH, SOME_SIGNATURE);

            wireMockServer.stubFor(post(urlEqualTo("/sign"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(responseJson)));

            var signerEndpoint = wireMockServer.baseUrl() + "/sign";
            var transaction = someUnsignedTransactionWithEndpoint(
                    SOME_INTENT_ID, SOME_EVM_CHAIN, signerEndpoint);

            // when
            adapter.sign(transaction, SOME_EVM_ADDRESS);

            // then
            wireMockServer.verify(postRequestedFor(urlEqualTo("/sign"))
                    .withHeader("Content-Type", equalTo("application/json")));
        }

        @Test
        void shouldIncludeChainAndAddressInRequestBody() {
            // given
            var responseJson = """
                    {"signedTransactionBytes":"%s","transactionHash":"%s","signature":"%s"}"""
                    .formatted(SIGNED_HEX, SOME_TX_HASH, SOME_SIGNATURE);

            wireMockServer.stubFor(post(urlEqualTo("/sign"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(responseJson)));

            var signerEndpoint = wireMockServer.baseUrl() + "/sign";
            var transaction = someUnsignedTransactionWithEndpoint(
                    SOME_INTENT_ID, SOME_EVM_CHAIN, signerEndpoint);

            // when
            adapter.sign(transaction, SOME_EVM_ADDRESS);

            // then
            wireMockServer.verify(postRequestedFor(urlEqualTo("/sign"))
                    .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock
                            .containing("\"chain\":\"" + SOME_EVM_CHAIN + "\""))
                    .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock
                            .containing("\"fromAddress\":\"" + SOME_EVM_ADDRESS + "\""))
                    .withRequestBody(com.github.tomakehurst.wiremock.client.WireMock
                            .containing("\"transactionId\":\"" + SOME_INTENT_ID + "\"")));
        }
    }

    @Nested
    class ErrorHandling {

        @Test
        void shouldThrowOnHttpError() {
            // given
            wireMockServer.stubFor(post(urlEqualTo("/sign"))
                    .willReturn(aResponse().withStatus(500).withBody("Internal error")));

            var signerEndpoint = wireMockServer.baseUrl() + "/sign";
            var transaction = someUnsignedTransactionWithEndpoint(
                    SOME_INTENT_ID, SOME_EVM_CHAIN, signerEndpoint);

            // when/then
            assertThatThrownBy(() -> adapter.sign(transaction, SOME_EVM_ADDRESS))
                    .isInstanceOf(CallbackSignerException.class)
                    .hasMessageContaining("HTTP 500");
        }

        @Test
        void shouldThrowOnTimeout() {
            // given
            var properties = CallbackSignerProperties.builder()
                    .hmacSecret(SOME_HMAC_SECRET)
                    .timeout(Duration.ofMillis(100))
                    .tls(CallbackSignerProperties.TlsProperties.builder().verify(false).build())
                    .build();
            var objectMapper = JsonMapper.builder()
                    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .build();
            var shortTimeoutAdapter = new CallbackSignerAdapter(properties, objectMapper);

            wireMockServer.stubFor(post(urlEqualTo("/sign"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withFixedDelay(5000)
                            .withBody("{}")));

            var signerEndpoint = wireMockServer.baseUrl() + "/sign";
            var transaction = someUnsignedTransactionWithEndpoint(
                    SOME_INTENT_ID, SOME_EVM_CHAIN, signerEndpoint);

            // when/then
            assertThatThrownBy(() -> shortTimeoutAdapter.sign(transaction, SOME_EVM_ADDRESS))
                    .isInstanceOf(CallbackSignerException.class)
                    .hasMessageContaining("Timeout");

            shortTimeoutAdapter.close();
        }

        @Test
        void shouldThrowOnAuthFailure() {
            // given
            wireMockServer.stubFor(post(urlEqualTo("/sign"))
                    .willReturn(aResponse().withStatus(401).withBody("Unauthorized")));

            var signerEndpoint = wireMockServer.baseUrl() + "/sign";
            var transaction = someUnsignedTransactionWithEndpoint(
                    SOME_INTENT_ID, SOME_EVM_CHAIN, signerEndpoint);

            // when/then
            assertThatThrownBy(() -> adapter.sign(transaction, SOME_EVM_ADDRESS))
                    .isInstanceOf(CallbackSignerException.class)
                    .hasMessageContaining("HTTP 401");
        }

        @Test
        void shouldThrowOnMalformedResponse() {
            // given
            wireMockServer.stubFor(post(urlEqualTo("/sign"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("not-json")));

            var signerEndpoint = wireMockServer.baseUrl() + "/sign";
            var transaction = someUnsignedTransactionWithEndpoint(
                    SOME_INTENT_ID, SOME_EVM_CHAIN, signerEndpoint);

            // when/then
            assertThatThrownBy(() -> adapter.sign(transaction, SOME_EVM_ADDRESS))
                    .isInstanceOf(CallbackSignerException.class)
                    .hasMessageContaining("Failed to parse");
        }
    }
}
