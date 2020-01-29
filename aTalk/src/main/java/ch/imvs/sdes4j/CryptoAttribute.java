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

import java.util.*;

/**
 * Primary class for a RFC4568 Crypto Attribute.
 * 
 * @author Ingo Bauersachs
 */
public class CryptoAttribute {
    protected int tag;
    protected CryptoSuite cryptoSuite;
    protected KeyParam[] keyParams;
    protected SessionParam[] sessionParams = null;

    protected CryptoAttribute(){
    }

    /**
     * Creates an instance of a CryptoAttribute from an SDes string in the
     * format of
     * <tt>tag 1*WSP crypto-suite 1*WSP key-params *(1*WSP session-param)</tt>
     * 
     * @param attribute the encoded SDes attribute
     * @param f factory that creates the instances for each part of the
     *            attribute
     * @return a parsed crypto attribute
     */
    public static CryptoAttribute create(String attribute, SDesFactory f) {
        CryptoAttribute result = f.createCryptoAttribute();
        List<String> tokens = new LinkedList<String>();
        for (String s : attribute.split("\\s")){
            if(s.trim().length() > 0)
                tokens.add(s);
        }

        result.setTag(tokens.remove(0));
        result.setCryptoSuite(tokens.remove(0), f);

        if (tokens.size() < 1)
            throw new IllegalArgumentException("There must be at least one key parameter");
        result.setKeyParams(tokens.remove(0), f);

        result.setSessionParams(tokens, f);
        return result;
    }

    /**
     * Creates an instance of a CryptoAttribute from a SDes attributes (tag,
     * crypto suite, key params and session params).
     *
     * @param tag unparsed tag as a string. 
     * @param cryptoSuite the crypto suite as an unparsed string.
     * @param keyParams An unparsed string representation of the key param list
     * (each key must be separated by a ";").
     * @param sessionParams An unparsed string representation of the session
     * param list (each key must be separated by a " ").
     * @param f factory that creates the instances for each part of the
     *            attribute
     *
     * @return a parsed crypto attribute
     */
    public static CryptoAttribute create(String tag, String cryptoSuite, String keyParams, String sessionParams, SDesFactory f) {
        CryptoAttribute result = f.createCryptoAttribute();

        result.setTag(tag);
        result.setCryptoSuite(cryptoSuite, f);
        result.setKeyParams(keyParams, f);

        List<String> tokens = new LinkedList<String>();
        if(sessionParams != null) {
            for (String s : sessionParams.split("\\s")) {
                if(s.trim().length() > 0)
                    tokens.add(s);
            }
        }

        result.setSessionParams(tokens, f);

        return result;
    }

    /**
     * Creates a crypto attribute from already instantiated objects.
     * 
     * @param tag identifier for this particular crypto attribute
     * @param cryptoSuite identifier that describes the encryption and
     *            authentication algorithms
     * @param keyParams one or more sets of keying material
     * @param sessionParams the additional key parameters
     */
    public CryptoAttribute(int tag, CryptoSuite cryptoSuite, KeyParam[] keyParams, SessionParam[] sessionParams) {
        if (tag > 99999999 || tag < 0)
            throw new IllegalArgumentException("tag can have at most 10 digits and must be non-negative");

        if (cryptoSuite == null)
            throw new IllegalArgumentException("cryptoSuite cannot be null");

        if (keyParams == null || keyParams.length == 0)
            throw new IllegalArgumentException("keyParams cannot be null or empty");

        this.tag = tag;
        this.cryptoSuite = cryptoSuite;
        this.keyParams = keyParams;

        if (sessionParams == null)
            this.sessionParams = new SessionParam[0];
        else
            this.sessionParams = sessionParams;
    }

    /**
     * Gets the identifier for this particular crypto attribute.
     * 
     * @return the tag
     */
    public int getTag() {
        return tag;
    }

    /**
     * Sets the tag which is used as an identifier for this particular crypto
     * attribute. The tag MUST be unique among all crypto attributes for a given
     * media line.
     * 
     * @param stringTag unparsed tag as a string. 
     */
    private void setTag(String stringTag) {
        int tag = Integer.valueOf(stringTag);
        if (tag > 99999999 || tag < 0)
            throw new IllegalArgumentException("tag can have at most 10 digits and must be non-negative");
        this.tag = tag;
    }

