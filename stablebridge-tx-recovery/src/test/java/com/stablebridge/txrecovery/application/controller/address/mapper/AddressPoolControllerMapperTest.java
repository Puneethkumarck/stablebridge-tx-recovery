package com.stablebridge.txrecovery.application.controller.address.mapper;

import static com.stablebridge.txrecovery.testutil.fixtures.AddressPoolFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class AddressPoolControllerMapperTest {

    private final AddressPoolControllerMapper mapper = Mappers.getMapper(AddressPoolControllerMapper.class);

    @Test
    void shouldMapPooledAddressToResponse() {
        // when
        var result = mapper.toResponse(SOME_REGISTERED_ADDRESS);

        // then
        assertThat(result)
                .usingRecursiveComparison()
                .isEqualTo(SOME_ADDRESS_RESPONSE);
    }

    @Test
    void shouldMapPooledAddressListToResponseList() {
        // when
        var result = mapper.toResponseList(List.of(SOME_REGISTERED_ADDRESS));

        // then
        assertThat(result).hasSize(1);
        assertThat(result.getFirst())
                .usingRecursiveComparison()
                .isEqualTo(SOME_ADDRESS_RESPONSE);
    }

    @Test
    void shouldMapDrainingAddressToResponse() {
        // when
        var result = mapper.toResponse(SOME_DRAINING_ADDRESS);

        // then
        assertThat(result)
                .usingRecursiveComparison()
                .isEqualTo(SOME_DRAINING_ADDRESS_RESPONSE);
    }

    @Test
    void shouldMapDrainResultToDrainResponse() {
        // when
        var result = mapper.toDrainResponse(SOME_DRAIN_RESULT);

        // then
        assertThat(result)
                .usingRecursiveComparison()
                .isEqualTo(SOME_DRAIN_RESPONSE);
    }
}
