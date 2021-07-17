/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.xmpp.extensions.jingle;

import org.xmpp.extensions.AbstractExtensionElement;

import org.jivesoftware.smack.packet.ExtensionElement;

import java.util.ArrayList;
import java.util.List;

/**
 * The element transporting encryption information during jingle session establishment.
 * XEP-0167: Jingle RTP Sessions 1.2.0 (2020-04-22)
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
public class EncryptionExtension extends AbstractExtensionElement
{
    /**
     * The namespace of the "encryption" element. It it set to "not null" only for Gtalk SDES
     * support (may be set to null once gtalk supports jingle).
     */
    public static final String NAMESPACE = "urn:xmpp:jingle:apps:rtp:1";

    /**
     * The name of the "encryption" element.
     */
    public static final String ELEMENT = "encryption";

    /**
     * The name of the <tt>required</tt> attribute.
     */
    public static final String REQUIRED_ATTR_NAME = "required";

    /**
     * The list of <tt>crypto</tt> elements transported by this <tt>encryption</tt> element.
     */
    private List<CryptoExtension> cryptoList = new ArrayList<CryptoExtension>();

    /**
     * Creates a new instance of this <tt>EncryptionExtensionElement</tt>.
     */
    public EncryptionExtension()
    {
        super(ELEMENT, NAMESPACE);
    }

    /**
     * Adds a new <tt>crypto</tt> element to this encryption element.
     *
     * @param crypto the new <tt>crypto</tt> element to add.
     */
    public void addCrypto(CryptoExtension crypto)
    {
        if (!cryptoList.contains(crypto)) {
            cryptoList.add(crypto);
        }
    }

    /**
     * Returns a <b>reference</b> to the list of <tt>crypto</tt> elements that we have registered
     * with this encryption element so far.
     *
     * @return a <b>reference</b> to the list of <tt>crypto</tt> elements that we have registered
     * with this encryption element so far.
     */
    public List<CryptoExtension> getCryptoList()
    {
        return cryptoList;
    }

    /**
     * Specifies whether encryption is required for this session or not.
     *
     * @param required <tt>true</tt> if encryption is required for this session and <tt>false</tt> otherwise.
     */
    public void setRequired(boolean required)
    {
        if (required)
            super.setAttribute(REQUIRED_ATTR_NAME, required);
        else
            super.removeAttribute(REQUIRED_ATTR_NAME);
    }

    /**
     * Returns <tt>true</tt> if encryption is required for this session and <tt>false</tt>
     * otherwise. Default value is <tt>false</tt>.
     *
     * @return <tt>true</tt> if encryption is required for this session and <tt>false</tt> otherwise.
     */
    public boolean isRequired()
    {
        String required = getAttributeAsString(REQUIRED_ATTR_NAME);

        return Boolean.valueOf(required) || "1".equals(required);
    }

    /**
     * Returns a list containing all <tt>crypto</tt> sub-elements.
     *
     * @return a {@link List} containing all our <tt>crypto</tt> sub-elements.
     */
    @Override
    public List<? extends ExtensionElement> getChildExtensions()
    {
        List<ExtensionElement> ret = new ArrayList<ExtensionElement>();

        ret.addAll(super.getChildExtensions());
        return ret;
    }

    /**
     * Adds the specified <tt>childExtension</tt> to the list of extensions registered with this packet.
     * <p/>
     * Overriding extensions may need to override this method if they would like to have anything
     * more elaborate than just a list of extensions (e.g. casting separate instances to more specific.
     *
     * @param childExtension the extension we'd like to add here.
     */
    @Override
    public void addChildExtension(ExtensionElement childExtension)
    {
        super.addChildExtension(childExtension);
        if (childExtension instanceof CryptoExtension) {
            this.addCrypto(((CryptoExtension) childExtension));
        }
    }
}
