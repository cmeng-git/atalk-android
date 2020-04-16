package org.xmpp.extensions.condesc;

import org.xmpp.extensions.AbstractExtensionElement;

/**
 * A <tt>ExtensionElement</tt> that represents a "transport" child element.
 */
public class TransportExtension extends AbstractExtensionElement
{
    /**
     * The name of the "transport" element.
     */
    public static final String ELEMENT = "transport";

    /**
     * Creates a new instance and sets the XML namespace to <tt>transport</tt>
     *
     * @param namespace the XML namespace of the "transport" element.
     */
    public TransportExtension(String namespace)
    {
        super(TransportExtension.ELEMENT, namespace);
    }
}