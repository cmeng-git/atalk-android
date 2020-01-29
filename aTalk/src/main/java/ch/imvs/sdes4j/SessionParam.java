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
 * Interface for Session parameters that are specific to a given transport.
 * 
 * @author Ingo Bauersachs
 */
public interface SessionParam {
    /**
     * Encodes the information contained in this object for use in the complete
     * crypto attribute.
     * 
     * @return Textual representation of the session parameter.
     */
    String encode();
}
