package com.stablebridge.txrecovery.infrastructure.signer;

import static com.stablebridge.txrecovery.testutil.fixtures.SignerFixtures.SOME_EVM_ADDRESS;
import static com.stablebridge.txrecovery.testutil.fixtures.SignerFixtures.SOME_EVM_CHAIN;
import static com.stablebridge.txrecovery.testutil.fixtures.SignerFixtures.SOME_INTENT_ID;
import static com.stablebridge.txrecovery.testutil.fixtures.SignerFixtures.SOME_PAYLOAD;
import static com.stablebridge.txrecovery.testutil.fixtures.SignerFixtures.SOME_SOLANA_ADDRESS;
import static com.stablebridge.txrecovery.testutil.fixtures.SignerFixtures.SOME_SOLANA_CHAIN;
import static com.stablebridge.txrecovery.testutil.fixtures.SignerFixtures.someUnsignedTransaction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Map;

import org.bouncycastle.crypto.ec.CustomNamedCurves;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.bouncycastle.math.ec.rfc8032.Ed25519;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.stablebridge.txrecovery.domain.exception.SignerKeyNotFoundException;
import com.stablebridge.txrecovery.domain.transaction.model.SignedTransaction;

class LocalKeystoreSignerTest {

    private static byte[] ed25519PrivateKey;
    private static byte[] ed25519PublicKey;
    private static byte[] secp256k1PrivateKey;
    private static BigInteger secp256k1PrivateKeyInt;
    private static org.bouncycastle.math.ec.ECPoint secp256k1PublicKeyPoint;
    private static ECDomainParameters secp256k1DomainParams;

    private LocalKeystoreSigner signer;

    @BeforeAll
    static void generateKeys() {
        var random = new SecureRandom();

        ed25519PrivateKey = new byte[Ed25519.SECRET_KEY_SIZE];
        ed25519PublicKey = new byte[Ed25519.PUBLIC_KEY_SIZE];
        random.nextBytes(ed25519PrivateKey);
        Ed25519.generatePublicKey(ed25519PrivateKey, 0, ed25519PublicKey, 0);

        var ecParams = CustomNamedCurves.getByName("secp256k1");
        secp256k1DomainParams = new ECDomainParameters(
                ecParams.getCurve(), ecParams.getG(), ecParams.getN(), ecParams.getH());
        secp256k1PrivateKeyInt = new BigInteger(256, random)
                .mod(ecParams.getN().subtract(BigInteger.ONE))
                .add(BigInteger.ONE);
        secp256k1PublicKeyPoint = ecParams.getG().multiply(secp256k1PrivateKeyInt).normalize();

        var rawBytes = secp256k1PrivateKeyInt.toByteArray();
        secp256k1PrivateKey = new byte[32];
        if (rawBytes.length > 32) {
            System.arraycopy(rawBytes, rawBytes.length - 32, secp256k1PrivateKey, 0, 32);
        } else {
            System.arraycopy(rawBytes, 0, secp256k1PrivateKey, 32 - rawBytes.length, rawBytes.length);
        }
    }

    @BeforeEach
    void setUp() {
        signer = new LocalKeystoreSigner(Map.of(
                SOME_EVM_ADDRESS, secp256k1PrivateKey,
                SOME_SOLANA_ADDRESS, ed25519PrivateKey));
    }

    @Nested
    class EvmSigning {

        @Test
        void shouldSignTransactionWithSecp256k1() {
            // given
            var transaction = someUnsignedTransaction(SOME_INTENT_ID, SOME_EVM_CHAIN);

            // when
            var result = signer.sign(transaction, SOME_EVM_ADDRESS);

            // then
            var expected = SignedTransaction.builder()
                    .intentId(SOME_INTENT_ID)
                    .chain(SOME_EVM_CHAIN)
                    .signedPayload(result.signedPayload())
                    .signerAddress(SOME_EVM_ADDRESS)
                    .build();
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);

            var r = new BigInteger(1, result.signedPayload(), 0, 32);
            var s = new BigInteger(1, result.signedPayload(), 32, 32);
            var hash = new Keccak.Digest256().digest(SOME_PAYLOAD);
            var publicKeyParam = new ECPublicKeyParameters(secp256k1PublicKeyPoint, secp256k1DomainParams);
            var verifier = new ECDSASigner();
            verifier.init(false, publicKeyParam);
            assertThat(verifier.verifySignature(hash, r, s)).isTrue();
        }

        @Test
        void shouldProduceLowSSignature() {
            // given
            var transaction = someUnsignedTransaction(SOME_INTENT_ID, SOME_EVM_CHAIN);

            // when
            var result = signer.sign(transaction, SOME_EVM_ADDRESS);

            // then
            var s = new BigInteger(1, result.signedPayload(), 32, 32);
            var halfN = CustomNamedCurves.getByName("secp256k1").getN().shiftRight(1);
            assertThat(s).isLessThanOrEqualTo(halfN);
        }

        @Test
        void shouldSignNonSolanaChainWithEcdsa() {
            // given
            var transaction = someUnsignedTransaction(SOME_INTENT_ID, "polygon");

            // when
            var result = signer.sign(transaction, SOME_EVM_ADDRESS);

            // then
            assertThat(result.signedPayload()).hasSize(65);
            var v = result.signedPayload()[64] & 0xFF;
            assertThat(v).isBetween(27, 28);
        }
    }

    @Nested
    class SolanaSigning {

        @Test
        void shouldSignTransactionWithEd25519() {
            // given
            var transaction = someUnsignedTransaction(SOME_INTENT_ID, SOME_SOLANA_CHAIN);

            // when
            var result = signer.sign(transaction, SOME_SOLANA_ADDRESS);

            // then
            var expected = SignedTransaction.builder()
                    .intentId(SOME_INTENT_ID)
                    .chain(SOME_SOLANA_CHAIN)
                    .signedPayload(result.signedPayload())
                    .signerAddress(SOME_SOLANA_ADDRESS)
                    .build();
            assertThat(result).usingRecursiveComparison().isEqualTo(expected);

            var signature = new byte[64];
            System.arraycopy(result.signedPayload(), 1, signature, 0, 64);
            assertThat(Ed25519.verify(signature, 0, ed25519PublicKey, 0,
                    SOME_PAYLOAD, 0, SOME_PAYLOAD.length)).isTrue();
        }

        @Test
        void shouldPrependSignatureCountByte() {
            // given
            var transaction = someUnsignedTransaction(SOME_INTENT_ID, SOME_SOLANA_CHAIN);

            // when
            var result = signer.sign(transaction, SOME_SOLANA_ADDRESS);

            // then
            assertThat(result.signedPayload()).hasSize(1 + 64 + SOME_PAYLOAD.length);
            assertThat(result.signedPayload()[0]).isEqualTo((byte) 0x01);
        }
    }

    @Nested
    class KeyLookup {

        @Test
        void shouldThrowWhenAddressNotFound() {
            // given
            var transaction = someUnsignedTransaction(SOME_INTENT_ID, SOME_EVM_CHAIN);
            var unknownAddress = "0xUnknownAddress";

            // when/then
            assertThatThrownBy(() -> signer.sign(transaction, unknownAddress))
                    .isInstanceOf(SignerKeyNotFoundException.class)
                    .hasMessage("No private key found for address: " + unknownAddress);
        }
    }
}
