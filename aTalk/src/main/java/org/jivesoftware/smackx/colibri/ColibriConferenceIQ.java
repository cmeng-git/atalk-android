/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jivesoftware.smackx.colibri;

import androidx.annotation.NonNull;

import org.jivesoftware.smackx.AbstractExtensionElement;

import org.atalk.service.neomedia.MediaDirection;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smack.packet.StanzaError.Condition;
import org.jivesoftware.smackx.jingle_rtp.element.IceUdpTransport;
import org.jivesoftware.smackx.jingle_rtp.element.PayloadType;
import org.jivesoftware.smackx.jingle_rtp.element.RtpHeader;
import org.jivesoftware.smackx.jingle_rtp.element.SdpSource;
import org.jivesoftware.smackx.jingle_rtp.element.SdpSourceGroup;
import org.jxmpp.jid.parts.Localpart;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.namespace.QName;

import timber.log.Timber;

/**
 * Implements the Jitsi Videobridge <code>conference</code> IQ within the COnferencing with LIghtweight
 * BRIdging. XEP-0340: COnferences with LIghtweight BRIdging (COLIBRI)
 *
 * @author Lyubomir Marinov
 * @author Boris Grozev
 * @author George Politis
 * @author Eng Chong Meng
 */
public class ColibriConferenceIQ extends IQ
{
    /**
     * The XML element name of the Jitsi Videobridge <code>conference</code> IQ.
     */
    public static final String ELEMENT = "conference";

    /**
     * The XML COnferencing with LIghtweight BRIdging namespace of the Jitsi Videobridge <code>conference</code> IQ.
     */
    public static final String NAMESPACE = "http://jitsi.org/protocol/colibri";

    /**
     * The XML name of the <code>id</code> attribute of the Jitsi Videobridge <code>conference</code> IQ
     * which represents the value of the <code>id</code> property of <code>ColibriConferenceIQ</code>.
     */
    public static final String ID_ATTR_NAME = "id";

    /**
     * The XML name of the <code>gid</code> attribute of the Jitsi Videobridge
     * <code>conference</code> IQ which represents the value of the <code>gid</code>
     * property of <code>ColibriConferenceIQ</code>.
     * This is a "global" ID of a conference, which is selected by the
     * conference organizer, as opposed to "id" which is specific to a single
     * Jitsi Videobridge and is selected by the bridge.
     */
    public static final String GID_ATTR_NAME = "gid";

    /**
     * The XML name of the <code>name</code> attribute of the Jitsi Videobridge
     * <code>conference</code> IQ which represents the value of the <code>name</code>
     * property of <code>ColibriConferenceIQ</code> if available.
     */
    public static final String NAME_ATTR_NAME = "name";

    /**
     * An array of <code>int</code>s which represents the lack of any (RTP) SSRCs seen/received on a
     * <code>Channel</code>. Explicitly defined to reduce unnecessary allocations.
     */
    public static final int[] NO_SSRCS = new int[0];

    /**
     * The {@link ChannelBundle}s included in this {@link ColibriConferenceIQ}, mapped by their ID.
     */
    private final Map<String, ChannelBundle> channelBundles = new ConcurrentHashMap<>();

    /**
     * The list of {@link Content}s included into this <code>conference</code> IQ.
     */
    private final List<Content> contents = new LinkedList<>();

    /**
     * The {@link Endpoint}s included in this {@link ColibriConferenceIQ}, mapped by their ID.
     */
    private final Map<String, Endpoint> endpoints = new ConcurrentHashMap<>();

    /**
     * The ID of the conference represented by this IQ.
     */
    private String id;

    /**
     * The ID of the global conference to which the conference represented by this {@link ColibriConferenceIQ} belongs.
     */
    private String gid;

    /**
     * Media recording.
     */
    private Recording recording;

    private RTCPTerminationStrategy rtcpTerminationStrategy;

    /**
     * Indicates if the information about graceful shutdown status is being carried by this IQ.
     */
    private boolean gracefulShutdown;

    /**
     * World readable name for the conference.
     */
    private Localpart name;

    /**
     * Initializes a new <code>ColibriConferenceIQ</code> instance.
     */
    public ColibriConferenceIQ()
    {
        super(ELEMENT, NAMESPACE);
    }

    /**
     * Returns an error response for given <code>IQ</code> that is returned by the videobridge after it
     * has entered graceful shutdown mode and new conferences can no longer be created.
     *
     * @param request the IQ for which error response will be created.
     * @return an IQ of 'error' type and 'service-unavailable' condition plus the body of request IQ.
     */
    public static IQ createGracefulShutdownErrorResponse(final IQ request)
    {
        final StanzaError error = StanzaError.getBuilder()
                .setCondition(Condition.service_unavailable)
                .setType(StanzaError.Type.CANCEL)
                .addExtension(new GracefulShutdown())
                .build();

        final IQ result = IQ.createErrorResponse(request, error);
        result.setType(Type.error);
        result.setStanzaId(request.getStanzaId());
        result.setFrom(request.getTo());
        result.setTo(request.getFrom());
        return result;
    }

    /**
     * Adds a specific {@link Content} instance to the list of <code>Content</code> instances included
     * into this <code>conference</code> IQ.
     *
     * @param channelBundle the <code>ChannelBundle</code> to add.
     */
    public ChannelBundle addChannelBundle(ChannelBundle channelBundle)
    {
        Objects.requireNonNull(channelBundle, "channelBundle");
        String id = Objects.requireNonNull(channelBundle.getId(), "channelBundle ID");

        return channelBundles.put(id, channelBundle);
    }

    /**
     * Adds a specific {@link Content} instance to the list of <code>Content</code> instances included
     * into this <code>conference</code> IQ.
     *
     * @param content the <code>Content</code> instance to be added to this list of <code>Content</code> instances
     * included into this <code>conference</code> IQ
     * @return <code>true</code> if the list of <code>Content</code> instances included into this
     * <code>conference</code> IQ has been modified as a result of the method call; otherwise, <code>false</code>
     * @throws NullPointerException if the specified <code>content</code> is <code>null</code>
     */
    public boolean addContent(Content content)
    {
        Objects.requireNonNull(content, "content");
        return !contents.contains(content) && contents.add(content);
    }

    /**
     * Initializes a new {@link Content} instance with a specific name and adds it to the list of
     * <code>Content</code> instances included into this <code>conference</code> IQ.
     *
     * @param contentName the name which which the new <code>Content</code> instance is to be initialized
     * @return <code>true</code> if the list of <code>Content</code> instances included into this
     * <code>conference</code> IQ has been modified as a result of the method call; otherwise, <code>false</code>
     */
    public boolean addContent(String contentName)
    {
        return addContent(new Content(contentName));
    }

    /**
     * Adds an {@link Endpoint} to this {@link ColibriConferenceIQ}. The
     * endpoint must be non-null and must have a non-null ID. If an
     * {@link Endpoint} with the same ID as the given {@link Endpoint} already
     * exists it is replaced and the previous one is returned.
     *
     * @param endpoint the {@link Endpoint} to add.
     * @return The previous {@link Endpoint} with the same ID, or {@code null}.
     */
    public Endpoint addEndpoint(Endpoint endpoint)
    {
        Objects.requireNonNull(endpoint, "endpoint");
        String id = Objects.requireNonNull(endpoint.getId(), "endpoint ID");

        return endpoints.put(id, endpoint);
    }

    /**
     * @return a list which contains the {@link ChannelBundle}s of this {@link ColibriConferenceIQ}.
     */
    public List<ChannelBundle> getChannelBundles()
    {
        return new LinkedList<>(channelBundles.values());
    }

    /**
     * @param channelBundleId The ID of the {@link ChannelBundle} to get.
     * @return The {@link ChannelBundle} identified by {@code channelBundleId}, or {@code null}.
     */
    public ChannelBundle getChannelBundle(String channelBundleId)
    {
        Objects.requireNonNull(channelBundleId, "channelBundleId");
        return channelBundles.get(channelBundleId);
    }

    /**
     * Finds {@link Endpoint} identified by given <code>endpointId</code>.
     *
     * @param endpointId <code>Endpoint</code> identifier.
     * @return {@link Endpoint} identified by given <code>endpointId</code> or <code>null</code> if not found.
     */
    public Endpoint getEndpoint(String endpointId)
    {
        if (endpointId == null) {
            return null;
        }
        return endpoints.get(endpointId);
    }

