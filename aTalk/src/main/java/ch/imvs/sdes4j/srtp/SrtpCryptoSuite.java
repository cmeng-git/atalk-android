/*
 * SDES4J
 * Java implementation of SDES (Security Descriptions for Media Streams,
 * RFC 4568).
 * 
 * Copyright (C) 2011 FHNW
 *   University of Applied Sciences Northwestern Switzerland (FHNW)
 *   School of Engineering
 *   Institute of Mobile and Distributed Systems (IMVS)
 *   http://sdes4j.imvs.ch
 * 
 * Distributable under LGPL license, see terms of license at gnu.org.
 */
package ch.imvs.sdes4j.srtp;

import ch.imvs.sdes4j.CryptoSuite;

/**
 * Crypto suite details for the SRTP grammar.
 * 
 * @author Ingo Bauersachs
 */
public class SrtpCryptoSuite implements CryptoSuite {
    public static final String AES_256_CM_HMAC_SHA1_32 = "AES_256_CM_HMAC_SHA1_32";
    public static final String AES_256_CM_HMAC_SHA1_80 = "AES_256_CM_HMAC_SHA1_80";
    public static final String AES_192_CM_HMAC_SHA1_32 = "AES_192_CM_HMAC_SHA1_32";
    public static final String AES_192_CM_HMAC_SHA1_80 = "AES_192_CM_HMAC_SHA1_80";
    public static final String SEED_128_GCM_96 = "SEED_128_GCM_96";
    public static final String SEED_128_CCM_80 = "SEED_128_CCM_80";
    public static final String SEED_CTR_128_HMAC_SHA1_80 = "SEED_CTR_128_HMAC_SHA1_80";
    public static final String F8_128_HMAC_SHA1_80 = "F8_128_HMAC_SHA1_80";
    public static final String AES_CM_128_HMAC_SHA1_32 = "AES_CM_128_HMAC_SHA1_32";
    public static final String AES_CM_128_HMAC_SHA1_80 = "AES_CM_128_HMAC_SHA1_80";

    public final static int ENCRYPTION_AES128_CM = 1;
    public final static int ENCRYPTION_AES128_F8 = 2;
    public final static int ENCRYPTION_SEED128_CTR = 5;
    public final static int ENCRYPTION_SEED128_CCM_80 = 6;
    public final static int ENCRYPTION_SEED128_GCM_96 = 7;
    public final static int ENCRYPTION_AES192_CM = 8;
    public final static int ENCRYPTION_AES256_CM = 9;

    public final static int HASH_HMAC_SHA1 = 1;
    public final static int HASH_SEED128_CCM_80 = 3;
    public final static int HASH_SEED128_GCM_96 = 4;

    private final String suite;

    private int encryptionAlgorithm;
    private int hashAlgoritm;
    private int encKeyLength;
    private int saltKeyLength;
    private int srtpAuthTagLength;
    private int srtcpAuthTagLength;
    private int srtpAuthKeyLength;
    private int srtcpAuthKeyLength;
    private long srtpLifetime;
    private long srtcpLifetime;

