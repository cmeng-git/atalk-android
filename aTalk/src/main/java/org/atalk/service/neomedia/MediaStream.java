/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia;

import java.beans.PropertyChangeListener;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

import org.atalk.impl.neomedia.codec.REDBlock;
import org.atalk.impl.neomedia.rtp.MediaStreamTrackReceiver;
import org.atalk.impl.neomedia.rtp.StreamRTPManager;
import org.atalk.impl.neomedia.rtp.TransportCCEngine;
import org.atalk.impl.neomedia.transform.TransformEngine;
import org.atalk.impl.neomedia.transform.TransformEngineChain;
import org.atalk.service.neomedia.device.MediaDevice;
import org.atalk.service.neomedia.format.MediaFormat;
import org.atalk.service.neomedia.stats.MediaStreamStats2;
import org.atalk.util.ByteArrayBuffer;

/**
 * The <code>MediaStream</code> class represents a (generally) bidirectional RTP stream between exactly
 * two parties. The class reflects one of the media stream, in the SDP sense of the word.
 * <code>MediaStream</code> instances are created through the <code>openMediaStream()</code> method of the
 * <code>MediaService</code>.
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 */
public interface MediaStream {
    /**
     * The name of the property which indicates whether the local SSRC is currently available.
     */
    String PNAME_LOCAL_SSRC = "localSSRCAvailable";

    /**
     * The name of the property which indicates whether the remote SSRC is currently available.
     */
    String PNAME_REMOTE_SSRC = "remoteSSRCAvailable";

    /**
     * Adds a new association in this <code>MediaStream</code> of the specified RTP payload type with
     * the specified <code>MediaFormat</code> in order to allow it to report <code>rtpPayloadType</code> in
     * RTP flows sending and receiving media in <code>format</code>. Usually, <code>rtpPayloadType</code>
     * will be in the range of dynamic RTP payload types.
     *
     * @param rtpPayloadType the RTP payload type to be associated in this <code>MediaStream</code>
     * with the specified <code>MediaFormat</code>
     * @param format the <code>MediaFormat</code> to be associated in this <code>MediaStream</code> with
     * <code>rtpPayloadType</code>
     */
    void addDynamicRTPPayloadType(byte rtpPayloadType, MediaFormat format);

    /**
     * Clears the dynamic RTP payload type associations in this <code>MediaStream</code>.
     */
    void clearDynamicRTPPayloadTypes();

    /**
     * Adds an additional RTP payload mapping that will overriding one that we've set with
     * {@link #addDynamicRTPPayloadType(byte, MediaFormat)}. This is necessary so that we can
     * support the RFC3264 case where the answerer has the right to declare what payload type
     * mappings it wants to receive RTP packets with even if they are different from those in the offer.
     * RFC3264 claims this is for support of legacy protocols such as H.323 but we've been bumping with
     * a number of cases where multi-component pure SIP systems also need to behave this way.
     * <p>
     *
     * @param originalPt the payload type that we are overriding
     * @param overloadPt the payload type that we are overriging it with
     */
    void addDynamicRTPPayloadTypeOverride(byte originalPt, byte overloadPt);

    /**
     * Adds a property change listener to this stream so that it would be notified upon property
     * change events like for example an SSRC ID which becomes known.
     *
     * @param listener the listener that we'd like to register for <code>PropertyChangeEvent</code>s
     */
    void addPropertyChangeListener(PropertyChangeListener listener);

    /**
     * Adds or updates an association in this <code>MediaStream</code> mapping the specified
     * <code>extensionID</code> to <code>rtpExtension</code> and enabling or disabling its use according to
     * the direction attribute of <code>rtpExtension</code>.
     *
     * @param extensionID the ID that is mapped to <code>rtpExtension</code> for the lifetime of this <code>MediaStream</code>.
     * @param rtpExtension the <code>RTPExtension</code> that we are mapping to <code>extensionID</code>.
     */
    void addRTPExtension(byte extensionID, RTPExtension rtpExtension);

