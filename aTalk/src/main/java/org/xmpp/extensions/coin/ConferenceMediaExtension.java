/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.xmpp.extensions.coin;

import org.xmpp.extensions.AbstractExtensionElement;

import javax.xml.namespace.QName;

/**
 * Conference media packet extension.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class ConferenceMediaExtension extends AbstractExtensionElement
{
    /**
     * The name of the element that contains the conference media.
     */
    public static final String ELEMENT = "available-media";

    /**
     * The namespace that conference media belongs to. cmeng NAMESPACE cannot be empty
     */
    public static final String NAMESPACE = CoinIQ.NAMESPACE;

    public static final QName QNAME = new QName(NAMESPACE, ELEMENT);

    /**
     * Constructor.
     */
    public ConferenceMediaExtension()
    {
        super(ELEMENT, NAMESPACE);
    }
}
