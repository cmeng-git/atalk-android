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
 * SRTP defines the SRTP-WINDOW-SIZE [RFC3711, Section 3.3.2] parameter to
 * protect against replay attacks. The Window Size Hint (WSH) session parameter
 * provides a hint for how big this window should be to work satisfactorily.
 * 
 * The minimum value is 64 [RFC3711]; however, this value may be considered too
 * low for some applications (e.g., video).
 * 
 * @author Ingo Bauersachs
 */
public class WshSessionParam extends SrtpSessionParam {
    private int wsh;

    /**
     * Creates a new instance of this class from an integer.
     * 
     * @param wsh The size of the window hint.
     */
    public WshSessionParam(int wsh) {
        if (wsh < 64)
            throw new IllegalArgumentException("Minimum size is 64");
        this.wsh = wsh;
    }

    /**
     * Creates a new instance of this class from the textual representation.
     * 
     * @param param The textual representation of the WSH parameter.
     */
    public WshSessionParam(String param) {
        wsh = Integer.valueOf(param.split("=")[1]);
        if (wsh < 64)
            throw new IllegalArgumentException("Minimum size is 64");
    }

    /**
     * Gets the size of the window hint.
     * 
     * @return the size of the window hint.
     */
    public int getWindowSizeHint() {
        return wsh;
    }

    @Override
    public String encode() {
        return "WSH=" + wsh;
    }
}
