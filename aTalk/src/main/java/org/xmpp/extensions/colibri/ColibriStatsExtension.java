/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.xmpp.extensions.colibri;

import org.xmpp.extensions.AbstractExtensionElement;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.util.XmlStringBuilder;

import java.util.Collections;
import java.util.List;

import timber.log.Timber;

/**
 * Implements the Jitsi Videobridge <tt>stats</tt> extension within COnferencing with LIghtweight
 * BRIdging that will provide various statistics.
 *
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
public class ColibriStatsExtension extends AbstractExtensionElement
{
    /**
     * The XML element name of the Jitsi Videobridge <tt>stats</tt> extension.
     */
    public static final String ELEMENT = "stats";

    /**
     * The XML COnferencing with LIghtweight BRIdging namespace of the Jitsi Videobridge <tt>stats</tt> extension.
     */
    public static final String NAMESPACE = "http://jitsi.org/protocol/colibri";

    /**
     * The name of the bit rate statistic for download.
     */
    public static final String BITRATE_DOWNLOAD = "bit_rate_download";

    /**
     * The name of the bit rate statistic for upload.
     */
    public static final String BITRATE_UPLOAD = "bit_rate_upload";

    /**
     * The name of the number of conferences statistic. Its runtime type is {@code Integer}.
     */
    public static final String CONFERENCES = "conferences";

    /**
     * The number of conferences with Octo enabled.
     */
    public static final String OCTO_CONFERENCES = "octo_conferences";

    /**
     * The number of inactive conferences (with no endpoints sending audio or
     * video).
     */
    public static final String INACTIVE_CONFERENCES = "inactive_conferences";

    /**
     * The number of conferences that have two endpoints connected peer-to-peer.
     */
    public static final String P2P_CONFERENCES = "p2p_conferences";

    /**
     * The name of the conference sizes statistic.
     */
    public static final String CONFERENCE_SIZES = "conference_sizes";

    /**
     * The number of conferences by the number of audio senders.
     */
    public static final String CONFERENCES_BY_AUDIO_SENDERS = "conferences_by_audio_senders";

    /**
     * The number of conferences by the number of video senders.
     */
    public static final String CONFERENCES_BY_VIDEO_SENDERS = "conferences_by_video_senders";

    /**
     * The name of the CPU usage statistic.
     */
    public static final String CPU_USAGE = "cpu_usage";

    /**
     * The name of the aggregate jitter statistic.
     */
    public static final String JITTER_AGGREGATE = "jitter_aggregate";

    /**
     * The name of the "largest conference" statistic.
     */
    public static final String LARGEST_CONFERENCE = "largest_conference";

    /**
     * The name of the loss rate statistic.
     */
    public static final String LOSS_RATE_DOWNLOAD = "loss_rate_download";

    /**
     * The name of the loss rate statistic.
     */
    public static final String LOSS_RATE_UPLOAD = "loss_rate_upload";

    /**
     * The name of the stat for the Octo receive bitrate in Kbps.
     */
    public static final String OCTO_RECEIVE_BITRATE = "octo_receive_bitrate";

    /**
     * The Octo receive packet rate in packets per second.
     */
    public static final String OCTO_RECEIVE_PACKET_RATE = "octo_receive_packet_rate";

    /**
     * The name of the stat for the Octo send bitrate in Kbps.
     */
    public static final String OCTO_SEND_BITRATE = "octo_send_bitrate";

    /**
     * The Octo sen packet rate in packets per second.
     */
    public static final String OCTO_SEND_PACKET_RATE = "octo_send_packet_rate";

    /**
     * The name of the packet rate statistic for download.
     */
    public static final String PACKET_RATE_DOWNLOAD = "packet_rate_download";

    /**
     * The name of the packet rate statistic for upload.
     */
    public static final String PACKET_RATE_UPLOAD = "packet_rate_upload";

    /**
     * The name of the stat used to indicate the number of participants.
     */
    public static final String PARTICIPANTS = "participants";

    /**
     * The number of endpoints in inactive conferences.
     */
    public static final String INACTIVE_ENDPOINTS = "inactive_endpoints";

    /**
     * The number of endpoints endpoints that have no audio or video, but
     * are not inactive.
     */
    public static final String RECEIVE_ONLY_ENDPOINTS = "receive_only_endpoints";

    /**
     * The number of endpoints currently sending audio.
     */
    public static final String ENDPOINTS_SENDING_AUDIO = "endpoints_sending_audio";

    /**
     * The number of endpoints currently sending video.
     */
    public static final String ENDPOINTS_SENDING_VIDEO = "endpoints_sending_video";

    /**
     * The number of remote Octo endpoints.
     */
    public static final String OCTO_ENDPOINTS = "octo_endpoints";

    /**
     * The name of the "region" statistic.
     */
    public static final String REGION = "region";

    /**
     * The name of the "relay_id" statistic.
     */
    public static final String RELAY_ID = "relay_id";

    /**
     * The name of the RTP loss statistic.
     * @deprecated
     */
    public static final String RTP_LOSS = "rtp_loss";

    /**
     * The name of the aggregate RTT statistic.
     */
    public static final String RTT_AGGREGATE = "rtt_aggregate";

    /**
     * The name of the stat that indicates entering graceful shutdown mode.
     */
    public static final String SHUTDOWN_IN_PROGRESS = "graceful_shutdown";

    /**
     * The name of the stat that indicates support of sip gateway capabilities.
     */
    public static final String SUPPORTS_SIP = "supports_sip";

    /**
     * The name of the stat that indicates support of transcription.
     */
    public static final String SUPPORTS_TRANSCRIPTION = "supports_transcription";

    /**
     * The name of the number of threads statistic. Its runtime type is
     * {@code Integer}.
     */
    public static final String THREADS = "threads";

    /**
     * The name of the piece of statistic which specifies the date and time at
     * which the associated set of statistics was generated. Its runtime type is
     * {@code String} and the value represents a {@code Date} value.
     */
    public static final String TIMESTAMP = "current_timestamp";

    /**
     * The name of the stat indicating the total number of bytes received in RTP packets.
     */
    public static final String TOTAL_BYTES_RECEIVED = "total_bytes_received";

    /**
     * The name of the stat indicating the total number of bytes received in
     * Octo packets.
     */
    public static final String TOTAL_BYTES_RECEIVED_OCTO = "total_bytes_received_octo";

    /**
     * The name of the stat indicating the total number of bytes sent in RTP packets.
     */
    public static final String TOTAL_BYTES_SENT = "total_bytes_sent";

    /**
     * The name of the stat indicating the total number of bytes sent in octo packets.
     */
    public static final String TOTAL_BYTES_SENT_OCTO = "total_bytes_sent_octo";

    /**
     * The name of the stat indicating the total number of messages received
     * from data channels.
     */
    public static final String TOTAL_COLIBRI_WEB_SOCKET_MESSAGES_RECEIVED
        = "total_colibri_web_socket_messages_received";

    /**
     * The name of the stat indicating the total number of messages sent over data channels.
     */
    public static final String TOTAL_COLIBRI_WEB_SOCKET_MESSAGES_SENT = "total_colibri_web_socket_messages_sent";

    /**
     * The name of the number of conferences which do not receive media from the gateway side.
     * {@code Integer}.
     */
    public static final String TOTAL_CALLS_WITH_DROPPED_MEDIA = "total_calls_with_dropped_media";

    /**
     * The name of the total number of completed/expired conferences
     * (failed + succeeded).
     */
    public static final String TOTAL_CONFERENCES_COMPLETED = "total_conferences_completed";

    /**
     * The name of the stat indicating the total number of conferences created.
     */
    public static final String TOTAL_CONFERENCES_CREATED = "total_conferences_created";

    /**
     * The name of the stat indicating the total number of conference-seconds
     * (i.e. the sum of the lengths is seconds).
     */
    public static final String TOTAL_CONFERENCE_SECONDS = "total_conference_seconds";

    /**
     * The name of the stat indicating the total number of messages received from data channels.
     */
    public static final String TOTAL_DATA_CHANNEL_MESSAGES_RECEIVED = "total_data_channel_messages_received";

    /**
     * The name of the stat indicating the total number of messages sent over data channels.
     */
    public static final String TOTAL_DATA_CHANNEL_MESSAGES_SENT = "total_data_channel_messages_sent";

    /**
     * The name of the total number of conferences where all channels failed due to no payload traffic.
     */
    public static final String TOTAL_FAILED_CONFERENCES = "total_failed_conferences";

    /**
     * The name of the stat indicating the total number of times ICE failed.
     */
    public static final String TOTAL_ICE_FAILED = "total_ice_failed";

    /**
     * The name of the stat indicating the total number of times ICE succeeded.
     */
    public static final String TOTAL_ICE_SUCCEEDED = "total_ice_succeeded";

    /**
     * The name of the stat indicating the total number of times ICE succeeded over TCP.
     */
    public static final String TOTAL_ICE_SUCCEEDED_TCP = "total_ice_succeeded_tcp";

    /**
     * The name of the stat indicating the total number of participant-seconds
     * that are loss-controlled (i.e. the sum of the lengths is seconds).
     */
    public static final String TOTAL_LOSS_CONTROLLED_PARTICIPANT_SECONDS = "total_loss_controlled_participant_seconds";

    /**
     * The name of the stat indicating the total number of participant-seconds that are loss-limited.
     */
    public static final String TOTAL_LOSS_LIMITED_PARTICIPANT_SECONDS = "total_loss_limited_participant_seconds";

    /**
     * The name of the stat indicating the total number of participant-seconds that are loss-degraded.
     */
    public static final String TOTAL_LOSS_DEGRADED_PARTICIPANT_SECONDS = "total_loss_degraded_participant_seconds";

    /**
     * The name of total memory statistic. Its runtime type is {@code Integer}.
     */
    public static final String TOTAL_MEMORY = "total_memory";

    /**
     * The total number of participants/endpoints created on this bridge.
     */
    public static final String TOTAL_PARTICIPANTS = "total_participants";

    /**
     * The name of the total number of conferences with some failed channels.
     */
    public static final String TOTAL_PARTIALLY_FAILED_CONFERENCES = "total_partially_failed_conferences";

    /**
     * The name of the stat indicating the total number of Octo packets which
     * were dropped (due to a failure to parse, or an unknown conference ID).
     */
    public static final String TOTAL_PACKETS_DROPPED_OCTO = "total_packets_dropped_octo";

    /**
     * The name of the stat indicating the total number of RTP packets received.
     */
    public static final String TOTAL_PACKETS_RECEIVED = "total_packets_received";

    /**
     * The name of the stat indicating the total number of Octo packets received.
     */
    public static final String TOTAL_PACKETS_RECEIVED_OCTO = "total_packets_received_octo";

    /**
     * The name of the stat indicating the total number of RTP packets sent.
     */
    public static final String TOTAL_PACKETS_SENT = "total_packets_sent";

    /**
     * The name of the stat indicating the total number of Octo packets sent.
     */
    public static final String TOTAL_PACKETS_SENT_OCTO = "total_packets_sent_octo";

    /**
     * The name of used memory statistic. Its runtime type is {@code Integer}.
     */
    public static final String USED_MEMORY = "used_memory";

    /**
     * The name of the "version" statistic.
     */
    public static final String VERSION = "version";

    /**
     * The name of the number of video channels statistic. Its runtime type is
     * {@code Integer}. We only use this for callstats.
     */
    public static final String VIDEO_CHANNELS = "videochannels";

    /**
     * The name of the number of video streams statistic. Its runtime type is {@code Integer}.
     */
    public static final String VIDEO_STREAMS = "videostreams";

    /**
     * The cumulative number of times the dominant speaker changed.
     */
    public static final String TOTAL_DOMINANT_SPEAKER_CHANGES = "total_dominant_speaker_changes";

    /**
     * Tries to parse an object as an integer, returns null on failure.
     *
     * @param obj the object to parse.
     */
    private static Integer getInt(Object obj)
    {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Integer) {
            return (Integer) obj;
        }

        String str = obj.toString();
        try {
            return Integer.valueOf(str);
        } catch (NumberFormatException e) {
            Timber.e("Error parsing an int: %s", obj);
        }
        return null;
    }

    /**
     * Creates a deep copy of a {@link ColibriStatsExtension}.
     *
     * @param source the {@link ColibriStatsExtension} to copy.
     * @return the copy.
     */
    public static ColibriStatsExtension clone(ColibriStatsExtension source)
    {
        ColibriStatsExtension destination = AbstractExtensionElement.clone(source);
        for (Stat stat : source.getChildExtensionsOfType(Stat.class)) {
            destination.addStat(Stat.clone(stat));
        }
        return destination;
    }

    /**
     * Constructs new <tt>ColibriStatsExtensionElement</tt>
     */
    public ColibriStatsExtension()
    {
        super(ELEMENT, NAMESPACE);
    }

    /**
     * Adds a specific {@link Stat} instance to the list of stats.
     *
     * @param stat the {@link Stat} instance to add.
     */
    public void addStat(Stat stat)
    {
        addChildExtension(stat);
    }

    /**
     * Adds a new {@link Stat} instance with a specific name and a specific value to the list of stats.
     *
     * @param name the name.
     * @param value the value.
     */
    public void addStat(String name, Object value)
    {
        addStat(new Stat(name, value));
    }

    /**
     * @param name the name of the stat to match.
     * @return the first {@link Stat}, if any, with a specific name.
     */
    public Stat getStat(String name)
    {
        for (Stat stat : getChildExtensionsOfType(Stat.class)) {
            if (stat.getName().equals(name)) {
                return stat;
            }
        }
        return null;
    }

    /**
     * @param name the name of the stat to match.
     * @return the value of the first {@link Stat}, if any, with a specific name.
     */
    public Object getValue(String name)
    {
        Stat stat = getStat(name);
        return stat == null ? null : stat.getValue();
    }

    /**
     * Tries to get the value of the stat with the given {@code name} as a {@link String}. If there is no stat
     * with the given name, or it has no value, returns {@code null}. Otherwise, it returns the {@link String}
     * representation of the value.
     *
     * @param name the name of the stat.
     * @return a {@link String} which represents the value of the stat with the given {@code name}, or {@code null}.
     */
    public String getValueAsString(String name)
    {
        Object o = getValue(name);
        if (o != null) {
            return (o instanceof String) ? (String) o : o.toString();
        }
        return null;
    }

    /**
     * Tries to get the value of the stat with the given {@code name} as an
     * {@link Integer}. If there is no stat with the given name, or it has no
     * value, returns {@code null}. Otherwise, it tries to parse the value as
     * an {@link Integer} and returns the result (or {@code null} if parsing fails).
     *
     * @param name the name of the stat.
     * @return an {@link Integer} representation of the value of the stat with the given {@code name}, or {@code null}.
     */
    public Integer getValueAsInt(String name)
    {
        return getInt(getValue(name));
    }

    @Override
    public List<? extends ExtensionElement> getChildExtensions()
    {
        return Collections.unmodifiableList(super.getChildExtensions());
    }

    public static class Stat extends AbstractExtensionElement
    {
        /**
         * The XML element name of a <tt>content</tt> of a Jitsi Videobridge <tt>stats</tt> IQ.
         */
        public static final String ELEMENT = "stat";

        /**
         * The XML name of the <tt>name</tt> attribute of a <tt>stat</tt> of a <tt>stats</tt> IQ
         * which represents the <tt>name</tt> property of the statistic.
         */
        public static final String NAME_ATTR_NAME = "name";

        /**
         * The XML name of the <tt>name</tt> attribute of a <tt>stat</tt> of a <tt>stats</tt> IQ
         * which represents the <tt>value</tt> property of the statistic.
         */
        public static final String VALUE_ATTR_NAME = "value";

        public Stat()
        {
            super(ELEMENT, NAMESPACE);
        }

        /**
         * Constructs new <tt>Stat</tt> by given name and value.
         *
         * @param name the name
         * @param value the value
         */
        public Stat(String name, Object value)
        {
            this();
            this.setName(name);
            this.setValue(value);
        }

        @Override
        public String getElementName()
        {
            return ELEMENT;
        }

        /**
         * @return the name
         */
        public String getName()
        {
            return getAttributeAsString(NAME_ATTR_NAME);
        }

        @Override
        public String getNamespace()
        {
            return NAMESPACE;
        }

        /**
         * @return the value
         */
        public Object getValue()
        {
            return getAttribute(VALUE_ATTR_NAME);
        }

        /**
         * @param name the name to set
         */
        public void setName(String name)
        {
            setAttribute(NAME_ATTR_NAME, name);
        }

        /**
         * @param value the value to set
         */
        public void setValue(Object value)
        {
            setAttribute(VALUE_ATTR_NAME, value);
        }

        @Override
        public XmlStringBuilder toXML(XmlEnvironment xmlEnvironment)
        {
            XmlStringBuilder xml = new XmlStringBuilder();
            String name = getName();
            Object value = getValue();

            if ((name != null) && (value != null)) {
                xml.halfOpenElement(ELEMENT);
                xml.optElement(NAME_ATTR_NAME, name);
                xml.optElement(VALUE_ATTR_NAME, value.toString());
                xml.closeEmptyElement();
            }
            return xml;
        }
    }
}