    /**
     * Gets the identifier that describes the encryption and authentication
     * algorithms (e.g., AES_CM_128_HMAC_SHA1_80) for the transport in question.
     * 
     * @return the cryptoSuite
     */
    public CryptoSuite getCryptoSuite() {
        return cryptoSuite;
    }

    /**
     * Sets the identifier that describes the encryption and authentication
     * algorithms (e.g., AES_CM_128_HMAC_SHA1_80) for the transport in question.
     * 
     * @param stringCryptoSuite the crypto suite as an unparsed string.
     * @param f factory that creates the crypto suite instance
     */
    private void setCryptoSuite(String stringCryptoSuite, SDesFactory f) {
        this.cryptoSuite = f.createCryptoSuite(stringCryptoSuite);
    }

    /**
     * Gets one or more sets of keying material for the crypto-suite in
     * question.
     * 
     * @return the keyParams
     */
    public KeyParam[] getKeyParams() {
        return keyParams;
    }

    /**
     * Sets one or more sets of keying material for the crypto-suite in
     * question.
     * 
     * @param stringKeyParams An unparsed string representation of the key param
     * list (each key must be separated by a ";").
     * @param f factory that creates the key params instances
     */
    private void setKeyParams(String stringKeyParams, SDesFactory f) {
        String[] params = stringKeyParams.split(";");
        List<KeyParam> keyParams = new LinkedList<KeyParam>();
        for (String p : params) {
            keyParams.add(f.createKeyParam(p));
        }
        this.keyParams = keyParams.toArray(f.createKeyParamArray(0));
    }

    /**
     * Gets the additional key parameters for this particular crypto attribute.
     * 
     * @return the sessionParams
     */
    public SessionParam[] getSessionParams() {
        return sessionParams;
    }

    /**
     * Sets additional key parameters for this particular crypto attribute.
     * 
     * @param tokens List of String-Tokens where the matching items are consumed
     *            and removed from the list
     * @param f factory that creates the session params instances
     */
    private void setSessionParams(List<String> tokens, SDesFactory f) {
        List<SessionParam> sessionParams = new LinkedList<SessionParam>();
        while (tokens.size() > 0) {
            sessionParams.add(f.createSessionParam(tokens.remove(0)));
        }
        this.sessionParams = sessionParams.toArray(f.createSessionParamArray(0));
    }

    /**
     * Encodes this crypto attribute as a string according to the ABNF rule
     * <tt>tag 1*WSP crypto-suite 1*WSP key-params *(1*WSP session-param)</tt>
     * 
     * @return Complete crypto attribute for use in the SDP.
     */
    public String encode() {
        StringBuilder sb = new StringBuilder();
        sb.append(tag);
        sb.append(' ');
        sb.append(cryptoSuite.encode());
        sb.append(' ');
        sb.append(getKeyParamsString());
        if (sessionParams != null && sessionParams.length > 0) {
            sb.append(' ');
            sb.append(getSessionParamsString());
        }

        return sb.toString();
    }

    /**
     * Returns a string representation the key parameters according to the ABNF
     * rule key-params.
     * 
     * @return String representation of the list of key params separated by ";".
     */
    public String getKeyParamsString() {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < keyParams.length; i++) {
            sb.append(keyParams[i].encode());
            if (i < keyParams.length - 1)
                sb.append(';');
        }

        return sb.toString();
    }

    /**
     * Returns a string representation of the session parameters according to
     * the ABNF rule session-param.
     *
     * @return Returns a string representation of the list of session params
     * separated by " ", or null if there are no session params.
     */
    public String getSessionParamsString() {
        if (sessionParams != null && sessionParams.length > 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < sessionParams.length; i++) {
                sb.append(sessionParams[i].encode());
                if (i < sessionParams.length - 1)
                    sb.append(' ');
            }
            return sb.toString();
        }
        else{
            return null;
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj != null && obj instanceof CryptoAttribute)
        {
            CryptoAttribute other = (CryptoAttribute)obj;
            return encode().equals(other.encode());
        }

        return false;
    }

    @Override
    public int hashCode() {
        return encode().hashCode();
    }
}
