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
package ch.imvs.sdes4j;

import java.util.Random;

/**
 * Interface to create instances of specific grammar 
 * 
 * @author Ingo Bauersachs
 */
public interface SDesFactory {
    /**
     * Creates a crypto suite instance for the grammar implementing this interface.
     * 
     * @param suite The suite name that defines the cryptographic parameters.
     * @return A crypto suite instance based on the supplied suite name.
     */
    CryptoSuite createCryptoSuite(String suite);

    /**
     * Creates a key parameter instance for the grammar implementing this interface.
     * 
     * @param keyParam The textual representation of the key parameter field.
     * @return The parsed key parameter.
     */
    KeyParam createKeyParam(String keyParam);

    /**
     * Utility method to create a typed array of <code>KeyParameter</code>s.
     * 
     * @param size The size of the array to create.
     * @return KeyParam array of the specified size.
     */
    KeyParam[] createKeyParamArray(int size);

    /**
     * Creates a session parameter instance for the grammar implementing this interface.
     * 
     * @param sessionParam The textual representation of the session parameter.
     * @return The parsed session parameter.
     */
    SessionParam createSessionParam(String sessionParam);

    /**
     * Utility method to create a typed array of <code>SessionParam</code>s.
     * 
     * @param size The size of the array to create.
     * @return SessionParam array of the specified size.
     */
    SessionParam[] createSessionParamArray(int size);

    /**
     * Creates an empty crypto attribute for the grammar implementing this interface.
     * 
     * @return Empty crypto attribute to be filled by a parser.
     */
    CryptoAttribute createCryptoAttribute();

    /**
     * Sets the random number generator to be used for generating the SDES keys.
     * 
     * @param r The random number generator.
     */
    void setRandomGenerator(Random r);
}