    public SrtpCryptoSuite(String suite) {
        this.suite = suite;
        // as per http://www.iana.org/assignments/sdp-security-descriptions
        if (suite.equals(AES_CM_128_HMAC_SHA1_80)) {
            encryptionAlgorithm = ENCRYPTION_AES128_CM;
            hashAlgoritm = HASH_HMAC_SHA1;
            encKeyLength = 128;
            saltKeyLength = 112;
            srtpAuthTagLength = 80;
            srtcpAuthTagLength = 80;
            srtpAuthKeyLength = 160;
            srtcpAuthKeyLength = 160;
            srtpLifetime = 0x1000000000000L;
            srtcpLifetime = 0x80000000L;
        }
        else if (suite.equals(AES_CM_128_HMAC_SHA1_32)) {
            encryptionAlgorithm = ENCRYPTION_AES128_CM;
            hashAlgoritm = HASH_HMAC_SHA1;
            encKeyLength = 128;
            saltKeyLength = 112;
            srtpAuthTagLength = 32;
            srtcpAuthTagLength = 80;
            srtpAuthKeyLength = 160;
            srtcpAuthKeyLength = 160;
            srtpLifetime = 0x1000000000000L;
            srtcpLifetime = 0x80000000L;
        }
        else if (suite.equals(F8_128_HMAC_SHA1_80)) {
            encryptionAlgorithm = ENCRYPTION_AES128_F8;
            hashAlgoritm = HASH_HMAC_SHA1;
            encKeyLength = 128;
            saltKeyLength = 112;
            srtpAuthTagLength = 80;
            srtcpAuthTagLength = 80;
            srtpAuthKeyLength = 160;
            srtcpAuthKeyLength = 160;
            srtpLifetime = 0x1000000000000L;
            srtcpLifetime = 0x80000000L;
        }
        // FIXME all that SEED stuff is not precisely declared in RFC5669
        else if (suite.equals(SEED_CTR_128_HMAC_SHA1_80)) {
            encryptionAlgorithm = ENCRYPTION_SEED128_CTR;
            hashAlgoritm = HASH_HMAC_SHA1;
            encKeyLength = 128;
            saltKeyLength = 128;
            srtpAuthTagLength = 80;
            srtcpAuthTagLength = 80;
            srtpAuthKeyLength = -1;
            srtcpAuthKeyLength = -1;
            srtpLifetime = 0x1000000000000L;
            srtcpLifetime = 0x80000000L;
            throw new UnsupportedOperationException("SEED parameters are not known for sure");
        }
        else if (suite.equals(SEED_128_CCM_80)) {
            encryptionAlgorithm = ENCRYPTION_SEED128_CCM_80;
            hashAlgoritm = HASH_SEED128_CCM_80;
            encKeyLength = 128;
            saltKeyLength = 128;
            srtpAuthTagLength = 80;
            srtcpAuthTagLength = 80;
            srtpAuthKeyLength = -1;
            srtcpAuthKeyLength = -1;
            srtpLifetime = 0x1000000000000L;
            srtcpLifetime = 0x80000000L;
            throw new UnsupportedOperationException("SEED parameters are not known for sure");
        }
        else if (suite.equals(SEED_128_GCM_96)) {
            encryptionAlgorithm = ENCRYPTION_SEED128_GCM_96;
            hashAlgoritm = HASH_SEED128_GCM_96;
            encKeyLength = 128;
            saltKeyLength = 128;
            srtpAuthTagLength = 96;
            srtcpAuthTagLength = 96;
            srtpAuthKeyLength = -1;
            srtcpAuthKeyLength = -1;
            srtpLifetime = 0x1000000000000L;
            srtcpLifetime = 0x80000000L;
            throw new UnsupportedOperationException("SEED parameters are not known for sure");
        }
        else if (suite.equals(AES_192_CM_HMAC_SHA1_80)) {
            encryptionAlgorithm = ENCRYPTION_AES192_CM;
            hashAlgoritm = HASH_HMAC_SHA1;
            encKeyLength = 192;
            saltKeyLength = 112;
            srtpAuthTagLength = 80;
            srtcpAuthTagLength = 80;
            srtpAuthKeyLength = 160;
            srtcpAuthKeyLength = 160;
            srtpLifetime = 0x80000000L;
            srtcpLifetime = 0x80000000L;
        }
        else if (suite.equals(AES_192_CM_HMAC_SHA1_32)) {
            encryptionAlgorithm = ENCRYPTION_AES192_CM;
            hashAlgoritm = HASH_HMAC_SHA1;
            encKeyLength = 192;
            saltKeyLength = 112;
            srtpAuthTagLength = 32;
            srtcpAuthTagLength = 80;
            srtpAuthKeyLength = 160;
            srtcpAuthKeyLength = 160;
            srtpLifetime = 0x80000000L;
            srtcpLifetime = 0x80000000L;
        }
        else if (suite.equals(AES_256_CM_HMAC_SHA1_80)) {
            encryptionAlgorithm = ENCRYPTION_AES256_CM;
            hashAlgoritm = HASH_HMAC_SHA1;
            encKeyLength = 256;
            saltKeyLength = 112;
            srtpAuthTagLength = 80;
            srtcpAuthTagLength = 80;
            srtpAuthKeyLength = 160;
            srtcpAuthKeyLength = 160;
            srtpLifetime = 0x80000000L;
            srtcpLifetime = 0x80000000L;
        }
        else if (suite.equals(AES_256_CM_HMAC_SHA1_32)) {
            encryptionAlgorithm = ENCRYPTION_AES256_CM;
            hashAlgoritm = HASH_HMAC_SHA1;
            encKeyLength = 256;
            saltKeyLength = 112;
            srtpAuthTagLength = 32;
            srtcpAuthTagLength = 80;
            srtpAuthKeyLength = 160;
            srtcpAuthKeyLength = 160;
            srtpLifetime = 0x80000000L;
            srtcpLifetime = 0x80000000L;
        }
        else
            throw new IllegalArgumentException("Unknown crypto suite");
    }

    public int getEncryptionAlgorithm() {
        return encryptionAlgorithm;
    }

    public int getHashAlgorithm() {
        return hashAlgoritm;
    }

    public int getEncKeyLength() {
        return encKeyLength;
    }

    public int getSaltKeyLength() {
        return saltKeyLength;
    }

    public int getSrtpAuthTagLength() {
        return srtpAuthTagLength;
    }

    public int getSrtcpAuthTagLength() {
        return srtcpAuthTagLength;
    }

    public int getSrtpAuthKeyLength() {
        return srtpAuthKeyLength;
    }

    public int getSrtcpAuthKeyLength() {
        return srtcpAuthKeyLength;
    }

    public long getSrtpLifetime() {
        return srtpLifetime;
    }

    public long getSrtcpLifetime() {
        return srtcpLifetime;
    }

    @Override
    public String encode() {
        return suite;
    }

    @Override
    public boolean equals(Object obj) {
        if(obj instanceof SrtpCryptoSuite && obj != null)
            return suite.equals(((SrtpCryptoSuite)obj).suite);
        return false;
    }

    @Override
    public int hashCode() {
        return suite.hashCode();
    }
}
