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
 * SRTP and SRTCP packet payloads are authenticated by default. The
 * UNAUTHENTICATED_SRTP session parameter signals that SRTP messages are not
 * authenticated. Use of UNAUTHENTICATED_SRTP is NOT RECOMMENDED (see Security
 * Considerations).
 * 
 * @author Ingo Bauersachs
 */
public class NoAuthSessionParam extends SrtpSessionParam {
    private static final String UNAUTHENTICATED_SRTP = "UNAUTHENTICATED_SRTP";

    @Override
    public String encode() {
        return UNAUTHENTICATED_SRTP;
    }

    @Override
    public int hashCode() {
        return UNAUTHENTICATED_SRTP.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return UNAUTHENTICATED_SRTP.equals(obj);
    }
}
