/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.xmpp.extensions.jingle;

import org.xmpp.extensions.AbstractExtensionElement;
import org.xmpp.extensions.colibri.SourceExtension;

import java.util.ArrayList;
import java.util.List;

import javax.xml.namespace.QName;

/**
 * Represents <tt>ssrc-group</tt> elements described in XEP-0339.
 *
 * Created by gp on 07/08/14.
 *
 * @author George Politis
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class SourceGroupExtension extends AbstractExtensionElement
{
    /**
     * The name of the "ssrc-group" element.
     */
    public static final String ELEMENT = "ssrc-group";

    /**
     * The namespace for the "ssrc-group" element.
     */
    public static final String NAMESPACE = "urn:xmpp:jingle:apps:rtp:ssma:0";

    public static final QName QNAME = new QName(NAMESPACE, ELEMENT);

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
    public static SourceGroupExtension createSimulcastGroup()
    {
        SourceGroupExtension simulcastGroupPe = new SourceGroupExtension();
        simulcastGroupPe.setSemantics(SEMANTICS_SIMULCAST);
        return simulcastGroupPe;
    }

    /**
     * Creates a new {@link SourceGroupExtension} instance with the proper element name and
     * namespace.
     */
    public SourceGroupExtension()
    {
        super(ELEMENT, NAMESPACE);
    }

    public SourceGroupExtension(String elementName)
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
    public List<SourceExtension> getSources()
    {
        return getChildExtensionsOfType(SourceExtension.class);
    }

    /**
     * Sets the sources of this source group.
     *
     * @param sources the sources of this source group.
     */
    public void addSources(List<SourceExtension> sources)
    {
        if (sources != null && sources.size() != 0) {
            for (SourceExtension source : sources)
                this.addChildExtension(source);
        }
    }

    /**
     * Returns deep copy of this <tt>SourceGroupExtensionElement</tt> instance.
     */
    public SourceGroupExtension copy()
    {
        SourceGroupExtension copy = AbstractExtensionElement.clone(this);
        copy.setSemantics(getSemantics());

        List<SourceExtension> sources = getSources();
        List<SourceExtension> sourcesCopy = new ArrayList<>(sources.size());

        for (SourceExtension source : sources) {
            sourcesCopy.add(source.copy());
        }
        copy.addSources(sourcesCopy);
        return copy;
    }
}