    /**
     * Returns an XML <code>String</code> representation of this <code>IQ</code>.
     *
     * @return an XML <code>String</code> representation of this <code>IQ</code>
     */
    @Override
    protected IQChildElementXmlStringBuilder getIQChildElementBuilder(IQChildElementXmlStringBuilder xml)
    {
        xml.optAttribute(ID_ATTR_NAME, getID());
        xml.optAttribute(GID_ATTR_NAME, getGID());
        xml.optAttribute(NAME_ATTR_NAME, name);

        List<Content> contents = getContents();
        List<ChannelBundle> channelBundles = getChannelBundles();
        List<Endpoint> endpoints = getEndpoints();

        boolean hasChildren = ((recording != null)
                || (rtcpTerminationStrategy != null)
                || (gracefulShutdown)
                || (contents.size() > 0)
                || (channelBundles.size() > 0)
                || (endpoints.size() > 0));

        if (!hasChildren) {
            xml.setEmptyElement();
        }
        else {
            xml.rightAngleBracket();
            for (Content content : contents)
                content.toXML(xml);
            for (ChannelBundle channelBundle : channelBundles)
                channelBundle.toXML(xml);
            for (Endpoint endpoint : endpoints)
                endpoint.toXML(xml);
            if (recording != null)
                recording.toXML(xml);
            if (rtcpTerminationStrategy != null)
                rtcpTerminationStrategy.toXML(xml);
            if (gracefulShutdown)
                xml.append(new GracefulShutdown().toXML(XmlEnvironment.EMPTY));
        }
        return xml;
    }

    /**
     * Returns a <code>Content</code> from the list of <code>Content</code>s of this <code>conference</code> IQ
     * which has a specific name. If no such <code>Content</code> exists, returns <code>null</code>.
     *
     * @param contentName the name of the <code>Content</code> to be returned
     * @return a <code>Content</code> from the list of <code>Content</code>s of this <code>conference</code> IQ
     * which has the specified <code>contentName</code> if such a <code>Content</code> exists;
     * otherwise, <code>null</code>
     */
    public Content getContent(String contentName)
    {
        for (Content content : getContents()) {
            if (contentName.equals(content.getName())) {
                return content;
            }
        }
        return null;
    }

    /**
     * Returns a list of the <code>Content</code>s included into this <code>conference</code> IQ.
     *
     * @return an unmodifiable <code>List</code> of the <code>Content</code>s included into this <code>conference</code> IQ
     */
    public List<Content> getContents()
    {
        return Collections.unmodifiableList(contents);
    }

    /**
     * Returns the list of <code>Endpoint</code>s included in this <code>ColibriConferenceIQ</code>.
     *
     * @return the list of <code>Endpoint</code>s included in this <code>ColibriConferenceIQ</code>.
     */
    public List<Endpoint> getEndpoints()
    {
        return new LinkedList<>(endpoints.values());
    }

    /**
     * Gets the ID of the conference represented by this IQ.
     *
     * @return the ID of the conference represented by this IQ
     */
    public String getID()
    {
        return id;
    }

    /**
     * @return the "global" ID of the conference represented by this IQ.
     */
    public String getGID()
    {
        return gid;
    }

    /**
     * Returns a <code>Content</code> from the list of <code>Content</code>s of this <code>conference</code> IQ
     * which has a specific name. If no such <code>Content</code> exists at the time of the invocation
     * of the method, initializes a new <code>Content</code> instance with the specified
     * <code>contentName</code> and includes it into this <code>conference</code> IQ.
     *
     * @param contentName the name of the <code>Content</code> to be returned
     * @return a <code>Content</code> from the list of <code>Content</code>s of this <code>conference</code> IQ
     * which has the specified <code>contentName</code>
     */
    public Content getOrCreateContent(String contentName)
    {
        Content content = getContent(contentName);
        if (content == null) {
            content = new Content(contentName);
            addContent(content);
        }
        return content;
    }

    /**
     * Gets the value of the recording field.
     *
     * @return the value of the recording field.
     */
    public Recording getRecording()
    {
        return recording;
    }

    public RTCPTerminationStrategy getRTCPTerminationStrategy()
    {
        return rtcpTerminationStrategy;
    }

    /**
     * Removes a specific {@link Content} instance from the list of <code>Content</code> instances
     * included into this <code>conference</code> IQ.
     *
     * @param content the <code>Content</code> instance to be removed from the list of <code>Content</code>
     * instances included into this <code>conference</code> IQ
     * @return <code>true</code> if the list of <code>Content</code> instances included into this
     * <code>conference</code> IQ has been modified as a result of the method call; otherwise,  <code>false</code>
     */
    public boolean removeContent(Content content)
    {
        return contents.remove(content);
    }

    /**
     * Sets the ID of the conference represented by this IQ.
     *
     * @param id the value to set.
     */
    public void setID(String id)
    {
        this.id = id;
    }

    /**
     * Sets the "global" ID of the conference represented by this IQ.
     *
     * @param gid the value to set.
     */
    public void setGID(String gid)
    {
        this.gid = gid;
    }

    /**
     * Sets the recording field.
     *
     * @param recording the value to set.
     */
    public void setRecording(Recording recording)
    {
        this.recording = recording;
    }

    public void setRTCPTerminationStrategy(RTCPTerminationStrategy rtcpTerminationStrategy)
    {
        this.rtcpTerminationStrategy = rtcpTerminationStrategy;
    }

    /**
     * Sets whether this IQ should contain the information about graceful shutdown in progress status.
     *
     * @param isGracefulShutdown <code>true</code> if graceful shutdown status should be indicated in this IQ.
     */
    public void setGracefulShutdown(boolean isGracefulShutdown)
    {
        this.gracefulShutdown = isGracefulShutdown;
    }

    /**
     * Returns <code>true</code> if graceful shutdown status info is indicated in this
     * <code>ColibriConferenceIQ</code> instance.
     */
    public boolean isGracefulShutdown()
    {
        return gracefulShutdown;
    }

    /**
     * The world readable name of the conference.
     *
     * @return name of the conference.
     */
    public Localpart getName()
    {
        return name;
    }

    /**
     * Sets name.
     *
     * @param name the name to set.
     */
    public void setName(Localpart name)
    {
        this.name = name;
    }

    /**
     * Represents a <code>channel</code> included into a <code>content</code> of a Jitsi Videobridge <code>conference</code> IQ.
     */
    public static class Channel extends ChannelCommon
    {
        /**
         * The name of the XML attribute of a <code>channel</code> which represents its direction.
         */
        public static final String DIRECTION_ATTR_NAME = "direction";

        /**
         * The XML element name of a <code>channel</code> of a <code>content</code> of a Jitsi Videobridge
         * <code>conference</code> IQ.
         */
        public static final String ELEMENT = "channel";

        /**
         * The XML name of the <code>host</code> attribute of a <code>channel</code> of a <code>content</code>
         * of a <code>conference</code> IQ which represents the value of the <code>host</code> property of
         * <code>ColibriConferenceIQ.Channel</code>.
         *
         * @deprecated The attribute is supported for the purposes of compatibility with legacy
         * versions of Jitsi and Jitsi Videobridge.
         */
        @Deprecated
        public static final String HOST_ATTR_NAME = "host";

        /**
         * The XML name of the <code>last-n</code> attribute of a video <code>channel</code> which specifies
         * the maximum number of video RTP streams to be sent from Jitsi Videobridge to the endpoint
         * associated with the video <code>channel</code>. The value of the <code>last-n</code> attribute is
         * a positive number.
         */
        public static final String LAST_N_ATTR_NAME = "last-n";

        /*
         * The XML name of the <code>simulcast-mode</code> attribute of a video <code>channel</code>.
         */
        public static final String SIMULCAST_MODE_ATTR_NAME = "simulcast-mode";
        /**
         * The XML name of the <code>receive-simulcast-layer</code> attribute of a video
         * <code>Channel</code> which specifies the target quality of the simulcast substreams to be
         * sent from Jitsi Videobridge to the endpoint associated with the video <code>Channel</code>.
         * The value of the <code>receive-simulcast-layer</code> attribute is an unsigned integer.
         * Typically used for debugging purposes.
         */
        public static final String RECEIVING_SIMULCAST_LAYER = "receive-simulcast-layer";

        /**
         * The XML name of the <code>packet-delay</code> attribute of
         * a <code>channel</code> of a <code>content</code> of a <code>conference</code> IQ
         * which represents the value of the {@link #packetDelay} property of
         * <code>ColibriConferenceIQ.Channel</code>.
         */
        public static final String PACKET_DELAY_ATTR_NAME = "packet-delay";

        /**
         * The XML name of the <code>rtcpport</code> attribute of a <code>channel</code> of a
         * <code>content</code> of a <code>conference</code> IQ which represents the value of the
         * <code>rtcpPort</code> property of <code>ColibriConferenceIQ.Channel</code>.
         *
         * @deprecated The attribute is supported for the purposes of compatibility with legacy
         * versions of Jitsi and Jitsi Videobridge.
         */
        @Deprecated
        public static final String RTCP_PORT_ATTR_NAME = "rtcpport";

        public static final String RTP_LEVEL_RELAY_TYPE_ATTR_NAME = "rtp-level-relay-type";

        /**
         * The XML name of the <code>rtpport</code> attribute of a <code>channel</code> of a
         * <code>content</code> of a <code>conference</code> IQ which represents the value of the
         * <code>rtpPort</code> property of <code>ColibriConferenceIQ.Channel</code>.
         *
         * @deprecated The attribute is supported for the purposes of compatibility with legacy
         * versions of Jitsi and Jitsi Videobridge.
         */
        @Deprecated
        public static final String RTP_PORT_ATTR_NAME = "rtpport";

