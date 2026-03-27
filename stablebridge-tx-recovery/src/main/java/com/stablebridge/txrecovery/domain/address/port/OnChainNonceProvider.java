package com.stablebridge.txrecovery.domain.address.port;

import java.math.BigInteger;

public interface OnChainNonceProvider {

    BigInteger getTransactionCount(String address, String chain);
}
