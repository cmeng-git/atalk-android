package org.jivesoftware.smackx.confdesc;

import org.jivesoftware.smackx.AbstractExtensionElement;

/**
 * A <code>ExtensionElement</code> that represents a "transport" child element.
 */
public class TransportExtension extends AbstractExtensionElement
{
    /**
     * The name of the "transport" element.
     */
    public static final String ELEMENT = "transport";

    /**
     * Creates a new instance and sets the XML namespace to <code>transport</code>
     *
     * @param namespace the XML namespace of the "transport" element.
     */
    public TransportExtension(String namespace)
    {
        super(TransportExtension.ELEMENT, namespace);
    }
}