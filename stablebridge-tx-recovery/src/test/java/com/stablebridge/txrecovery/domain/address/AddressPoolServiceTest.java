package com.stablebridge.txrecovery.domain.address;

import static com.stablebridge.txrecovery.testutil.TestUtils.eqIgnoring;
import static com.stablebridge.txrecovery.testutil.fixtures.AddressPoolFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.math.BigInteger;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stablebridge.txrecovery.domain.address.model.AddressStatus;
import com.stablebridge.txrecovery.domain.address.model.AddressTier;
import com.stablebridge.txrecovery.domain.address.model.ChainFamily;
import com.stablebridge.txrecovery.domain.address.model.PooledAddress;
import com.stablebridge.txrecovery.domain.address.port.AddressPoolRepository;
import com.stablebridge.txrecovery.domain.address.port.ChainFamilyResolver;
import com.stablebridge.txrecovery.domain.address.port.OnChainNonceProvider;
import com.stablebridge.txrecovery.domain.exception.AddressNotFoundException;
import com.stablebridge.txrecovery.domain.exception.DuplicateAddressException;
import com.stablebridge.txrecovery.domain.exception.InvalidAddressStateException;

@ExtendWith(MockitoExtension.class)
class AddressPoolServiceTest {

    @Mock
    private AddressPoolRepository addressPoolRepository;

    @Mock
    private OnChainNonceProvider onChainNonceProvider;

    @Mock
    private ChainFamilyResolver chainFamilyResolver;

    @Spy
    private final Clock clock = Clock.fixed(SOME_REGISTERED_AT, ZoneOffset.UTC);

    @InjectMocks
    private AddressPoolService addressPoolService;

    @Nested
    class Register {

        @Test
        void shouldRegisterEvmAddressWithOnChainNonce() {
            // given
            given(addressPoolRepository.findByAddressAndChain(SOME_EVM_ADDRESS, SOME_CHAIN))
                    .willReturn(Optional.empty());
            given(chainFamilyResolver.resolve(SOME_CHAIN))
                    .willReturn(ChainFamily.EVM);
            given(onChainNonceProvider.getTransactionCount(SOME_EVM_ADDRESS, SOME_CHAIN))
                    .willReturn(BigInteger.valueOf(42));

            var expected = PooledAddress.builder()
                    .address(SOME_EVM_ADDRESS)
                    .chain(SOME_CHAIN)
                    .chainFamily(ChainFamily.EVM)
                    .tier(AddressTier.HOT)
                    .status(AddressStatus.ACTIVE)
                    .currentNonce(42)
                    .inFlightCount(0)
                    .signerEndpoint(SOME_SIGNER_ENDPOINT)
                    .registeredAt(SOME_REGISTERED_AT)
                    .build();

            given(addressPoolRepository.save(eqIgnoring(expected, "id")))
                    .willAnswer(inv -> inv.getArgument(0));

            // when
            var result = addressPoolService.register(
                    SOME_EVM_ADDRESS, SOME_CHAIN, AddressTier.HOT, SOME_SIGNER_ENDPOINT);

            // then
            assertThat(result)
                    .usingRecursiveComparison()
                    .ignoringFields("id")
                    .isEqualTo(expected);
        }

        @Test
        void shouldRegisterSolanaAddressWithZeroNonce() {
            // given
            given(addressPoolRepository.findByAddressAndChain("SolAddr123", "solana"))
                    .willReturn(Optional.empty());
            given(chainFamilyResolver.resolve("solana"))
                    .willReturn(ChainFamily.SOLANA);

            var expected = PooledAddress.builder()
                    .address("SolAddr123")
                    .chain("solana")
                    .chainFamily(ChainFamily.SOLANA)
                    .tier(AddressTier.HOT)
                    .status(AddressStatus.ACTIVE)
                    .currentNonce(0)
                    .inFlightCount(0)
                    .signerEndpoint(SOME_SIGNER_ENDPOINT)
                    .build();

            given(addressPoolRepository.save(eqIgnoring(expected, "id", "registeredAt")))
                    .willAnswer(inv -> inv.getArgument(0));

            // when
            var result = addressPoolService.register(
                    "SolAddr123", "solana", AddressTier.HOT, SOME_SIGNER_ENDPOINT);

            // then
            assertThat(result)
                    .usingRecursiveComparison()
                    .ignoringFields("id", "registeredAt")
                    .isEqualTo(expected);
        }

        @Test
        void shouldThrowDuplicateAddressException() {
            // given
            given(addressPoolRepository.findByAddressAndChain(SOME_EVM_ADDRESS, SOME_CHAIN))
                    .willReturn(Optional.of(SOME_REGISTERED_ADDRESS));

            // when/then
            assertThatThrownBy(() -> addressPoolService.register(
                    SOME_EVM_ADDRESS, SOME_CHAIN, AddressTier.HOT, SOME_SIGNER_ENDPOINT))
                    .isInstanceOf(DuplicateAddressException.class)
                    .hasMessageContaining(SOME_EVM_ADDRESS)
                    .hasMessageContaining(SOME_CHAIN);
        }
    }

    @Nested
    class ListAddresses {

