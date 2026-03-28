package com.stablebridge.txrecovery.infrastructure.signer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.Executors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.SSLParameters;

import com.stablebridge.txrecovery.domain.exception.CallbackSignerException;
import com.stablebridge.txrecovery.domain.transaction.model.SignedTransaction;
import com.stablebridge.txrecovery.domain.transaction.model.UnsignedTransaction;
import com.stablebridge.txrecovery.domain.transaction.port.TransactionSigner;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

@Slf4j
public class CallbackSignerAdapter implements TransactionSigner, AutoCloseable {

    public static final String SIGNER_ENDPOINT_KEY = "signerEndpoint";
    private static final String CONTENT_TYPE = "application/json";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int HTTP_OK = 200;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);
    private static final HexFormat HEX = HexFormat.of();

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final byte[] hmacSecret;
    private final Duration timeout;
    private final boolean requireHttps;

    public CallbackSignerAdapter(
            CallbackSignerProperties properties,
            ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.hmacSecret = properties.hmacSecret().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        this.timeout = properties.timeout() != null ? properties.timeout() : DEFAULT_TIMEOUT;
        this.requireHttps = properties.tls() != null && properties.tls().verify();

        var builder = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(this.timeout)
                .executor(Executors.newVirtualThreadPerTaskExecutor());

        if (requireHttps) {
            var sslParams = new SSLParameters();
            sslParams.setProtocols(new String[]{"TLSv1.3"});
            builder.sslParameters(sslParams);
        }

        this.httpClient = builder.build();
    }

    @Override
    public SignedTransaction sign(UnsignedTransaction transaction, String fromAddress) {
        var signerEndpoint = resolveSignerEndpoint(transaction);
        var requestBody = buildRequestBody(transaction, fromAddress);
        var responseBody = doPost(signerEndpoint, requestBody, transaction.intentId());

        var response = parseResponse(responseBody, transaction.intentId());

        log.debug("Callback signer completed intentId={} chain={} address={}",
                transaction.intentId(), transaction.chain(), fromAddress);

        var signedHex = response.signedTransactionBytes();
        if (signedHex == null || signedHex.isBlank()) {
            throw new CallbackSignerException(
                    "Missing signedTransactionBytes in response for intentId=%s"
                            .formatted(transaction.intentId()));
        }

        final byte[] signedPayload;
        try {
            signedPayload = HEX.parseHex(signedHex);
        } catch (IllegalArgumentException e) {
            throw new CallbackSignerException(
                    "Invalid signedTransactionBytes from callback signer for intentId=%s"
                            .formatted(transaction.intentId()), e);
        }

        return SignedTransaction.builder()
                .intentId(transaction.intentId())
                .chain(transaction.chain())
                .signedPayload(signedPayload)
                .signerAddress(fromAddress)
                .build();
    }

    private String resolveSignerEndpoint(UnsignedTransaction transaction) {
        var endpoint = transaction.metadata().get(SIGNER_ENDPOINT_KEY);
        if (endpoint == null || endpoint.isBlank()) {
            throw new CallbackSignerException(
                    "Missing signerEndpoint in transaction metadata for intentId=%s"
                            .formatted(transaction.intentId()));
        }
        if (requireHttps) {
            var uri = URI.create(endpoint);
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                throw new CallbackSignerException(
                        "Non-HTTPS signerEndpoint is not allowed for intentId=%s"
                                .formatted(transaction.intentId()));
            }
        }
        return endpoint;
    }

    private String buildRequestBody(UnsignedTransaction transaction, String fromAddress) {
        var unsignedHex = HEX.formatHex(transaction.payload());

        var bodyWithoutSignature = objectMapper.writeValueAsString(CallbackSignRequest.builder()
                .chain(transaction.chain())
                .fromAddress(fromAddress)
                .unsignedTransactionBytes(unsignedHex)
                .transactionId(transaction.intentId())
                .requestSignature("")
                .build());

        var hmac = computeHmac(bodyWithoutSignature);

        return objectMapper.writeValueAsString(CallbackSignRequest.builder()
                .chain(transaction.chain())
                .fromAddress(fromAddress)
                .unsignedTransactionBytes(unsignedHex)
                .transactionId(transaction.intentId())
                .requestSignature(hmac)
                .build());
    }

    private String computeHmac(String data) {
        try {
            var mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(hmacSecret, HMAC_ALGORITHM));
            return HEX.formatHex(mac.doFinal(data.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new CallbackSignerException("HMAC computation failed", e);
        }
    }

    private String doPost(String url, String body, String intentId) {
        try {
            var httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(timeout)
                    .header("Content-Type", CONTENT_TYPE)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            var httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (httpResponse.statusCode() != HTTP_OK) {
                throw new CallbackSignerException(
                        "HTTP %d from callback signer for intentId=%s".formatted(
                                httpResponse.statusCode(), intentId));
            }

            return httpResponse.body();
        } catch (CallbackSignerException e) {
            throw e;
        } catch (HttpTimeoutException e) {
            throw new CallbackSignerException(
                    "Timeout calling callback signer for intentId=%s".formatted(intentId), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CallbackSignerException(
                    "Interrupted calling callback signer for intentId=%s".formatted(intentId), e);
        } catch (Exception e) {
            throw new CallbackSignerException(
                    "Failed to call callback signer for intentId=%s".formatted(intentId), e);
        }
    }

    private CallbackSignResponse parseResponse(String responseBody, String intentId) {
        try {
            return objectMapper.readValue(responseBody, CallbackSignResponse.class);
        } catch (Exception e) {
            throw new CallbackSignerException(
                    "Failed to parse callback signer response for intentId=%s".formatted(intentId), e);
        }
    }

    @Override
    public void close() {
        httpClient.close();
    }
}
