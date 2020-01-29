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

/**
 * SRTP and SRTCP packet payloads are encrypted by default. The
 * UNENCRYPTED_SRTCP and UNENCRYPTED_SRTP session parameters modify the default
 * behavior of the crypto-suites with which they are used.
 * 
 * UNENCRYPTED_SRTCP signals that the SRTP packet payloads are not encrypted.
 * 
 * @author Ingo Bauersachs
 */
public class PlainSrtcpSessionParam extends SrtpSessionParam {
    private static final String UNENCRYPTED_SRTCP = "UNENCRYPTED_SRTCP";

    @Override
    public String encode() {
        return UNENCRYPTED_SRTCP;
    }

    @Override
    public int hashCode() {
        return UNENCRYPTED_SRTCP.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return UNENCRYPTED_SRTCP.equals(obj);
    }
}