        /**
         * The name of the XML element which is a child of the &lt;channel&gt; element and which
         * identifies/specifies an (RTP) SSRC which has been seen/received on the respective <code>Channel</code>.
         */
        public static final String SSRC_ELEMENT = "ssrc";

        /**
         * The direction of the <code>channel</code> represented by this instance.
         */
        private MediaDirection direction;

        /**
         * The host of the <code>channel</code> represented by this instance.
         *
         * @deprecated The field is supported for the purposes of compatibility with legacy versions
         * of Jitsi and Jitsi Videobridge.
         */
        @Deprecated
        private String host;

        /**
         * The maximum number of video RTP streams to be sent from Jitsi Videobridge to the endpoint
         * associated with this video <code>Channel</code>.
         */
        private Integer lastN;

        /**
         * The 'simulcast-mode' flag.
         */
        private SimulcastMode simulcastMode;

        /**
         * The amount of delay added to the RTP stream in a number of packets.
         */
        private Integer packetDelay;

        /**
         * The <code>payload-type</code> elements defined by XEP-0167: Jingle RTP Sessions associated
         * with this <code>channel</code>.
         */
        private final List<PayloadType> payloadTypes = new ArrayList<>();

        /**
         * The <code>rtp-hdrext</code> elements defined by XEP-0294: Jingle RTP Header Extensions
         * Negotiation associated with this channel.
         */
        private final Map<Integer, RtpHeader> rtpHeaders = new HashMap<>();

        /**
         * The target quality of the simulcast subStreams to be sent from Jitsi Videobridge to the
         * endpoint associated with this video <code>Channel</code>.
         */
        private Integer receivingSimulcastLayer;

        /**
         * The RTCP port of the <code>channel</code> represented by this instance.
         *
         * @deprecated The field is supported for the purposes of compatibility with legacy versions
         * of Jitsi and Jitsi Videobridge.
         */
        @Deprecated
        private int rtcpPort;

        /**
         * The type of RTP-level relay (in the terms specified by RFC 3550 &quot;RTP: A Transport
         * Protocol for Real-Time Applications&quot; in section 2.3 &quot;Mixers and
         * Translators&quot;) used for this <code>Channel</code>.
         */
        private RTPLevelRelayType rtpLevelRelayType;

        /**
         * The RTP port of the <code>channel</code> represented by this instance.
         *
         * @deprecated The field is supported for the purposes of compatibility with legacy versions
         * of Jitsi and Jitsi Videobridge.
         */
        @Deprecated
        private int rtpPort;

        /**
         * The <code>SdpSourceGroup</code>s of this channel.
         */
        private List<SdpSourceGroup> sourceGroups;

        /**
         * The <code>SdpSourceGroup</code>s of this channel.
         */
        private final List<SdpSource> sources = new LinkedList<>();
        /**
         * The list of (RTP) SSRCs which have been seen/received on this <code>Channel</code> by now.
         * These may exclude SSRCs which are no longer active. Set by the Jitsi Videobridge server,
         * not its clients.
         */
        private int[] ssrcs = NO_SSRCS;

        /**
         * Initializes a new <code>Channel</code> instance.
         */
        public Channel()
        {
            super(Channel.ELEMENT);
        }

        /**
         * Adds a <code>payload-type</code> element defined by XEP-0167: Jingle RTP Sessions to this <code>channel</code>.
         *
         * @param payloadType the <code>payload-type</code> element to be added to this <code>channel</code>
         * @return <code>true</code> if the list of <code>payload-type</code> elements associated with this
         * <code>channel</code> has been modified as part of the method call; otherwise, <code>false</code>
         * @throws NullPointerException if the specified <code>payloadType</code> is <code>null</code>
         */
        public boolean addPayloadType(PayloadType payloadType)
        {
            Objects.requireNonNull(payloadType, "payloadType");

            // Make sure that the COLIBRI namespace is used.
            // payloadType.setNamespace(null);
            // for (ParameterElement p : payloadType.getParameters())
            //    p.setNamespace(null);

            return !payloadTypes.contains(payloadType) && payloadTypes.add(payloadType);
        }

        /**
         * Adds an <code>rtp-hdrext</code> element defined by XEP-0294: Jingle RTP Header Extensions
         * Negotiation to this <code>Channel</code>.
         *
         * @param ext the <code>payload-type</code> element to be added to this <code>channel</code>
         * @throws NullPointerException if the specified <code>ext</code> is <code>null</code>
         */
        public void addRtpHeaderExtension(RtpHeader ext)
        {
            Objects.requireNonNull(ext, "ext");

            // Create a new instance, because we are going to modify the NS
            // Make sure that the parent namespace (COLIBRI) is used.
            RtpHeader newExt = RtpHeader.clone(ext);

            int id = -1;
            try {
                id = Integer.parseInt(newExt.getId());
            } catch (NumberFormatException ex) {
                ex.printStackTrace();
            }

            // Only accept valid extension IDs (4-bits, 0xF reserved)
            if (id < 0 || id > 14) {
                Timber.w("Failed to add an RTP header extension element with an invalid ID: %s", newExt.getId());
                return;
            }
            rtpHeaders.put(id, newExt);
        }

        /**
         * Adds a <code>SdpSourceGroup</code> to the list of sources of this channel.
         *
         * @param source the <code>SdpSourceGroup</code> to add to the list of sources of this channel
         * @return <code>true</code> if the list of sources of this channel changed as a result of the
         * execution of the method; otherwise, <code>false</code>
         */
        public synchronized boolean addSource(SdpSource source)
        {
            Objects.requireNonNull(source, "source");
            return !sources.contains(source) && sources.add(source);
        }

        /**
         * Adds a <code>SdpSourceGroup</code> to the list of source groups of this channel.
         *
         * @param sourceGroup the <code>SdpSourceGroup</code> to add to the list of sources of this channel
         * @return <code>true</code> if the list of sources of this channel changed as a result of the
         * execution of the method; otherwise, <code>false</code>
         */
        public synchronized boolean addSourceGroup(SdpSourceGroup sourceGroup)
        {
            Objects.requireNonNull(sourceGroup, "sourceGroup");
            if (sourceGroups == null)
                sourceGroups = new LinkedList<>();

            return !sourceGroups.contains(sourceGroup) && sourceGroups.add(sourceGroup);
        }

        /**
         * Adds a specific (RTP) SSRC to the list of SSRCs seen/received on this <code>Channel</code>.
         * Invoked by the Jitsi Videobridge server, not its clients.
         *
         * @param ssrc the (RTP) SSRC to be added to the list of SSRCs seen/received on this <code>Channel</code>
         * @return <code>true</code> if the list of SSRCs seen/received on this <code>Channel</code> has
         * been modified as part of the method call; otherwise, <code>false</code>
         */
        public synchronized boolean addSSRC(int ssrc)
        {
            // contains
            for (int ssrc1 : ssrcs) {
                if (ssrc1 == ssrc)
                    return false;
            }

            // add
            int[] newSSRCs = new int[ssrcs.length + 1];
            System.arraycopy(ssrcs, 0, newSSRCs, 0, ssrcs.length);
            newSSRCs[ssrcs.length] = ssrc;
            ssrcs = newSSRCs;
            return true;
        }

        /**
         * Gets the <code>direction</code> of this <code>Channel</code>.
         *
         * @return the <code>direction</code> of this <code>Channel</code>.
         */
        public MediaDirection getDirection()
        {
            return (direction == null) ? MediaDirection.SENDRECV : direction;
        }

        /**
         * Gets the IP address (as a <code>String</code> value) of the host on which the
         * <code>channel</code> represented by this instance has been allocated.
         *
         * @return a <code>String</code> value which represents the IP address of the host on which the
         * <code>channel</code> represented by this instance has been allocated
         * @deprecated The method is supported for the purposes of compatibility with legacy
         * versions of Jitsi and Jitsi Videobridge.
         */
        @Deprecated
        public String getHost()
        {
            return host;
        }

        /**
         * Gets the maximum number of video RTP streams to be sent from Jitsi Videobridge to the
         * endpoint associated with this video <code>Channel</code>.
         *
         * @return the maximum number of video RTP streams to be sent from Jitsi Videobridge to the
         * endpoint associated with this video <code>Channel</code>
         */
        public Integer getLastN()
        {
            return lastN;
        }

        /**
         * Gets the value of the 'simulcast-mode' flag.
         *
         * @return the value of the 'simulcast-mode' flag.
         */
        public SimulcastMode getSimulcastMode()
        {
            return simulcastMode;
        }

        /**
         * Returns an <code>Integer</code> which stands for the amount of delay
         * added to the RTP stream in a number of packets.
         *
         * @return <code>Integer</code> with the value or <code>null</code> if
         * unspecified.
         */
        public Integer getPacketDelay()
        {
            return packetDelay;
        }

        /**
         * Gets a list of <code>payload-type</code> elements defined by XEP-0167: Jingle RTP Sessions
         * added to this <code>channel</code>.
         *
         * @return an unmodifiable <code>List</code> of <code>payload-type</code> elements defined by
         * XEP-0167: Jingle RTP Sessions added to this <code>channel</code>
         */
        public List<PayloadType> getPayloadTypes()
        {
            return Collections.unmodifiableList(payloadTypes);
        }

