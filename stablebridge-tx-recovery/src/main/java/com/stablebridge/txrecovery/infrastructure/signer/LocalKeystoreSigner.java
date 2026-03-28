package com.stablebridge.txrecovery.infrastructure.signer;

import java.math.BigInteger;
import java.util.Locale;
import java.util.Map;

import org.bouncycastle.crypto.ec.CustomNamedCurves;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.math.ec.rfc8032.Ed25519;

import com.stablebridge.txrecovery.domain.transaction.model.SignedTransaction;
import com.stablebridge.txrecovery.domain.transaction.model.UnsignedTransaction;
import com.stablebridge.txrecovery.domain.transaction.port.TransactionSigner;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class LocalKeystoreSigner implements TransactionSigner {

    private static final int ECDSA_SIGNATURE_LENGTH = 65;
    private static final int ED25519_SIGNATURE_LENGTH = 64;
    private static final int KEY_SIZE_32 = 32;
    private static final int ETHEREUM_V_OFFSET = 27;

    private final Map<String, byte[]> privateKeys;

    LocalKeystoreSigner(Map<String, byte[]> privateKeys) {
        this.privateKeys = Map.copyOf(privateKeys);
    }

    @Override
    public SignedTransaction sign(UnsignedTransaction transaction, String fromAddress) {
        var privateKey = privateKeys.get(fromAddress);
        if (privateKey == null) {
            throw new IllegalArgumentException("No private key found for address: " + fromAddress);
        }

        var signedPayload = isSolanaChain(transaction.chain())
                ? signEd25519(transaction.payload(), privateKey)
                : signEcdsa(transaction.payload(), privateKey);

        log.debug("Signed transaction intentId={} chain={} address={}",
                transaction.intentId(), transaction.chain(), fromAddress);

        return SignedTransaction.builder()
                .intentId(transaction.intentId())
                .chain(transaction.chain())
                .signedPayload(signedPayload)
                .signerAddress(fromAddress)
                .build();
    }

    private static boolean isSolanaChain(String chain) {
        return chain.toLowerCase(Locale.ROOT).contains("solana");
    }

    private static byte[] signEcdsa(byte[] payload, byte[] privateKeyBytes) {
        var ecParams = CustomNamedCurves.getByName("secp256k1");
        var domainParams = new ECDomainParameters(
                ecParams.getCurve(), ecParams.getG(), ecParams.getN(), ecParams.getH());

        var d = new BigInteger(1, privateKeyBytes);
        var privateKeyParam = new ECPrivateKeyParameters(d, domainParams);

        var digest = new Keccak.Digest256();
        var hash = digest.digest(payload);

        var signer = new ECDSASigner();
        signer.init(true, privateKeyParam);
        var components = signer.generateSignature(hash);
        var r = components[0];
        var s = components[1];

        var halfN = ecParams.getN().shiftRight(1);
        if (s.compareTo(halfN) > 0) {
            s = ecParams.getN().subtract(s);
        }

        var publicKeyPoint = ecParams.getG().multiply(d).normalize();
        var recId = computeRecoveryId(domainParams, r, s, hash, publicKeyPoint);

        var result = new byte[ECDSA_SIGNATURE_LENGTH];
        encodeBigInteger(r, result, 0);
        encodeBigInteger(s, result, KEY_SIZE_32);
        result[64] = (byte) (ETHEREUM_V_OFFSET + recId);

        return result;
    }

    private static byte[] signEd25519(byte[] payload, byte[] privateKey) {
        var signature = new byte[ED25519_SIGNATURE_LENGTH];
        Ed25519.sign(privateKey, 0, payload, 0, payload.length, signature, 0);

        var result = new byte[ED25519_SIGNATURE_LENGTH + payload.length];
        System.arraycopy(signature, 0, result, 0, ED25519_SIGNATURE_LENGTH);
        System.arraycopy(payload, 0, result, ED25519_SIGNATURE_LENGTH, payload.length);

        return result;
    }

    private static int computeRecoveryId(
            ECDomainParameters domain, BigInteger r, BigInteger s, byte[] hash, ECPoint publicKey) {
        var n = domain.getN();
        var e = new BigInteger(1, hash);
        var prime = domain.getCurve().getField().getCharacteristic();

        for (var recId = 0; recId < 4; recId++) {
            var x = r.add(BigInteger.valueOf(recId / 2).multiply(n));
            if (x.compareTo(prime) >= 0) {
                continue;
            }

            var rPoint = decompressPoint(domain.getCurve(), x, (recId & 1) == 1);
            if (!rPoint.multiply(n).isInfinity()) {
                continue;
            }

            var rInv = r.modInverse(n);
            var recovered = rPoint.multiply(s)
                    .subtract(domain.getG().multiply(e))
                    .multiply(rInv)
                    .normalize();

            if (recovered.equals(publicKey)) {
                return recId;
            }
        }

        throw new IllegalStateException("Could not compute ECDSA recovery id");
    }

    private static ECPoint decompressPoint(ECCurve curve, BigInteger x, boolean yOdd) {
        var encoded = new byte[KEY_SIZE_32 + 1];
        encoded[0] = (byte) (yOdd ? 0x03 : 0x02);
        encodeBigInteger(x, encoded, 1);
        return curve.decodePoint(encoded);
    }

    private static void encodeBigInteger(BigInteger value, byte[] dest, int offset) {
        var bytes = value.toByteArray();
        if (bytes.length == KEY_SIZE_32) {
            System.arraycopy(bytes, 0, dest, offset, KEY_SIZE_32);
        } else if (bytes.length > KEY_SIZE_32) {
            System.arraycopy(bytes, bytes.length - KEY_SIZE_32, dest, offset, KEY_SIZE_32);
        } else {
            System.arraycopy(bytes, 0, dest, offset + KEY_SIZE_32 - bytes.length, bytes.length);
        }
    }
}