    /**
     * Clears the RTP header extension associations stored in this instance.
     */
    void clearRTPExtensions();

    /**
     * Releases the resources allocated by this instance in the course of its execution and
     * prepares it to be garbage collected.
     */
    void close();

    /**
     * Returns a map containing all currently active <code>RTPExtension</code>s in use by this stream.
     *
     * @return a map containing all currently active <code>RTPExtension</code>s in use by this stream.
     */
    Map<Byte, RTPExtension> getActiveRTPExtensions();

    /**
     * Gets the device that this stream uses to play back and capture media.
     *
     * @return the <code>MediaDevice</code> that this stream uses to play back and capture media.
     */
    MediaDevice getDevice();

    /**
     * Gets the direction in which this <code>MediaStream</code> is allowed to stream media.
     *
     * @return the <code>MediaDirection</code> in which this <code>MediaStream</code> is allowed to stream media
     */
    MediaDirection getDirection();

    /**
     * Gets the existing associations in this <code>MediaStream</code> of RTP payload types to
     * <code>MediaFormat</code>s. The returned <code>Map</code> only contains associations previously added
     * in this instance with {@link #addDynamicRTPPayloadType(byte, MediaFormat)} and not globally
     * or well-known associations reported by {@link MediaFormat#getRTPPayloadType()}.
     *
     * @return a <code>Map</code> of RTP payload type expressed as <code>Byte</code> to
     * <code>MediaFormat</code> describing the existing (dynamic) associations in this instance of
     * RTP payload types to <code>MediaFormat</code>s. The <code>Map</code> represents a snapshot of the
     * existing associations at the time of the <code>getDynamicRTPPayloadTypes()</code> method call
     * and modifications to it are not reflected on the internal storage
     */
    Map<Byte, MediaFormat> getDynamicRTPPayloadTypes();

    /**
     * Returns the payload type number that has been negotiated for the specified
     * <code>encoding</code> or <code>-1</code> if no payload type has been negotiated for it. If
     * multiple formats match the specified <code>encoding</code>, then this method would return the
     * first one it encounters while iterating through the map.
     *
     * @param codec the encoding whose payload type we are trying to obtain.
     *
     * @return the payload type number that has been negotiated for the specified <code>codec</code>
     * or <code>-1</code> if no payload type has been negotiated for it.
     */
    byte getDynamicRTPPayloadType(String codec);

    /**
     * Returns the <code>MediaFormat</code> that this stream is currently transmitting in.
     *
     * @return the <code>MediaFormat</code> that this stream is currently transmitting in.
     */
    MediaFormat getFormat();

    /**
     * Returns the <code>MediaFormat</code> that is associated to the payload type passed in as a parameter.
     *
     * @param payloadType the payload type of the <code>MediaFormat</code> to get.
     *
     * @return the <code>MediaFormat</code> that is associated to the payload type passed in as a parameter.
     */
    MediaFormat getFormat(byte payloadType);

    /**
     * Returns the synchronization source (SSRC) identifier of the local participant or <code>-1</code>
     * if that identifier is not known at this point.
     *
     * @return the synchronization source (SSRC) identifier of the local participant or <code>-1</code>
     * if that identifier is not known at this point.
     */
    long getLocalSourceID();

    /**
     * Returns a <code>MediaStreamStats</code> object used to get statistics about this <code>MediaStream</code>.
     *
     * @return the <code>MediaStreamStats</code> object used to get statistics about this <code>MediaStream</code>.
     */
    MediaStreamStats2 getMediaStreamStats();

    /**
     * Returns the name of this stream or <code>null</code> if no name has been set. A stream name is
     * used by some protocols, for diagnostic purposes mostly. In XMPP for example this is the name
     * of the content element that describes a stream.
     *
     * @return the name of this stream or <code>null</code> if no name has been set.
     */
    String getName();

