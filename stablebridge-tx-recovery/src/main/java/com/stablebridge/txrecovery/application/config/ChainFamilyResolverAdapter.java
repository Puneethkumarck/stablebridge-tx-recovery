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
        var chainProperties = strProperties.chains().get(chain);
        if (chainProperties == null) {
            throw new UnknownChainException(chain);
        }
        return ChainFamily.valueOf(chainProperties.chainFamily());
    }
}
