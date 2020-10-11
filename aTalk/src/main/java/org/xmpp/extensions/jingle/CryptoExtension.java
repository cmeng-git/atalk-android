/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.xmpp.extensions.jingle;

import org.atalk.android.util.ApiLib;
import org.xmpp.extensions.AbstractExtensionElement;

import javax.xml.namespace.QName;

/**
 * The element containing details about an encryption algorithm that could be used during
 * a jingle session. XEP-0167: Jingle RTP Sessions 1.2.0 (2020-04-22)
 *
 * @author Emil Ivov
 * @author Vincent Lucas
 * @author Eng Chong Meng
 */
public class CryptoExtension extends AbstractExtensionElement
{
    /**
     * The name of the "crypto" element.
     */
    public static final String ELEMENT = "crypto";

    /**
     * The namespace for the "crypto" element. It it set to "not null" only for Gtalk SDES support
     * (may be set to null once gtalk supports jingle).
     */
    public static final String NAMESPACE = "urn:xmpp:jingle:apps:rtp:1";

    public static final QName QNAME = new QName(NAMESPACE, ELEMENT);

    /**
     * The name of the 'crypto-suite' argument.
     */
    public static final String CRYPTO_SUITE_ATTR_NAME = "crypto-suite";

    /**
     * The name of the 'key-params' argument.
     */
    public static final String KEY_PARAMS_ATTR_NAME = "key-params";

    /**
     * The name of the 'session-params' argument.
     */
    public static final String SESSION_PARAMS_ATTR_NAME = "session-params";

    /**
     * The name of the 'tag' argument.
     */
    public static final String TAG_ATTR_NAME = "tag";

    /**
     * Creates a new {@link CryptoExtension} instance with the proper element name and namespace.
     */
    public CryptoExtension()
    {
        super(ELEMENT, NAMESPACE);
    }

    /**
     * Creates a new {@link CryptoExtension} instance with the proper element name
     * and namespace and initialises it with the parameters contained by the cryptoAttribute.
     *
     * @param tag a <tt>String</tt> containing a decimal number used as an
     * identifier for a particular crypto element.
     * @param cryptoSuite a <tt>String</tt> that describes the encryption and authentication algorithms.
     * @param keyParams a <tt>String</tt> that provides one or more sets of keying material for the crypto-suite in question.
     * @param sessionParams a <tt>String</tt> that provides transport-specific parameters for SRTP negotiation.
     */
    public CryptoExtension(int tag, String cryptoSuite, String keyParams, String sessionParams)
    {
        this();
        // Encode the tag element.
        this.setTag(Integer.toString(tag));
        // Encode the crypto-suite element.
        this.setCryptoSuite(cryptoSuite);
        // Encode the key-params element.
        this.setKeyParams(keyParams);

        // Encode the session-params element (optional).
        if (sessionParams != null) {
            this.setSessionParams(sessionParams);
        }
    }

    /**
     * Sets the value of the <tt>crypto-suite</tt> attribute: an identifier that describes the
     * encryption and authentication algorithms.
     *
     * @param cryptoSuite a <tt>String</tt> that describes the encryption and authentication algorithms.
     */
    public void setCryptoSuite(String cryptoSuite)
    {
        super.setAttribute(CRYPTO_SUITE_ATTR_NAME, cryptoSuite);
    }

    /**
     * Returns the value of the <tt>crypto-suite</tt> attribute.
     *
     * @return a <tt>String</tt> that describes the encryption and authentication algorithms.
     */
    public String getCryptoSuite()
    {
        return getAttributeAsString(CRYPTO_SUITE_ATTR_NAME);
    }

    /**
     * Returns if the current crypto suite equals the one given in parameter.
     *
     * @param cryptoSuite a <tt>String</tt> that describes the encryption and authentication algorithms.
     * @return True if the current crypto suite equals the one given in parameter. False, otherwise.
     */
    public boolean equalsCryptoSuite(String cryptoSuite)
    {
        String currentCryptoSuite = this.getCryptoSuite();
        return CryptoExtension.equalsStrings(currentCryptoSuite, cryptoSuite);
    }

    /**
     * Sets the value of the <tt>key-params</tt> attribute that provides one or more sets of keying
     * material for the crypto-suite in question).
     *
     * @param keyParams a <tt>String</tt> that provides one or more sets of keying material for the
     * crypto-suite in question.
     */
    public void setKeyParams(String keyParams)
    {
        super.setAttribute(KEY_PARAMS_ATTR_NAME, keyParams);
    }