    /**
     * Gets the value of a specific opaque property of this <code>MediaStream</code>.
     *
     * @param propertyName the name of the opaque property of this <code>MediaStream</code>
     * the value of which is to be returned
     *
     * @return the value of the opaque property of this <code>MediaStream</code> specified by <code>propertyName</code>
     */
    Object getProperty(String propertyName);

    /**
     * Returns the address that this stream is sending RTCP traffic to.
     *
     * @return an <code>InetSocketAddress</code> instance indicating the address that we are sending RTCP packets to.
     */
    InetSocketAddress getRemoteControlAddress();

    /**
     * Returns the address that this stream is sending RTP traffic to.
     *
     * @return an <code>InetSocketAddress</code> instance indicating the address that we are sending RTP packets to.
     */
    InetSocketAddress getRemoteDataAddress();

    /**
     * Gets the synchronization source (SSRC) identifier of the remote peer or <code>-1</code> if that
     * identifier is not yet known at this point in the execution.
     * <p>
     * <b>Warning</b>: A <code>MediaStream</code> may receive multiple RTP streams and may thus have
     * multiple remote SSRCs. Since it is not clear how this <code>MediaStream</code> instance chooses
     * which of the multiple remote SSRCs to be returned by the method, it is advisable to always
     * consider {@link #getRemoteSourceIDs()} first.
     * </p>
     *
     * @return the synchronization source (SSRC) identifier of the remote peer or <code>-1</code> if
     * that identifier is not yet known at this point in the execution
     */
    long getRemoteSourceID();

    /**
     * Gets the synchronization source (SSRC) identifiers of the remote peer.
     *
     * @return the synchronization source (SSRC) identifiers of the remote peer
     */
    List<Long> getRemoteSourceIDs();

    /**
     * Gets the {@code StreamRTPManager} which is to forward RTP and RTCP traffic between this
     * and other {@code MediaStream}s.
     *
     * @return the {@code StreamRTPManager} which is to forward RTP and RTCP traffic between this
     * and other {@code MediaStream}s
     */
    StreamRTPManager getStreamRTPManager();

    /**
     * The <code>ZrtpControl</code> which controls the ZRTP for this stream.
     *
     * @return the <code>ZrtpControl</code> which controls the ZRTP for this stream
     */
    SrtpControl getSrtpControl();

    /**
     * Returns the target of this <code>MediaStream</code> to which it is to send and from which it is
     * to receive data (e.g. RTP) and control data (e.g. RTCP).
     *
     * @return the <code>MediaStreamTarget</code> describing the data (e.g. RTP) and the control data
     * (e.g. RTCP) locations to which this <code>MediaStream</code> is to send and from which it is to receive
     *
     * @see MediaStream#setTarget(MediaStreamTarget)
     */
    MediaStreamTarget getTarget();

    /**
     * Returns the transport protocol used by the streams.
     *
     * @return the transport protocol (UDP or TCP) used by the streams. null if the stream connector is not instantiated.
     */
    StreamConnector.Protocol getTransportProtocol();

    /**
     * Determines whether this <code>MediaStream</code> is set to transmit "silence" instead of the
     * media being fed from its <code>MediaDevice</code>. "Silence" for video is understood as video
     * data which is not the captured video data and may represent, for example, a black image.
     *
     * @return <code>true</code> if this <code>MediaStream</code> is set to transmit "silence" instead of
     * the media fed from its <code>MediaDevice</code>; <code>false</code>, otherwise
     */
    boolean isMute();

    /**
     * Determines whether {@link #start()} has been called on this <code>MediaStream</code> without
     * {@link #stop()} or {@link #close()} afterwards.
     *
     * @return <code>true</code> if {@link #start()} has been called on this <code>MediaStream</code>
     * without {@link #stop()} or {@link #close()} afterwards
     */
    boolean isStarted();

    /**
     * Removes the specified property change <code>listener</code> from this stream so that it won't
     * receive further property change events.
     *
     * @param listener the listener that we'd like to remove.
     */
    void removePropertyChangeListener(PropertyChangeListener listener);

