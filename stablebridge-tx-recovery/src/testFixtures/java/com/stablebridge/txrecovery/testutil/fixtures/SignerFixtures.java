package com.stablebridge.txrecovery.testutil.fixtures;

import java.math.BigDecimal;
import java.util.Map;

import com.stablebridge.txrecovery.domain.recovery.model.FeeEstimate;
import com.stablebridge.txrecovery.domain.recovery.model.FeeUrgency;
import com.stablebridge.txrecovery.domain.transaction.model.UnsignedTransaction;

public final class SignerFixtures {

    private SignerFixtures() {}

    public static final String SOME_INTENT_ID = "intent-123";
    public static final String SOME_EVM_CHAIN = "ethereum";
    public static final String SOME_SOLANA_CHAIN = "solana-mainnet";
    public static final String SOME_EVM_ADDRESS = "0xTestEvmAddress";
    public static final String SOME_SOLANA_ADDRESS = "solana-test-address";
    public static final String SOME_HMAC_SECRET = "test-hmac-secret-key-for-signing";
    public static final String SOME_SIGNER_ENDPOINT = "https://localhost:%d/sign";
    public static final byte[] SOME_PAYLOAD = {0x01, 0x02, 0x03, 0x04};

    public static UnsignedTransaction someUnsignedTransaction(String intentId, String chain) {
        return UnsignedTransaction.builder()
                .intentId(intentId)
                .chain(chain)
                .fromAddress("from-address")
                .toAddress("to-address")
                .payload(SOME_PAYLOAD)
                .feeEstimate(FeeEstimate.builder()
                        .estimatedCost(BigDecimal.ZERO)
                        .denomination("ETH")
                        .urgency(FeeUrgency.MEDIUM)
                        .build())
                .build();
    }

    public static UnsignedTransaction someUnsignedTransactionWithEndpoint(
            String intentId, String chain, String signerEndpoint) {
        return UnsignedTransaction.builder()
                .intentId(intentId)
                .chain(chain)
                .fromAddress("from-address")
                .toAddress("to-address")
                .payload(SOME_PAYLOAD)
                .feeEstimate(FeeEstimate.builder()
                        .estimatedCost(BigDecimal.ZERO)
                        .denomination("ETH")
                        .urgency(FeeUrgency.MEDIUM)
                        .build())
                .metadata(Map.of("signerEndpoint", signerEndpoint))
                .build();
    }
}
