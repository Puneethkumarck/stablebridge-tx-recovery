package com.stablebridge.txrecovery.domain.recovery.port;

import java.util.Set;

public interface ChainConfigProvider {

    boolean isChainEnabled(String chain);

    Set<String> enabledChains();
}
