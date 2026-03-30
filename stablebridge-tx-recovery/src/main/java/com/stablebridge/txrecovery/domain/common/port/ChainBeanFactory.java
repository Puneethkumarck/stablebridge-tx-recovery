package com.stablebridge.txrecovery.domain.common.port;

import com.stablebridge.txrecovery.domain.address.model.ChainFamily;
import com.stablebridge.txrecovery.domain.common.model.ChainConfig;
import com.stablebridge.txrecovery.domain.recovery.port.FeeOracle;
import com.stablebridge.txrecovery.domain.recovery.port.RecoveryStrategy;
import com.stablebridge.txrecovery.domain.transaction.port.ChainTransactionManager;
import com.stablebridge.txrecovery.domain.transaction.port.SubmissionResourceManager;

public interface ChainBeanFactory {

    ChainFamily supportedFamily();

    FeeOracle createFeeOracle(ChainConfig config);

    ChainTransactionManager createTransactionManager(ChainConfig config);

    RecoveryStrategy createRecoveryStrategy(ChainConfig config, SubmissionResourceManager resourceManager);

    SubmissionResourceManager createResourceManager(ChainConfig config);
}
