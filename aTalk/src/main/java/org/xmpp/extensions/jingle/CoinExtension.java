/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.xmpp.extensions.jingle;

import org.xmpp.extensions.AbstractExtensionElement;

import javax.xml.namespace.QName;

/**
 * Represents the conference information.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class CoinExtension extends AbstractExtensionElement
{
    /**
     * Name of the XML element representing the extension.
     */
    public final static String ELEMENT = "conference-info";

    /**
     * Namespace.
     */
    public final static String NAMESPACE = "urn:xmpp:coin:1";

    public static final QName QNAME = new QName(NAMESPACE, ELEMENT);

    /**
     * IsFocus attribute name.
     */
    public final static String ISFOCUS_ATTR_NAME = "isfocus";

    /**
     * Constructs a new <tt>coin</tt> extension.
     */
    public CoinExtension()
    {
        super(ELEMENT, NAMESPACE);
    }

    /**
     * Constructs a new <tt>coin</tt> extension.
     *
     * @param isFocus <tt>true</tt> if the peer is a conference focus; otherwise, <tt>false</tt>
     */
    public CoinExtension(boolean isFocus)
    {
        super(ELEMENT, NAMESPACE);
        setAttribute(ISFOCUS_ATTR_NAME, isFocus);
    }
}
