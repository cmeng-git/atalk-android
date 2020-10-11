/*
 * otr4j, the open source java otr library.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */

package net.java.otr4j.crypto;

import java.math.BigInteger;
import java.security.*;

import javax.crypto.interfaces.DHPublicKey;

/**
 * @author George Politis
 * @author Eng Chong Meng
 */
public interface OtrCryptoEngine
{
	String MODULUS_TEXT =
			"00FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74020BBEA63B139B22514A08798E3404DDEF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7EDEE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3DC2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F83655D23DCA3AD961C62F356208552BB9ED529077096966D670C354E4ABC9804F1746C08CA237327FFFFFFFFFFFFFFFF";
	BigInteger MODULUS = new BigInteger(MODULUS_TEXT, 16);
	BigInteger BIGINTEGER_TWO = BigInteger.valueOf(2);
	BigInteger MODULUS_MINUS_TWO = MODULUS.subtract(BIGINTEGER_TWO);

	BigInteger GENERATOR = new BigInteger("2", 10);

	int AES_KEY_BYTE_LENGTH = 16;
	int AES_CTR_BYTE_LENGTH = 16;

	int SHA256_HMAC_KEY_BYTE_LENGTH = 32;
	int DH_PRIVATE_KEY_MINIMUM_BIT_LENGTH = 320;

	int DSA_PUB_TYPE = 0;
	int DSA_KEY_LENGTH = 1024;

	KeyPair generateDHKeyPair()
			throws OtrCryptoException;

	DHPublicKey getDHPublicKey(byte[] mpiBytes)
			throws OtrCryptoException;

	DHPublicKey getDHPublicKey(BigInteger mpi)
			throws OtrCryptoException;

	byte[] sha256Hmac(byte[] b, byte[] key)
			throws OtrCryptoException;

	byte[] sha256Hmac(byte[] b, byte[] key, int length)
			throws OtrCryptoException;

	byte[] sha1Hmac(byte[] b, byte[] key, int length)
			throws OtrCryptoException;

	byte[] sha256Hmac160(byte[] b, byte[] key)
			throws OtrCryptoException;

	byte[] sha256Hash(byte[] b)
			throws OtrCryptoException;

	byte[] sha1Hash(byte[] b)
			throws OtrCryptoException;

	byte[] aesDecrypt(byte[] key, byte[] ctr, byte[] b)
			throws OtrCryptoException;

	byte[] aesEncrypt(byte[] key, byte[] ctr, byte[] b)
			throws OtrCryptoException;

	BigInteger generateSecret(PrivateKey privKey, PublicKey pubKey)
			throws OtrCryptoException;

	byte[] sign(byte[] b, PrivateKey privatekey)
			throws OtrCryptoException;

	boolean verify(byte[] b, PublicKey pubKey, byte[] rs)
			throws OtrCryptoException;

	String getFingerprint(PublicKey pubKey)
			throws OtrCryptoException;

	byte[] getFingerprintRaw(PublicKey pubKey)
			throws OtrCryptoException;
}
