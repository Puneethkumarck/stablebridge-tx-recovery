package com.stablebridge.txrecovery;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.stablebridge.txrecovery.domain.address.port.AddressPoolRepository;
import com.stablebridge.txrecovery.domain.address.port.NonceManager;

@SpringBootTest
class ApplicationTest {

    @MockitoBean
    @SuppressWarnings("unused")
    private NonceManager nonceManager;

    @MockitoBean
    @SuppressWarnings("unused")
    private AddressPoolRepository addressPoolRepository;

    @Test
    void contextLoads() {}
}
