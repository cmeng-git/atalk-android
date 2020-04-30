/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.xmpp.extensions.jingle;

import org.xmpp.extensions.AbstractExtensionElement;

import javax.xml.namespace.QName;

/**
 * Implements <tt>AbstractExtensionElement</tt> for the "transferred" element defined by XEP-0251:
 * Jingle Session Transfer.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public class TransferredExtension extends AbstractExtensionElement
{
    /**
     * The name of the "transfer" element.
     */
    public static final String ELEMENT = "transferred";

    /**
     * The namespace of the "transfer" element.
     */
    public static final String NAMESPACE = "urn:xmpp:jingle:transfer:0";

    public static final QName QNAME = new QName(NAMESPACE, ELEMENT);

    /**
     * Initializes a new <tt>TransferredExtensionElement</tt> instance.
     */
    public TransferredExtension()
    {
        super(ELEMENT, NAMESPACE);
    }
}
