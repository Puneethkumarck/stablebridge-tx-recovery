package com.stablebridge.txrecovery.application.config;

import java.util.Locale;
import java.util.Map;
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
        var normalized = normalize(chain);
        var chainProps = strProperties.chains().entrySet().stream()
                .filter(e -> normalize(e.getKey()).equals(normalized))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
        return chainProps != null && chainProps.enabled();
    }

    @Override
    public Set<String> enabledChains() {
        return strProperties.chains().entrySet().stream()
                .filter(e -> e.getValue().enabled())
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String normalize(String chain) {
        return chain.strip().toLowerCase(Locale.ROOT);
    }
}
