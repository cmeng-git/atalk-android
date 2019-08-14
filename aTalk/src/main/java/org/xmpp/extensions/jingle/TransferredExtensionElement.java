/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.xmpp.extensions.jingle;

import org.xmpp.extensions.AbstractExtensionElement;

/**
 * Implements <tt>AbstractExtensionElement</tt> for the "transferred" element defined by XEP-0251:
 * Jingle Session Transfer.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public class TransferredExtensionElement extends AbstractExtensionElement
{
    /**
     * The name of the "transfer" element.
     */
    public static final String ELEMENT = "transferred";

    /**
     * The namespace of the "transfer" element.
     */
    public static final String NAMESPACE = "urn:xmpp:jingle:transfer:0";

    /**
     * Initializes a new <tt>TransferredExtensionElement</tt> instance.
     */
    public TransferredExtensionElement()
    {
        super(ELEMENT, NAMESPACE);
    }
}
