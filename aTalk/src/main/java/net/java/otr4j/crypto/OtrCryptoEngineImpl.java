/*
 * otr4j, the open source java otr library.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.otr4j.crypto;

import net.java.otr4j.io.SerializationUtils;

import org.bouncycastle.crypto.BufferedBlockCipher;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.modes.SICBlockCipher;
import org.bouncycastle.crypto.params.*;
import org.bouncycastle.crypto.signers.DSASigner;
import org.bouncycastle.util.BigIntegers;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.*;
import java.security.interfaces.*;

import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.interfaces.DHPublicKey;
import javax.crypto.spec.*;

/**
 * @author George Politis
 */
public class OtrCryptoEngineImpl implements OtrCryptoEngine
{
    @Override
    public KeyPair generateDHKeyPair()
            throws OtrCryptoException
    {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("DH");
            keyPairGenerator.initialize(new DHParameterSpec(MODULUS, GENERATOR, DH_PRIVATE_KEY_MINIMUM_BIT_LENGTH));
            return keyPairGenerator.generateKeyPair();
        } catch (Exception e) {
            throw new OtrCryptoException(e);
        }
    }

    public DHPublicKey getDHPublicKey(byte[] mpiBytes)
            throws OtrCryptoException
    {
        return getDHPublicKey(new BigInteger(mpiBytes));
    }

    @Override
    public DHPublicKey getDHPublicKey(BigInteger mpi)
            throws OtrCryptoException
    {
        DHPublicKeySpec pubKeySpecs = new DHPublicKeySpec(mpi, MODULUS, GENERATOR);
        try {
            KeyFactory keyFac = KeyFactory.getInstance("DH");
            return (DHPublicKey) keyFac.generatePublic(pubKeySpecs);
        } catch (Exception e) {
            throw new OtrCryptoException(e);
        }
    }

    @Override
    public byte[] sha256Hmac(byte[] b, byte[] key)
            throws OtrCryptoException
    {
        return this.sha256Hmac(b, key, 0);
    }

    @Override
    public byte[] sha256Hmac(byte[] b, byte[] key, int length)
            throws OtrCryptoException
    {

        SecretKeySpec keyspec = new SecretKeySpec(key, "HmacSHA256");
        Mac mac;
        try {
            mac = Mac.getInstance("HmacSHA256");
        } catch (NoSuchAlgorithmException e) {
            throw new OtrCryptoException(e);
        }
        try {
            mac.init(keyspec);
        } catch (InvalidKeyException e) {
            throw new OtrCryptoException(e);
        }

        byte[] macBytes = mac.doFinal(b);

        if (length > 0) {
            byte[] bytes = new byte[length];
            ByteBuffer buff = ByteBuffer.wrap(macBytes);
            buff.get(bytes);
            return bytes;
        }
        else {
            return macBytes;
        }
    }

    @Override
    public byte[] sha1Hmac(byte[] b, byte[] key, int length)
            throws OtrCryptoException
    {
        try {
            SecretKeySpec keyspec = new SecretKeySpec(key, "HmacSHA1");
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(keyspec);

            byte[] macBytes = mac.doFinal(b);

            if (length > 0) {
                byte[] bytes = new byte[length];
                ByteBuffer buff = ByteBuffer.wrap(macBytes);
                buff.get(bytes);
                return bytes;
            }
            else {
                return macBytes;
            }
        } catch (Exception e) {
            throw new OtrCryptoException(e);
        }
    }

    @Override
    public byte[] sha256Hmac160(byte[] b, byte[] key)
            throws OtrCryptoException
    {
        return sha256Hmac(b, key, 20);
    }

    @Override
    public byte[] sha256Hash(byte[] b)
            throws OtrCryptoException
    {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            sha256.update(b, 0, b.length);
            return sha256.digest();
        } catch (Exception e) {
            throw new OtrCryptoException(e);
        }
    }

    @Override
    public byte[] sha1Hash(byte[] b)
            throws OtrCryptoException
    {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-1");
            sha256.update(b, 0, b.length);
            return sha256.digest();
        } catch (Exception e) {
            throw new OtrCryptoException(e);
        }
    }

    @Override
    public byte[] aesDecrypt(byte[] key, byte[] ctr, byte[] b)
            throws OtrCryptoException
    {
        AESEngine aesDec = new AESEngine();
        SICBlockCipher sicAesDec = new SICBlockCipher(aesDec);
        BufferedBlockCipher bufSicAesDec = new BufferedBlockCipher(sicAesDec);

        // Create initial counter value 0.
        if (ctr == null)
            ctr = new byte[AES_CTR_BYTE_LENGTH];
        bufSicAesDec.init(false, new ParametersWithIV(new KeyParameter(key), ctr));
        byte[] aesOutLwDec = new byte[b.length];
        int done = bufSicAesDec.processBytes(b, 0, b.length, aesOutLwDec, 0);
        try {
            bufSicAesDec.doFinal(aesOutLwDec, done);
        } catch (Exception e) {
            throw new OtrCryptoException(e);
        }

        return aesOutLwDec;
    }

    @Override
    public byte[] aesEncrypt(byte[] key, byte[] ctr, byte[] b)
            throws OtrCryptoException
    {
        AESEngine aesEnc = new AESEngine();
        SICBlockCipher sicAesEnc = new SICBlockCipher(aesEnc);
        BufferedBlockCipher bufSicAesEnc = new BufferedBlockCipher(sicAesEnc);

        // Create initial counter value 0.
        if (ctr == null)
            ctr = new byte[AES_CTR_BYTE_LENGTH];
        bufSicAesEnc.init(true, new ParametersWithIV(new KeyParameter(key), ctr));
        byte[] aesOutLwEnc = new byte[b.length];
        int done = bufSicAesEnc.processBytes(b, 0, b.length, aesOutLwEnc, 0);
        try {
            bufSicAesEnc.doFinal(aesOutLwEnc, done);
        } catch (Exception e) {
            throw new OtrCryptoException(e);
        }
        return aesOutLwEnc;
    }

    @Override
    public BigInteger generateSecret(PrivateKey privKey, PublicKey pubKey)
            throws OtrCryptoException
    {
        try {
            KeyAgreement ka = KeyAgreement.getInstance("DH");
            ka.init(privKey);
            ka.doPhase(pubKey, true);
            byte[] sb = ka.generateSecret();
            BigInteger s = new BigInteger(1, sb);
            return s;

        } catch (Exception e) {
            throw new OtrCryptoException(e);
        }
    }

    @Override
    public byte[] sign(byte[] b, PrivateKey privatekey)
            throws OtrCryptoException
    {
        if (!(privatekey instanceof DSAPrivateKey))
            throw new IllegalArgumentException();

        DSAParams dsaParams = ((DSAPrivateKey) privatekey).getParams();
        DSAParameters bcDSAParameters
                = new DSAParameters(dsaParams.getP(), dsaParams.getQ(), dsaParams.getG());

        DSAPrivateKey dsaPrivateKey = (DSAPrivateKey) privatekey;
        DSAPrivateKeyParameters bcDSAPrivateKeyParms
                = new DSAPrivateKeyParameters(dsaPrivateKey.getX(), bcDSAParameters);

        DSASigner dsaSigner = new DSASigner();
        dsaSigner.init(true, bcDSAPrivateKeyParms);

        BigInteger q = dsaParams.getQ();

        // Ian: Note that if you can get the standard DSA implementation you're
        // using to not hash its input, you should be able to pass it ((256-bit
        // value) mod q), (rather than truncating the 256-bit value) and all
        // should be well.
        // ref: Interop problems with libotr - DSA signature
        BigInteger bmpi = new BigInteger(1, b);
        BigInteger[] rs
                = dsaSigner.generateSignature(BigIntegers.asUnsignedByteArray(bmpi.mod(q)));

        int siglen = q.bitLength() / 4;
        int rslen = siglen / 2;
        byte[] rb = BigIntegers.asUnsignedByteArray(rs[0]);
        byte[] sb = BigIntegers.asUnsignedByteArray(rs[1]);

        // Create the final signature array, padded with zeros if necessary.
        byte[] sig = new byte[siglen];
        System.arraycopy(rb, 0, sig, rslen - rb.length, rb.length);
        System.arraycopy(sb, 0, sig, sig.length - sb.length, sb.length);
        return sig;
    }

    @Override
    public boolean verify(byte[] b, PublicKey pubKey, byte[] rs)
            throws OtrCryptoException
    {
        if (!(pubKey instanceof DSAPublicKey))
            throw new IllegalArgumentException();

        DSAParams dsaParams = ((DSAPublicKey) pubKey).getParams();
        int qlen = dsaParams.getQ().bitLength() / 8;
        ByteBuffer buff = ByteBuffer.wrap(rs);
        byte[] r = new byte[qlen];
        buff.get(r);
        byte[] s = new byte[qlen];
        buff.get(s);
        return verify(b, pubKey, r, s);
    }

    private Boolean verify(byte[] b, PublicKey pubKey, byte[] r, byte[] s)
            throws OtrCryptoException
    {
        Boolean result = verify(b, pubKey, new BigInteger(1, r), new BigInteger(1, s));
        return result;
    }

    private Boolean verify(byte[] b, PublicKey pubKey, BigInteger r, BigInteger s)
            throws OtrCryptoException
    {
        if (!(pubKey instanceof DSAPublicKey))
            throw new IllegalArgumentException();

        DSAParams dsaParams = ((DSAPublicKey) pubKey).getParams();

        BigInteger q = dsaParams.getQ();
        DSAParameters bcDSAParams = new DSAParameters(dsaParams.getP(), q, dsaParams.getG());

        DSAPublicKey dsaPrivateKey = (DSAPublicKey) pubKey;
        DSAPublicKeyParameters dsaPrivParms
                = new DSAPublicKeyParameters(dsaPrivateKey.getY(), bcDSAParams);

        // Ian: Note that if you can get the standard DSA implementation you're using to not hash
        // its input, you should be able to pass it ((256-bit value) mod q), (rather than
        // truncating the 256-bit value) and all should be well.
        // ref: Interop problems with libotr - DSA signature
        DSASigner dsaSigner = new DSASigner();
        dsaSigner.init(false, dsaPrivParms);

        BigInteger bmpi = new BigInteger(1, b);
        Boolean result
                = dsaSigner.verifySignature(BigIntegers.asUnsignedByteArray(bmpi.mod(q)), r, s);
        return result;
    }

    @Override
    public String getFingerprint(PublicKey pubKey)
            throws OtrCryptoException
    {
        byte[] b = getFingerprintRaw(pubKey);
        return SerializationUtils.byteArrayToHexString(b);
    }

    @Override
    public byte[] getFingerprintRaw(PublicKey pubKey)
            throws OtrCryptoException
    {
        byte[] b;
        try {
            byte[] bRemotePubKey = SerializationUtils.writePublicKey(pubKey);

            if (pubKey.getAlgorithm().equals("DSA")) {
                byte[] trimmed = new byte[bRemotePubKey.length - 2];
                System.arraycopy(bRemotePubKey, 2, trimmed, 0, trimmed.length);
                b = new OtrCryptoEngineImpl().sha1Hash(trimmed);
            }
            else
                b = new OtrCryptoEngineImpl().sha1Hash(bRemotePubKey);
        } catch (IOException e) {
            throw new OtrCryptoException(e);
        }
        return b;
    }
}
