package com.stablebridge.txrecovery.application.config;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.stablebridge.txrecovery.domain.recovery.port.ChainConfigProvider;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class ChainConfigProviderAdapter implements ChainConfigProvider {

    private final StrProperties strProperties;

    @Override
    public boolean isChainEnabled(String chain) {
        var chainProps = strProperties.chains().get(chain);
        return chainProps != null && chainProps.enabled();
    }

    @Override
    public Set<String> enabledChains() {
        return strProperties.chains().entrySet().stream()
                .filter(e -> e.getValue().enabled())
                .map(java.util.Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }
}