        /**
         * Gets a list of <code>rtp-hdrext</code> elements defined by XEP-0294: Jingle RTP Header
         * Extensions Negotiation added to this <code>channel</code>.
         *
         * @return an unmodifiable <code>List</code> of <code>rtp-hdrext</code> elements defined by
         * XEP-0294: Jingle RTP Header Extensions Negotiation added to this <code>channel</code>
         */
        public Collection<RtpHeader> getRtpHeaders()
        {
            return Collections.unmodifiableCollection(rtpHeaders.values());
        }

        /**
         * Gets the target quality of the simulcast substreams to be sent from Jitsi Videobridge to
         * the endpoint associated with this video <code>Channel</code>.
         *
         * @return the target quality of the simulcast substreams to be sent from Jitsi Videobridge
         * to the endpoint associated with this video <code>Channel</code>.
         */
        public Integer getReceivingSimulcastLayer()
        {
            return receivingSimulcastLayer;
        }

        /**
         * Gets the port which has been allocated to this <code>channel</code> for the purposes of transmitting RTCP packets.
         *
         * @return the port which has been allocated to this <code>channel</code> for the purposes of
         * transmitting RTCP packets
         * @deprecated The method is supported for the purposes of compatibility with legacy
         * versions of Jitsi and Jitsi Videobridge.
         */
        @Deprecated
        public int getRTCPPort()
        {
            return rtcpPort;
        }

        /**
         * Gets the type of RTP-level relay (in the terms specified by RFC 3550 &quot;RTP: A
         * Transport Protocol for Real-Time Applications&quot; in section 2.3 &quot;Mixers and
         * Translators&quot;) used for this <code>Channel</code>.
         *
         * @return the type of RTP-level relay used for this <code>Channel</code>
         */
        public RTPLevelRelayType getRTPLevelRelayType()
        {
            return rtpLevelRelayType;
        }

        /**
         * Gets the port which has been allocated to this <code>channel</code> for the purposes of
         * transmitting RTP packets.
         *
         * @return the port which has been allocated to this <code>channel</code> for the purposes of
         * transmitting RTP packets
         * @deprecated The method is supported for the purposes of compatibility with legacy
         * versions of Jitsi and Jitsi Videobridge.
         */
        @Deprecated
        public int getRTPPort()
        {
            return rtpPort;
        }

        /**
         * Gets the list of <code>SourceGroupPacketExtensions</code>s which represent the source groups of this channel.
         *
         * @return a <code>List</code> of <code>SdpSourceGroup</code>s which represent the
         * source groups of this channel
         */
        public synchronized List<SdpSourceGroup> getSourceGroups()
        {
            return (sourceGroups == null) ? null : new ArrayList<>(sourceGroups);
        }

        /**
         * Gets the list of <code>SourcePacketExtensions</code>s which represent the sources of this channel.
         *
         * @return a <code>List</code> of <code>SdpSourceGroup</code>s which represent the sources of this channel
         */
        public synchronized List<SdpSource> getSources()
        {
            return new ArrayList<>(sources);
        }

        /**
         * Gets (a copy of) the list of (RTP) SSRCs seen/received on this <code>Channel</code>.
         *
         * @return an array of <code>int</code>s which represents (a copy of) the list of (RTP) SSRCs
         * seen/received on this <code>Channel</code>
         */
        public synchronized int[] getSSRCs()
        {
            return (ssrcs.length == 0) ? NO_SSRCS : ssrcs.clone();
        }

        @Override
        protected boolean hasContent()
        {
            List<PayloadType> payloadTypes = getPayloadTypes();
            if (!payloadTypes.isEmpty())
                return true;

            List<SdpSourceGroup> sourceGroups = getSourceGroups();
            if (sourceGroups != null && !getSourceGroups().isEmpty())
                return true;

            List<SdpSource> sources = getSources();
            if (!sources.isEmpty())
                return true;

            int[] ssrcs = getSSRCs();
            return (ssrcs.length != 0);
        }

        @Override
        protected IQChildElementXmlStringBuilder printAttributes(IQChildElementXmlStringBuilder xml)
        {
            // direction
            MediaDirection direction = getDirection();
            if ((direction != null) && (direction != MediaDirection.SENDRECV)) {
                xml.attribute(DIRECTION_ATTR_NAME, direction.toString());
            }

            // host
            xml.optAttribute(HOST_ATTR_NAME, getHost());

            // lastN
            Integer lastN = getLastN();
            if (lastN != null) {
                xml.attribute(LAST_N_ATTR_NAME, getLastN());
            }

            // packet-delay
            Integer packetDelay = getPacketDelay();
            if (packetDelay != null) {
                xml.attribute(PACKET_DELAY_ATTR_NAME, packetDelay);
            }
            // simulcastMode
            SimulcastMode simulcastMode = getSimulcastMode();
            if (simulcastMode != null) {
                xml.attribute(SIMULCAST_MODE_ATTR_NAME, simulcastMode.toString());
            }

            // rtcpPort
            int rtcpPort = getRTCPPort();
            if (rtcpPort > 0) {
                xml.attribute(RTCP_PORT_ATTR_NAME, rtcpPort);
            }

            // rtpLevelRelayType
            RTPLevelRelayType rtpLevelRelayType = getRTPLevelRelayType();
            if (rtpLevelRelayType != null) {
                xml.attribute(RTP_LEVEL_RELAY_TYPE_ATTR_NAME, rtpLevelRelayType.toString());
            }

            // rtpPort
            int rtpPort = getRTPPort();
            if (rtpPort > 0) {
                xml.attribute(RTP_PORT_ATTR_NAME, rtpPort);
            }
            return xml;
        }

        @Override
        protected IQChildElementXmlStringBuilder printContent(IQChildElementXmlStringBuilder xml)
        {
            List<PayloadType> payloadTypes = getPayloadTypes();
            Collection<RtpHeader> rtpHdrExtPacketExtensions = getRtpHeaders();
            List<SdpSource> sources = getSources();
            List<SdpSourceGroup> sourceGroups = getSourceGroups();
            int[] ssrcs = getSSRCs();

            for (PayloadType payloadType : payloadTypes)
                xml.append(payloadType.toXML(XmlEnvironment.EMPTY));

            for (RtpHeader ext : rtpHdrExtPacketExtensions)
                xml.append(ext.toXML(XmlEnvironment.EMPTY));

            for (SdpSource source : sources)
                xml.append(source.toXML(XmlEnvironment.EMPTY));

            if (sourceGroups != null && sourceGroups.size() != 0)
                for (SdpSourceGroup sourceGroup : sourceGroups)
                    xml.append(sourceGroup.toXML(XmlEnvironment.EMPTY));

            for (int ssrc : ssrcs) {
                xml.openElement(SSRC_ELEMENT);
                xml.append(Long.toString(ssrc & 0xFFFFFFFFL));
            }
            return xml;
        }

        /**
         * Removes a <code>payload-type</code> element defined by XEP-0167: Jingle RTP Sessions from this <code>channel</code>.
         *
         * @param payloadType the <code>payload-type</code> element to be removed from this <code>channel</code>
         * @return <code>true</code> if the list of <code>payload-type</code> elements associated with this
         * <code>channel</code> has been modified as part of the method call; otherwise, <code>false</code>
         */
        public boolean removePayloadType(PayloadType payloadType)
        {
            return payloadTypes.remove(payloadType);
        }

        /**
         * Removes a <code>rtp-hdrext</code> element defined by XEP-0294: Jingle RTP Header Extensions
         * Negotiation from this <code>channel</code>.
         *
         * @param ext the <code>rtp-hdrext</code> element to be removed from this <code>channel</code>
         */
        public void removeRtpHeaderExtension(RtpHeader ext)
        {
            int id;
            try {
                id = Integer.parseInt(ext.getId());
            } catch (NumberFormatException nfe) {
                Timber.w("Invalid ID: %s", ext.getId());
                return;
            }
            rtpHeaders.remove(id);
        }

        /**
         * Removes a <code>SdpSourceGroup</code> from the list of sources of this channel.
         *
         * @param source the <code>SdpSourceGroup</code> to remove from the list of sources of this channel
         * @return <code>true</code> if the list of sources of this channel changed as a result of the
         * execution of the method; otherwise, <code>false</code>
         */
        public synchronized boolean removeSource(SdpSource source)
        {
            return sources.remove(source);
        }

        /**
         * Removes a specific (RTP) SSRC from the list of SSRCs seen/received on this
         * <code>Channel</code>. Invoked by the Jitsi Videobridge server, not its clients.
         *
         * @param ssrc the (RTP) SSRC to be removed from the list of SSRCs seen/received on this <code>Channel</code>
         * @return <code>true</code> if the list of SSRCs seen/received on this <code>Channel</code> has
         * been modified as part of the method call; otherwise, <code>false</code>
         */
        public synchronized boolean removeSSRC(int ssrc)
        {
            if (ssrcs.length == 1) {
                if (ssrcs[0] == ssrc) {
                    ssrcs = NO_SSRCS;
                    return true;
                }
                else
                    return false;
            }
            else {
                for (int i = 0; i < ssrcs.length; i++) {
                    if (ssrcs[i] == ssrc) {
                        int[] newSSRCs = new int[ssrcs.length - 1];
                        if (i != 0)
                            System.arraycopy(ssrcs, 0, newSSRCs, 0, i);
                        if (i != newSSRCs.length) {
                            System.arraycopy(ssrcs, i + 1, newSSRCs, i, newSSRCs.length - i);
                        }
                        ssrcs = newSSRCs;
                        return true;
                    }
                }
                return false;
            }
        }

