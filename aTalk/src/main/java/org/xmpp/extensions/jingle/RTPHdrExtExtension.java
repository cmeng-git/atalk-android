/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.xmpp.extensions.jingle;

import org.xmpp.extensions.AbstractExtensionElement;
import org.xmpp.extensions.jingle.element.JingleContent;
import org.xmpp.extensions.jingle.element.JingleContent.Senders;

import org.jivesoftware.smack.packet.ExtensionElement;

import java.net.URI;

/**
 * RTP header extension.
 *
 * Jingle's Discovery Info URN for "XEP-0294: Jingle RTP Header Extensions Negotiation" support.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class RTPHdrExtExtension extends AbstractExtensionElement
{
    /**
     * The namespace.
     */
    public static final String NAMESPACE = "urn:xmpp:jingle:apps:rtp:rtp-hdrext:0";

    /**
     * The name of the "candidate" element.
     */
    public static final String ELEMENT = "rtp-hdrext";

    /**
     * The name of the ID attribute.
     */
    public static final String ID_ATTR_NAME = "id";

    /**
     * The name of the senders attribute.
     */
    public static final String SENDERS_ATTR_NAME = "senders";

    /**
     * The name of the URI attribute.
     */
    public static final String URI_ATTR_NAME = "uri";

    /**
     * The name of the <tt>attributes</tt> attribute in the <tt>extmap</tt> element.
     */
    public static final String ATTRIBUTES_ATTR_NAME = "attributes";

    /**
     * Constructor.
     */
    public RTPHdrExtExtension()
    {
        super(ELEMENT, NAMESPACE);
    }

    /**
     * Creates a deep copy of a {@link PayloadTypeExtension}.
     *
     * @param source the {@link PayloadTypeExtension} to copy.
     * @return the copy.
     */
    public static RTPHdrExtExtension clone(RTPHdrExtExtension source)
    {
        RTPHdrExtExtension destination = AbstractExtensionElement.clone(source);

        // Note that this has no relation to the XML attributes of the extension.
        // It is a value transported in a "parameter" child extension.
        String attributes = source.getAttributes();
        if (attributes != null) {
            destination.setAttributes(attributes);
        }
        return destination;
    }

    public RTPHdrExtExtension(RTPHdrExtExtension ext)
    {
        this();
        String attributes = ext.getAttributes();
        if (attributes != null) {
            setAttributes(attributes);
        }
        setID(ext.getID());
        setSenders(ext.getSenders());
        setURI(ext.getURI());
    }

    /**
     * Set the ID.
     *
     * @param id ID to set
     */
    public void setID(String id)
    {
        setAttribute(ID_ATTR_NAME, id);
    }

    /**
     * Get the ID.
     *
     * @return the ID
     */
    public String getID()
    {
        return getAttributeAsString(ID_ATTR_NAME);
    }

    /**
     * Set the direction.
     *
     * @param senders the direction
     */
    public void setSenders(JingleContent.Senders senders)
    {
        setAttribute(SENDERS_ATTR_NAME, senders);
    }

    /**
     * Get the direction.
     *
     * @return the direction
     */
    public Senders getSenders()
    {
        String attributeVal = getAttributeAsString(SENDERS_ATTR_NAME);

        return (attributeVal == null) ? null : Senders.valueOf(attributeVal.toString());
    }

    /**
     * Set the URI.
     *
     * @param uri URI to set
     */
    public void setURI(URI uri)
    {
        setAttribute(URI_ATTR_NAME, uri.toString());
    }

    /**
     * Get the URI.
     *
     * @return the URI
     */
    public URI getURI()
    {
        return getAttributeAsURI(URI_ATTR_NAME);
    }

    /**
     * Set attributes.
     *
     * @param attributes attributes value
     */
    public void setAttributes(String attributes)
    {
        ParameterExtension paramExt = new ParameterExtension();

        paramExt.setName(ATTRIBUTES_ATTR_NAME);
        paramExt.setValue(attributes);

        // The rtp-hdrext extension can only contain a single "parameter" child
        getChildExtensions().clear();
        addChildExtension(paramExt);
    }

    /**
     * Get "attributes" value.
     *
     * @return "attributes" value
     */
    public String getAttributes()
    {
        for (ExtensionElement ext : getChildExtensions()) {
            if (ext instanceof ParameterExtension) {
                ParameterExtension p = (ParameterExtension) ext;
                if (p.getName().equals(ATTRIBUTES_ATTR_NAME)) {
                    return p.getValue();
                }
            }
        }
        return null;
    }
}
