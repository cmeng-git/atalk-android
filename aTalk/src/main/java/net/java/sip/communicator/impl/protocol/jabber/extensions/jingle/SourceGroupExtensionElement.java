/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber.extensions.jingle;

import net.java.sip.communicator.impl.protocol.jabber.extensions.AbstractExtensionElement;
import net.java.sip.communicator.impl.protocol.jabber.extensions.colibri.SourceExtensionElement;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents <tt>ssrc-group</tt> elements described in XEP-0339.
 *
 * Created by gp on 07/08/14.
 *
 * @author George Politis
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class SourceGroupExtensionElement extends AbstractExtensionElement
{
    /**
     * The name of the "ssrc-group" element.
     */
    public static final String ELEMENT_NAME = "ssrc-group";

    /**
     * The namespace for the "ssrc-group" element.
     */
    public static final String NAMESPACE = "urn:xmpp:jingle:apps:rtp:ssma:0";

    /**
     * The name of the payload <tt>id</tt> SDP argument.
     */
    public static final String SEMANTICS_ATTR_NAME = "semantics";

    /**
     * The constant used for signaling simulcast semantics.
     */
    public static final String SEMANTICS_SIMULCAST = "SIM";

    /**
     * The constant used for flow identification (see RFC5888).
     */
    public static final String SEMANTICS_FID = "FID";

    /**
     * The constant used for fec (see RFC5956)
     */
    public static final String SEMANTICS_FEC = "FEC-FR";

    /**
     * Return new instance of <tt>SourceGroupExtensionElement</tt> with simulcast semantics
     * pre-configured.
     */
    public static SourceGroupExtensionElement createSimulcastGroup()
    {
        SourceGroupExtensionElement simulcastGroupPe = new SourceGroupExtensionElement();
        simulcastGroupPe.setSemantics(SEMANTICS_SIMULCAST);
        return simulcastGroupPe;
    }

    /**
     * Creates a new {@link SourceGroupExtensionElement} instance with the proper element name and
     * namespace.
     */
    public SourceGroupExtensionElement()
    {
        super(ELEMENT_NAME, NAMESPACE);
    }

    public SourceGroupExtensionElement(String elementName)
    {
        super(elementName, NAMESPACE);
    }

    /**
     * Gets the semantics of this source group.
     *
     * @return the semantics of this source group.
     */
    public String getSemantics()
    {
        return getAttributeAsString(SEMANTICS_ATTR_NAME);
    }

    /**
     * Sets the semantics of this source group.
     */
    public void setSemantics(String semantics)
    {
        this.setAttribute(SEMANTICS_ATTR_NAME, semantics);
    }

    /**
     * Gets the sources of this source group.
     *
     * @return the sources of this source group.
     */
    public List<SourceExtensionElement> getSources()
    {
        return getChildExtensionsOfType(SourceExtensionElement.class);
    }

    /**
     * Sets the sources of this source group.
     *
     * @param sources the sources of this source group.
     */
    public void addSources(List<SourceExtensionElement> sources)
    {
        if (sources != null && sources.size() != 0) {
            for (SourceExtensionElement source : sources)
                this.addChildExtension(source);
        }
    }

    /**
     * Returns deep copy of this <tt>SourceGroupExtensionElement</tt> instance.
     */
    public SourceGroupExtensionElement copy()
    {
        SourceGroupExtensionElement copy = AbstractExtensionElement.clone(this);
        copy.setSemantics(getSemantics());

        List<SourceExtensionElement> sources = getSources();
        List<SourceExtensionElement> sourcesCopy = new ArrayList<>(sources.size());

        for (SourceExtensionElement source : sources) {
            sourcesCopy.add(source.copy());
        }
        copy.addSources(sourcesCopy);
        return copy;
    }
}
