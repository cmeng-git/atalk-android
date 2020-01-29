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

/**
 * Interface for grammar implementations of an identifier that describes the
 * encryption and authentication algorithms (e.g., AES_CM_128_HMAC_SHA1_80) for
 * the transport in question
 * 
 * @author Ingo Bauersachs
 */
public interface CryptoSuite {
    /**
     * Encodes the information contained in this object for use in the complete
     * crypto attribute.
     * 
     * @return The name of the crypto suite.
     */
    String encode();
}