        /**
         * Sets the <code>direction</code> of this <code>Channel</code>
         *
         * @param direction the <code>MediaDirection</code> to set the <code>direction</code> of this <code>Channel</code> to.
         */
        public void setDirection(MediaDirection direction)
        {
            this.direction = direction;
        }

        /**
         * Sets the IP address (as a <code>String</code> value) of the host on which the
         * <code>channel</code> represented by this instance has been allocated.
         *
         * @param host a <code>String</code> value which represents the IP address of the host on which the
         * <code>channel</code> represented by this instance has been allocated
         * @deprecated The method is supported for the purposes of compatibility with legacy
         * versions of Jitsi and Jitsi Videobridge.
         */
        @Deprecated
        public void setHost(String host)
        {
            this.host = host;
        }

        /**
         * Sets the maximum number of video RTP streams to be sent from Jitsi Videobridge to the
         * endpoint associated with this video <code>Channel</code>.
         *
         * @param lastN the maximum number of video RTP streams to be sent from Jitsi Videobridge to the
         * endpoint associated with this video <code>Channel</code>
         */
        public void setLastN(Integer lastN)
        {
            this.lastN = lastN;
        }

        /**
         * Configures channel's packet delay which tells by how many packets
         * the RTP streams will be delayed.
         *
         * @param packetDelay an <code>Integer</code> value which stands for
         * the packet delay that will be set or <code>null</code> to leave undefined
         */
        public void setPacketDelay(Integer packetDelay)
        {
            this.packetDelay = packetDelay;
        }

        /**
         * Sets the value of the 'simulcast-mode' flag.
         *
         * @param simulcastMode the value to set.
         */
        public void setSimulcastMode(SimulcastMode simulcastMode)
        {
            this.simulcastMode = simulcastMode;
        }

        /**
         * Sets the target quality of the simulcast substreams to be sent from Jitsi Videobridge to
         * the endpoint associated with this video <code>Channel</code>.
         *
         * @param simulcastLayer the target quality of the simulcast substreams to be sent from Jitsi Videobridge
         * to the endpoint associated with this video <code>Channel</code>.
         */
        public void setReceivingSimulcastLayer(Integer simulcastLayer)
        {
            this.receivingSimulcastLayer = simulcastLayer;
        }

        /**
         * Sets the port which has been allocated to this <code>channel</code> for the purposes of
         * transmitting RTCP packets.
         *
         * @param rtcpPort the port which has been allocated to this <code>channel</code> for the purposes of
         * transmitting RTCP packets
         * @deprecated The method is supported for the purposes of compatibility with legacy
         * versions of Jitsi and Jitsi Videobridge.
         */
        @Deprecated
        public void setRTCPPort(int rtcpPort)
        {
            this.rtcpPort = rtcpPort;
        }

        /**
         * Sets the type of RTP-level relay (in the terms specified by RFC 3550 &quot;RTP: A
         * Transport Protocol for Real-Time Applications&quot; in section 2.3 &quot;Mixers and
         * Translators&quot;) used for this <code>Channel</code>.
         *
         * @param rtpLevelRelayType the type of RTP-level relay used for this <code>Channel</code>
         */
        public void setRTPLevelRelayType(RTPLevelRelayType rtpLevelRelayType)
        {
            this.rtpLevelRelayType = rtpLevelRelayType;
        }

        /**
         * Sets the type of RTP-level relay (in the terms specified by RFC 3550 &quot;RTP: A
         * Transport Protocol for Real-Time Applications&quot; in section 2.3 &quot;Mixers and
         * Translators&quot;) used for this <code>Channel</code>.
         *
         * @param s the type of RTP-level relay used for this <code>Channel</code>
         */
        public void setRTPLevelRelayType(String s)
        {
            setRTPLevelRelayType(RTPLevelRelayType.parseRTPLevelRelayType(s));
        }

        /**
         * Sets the port which has been allocated to this <code>channel</code> for the purposes of
         * transmitting RTP packets.
         *
         * @param rtpPort the port which has been allocated to this <code>channel</code> for the purposes of
         * transmitting RTP packets
         * @deprecated The method is supported for the purposes of compatibility with legacy
         * versions of Jitsi and Jitsi Videobridge.
         */
        @Deprecated
        public void setRTPPort(int rtpPort)
        {
            this.rtpPort = rtpPort;
        }

        /**
         * Sets the list of (RTP) SSRCs seen/received on this <code>Channel</code>.
         *
         * @param ssrcs the list of (RTP) SSRCs to be set as seen/received on this <code>Channel</code>
         */
        public void setSSRCs(int[] ssrcs)
        {
            /*
             * TODO Make sure that the SSRCs set on this instance do not contain duplicates.
             */
            this.ssrcs = ((ssrcs == null) || (ssrcs.length == 0)) ? NO_SSRCS : ssrcs.clone();
        }
    }

    /**
     * Represents a {@link Channel} of type "octo".
     */
    public static class OctoChannel extends Channel
    {
        /**
         * The value of the "type" attribute which corresponds to Octo channels.
         */
        public static final String TYPE = "octo";

        /**
         * The name of the "relay" child element of an {@link OctoChannel}.
         */
        public static final String RELAY_ELEMENT = "relay";

        /**
         * The name of the "id" attribute of child elements with name "relay".
         */
        public static final String RELAY_ID_ATTR_NAME = "id";

        /**
         * The list of relays of this {@link OctoChannel}.
         */
        private List<String> relays = new LinkedList<>();

        /**
         * Initializes a new {@link OctoChannel} instance.
         */
        public OctoChannel()
        {
            setType(TYPE);
        }

        /**
         * Sets the list of relays of this {@link OctoChannel}.
         *
         * @param relays the ids of the relays to set.
         */
        public void setRelays(List<String> relays)
        {
            this.relays = new LinkedList<>(relays);
        }

        /**
         * @return the list of relays of this {@link OctoChannel}.
         */
        public List<String> getRelays()
        {
            return relays;
        }

        /**
         * Adds a relay to this {@link OctoChannel}.
         *
         * @param relay the id of the relay to add.
         */
        public void addRelay(String relay)
        {
            if (!relays.contains(relay)) {
                relays.add(relay);
            }
        }

