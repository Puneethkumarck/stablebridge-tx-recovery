package com.stablebridge.txrecovery.testutil.fixtures;

import static com.stablebridge.txrecovery.infrastructure.signer.CallbackSignerAdapter.SIGNER_ENDPOINT_KEY;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;

import com.stablebridge.txrecovery.domain.recovery.model.FeeEstimate;
import com.stablebridge.txrecovery.domain.recovery.model.FeeUrgency;
import com.stablebridge.txrecovery.domain.transaction.model.UnsignedTransaction;
import com.stablebridge.txrecovery.infrastructure.client.evm.EvmEncoding;

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

    public static final byte[] SOME_EVM_PAYLOAD = EvmEncoding.encodeEip1559Transaction(
            1L, 0L,
            BigInteger.valueOf(2_000_000_000L),
            BigInteger.valueOf(30_000_000_000L),
            BigInteger.valueOf(21_000L),
            "0x742d35Cc6634C0532925a3b844Bc9e7595f2bD18",
            BigInteger.ZERO,
            new byte[]{(byte) 0xa9, 0x05, (byte) 0x9c, (byte) 0xbb});

    public static UnsignedTransaction someUnsignedTransaction(String intentId, String chain) {
        var payload = chain.toLowerCase(java.util.Locale.ROOT).contains("solana")
                ? SOME_PAYLOAD
                : SOME_EVM_PAYLOAD;
        return UnsignedTransaction.builder()
                .intentId(intentId)
                .chain(chain)
                .fromAddress("from-address")
                .toAddress("to-address")
                .payload(payload)
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
                .metadata(Map.of(SIGNER_ENDPOINT_KEY, signerEndpoint))
                .build();
    }
}
