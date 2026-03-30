package com.stablebridge.txrecovery.application.controller.address.mapper;

import static com.stablebridge.txrecovery.testutil.fixtures.AddressPoolControllerFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class AddressPoolControllerMapperTest {

    private final AddressPoolControllerMapper mapper = Mappers.getMapper(AddressPoolControllerMapper.class);

    @Test
    void shouldMapPooledAddressToResponse() {
        var result = mapper.toResponse(SOME_REGISTERED_ADDRESS);

        assertThat(result)
                .usingRecursiveComparison()
                .isEqualTo(SOME_ADDRESS_RESPONSE);
    }

    @Test
    void shouldMapPooledAddressListToResponseList() {
        var result = mapper.toResponseList(List.of(SOME_REGISTERED_ADDRESS));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst())
                .usingRecursiveComparison()
                .isEqualTo(SOME_ADDRESS_RESPONSE);
    }

    @Test
    void shouldMapDrainingAddressToResponse() {
        var result = mapper.toResponse(SOME_DRAINING_ADDRESS);

        assertThat(result.status()).isEqualTo("DRAINING");
        assertThat(result.inFlightCount()).isEqualTo(2);
    }
}
