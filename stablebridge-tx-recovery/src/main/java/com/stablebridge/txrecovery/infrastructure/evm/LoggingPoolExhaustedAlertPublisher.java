package com.stablebridge.txrecovery.infrastructure.evm;

import org.springframework.stereotype.Component;

import com.stablebridge.txrecovery.domain.address.port.PoolExhaustedAlertPublisher;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
class LoggingPoolExhaustedAlertPublisher implements PoolExhaustedAlertPublisher {

    @Override
    public void publish(String chain, String tier) {
        log.error("ALERT: Address pool exhausted for chain={} tier={}", chain, tier);
    }
}