        /**
         * Removes a relay from this {@link OctoChannel}.
         *
         * @param relay the id of the relay to remove.
         */
        public void removeRelay(String relay)
        {
            relays.remove(relay);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected boolean hasContent()
        {
            return !relays.isEmpty() || super.hasContent();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected IQChildElementXmlStringBuilder printContent(IQChildElementXmlStringBuilder xml)
        {
            super.printContent(xml);
            for (String relay : relays) {
                xml.halfOpenElement(RELAY_ELEMENT);
                xml.attribute(ID_ATTR_NAME, relay);
                xml.closeEmptyElement();
            }
            return xml;
        }
    }

    /**
     * Represents a "channel-bundle" element.
     */
    public static class ChannelBundle
    {
        /**
         * The name of the "channel-bundle" element.
         */
        public static final String ELEMENT = "channel-bundle";

        /**
         * The name of the "id" attribute.
         */
        public static final String ID_ATTR_NAME = "id";

        /**
         * The ID of this <code>ChannelBundle</code>.
         */
        private String id;

        /**
         * The transport element of this <code>ChannelBundle</code>.
         */
        private IceUdpTransport transport;

        /**
         * Initializes a new <code>ChannelBundle</code> with the given ID.
         *
         * @param id the ID.
         */
        public ChannelBundle(String id)
        {
            this.id = id;
        }

        /**
         * Returns the ID of this <code>ChannelBundle</code>.
         *
         * @return the ID of this <code>ChannelBundle</code>.
         */
        public String getId()
        {
            return id;
        }

        /**
         * Returns the transport element of this <code>ChannelBundle</code>.
         *
         * @return the transport element of this <code>ChannelBundle</code>.
         */
        public IceUdpTransport getTransport()
        {
            return transport;
        }

        /**
         * Sets the ID of this <code>ChannelBundle</code>.
         *
         * @param id the ID to set.
         */
        public void setId(String id)
        {
            this.id = id;
        }

        /**
         * Sets the transport element of this <code>ChannelBundle</code>.
         *
         * @param transport the transport to set.
         */
        public void setTransport(IceUdpTransport transport)
        {
            this.transport = transport;
        }

        /**
         * Appends an XML representation of this <code>ChannelBundle</code> to <code>xml</code>.
         *
         * @param xml the <code>StringBuilder</code> to append to.
         */
        public IQChildElementXmlStringBuilder toXML(IQChildElementXmlStringBuilder xml)
        {
            xml.halfOpenElement(ELEMENT);
            xml.attribute(ID_ATTR_NAME, id);

            if (transport != null) {
                xml.rightAngleBracket();
                xml.append(transport.toXML(XmlEnvironment.EMPTY));
                xml.closeElement(ELEMENT);
            }
            else {
                xml.closeEmptyElement();
            }
            return xml;
        }
    }

    /**
     * Class contains common code for both <code>Channel</code> and <code>SctpConnection</code> IQ classes.
     *
     * @author Pawel Domas
     */
    public static abstract class ChannelCommon
    {
        /**
         * The name of the "channel-bundle-id" attribute.
         */
        public static final String CHANNEL_BUNDLE_ID_ATTR_NAME = "channel-bundle-id";

        /**
         * The XML name of the <code>endpoint</code> attribute which specifies the optional identifier
         * of the endpoint of the conference participant associated with a <code>channel</code>. The
         * value of the <code>endpoint</code> attribute is an opaque <code>String</code> from the point of
         * view of Jitsi Videobridge.
         */
        public static final String ENDPOINT_ATTR_NAME = "endpoint";

        /**
         * The XML name of the <code>expire</code> attribute of a <code>channel</code> of a <code>content</code>
         * of a <code>conference</code> IQ which represents the value of the <code>expire</code> property of
         * <code>ColibriConferenceIQ.Channel</code>.
         */
        public static final String EXPIRE_ATTR_NAME = "expire";

        /**
         * The value of the <code>expire</code> property of <code>ColibriConferenceIQ.Channel</code> which
         * indicates that no actual value has been specified for the property in question.
         */
        public static final int EXPIRE_NOT_SPECIFIED = -1;

        /**
         * The XML name of the <code>id</code> attribute of a <code>channel</code> of a <code>content</code> of
         * a <code>conference</code> IQ which represents the value of the <code>id</code> property of
         * <code>ColibriConferenceIQ.Channel</code>.
         */
        public static final String ID_ATTR_NAME = "id";

        /**
         * The XML name of the <code>initiator</code> attribute of a <code>channel</code> of a
         * <code>content</code> of a <code>conference</code> IQ which represents the value of the
         * <code>initiator</code> property of <code>ColibriConferenceIQ.Channel</code>.
         */
        public static final String INITIATOR_ATTR_NAME = "initiator";

        /**
         * The name of the "type" attribute.
         */
        public static final String TYPE_ATTR_NAME = "type";

        /**
         * The channel-bundle-id attribute of this <code>CommonChannel</code>.
         */
        private String channelBundleId = null;

        /**
         * XML element name.
         */
        private String elementName;

        /**
         * The identifier of the endpoint of the conference participant associated with this <code>Channel</code>.
         */
        private String endpoint;

        /**
         * The optional type of the channel.
         */
        private String type;

        /**
         * The number of seconds of inactivity after which the <code>channel</code>
         * represented by this instance expires.
         */
        private int expire = EXPIRE_NOT_SPECIFIED;

        /**
         * The ID of the <code>channel</code> represented by this instance.
         */
        private String id;

        /**
         * The indicator which determines whether the conference focus is the initiator/offerer (as
         * opposed to the responder/answerer) of the media negotiation associated with this instance.
         */
        private Boolean initiator;

        private IceUdpTransport transport;

        /**
         * Initializes this class with given XML <code>elementName</code>.
         *
         * @param elementName XML element name to be used for producing XML representation of derived IQ class.
         */
        protected ChannelCommon(String elementName)
        {
            this.elementName = elementName;
        }

        /**
         * Get the channel-bundle-id attribute of this <code>CommonChannel</code>.
         *
         * @return the channel-bundle-id attribute of this <code>CommonChannel</code>.
         */
        public String getChannelBundleId()
        {
            return channelBundleId;
        }

        /**
         * Gets the identifier of the endpoint of the conference participant associated with this
         * <code>Channel</code>.
         *
         * @return the identifier of the endpoint of the conference participant associated with this <code>Channel</code>
         */
        public String getEndpoint()
        {
            return endpoint;
        }

        /**
         * @return optional type of this channel.
         */
        public String getType()
        {
            return type;
        }

        /**
         * Gets the number of seconds of inactivity after which the
         * <code>channel</code> represented by this instance expires.
         *
         * @return the number of seconds of inactivity after which the <code>channel</code> represented
         * by this instance expires
         */
        public int getExpire()
        {
            return expire;
        }

        /**
         * Gets the ID of the <code>channel</code> represented by this instance.
         *
         * @return the ID of the <code>channel</code> represented by this instance
         */
        public String getID()
        {
            return id;
        }

        public IceUdpTransport getTransport()
        {
            return transport;
        }

        /**
         * Indicates whether there are some contents that should be printed as
         * child elements of this IQ. If <code>true</code> is returned
         * {@link #printContent(IQChildElementXmlStringBuilder)} method will be
         * called when XML representation of this IQ is being constructed.
         *
         * @return <code>true</code> if there are content to be printed as child elements of this IQ or
         * <code>false</code> otherwise.
         */
        protected abstract boolean hasContent();

        /**
         * Gets the indicator which determines whether the conference focus is the initiator/offerer
         * (as opposed to the responder/answerer) of the media negotiation associated with this instance.
         *
         * @return {@link Boolean#TRUE} if the conference focus is the initiator/offerer of the
         * media negotiation associated with this instance, {@link Boolean#FALSE} if the
         * conference focus is the responder/answerer or <code>null</code> if the
         * <code>initiator</code> state is unspecified
         */
        public Boolean isInitiator()
        {
            return initiator;
        }

        /**
         * Derived class implements this method in order to print additional attributes to main XML element.
         *
         * @param xml <the <code>StringBuilder</code> to which the XML <code>String</code> representation of
         * this <code>Channel</code> is to be appended</code>
         */
        protected abstract IQChildElementXmlStringBuilder printAttributes(IQChildElementXmlStringBuilder xml);

        /**
         * Implement in order to print content child elements of this IQ using given
         * <code>StringBuilder</code>. Called during construction of XML representation if
         * {@link #hasContent()} returns <code>true</code>.
         *
         * @param xml the <code>StringBuilder</code> to which the XML <code>String</code> representation of this
         * <code>Channel</code> is to be appended</code></code>.
         */
        protected abstract IQChildElementXmlStringBuilder printContent(IQChildElementXmlStringBuilder xml);

        /**
         * Sets the channel-bundle-id attribute of this <code>CommonChannel</code>.
         *
         * @param channelBundleId the value to set.
         */
        public void setChannelBundleId(String channelBundleId)
        {
            this.channelBundleId = channelBundleId;
        }

        /**
         * Sets the identifier of the endpoint of the conference participant associated with this <code>Channel</code>.
         *
         * @param endpoint the identifier of the endpoint of the conference participant associated with this
         * <code>Channel</code>
         */
        public void setEndpoint(String endpoint)
        {
            this.endpoint = endpoint;
        }

        /**
         * Sets the optional type of this channel.
         *
         * @param type the value to set.
         */
        public void setType(String type)
        {
            this.type = type;
        }

        /**
         * Sets the number of seconds of inactivity after which the <code>channel</code> represented by this instance expires.
         *
         * @param expire the number of seconds of activity after which the <code>channel</code> represented by
         * this instance expires
         * @throws IllegalArgumentException if the value of the specified <code>expire</code> is other than
         * {@link #EXPIRE_NOT_SPECIFIED} and negative
         */
        public void setExpire(int expire)
        {
            if ((expire != EXPIRE_NOT_SPECIFIED) && (expire < 0))
                throw new IllegalArgumentException("expire");
            this.expire = expire;
        }

        /*
         * Sets the ID of the <code>channel</code> represented by this instance.
         *
         * @param id the ID of the <code>channel</code> represented by this instance
         */
        public void setID(String id)
        {
            this.id = id;
        }

        /**
         * Sets the indicator which determines whether the conference focus is the initiator/offerer
         * (as opposed to the responder/answerer) of the media negotiation associated with this instance.
         *
         * @param initiator {@link Boolean#TRUE} if the conference focus is the initiator/offerer of the media
         * negotiation associated with this instance, {@link Boolean#FALSE} if the conference
         * focus is the responder/answerer or <code>null</code> if the <code>initiator</code> state is to be unspecified
         */
        public void setInitiator(Boolean initiator)
        {
            this.initiator = initiator;
        }

        public void setTransport(IceUdpTransport transport)
        {
            this.transport = transport;
        }

        /**
         * Appends the XML <code>String</code> representation of this <code>Channel</code> to a specific <code>StringBuilder</code>.
         *
         * @param xml the <code>StringBuilder</code> to which the XML <code>String</code> representation of this
         * <code>Channel</code> is to be appended
         */
        public IQChildElementXmlStringBuilder toXML(IQChildElementXmlStringBuilder xml)
        {
            xml.halfOpenElement(elementName);

            // endpoint
            xml.optAttribute(ENDPOINT_ATTR_NAME, getEndpoint());

            // expire
            xml.optIntAttribute(EXPIRE_ATTR_NAME, getExpire());

            // id
            xml.optAttribute(ID_ATTR_NAME, getID());
            xml.optAttribute(TYPE_ATTR_NAME, getType());

            // initiator
            xml.optBooleanAttribute(INITIATOR_ATTR_NAME, isInitiator() == null ? false : isInitiator());
            xml.optAttribute(CHANNEL_BUNDLE_ID_ATTR_NAME, getChannelBundleId());

            // Print derived class attributes
            printAttributes(xml);

            IceUdpTransport transport = getTransport();
            boolean hasTransport = (transport != null);
            if (hasTransport || hasContent()) {
                xml.rightAngleBracket();
                if (hasContent()) {
                    printContent(xml);
                }
                if (hasTransport) {
                    xml.append(transport.toXML(XmlEnvironment.EMPTY));
                }
                xml.closeElement(elementName);
            }
            else {
                xml.closeEmptyElement();
            }
            return xml;
        }
    }

    /**
     * Represents a <code>content</code> included into a Jitsi Videobridge <code>conference</code> IQ.
     */
    public static class Content
    {
        /**
         * The XML element name of a <code>content</code> of a Jitsi Videobridge <code>conference</code> IQ.
         */
        public static final String ELEMENT = "content";

        /**
         * The XML name of the <code>name</code> attribute of a <code>content</code> of a
         * <code>conference</code> IQ which represents the <code>name</code> property of <code>ColibriConferenceIQ.Content</code>.
         */
        public static final String NAME_ATTR_NAME = "name";

        /**
         * The list of {@link Channel}s included into this <code>content</code> of a <code>conference</code> IQ.
         */
        private final List<Channel> channels = new LinkedList<>();

        /**
         * The name of the <code>content</code> represented by this instance.
         */
        private String name;

        /**
         * The list of {@link SctpConnection}s included into this <code>content</code> of a <code>conference</code> IQ.
         */
        private final List<SctpConnection> sctpConnections = new LinkedList<>();

        /**
         * Initializes a new <code>Content</code> instance without a name and channels.
         */
        public Content()
        {
        }

        /**
         * Initializes a new <code>Content</code> instance with a specific name and without channels.
         *
         * @param name the name to initialize the new instance with
         */
        public Content(String name)
        {
            setName(name);
        }

        /**
         * Adds a specific <code>Channel</code> to the list of <code>Channel</code>s included into this <code>Content</code>.
         *
         * @param channel the <code>Channel</code> to be included into this <code>Content</code>
         * @return <code>true</code> if the list of <code>Channel</code>s included into this
         * <code>Content</code> was modified as a result of the execution of the method;
         * otherwise, <code>false</code>
         * @throws NullPointerException if the specified <code>channel</code> is <code>null</code>
         */
        public boolean addChannel(Channel channel)
        {
            Objects.requireNonNull(channel, "channel");
            return !channels.contains(channel) && channels.add(channel);
        }

        /**
         * Adds <code>ChannelCommon</code> to this <code>Content</code>.
         *
         * @param channelCommon {@link ChannelCommon} instance to be added to this content.
         * @return <code>true</code> if given <code>channelCommon</code> has been
         * actually added to this <code>Content</code> instance.
         */
        public boolean addChannelCommon(ChannelCommon channelCommon)
        {
            if (channelCommon instanceof Channel) {
                return addChannel((Channel) channelCommon);
            }
            else {
                return addSctpConnection((SctpConnection) channelCommon);
            }
        }

        /**
         * Adds a specific <code>SctpConnection</code> to the list of <code>SctpConnection</code>s included
         * into this <code>Content</code>.
         *
         * @param conn the <code>SctpConnection</code> to be included into this <code>Content</code>
         * @return <code>true</code> if the list of <code>SctpConnection</code>s included into this
         * <code>Content</code> was modified as a result of the execution of the method; otherwise, <code>false</code>
         * @throws NullPointerException if the specified <code>conn</code> is <code>null</code>
         */
        public boolean addSctpConnection(SctpConnection conn)
        {
            Objects.requireNonNull(conn, "conn");
            return !sctpConnections.contains(conn) && sctpConnections.add(conn);
        }

        /**
         * Gets the <code>Channel</code> at a specific index/position within the list of
         * <code>Channel</code>s included in this <code>Content</code>.
         *
         * @param channelIndex the index/position within the list of <code>Channel</code>s included in this
         * <code>Content</code> of the <code>Channel</code> to be returned
         * @return the <code>Channel</code> at the specified <code>channelIndex</code> within the list of
         * <code>Channel</code>s included in this <code>Content</code>
         */
        public Channel getChannel(int channelIndex)
        {
            return getChannels().get(channelIndex);
        }

        /**
         * Gets a <code>Channel</code> which is included into this <code>Content</code> and which has a
         * specific ID.
         *
         * @param channelID the ID of the <code>Channel</code> included into this <code>Content</code> to be returned
         * @return the <code>Channel</code> which is included into this <code>Content</code> and which has
         * the specified <code>channelID</code> if such a <code>Channel</code> exists; otherwise, <code>null</code>
         */
        public Channel getChannel(String channelID)
        {
            for (Channel channel : getChannels()) {
                if (channelID.equals(channel.getID()))
                    return channel;
            }
            return null;
        }

        /**
         * Finds an SCTP connection identified by given <code>connectionID</code>.
         *
         * @param connectionID the ID of the SCTP connection to find.
         * @return <code>SctpConnection</code> instance identified by given ID or <code>null</code> if no
         * such connection is contained in this IQ.
         */
        public SctpConnection getSctpConnection(String connectionID)
        {
            for (SctpConnection conn : getSctpConnections())
                if (connectionID.equals(conn.getID()))
                    return conn;
            return null;
        }

        /**
         * Gets the number of <code>Channel</code>s included into/associated with this <code>Content</code>.
         *
         * @return the number of <code>Channel</code>s included into/associated with this <code>Content</code>
         */
        public int getChannelCount()
        {
            return getChannels().size();
        }

        /**
         * Gets a list of the <code>Channel</code> included into/associated with this <code>Content</code>.
         *
         * @return an unmodifiable <code>List</code> of the <code>Channel</code>s included into/associated
         * with this <code>Content</code>
         */
        public List<Channel> getChannels()
        {
            return Collections.unmodifiableList(channels);
        }

        /**
         * Gets the name of the <code>content</code> represented by this instance.
         *
         * @return the name of the <code>content</code> represented by this instance
         */
        public String getName()
        {
            return name;
        }

        /**
         * Gets a list of the <code>SctpConnection</code>s included into/associated with this
         * <code>Content</code>.
         *
         * @return an unmodifiable <code>List</code> of the <code>SctpConnection</code>s included
         * into/associated with this <code>Content</code>
         */
        public List<SctpConnection> getSctpConnections()
        {
            return Collections.unmodifiableList(sctpConnections);
        }

        /**
         * Removes a specific <code>Channel</code> from the list of <code>Channel</code>s included into this
         * <code>Content</code>.
         *
         * @param channel the <code>Channel</code> to be excluded from this <code>Content</code>
         * @return <code>true</code> if the list of <code>Channel</code>s included into this
         * <code>Content</code> was modified as a result of the execution of the method; otherwise, <code>false</code>
         */
        public boolean removeChannel(Channel channel)
        {
            return channels.remove(channel);
        }

        /**
         * Sets the name of the <code>content</code> represented by this instance.
         *
         * @param name the name of the <code>content</code> represented by this instance
         * @throws NullPointerException if the specified <code>name</code> is <code>null</code>
         */
        public void setName(String name)
        {
            Objects.requireNonNull(name, "name");

            this.name = name;
        }

        /**
         * Appends the XML <code>String</code> representation of this <code>Content</code> to a specific
         * <code>StringBuilder</code>.
         *
         * @param xml the <code>StringBuilder</code> to which the XML <code>String</code> representation of this
         * <code>Content</code> is to be appended
         */
        public IQChildElementXmlStringBuilder toXML(IQChildElementXmlStringBuilder xml)
        {
            xml.halfOpenElement(ELEMENT);
            xml.attribute(NAME_ATTR_NAME, getName());

            List<Channel> channels = getChannels();
            List<SctpConnection> connections = getSctpConnections();

            if (channels.size() == 0 && connections.size() == 0) {
                xml.closeEmptyElement();
            }
            else {
                xml.rightAngleBracket();
                for (Channel channel : channels) {
                    channel.toXML(xml);
                }

                for (SctpConnection conn : connections) {
                    conn.toXML(xml);
                }
                xml.closeElement(ELEMENT);
            }
            return xml;
        }

        /**
         * Removes given SCTP connection from this IQ.
         *
         * @param connection the SCTP connection instance to be removed.
         * @return <code>true</code> if given <code>connection</code> was contained in this IQ and has been removed successfully.
         */
        public boolean removeSctpConnection(SctpConnection connection)
        {
            return sctpConnections.remove(connection);
        }
    }

    /**
     * Represents an 'endpoint' element.
     */
    public static class Endpoint
    {
        /**
         * The name of the 'displayname' attribute.
         */
        public static final String DISPLAYNAME_ATTR_NAME = "displayname";

        /**
         * The name of the 'endpoint' element.
         */
        public static final String ELEMENT = "endpoint";

        /**
         * The name of the 'id' attribute.
         */
        public static final String ID_ATTR_NAME = "id";

        /**
         * The name of the 'stats-id' attribute.
         */
        public static final String STATS_ID_ATTR_NAME = "stats-id";

        /**
         * The 'display name' of this <code>Endpoint</code>.
         */
        private String displayName;

        /**
         * The 'id' of this <code>Endpoint</code>.
         */
        private String id;

        /**
         * The 'stats-id' of this <code>Endpoint</code>.
         */
        private String statsId;

        /**
         * Initializes a new <code>Endpoint</code> with the given ID and display
         * name.
         *
         * @param id the ID.
         * @param statsId stats ID value
         * @param displayName the display name.
         */
        public Endpoint(String id, String statsId, String displayName)
        {
            this.id = id;
            this.statsId = statsId;
            this.displayName = displayName;
        }

        /**
         * Returns the display name of this <code>Endpoint</code>.
         *
         * @return the display name of this <code>Endpoint</code>.
         */
        public String getDisplayName()
        {
            return displayName;
        }

        /**
         * Returns the ID of this <code>Endpoint</code>.
         *
         * @return the ID of this <code>Endpoint</code>.
         */
        public String getId()
        {
            return id;
        }

        /**
         * Returns the stats ID of this <code>Endpoint</code>.
         *
         * @return the stats ID of this <code>Endpoint</code>.
         */
        public String getStatsId()
        {
            return statsId;
        }

        /**
         * Sets the display name of this <code>Endpoint</code>.
         *
         * @param displayName the display name to set.
         */
        public void setDisplayName(String displayName)
        {
            this.displayName = displayName;
        }

        /**
         * Sets the ID of this <code>Endpoint</code>.
         *
         * @param id the ID to set.
         */
        public void setId(String id)
        {
            this.id = id;
        }

        /**
         * Sets the stats ID of this <code>Endpoint</code>.
         *
         * @param statsId the stats ID to set.
         */
        public void setStatsId(String statsId)
        {
            this.statsId = statsId;
        }

        /**
         * Appends the XML <code>String</code> representation of this
         * <code>Endpoint</code> to <code>xml</code>.
         *
         * @param xml the <code>StringBuilder</code> to which the XML
         * <code>String</code> representation of this <code>Endpoint</code> is to be appended
         */
        public IQChildElementXmlStringBuilder toXML(
                IQChildElementXmlStringBuilder xml)
        {
            xml.halfOpenElement(ELEMENT);
            xml.attribute(ID_ATTR_NAME, id);

            xml.optAttribute(DISPLAYNAME_ATTR_NAME, displayName);
            xml.optAttribute(STATS_ID_ATTR_NAME, statsId);
            xml.closeEmptyElement();
            return xml;
        }
    }

    /**
     * Represents a <code>recording</code> element.
     */
    public static class Recording
    {
        /**
         * The XML name of the <code>recording</code> element.
         */
        public static final String ELEMENT = "recording";

        /**
         * The XML name of the <code>path</code> attribute.
         */
        public static final String DIRECTORY_ATTR_NAME = "directory";

        /**
         * The XML name of the <code>state</code> attribute.
         */
        public static final String STATE_ATTR_NAME = "state";

        /**
         * The XML name of the <code>token</code> attribute.
         */
        public static final String TOKEN_ATTR_NAME = "token";

        /**
         * The target directory.
         */
        private String directory;

        /**
         * State of the recording..
         */
        private final State state;

        /**
         * Access token.
         */
        private String token;

        /**
         * Construct new recording element.
         *
         * @param state the state as string
         */
        public Recording(String state)
        {
            this.state = State.fromString(state);
        }

        /**
         * Construct new recording element.
         *
         * @param state recording state ON | OFF | PENDING
         */
        public Recording(State state)
        {
            this.state = state;
        }

        /**
         * Construct new recording element.
         *
         * @param state the state as string
         * @param token the token to authenticate
         */
        public Recording(String state, String token)
        {
            this(State.fromString(state), token);
        }

        /**
         * Construct new recording element.
         *
         * @param state the state
         * @param token the token to authenticate
         */
        public Recording(State state, String token)
        {
            this(state);
            this.token = token;
        }

        public String getDirectory()
        {
            return directory;
        }

        public State getState()
        {
            return state;
        }

        public String getToken()
        {
            return token;
        }

        public void setToken(String token)
        {
            this.token = token;
        }

        public void setDirectory(String directory)
        {
            this.directory = directory;
        }

        public IQChildElementXmlStringBuilder toXML(IQChildElementXmlStringBuilder xml)
        {
            xml.halfOpenElement(ELEMENT);
            xml.attribute(STATE_ATTR_NAME, state);
            xml.optAttribute(TOKEN_ATTR_NAME, token);
            xml.optAttribute(DIRECTORY_ATTR_NAME, directory);
            xml.closeEmptyElement();
            return xml;
        }

        /**
         * The recording state.
         */
        public enum State
        {
            /**
             * Recording is started.
             */
            ON("on"),
            /**
             * Recording is stopped.
             */
            OFF("off"),
            /**
             * Recording is pending. Record has been requested but no conference has been
             * established and it will be started once this is done.
             */
            PENDING("pending");

            /**
             * The name.
             */
            private String name;

            /**
             * Constructs new state.
             *
             * @param name
             */
            private State(String name)
            {
                this.name = name;
            }

            /**
             * Returns state name.
             *
             * @return returns state name.
             */
            @NonNull
            public String toString()
            {
                return name;
            }

            /**
             * Parses state.
             *
             * @param s state name.
             * @return the state found.
             */
            public static State fromString(String s)
            {
                if (ON.toString().equalsIgnoreCase(s))
                    return ON;
                else if (PENDING.toString().equalsIgnoreCase(s))
                    return PENDING;
                return OFF;
            }
        }
    }

    /**
     * Packet extension indicating graceful shutdown in progress status.
     */
    public static class GracefulShutdown extends AbstractExtensionElement
    {
        public static final String ELEMENT = "graceful-shutdown";

        public static final String NAMESPACE = ColibriConferenceIQ.NAMESPACE;

        public static final QName QNAME = new QName(NAMESPACE, ELEMENT);

        public GracefulShutdown()
        {
            super(ELEMENT, ColibriConferenceIQ.NAMESPACE);
        }
    }

    public static class RTCPTerminationStrategy
    {
        public static final String ELEMENT = "rtcp-termination-strategy";

        public static final String NAME_ATTR_NAME = "name";

        private String name;

        public String getName()
        {
            return name;
        }

        public void setName(String name)
        {
            this.name = name;
        }

        public IQChildElementXmlStringBuilder toXML(IQChildElementXmlStringBuilder xml)
        {
            xml.halfOpenElement(ELEMENT);
            xml.attribute(NAME_ATTR_NAME, name);
            xml.closeEmptyElement();
            return xml;
        }
    }

    /**
     * Represents a <code>SCTP connection</code> included into a <code>content</code> of a Jitsi Videobridge
     * <code>conference</code> IQ.
     *
     * @author Pawel Domas
     */
    public static class SctpConnection extends ChannelCommon
    {
        /**
         * The XML element name of a <code>content</code> of a Jitsi Videobridge <code>conference</code> IQ.
         */
        public static final String ELEMENT = "sctpconnection";

        /**
         * The XML name of the <code>port</code> attribute of a <code>SctpConnection</code> of a
         * <code>conference</code> IQ which represents the SCTP port property of
         * <code>ColibriConferenceIQ.SctpConnection</code>.
         */
        public static final String PORT_ATTR_NAME = "port";

        /**
         * SCTP port attribute. 5000 by default.
         */
        private int port = 5000;

        /**
         * Initializes a new <code>SctpConnection</code> instance without an endpoint name and with
         * default port value set.
         */
        public SctpConnection()
        {
            super(SctpConnection.ELEMENT);
        }

        /**
         * Gets the SCTP port of the <code>SctpConnection</code> described by this instance.
         *
         * @return the SCTP port of the <code>SctpConnection</code> represented by this instance.
         */
        public int getPort()
        {
            return port;
        }

        /**
         * {@inheritDoc}
         *
         * No content other than transport for <code>SctpConnection</code>.
         */
        @Override
        protected boolean hasContent()
        {
            return false;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected IQChildElementXmlStringBuilder printAttributes(IQChildElementXmlStringBuilder xml)
        {
            xml.attribute(PORT_ATTR_NAME, getPort());
            return xml;
        }

        @Override
        protected IQChildElementXmlStringBuilder printContent(IQChildElementXmlStringBuilder xml)
        {
            // No other content than the transport shared from ChannelCommon
            return xml;
        }

        /**
         * Sets the SCTP port of the <code>SctpConnection</code> represented by this instance.
         *
         * @param port the SCTP port of the <code>SctpConnection</code> represented by this instance
         */
        public void setPort(int port)
        {
            this.port = port;
        }
    }
}
