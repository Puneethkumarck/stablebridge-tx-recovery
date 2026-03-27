package com.stablebridge.txrecovery;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.stablebridge.txrecovery.domain.address.port.AddressPoolRepository;
import com.stablebridge.txrecovery.domain.address.port.NonceAccountPoolRepository;
import com.stablebridge.txrecovery.domain.address.port.NonceManager;
import com.stablebridge.txrecovery.domain.address.port.PoolExhaustedAlertPublisher;

@SpringBootTest
class ApplicationTest {

    @MockitoBean
    private AddressPoolRepository addressPoolRepository;

    @MockitoBean
    private NonceAccountPoolRepository nonceAccountPoolRepository;

    @MockitoBean
    private NonceManager nonceManager;

    @MockitoBean
    private PoolExhaustedAlertPublisher poolExhaustedAlertPublisher;

    @Test
    void contextLoads() {
    }
}