    /**
     * Returns the value of the <tt>key-params</tt> attribute.
     *
     * @return a <tt>String</tt> that provides one or more sets of keying material for the crypto-suite in question.
     */
    public String getKeyParams()
    {
        return getAttributeAsString(KEY_PARAMS_ATTR_NAME);
    }

    /**
     * Returns if the current key params equals the one given in parameter.
     *
     * @param keyParams a <tt>String</tt> that provides one or more sets of keying material for the
     * crypto-suite in question.
     * @return True if the current key params equals the one given in parameter. False, otherwise.
     */
    public boolean equalsKeyParams(String keyParams)
    {
        String currentKeyParams = this.getKeyParams();
        return CryptoExtension.equalsStrings(currentKeyParams, keyParams);
    }

    /**
     * Sets the value of the <tt>session-params</tt> attribute that provides transport-specific parameters for SRTP negotiation.
     *
     * @param sessionParams a <tt>String</tt> that provides transport-specific parameters for SRTP negotiation.
     */
    public void setSessionParams(String sessionParams)
    {
        super.setAttribute(SESSION_PARAMS_ATTR_NAME, sessionParams);
    }

    /**
     * Returns the value of the <tt>session-params</tt> attribute.
     *
     * @return a <tt>String</tt> that provides transport-specific parameters for SRTP negotiation.
     */
    public String getSessionParams()
    {
        return getAttributeAsString(SESSION_PARAMS_ATTR_NAME);
    }

    /**
     * Returns if the current session params equals the one given in parameter.
     *
     * @param sessionParams a <tt>String</tt> that provides transport-specific parameters for SRTP negotiation.
     * @return True if the current session params equals the one given in parameter. False, otherwise.
     */
    public boolean equalsSessionParams(String sessionParams)
    {
        String currentSessionParams = this.getSessionParams();
        return CryptoExtension.equalsStrings(currentSessionParams, sessionParams);
    }

    /**
     * Sets the value of the <tt>tag</tt> attribute: a decimal number used as an identifier for a particular crypto element.
     *
     * @param tag a <tt>String</tt> containing a decimal number used as an identifier for a particular crypto element.
     */
    public void setTag(String tag)
    {
        super.setAttribute(TAG_ATTR_NAME, tag);
    }

    /**
     * Returns the value of the <tt>tag</tt> attribute.
     *
     * @return a <tt>String</tt> containing a decimal number used as an identifier for a particular crypto element.
     */
    public String getTag()
    {
        return getAttributeAsString(TAG_ATTR_NAME);
    }

    /**
     * Returns if the current tag equals the one given in parameter.
     *
     * @param tag a <tt>String</tt> containing a decimal number used as an identifier for a particular crypto element.
     * @return True if the current tag equals the one given in parameter. False, otherwise.
     */
    public boolean equalsTag(String tag)
    {
        String currentTag = this.getTag();
        return CryptoExtension.equalsStrings(currentTag, tag);
    }

    /**
     * Returns if the first String equals the second one.
     *
     * @param string1 A String to be compared with the second one.
     * @param string2 A String to be compared with the fisrt one.
     * @return True if both strings are null, or if they represent the same sequane of characters. False, otherwise.
     */
    private static boolean equalsStrings(String string1, String string2)
    {
        return (((string1 == null) && (string2 == null)) || string1.equals(string2));
    }

    /**
     * Returns if the current CryptoExtensionElement equals the one given in parameter.
     *
     * @param obj an object which might be an instance of CryptoExtensionElement.
     * @return True if the object in parameter is a CryptoPAcketExtension with all fields
     * (crypto-suite, key-params, session-params and tag) corresponding to the current one. False, otherwise.
     */
    @Override
    public boolean equals(Object obj)
    {
        if (obj instanceof CryptoExtension) {
            CryptoExtension crypto = (CryptoExtension) obj;

            return (crypto.equalsCryptoSuite(this.getCryptoSuite())
                    && crypto.equalsKeyParams(this.getKeyParams())
                    && crypto.equalsSessionParams(this.getSessionParams())
                    && crypto.equalsTag(this.getTag()));
        }
        return false;
    }

    @Override
    public int hashCode()
    {
        return ApiLib.hash(
                getCryptoSuite(),
                getKeyParams(),
                getSessionParams(),
                getTag());
    }
}
