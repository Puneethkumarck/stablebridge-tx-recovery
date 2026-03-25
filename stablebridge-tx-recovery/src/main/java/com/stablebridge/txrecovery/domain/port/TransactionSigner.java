package com.stablebridge.txrecovery.domain.port;

import com.stablebridge.txrecovery.domain.model.SignedTransaction;
import com.stablebridge.txrecovery.domain.model.UnsignedTransaction;

public interface TransactionSigner {

    SignedTransaction sign(UnsignedTransaction transaction, String fromAddress);
}