        @Test
        void shouldReturnFilteredAddresses() {
            // given
            given(addressPoolRepository.findByFilters(SOME_CHAIN, AddressTier.HOT, AddressStatus.ACTIVE))
                    .willReturn(List.of(SOME_REGISTERED_ADDRESS));

            // when
            var result = addressPoolService.list(SOME_CHAIN, AddressTier.HOT, AddressStatus.ACTIVE);

            // then
            assertThat(result).hasSize(1).containsOnly(SOME_REGISTERED_ADDRESS);
        }

        @Test
        void shouldReturnAllAddressesWhenNoFilters() {
            // given
            given(addressPoolRepository.findByFilters(null, null, null))
                    .willReturn(List.of(SOME_REGISTERED_ADDRESS, SOME_DRAINING_ADDRESS));

            // when
            var result = addressPoolService.list(null, null, null);

            // then
            assertThat(result).hasSize(2).containsOnly(SOME_REGISTERED_ADDRESS, SOME_DRAINING_ADDRESS);
        }
    }

    @Nested
    class Drain {

        @Test
        void shouldTransitionToDrainingWhenInFlightPositive() {
            // given
            var activeWithInFlight = SOME_REGISTERED_ADDRESS.toBuilder().inFlightCount(3).build();
            given(addressPoolRepository.findByAddressAndChain(SOME_EVM_ADDRESS, SOME_CHAIN))
                    .willReturn(Optional.of(activeWithInFlight));

            var expected = activeWithInFlight.toBuilder()
                    .status(AddressStatus.DRAINING)
                    .build();

            given(addressPoolRepository.save(eqIgnoring(expected)))
                    .willAnswer(inv -> inv.getArgument(0));

            // when
            var result = addressPoolService.drain(SOME_EVM_ADDRESS, SOME_CHAIN);

            // then
            assertThat(result)
                    .usingRecursiveComparison()
                    .isEqualTo(expected);
        }

        @Test
        void shouldTransitionDirectlyToRetiredWhenNoInFlight() {
            // given
            given(addressPoolRepository.findByAddressAndChain(SOME_EVM_ADDRESS, SOME_CHAIN))
                    .willReturn(Optional.of(SOME_REGISTERED_ADDRESS));

            var expected = SOME_REGISTERED_ADDRESS.toBuilder()
                    .status(AddressStatus.RETIRED)
                    .retiredAt(SOME_REGISTERED_AT)
                    .build();

            given(addressPoolRepository.save(eqIgnoring(expected)))
                    .willAnswer(inv -> inv.getArgument(0));

            // when
            var result = addressPoolService.drain(SOME_EVM_ADDRESS, SOME_CHAIN);

            // then
            assertThat(result)
                    .usingRecursiveComparison()
                    .ignoringFields("retiredAt")
                    .isEqualTo(expected);
        }

        @Test
        void shouldThrowAddressNotFoundException() {
            // given
            given(addressPoolRepository.findByAddressAndChain(SOME_EVM_ADDRESS, SOME_CHAIN))
                    .willReturn(Optional.empty());

            // when/then
            assertThatThrownBy(() -> addressPoolService.drain(SOME_EVM_ADDRESS, SOME_CHAIN))
                    .isInstanceOf(AddressNotFoundException.class)
                    .hasMessageContaining(SOME_EVM_ADDRESS);
        }

        @Test
        void shouldThrowInvalidAddressStateExceptionWhenAlreadyDraining() {
            // given
            given(addressPoolRepository.findByAddressAndChain(SOME_EVM_ADDRESS, SOME_CHAIN))
                    .willReturn(Optional.of(SOME_DRAINING_ADDRESS));

            // when/then
            assertThatThrownBy(() -> addressPoolService.drain(SOME_EVM_ADDRESS, SOME_CHAIN))
                    .isInstanceOf(InvalidAddressStateException.class)
                    .hasMessageContaining("DRAINING");
        }
    }

    @Nested
    class SyncNonce {

        @Test
        void shouldSyncNonceFromChain() {
            // given
            given(addressPoolRepository.findByAddressAndChain(SOME_EVM_ADDRESS, SOME_CHAIN))
                    .willReturn(Optional.of(SOME_REGISTERED_ADDRESS));
            given(onChainNonceProvider.getTransactionCount(SOME_EVM_ADDRESS, SOME_CHAIN))
                    .willReturn(BigInteger.valueOf(100));

            var expectedSaved = SOME_REGISTERED_ADDRESS.toBuilder()
                    .currentNonce(100)
                    .build();

            given(addressPoolRepository.save(eqIgnoring(expectedSaved)))
                    .willAnswer(inv -> inv.getArgument(0));

            // when
            var result = addressPoolService.syncNonce(SOME_EVM_ADDRESS, SOME_CHAIN);

            // then
            assertThat(result.previousNonce()).isEqualTo(42);
            assertThat(result.currentNonce()).isEqualTo(100);
            assertThat(result.address())
                    .usingRecursiveComparison()
                    .isEqualTo(expectedSaved);
        }

        @Test
        void shouldThrowAddressNotFoundException() {
            // given
            given(addressPoolRepository.findByAddressAndChain(SOME_EVM_ADDRESS, SOME_CHAIN))
                    .willReturn(Optional.empty());

            // when/then
            assertThatThrownBy(() -> addressPoolService.syncNonce(SOME_EVM_ADDRESS, SOME_CHAIN))
                    .isInstanceOf(AddressNotFoundException.class)
                    .hasMessageContaining(SOME_EVM_ADDRESS);
        }
    }
}
