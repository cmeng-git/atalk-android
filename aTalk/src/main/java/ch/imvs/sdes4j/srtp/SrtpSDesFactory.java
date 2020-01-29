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

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Random;

import ch.imvs.sdes4j.*;

/**
 * Factory for the SRTP grammar of RFC4568.
 * 
 * @author Ingo Bauersachs
 */
public class SrtpSDesFactory implements SDesFactory {
    private Random r = null;

    /**
     * Creates an SRTP crypto attribute with the specified parameters, for use in an SDP.
     * <p>
     * If no random generator is set with {@link #setRandomGenerator(Random)} then the
     * SHA1PRNG, or if not available, the system's default {@link SecureRandom} will be used.
     * 
     * @param tag decimal number used as an identifier for a particular crypto attribute
     * @param keyAlg identifier that describes the encryption and authentication algorithms
     * @return SRTP crypto attribute without session parameters.
     */
    public SrtpCryptoAttribute createCryptoAttribute(int tag, String keyAlg) {
        return createCryptoAttribute(tag, keyAlg, null);
    }

    /**
     * Creates an SRTP crypto attribute with the specified parameters, for use in an SDP.
     * 
     * @param tag decimal number used as an identifier for a particular crypto attribute
     * @param keyAlg identifier that describes the encryption and authentication algorithms
     * @param params Session parameters for the crypto attribute
     * @return SRTP crypto attribute without session parameters.
     */
    public SrtpCryptoAttribute createCryptoAttribute(int tag, String keyAlg, SrtpSessionParam[] params) {
        SrtpCryptoSuite suite = createCryptoSuite(keyAlg);
        byte[] keyData = new byte[(suite.getEncKeyLength() + suite.getSaltKeyLength()) / 8];
        getRandom().nextBytes(keyData);
        SrtpKeyParam key = new SrtpKeyParam(
                SrtpKeyParam.KEYMETHOD_INLINE,
                keyData,
                0, 0, 0
        );
        return new SrtpCryptoAttribute(tag, suite, new SrtpKeyParam[] { key }, params);
    }
    
    private Random getRandom(){
        if(r == null){
            try {
                r = SecureRandom.getInstance("SHA1PRNG");
            }
            catch (NoSuchAlgorithmException e) {
                r = new SecureRandom();
            }
        }
        return r;
    }

    @Override
    public void setRandomGenerator(Random r) {
        this.r = r;
    }

    @Override
    public SrtpCryptoAttribute createCryptoAttribute() {
        return new SrtpCryptoAttribute();
    }

    @Override
    public SrtpCryptoSuite createCryptoSuite(String suite) {
        return new SrtpCryptoSuite(suite);
    }

    @Override
    public SrtpKeyParam createKeyParam(String keyParam) {
        return new SrtpKeyParam(keyParam);
    }

    @Override
    public SrtpKeyParam[] createKeyParamArray(int size) {
        return new SrtpKeyParam[size];
    }

    @Override
    public SrtpSessionParam createSessionParam(String sessionParam) {
        return SrtpSessionParam.create(sessionParam);
    }

    @Override
    public SrtpSessionParam[] createSessionParamArray(int size) {
        return new SrtpSessionParam[size];
    }
}
