package com.stablebridge.txrecovery.testutil.stubs;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.github.tomakehurst.wiremock.WireMockServer;

public final class EvmRpcStubs {

    private EvmRpcStubs() {}

    public static void stubJsonRpcResponse(WireMockServer server, String method, String resultJson) {
        var responseBody = """
                {"jsonrpc": "2.0", "id": 1, "result": %s}
                """
                .formatted(resultJson);

        server.stubFor(post(urlEqualTo("/"))
                .withRequestBody(containing(method))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseBody)));
    }
}
