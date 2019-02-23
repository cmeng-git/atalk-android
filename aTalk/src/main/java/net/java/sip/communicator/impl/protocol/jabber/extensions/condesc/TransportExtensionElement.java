package net.java.sip.communicator.impl.protocol.jabber.extensions.condesc;

import net.java.sip.communicator.impl.protocol.jabber.extensions.AbstractExtensionElement;

/**
 * A <tt>ExtensionElement</tt> that represents a "transport" child element.
 */
public class TransportExtensionElement extends AbstractExtensionElement
{
    /**
     * The name of the "transport" element.
     */
    public static final String ELEMENT_NAME = "transport";

    /**
     * Creates a new instance and sets the XML namespace to <tt>transport</tt>
     *
     * @param namespace the XML namespace of the "transport" element.
     */
    public TransportExtensionElement(String namespace)
    {
        super(TransportExtensionElement.ELEMENT_NAME, namespace);
    }
}