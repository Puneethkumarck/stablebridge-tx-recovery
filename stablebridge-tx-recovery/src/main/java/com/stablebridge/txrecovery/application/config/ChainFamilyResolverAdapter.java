package com.stablebridge.txrecovery.application.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import com.stablebridge.txrecovery.domain.address.model.ChainFamily;
import com.stablebridge.txrecovery.domain.address.port.ChainFamilyResolver;
import com.stablebridge.txrecovery.domain.exception.UnknownChainException;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(StrProperties.class)
class ChainFamilyResolverAdapter implements ChainFamilyResolver {

    private final StrProperties strProperties;

    @Override
    public ChainFamily resolve(String chain) {
        return strProperties.chains().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(chain))
                .map(entry -> ChainFamily.valueOf(entry.getValue().chainFamily()))
                .findFirst()
                .orElseThrow(() -> new UnknownChainException(chain));
    }
}
