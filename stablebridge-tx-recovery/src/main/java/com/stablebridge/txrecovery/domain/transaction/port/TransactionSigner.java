package com.stablebridge.txrecovery.domain.transaction.port;

import com.stablebridge.txrecovery.domain.transaction.model.SignedTransaction;
import com.stablebridge.txrecovery.domain.transaction.model.UnsignedTransaction;

public interface TransactionSigner {

    SignedTransaction sign(UnsignedTransaction transaction, String fromAddress);
}
