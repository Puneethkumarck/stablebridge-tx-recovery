package com.stablebridge.txrecovery.infrastructure.signer;

import lombok.Builder;

@Builder(toBuilder = true)
record CallbackSignRequest(
        String chain,
        String fromAddress,
        String unsignedTransactionBytes,
        String transactionId,
        String requestSignature) {
}
