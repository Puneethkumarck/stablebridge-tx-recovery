package com.stablebridge.txrecovery.domain.address;

import static com.stablebridge.txrecovery.testutil.fixtures.AddressPoolControllerFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
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
import com.stablebridge.txrecovery.domain.address.port.AddressPoolRepository;
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

    @Spy
    private final Clock clock = Clock.fixed(SOME_REGISTERED_AT, ZoneOffset.UTC);

    @InjectMocks
    private AddressPoolService addressPoolService;

    @Nested
    class Register {

        @Test
        void shouldRegisterEvmAddressWithOnChainNonce() {
            given(addressPoolRepository.findByAddressAndChain(SOME_EVM_ADDRESS, SOME_CHAIN))
                    .willReturn(Optional.empty());
            given(onChainNonceProvider.getTransactionCount(SOME_EVM_ADDRESS, SOME_CHAIN))
                    .willReturn(BigInteger.valueOf(42));
            given(addressPoolRepository.save(argThat(a ->
                    a.address().equals(SOME_EVM_ADDRESS) && a.currentNonce() == 42)))
                    .willAnswer(inv -> inv.getArgument(0));

            var result = addressPoolService.register(
                    SOME_EVM_ADDRESS, SOME_CHAIN, ChainFamily.EVM, AddressTier.HOT, SOME_SIGNER_ENDPOINT);

            assertThat(result.address()).isEqualTo(SOME_EVM_ADDRESS);
            assertThat(result.currentNonce()).isEqualTo(42);
            assertThat(result.status()).isEqualTo(AddressStatus.ACTIVE);
            assertThat(result.chainFamily()).isEqualTo(ChainFamily.EVM);
        }

        @Test
        void shouldRegisterSolanaAddressWithZeroNonce() {
            given(addressPoolRepository.findByAddressAndChain("SolAddr123", "solana"))
                    .willReturn(Optional.empty());
            given(addressPoolRepository.save(argThat(a ->
                    a.address().equals("SolAddr123") && a.currentNonce() == 0)))
                    .willAnswer(inv -> inv.getArgument(0));

            var result = addressPoolService.register(
                    "SolAddr123", "solana", ChainFamily.SOLANA, AddressTier.HOT, SOME_SIGNER_ENDPOINT);

            assertThat(result.currentNonce()).isEqualTo(0);
            assertThat(result.chainFamily()).isEqualTo(ChainFamily.SOLANA);
        }

        @Test
        void shouldThrowDuplicateAddressException() {
            given(addressPoolRepository.findByAddressAndChain(SOME_EVM_ADDRESS, SOME_CHAIN))
                    .willReturn(Optional.of(SOME_REGISTERED_ADDRESS));

            assertThatThrownBy(() -> addressPoolService.register(
                    SOME_EVM_ADDRESS, SOME_CHAIN, ChainFamily.EVM, AddressTier.HOT, SOME_SIGNER_ENDPOINT))
                    .isInstanceOf(DuplicateAddressException.class)
                    .hasMessageContaining(SOME_EVM_ADDRESS)
                    .hasMessageContaining(SOME_CHAIN);
        }
    }

    @Nested
    class ListAddresses {

        @Test
        void shouldReturnFilteredAddresses() {
            given(addressPoolRepository.findByFilters(SOME_CHAIN, AddressTier.HOT, AddressStatus.ACTIVE))
                    .willReturn(List.of(SOME_REGISTERED_ADDRESS));

            var result = addressPoolService.list(SOME_CHAIN, AddressTier.HOT, AddressStatus.ACTIVE);

            assertThat(result).hasSize(1).containsOnly(SOME_REGISTERED_ADDRESS);
        }

        @Test
        void shouldReturnAllAddressesWhenNoFilters() {
            given(addressPoolRepository.findByFilters(null, null, null))
                    .willReturn(List.of(SOME_REGISTERED_ADDRESS, SOME_DRAINING_ADDRESS));

            var result = addressPoolService.list(null, null, null);

            assertThat(result).hasSize(2);
        }
    }

    @Nested
    class Drain {

        @Test
        void shouldTransitionToDrainingWhenInFlightPositive() {
            var activeWithInFlight = SOME_REGISTERED_ADDRESS.toBuilder().inFlightCount(3).build();
            given(addressPoolRepository.findByAddressAndChain(SOME_EVM_ADDRESS, SOME_CHAIN))
                    .willReturn(Optional.of(activeWithInFlight));
            given(addressPoolRepository.save(argThat(a -> a.status() == AddressStatus.DRAINING)))
                    .willAnswer(inv -> inv.getArgument(0));

            var result = addressPoolService.drain(SOME_EVM_ADDRESS, SOME_CHAIN);

            assertThat(result.status()).isEqualTo(AddressStatus.DRAINING);
        }

        @Test
        void shouldTransitionDirectlyToRetiredWhenNoInFlight() {
            given(addressPoolRepository.findByAddressAndChain(SOME_EVM_ADDRESS, SOME_CHAIN))
                    .willReturn(Optional.of(SOME_REGISTERED_ADDRESS));
            given(addressPoolRepository.save(argThat(a -> a.status() == AddressStatus.RETIRED)))
                    .willAnswer(inv -> inv.getArgument(0));

            var result = addressPoolService.drain(SOME_EVM_ADDRESS, SOME_CHAIN);

            assertThat(result.status()).isEqualTo(AddressStatus.RETIRED);
        }

        @Test
        void shouldThrowAddressNotFoundException() {
            given(addressPoolRepository.findByAddressAndChain(SOME_EVM_ADDRESS, SOME_CHAIN))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> addressPoolService.drain(SOME_EVM_ADDRESS, SOME_CHAIN))
                    .isInstanceOf(AddressNotFoundException.class)
                    .hasMessageContaining(SOME_EVM_ADDRESS);
        }

        @Test
        void shouldThrowInvalidAddressStateExceptionWhenAlreadyDraining() {
            given(addressPoolRepository.findByAddressAndChain(SOME_EVM_ADDRESS, SOME_CHAIN))
                    .willReturn(Optional.of(SOME_DRAINING_ADDRESS));

            assertThatThrownBy(() -> addressPoolService.drain(SOME_EVM_ADDRESS, SOME_CHAIN))
                    .isInstanceOf(InvalidAddressStateException.class)
                    .hasMessageContaining("DRAINING");
        }
    }

    @Nested
    class SyncNonce {

        @Test
        void shouldSyncNonceFromChain() {
            given(addressPoolRepository.findByAddressAndChain(SOME_EVM_ADDRESS, SOME_CHAIN))
                    .willReturn(Optional.of(SOME_REGISTERED_ADDRESS));
            given(onChainNonceProvider.getTransactionCount(SOME_EVM_ADDRESS, SOME_CHAIN))
                    .willReturn(BigInteger.valueOf(100));
            given(addressPoolRepository.save(argThat(a -> a.currentNonce() == 100)))
                    .willAnswer(inv -> inv.getArgument(0));

            var result = addressPoolService.syncNonce(SOME_EVM_ADDRESS, SOME_CHAIN);

            assertThat(result.currentNonce()).isEqualTo(100);
        }

        @Test
        void shouldThrowAddressNotFoundException() {
            given(addressPoolRepository.findByAddressAndChain(SOME_EVM_ADDRESS, SOME_CHAIN))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> addressPoolService.syncNonce(SOME_EVM_ADDRESS, SOME_CHAIN))
                    .isInstanceOf(AddressNotFoundException.class)
                    .hasMessageContaining(SOME_EVM_ADDRESS);
        }
    }
}
