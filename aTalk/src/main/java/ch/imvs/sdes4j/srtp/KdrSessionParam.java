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
 * KDR specifies the Key Derivation Rate, as described in Section 4.3.1 of
 * [RFC3711].
 * 
 * The value n MUST be a decimal integer in the set {1,2,...,24}, which denotes
 * a power of 2 from 2^1 to 2^24, inclusive; leading zeroes MUST NOT be used.
 * The SRTP key derivation rate controls how frequently a new session key is
 * derived from an SRTP master key(s) [RFC3711] given in the declaration. When
 * the key derivation rate is not specified (i.e., the KDR parameter is
 * omitted), a single initial key derivation is performed [RFC3711].
 * 
 * @author Ingo Bauersachs
 */
public class KdrSessionParam extends SrtpSessionParam {
    private int kdr;

    /**
     * Creates a new instance of this class from a known derivation rate.
     * 
     * @param kdr The key derivation rate.
     */
    public KdrSessionParam(int kdr) {
        if (kdr < 0 || kdr > 24)
            throw new IllegalArgumentException("kdr must be in range 0..24 inclusive");
        this.kdr = kdr;
    }

    /**
     * Creates a new instance of this class from the textual representation.
     * 
     * @param param The textual representation of the key derivation rate parameter.
     */
    public KdrSessionParam(String param) {
        kdr = Integer.valueOf(param.substring("KDR=".length()));
        if (kdr < 0 || kdr > 24)
            throw new IllegalArgumentException("kdr must be in range 0..24 inclusive");
    }

    /**
     * The key derivation rate as encoded in the session parameters.
     * 
     * @return decimal integer in the set {1,2,...,24}
     */
    public int getKeyDerivationRate() {
        return kdr;
    }

    /**
     * The key derivation rate in its exponentiated form.
     * 
     * @return integer in the range from 2 to 16'777'216.
     */
    public int getKeyDerivationRateExpanded() {
        return (int) Math.pow(2, kdr);
    }

    @Override
    public String encode() {
        return "KDR=" + String.valueOf(kdr);
    }
}
