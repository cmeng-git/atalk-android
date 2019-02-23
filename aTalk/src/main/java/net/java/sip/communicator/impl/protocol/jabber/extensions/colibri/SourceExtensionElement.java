/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber.extensions.colibri;

import net.java.sip.communicator.impl.protocol.jabber.extensions.AbstractExtensionElement;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ParameterExtensionElement;

import org.jivesoftware.smack.packet.ExtensionElement;

import java.util.List;

import timber.log.Timber;

/**
 * Implements <tt>AbstractExtensionElement</tt> for the <tt>source</tt> element defined by <a
 * href="http://hancke.name/jabber/jingle-sources"> Source-Specific Media Attributes in Jingle</a>.
 *
 * @author Lyubomir Marinov
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class SourceExtensionElement extends AbstractExtensionElement
{
    /**
     * The XML name of the <tt>setup</tt> element defined by Source-Specific Media Attributes in Jingle.
     */
    public static final String ELEMENT_NAME = "source";

    /**
     * The XML namespace of the <tt>setup</tt> element defined by Source-Specific Media Attributes in Jingle.
     */
    public static final String NAMESPACE = "urn:xmpp:jingle:apps:rtp:ssma:0";

    /**
     * The XML name of the <tt>setup</tt> element's attribute which corresponds to the <tt>ssrc</tt>
     * media attribute in SDP.
     */
    public static final String SSRC_ATTR_NAME = "ssrc";

    /**
     * The XML name of the attribute which corresponds to the <tt>rid</tt>
     * attribute in SDP.
     */
    public static final String RID_ATTR_NAME = "rid";

    /**
     * Initializes a new <tt>SourceExtensionElement</tt> instance.
     */
    public SourceExtensionElement()
    {
        super(ELEMENT_NAME, NAMESPACE);
    }

    /**
     * Adds a specific parameter (as defined by Source-Specific Media Attributes in Jingle) to this source.
     *
     * @param parameter the <tt>ParameterExtensionElement</tt> to add to this source
     */
    public void addParameter(ParameterExtensionElement parameter)
    {
        addChildExtension(parameter);
    }

    /**
     * Gets the parameters (as defined by Source-Specific Media Attributes in Jingle) of this source.
     *
     * @return the <tt>ParameterExtensionElement</tt>s of this source
     */
    public List<ParameterExtensionElement> getParameters()
    {
        return getChildExtensionsOfType(ParameterExtensionElement.class);
    }

    /**
     * Finds the value of SSRC parameter identified by given name.
     *
     * @param name the name of SSRC parameter to find.
     * @return value of SSRC parameter
     */
    public String getParameter(String name)
    {
        for (ParameterExtensionElement param : getParameters()) {
            if (name.equals(param.getName()))
                return param.getValue();
        }
        return null;
    }

    /**
     * Gets the synchronization source (SSRC) ID of this source.
     *
     * @return the synchronization source (SSRC) ID of this source
     */
    public long getSSRC()
    {
        String s = getAttributeAsString(SSRC_ATTR_NAME);

        return (s == null) ? -1 : Long.parseLong(s);
    }

    /**
     * Sets the synchronization source (SSRC) ID of this source.
     *
     * @param ssrc the synchronization source (SSRC) ID to be set on this source
     */
    public void setSSRC(long ssrc)
    {
        if (ssrc == -1) {
            removeAttribute(SSRC_ATTR_NAME);
        }
        else {
            setAttribute(SSRC_ATTR_NAME, Long.toString(0xffffffffL & ssrc));
        }
    }

    /**
     * Check if this source has an ssrc
     *
     * @return true if it has an ssrc, false otherwise
     */
    public boolean hasSSRC()
    {
        return getAttributeAsString(SSRC_ATTR_NAME) != null;
    }

    /**
     * Gets the rid of this source, if it has one
     *
     * @return the rid of the source or null
     */
    public String getRid()
    {
        return getAttributeAsString(RID_ATTR_NAME);
    }

    /**
     * Sets the rid of this source
     *
     * @param rid the rid to be set (or null to clear the existing rid)
     */
    public void setRid(String rid)
    {
        if (rid == null) {
            removeAttribute(RID_ATTR_NAME);
        }
        else {
            setAttribute(RID_ATTR_NAME, rid);
        }
    }

    /**
     * Check if this source has an rid
     *
     * @return true if it has an rid, false otherwise
     */
    public boolean hasRid()
    {
        return getAttribute(RID_ATTR_NAME) != null;
    }

    /**
     * Check if this source matches the given one with regards to
     * matching source identifiers (ssrc or rid)
     *
     * @param other the other SourceExtensionElement to compare to
     * @return true if this SourceExtensionElement and the one
     * given have matching source identifiers.  NOTE: will return
     * false if neither SourceExtensionElement has any source
     * identifier set
     */
    public boolean sourceEquals(SourceExtensionElement other)
    {
        if (hasSSRC() && other.hasSSRC()) {
            return getSSRC() == other.getSSRC();
        }
        else if (hasRid() && other.hasRid()) {
            return getRid().equals(other.getRid());
        }
        return false;
    }

    /**
     * Returns deep copy of this <tt>SourceExtensionElement</tt>.
     */
    public SourceExtensionElement copy()
    {
        SourceExtensionElement copy = AbstractExtensionElement.clone(this);

        // COPY SSRC PARAMS
        for (ExtensionElement ppe : getChildExtensions()) {
            if (ppe instanceof AbstractExtensionElement) {
                copy.addChildExtension(AbstractExtensionElement.clone((AbstractExtensionElement) ppe));
            }
            else {
                Timber.e("Failed to clone %s", ppe);
            }
        }
        return copy;
    }

    public String toString()
    {
        if (hasRid()) {
            return "rid=" + getRid();
        }
        else if (hasSSRC()) {
            return "ssrc=" + getAttributeAsString(SSRC_ATTR_NAME);
        }
        else {
            return "[no identifier]";
        }
    }

}
