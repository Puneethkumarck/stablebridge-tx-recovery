package com.stablebridge.txrecovery.domain.address.port;

import com.stablebridge.txrecovery.domain.address.model.ChainFamily;

public interface ChainFamilyResolver {

    ChainFamily resolve(String chain);
}
