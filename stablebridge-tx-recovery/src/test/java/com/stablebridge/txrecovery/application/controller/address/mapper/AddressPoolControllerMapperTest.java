package com.stablebridge.txrecovery.application.controller.address.mapper;

import static com.stablebridge.txrecovery.testutil.fixtures.AddressPoolFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.stablebridge.txrecovery.domain.address.model.AddressStatus;
import com.stablebridge.txrecovery.domain.address.model.AddressTier;
import com.stablebridge.txrecovery.domain.exception.InvalidParameterException;

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

    @Test
    void shouldMapNonceSyncResultToResponse() {
        // when
        var result = mapper.toNonceSyncResponse(SOME_NONCE_SYNC_RESULT);

        // then
        assertThat(result)
                .usingRecursiveComparison()
                .isEqualTo(SOME_NONCE_SYNC_RESPONSE);
    }

    @Test
    void shouldParseTierFromValidString() {
        // when
        var result = mapper.parseTier("HOT");

        // then
        assertThat(result).isEqualTo(AddressTier.HOT);
    }

    @Test
    void shouldThrowInvalidParameterExceptionForInvalidTier() {
        // when/then
        assertThatThrownBy(() -> mapper.parseTier("INVALID"))
                .isInstanceOf(InvalidParameterException.class)
                .hasMessageContaining("tier");
    }

    @Test
    void shouldParseStatusFromValidString() {
        // when
        var result = mapper.parseStatus("ACTIVE");

        // then
        assertThat(result).isEqualTo(AddressStatus.ACTIVE);
    }

    @Test
    void shouldThrowInvalidParameterExceptionForInvalidStatus() {
        // when/then
        assertThatThrownBy(() -> mapper.parseStatus("INVALID"))
                .isInstanceOf(InvalidParameterException.class)
                .hasMessageContaining("status");
    }
}
