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
 * The key-param provides keying material for the crypto-suite in question. It
 * consists of a method and the actual keying information
 * 
 * @author Ingo Bauersachs
 */
public interface KeyParam {
    /**
     * Gets the method name that defines the type of the key information. Only
     * one method is defined, namely, "inline", which indicates that the actual
     * keying material is provided in the key-info field itself.
     * 
     * @return <code>inline</code>
     */
    String getKeyMethod();

    /**
     * Encodes the information contained in this object for use in the complete
     * crypto attribute.
     * 
     * @return Textual representation of the key parameter.
     */
    String encode();
}