    /**
     * Removes the <code>ReceiveStream</code> with SSRC <code>ssrc</code>, if there is such a
     * <code>ReceiveStream</code>, from the receive streams of this <code>MediaStream</code>
     *
     * @param ssrc the SSRC for which to remove a <code>ReceiveStream</code>
     */
    void removeReceiveStreamForSsrc(long ssrc);

    /**
     * Sets the <code>StreamConnector</code> to be used by this <code>MediaStream</code> for sending and receiving media.
     *
     * @param connector the <code>StreamConnector</code> to be used by this <code>MediaStream</code> for sending and
     * receiving media
     */
    void setConnector(StreamConnector connector);

    /**
     * Sets the device that this stream should use to play back and capture media.
     *
     * @param device the <code>MediaDevice</code> that this stream should use to play back and capture media.
     */
    void setDevice(MediaDevice device);

    /**
     * Sets the direction in which media in this <code>MediaStream</code> is to be streamed. If this
     * <code>MediaStream</code> is not currently started, calls to {@link #start()} later on will start
     * it only in the specified <code>direction</code>. If it is currently started in a direction
     * different than the specified, directions other than the specified will be stopped.
     *
     * @param direction the <code>MediaDirection</code> in which this <code>MediaStream</code>
     * is to stream media when it is started
     */
    void setDirection(MediaDirection direction);

    /**
     * Sets the <code>MediaFormat</code> that this <code>MediaStream</code> should transmit in.
     *
     * @param format the <code>MediaFormat</code> that this <code>MediaStream</code> should transmit in.
     */
    void setFormat(MediaFormat format);

    /**
     * Causes this <code>MediaStream</code> to stop transmitting the media being fed from this stream's
     * <code>MediaDevice</code> and transmit "silence" instead. "Silence" for video is understood as
     * video data which is not the captured video data and may represent, for example, a black image.
     *
     * @param mute <code>true</code> if we are to start transmitting "silence" and <code>false</code> if we are
     * to use media from this stream's <code>MediaDevice</code> again.
     */
    void setMute(boolean mute);

    /**
     * Sets the name of this stream. Stream names are used by some protocols, for diagnostic
     * purposes mostly. In XMPP for example this is the name of the content element that describes a stream.
     *
     * @param name the name of this stream or <code>null</code> if no name has been set.
     */
    void setName(String name);

    /**
     * Sets the value of a specific opaque property of this <code>MediaStream</code>.
     *
     * @param propertyName the name of the opaque property of this <code>MediaStream</code> the value of which is to
     * be set to the specified <code>value</code>
     * @param value the value of the opaque property of this <code>MediaStream</code> specified by
     * <code>propertyName</code> to be set
     */
    void setProperty(String propertyName, Object value);

    /**
     * Sets the <code>RTPTranslator</code> which is to forward RTP and RTCP traffic between this and
     * other <code>MediaStream</code>s.
     *
     * @param rtpTranslator the <code>RTPTranslator</code> which is to forward RTP and RTCP traffic between this and
     * other <code>MediaStream</code>s
     */
    void setRTPTranslator(RTPTranslator rtpTranslator);

    /**
     * Gets the {@link RTPTranslator} which forwards RTP and RTCP traffic between this and other
     * {@code MediaStream}s.
     *
     * @return the {@link RTPTranslator} which forwards RTP and RTCP traffic between this and other
     * {@code MediaStream}s or {@code null}
     */
    RTPTranslator getRTPTranslator();

    /**
     * Sets the <code>SSRCFactory</code> which is to generate new synchronization source (SSRC) identifiers.
     *
     * @param ssrcFactory the <code>SSRCFactory</code> which is to generate new synchronization source (SSRC)
     * identifiers or <code>null</code> if this <code>MediaStream</code> is to employ internal logic
     * to generate new synchronization source (SSRC) identifiers
     */
    void setSSRCFactory(SSRCFactory ssrcFactory);

