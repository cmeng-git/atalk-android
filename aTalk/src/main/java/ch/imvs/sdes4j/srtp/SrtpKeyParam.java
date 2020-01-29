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

import org2.apache.commons.codec.binary.Base64;
import org2.apache.commons.codec.binary.StringUtils;

import ch.imvs.sdes4j.KeyParam;

/**
 * SRTP security descriptions define the use of the "inline" key method. Use of
 * any other keying method (e.g., URL) for SRTP security descriptions is for
 * further study.
 * 
 * The "inline" type of key contains the keying material (master key and salt)
 * and all policy related to that master key, including how long it can be used
 * (lifetime) and whether it uses a master key identifier (MKI) to associate an
 * incoming SRTP packet with a particular master key.
 * 
 * @author Ingo Bauersachs
 * 
 */
public class SrtpKeyParam implements KeyParam {
    /**
     * Constant for the <code>inline</code> key method.
     */
    public final static String KEYMETHOD_INLINE = "inline";

    private final String keyMethod = KEYMETHOD_INLINE;
    private byte[] key;
    private int lifetime;
    private int mki;
    private int mkiLength;

    /**
     * Creates a new instance of this class from known parameters.
     * 
     * @param keyMethod The key method for this key parameter. Only
     *            {@value #KEYMETHOD_INLINE} is currently supported.
     * @param key Concatenated master key and salt; MUST be a unique
     *            cryptographically random value with respect to other master
     *            keys in the entire SDP message (i.e., including master keys
     *            for other streams)
     * @param lifetime The master key lifetime (max number of SRTP or SRTCP packets
     *            using this master key)
     * @param mki The master key identifier in the SRTP packets.
     * @param mkiLength Length of the MKI field in SRTP packets.
     */
    public SrtpKeyParam(String keyMethod, byte[] key, int lifetime, int mki, int mkiLength) {
        if (!keyMethod.equals(KEYMETHOD_INLINE))
            throw new IllegalArgumentException("key method must be inline");
        if (mkiLength < 0 || mkiLength > 128)
            throw new IllegalArgumentException("mki length must be in range 1..128 inclusive or 0 to indicate default");

        this.key = key;
        this.lifetime = lifetime;
        this.mki = mki;
        this.mkiLength = mkiLength;
    }

    /**
     * Creates a new instance of this class from the textual representation.
     * 
     * @param keyParam The textual representation of the key parameter.
     */
    public SrtpKeyParam(String keyParam) {
        if (!keyParam.startsWith(keyMethod + ":"))
            throw new IllegalArgumentException("Unknown key method in <" + keyParam + ">");

        keyParam = keyParam.substring(KEYMETHOD_INLINE.length() + 1);
        String[] parts = keyParam.split("\\|");
        key = Base64.decodeBase64(parts[0]);
        if(key.length == 0)
            throw new IllegalArgumentException("key must be present");

        int partIndex = 1;
        if (parts.length > 1 && !parts[1].contains(":")) {
            if(parts[1].startsWith("2^"))
                lifetime = (int)Math.pow(2, Double.valueOf(parts[1].substring(2)));
            else
                lifetime = Integer.valueOf(parts[1]);
            partIndex++;
        }
        if (parts.length > partIndex && parts[partIndex].contains(":")) {
            String[] mkiParts = parts[partIndex].split(":");
            mki = Integer.valueOf(mkiParts[0]);
            mkiLength = Integer.valueOf(mkiParts[1]);
            if (mkiLength < 1 || mkiLength > 128)
                throw new IllegalArgumentException("mki length must be in range 1..128 inclusive");
        }
    }

    /**
     * The key method for this key parameter.
     * @return {@value #KEYMETHOD_INLINE}
     */
    @Override
    public String getKeyMethod() {
        return keyMethod;
    }

    /**
     * Gets the concatenated master key and salt.
     * @return the concatenated master key and salt.
     */
    public byte[] getKey() {
        return key;
    }

    /**
     * Gets the master key lifetime (max number of SRTP or SRTCP packets using
     * this master key)
     * 
     * @return The master key lifetime.
     */
    public int getLifetime() {
        return lifetime;
    }

    /**
     * Gets the master key identifier in the SRTP packets.
     * @return The master key identifier in the SRTP packets.
     */
    public int getMki() {
        return mki;
    }

    /**
     * Gets the length of the MKI field in SRTP packets
     * @return The length of the MKI field in SRTP packets.
     */
    public int getMkiLength() {
        return mkiLength;
    }

    @Override
    public String encode() {
        StringBuilder sb = new StringBuilder();
        sb.append(keyMethod);
        sb.append(':');
        sb.append(StringUtils.newStringUtf8(Base64.encodeBase64(key, false)));
        if (lifetime > 0) {
            sb.append('|');
            sb.append(lifetime);
        }
        if (mkiLength > 0) {
            sb.append('|');
            sb.append(mki);
            sb.append(':');
            sb.append(mkiLength);
        }
        return sb.toString();
    }
}
