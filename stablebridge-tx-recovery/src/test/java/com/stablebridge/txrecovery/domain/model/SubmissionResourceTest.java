package com.stablebridge.txrecovery.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SubmissionResourceTest {

    @Test
    void shouldCreateEvmSubmissionResource() {
        // when
        var resource = EvmSubmissionResource.builder()
                .chain("ethereum")
                .fromAddress("0x742d35Cc6634C0532925a3b844Bc9e7595f2bD18")
                .nonce(42)
                .tier(AddressTier.HOT)
                .build();

        // then
        var expected = EvmSubmissionResource.builder()
                .chain("ethereum")
                .fromAddress("0x742d35Cc6634C0532925a3b844Bc9e7595f2bD18")
                .nonce(42)
                .tier(AddressTier.HOT)
                .build();

        assertThat(resource).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldCreateSolanaSubmissionResource() {
        // when
        var resource = SolanaSubmissionResource.builder()
                .chain("solana")
                .fromAddress("5eykt4UsFv8P8NJdTREpY1vzqKqZKvdpKuc147dw2N9d")
                .nonceAccountAddress("9WzDXwBbmkg8ZTbNMqUxvQRAyrZzDsGYdLVL9zYtAWWM")
                .nonceValue("abc123nonce")
                .build();

        // then
        var expected = SolanaSubmissionResource.builder()
                .chain("solana")
                .fromAddress("5eykt4UsFv8P8NJdTREpY1vzqKqZKvdpKuc147dw2N9d")
                .nonceAccountAddress("9WzDXwBbmkg8ZTbNMqUxvQRAyrZzDsGYdLVL9zYtAWWM")
                .nonceValue("abc123nonce")
                .build();

        assertThat(resource).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldAccessChainViaSubmissionResourceInterface() {
        // given
        SubmissionResource evmResource = EvmSubmissionResource.builder()
                .chain("base")
                .fromAddress("0xabc")
                .nonce(1)
                .tier(AddressTier.PRIORITY)
                .build();
        SubmissionResource solanaResource = SolanaSubmissionResource.builder()
                .chain("solana")
                .fromAddress("SolAddr123")
                .nonceAccountAddress("NonceAcct456")
                .nonceValue("nonce789")
                .build();

        // when / then
        assertThat(evmResource.chain()).isEqualTo("base");
        assertThat(solanaResource.chain()).isEqualTo("solana");
    }

    @Test
    void shouldAccessFromAddressViaSubmissionResourceInterface() {
        // given
        SubmissionResource evmResource = EvmSubmissionResource.builder()
                .chain("polygon")
                .fromAddress("0xEvmAddr")
                .nonce(5)
                .tier(AddressTier.COLD)
                .build();
        SubmissionResource solanaResource = SolanaSubmissionResource.builder()
                .chain("solana")
                .fromAddress("SolAddr")
                .nonceAccountAddress("NonceAcct")
                .nonceValue("nonce")
                .build();

        // when / then
        assertThat(evmResource.fromAddress()).isEqualTo("0xEvmAddr");
        assertThat(solanaResource.fromAddress()).isEqualTo("SolAddr");
    }

    @Test
    void shouldPatternMatchSubmissionResourceVariants() {
        // given
        SubmissionResource evmResource = EvmSubmissionResource.builder()
                .chain("ethereum")
                .fromAddress("0x123")
                .nonce(10)
                .tier(AddressTier.HOT)
                .build();
        SubmissionResource solanaResource = SolanaSubmissionResource.builder()
                .chain("solana")
                .fromAddress("Sol123")
                .nonceAccountAddress("Nonce456")
                .nonceValue("val789")
                .build();

        // when / then
        assertThat(describeResource(evmResource)).isEqualTo("evm");
        assertThat(describeResource(solanaResource)).isEqualTo("solana");
    }

    @Test
    void shouldThrowNullPointerException_whenEvmChainIsNull() {
        // when / then
        assertThatThrownBy(() -> EvmSubmissionResource.builder()
                .chain(null)
                .fromAddress("0x123")
                .nonce(1)
                .tier(AddressTier.HOT)
                .build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowNullPointerException_whenEvmFromAddressIsNull() {
        // when / then
        assertThatThrownBy(() -> EvmSubmissionResource.builder()
                .chain("ethereum")
                .fromAddress(null)
                .nonce(1)
                .tier(AddressTier.HOT)
                .build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowNullPointerException_whenEvmTierIsNull() {
        // when / then
        assertThatThrownBy(() -> EvmSubmissionResource.builder()
                .chain("ethereum")
                .fromAddress("0x123")
                .nonce(1)
                .tier(null)
                .build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowNullPointerException_whenSolanaChainIsNull() {
        // when / then
        assertThatThrownBy(() -> SolanaSubmissionResource.builder()
                .chain(null)
                .fromAddress("Sol123")
                .nonceAccountAddress("Nonce456")
                .nonceValue("val789")
                .build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowNullPointerException_whenSolanaFromAddressIsNull() {
        // when / then
        assertThatThrownBy(() -> SolanaSubmissionResource.builder()
                .chain("solana")
                .fromAddress(null)
                .nonceAccountAddress("Nonce456")
                .nonceValue("val789")
                .build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowNullPointerException_whenSolanaNonceAccountAddressIsNull() {
        // when / then
        assertThatThrownBy(() -> SolanaSubmissionResource.builder()
                .chain("solana")
                .fromAddress("Sol123")
                .nonceAccountAddress(null)
                .nonceValue("val789")
                .build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowNullPointerException_whenSolanaNonceValueIsNull() {
        // when / then
        assertThatThrownBy(() -> SolanaSubmissionResource.builder()
                .chain("solana")
                .fromAddress("Sol123")
                .nonceAccountAddress("Nonce456")
                .nonceValue(null)
                .build())
                .isInstanceOf(NullPointerException.class);
    }

    private String describeResource(SubmissionResource resource) {
        return switch (resource) {
            case EvmSubmissionResource _ -> "evm";
            case SolanaSubmissionResource _ -> "solana";
        };
    }
}