    /**
     * Sets the target of this <code>MediaStream</code> to which it is to send and from which it is to
     * receive data (e.g. RTP) and control data (e.g. RTCP).
     *
     * @param target the <code>MediaStreamTarget</code> describing the data (e.g. RTP) and the control data
     * (e.g. RTCP) locations to which this <code>MediaStream</code> is to send and from which it is to receive
     */
    void setTarget(MediaStreamTarget target);

    /**
     * Starts capturing media from this stream's <code>MediaDevice</code> and then streaming it through
     * the local <code>StreamConnector</code> toward the stream's target address and port. The method
     * also puts the <code>MediaStream</code> in a listening state that would make it play all media
     * received from the <code>StreamConnector</code> on the stream's <code>MediaDevice</code>.
     */
    void start();

    /**
     * Stops all streaming and capturing in this <code>MediaStream</code> and closes and releases all
     * open/allocated devices/resources. This method has no effect on an already closed stream and
     * is simply ignored.
     */
    void stop();

    /**
     * Sets the external (application-provided) <code>TransformEngine</code> of this <code>MediaStream</code>.
     *
     * @param transformEngine the <code>TransformerEngine</code> to use.
     */
    void setExternalTransformer(TransformEngine transformEngine);

    /**
     * Sends a given RTP or RTCP packet to the remote peer/side.
     *
     * @param pkt the packet to send.
     * @param data {@code true} to send an RTP packet or {@code false} to send an RTCP packet.
     * @param after the {@code TransformEngine} in the {@code TransformEngine} chain of this
     * {@code MediaStream} after which the injection is to begin. If the specified
     * {@code after} is not in the {@code TransformEngine} chain of this {@code MediaStream},
     * {@code pkt} will be injected at the beginning of the {@code TransformEngine} chain of
     * this {@code MediaStream}. Generally, the value of {@code after} should be {@code null}
     * unless the injection is being performed by a {@code TransformEngine} itself (while executing
     * {@code transform} or {@code reverseTransform} of a {@code PacketTransformer} of its own even).
     *
     * @throws TransmissionFailedException if the transmission failed.
     */
    void injectPacket(RawPacket pkt, boolean data, TransformEngine after)
            throws TransmissionFailedException;

    /**
     * Utility method that determines whether or not a packet is a key frame.
     *
     * @param buf the buffer that holds the RTP payload.
     * @param off the offset in the buff where the RTP payload is found.
     * @param len then length of the RTP payload in the buffer.
     *
     * @return true if the packet is a key frame, false otherwise.
     */
    boolean isKeyFrame(byte[] buf, int off, int len);

    /**
     * Utility method that determines whether or not a packet is a key frame.
     *
     * @param pkt the packet.
     */
    boolean isKeyFrame(RawPacket pkt);

    /**
     * Gets the primary {@link REDBlock} that contains the payload of the RTP
     * packet passed in as a parameter.
     *
     * @param pkt the {@link RawPacket} that holds the RTP payload.
     *
     * @return the primary {@link REDBlock} that contains the payload of the RTP
     * packet passed in as a parameter, or null if the buffer is invalid.
     */
    REDBlock getPrimaryREDBlock(RawPacket pkt);

    /**
     * @return the {@link RetransmissionRequester} for this media stream.
     */
    RetransmissionRequester getRetransmissionRequester();

    /**
     * Gets the {@link TransformEngineChain} of this {@link MediaStream}.
     */
    TransformEngineChain getTransformEngineChain();

    /**
     * Gets the {@link MediaStreamTrackReceiver} of this {@link MediaStream}.
     *
     * @return the {@link MediaStreamTrackReceiver} of this {@link MediaStream}, or null.
     */
    MediaStreamTrackReceiver getMediaStreamTrackReceiver();

    /**
     * Sets the {@link TransportCCEngine} of this media stream. Note that for
     * this to take effect it needs to be called early, before the transform
     * chain is initialized (i.e. before a connector is set).
     *
     * @param engine the engine to set.
     */
    void setTransportCCEngine(TransportCCEngine engine);
}
