/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.xmpp.extensions.jingle;

import org.xmpp.extensions.AbstractExtensionElement;

import java.util.List;

/**
 * Represents the <tt>payload-type</tt> elements described in
 * XEP-0167: Jingle RTP Sessions 1.2.0 (2020-04-22)
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
public class PayloadTypeExtension extends AbstractExtensionElement
{
    /**
     * The name of the "payload-type" element.
     */
    public static final String ELEMENT = "payload-type";

    /**
     * The namespace of the "payload-type" element
     */
    public static final String NAMESPACE = "urn:xmpp:jingle:apps:rtp:1";

    /**
     * The name of the <tt>channels</tt> <tt>payload-type</tt> argument.
     */
    public static final String CHANNELS_ATTR_NAME = "channels";

    /**
     * The name of the <tt>clockrate</tt> SDP argument.
     */
    public static final String CLOCKRATE_ATTR_NAME = "clockrate";

    /**
     * The name of the payload <tt>id</tt> SDP argument.
     */
    public static final String ID_ATTR_NAME = "id";

    /**
     * The name of the <tt>maxptime</tt> SDP argument.
     */
    public static final String MAXPTIME_ATTR_NAME = "maxptime";

    /**
     * The name of the <tt>name</tt> SDP argument.
     */
    public static final String NAME_ATTR_NAME = "name";

    /**
     * The name of the <tt>ptime</tt> SDP argument.
     */
    public static final String PTIME_ATTR_NAME = "ptime";

    /**
     * Creates a deep copy of a {@link PayloadTypeExtension}.
     *
     * @param source the {@link PayloadTypeExtension} to copy.
     * @return the copy.
     */
    public static PayloadTypeExtension clone(PayloadTypeExtension source)
    {
        PayloadTypeExtension destination = AbstractExtensionElement.clone(source);
        for (RtcpFbExtension rtcpFb : source.getRtcpFeedbackTypeList()) {
            destination.addRtcpFeedbackType(RtcpFbExtension.clone(rtcpFb));
        }

        for (ParameterExtension parameter : source.getParameters()) {
            destination.addParameter(ParameterExtension.clone(parameter));
        }
        return destination;
    }

    /**
     * Creates a new {@link PayloadTypeExtension} instance.
     */
    public PayloadTypeExtension()
    {
        super(ELEMENT, NAMESPACE);
    }

    /**
     * Sets the number of channels in this payload type. If omitted, it will be assumed to contain one channel.
     *
     * @param channels the number of channels in this payload type.
     */
    public void setChannels(int channels)
    {
        super.setAttribute(CHANNELS_ATTR_NAME, channels);
    }

    /**
     * Returns the number of channels in this payload type.
     *
     * @return the number of channels in this payload type.
     */
    public int getChannels()
    {
        /*
         * XEP-0167: Jingle RTP Sessions says: if omitted, it MUST be assumed to contain one channel.
         */
        return getAttributeAsInt(CHANNELS_ATTR_NAME, 1);
    }

    /**
     * Specifies the sampling frequency in Hertz used by this encoding.
     *
     * @param clockrate the sampling frequency in Hertz used by this encoding.
     */
    public void setClockrate(int clockrate)
    {
        super.setAttribute(CLOCKRATE_ATTR_NAME, clockrate);
    }

    /**
     * Returns the sampling frequency in Hertz used by this encoding.
     *
     * @return the sampling frequency in Hertz used by this encoding.
     */
    public int getClockrate()
    {
        return getAttributeAsInt(CLOCKRATE_ATTR_NAME);
    }

    /**
     * Specifies the payload identifier for this encoding.
     *
     * @param id the payload type id
     */
    public void setId(int id)
    {
        super.setAttribute(ID_ATTR_NAME, id);
    }

    /**
     * Returns the payload identifier for this encoding (as specified by RFC 3551 or a dynamic one).
     *
     * @return the payload identifier for this encoding (as specified by RFC 3551 or a dynamic one).
     */
    public int getID()
    {
        return getAttributeAsInt(ID_ATTR_NAME);
    }

    /**
     * Sets the maximum packet time as specified in RFC 4566.
     *
     * @param maxptime the maximum packet time as specified in RFC 4566
     */
    public void setMaxptime(int maxptime)
    {
        setAttribute(MAXPTIME_ATTR_NAME, maxptime);
    }

    /**
     * Returns maximum packet time as specified in RFC 4566.
     *
     * @return maximum packet time as specified in RFC 4566
     */
    public int getMaxptime()
    {
        return getAttributeAsInt(MAXPTIME_ATTR_NAME);
    }

    /**
     * Sets the packet time as specified in RFC 4566.
     *
     * @param ptime the packet time as specified in RFC 4566
     */
    public void setPtime(int ptime)
    {
        super.setAttribute(PTIME_ATTR_NAME, ptime);
    }

    /**
     * Returns packet time as specified in RFC 4566.
     *
     * @return packet time as specified in RFC 4566
     */
    public int getPtime()
    {
        return getAttributeAsInt(PTIME_ATTR_NAME);
    }

    /**
     * Sets the name of the encoding, or as per the XEP: the appropriate subtype of the MIME type.
     * Setting this field is RECOMMENDED for static payload types, REQUIRED for dynamic payload types.
     *
     * @param name the name of this encoding.
     */
    public void setName(String name)
    {
        setAttribute(NAME_ATTR_NAME, name);
    }

    /**
     * Returns the name of the encoding, or as per the XEP: the appropriate subtype of the MIME
     * type. Setting this field is RECOMMENDED for static payload types, REQUIRED for dynamic payload types.
     *
     * @return the name of the encoding, or as per the XEP: the appropriate subtype of the MIME
     * type. Setting this field is RECOMMENDED for static payload types, REQUIRED for dynamic payload types.
     */
    public String getName()
    {
        return getAttributeAsString(NAME_ATTR_NAME);
    }

    /**
     * Adds an SDP parameter to the list that we already have registered for this payload type.
     *
     * @param parameter an SDP parameter for this encoding.
     */
    public void addParameter(ParameterExtension parameter)
    {
        // parameters are the only extensions we can have so let's use super's list.
        addChildExtension(parameter);
    }

    /**
     * Returns a <b>reference</b> to the the list of parameters currently registered for this payload type.
     *
     * @return a <b>reference</b> to the the list of parameters currently registered for this payload type.
     */
    public List<ParameterExtension> getParameters()
    {
        return getChildExtensionsOfType(ParameterExtension.class);
    }

    /**
     * Adds an RTCP feedback type to the list that we already have registered for this payload type.
     *
     * @param rtcpFbPacketExtension RTCP feedback type for this encoding.
     */
    public void addRtcpFeedbackType(RtcpFbExtension rtcpFbPacketExtension)
    {
        addChildExtension(rtcpFbPacketExtension);
    }

    /**
     * Returns the list of RTCP feedback types currently registered for this payload type.
     *
     * @return the list of RTCP feedback types currently registered for this payload type.
     */
    public List<RtcpFbExtension> getRtcpFeedbackTypeList()
    {
        return getChildExtensionsOfType(RtcpFbExtension.class);
    }
}
