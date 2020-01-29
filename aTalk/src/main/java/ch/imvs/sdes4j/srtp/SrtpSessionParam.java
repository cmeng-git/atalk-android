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

import ch.imvs.sdes4j.SessionParam;

/**
 * Base class for SRTP specific session parameters.
 * 
 * @author Ingo Bauersachs
 */
public abstract class SrtpSessionParam implements SessionParam {
    SrtpSessionParam() {
    }

    /**
     * Creates instances from the text based representation of SRTP session parameters. 
     * 
     * @param param The text based representation of a session parameter.
     * @return The instance of a SRTP session parameter.
     */
    public static SrtpSessionParam create(String param) {
        if (param.startsWith("KDR="))
            return new KdrSessionParam(param);
        else if (param.equals("UNENCRYPTED_SRTP"))
            return new PlainSrtpSessionParam();
        else if (param.equals("UNENCRYPTED_SRTCP"))
            return new PlainSrtcpSessionParam();
        else if (param.equals("UNAUTHENTICATED_SRTP"))
            return new NoAuthSessionParam();
        else if (param.startsWith("FEC_ORDER="))
            return new FecOrderSessionParam(param);
        else if (param.startsWith("FEC_KEY="))
            return new FecKeySessionParam(param);
        else if (param.startsWith("WSH="))
            return new WshSessionParam(param);

        throw new IllegalArgumentException("Unknown session parameter");
    }
}
