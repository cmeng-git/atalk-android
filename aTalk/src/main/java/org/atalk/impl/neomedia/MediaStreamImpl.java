

/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia;

import org.atalk.impl.timberlog.TimberLog;
import org.atalk.impl.neomedia.codec.REDBlock;
import org.atalk.impl.neomedia.codec.REDBlockIterator;
import org.atalk.impl.neomedia.device.AbstractMediaDevice;
import org.atalk.impl.neomedia.device.MediaDeviceSession;
import org.atalk.impl.neomedia.device.ReceiveStreamPushBufferDataSource;
import org.atalk.impl.neomedia.device.VideoMediaDeviceSession;
import org.atalk.impl.neomedia.format.MediaFormatImpl;
import org.atalk.impl.neomedia.format.ParameterizedVideoFormat;
import org.atalk.impl.neomedia.protocol.TranscodingDataSource;
import org.atalk.impl.neomedia.rtp.FrameMarkingHeaderExtension;
import org.atalk.impl.neomedia.rtp.MediaStreamTrackReceiver;
import org.atalk.impl.neomedia.rtp.StreamRTPManager;
import org.atalk.impl.neomedia.rtp.TransportCCEngine;
import org.atalk.impl.neomedia.rtp.remotebitrateestimator.RemoteBitrateEstimatorWrapper;
import org.atalk.impl.neomedia.rtp.translator.RTPTranslatorImpl;
import org.atalk.impl.neomedia.stats.MediaStreamStats2Impl;
import org.atalk.impl.neomedia.transform.AbsSendTimeEngine;
import org.atalk.impl.neomedia.transform.CachingTransformer;
import org.atalk.impl.neomedia.transform.DiscardTransformEngine;
import org.atalk.impl.neomedia.transform.OriginalHeaderBlockTransformEngine;
import org.atalk.impl.neomedia.transform.PacketTransformer;
import org.atalk.impl.neomedia.transform.PaddingTermination;
import org.atalk.impl.neomedia.transform.REDTransformEngine;
import org.atalk.impl.neomedia.transform.RTPTransformTCPConnector;
import org.atalk.impl.neomedia.transform.RTPTransformUDPConnector;
import org.atalk.impl.neomedia.transform.RetransmissionRequesterImpl;
import org.atalk.impl.neomedia.transform.RtxTransformer;
import org.atalk.impl.neomedia.transform.TransformEngine;
import org.atalk.impl.neomedia.transform.TransformEngineChain;
import org.atalk.impl.neomedia.transform.TransformEngineWrapper;
import org.atalk.impl.neomedia.transform.TransformTCPOutputStream;
import org.atalk.impl.neomedia.transform.TransformUDPOutputStream;
import org.atalk.impl.neomedia.transform.csrc.CsrcTransformEngine;
import org.atalk.impl.neomedia.transform.csrc.SsrcTransformEngine;
import org.atalk.impl.neomedia.transform.dtmf.DtmfTransformEngine;
import org.atalk.impl.neomedia.transform.fec.FECTransformEngine;
import org.atalk.impl.neomedia.transform.pt.PayloadTypeTransformEngine;
import org.atalk.impl.neomedia.transform.rtcp.StatisticsEngine;
import org.atalk.impl.neomedia.transform.zrtp.ZRTPTransformEngine;
import org.atalk.service.neomedia.AbstractMediaStream;
import org.atalk.service.neomedia.AudioMediaStream;
import org.atalk.service.neomedia.MediaDirection;
import org.atalk.service.neomedia.MediaStream;
import org.atalk.service.neomedia.MediaStreamStats;
import org.atalk.service.neomedia.MediaStreamTarget;
import org.atalk.service.neomedia.RTPExtension;
import org.atalk.service.neomedia.RTPTranslator;
import org.atalk.service.neomedia.RawPacket;
import org.atalk.service.neomedia.RetransmissionRequester;
import org.atalk.service.neomedia.SSRCFactory;
import org.atalk.service.neomedia.SrtpControl;
import org.atalk.service.neomedia.SrtpControlType;
import org.atalk.service.neomedia.StreamConnector;
import org.atalk.service.neomedia.TransmissionFailedException;
import org.atalk.service.neomedia.VideoMediaStream;
import org.atalk.service.neomedia.codec.Constants;
import org.atalk.service.neomedia.control.PacketLossAwareEncoder;
import org.atalk.service.neomedia.device.MediaDevice;
import org.atalk.service.neomedia.format.MediaFormat;
import org.atalk.util.ByteArrayBuffer;
import org.atalk.util.MediaType;
import org.atalk.util.RTPUtils;
import org.atalk.util.logging.DiagnosticContext;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.media.Format;
import javax.media.MediaLocator;
import javax.media.control.BufferControl;
import javax.media.format.UnsupportedFormatException;
import javax.media.protocol.DataSource;
import javax.media.protocol.PullBufferDataSource;
import javax.media.protocol.PullBufferStream;
import javax.media.protocol.PullDataSource;
import javax.media.protocol.PullSourceStream;
import javax.media.protocol.PushBufferDataSource;
import javax.media.protocol.PushBufferStream;
import javax.media.protocol.PushDataSource;
import javax.media.protocol.PushSourceStream;
import javax.media.rtp.GlobalReceptionStats;
import javax.media.rtp.GlobalTransmissionStats;
import javax.media.rtp.Participant;
import javax.media.rtp.RTPConnector;
import javax.media.rtp.ReceiveStream;
import javax.media.rtp.ReceiveStreamListener;
import javax.media.rtp.RemoteListener;
import javax.media.rtp.SendStream;
import javax.media.rtp.SendStreamListener;
import javax.media.rtp.SessionAddress;
import javax.media.rtp.SessionListener;
import javax.media.rtp.event.NewReceiveStreamEvent;
import javax.media.rtp.event.NewSendStreamEvent;
import javax.media.rtp.event.ReceiveStreamEvent;
import javax.media.rtp.event.ReceiverReportEvent;
import javax.media.rtp.event.RemoteEvent;
import javax.media.rtp.event.RemotePayloadChangeEvent;
import javax.media.rtp.event.SendStreamEvent;
import javax.media.rtp.event.SenderReportEvent;
import javax.media.rtp.event.SessionEvent;
import javax.media.rtp.event.TimeoutEvent;
import javax.media.rtp.rtcp.Feedback;
import javax.media.rtp.rtcp.Report;
import javax.media.rtp.rtcp.SenderReport;

import timber.log.Timber;

/**
 * Implements <code>MediaStream</code> using JMF.
 *
 * @author Lyubomir Marinov
 * @author Emil Ivov
 * @author Sebastien Vincent
 * @author Boris Grozev
 * @author George Politis
 * @author Eng Chong Meng
 * @author MilanKral
 */
public class MediaStreamImpl extends AbstractMediaStream
        implements ReceiveStreamListener, SendStreamListener, SessionListener, RemoteListener
{
    /**
     * The name of the property indicating the length of our receive buffer.
     */
    protected static final String PROPERTY_NAME_RECEIVE_BUFFER_LENGTH = "neomedia.RECEIVE_BUFFER_LENGTH";

    /**
     * Returns a human-readable representation of a specific <code>DataSource</code> instance in the
     * form of a <code>String</code> value.
     *
     * @param dataSource the <code>DataSource</code> to return a human-readable representation of
     * @return a <code>String</code> value which gives a human-readable representation of the specified <code>dataSource</code>
     */
    public static String toString(DataSource dataSource)
    {
        StringBuilder str = new StringBuilder();
        str.append(dataSource.getClass().getSimpleName());
        str.append(" with hashCode ");
        str.append(dataSource.hashCode());

        MediaLocator locator = dataSource.getLocator();
        if (locator != null) {
            str.append(" and locator ");
            str.append(locator);
        }
        return str.toString();
    }

    /**
     * The map of currently active <code>RTPExtension</code>s and the IDs that they have been assigned
     * for the lifetime of this <code>MediaStream</code>.
     */
    private final Map<Byte, RTPExtension> activeRTPExtensions = new Hashtable<>();

    /**
     * The engine that we are using in order to add CSRC lists in conference calls, send CSRC sound
     * levels, and handle incoming levels and CSRC lists.
     */
    private CsrcTransformEngine csrcEngine;

    /**
     * The session with the <code>MediaDevice</code> this instance uses for both capture and playback of media.
     */
    private MediaDeviceSession deviceSession;

    /**
     * The <code>PropertyChangeListener</code> which listens to {@link #deviceSession} and changes in
     * the values of its {@link MediaDeviceSession#OUTPUT_DATA_SOURCE} property.
     */
    private final PropertyChangeListener deviceSessionPropertyChangeListener = ev -> {
        String propertyName = ev.getPropertyName();

        if (MediaDeviceSession.OUTPUT_DATA_SOURCE.equals(propertyName))
            deviceSessionOutputDataSourceChanged();
        else if (MediaDeviceSession.SSRC_LIST.equals(propertyName))
            deviceSessionSsrcListChanged(ev);
    };

    /**
     * The <code>MediaDirection</code> in which this <code>MediaStream</code> is allowed to stream media.
     */
    private MediaDirection mDirection;

    /**
     * The <code>Map</code> of associations in this <code>MediaStream</code> and the <code>RTPManager</code> it
     * utilizes of (dynamic) RTP payload types to <code>MediaFormat</code>s.
     */
    private final Map<Byte, MediaFormat> dynamicRTPPayloadTypes = new HashMap<>();

    /**
     * The list of CSRC IDs contributing to the media that this <code>MediaStream</code> is sending to its remote party.
     */
    private long[] localContributingSourceIDs;

    /**
     * Our own SSRC identifier.
     *
     * XXX(gp) how about taking the local source ID directly from {@link this.rtpManager}, given
     * that it offers this information with its getLocalSSRC() method? TAG(cat4-local-ssrc-hurricane)
     */
    private long localSourceID = (new Random().nextInt()) & 0x00000000FFFFFFFFL;

    /**
     * The MediaStreamStatsImpl object used to compute the statistics about this MediaStreamImpl.
     */
    private final MediaStreamStats2Impl mediaStreamStatsImpl;

    /**
     * The indicator which determines whether this <code>MediaStream</code> is set to transmit
     * "silence" instead of the actual media fed from its <code>MediaDevice</code>.
     */
    private boolean mute = false;

    /**
     * Number of received receiver reports. Used for logging and debugging only.
     */
    private long numberOfReceivedReceiverReports = 0;

    /**
     * Number of received sender reports. Used for logging and debugging only.
     */
    private long numberOfReceivedSenderReports = 0;

    /**
     * Engine chain overriding payload type if needed.
     */
    private PayloadTypeTransformEngine ptTransformEngine;

    /**
     * The <code>ReceiveStream</code>s this instance plays back on its associated <code>MediaDevice</code>.
     * The (read and write) accesses to the field are to be synchronized using {@link #receiveStreamsLock}.
     */
    private final List<ReceiveStream> receiveStreams = new LinkedList<>();

    /**
     * The <code>ReadWriteLock</code> which synchronizes the (read and write) accesses to {@link #receiveStreams}.
     */
    private final ReadWriteLock receiveStreamsLock = new ReentrantReadWriteLock();

    /**
     * The SSRC identifiers of the party that we are exchanging media with.
     *
     * XXX(gp) I'm sure there's a reason why we do it the way we do it, but we might want to
     * re-think about how we manage receive SSRCs. We keep track of the receive SSRC in at least 3
     * places, in the MediaStreamImpl (we have a remoteSourceIDs vector), in
     * StreamRTPManager.receiveSSRCs and in RtpChannel.receiveSSRCs. TAG (cat4-remote-ssrc-hurricane)
     */
    private final Vector<Long> remoteSourceIDs = new Vector<>(1, 1);

    /**
     * The <code>RTPConnector</code> through which this instance sends and receives
     * RTP and RTCP traffic. The instance is a <code>TransformConnector</code> in
     * order to also enable packet transformations.
     */
    private AbstractRTPConnector rtpConnector;

    /**
     * The one and only <code>MediaStreamTarget</code> this instance has added as a target in {@link #rtpConnector}.
     */
    private MediaStreamTarget rtpConnectorTarget;

    /**
     * The <code>RTPManager</code> which utilizes {@link #rtpConnector} and sends and receives RTP and
     * RTCP traffic on behalf of this <code>MediaStream</code>.
     */
    private StreamRTPManager rtpManager;

    /**
     * The indicator which determines whether {@link #createSendStreams()} has been executed for
     * {@link #rtpManager}. If <code>true</code>, the <code>SendStream</code>s have to be recreated when
     * the <code>MediaDevice</code>, respectively the <code>MediaDeviceSession</code>, of this instance is changed.
     */
    protected boolean sendStreamsAreCreated = false;

    /**
     * The <code>SrtpControl</code> which controls the SRTP functionality of this <code>MediaStream</code>.
     */
    private final SrtpControl srtpControl;

    /**
     * The <code>SSRCFactory</code> to be utilized by this instance to generate new synchronization
     * source (SSRC) identifiers. If <code>null</code>, this instance will employ internal logic to
     * generate new synchronization source (SSRC) identifiers.
     */
    private SSRCFactory ssrcFactory = new SSRCFactoryImpl(localSourceID);

    /**
     * The indicator which determines whether {@link #start()} has been called on this
     * <code>MediaStream</code> without {@link #stop()} or {@link #close()}.
     */
    private boolean started = false;

    /**
     * The <code>MediaDirection</code> in which this instance is started. For example,
     * {@link MediaDirection#SENDRECV} if this instances is both sending and receiving data (e.g.
     * RTP and RTCP) or {@link MediaDirection#SENDONLY} if this instance is only sending data.
     */
    private MediaDirection startedDirection;

    /**
     * Engine chain reading sent RTCP sender reports and stores/prints statistics.
     */
    private StatisticsEngine statisticsEngine = null;

    /**
     * The <code>TransformEngine</code> instance registered in the
     * <code>RTPConnector</code>'s transformer chain, which allows the "external" transformer to be swapped.
     */
    private final TransformEngineWrapper<TransformEngine> externalTransformerWrapper = new TransformEngineWrapper<>();

    /**
     * The transformer which replaces the timestamp in an abs-send-time RTP header extension.
     */
    private AbsSendTimeEngine absSendTimeEngine;

    /**
     * The transformer which caches outgoing RTP packets for this {@link MediaStream}.
     */
    private CachingTransformer cachingTransformer = createCachingTransformer();

    /**
     * The chain used to by the RTPConnector to transform packets.
     */
    private TransformEngineChain transformEngineChain;

    /**
     * The {@code RetransmissionRequester} instance for this {@code MediaStream} which will request
     * missing packets by sending RTCP NACKs.
     */
    private final RetransmissionRequesterImpl retransmissionRequester = createRetransmissionRequester();

    /**
     * The engine which adds an Original Header Block header extension to incoming packets.
     */
    private final OriginalHeaderBlockTransformEngine ohbEngine = new OriginalHeaderBlockTransformEngine();

    /**
     * The {@link DiagnosticContext} that this instance provides.
     */
    private final DiagnosticContext diagnosticContext = new DiagnosticContext();

    /**
     * The ID of the frame markings RTP header extension. We use this field as
     * a cache, in order to not access {@link #activeRTPExtensions} every time.
     */
    private int frameMarkingsExtensionId = -1;

    /**
     * The {@link TransportCCEngine} instance, if any, for this
     * {@link MediaStream}. The instance could be shared between more than one
     * {@link MediaStream}, if they all use the same transport.
     */
    private TransportCCEngine transportCCEngine;

    /**
     * Initializes a new <code>MediaStreamImpl</code> instance which will use the specified
     * <code>MediaDevice</code> for both capture and playback of media. The new instance will not have
     * an associated <code>StreamConnector</code> and it must be set later for the new instance to be
     * able to exchange media with a remote peer.
     *
     * @param device the <code>MediaDevice</code> the new instance is to use for both capture and playback of media
     * @param srtpControl an existing control instance to control the SRTP operations
     */
    public MediaStreamImpl(MediaDevice device, SrtpControl srtpControl)
    {
        this(null, device, srtpControl);
    }

    /**
     * Initializes a new <code>MediaStreamImpl</code> instance which will use the specified <code>MediaDevice</code> for
     * both capture and playback of media exchanged via the specified <code>StreamConnector</code>.
     *
     * @param connector the <code>StreamConnector</code> the new instance is to use for sending and receiving
     * media or <code>null</code> if the <code>StreamConnector</code> of the new instance is to not
     * be set at initialization time but specified later on
     * @param device the <code>MediaDevice</code> the new instance is to use for both capture and playback of
     * media exchanged via the specified <code>StreamConnector</code>
     * @param srtpControl an existing control instance to control the ZRTP operations or <code>null</code> if a new
     * control instance is to be created by the new <code>MediaStreamImpl</code>
     */
    public MediaStreamImpl(StreamConnector connector, MediaDevice device, SrtpControl srtpControl)
    {
        if (device != null) {
            /*
             * XXX Set the device early in order to make sure that it is of the right type because
             * we do not support just about any MediaDevice yet.
             */
            setDevice(device);
        }

        // TODO Add option to disable ZRTP, e.g. by implementing a NullControl.
        // If you change the default behavior (initiates a ZrtpControlImpl if the srtpControl
        // attribute is null), please accordingly modify the CallPeerMediaHandler.initStream function.
        this.srtpControl = (srtpControl == null)
                ? NeomediaServiceUtils.getMediaServiceImpl().createSrtpControl(SrtpControlType.ZRTP, null) : srtpControl;

        this.srtpControl.registerUser(this);

        // cmeng: 2016/11/02 MediaStreamStats2Impl(this) must be executed before
        // setConnector(connector). This is to ensure mediaStreamStats is initialized before
        // StaticEngine makes reference to it.
        this.mediaStreamStatsImpl = new MediaStreamStats2Impl(this);

        if (connector != null)
            setConnector(connector);

        Timber.log(TimberLog.FINER, "Created %S with hashCode %S", getClass().getSimpleName(), hashCode());
        diagnosticContext.put("stream", hashCode());
    }

    /**
     * Gets the {@link DiagnosticContext} of this instance.
     */
    public DiagnosticContext getDiagnosticContext()
    {
        return diagnosticContext;
    }

    /**
     * Adds a new association in this <code>MediaStream</code> of the specified RTP payload type with
     * the specified <code>MediaFormat</code> in order to allow it to report <code>rtpPayloadType</code> in
     * RTP flows sending and receiving media in <code>format</code>. Usually, <code>rtpPayloadType</code>
     * will be in the range of dynamic RTP payload types.
     *
     * @param rtpPayloadType the RTP payload type to be associated in this
     * <code>MediaStream</code> with the specified <code>MediaFormat</code>
     * @param format the <code>MediaFormat</code> to be associated in this <code>MediaStream</code> with <code>rtpPayloadType</code>
     * @see MediaStream#addDynamicRTPPayloadType(byte, MediaFormat)
     */
    @Override
    public void addDynamicRTPPayloadType(byte rtpPayloadType, MediaFormat format)
    {
        MediaFormatImpl<? extends Format> mediaFormatImpl = (MediaFormatImpl<? extends Format>) format;

        synchronized (dynamicRTPPayloadTypes) {
            dynamicRTPPayloadTypes.put(rtpPayloadType, format);
        }

        String encoding = format.getEncoding();
        if (Constants.RED.equals(encoding)) {
            REDTransformEngine redTransformEngine = getRedTransformEngine();
            if (redTransformEngine != null) {
                redTransformEngine.setIncomingPT(rtpPayloadType);
                // setting outgoingPT enables RED encapsulation for outgoing packets.
                redTransformEngine.setOutgoingPT(rtpPayloadType);
            }
        }
        else if (Constants.ULPFEC.equals(encoding)) {
            TransformEngineWrapper<FECTransformEngine> fecTransformEngineWrapper = getFecTransformEngine();
            if ((fecTransformEngineWrapper != null) && (fecTransformEngineWrapper.getWrapped() != null)) {
                FECTransformEngine fecTransformEngine = fecTransformEngineWrapper.getWrapped();
                fecTransformEngine.setIncomingPT(rtpPayloadType);
                // TODO ULPFEC without RED doesn't make sense.
                fecTransformEngine.setOutgoingPT(rtpPayloadType);
            }
        }
        else if (Constants.FLEXFEC_03.equals(encoding)) {
            TransformEngineWrapper<FECTransformEngine> fecTransformEngineWrapper = getFecTransformEngine();
            if (fecTransformEngineWrapper.getWrapped() != null) {
                Timber.i("Updating existing FlexFEC-03 transform engine with payload type %s", rtpPayloadType);
                fecTransformEngineWrapper.getWrapped().setIncomingPT(rtpPayloadType);
                fecTransformEngineWrapper.getWrapped().setOutgoingPT(rtpPayloadType);
            }
            else {
                Timber.i("Creating FlexFEC-03 transform engine with payload type %s", rtpPayloadType);
                FECTransformEngine flexFecTransformEngine
                        = new FECTransformEngine(FECTransformEngine.FecType.FLEXFEC_03,
                        rtpPayloadType, rtpPayloadType, this);
                setFecTransformEngine(flexFecTransformEngine);
            }
        }

        if (rtpManager != null) {
            // We do not add RED and FEC payload types to the RTP Manager because RED and FEC
            // packets will be handled before they get to the RTP Manager.
            rtpManager.addFormat(mediaFormatImpl.getFormat(), rtpPayloadType);
        }
        this.onDynamicPayloadTypesChanged();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clearDynamicRTPPayloadTypes()
    {
        synchronized (dynamicRTPPayloadTypes) {
            dynamicRTPPayloadTypes.clear();
        }

        REDTransformEngine redTransformEngine = getRedTransformEngine();
        if (redTransformEngine != null) {
            redTransformEngine.setIncomingPT((byte) -1);
            redTransformEngine.setOutgoingPT((byte) -1);
        }

        TransformEngineWrapper<FECTransformEngine> fecTransformEngineWrapper = getFecTransformEngine();
        if (fecTransformEngineWrapper != null && fecTransformEngineWrapper.getWrapped() != null) {
            FECTransformEngine fecTransformEngine = fecTransformEngineWrapper.getWrapped();
            fecTransformEngine.setIncomingPT((byte) -1);
            fecTransformEngine.setOutgoingPT((byte) -1);
        }
        this.onDynamicPayloadTypesChanged();
    }

    /**
     * Add an RTP payload mapping that will overriding one we've set with
     * {@link #addDynamicRTPPayloadType(byte, MediaFormat)}. This is necessary so that we can
     * support the RFC3264 case where the answerer has the right to declare what payload type
     * mappings it wants to receive RTP packets with even if they are different from those in the
     * offer. RFC3264 claims this is for support of legacy protocols such as H.323 but we've been
     * bumping with a number of cases where multi-component pure SIP systems also need to behave this way.
     *
     * @param originalPt the payload type that we are overriding
     * @param overloadPt the payload type that we are overriding it with
     */
    @Override
    public void addDynamicRTPPayloadTypeOverride(byte originalPt, byte overloadPt)
    {
        if (ptTransformEngine != null)
            ptTransformEngine.addPTMappingOverride(originalPt, overloadPt);
    }

    /**
     * Adds a specific <code>ReceiveStream</code> to {@link #receiveStreams}.
     *
     * @param receiveStream the <code>ReceiveStream</code> to add
     * @return <code>true</code> if <code>receiveStreams</code> changed as a result of the method call; otherwise, <code>false</code>
     */
    private boolean addReceiveStream(ReceiveStream receiveStream)
    {
        Lock writeLock = receiveStreamsLock.writeLock();
        Lock readLock = receiveStreamsLock.readLock();
        boolean added = false;

        writeLock.lock();
        try {
            // Downgrade the writeLock to a read lock in order to allow readers during the invocation of
            // MediaDeviceSession.addReceiveStream(ReceiveStream) (and disallow writers, of course).
            if (!receiveStreams.contains(receiveStream) && receiveStreams.add(receiveStream)) {
                readLock.lock();
                added = true;
            }
        } finally {
            writeLock.unlock();
        }
        if (added) {
            try {
                MediaDeviceSession deviceSession = getDeviceSession();
                if (deviceSession == null || deviceSession.useTranslator) {
                    // Since there is no output MediaDevice to render the receiveStream on, the
                    // JitterBuffer of the receiveStream will needlessly buffer and, possibly,
                    // eventually try to adapt to the lack of free buffer space.
                    ReceiveStreamPushBufferDataSource.setNullTransferHandler(receiveStream);
                }
                else {
                    deviceSession.addReceiveStream(receiveStream);
                }
            } finally {
                readLock.unlock();
            }
        }
        return added;
    }

    /**
     * Sets the remote SSRC identifier and fires the corresponding <code>PropertyChangeEvent</code>.
     *
     * @param remoteSourceID the SSRC identifier that this stream will be using in outgoing RTP packets from now on.
     */
    protected void addRemoteSourceID(long remoteSourceID)
    {
        Long oldValue = getRemoteSourceID();
        if (!remoteSourceIDs.contains(remoteSourceID))
            remoteSourceIDs.add(remoteSourceID);

        firePropertyChange(PNAME_REMOTE_SSRC, oldValue, remoteSourceID);
    }

    /**
     * Maps or updates the mapping between <code>extensionID</code> and <code>rtpExtension</code>. If
     * <code>rtpExtension</code>'s <code>MediaDirection</code> attribute is set to <code>INACTIVE</code> the
     * mapping is removed from the local extensions table and the extension would not be
     * transmitted or handled by this stream's <code>RTPConnector</code>.
     *
     * @param extensionID the ID that is being mapped to <code>rtpExtension</code>
     * @param rtpExtension the <code>RTPExtension</code> that we are mapping.
     */
    @Override
    public void addRTPExtension(byte extensionID, RTPExtension rtpExtension)
    {
        if (rtpExtension == null)
            return;

        boolean active = !MediaDirection.INACTIVE.equals(rtpExtension.getDirection());
        synchronized (activeRTPExtensions) {
            if (active)
                activeRTPExtensions.put(extensionID, rtpExtension);
            else
                activeRTPExtensions.remove(extensionID);
        }
        enableRTPExtension(extensionID, rtpExtension);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clearRTPExtensions()
    {
        synchronized (activeRTPExtensions) {
            activeRTPExtensions.clear();

            frameMarkingsExtensionId = -1;

            if (transportCCEngine != null) {
                transportCCEngine.setExtensionID(-1);
            }
            if (ohbEngine != null) {
                ohbEngine.setExtensionID(-1);
            }

            RemoteBitrateEstimatorWrapper remoteBitrateEstimatorWrapper = getRemoteBitrateEstimator();
            if (remoteBitrateEstimatorWrapper != null) {
                remoteBitrateEstimatorWrapper.setAstExtensionID(-1);
                remoteBitrateEstimatorWrapper.setTccExtensionID(-1);
            }

            if (absSendTimeEngine != null) {
                absSendTimeEngine.setExtensionID(-1);
            }
        }
    }

    /**
     * Enables all RTP extensions configured for this {@link MediaStream}.
     */
    private void enableRTPExtensions()
    {
        synchronized (activeRTPExtensions) {
            for (Map.Entry<Byte, RTPExtension> entry : activeRTPExtensions.entrySet()) {
                enableRTPExtension(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * Enables the use of a specific RTP extension.
     *
     * @param extensionID the ID.
     * @param rtpExtension the extension.
     */
    private void enableRTPExtension(byte extensionID, RTPExtension rtpExtension)
    {
        boolean active = !MediaDirection.INACTIVE.equals(rtpExtension.getDirection());
        int effectiveId = active ? RTPUtils.as16Bits(extensionID) : -1;

        String uri = rtpExtension.getURI().toString();
        switch (uri) {
            case RTPExtension.ABS_SEND_TIME_URN: {
                if (absSendTimeEngine != null) {
                    absSendTimeEngine.setExtensionID(effectiveId);
                }

                RemoteBitrateEstimatorWrapper remoteBitrateEstimatorWrapper = getRemoteBitrateEstimator();
                if (remoteBitrateEstimatorWrapper != null) {
                    remoteBitrateEstimatorWrapper.setAstExtensionID(effectiveId);
                }
                break;
            }
            case RTPExtension.FRAME_MARKING_URN:
                frameMarkingsExtensionId = effectiveId;
                break;
            case RTPExtension.ORIGINAL_HEADER_BLOCK_URN:
                ohbEngine.setExtensionID(effectiveId);
                break;
            case RTPExtension.TRANSPORT_CC_URN: {
                transportCCEngine.setExtensionID(effectiveId);
                RemoteBitrateEstimatorWrapper remoteBitrateEstimatorWrapper = getRemoteBitrateEstimator();
                if (remoteBitrateEstimatorWrapper != null) {
                    remoteBitrateEstimatorWrapper.setTccExtensionID(effectiveId);
                }
                break;
            }
        }
    }

    /**
     * Releases the resources allocated by this instance in the course of its execution and
     * prepares it to be garbage collected.
     *
     * @see MediaStream#close()
     */
    @Override
    public void close()
    {
        /*
         * Some statistics cannot be taken from the RTP manager and have to be gathered from the
         * ReceiveStream. We need to do this before calling stop().
         */
        printReceiveStreamStatistics();

        stop();
        closeSendStreams();
        srtpControl.cleanup(this);

        if (csrcEngine != null) {
            csrcEngine = null;
        }

        if (cachingTransformer != null) {
            cachingTransformer.close();
            cachingTransformer = null;
        }

        if (retransmissionRequester != null) {
            retransmissionRequester.close();
        }

        if (transformEngineChain != null) {
            PacketTransformer t = transformEngineChain.getRTPTransformer();
            if (t != null)
                t.close();
            t = transformEngineChain.getRTCPTransformer();
            if (t != null)
                t.close();
            transformEngineChain = null;
        }

        if (transportCCEngine != null) {
            transportCCEngine.removeMediaStream(this);
        }

        if (rtpManager != null) {
            printFlowStatistics(rtpManager);

            rtpManager.removeReceiveStreamListener(this);
            rtpManager.removeSendStreamListener(this);
            rtpManager.removeSessionListener(this);
            rtpManager.removeRemoteListener(this);
            try {
                rtpManager.dispose();
                rtpManager = null;
            } catch (Throwable t) {
                if (t instanceof ThreadDeath)
                    throw (ThreadDeath) t;

                /*
                 * Analysis of heap dumps and application logs suggests that RTPManager#dispose()
                 * may throw an exception after a NullPointerException has been thrown by
                 * SendStream#close() as documented in #stopSendStreams(Iterable<SendStream>,
                 * boolean). It is unknown at the time of this writing whether we can do
                 * anything to prevent the exception here but it is clear that, if we let it go
                 * through, we will not release at least one capture device (i.e. we will at
                 * least skip the MediaDeviceSession#close() bellow). For example, if the
                 * exception is thrown for the audio stream in a call, its capture device will
                 * not be released and any video stream will not get its #close() method called at all.
                 */
                Timber.e(t, "Failed to dispose of RTPManager");
            }
        }

        /*
         * XXX Call AbstractRTPConnector#removeTargets() after StreamRTPManager#dispose().
         * Otherwise, the latter will try to send an RTCP BYE and there will be no targets to send it to.
         */
        if (rtpConnector != null)
            rtpConnector.removeTargets();
        rtpConnectorTarget = null;

        if (deviceSession != null)
            deviceSession.close(MediaDirection.SENDRECV);
    }

    /**
     * Closes the <code>SendStream</code>s this instance is sending to its remote peer.
     */
    private void closeSendStreams()
    {
        stopSendStreams(true);
    }

    /**
     * Performs any optional configuration on a specific <code>RTPConnectorInputStream</code> of an
     * <code>RTPManager</code> to be used by this <code>MediaStreamImpl</code>. Allows extenders to override.
     *
     * @param dataInputStream the <code>RTPConnectorInputStream</code> to be used by an <code>RTPManager</code> of this
     * <code>MediaStreamImpl</code> and to be configured
     */
    protected void configureDataInputStream(RTPConnectorInputStream<?> dataInputStream)
    {
        dataInputStream.setPriority(getPriority());
    }

    /**
     * Performs any optional configuration on a specific <code>RTPConnectorOutputStream</code> of an
     * <code>RTPManager</code> to be used by this <code>MediaStreamImpl</code>. Allows extenders to override.
     *
     * @param dataOutputStream the <code>RTPConnectorOutputStream</code> to be used by an <code>RTPManager</code> of this
     * <code>MediaStreamImpl</code> and to be configured
     */
    protected void configureDataOutputStream(RTPConnectorOutputStream dataOutputStream)
    {
        dataOutputStream.setPriority(getPriority());
    }

    /**
     * Performs any optional configuration on the <code>BufferControl</code> of the specified
     * <code>RTPManager</code> which is to be used as the <code>RTPManager</code> of this
     * <code>MediaStreamImpl</code>. Allows extenders to override.
     *
     * @param rtpManager the <code>RTPManager</code> which is to be used by this <code>MediaStreamImpl</code>
     * @param bufferControl the <code>BufferControl</code> of <code>rtpManager</code> on which any optional
     * configuration is to be performed
     */
    protected void configureRTPManagerBufferControl(StreamRTPManager rtpManager, BufferControl bufferControl)
    {
    }

    /**
     * A stub that allows audio oriented streams to create and keep a reference to a <code>DtmfTransformEngine</code>.
     *
     * @return a <code>DtmfTransformEngine</code> if this is an audio oriented stream and <code>null</code> otherwise.
     */
    protected DtmfTransformEngine createDtmfTransformEngine()
    {
        return null;
    }

    /**
     * Creates new <code>SendStream</code> instances for the streams of {@link #deviceSession} through
     * {@link #rtpManager}.
     */
    protected void createSendStreams()
    {
        StreamRTPManager rtpManager = getRTPManager();
        MediaDeviceSession deviceSession = getDeviceSession();
        DataSource dataSource = (deviceSession == null) ? null : deviceSession.getOutputDataSource();

        int streamCount;
        if (dataSource instanceof PushBufferDataSource) {
            PushBufferStream[] streams = ((PushBufferDataSource) dataSource).getStreams();
            streamCount = (streams == null) ? 0 : streams.length;
        }
        else if (dataSource instanceof PushDataSource) {
            PushSourceStream[] streams = ((PushDataSource) dataSource).getStreams();
            streamCount = (streams == null) ? 0 : streams.length;
        }
        else if (dataSource instanceof PullBufferDataSource) {
            PullBufferStream[] streams = ((PullBufferDataSource) dataSource).getStreams();
            streamCount = (streams == null) ? 0 : streams.length;
        }
        else if (dataSource instanceof PullDataSource) {
            PullSourceStream[] streams = ((PullDataSource) dataSource).getStreams();
            streamCount = (streams == null) ? 0 : streams.length;
        }
        else
            streamCount = (dataSource == null) ? 0 : 1;

        /*
         * XXX We came up with a scenario in our testing in which G.722 would work fine for the
         * first call since the start of the application and then it would fail for subsequent
         * calls, JMF would complain that the G.722 RTP format is unknown to the RTPManager. Since
         * RTPManager#createSendStream(DataSource, int) is one of the cases in which the formats
         * registered with the RTPManager are necessary, register them (again) just before we use them.
         */
        registerCustomCodecFormats(rtpManager);

        for (int streamIndex = 0; streamIndex < streamCount; streamIndex++) {
            try {
                SendStream sendStream = rtpManager.createSendStream(dataSource, streamIndex);
                Timber.log(TimberLog.FINER, "Created SendStream with hashCode %s for %s and streamIndex %s in RTPManager with hashCode %s",
                        sendStream.hashCode(), toString(dataSource), streamIndex, rtpManager.hashCode());

                /*
                 * JMF stores the synchronization source (SSRC) identifier as a 32-bit signed
                 * integer, we store it as unsigned.
                 */
                long localSSRC = sendStream.getSSRC() & 0xFFFFFFFFL;

                if (getLocalSourceID() != localSSRC)
                    setLocalSourceID(localSSRC);
            } catch (IOException | NullPointerException ioe) {
                Timber.e(ioe, "Failed to create send stream for data source %s  and stream index %s;\n%s",
                        dataSource, streamIndex, ioe.getMessage());
            } catch (UnsupportedFormatException ufe) {
                Timber.e(ufe, "Failed to create send stream for data source %s and stream index %s because of failed format %s;;\n%s",
                        dataSource, streamIndex, ufe.getFailedFormat(), ufe.getMessage());
            }
        }
        sendStreamsAreCreated = true;

        if (TimberLog.isTraceEnable) {
            @SuppressWarnings("unchecked")
            Vector<SendStream> sendStreams = rtpManager.getSendStreams();
            int sendStreamCount = (sendStreams == null) ? 0 : sendStreams.size();

            Timber.log(TimberLog.FINER, "Total number of SendStreams in RTPManager with hashCode %s is %s",
                    rtpManager.hashCode(), sendStreamCount);
        }
    }

    protected SsrcTransformEngine createSsrcTransformEngine()
    {
        return null;
    }

    /**
     * Creates the {@link AbsSendTimeEngine} for this {@code MediaStream}.
     *
     * @return the created {@link AbsSendTimeEngine}.
     */
    protected AbsSendTimeEngine createAbsSendTimeEngine()
    {
        return new AbsSendTimeEngine();
    }

    /**
     * Creates the {@link CachingTransformer} for this {@code MediaStream}.
     *
     * @return the created {@link CachingTransformer}.
     */
    protected CachingTransformer createCachingTransformer()
    {
        return null;
    }

    /**
     * Creates the {@link RetransmissionRequester} for this {@code MediaStream}.
     *
     * @return the created {@link RetransmissionRequester}.
     */
    protected RetransmissionRequesterImpl createRetransmissionRequester()
    {
        return null;
    }

    /**
     * Creates a chain of transform engines for use with this stream. Note that this is the only
     * place where the <code>TransformEngineChain</code> is and should be manipulated to avoid
     * problems with the order of the transformers.
     *
     * @return the <code>TransformEngineChain</code> that this stream should be using.
     */
    private TransformEngineChain createTransformEngineChain()
    {
        List<TransformEngine> engineChain = new ArrayList<>(9);

        // CSRCs and CSRC audio levels
        if (csrcEngine == null) {
            csrcEngine = new CsrcTransformEngine(this);
        }
        engineChain.add(csrcEngine);

        // DTMF
        DtmfTransformEngine dtmfEngine = createDtmfTransformEngine();
        if (dtmfEngine != null) {
            engineChain.add(dtmfEngine);
        }

        engineChain.add(externalTransformerWrapper);

        // RRs and REMBs.
        TransformEngine rtcpFeedbackTermination = getRTCPTermination();
        if (rtcpFeedbackTermination != null) {
            engineChain.add(rtcpFeedbackTermination);
        }

        // here comes the override payload type transformer
        // as it changes headers of packets, need to go before encryption
        if (ptTransformEngine == null)
            ptTransformEngine = new PayloadTypeTransformEngine();
        engineChain.add(ptTransformEngine);

        // FEC
        TransformEngineWrapper<FECTransformEngine> fecTransformEngineWrapper = getFecTransformEngine();
        if (fecTransformEngineWrapper != null) {
            engineChain.add(fecTransformEngineWrapper);
        }
        // RED
        REDTransformEngine redTransformEngine = getRedTransformEngine();
        if (redTransformEngine != null)
            engineChain.add(redTransformEngine);

        // RTCP Statistics
        if (statisticsEngine == null)
            statisticsEngine = new StatisticsEngine(this);
        engineChain.add(statisticsEngine);

        if (retransmissionRequester != null) {
            engineChain.add(retransmissionRequester);
        }

        if (cachingTransformer != null) {
            engineChain.add(cachingTransformer);
        }
        // Discard
        DiscardTransformEngine discardEngine = createDiscardEngine();
        if (discardEngine != null)
            engineChain.add(discardEngine);

        MediaStreamTrackReceiver mediaStreamTrackReceiver = getMediaStreamTrackReceiver();
        if (mediaStreamTrackReceiver != null) {
            engineChain.add(mediaStreamTrackReceiver);
        }

        // Padding termination.
        PaddingTermination paddingTermination = getPaddingTermination();
        if (paddingTermination != null) {
            engineChain.add(paddingTermination);
        }

        // RTX
        RtxTransformer rtxTransformer = getRtxTransformer();
        if (rtxTransformer != null) {
            engineChain.add(rtxTransformer);
        }

        // TODO RTCP termination should end up here.

        RemoteBitrateEstimatorWrapper remoteBitrateEstimator = getRemoteBitrateEstimator();
        if (remoteBitrateEstimator != null) {
            engineChain.add(remoteBitrateEstimator);
        }

        absSendTimeEngine = createAbsSendTimeEngine();
        if (absSendTimeEngine != null) {
            engineChain.add(absSendTimeEngine);
        }

        if (transportCCEngine != null) {
            engineChain.add(transportCCEngine.getEgressEngine());
        }

        // OHB
        engineChain.add(ohbEngine);

        // SRTP
        TransformEngine srtpTransformEngine = srtpControl.getTransformEngine();
        if (srtpTransformEngine != null) {
            engineChain.add(srtpControl.getTransformEngine());
        }

        if (transportCCEngine != null) {
            engineChain.add(transportCCEngine.getIngressEngine());
        }

        // SSRC audio levels
        /*
         * It needs to go first in the reverse transform in order to be able to prevent RTP packets
         * from a muted audio source from being decrypted.
         */
        SsrcTransformEngine ssrcEngine = createSsrcTransformEngine();
        if (ssrcEngine != null) {
            engineChain.add(ssrcEngine);
        }

        // RTP extensions may be implemented in some of the engines just created (e.g.
        // created (e.g. abs-send-time). So take into account their configuration.
        enableRTPExtensions();
        return new TransformEngineChain(engineChain.toArray(new TransformEngine[0]));
    }

    /**
     * Notifies this <code>MediaStream</code> that the <code>MediaDevice</code> (and respectively the
     * <code>MediaDeviceSession</code> with it) which this instance uses for capture and playback of
     * media has been changed. Allows extenders to override and provide additional processing of
     * <code>oldValue</code> and <code>newValue</code>.
     *
     * @param oldValue the <code>MediaDeviceSession</code> with the <code>MediaDevice</code> this instance used work with
     * @param newValue the <code>MediaDeviceSession</code> with the <code>MediaDevice</code> this instance is to work with
     */
    protected void deviceSessionChanged(MediaDeviceSession oldValue, MediaDeviceSession newValue)
    {
        recreateSendStreams();
    }

    /**
     * Notifies this instance that the output <code>DataSource</code> of its
     * <code>MediaDeviceSession</code> has changed. Recreates the <code>SendStream</code>s of this
     * instance as necessary so that it, for example, continues streaming after the change if it
     * was streaming before the change.
     */
    private void deviceSessionOutputDataSourceChanged()
    {
        recreateSendStreams();
    }

    /**
     * Recalculates the list of CSRC identifiers that this <code>MediaStream</code> needs to include in
     * RTP packets bound to its interlocutor. The method uses the list of SSRC identifiers
     * currently handled by our device (possibly a mixer), then removes the SSRC ID of this
     * stream's interlocutor. If this turns out to be the only SSRC currently in the list we set
     * the list of local CSRC identifiers to null since this is obviously a non-conf call and we
     * don't need to be advertising CSRC lists. If that's not the case, we also add our own SSRC
     * to the list of IDs and cache the entire list.
     *
     * @param ev the <code>PropertyChangeEvent</code> containing the list of SSRC identifiers handled by our
     * device session before and after it changed.
     */
    private void deviceSessionSsrcListChanged(PropertyChangeEvent ev)
    {
        long[] ssrcArray = (long[]) ev.getNewValue();

        // the list is empty
        if (ssrcArray == null) {
            this.localContributingSourceIDs = null;
            return;
        }

        int elementsToRemove = 0;
        Vector<Long> remoteSourceIDs = this.remoteSourceIDs;

        // in case of a conf call the mixer would return all SSRC IDs that are currently
        // contributing including this stream's counterpart. We need to remove that last one
        // since that's where we will be sending our csrc list
        for (long csrc : ssrcArray) {
            if (remoteSourceIDs.contains(csrc))
                elementsToRemove++;
        }

        // we don't seem to be in a conf call since the list only contains the SSRC id of the
        // party that we are directly interacting with.
        if (elementsToRemove >= ssrcArray.length) {
            this.localContributingSourceIDs = null;
            return;
        }

        // prepare the new array. make it big enough to also add the local SSRC id but do not
        // make it bigger than 15 since that's the maximum for RTP.
        int cc = Math.min(ssrcArray.length - elementsToRemove + 1, 15);
        long[] csrcArray = new long[cc];

        for (int i = 0, j = 0; (i < ssrcArray.length) && (j < csrcArray.length - 1); i++) {
            long ssrc = ssrcArray[i];

            if (!remoteSourceIDs.contains(ssrc)) {
                csrcArray[j] = ssrc;
                j++;
            }
        }
        csrcArray[csrcArray.length - 1] = getLocalSourceID();
        this.localContributingSourceIDs = csrcArray;
    }

    /**
     * Sets the target of this <code>MediaStream</code> to which it is to send and from which it is to
     * receive data (e.g. RTP) and control data (e.g. RTCP). In contrast to
     * {@link #setTarget(MediaStreamTarget)}, sets the specified <code>target</code> on this
     * <code>MediaStreamImpl</code> even if its current <code>target</code> is equal to the specified one.
     *
     * @param target the <code>MediaStreamTarget</code> describing the data (e.g. RTP) and the control data
     * (e.g. RTCP) locations to which this <code>MediaStream</code> is to send and from which it is to receive
     * @see MediaStreamImpl#setTarget(MediaStreamTarget)
     */
    private void doSetTarget(MediaStreamTarget target)
    {
        InetSocketAddress newDataAddr;
        InetSocketAddress newControlAddr;
        AbstractRTPConnector connector = rtpConnector;

        if (target == null) {
            newDataAddr = null;
            newControlAddr = null;
        }
        else {
            newDataAddr = target.getDataAddress();
            newControlAddr = target.getControlAddress();
        }

        /*
         * Invoke AbstractRTPConnector#removeTargets() if the new value does actually remove an
         * RTP or RTCP target in comparison to the old value. If the new value is equal to the
         * oldValue or adds an RTP or RTCP target (i.e. the old value does not specify the
         * respective RTP or RTCP target and the new value does), then removeTargets is
         * unnecessary and would've needlessly allowed a (tiny) interval of (execution) time
         * (between removeTargets and addTarget) without a target.
         */
        if (rtpConnectorTarget != null && connector != null) {
            InetSocketAddress oldDataAddr = rtpConnectorTarget.getDataAddress();
            boolean removeTargets = !Objects.equals(oldDataAddr, newDataAddr);

            if (!removeTargets) {
                InetSocketAddress oldControlAddr = rtpConnectorTarget.getControlAddress();
                removeTargets = !Objects.equals(oldControlAddr, newControlAddr);
            }
            if (removeTargets) {
                connector.removeTargets();
                rtpConnectorTarget = null;
            }
        }

        boolean targetIsSet;

        if ((target == null) || (newDataAddr == null) || (connector == null)) {
            targetIsSet = true;
        }
        else {
            try {
                InetAddress controlInetAddr;
                int controlPort;

                if (newControlAddr == null) {
                    controlInetAddr = null;
                    controlPort = 0;
                }
                else {
                    controlInetAddr = newControlAddr.getAddress();
                    controlPort = newControlAddr.getPort();
                }

                connector.addTarget(new SessionAddress(
                        newDataAddr.getAddress(), newDataAddr.getPort(), controlInetAddr, controlPort));
                targetIsSet = true;
            } catch (IOException ioe) {
                targetIsSet = false;
                Timber.e(ioe, "Failed to set target %s", target);
            }
        }
        if (targetIsSet) {
            rtpConnectorTarget = target;

            Timber.log(TimberLog.FINER, "Set target of %s with hashCode %s to %s",
                    getClass().getSimpleName(), hashCode(), target);
        }
    }

    /**
     * Gets the {@link TransportCCEngine} instance, if any, for this
     * {@link MediaStream}. The instance could be shared between more than one
     * {@link MediaStream}, if they all use the same transport.
     */
    public TransportCCEngine getTransportCCEngine()
    {
        return this.transportCCEngine;
    }

    /**
     * Returns the ID currently assigned to a specific RTP extension.
     *
     * @param rtpExtension the RTP extension to get the currently assigned ID of
     * @return the ID currently assigned to the specified RTP extension or <code>-1</code> if no ID has
     * been defined for this extension so far
     */
    public byte getActiveRTPExtensionID(RTPExtension rtpExtension)
    {
        synchronized (activeRTPExtensions) {
            for (Map.Entry<Byte, RTPExtension> entry : activeRTPExtensions.entrySet()) {
                if (entry.getValue().equals(rtpExtension))
                    return entry.getKey();
            }
        }
        return -1;
    }

    /**
     * Returns a map containing all currently active <code>RTPExtension</code>s in use by this stream.
     *
     * @return a map containing all currently active <code>RTPExtension</code>s in use by this stream.
     */
    @Override
    public Map<Byte, RTPExtension> getActiveRTPExtensions()
    {
        synchronized (activeRTPExtensions) {
            return new HashMap<>(activeRTPExtensions);
        }
    }

    /**
     * Returns the engine that is responsible for adding the list of CSRC identifiers to outgoing
     * RTP packets during a conference.
     *
     * @return the engine that is responsible for adding the list of CSRC identifiers to outgoing
     * RTP packets during a conference.
     */
    protected CsrcTransformEngine getCsrcEngine()
    {
        return csrcEngine;
    }

    /**
     * Gets the <code>MediaDevice</code> that this stream uses to play back and capture media.
     *
     * @return the <code>MediaDevice</code> that this stream uses to play back and capture media
     * @see MediaStream#getDevice()
     */
    @Override
    public AbstractMediaDevice getDevice()
    {
        MediaDeviceSession deviceSession = getDeviceSession();
        return (deviceSession == null) ? null : deviceSession.getDevice();
    }

    /**
     * Gets the <code>MediaDirection</code> of the <code>device</code> of this instance. In case there
     * is no device, {@link MediaDirection#SENDRECV} is assumed.
     *
     * @return the <code>MediaDirection</code> of the <code>device</code> of this instance if any or
     * <code>MediaDirection.SENDRECV</code>
     */
    private MediaDirection getDeviceDirection()
    {
        MediaDeviceSession deviceSession = getDeviceSession();
        return (deviceSession == null) ? MediaDirection.SENDRECV : deviceSession.getDevice().getDirection();
    }

    /**
     * Gets the <code>MediaDeviceSession</code> which represents the work of this <code>MediaStream</code>
     * with its associated <code>MediaDevice</code>.
     *
     * @return the <code>MediaDeviceSession</code> which represents the work of this
     * <code>MediaStream</code> with its associated <code>MediaDevice</code>
     */
    public MediaDeviceSession getDeviceSession()
    {
        return deviceSession;
    }

    /**
     * Gets the direction in which this <code>MediaStream</code> is allowed to stream media.
     *
     * @return the <code>MediaDirection</code> in which this <code>MediaStream</code> is allowed to stream media
     * @see MediaStream#getDirection()
     */
    @Override
    public MediaDirection getDirection()
    {
        return (mDirection == null) ? getDeviceDirection() : mDirection;
    }

    /**
     * Returns the payload type number that has been negotiated for the specified <code>encoding</code>
     * or <code>-1</code> if no payload type has been negotiated for it. If multiple formats match the
     * specified <code>encoding</code>, then this method would return the first one it encounters while
     * iterating through the map.
     *
     * @param encoding the encoding whose payload type we are trying to obtain.
     * @return the payload type number that has been negotiated for the specified <code>encoding</code>
     * or <code>-1</code> if no payload type has been negotiated for it.
     */
    public byte getDynamicRTPPayloadType(String encoding)
    {
        for (Map.Entry<Byte, MediaFormat> dynamicRTPPayloadType : getDynamicRTPPayloadTypes().entrySet())
            if (dynamicRTPPayloadType.getValue().getEncoding().equals(encoding)) {
                return dynamicRTPPayloadType.getKey();
            }
        return -1;
    }

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
     * @see MediaStream#getDynamicRTPPayloadTypes()
     */
    @Override
    public Map<Byte, MediaFormat> getDynamicRTPPayloadTypes()
    {
        synchronized (dynamicRTPPayloadTypes) {
            return new HashMap<>(dynamicRTPPayloadTypes);
        }
    }

    /**
     * Creates the <code>FECTransformEngine</code> for this <code>MediaStream</code>. By default none is
     * created, allows extenders to implement it.
     *
     * @return the <code>FECTransformEngine</code> created.
     */
    protected TransformEngineWrapper<FECTransformEngine> getFecTransformEngine()
    {
        return null;
    }

    /**
     * Sets the {@link FECTransformEngine} for this {@link MediaStream}
     * By default, nothing is done with the passed engine, allowing extenders to implement it
     *
     * @param fecTransformEngine FEC Transform Engine
     */
    protected void setFecTransformEngine(FECTransformEngine fecTransformEngine)
    {
        // no op
    }

    /**
     * Gets the <code>MediaFormat</code> that this stream is currently transmitting in.
     *
     * @return the <code>MediaFormat</code> that this stream is currently transmitting in
     * @see MediaStream#getFormat()
     */
    @Override
    public MediaFormat getFormat()
    {
        MediaDeviceSession devSess = getDeviceSession();
        return (devSess == null) ? null : devSess.getFormat();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MediaFormat getFormat(byte pt)
    {
        synchronized (dynamicRTPPayloadTypes) {
            return dynamicRTPPayloadTypes.get(pt);
        }
    }

    /**
     * Returns the list of CSRC identifiers for all parties currently known to contribute to the
     * media that this stream is sending toward its remote counter part. In other words, the method
     * returns the list of CSRC IDs that this stream will include in outgoing RTP packets. This
     * method will return an <code>null</code> in case this stream is not part of a mixed conference call.
     *
     * @return a <code>long[]</code> array of CSRC IDs representing parties that are currently known to
     * contribute to the media that this stream is sending or an <code>null</code> in case this
     * <code>MediaStream</code> is not part of a conference call.
     */
    public long[] getLocalContributingSourceIDs()
    {
        return localContributingSourceIDs;
    }

    /**
     * Gets the local address that this stream is sending RTCP traffic from.
     *
     * @return an <code>InetSocketAddress</code> instance indicating the local address that this stream
     * is sending RTCP traffic from.
     */
    public InetSocketAddress getLocalControlAddress()
    {
        StreamConnector connector = (rtpConnector != null) ? rtpConnector.getConnector() : null;
        if (connector != null) {
            if (connector.getDataSocket() != null) {
                return (InetSocketAddress) connector.getControlSocket().getLocalSocketAddress();
            }
            else if (connector.getDataTCPSocket() != null) {
                return (InetSocketAddress) connector.getControlTCPSocket().getLocalSocketAddress();
            }
        }
        return null;
    }

    /**
     * Gets the local address that this stream is sending RTP traffic from.
     *
     * @return an <code>InetSocketAddress</code> instance indicating the local address that this stream
     * is sending RTP traffic from.
     */
    public InetSocketAddress getLocalDataAddress()
    {
        StreamConnector connector = (rtpConnector != null) ? rtpConnector.getConnector() : null;
        if (connector != null) {
            if (connector.getDataSocket() != null) {
                return (InetSocketAddress) connector.getDataSocket().getLocalSocketAddress();
            }
            else if (connector.getDataTCPSocket() != null) {
                return (InetSocketAddress) connector.getDataTCPSocket().getLocalSocketAddress();
            }
        }
        return null;
    }

    /**
     * Gets the synchronization source (SSRC) identifier of the local peer or <code>-1</code> if it is not yet known.
     *
     * @return the synchronization source (SSRC) identifier of the local peer or <code>-1</code> if it is not yet known
     * @see MediaStream#getLocalSourceID()
     */
    @Override
    public long getLocalSourceID()
    {
        return localSourceID;
    }

    /**
     * Returns the statistical information gathered about this <code>MediaStream</code>.
     *
     * @return the statistical information gathered about this <code>MediaStream</code>
     */
    @Override
    public MediaStreamStats2Impl getMediaStreamStats()
    {
        return mediaStreamStatsImpl;
    }

    /**
     * Gets the <code>MediaType</code> of this <code>MediaStream</code>.
     *
     * @return the <code>MediaType</code> of this <code>MediaStream</code>
     */
    public MediaType getMediaType()
    {
        MediaFormat format = getFormat();
        MediaType mediaType = null;

        if (format != null)
            mediaType = format.getMediaType();
        if (mediaType == null) {
            MediaDeviceSession deviceSession = getDeviceSession();

            if (deviceSession != null)
                mediaType = deviceSession.getDevice().getMediaType();
            if (mediaType == null) {
                if (this instanceof AudioMediaStream)
                    mediaType = MediaType.AUDIO;
                else if (this instanceof VideoMediaStream)
                    mediaType = MediaType.VIDEO;
            }
        }
        return mediaType;
    }

    /**
     * Used to set the priority of the receive/send streams. Underling implementations can override
     * this and return different than current default value.
     *
     * @return the priority for the current thread.
     */
    protected int getPriority()
    {
        return Thread.currentThread().getPriority();
    }

    /**
     * Gets a <code>ReceiveStream</code> which this instance plays back on its associated
     * <code>MediaDevice</code> and which has a specific synchronization source identifier (SSRC).
     *
     * @param ssrc the synchronization source identifier of the <code>ReceiveStream</code> to return
     * @return a <code>ReceiveStream</code> which this instance plays back on its associated
     * <code>MediaDevice</code> and which has the specified <code>ssrc</code>
     */
    public ReceiveStream getReceiveStream(int ssrc)
    {
        for (ReceiveStream receiveStream : getReceiveStreams()) {
            int receiveStreamSSRC = (int) receiveStream.getSSRC();

            if (receiveStreamSSRC == ssrc)
                return receiveStream;
        }
        return null;
    }

    /**
     * Gets a list of the <code>ReceiveStream</code>s this instance plays back on its associated <code>MediaDevice</code>.
     *
     * @return a list of the <code>ReceiveStream</code>s this instance plays back on its associated
     * <code>MediaDevice</code>
     */
    public Collection<ReceiveStream> getReceiveStreams()
    {
        Set<ReceiveStream> receiveStreams = new HashSet<>();

        // This instance maintains a list of the ReceiveStreams.
        Lock readLock = receiveStreamsLock.readLock();

        readLock.lock();
        try {
            receiveStreams.addAll(this.receiveStreams);
        } finally {
            readLock.unlock();
        }

        /*
         * Unfortunately, it has been observed that sometimes there are valid ReceiveStreams in
         * this instance which are not returned by the rtpManager.
         */
        StreamRTPManager rtpManager = queryRTPManager();
        if (rtpManager != null) {
            @SuppressWarnings("unchecked")
            Collection<ReceiveStream> rtpManagerReceiveStreams = rtpManager.getReceiveStreams();

            if (rtpManagerReceiveStreams != null) {
                receiveStreams.addAll(rtpManagerReceiveStreams);
            }
        }
        return receiveStreams;
    }

    /**
     * Creates the <code>REDTransformEngine</code> for this <code>MediaStream</code>. By default none is
     * created, allows extenders to implement it.
     *
     * @return the <code>REDTransformEngine</code> created.
     */
    protected REDTransformEngine getRedTransformEngine()
    {
        return null;
    }

    /**
     * Returns the <code>List</code> of CSRC identifiers representing the parties contributing to the
     * stream that we are receiving from this <code>MediaStream</code>'s remote party.
     *
     * @return a <code>List</code> of CSRC identifiers representing the parties contributing to the
     * stream that we are receiving from this <code>MediaStream</code>'s remote party.
     */
    public long[] getRemoteContributingSourceIDs()
    {
        return getDeviceSession().getRemoteSSRCList();
    }

    /**
     * Gets the address that this stream is sending RTCP traffic to.
     *
     * @return an <code>InetSocketAddress</code> instance indicating the address that this stream is sending RTCP traffic to
     * @see MediaStream#getRemoteControlAddress()
     */
    @Override
    public InetSocketAddress getRemoteControlAddress()
    {
        if (rtpConnector != null) {
            StreamConnector connector = rtpConnector.getConnector();

            if (connector != null) {
                if (connector.getDataSocket() != null) {
                    return (InetSocketAddress) connector.getControlSocket().getRemoteSocketAddress();
                }
                else if (connector.getDataTCPSocket() != null) {
                    return (InetSocketAddress) connector.getControlTCPSocket().getRemoteSocketAddress();
                }
            }
        }
        return null;
    }

    /**
     * Gets the address that this stream is sending RTP traffic to.
     *
     * @return an <code>InetSocketAddress</code> instance indicating the address that this stream is
     * sending RTP traffic to
     * @see MediaStream#getRemoteDataAddress()
     */
    @Override
    public InetSocketAddress getRemoteDataAddress()
    {
        StreamConnector connector = (rtpConnector != null) ? rtpConnector.getConnector() : null;

        if (connector != null) {
            if (connector.getDataSocket() != null) {
                return (InetSocketAddress) connector.getDataSocket().getRemoteSocketAddress();
            }
            else if (connector.getDataTCPSocket() != null) {
                return (InetSocketAddress) connector.getDataTCPSocket().getRemoteSocketAddress();
            }
        }
        return null;
    }

    /**
     * {@inheritDoc}
     *
     * Returns the last element of {@link #getRemoteSourceIDs()} which may or may not always be appropriate.
     *
     * @see MediaStream#getRemoteSourceID()
     */
    @Override
    public long getRemoteSourceID()
    {
        return remoteSourceIDs.isEmpty() ? -1 : remoteSourceIDs.lastElement();
    }

    /**
     * Gets the synchronization source (SSRC) identifiers of the remote peer.
     *
     * @return the synchronization source (SSRC) identifiers of the remote peer
     */
    @Override
    public List<Long> getRemoteSourceIDs()
    {
        /*
         * TODO Returning an unmodifiable view of remoteSourceIDs prevents modifications of private
         * state from the outside but it does not prevent ConcurrentModificationException.
         */
        return Collections.unmodifiableList(remoteSourceIDs);
    }

    /**
     * Gets the <code>RTPConnector</code> through which this instance sends and receives RTP and RTCP traffic.
     *
     * @return the <code>RTPConnector</code> through which this instance sends and receives RTP and RTCP traffic
     */
    protected AbstractRTPConnector getRTPConnector()
    {
        return rtpConnector;
    }

    /**
     * Gets the <code>RTPManager</code> instance which sends and receives RTP and RTCP traffic on
     * behalf of this <code>MediaStream</code>. If the <code>RTPManager</code> does not exist yet, it is created.
     *
     * @return the <code>RTPManager</code> instance which sends and receives RTP and RTCP traffic on
     * behalf of this <code>MediaStream</code>
     */
    public StreamRTPManager getRTPManager()
    {
        if (rtpManager == null) {
            RTPConnector rtpConnector = getRTPConnector();

            if (rtpConnector == null)
                throw new IllegalStateException("rtpConnector");

            rtpManager = new StreamRTPManager(this, rtpTranslator);
            registerCustomCodecFormats(rtpManager);

            rtpManager.addReceiveStreamListener(this);
            rtpManager.addSendStreamListener(this);
            rtpManager.addSessionListener(this);
            rtpManager.addRemoteListener(this);

            BufferControl bc = rtpManager.getControl(BufferControl.class);

            if (bc != null)
                configureRTPManagerBufferControl(rtpManager, bc);

            rtpManager.setSSRCFactory(ssrcFactory);
            rtpManager.initialize(rtpConnector);

            // JMF initializes the local SSRC upon #initialize(RTPConnector) so now's the time to
            // ask. As JMF stores the SSRC as a 32-bit signed integer value, convert it to
            // unsigned.
            long localSSRC = rtpManager.getLocalSSRC();
            setLocalSourceID((localSSRC == Long.MAX_VALUE) ? -1 : (localSSRC & 0xFFFFFFFFL));
        }
        return rtpManager;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StreamRTPManager getStreamRTPManager()
    {
        return queryRTPManager();
    }

    /**
     * Gets the <code>SrtpControl</code> which controls the SRTP of this stream.
     *
     * @return the <code>SrtpControl</code> which controls the SRTP of this stream
     */
    @Override
    public SrtpControl getSrtpControl()
    {
        return srtpControl;
    }

    /**
     * Returns the target of this <code>MediaStream</code> to which it is to send and from which it is
     * to receive data (e.g. RTP) and control data (e.g. RTCP).
     *
     * @return the <code>MediaStreamTarget</code> describing the data (e.g. RTP) and the control data
     * (e.g. RTCP) locations to which this <code>MediaStream</code> is to send and from which it is to receive
     * @see MediaStream#setTarget(MediaStreamTarget)
     */
    @Override
    public MediaStreamTarget getTarget()
    {
        return rtpConnectorTarget;
    }

    /**
     * Returns the transport protocol used by the streams.
     *
     * @return the transport protocol (UDP or TCP) used by the streams. null if the stream connector is not instantiated.
     */
    @Override
    public StreamConnector.Protocol getTransportProtocol()
    {
        StreamConnector connector = (rtpConnector != null) ? rtpConnector.getConnector() : null;
        if (connector == null) {
            return null;
        }
        return connector.getProtocol();
    }

    /**
     * Determines whether this <code>MediaStream</code> is set to transmit "silence" instead of the
     * media being fed from its <code>MediaDevice</code>. "Silence" for video is understood as video
     * data which is not the captured video data and may represent, for example, a black image.
     *
     * @return <code>true</code> if this <code>MediaStream</code> is set to transmit "silence" instead of
     * the media fed from its <code>MediaDevice</code>; <code>false</code>, otherwise
     * @see MediaStream#isMute()
     */
    @Override
    public boolean isMute()
    {
        MediaDeviceSession deviceSession = getDeviceSession();
        return (deviceSession == null) ? mute : deviceSession.isMute();
    }

    /**
     * Determines whether {@link #start()} has been called on this <code>MediaStream</code> without
     * {@link #stop()} or {@link #close()} afterwards.
     *
     * @return <code>true</code> if {@link #start()} has been called on this <code>MediaStream</code>
     * without {@link #stop()} or {@link #close()} afterwards
     * @see MediaStream#isStarted()
     */
    @Override
    public boolean isStarted()
    {
        return started;
    }

    /**
     * If necessary and the state of this <code>MediaStreamImpl</code> instance is appropriate, updates
     * the FMJ <code>Format</code>s registered with a specific <code>StreamRTPManager</code> in order to
     * possibly prevent the loss of format parameters (i.e. SDP fmtp) specified by the remote peer
     * and to be used for the playback of <code>ReceiveStream</code>s. The <code>Format</code>s in
     * {@link #dynamicRTPPayloadTypes} will likely represent the view of the local peer while the
     * <code>Format</code> set on this <code>MediaStream</code> instance will likely represent the view of
     * the remote peer. The view of the remote peer matters for the playback of <code>ReceiveStream</code>s.
     *
     * @param rtpManager the <code>StreamRTPManager</code> to update the registered FMJ <code>Format</code>s of. If
     * <code>null</code>, the method uses {@link #rtpManager}.
     */
    private void maybeUpdateDynamicRTPPayloadTypes(StreamRTPManager rtpManager)
    {
        if (rtpManager == null) {
            rtpManager = queryRTPManager();
            if (rtpManager == null)
                return;
        }

        MediaFormat mediaFormat = getFormat();
        if (!(mediaFormat instanceof MediaFormatImpl))
            return;

        @SuppressWarnings("unchecked")
        MediaFormatImpl<? extends Format> mediaFormatImpl = (MediaFormatImpl<? extends Format>) mediaFormat;
        Format format = mediaFormatImpl.getFormat();

        if (!(format instanceof ParameterizedVideoFormat))
            return;

        for (Map.Entry<Byte, MediaFormat> dynamicRTPPayloadType : getDynamicRTPPayloadTypes().entrySet()) {
            MediaFormat dynamicMediaFormat = dynamicRTPPayloadType.getValue();

            if (!(dynamicMediaFormat instanceof MediaFormatImpl))
                continue;

            MediaFormatImpl<? extends Format> dynamicMediaFormatImpl
                    = (MediaFormatImpl<? extends Format>) dynamicMediaFormat;
            Format dynamicFormat = dynamicMediaFormatImpl.getFormat();

            if (format.matches(dynamicFormat) && dynamicFormat.matches(format)) {
                rtpManager.addFormat(format, dynamicRTPPayloadType.getKey());
            }
        }
    }

    /**
     * Prints all statistics available for {@link #rtpManager}.
     *
     * @param rtpManager the <code>RTPManager</code> to print statistics for
     */
    private void printFlowStatistics(StreamRTPManager rtpManager)
    {
        try {
            if (!TimberLog.isFinestEnable)
                return;

            // print flow statistics.
            GlobalTransmissionStats s = rtpManager.getGlobalTransmissionStats();

            String rtpstat = StatisticsEngine.RTP_STAT_PREFIX;
            MediaStreamStats2Impl mss = getMediaStreamStats();
            StringBuilder buff = new StringBuilder(rtpstat);
            MediaType mediaType = getMediaType();
            String mediaTypeStr = (mediaType == null) ? "" : mediaType.toString();
            String eol = "\n" + rtpstat;

            buff.append("call stats for outgoing ").append(mediaTypeStr)
                    .append(" stream SSRC: ").append(getLocalSourceID()).append(eol)
                    .append("bytes sent: ").append(s.getBytesSent()).append(eol)
                    .append("RTP sent: ").append(s.getRTPSent()).append(eol)
                    .append("remote reported min inter-arrival jitter: ")
                    .append(mss.getMinUploadJitterMs()).append("ms").append(eol)
                    .append("remote reported max inter-arrival jitter: ")
                    .append(mss.getMaxUploadJitterMs()).append("ms").append(eol)
                    .append("local collisions: ").append(s.getLocalColls())
                    .append(eol)
                    .append("remote collisions: ").append(s.getRemoteColls())
                    .append(eol)
                    .append("RTCP sent: ").append(s.getRTCPSent()).append(eol)
                    .append("transmit failed: ").append(s.getTransmitFailed());

            Timber.log(TimberLog.FINER, "%s", buff);

            GlobalReceptionStats rs = rtpManager.getGlobalReceptionStats();
            MediaFormat format = getFormat();

            buff = new StringBuilder(rtpstat);
            buff.append("call stats for incoming ")
                    .append((format == null) ? "" : format)
                    .append(" stream SSRC: ").append(getRemoteSourceID())
                    .append(eol)
                    .append("packets received: ").append(rs.getPacketsRecd())
                    .append(eol)
                    .append("bytes received: ").append(rs.getBytesRecd())
                    .append(eol)
                    .append("packets lost: ")
                    .append(mss.getReceiveStats().getPacketsLost())
                    .append(eol)
                    .append("min inter-arrival jitter: ")
                    .append(statisticsEngine.getMinInterArrivalJitter())
                    .append(eol)
                    .append("max inter-arrival jitter: ")
                    .append(statisticsEngine.getMaxInterArrivalJitter())
                    .append(eol)
                    .append("RTCPs received: ").append(rs.getRTCPRecd())
                    .append(eol)
                    .append("bad RTCP packets: ").append(rs.getBadRTCPPkts())
                    .append(eol)
                    .append("bad RTP packets: ").append(rs.getBadRTPkts())
                    .append(eol)
                    .append("local collisions: ").append(rs.getLocalColls())
                    .append(eol)
                    .append("malformed BYEs: ").append(rs.getMalformedBye())
                    .append(eol)
                    .append("malformed RRs: ").append(rs.getMalformedRR())
                    .append(eol)
                    .append("malformed SDESs: ").append(rs.getMalformedSDES())
                    .append(eol)
                    .append("malformed SRs: ").append(rs.getMalformedSR())
                    .append(eol)
                    .append("packets looped: ").append(rs.getPacketsLooped())
                    .append(eol)
                    .append("remote collisions: ").append(rs.getRemoteColls())
                    .append(eol)
                    .append("SRs received: ").append(rs.getSRRecd())
                    .append(eol)
                    .append("transmit failed: ").append(rs.getTransmitFailed())
                    .append(eol)
                    .append("unknown types: ").append(rs.getUnknownTypes());

            Timber.log(TimberLog.FINER, "%s", buff);
        } catch (Throwable t) {
            Timber.e(t, "Error writing statistics");
        }
    }

    private void printReceiveStreamStatistics()
    {
        mediaStreamStatsImpl.updateStats();

        if (TimberLog.isTraceEnable) {
            StringBuilder buff = new StringBuilder("\nReceive stream stats: discarded RTP packets: ")
                    .append(mediaStreamStatsImpl.getNbDiscarded())
                    .append("\nReceive stream stats: decoded with FEC: ")
                    .append(mediaStreamStatsImpl.getNbFec());

            Timber.log(TimberLog.FINER, "%s", buff);
        }
    }

    /**
     * Gets the <code>RTPManager</code> instance which sends and receives RTP and RTCP traffic on
     * behalf of this <code>MediaStream</code>. If the <code>RTPManager</code> does not exist yet, it is not created.
     *
     * @return the <code>RTPManager</code> instance which sends and receives RTP and RTCP traffic on
     * behalf of this <code>MediaStream</code>
     */
    public StreamRTPManager queryRTPManager()
    {
        return rtpManager;
    }

    /**
     * Recreates the <code>SendStream</code>s of this instance (i.e. of its <code>RTPManager</code>) as
     * necessary. For example, if there was no attempt to create the <code>SendStream</code>s prior to
     * the call, does nothing. If they were created prior to the call, closes them and creates them
     * again. If they were not started prior to the call, does not start them after recreating them.
     */
    protected void recreateSendStreams()
    {
        if (sendStreamsAreCreated) {
            closeSendStreams();

            if ((getDeviceSession() != null) && (rtpManager != null)) {
                if (MediaDirection.SENDONLY.equals(startedDirection)
                        || MediaDirection.SENDRECV.equals(startedDirection))
                    startSendStreams();
            }
        }
    }

    /**
     * Registers any custom JMF <code>Format</code>s with a specific <code>RTPManager</code>. Extenders
     * should override in order to register their own customizations and should call back to this
     * super implementation during the execution of their override in order to register the
     * associations defined in this instance of (dynamic) RTP payload types to <code>MediaFormat</code>s.
     *
     * @param rtpManager the <code>RTPManager</code> to register any custom JMF <code>Format</code> s with
     */
    protected void registerCustomCodecFormats(StreamRTPManager rtpManager)
    {
        for (Map.Entry<Byte, MediaFormat> dynamicRTPPayloadType : getDynamicRTPPayloadTypes().entrySet()) {
            MediaFormatImpl<? extends Format> mediaFormatImpl
                    = (MediaFormatImpl<? extends Format>) dynamicRTPPayloadType.getValue();
            Format format = mediaFormatImpl.getFormat();
            rtpManager.addFormat(format, dynamicRTPPayloadType.getKey());
        }
        maybeUpdateDynamicRTPPayloadTypes(rtpManager);
    }

    /**
     * Removes a specific <code>ReceiveStream</code> from {@link #receiveStreams}.
     *
     * @param receiveStream the <code>ReceiveStream</code> to remove
     * @return <code>true</code> if <code>receiveStreams</code> changed as a result of the method call;
     * otherwise, <code>false</code>
     */
    private boolean removeReceiveStream(ReceiveStream receiveStream)
    {
        Lock writeLock = receiveStreamsLock.writeLock();
        Lock readLock = receiveStreamsLock.readLock();
        boolean removed = false;

        writeLock.lock();
        try {
            if (receiveStreams.remove(receiveStream)) {
                /*
                 * Downgrade the writeLock to a readLock in order to allow readers during the invocation of
                 * MediaDeviceSession#removeReceiveStream(ReceiveStream) (and disallow writers, of course).
                 */
                readLock.lock();
                removed = true;
            }
        } finally {
            writeLock.unlock();
        }
        if (removed) {
            try {
                MediaDeviceSession deviceSession = getDeviceSession();

                if (deviceSession != null)
                    deviceSession.removeReceiveStream(receiveStream);
            } finally {
                readLock.unlock();
            }
        }
        return removed;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeReceiveStreamForSsrc(long ssrc)
    {
        ReceiveStream toRemove = getReceiveStream((int) ssrc);

        if (toRemove != null)
            removeReceiveStream(toRemove);
    }

    /**
     * Notifies this <code>MediaStream</code> implementation that its <code>RTPConnector</code> instance
     * has changed from a specific old value to a specific new value. Allows extenders to
     * override and perform additional processing after this <code>MediaStream</code> has changed its
     * <code>RTPConnector</code> instance.
     *
     * @param oldValue the <code>RTPConnector</code> of this <code>MediaStream</code> implementation before it got
     * changed to <code>newValue</code>
     * @param newValue the current <code>RTPConnector</code> of this <code>MediaStream</code> which replaced <code>oldValue</code>
     */
    protected void rtpConnectorChanged(AbstractRTPConnector oldValue, AbstractRTPConnector newValue)
    {
        if (newValue != null) {
            /*
             * Register the transform engines that we will be using in this stream.
             */
            if (newValue instanceof RTPTransformUDPConnector) {
                transformEngineChain = createTransformEngineChain();
                ((RTPTransformUDPConnector) newValue).setEngine(transformEngineChain);
            }
            else if (newValue instanceof RTPTransformTCPConnector) {
                transformEngineChain = createTransformEngineChain();
                ((RTPTransformTCPConnector) newValue).setEngine(transformEngineChain);
            }

            if (rtpConnectorTarget != null)
                doSetTarget(rtpConnectorTarget);

            // Trigger the re-configuration of RTP header extensions
            addRTPExtension((byte) 0, null);
        }
        srtpControl.setConnector(newValue);

        /*
         * TODO The following is a very ugly way to expose the RTPConnector created by this
         * instance so it may be configured from outside the class hierarchy. That's why the
         * property in use bellow is not defined as a well-known constant and is to be considered
         * internal and likely to be removed in a future revision.
         */
        try {
            firePropertyChange(MediaStreamImpl.class.getName() + ".rtpConnector", oldValue, newValue);
        } catch (Throwable t) {
            if (t instanceof ThreadDeath)
                throw (ThreadDeath) t;
            else
                Timber.e(t);
        }
    }

    /**
     * Notifies this instance that its {@link #rtpConnector} has created a new
     * <code>RTPConnectorInputStream</code> either RTP or RTCP.
     *
     * @param inputStream the new <code>RTPConnectorInputStream</code> instance created by the <code>rtpConnector</code>
     * of this instance
     * @param data <code>true</code> if <code>inputStream</code> will be used for RTP or <code>false</code> for RTCP
     */
    private void rtpConnectorInputStreamCreated(RTPConnectorInputStream<?> inputStream, boolean data)
    {
        /*
         * TODO The following is a very ugly way to expose the RTPConnectorInputStreams created by
         * the rtpConnector of this instance so they may be configured from outside the class
         * hierarchy (e.g. to invoke addDatagramPacketFilter). That's why the property in use
         * bellow is not defined as a well-known constant and is to be considered internal and
         * likely to be removed in a future revision.
         */
        try {
            firePropertyChange(MediaStreamImpl.class.getName() + ".rtpConnector."
                    + (data ? "data" : "control") + "InputStream", null, inputStream);
        } catch (Throwable t) {
            if (t instanceof ThreadDeath)
                throw (ThreadDeath) t;
            else
                Timber.e(t);
        }
    }

    /**
     * Sets the <code>StreamConnector</code> to be used by this instance for sending and receiving media.
     *
     * @param connector the <code>StreamConnector</code> to be used by this instance for sending and receiving media
     */
    @Override
    public void setConnector(StreamConnector connector)
    {
        if (connector == null)
            throw new NullPointerException("connector");

        AbstractRTPConnector oldValue = rtpConnector;
        // Is the StreamConnector really changing?
        if ((oldValue != null) && (oldValue.getConnector() == connector))
            return;

        switch (connector.getProtocol()) {
            case UDP:
                rtpConnector = new RTPTransformUDPConnector(connector)
                {
                    @Override
                    protected RTPConnectorUDPInputStream createControlInputStream()
                            throws IOException
                    {
                        RTPConnectorUDPInputStream s = super.createControlInputStream();
                        rtpConnectorInputStreamCreated(s, false);
                        return s;
                    }

                    @Override
                    protected RTPConnectorUDPInputStream createDataInputStream()
                            throws IOException
                    {
                        RTPConnectorUDPInputStream s = super.createDataInputStream();
                        rtpConnectorInputStreamCreated(s, true);
                        if (s != null)
                            configureDataInputStream(s);
                        return s;
                    }

                    @Override
                    protected TransformUDPOutputStream createDataOutputStream()
                            throws IOException
                    {
                        TransformUDPOutputStream s = super.createDataOutputStream();
                        if (s != null)
                            configureDataOutputStream(s);
                        return s;
                    }
                };
                break;
            case TCP:
                rtpConnector = new RTPTransformTCPConnector(connector)
                {
                    @Override
                    protected RTPConnectorTCPInputStream createControlInputStream()
                            throws IOException
                    {
                        RTPConnectorTCPInputStream s = super.createControlInputStream();
                        rtpConnectorInputStreamCreated(s, false);
                        return s;
                    }

                    @Override
                    protected RTPConnectorTCPInputStream createDataInputStream()
                            throws IOException
                    {
                        RTPConnectorTCPInputStream s = super.createDataInputStream();
                        rtpConnectorInputStreamCreated(s, true);
                        if (s != null)
                            configureDataInputStream(s);
                        return s;
                    }

                    @Override
                    protected TransformTCPOutputStream createDataOutputStream()
                            throws IOException
                    {
                        TransformTCPOutputStream s = super.createDataOutputStream();
                        if (s != null)
                            configureDataOutputStream(s);
                        return s;
                    }
                };
                break;
            default:
                throw new IllegalArgumentException("connector");
        }
        rtpConnectorChanged(oldValue, rtpConnector);
    }

    /**
     * Sets the <code>MediaDevice</code> that this stream should use to play back and capture media.
     *
     * <b>Note</b>: Also resets any previous direction set with
     * {@link #setDirection(MediaDirection)} to the direction of the specified <code>MediaDevice</code>.
     *
     * @param device the <code>MediaDevice</code> that this stream should use to play back and capture media
     * @see MediaStream#setDevice(MediaDevice)
     */
    @Override
    public void setDevice(MediaDevice device)
    {
        if (device == null)
            throw new NullPointerException("device");

        // Require AbstractMediaDevice for MediaDeviceSession support.
        AbstractMediaDevice abstractMediaDevice = (AbstractMediaDevice) device;

        // cmeng: 20210403, new camera switching restricts to camera device preview handling only, does not
        // trigger codec close/open, so remove device change check. See PreviewStream#switchCamera()
        // if ((deviceSession == null) || (deviceSession.getDevice() != device)) {
        if (deviceSession == null) {
            assertDirection(mDirection, device.getDirection(), "device");

            MediaDeviceSession oldValue = deviceSession;
            MediaFormat format;
            MediaDirection startedDirection;

            if (deviceSession != null) {
                format = getFormat();
                startedDirection = deviceSession.getStartedDirection();

                deviceSession.removePropertyChangeListener(deviceSessionPropertyChangeListener);

                // keep player active
                deviceSession.setDisposePlayerOnClose(!(deviceSession instanceof VideoMediaDeviceSession));
                deviceSession.close(MediaDirection.SENDONLY);
                deviceSession = null;
            }
            else {
                format = null;
                startedDirection = MediaDirection.INACTIVE;
            }
            deviceSession = abstractMediaDevice.createSession();

            /*
             * Copy the playback from the old MediaDeviceSession into the new MediaDeviceSession in
             * order to prevent the recreation of the playback of the ReceiveStream(s) when just
             * changing the MediaDevice of this MediaSteam.
             */
            if (oldValue != null)
                deviceSession.copyPlayback(oldValue);

            deviceSession.addPropertyChangeListener(deviceSessionPropertyChangeListener);

            /*
             * Setting a new device resets any previously-set direction. Otherwise, we risk not
             * being able to set a new device if it is mandatory for the new device to fully cover
             * any previously-set direction.
             */
            mDirection = null;

            if (deviceSession != null) {
                if (format != null)
                    deviceSession.setFormat(format);
                deviceSession.setMute(mute);
            }
            deviceSessionChanged(oldValue, deviceSession);
            if (deviceSession != null) {
                deviceSession.start(startedDirection);

                // Add the receiveStreams of this instance to the new
                // deviceSession.
                Lock receiveStreamsReadLock = receiveStreamsLock.readLock();

                receiveStreamsReadLock.lock();
                try {
                    for (ReceiveStream receiveStream : receiveStreams)
                        deviceSession.addReceiveStream(receiveStream);
                } finally {
                    receiveStreamsReadLock.unlock();
                }
            }
        }
    }

    /**
     * Sets the direction in which media in this <code>MediaStream</code> is to be streamed. If this
     * <code>MediaStream</code> is not currently started, calls to {@link #start()} later on will start
     * it only in the specified <code>direction</code>. If it is currently started in a direction
     * different than the specified, directions other than the specified will be stopped.
     *
     * @param direction the <code>MediaDirection</code> in which this <code>MediaStream</code> is to stream media when
     * it is started
     * @see MediaStream#setDirection(MediaDirection)
     */
    @Override
    public void setDirection(MediaDirection direction)
    {
        if (direction == null)
            throw new NullPointerException("direction");
        if (mDirection == direction)
            return;

        Timber.log(TimberLog.FINER, "Changing direction of stream %s from: %s to: %s",
                hashCode(), mDirection, direction);

        /*
         * Make sure that the specified direction is in accord with the direction of the
         * MediaDevice of this instance.
         */
        assertDirection(direction, getDeviceDirection(), "direction");
        mDirection = direction;
        switch (mDirection) {
            case INACTIVE:
                stop(MediaDirection.SENDRECV);
                return;
            case RECVONLY:
                stop(MediaDirection.SENDONLY);
                break;
            case SENDONLY:
                stop(MediaDirection.RECVONLY);
                break;
            case SENDRECV:
                break;
            default:
                // Don't know what it may be (in the future) so ignore it.
                return;
        }
        if (started)
            start(mDirection);

        // Make sure that RTP is filtered in accord with the direction of this
        // MediaStream, so that we don't have to worry about, for example, new
        // ReceiveStreams being created while in sendonly/inactive.
        AbstractRTPConnector connector = getRTPConnector();

        if (connector != null)
            connector.setDirection(direction);
    }

    /**
     * Sets the <code>MediaFormat</code> that this <code>MediaStream</code> should transmit in.
     *
     * @param format the <code>MediaFormat</code> that this <code>MediaStream</code> should transmit in
     * @see MediaStream#setFormat(MediaFormat)
     */
    @Override
    public void setFormat(MediaFormat format)
    {
        MediaDeviceSession devSess = getDeviceSession();
        MediaFormatImpl<? extends Format> thisFormat = null;

        if (devSess != null) {
            thisFormat = devSess.getFormat();
            if ((thisFormat != null)
                    && thisFormat.equals(format)
                    && thisFormat.advancedAttributesAreEqual(thisFormat.getAdvancedAttributes(),
                    format.getAdvancedAttributes())) {
                return;
            }
        }
        Timber.log(TimberLog.FINER, "Changing format of stream %s from: %S to: %S",
                hashCode(), thisFormat, format);

        handleAttributes(format, format.getAdvancedAttributes());
        handleAttributes(format, format.getFormatParameters());

        if (devSess != null)
            devSess.setFormat(format);

        maybeUpdateDynamicRTPPayloadTypes(null);
    }

    /**
     * Sets the local SSRC identifier and fires the corresponding <code>PropertyChangeEvent</code>.
     *
     * @param localSourceID the SSRC identifier that this stream will be using in outgoing RTP packets from now on
     */
    protected void setLocalSourceID(long localSourceID)
    {
        if (this.localSourceID != localSourceID) {
            Long oldValue = this.localSourceID;
            this.localSourceID = localSourceID;

            /*
             * If ZRTP is used, then let it know about the SSRC of the new SendStream. Currently,
             * ZRTP supports only one SSRC per engine.
             */
            TransformEngine transformEngine = srtpControl.getTransformEngine();

            if (transformEngine instanceof ZRTPTransformEngine) {
                ((ZRTPTransformEngine) transformEngine).setOwnSSRC(getLocalSourceID());
            }
            firePropertyChange(PNAME_LOCAL_SSRC, oldValue, this.localSourceID);
        }
    }

    /**
     * Causes this <code>MediaStream</code> to stop transmitting the media being fed from this stream's
     * <code>MediaDevice</code> and transmit "silence" instead. "Silence" for video is understood as
     * video data which is not the captured video data and may represent, for example, a black image.
     *
     * @param mute <code>true</code> to have this <code>MediaStream</code> transmit "silence" instead of the
     * actual media data that it captures from its <code>MediaDevice</code>; <code>false</code> to
     * transmit actual media data captured from the <code>MediaDevice</code> of this <code>MediaStream</code>
     * @see MediaStream#setMute(boolean)
     */
    @Override
    public void setMute(boolean mute)
    {
        if (this.mute != mute) {
            Timber.d("%s stream with hashcode %s", (mute ? "Muting" : "Unmuting"), hashCode());

            this.mute = mute;
            MediaDeviceSession deviceSession = getDeviceSession();
            if (deviceSession != null)
                deviceSession.setMute(this.mute);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSSRCFactory(SSRCFactory ssrcFactory)
    {
        if (this.ssrcFactory != ssrcFactory) {
            this.ssrcFactory = ssrcFactory;

            StreamRTPManager rtpManager = this.rtpManager;
            RTPTranslator translator = rtpTranslator;

            if (rtpManager != null)
                rtpManager.setSSRCFactory(ssrcFactory);
            else if (translator instanceof RTPTranslatorImpl)
                ((RTPTranslatorImpl) translator).setSSRCFactory(ssrcFactory);
        }
    }

    /**
     * Sets the target of this <code>MediaStream</code> to which it is to send and from which it is to
     * receive data (e.g. RTP) and control data (e.g. RTCP).
     *
     * @param target the <code>MediaStreamTarget</code> describing the data (e.g. RTP) and the control data
     * (e.g. RTCP) locations to which this <code>MediaStream</code> is to send and from which it is to receive
     * @see MediaStream#setTarget(MediaStreamTarget)
     */
    @Override
    public void setTarget(MediaStreamTarget target)
    {
        // Short-circuit if setting the same target.
        if (target == null) {
            if (rtpConnectorTarget == null)
                return;
        }
        else if (target.equals(rtpConnectorTarget))
            return;
        doSetTarget(target);
    }

    /**
     * Starts capturing media from this stream's <code>MediaDevice</code> and then streaming it through
     * the local <code>StreamConnector</code> toward the stream's target address and port. Also puts
     * the <code>MediaStream</code> in a listening state which make it play all media received from the
     * <code>StreamConnector</code> on the stream's <code>MediaDevice</code>.
     *
     * @see MediaStream#start()
     */
    @Override
    public void start()
    {
        start(getDirection());
        started = true;
    }

    /**
     * Starts the processing of media in this instance in a specific direction.
     *
     * @param direction a <code>MediaDirection</code> value which represents the direction of the processing of
     * media to be started. For example, {@link MediaDirection#SENDRECV} to start both
     * capture and playback of media in this instance or {@link MediaDirection#SENDONLY} to
     * only start the capture of media in this instance
     */
    private void start(MediaDirection direction)
    {
        if (direction == null)
            throw new NullPointerException("direction");

        /*
         * If the local peer is the focus of a conference for which it is to perform RTP
         * translation even without generating media to be sent, it should create its StreamRTPManager.
         */
        boolean getRTPManagerForRTPTranslator = true;
        MediaDeviceSession deviceSession = getDeviceSession();

        if (direction.allowsSending()
                && ((startedDirection == null) || !startedDirection.allowsSending())) {
            /*
             * The startSendStreams method will be called so the getRTPManager method will be
             * called as part of the execution of the former.
             */
            getRTPManagerForRTPTranslator = false;
            startSendStreams();

            // cmeng: must send the actual direction which handled both send and receive stream at the same time;
            // else the remoteVideoContainer will get removed even if it is enabled
            // @link {VideoMediaDeviceSession#startedDirectionChanged(MediaDirection oldValue, MediaDirection newValue)}
            if (deviceSession != null) {
                // deviceSession.start(MediaDirection.SENDONLY);
                deviceSession.start(direction);
            }

            if (MediaDirection.RECVONLY.equals(startedDirection))
                startedDirection = MediaDirection.SENDRECV;
            else if (startedDirection == null)
                startedDirection = MediaDirection.SENDONLY;

            if (TimberLog.isTraceEnable) {
                MediaType mediaType = getMediaType();
                MediaStreamStats stats = getMediaStreamStats();

                Timber.i("%s codec/freq: %s/%s Hz", mediaType, stats.getEncoding(), stats.getEncodingClockRate());
                Timber.i("%s remote IP/port: %s/%s", mediaType, stats.getRemoteIPAddress(), stats.getRemotePort());
            }
        }

        if (direction.allowsReceiving()
                && ((startedDirection == null) || !startedDirection.allowsReceiving())) {
            /*
             * The startReceiveStreams method will be called so the getRTPManager method will be
             * called as part of the execution of the former.
             */
            getRTPManagerForRTPTranslator = false;
            startReceiveStreams();

            if (deviceSession != null) {
                // deviceSession.start(MediaDirection.RECVONLY);
                deviceSession.start(direction);
            }

            if (MediaDirection.SENDONLY.equals(startedDirection))
                startedDirection = MediaDirection.SENDRECV;
            else if (startedDirection == null)
                startedDirection = MediaDirection.RECVONLY;
        }

        /*
         * If the local peer is the focus of a conference for which it is to perform RTP
         * translation even without generating media to be sent, it should create its StreamRTPManager.
         */
        if (getRTPManagerForRTPTranslator && (rtpTranslator != null))
            getRTPManager();
    }

    /**
     * Starts the <code>ReceiveStream</code>s that this instance is receiving from its remote peer. By
     * design, a <code>MediaStream</code> instance is associated with a single
     * <code>ReceiveStream</code> at a time. However, the <code>ReceiveStream</code>s are created by
     * <code>RTPManager</code> and it tracks multiple <code>ReceiveStream</code>s. In practice, the
     * <code>RTPManager</code> of this <code>MediaStreamImpl</code> will have a single <code>ReceiveStream</code> in its list.
     */
    private void startReceiveStreams()
    {
        /*
         * The ReceiveStreams originate from RtpManager, make sure that there is an actual
         * RTPManager to initialize ReceiveStreams which are then to be started.
         */
        getRTPManager();

        for (ReceiveStream receiveStream : getReceiveStreams()) {
            try {
                DataSource receiveStreamDataSource = receiveStream.getDataSource();

                /*
                 * For an unknown reason, the stream DataSource can be null at the end of the Call
                 * after re-invites have been handled.
                 */
                if (receiveStreamDataSource != null)
                    receiveStreamDataSource.start();
            } catch (IOException ioex) {
                Timber.w(ioex, "Failed to start receive stream %s", receiveStream);
            }
        }
    }

    /**
     * Starts the <code>SendStream</code>s of the <code>RTPManager</code> of this <code>MediaStreamImpl</code>.
     */
    private void startSendStreams()
    {
        /*
         * Until it's clear that the SendStreams are required (i.e. we've negotiated to send), they
         * will not be created. Otherwise, their creation isn't only illogical but also causes the
         * CaptureDevice to be used.
         */
        if (!sendStreamsAreCreated)
            createSendStreams();

        StreamRTPManager rtpManager = getRTPManager();
        @SuppressWarnings("unchecked")
        Iterable<SendStream> sendStreams = rtpManager.getSendStreams();

        if (sendStreams != null) {
            for (SendStream sendStream : sendStreams) {
                try {
                    DataSource sendStreamDataSource = sendStream.getDataSource();

                    // TODO Are we sure we want to connect here?
                    sendStreamDataSource.connect();
                    sendStream.start();
                    sendStreamDataSource.start();

                    Timber.log(TimberLog.FINER, "Started SendStream with hashCode %s", sendStream.hashCode());
                } catch (IOException ioe) {
                    Timber.w(ioe, "Failed to start stream %s", sendStream);
                }
            }
        }
    }

    /**
     * Stops all streaming and capturing in this <code>MediaStream</code> and closes and releases all
     * open/allocated devices/resources. Has no effect if this <code>MediaStream</code> is already
     * closed and is simply ignored.
     *
     * @see MediaStream#stop()
     */
    @Override
    public void stop()
    {
        stop(MediaDirection.SENDRECV);
        started = false;
    }

    /**
     * Stops the processing of media in this instance in a specific direction.
     *
     * @param direction a <code>MediaDirection</code> value which represents the direction of the processing of
     * media to be stopped. For example, {@link MediaDirection#SENDRECV} to stop both capture
     * and playback of media in this instance or {@link MediaDirection#SENDONLY} to only stop
     * the capture of media in this instance
     */
    private void stop(MediaDirection direction)
    {
        if (direction == null)
            throw new NullPointerException("direction");

        if (rtpManager == null)
            return;

        if ((MediaDirection.SENDRECV.equals(direction)
                || MediaDirection.SENDONLY.equals(direction))
                && (MediaDirection.SENDRECV.equals(startedDirection)
                || MediaDirection.SENDONLY.equals(startedDirection))) {
            /*
             * XXX It is not very clear at the time of this writing whether the SendStreams are to
             * be stopped or closed. On one hand, stopping a direction may be a temporary
             * transition which relies on retaining the SSRC. On the other hand, it may be
             * permanent. In which case, the respective ReceiveStream on the remote peer will
             * timeout at some point in time. In the context of video conferences, when a member
             * stops the streaming of their video without leaving the conference, they will stop
             * their SendStreams. However, the other members will need respective BYE RTCP
             * packets in order to know that they are to remove the associated ReceiveStreams
             * from display. The initial version of the code here used to stop the SendStreams
             * without closing them but, given the considerations above, it is being changed to
             * close them in the case of video.
             */
            stopSendStreams(this instanceof VideoMediaStream);

            if (deviceSession != null)
                deviceSession.stop(MediaDirection.SENDONLY);

            if (MediaDirection.SENDRECV.equals(startedDirection))
                startedDirection = MediaDirection.RECVONLY;
            else if (MediaDirection.SENDONLY.equals(startedDirection))
                startedDirection = null;
        }

        if ((MediaDirection.SENDRECV.equals(direction)
                || MediaDirection.RECVONLY.equals(direction))
                && (MediaDirection.SENDRECV.equals(startedDirection)
                || MediaDirection.RECVONLY.equals(startedDirection))) {
            stopReceiveStreams();

            if (deviceSession != null)
                deviceSession.stop(MediaDirection.RECVONLY);

            if (MediaDirection.SENDRECV.equals(startedDirection))
                startedDirection = MediaDirection.SENDONLY;
            else if (MediaDirection.RECVONLY.equals(startedDirection))
                startedDirection = null;
        }
    }

    /**
     * Stops the <code>ReceiveStream</code>s that this instance is receiving from its remote peer. By
     * design, a <code>MediaStream</code> instance is associated with a single
     * <code>ReceiveStream</code> at a time. However, the <code>ReceiveStream</code>s are created by
     * <code>RTPManager</code> and it tracks multiple <code>ReceiveStream</code>s. In practice, the
     * <code>RTPManager</code> of this <code>MediaStreamImpl</code> will have a single
     * <code>ReceiveStream</code> in its list.
     */
    private void stopReceiveStreams()
    {
        for (ReceiveStream receiveStream : getReceiveStreams()) {
            try {
                Timber.log(TimberLog.FINER, "Stopping receive stream with hashcode %s", receiveStream.hashCode());
                DataSource receiveStreamDataSource = receiveStream.getDataSource();

                /*
                 * For an unknown reason, the stream DataSource can be null at the end of the Call
                 * after re-invites have been handled.
                 */
                if (receiveStreamDataSource != null)
                    receiveStreamDataSource.stop();
            } catch (IOException ioex) {
                Timber.w(ioex, "Failed to stop receive stream %s", receiveStream);
            }
        }
    }

    /**
     * Stops the <code>SendStream</code>s that this instance is sending to its remote peer and optionally closes them.
     *
     * @param close <code>true</code> to close the <code>SendStream</code>s that this instance is sending to its
     * remote peer after stopping them; <code>false</code> to only stop them
     * @return the <code>SendStream</code>s which were stopped
     */
    private Iterable<SendStream> stopSendStreams(boolean close)
    {
        if (rtpManager == null)
            return null;

        @SuppressWarnings("unchecked")
        Iterable<SendStream> sendStreams = rtpManager.getSendStreams();
        Iterable<SendStream> stoppedSendStreams = stopSendStreams(sendStreams, close);

        if (close)
            sendStreamsAreCreated = false;
        return stoppedSendStreams;
    }

    /**
     * Stops specific <code>SendStream</code>s and optionally closes them.
     *
     * @param sendStreams the <code>SendStream</code>s to be stopped and optionally closed
     * @param close <code>true</code> to close the specified <code>SendStream</code>s after stopping them;
     * <code>false</code> to only stop them
     * @return the stopped <code>SendStream</code>s
     */
    private Iterable<SendStream> stopSendStreams(Iterable<SendStream> sendStreams, boolean close)
    {
        if (sendStreams == null)
            return null;

        for (SendStream sendStream : sendStreams) {
            try {
                Timber.log(TimberLog.FINER, "Stopping send stream with hashcode %s", sendStream.hashCode());

                sendStream.getDataSource().stop();
                sendStream.stop();

                if (close) {
                    try {
                        sendStream.close();
                    } catch (NullPointerException npe) {
                        /*
                         * Sometimes com.sun.media.rtp.RTCPTransmitter#bye() may throw
                         * NullPointerException but it does not seem to be guaranteed because it
                         * does not happen while debugging and stopping at a breakpoint on
                         * SendStream#close(). One of the cases in which it appears upon call
                         * hang-up is if we do not close the "old" SendStreams upon re-invite(s).
                         * Though we are now closing such SendStreams, ignore the exception here
                         * just in case because we already ignore IOExceptions.
                         */
                        Timber.e(npe, "Failed to close send stream %s", sendStream);
                    }
                }
            } catch (IOException ioe) {
                Timber.w(ioe, "Failed to stop send stream %s", sendStream);
            }
        }
        return sendStreams;
    }

    /**
     * Notifies this <code>ReceiveStreamListener</code> that the <code>RTPManager</code> it is registered
     * with has generated an event related to a <code>ReceiveStream</code>.
     *
     * @param ev the <code>ReceiveStreamEvent</code> which specifies the <code>ReceiveStream</code>
     * that is the cause of the event, and the very type of the event
     * @see ReceiveStreamListener#update(ReceiveStreamEvent)
     */
    @Override
    public void update(ReceiveStreamEvent ev)
    {
        ReceiveStream receiveStream = ev.getReceiveStream();
        boolean hasStream = receiveStream != null;

        if (ev instanceof NewReceiveStreamEvent) {
            // XXX we might consider not adding (or not starting) new ReceiveStreams
            // unless this MediaStream's direction allows receiving.

            Timber.d("Received ReceiveStreamEvent (new): %s = %s", receiveStreams.contains(receiveStream), ev);
            if (hasStream) {
                long receiveStreamSSRC = 0xFFFFFFFFL & receiveStream.getSSRC();

                addRemoteSourceID(receiveStreamSSRC);
                addReceiveStream(receiveStream);
            }
        }
        else if (ev instanceof TimeoutEvent) {
            Participant participant = ev.getParticipant();

            // If we recreate streams, we will already have restarted zrtpControl,
            // but when on the other end someone recreates his streams, we will receive a
            // ByeEvent (which extends TimeoutEvent) and then we must also restart our ZRTP.
            // This happens, for example, when we are already in a call and the remote peer
            // converts his side of the call into a conference call.
            // if(!zrtpRestarted)
            //		restartZrtpControl();

            List<ReceiveStream> receiveStreamsToRemove = new ArrayList<>();
            if (hasStream) {
                receiveStreamsToRemove.add(receiveStream);
                Timber.w("### Receiving stream TimeoutEvent occurred for: %s", receiveStream);
            }
            else if (participant != null) {
                Collection<ReceiveStream> receiveStreams = getReceiveStreams();
                Collection<?> rtpManagerReceiveStreams = rtpManager.getReceiveStreams();

                for (ReceiveStream receiveStreamX : receiveStreams) {
                    if (participant.equals(receiveStreamX.getParticipant())
                            && !participant.getStreams().contains(receiveStreamX)
                            && !rtpManagerReceiveStreams.contains(receiveStreamX)) {
                        receiveStreamsToRemove.add(receiveStreamX);
                    }
                }
            }

            // cmeng: can happen when remote video streaming is enabled but arriving
            // late/TimeoutEvent happen or remote video streaming is stop/terminated
            for (ReceiveStream receiveStreamX : receiveStreamsToRemove) {
                removeReceiveStream(receiveStreamX);

                // The DataSource needs to be disconnected, because otherwise
                // its RTPStream thread will stay alive. We do this here because
                // we observed that in certain situations it fails to be done earlier.
                DataSource dataSource = receiveStreamX.getDataSource();
                if (dataSource != null)
                    dataSource.disconnect();
            }
        }
        else if (ev instanceof RemotePayloadChangeEvent) {
            if (hasStream) {
                MediaDeviceSession devSess = getDeviceSession();

                if (devSess != null) {
                    TranscodingDataSource transcodingDS
                            = devSess.getTranscodingDataSource(receiveStream);

                    // we receive packets, streams are active if processor in transcoding
                    // DataSource is running, we need to recreate it by disconnect,
                    // connect and starting again the DataSource
                    try {
                        if (transcodingDS != null) {
                            transcodingDS.disconnect();
                            transcodingDS.connect();
                            transcodingDS.start();
                        }

                        // as output streams of the DataSource are recreated we
                        // need to update mixers and everything that are using them
                        devSess.playbackDataSourceChanged(receiveStream.getDataSource());
                    } catch (IOException e) {
                        Timber.e(e, "Error re-creating TranscodingDataSource's processor!");
                    }
                }
            }
        }
    }

    /**
     * Method called back in the RemoteListener to notify listener of all RTP Remote
     * Events.RemoteEvents are one of ReceiverReportEvent, SenderReportEvent or RemoteCollisionEvent
     *
     * @param ev the event
     */
    @Override
    public void update(RemoteEvent ev)
    {
        if (ev instanceof SenderReportEvent || ev instanceof ReceiverReportEvent) {
            Report report;
            boolean senderReport = false;
            if (ev instanceof SenderReportEvent) {
                numberOfReceivedSenderReports++;
                report = ((SenderReportEvent) ev).getReport();
                senderReport = true;
            }
            else {
                numberOfReceivedReceiverReports++;
                report = ((ReceiverReportEvent) ev).getReport();
            }
            Feedback feedback = null;
            long remoteJitter = -1;

            if (report.getFeedbackReports().size() > 0) {
                feedback = (Feedback) report.getFeedbackReports().get(0);
                remoteJitter = feedback.getJitter();
                getMediaStreamStats().updateRemoteJitter(remoteJitter);
            }

            // Notify encoders of the percentage of packets lost by the other side.
            // See RFC3550 Section 6.4.1 for the interpretation of 'fraction lost'
            if ((feedback != null) && (getDirection() != MediaDirection.INACTIVE)) {
                Set<PacketLossAwareEncoder> plaes = null;
                MediaDeviceSession deviceSession = getDeviceSession();
                if (deviceSession != null)
                    plaes = deviceSession.getEncoderControls(PacketLossAwareEncoder.class);

                if (plaes != null && !plaes.isEmpty()) {
                    int expectedPacketLoss = (feedback.getFractionLost() * 100) / 256;

                    for (PacketLossAwareEncoder plae : plaes) {
                        if (plae != null)
                            plae.setExpectedPacketLoss(expectedPacketLoss);
                    }
                }
            }

            /*
             * The level of logger used here is in accord with the level of
             * logger used in StatisticsEngine where sent reports are logged.
             */
            if (TimberLog.isTraceEnable) {
                // As reports are received on every 5 seconds
                // print every 4th packet, on every 20 seconds
                if ((numberOfReceivedSenderReports + numberOfReceivedReceiverReports) % 4 != 1)
                    return;

                StringBuilder buff = new StringBuilder(StatisticsEngine.RTP_STAT_PREFIX);
                MediaType mediaType = getMediaType();
                String mediaTypeStr = (mediaType == null) ? "" : mediaType.toString();

                buff.append("Received a ")
                        .append(senderReport ? "sender" : "receiver")
                        .append(" report for ")
                        .append(mediaTypeStr)
                        .append(" stream SSRC:")
                        .append(getLocalSourceID())
                        .append(" [");
                if (senderReport) {
                    buff.append("packet count:")
                            .append(((SenderReport) report).getSenderPacketCount())
                            .append(", bytes:")
                            .append(((SenderReport) report).getSenderByteCount());
                }

                if (feedback != null) {
                    buff.append(", inter-arrival jitter:")
                            .append(remoteJitter)
                            .append(", lost packets:").append(feedback.getNumLost())
                            .append(", time since previous report:")
                            .append((int) (feedback.getDLSR() / 65.536))
                            .append("ms");
                }
                buff.append(" ]");
                Timber.log(TimberLog.FINER, "%s", buff);
            }
        }
    }

    /**
     * Notifies this <code>SendStreamListener</code> that the <code>RTPManager</code> it is registered with
     * has generated an event related to a <code>SendStream</code>.
     *
     * @param ev the <code>SendStreamEvent</code> which specifies the <code>SendStream</code> that is the cause
     * of the event and the very type of the event
     * @see SendStreamListener#update(SendStreamEvent)
     */
    @Override
    public void update(SendStreamEvent ev)
    {
        if (ev instanceof NewSendStreamEvent) {
            /*
             * JMF stores the synchronization source (SSRC) identifier as a 32-bit signed integer,
             * we store it as unsigned.
             */
            long localSourceID = ev.getSendStream().getSSRC() & 0xFFFFFFFFL;

            if (getLocalSourceID() != localSourceID)
                setLocalSourceID(localSourceID);
        }
    }

    /**
     * Notifies this <code>SessionListener</code> that the <code>RTPManager</code> it is registered with
     * has generated an event which pertains to the session as a whole and does not belong to a
     * <code>ReceiveStream</code> or a <code>SendStream</code> or a remote participant necessarily.
     *
     * @param ev the <code>SessionEvent</code> which specifies the source and the very type of the event
     * @see SessionListener#update(SessionEvent)
     */
    @Override
    public void update(SessionEvent ev)
    {
    }

    /**
     * Returns the <code>StatisticsEngine</code> of this instance.
     *
     * @return the <code>StatisticsEngine</code> of this instance.
     */
    StatisticsEngine getStatisticsEngine()
    {
        return statisticsEngine;
    }

    /**
     * {@inheritDoc}
     */
    public void setExternalTransformer(TransformEngine transformEngine)
    {
        externalTransformerWrapper.setWrapped(transformEngine);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public void injectPacket(RawPacket pkt, boolean data, TransformEngine after)
            throws TransmissionFailedException
    {
        try {
            if (pkt == null || pkt.getBuffer() == null) {
                // It's a waste of time to invoke the method with a null pkt so disallow it.
                throw new NullPointerException(pkt == null ? "pkt" : "pkt.getBuffer()");
            }

            AbstractRTPConnector rtpConnector = getRTPConnector();
            if (rtpConnector == null)
                throw new IllegalStateException("rtpConnector");

            RTPConnectorOutputStream outputStream = data
                    ? rtpConnector.getDataOutputStream(false)
                    : rtpConnector.getControlOutputStream(false);

            // We utilize TransformEngineWrapper, so it is possible to have after wrapped. Unless
            // we wrap after, pkt will go through the whole TransformEngine chain (which is
            // obviously not the idea of the caller).
            if (after != null) {
                TransformEngineWrapper wrapper;

                // externalTransformerWrapper
                wrapper = externalTransformerWrapper;
                if (wrapper.contains(after)) {
                    after = wrapper;
                }
            }

            outputStream.write(pkt.getBuffer(), pkt.getOffset(), pkt.getLength(), after);
        } catch (IllegalStateException | IOException | NullPointerException e) {
            throw new TransmissionFailedException(e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @param pkt the packet from which to get the temporal layer id
     * @return the TID of the packet, -1 otherwise.
     *
     * FIXME(gp) conceptually this belongs to the {@link VideoMediaStreamImpl},
     * but I don't want to be obliged to cast to use this method.
     */
    public int getTemporalID(RawPacket pkt)
    {
        if (frameMarkingsExtensionId != -1) {
            RawPacket.HeaderExtension fmhe = pkt.getHeaderExtension((byte) frameMarkingsExtensionId);

            if (fmhe != null) {
                return FrameMarkingHeaderExtension.getTemporalID(fmhe);
            }
            // Note that we go on and try to use the payload itself. We may want
            // to change this behaviour in the future, because it will give
            // wrong results if the payload is encrypted.
        }

        REDBlock redBlock = getPrimaryREDBlock(pkt);
        if (redBlock == null || redBlock.getLength() == 0) {
            return -1;
        }

        final byte vp8PT = getDynamicRTPPayloadType(Constants.VP8);
        final byte vp9PT = getDynamicRTPPayloadType(Constants.VP9);

        if (redBlock.getPayloadType() == vp8PT) {
            return org.atalk.impl.neomedia.codec.video.vp8.DePacketizer.VP8PayloadDescriptor
                    .getTemporalLayerIndex(redBlock.getBuffer(), redBlock.getOffset(), redBlock.getLength());
        }
        else if (redBlock.getPayloadType() == vp9PT) {
            return org.atalk.impl.neomedia.codec.video.vp9.DePacketizer.VP9PayloadDescriptor
                    .getTemporalLayerIndex(redBlock.getBuffer(), redBlock.getOffset(), redBlock.getLength());
        }
        else {
            // XXX not implementing temporal layer detection should not break things.
            return -1;
        }
    }

    /**
     * Utility method that determines the spatial layer index (SID) of an RTP packet.
     *
     * @param pkt the RTP packet.
     * @return the SID of the packet, -1 otherwise.
     *
     * FIXME(gp) conceptually this belongs to the {@link VideoMediaStreamImpl},
     * but I don't want to be obliged to cast to use this method.
     */
    public int getSpatialID(RawPacket pkt)
    {
        if (frameMarkingsExtensionId != -1) {
            String encoding = getFormat(pkt.getPayloadType()).getEncoding();
            RawPacket.HeaderExtension fmhe = pkt.getHeaderExtension((byte) frameMarkingsExtensionId);
            if (fmhe != null) {
                return FrameMarkingHeaderExtension.getSpatialID(fmhe, encoding);
            }
            // Note that we go on and try to use the payload itself. We may want
            // to change this behaviour in the future, because it will give
            // wrong results if the payload is encrypted.
        }

        REDBlock redBlock = getPrimaryREDBlock(pkt);
        if (redBlock == null || redBlock.getLength() == 0) {
            return -1;
        }

        final byte vp9PT = getDynamicRTPPayloadType(Constants.VP9);
        if (redBlock.getPayloadType() == vp9PT) {
            return org.atalk.impl.neomedia.codec.video.vp9.DePacketizer.VP9PayloadDescriptor
                    .getSpatialLayerIndex(redBlock.getBuffer(), redBlock.getOffset(), redBlock.getLength());
        }
        else {
            // XXX not implementing temporal layer detection should not break things.
            return -1;
        }
    }

    /**
     * Returns a boolean that indicates whether we're able to detect the frame boundaries
     * for the codec of the packet that is specified as an argument.
     *
     * @param pkt the {@link RawPacket} that holds the RTP packet.
     * @return true if we're able to detect the frame boundaries for the codec
     * of the packet that is specified as an argument, false otherwise.
     */
    public boolean supportsFrameBoundaries(RawPacket pkt)
    {
        if (frameMarkingsExtensionId == -1) {
            REDBlock redBlock = getPrimaryREDBlock(pkt);
            if (redBlock != null && redBlock.getLength() != 0) {
                final byte vp9PT = getDynamicRTPPayloadType(Constants.VP9),
                        vp8PT = getDynamicRTPPayloadType(Constants.VP8), pt = redBlock.getPayloadType();

                return vp9PT == pt || vp8PT == pt;
            }
            else {
                return false;
            }
        }
        else {
            return pkt.getHeaderExtension((byte) frameMarkingsExtensionId) != null;
        }
    }

    /**
     * Utility method that determines whether a packet is the start of frame.
     *
     * @param pkt raw rtp packet.
     * @return true if the packet is the start of a frame, false otherwise.
     *
     * FIXME(gp) conceptually this belongs to the {@link VideoMediaStreamImpl},
     * but I don't want to be obliged to cast to use this method.
     */
    public boolean isStartOfFrame(RawPacket pkt)
    {
        if (!RTPPacketPredicate.INSTANCE.test(pkt)) {
            return false;
        }

        if (frameMarkingsExtensionId != -1) {
            RawPacket.HeaderExtension fmhe = pkt.getHeaderExtension((byte) frameMarkingsExtensionId);
            if (fmhe != null) {
                return FrameMarkingHeaderExtension.isStartOfFrame(fmhe);
            }
            // Note that we go on and try to use the payload itself. We may want
            // to change this behaviour in the future, because it will give
            // wrong results if the payload is encrypted.
        }

        REDBlock redBlock = getPrimaryREDBlock(pkt);
        if (redBlock == null || redBlock.getLength() == 0) {
            return false;
        }

        final byte vp8PT = getDynamicRTPPayloadType(Constants.VP8);
        final byte vp9PT = getDynamicRTPPayloadType(Constants.VP9);

        if (redBlock.getPayloadType() == vp8PT) {
            return org.atalk.impl.neomedia.codec.video.vp8.DePacketizer.VP8PayloadDescriptor.isStartOfFrame(
                    redBlock.getBuffer(), redBlock.getOffset());
        }
        else if (redBlock.getPayloadType() == vp9PT) {
            return org.atalk.impl.neomedia.codec.video.vp9.DePacketizer.VP9PayloadDescriptor.isStartOfFrame(
                    redBlock.getBuffer(), redBlock.getOffset(), redBlock.getLength());
        }
        else {
            return false;
        }
    }

    /**
     * Utility method that determines whether a packet is an end of frame.
     *
     * @param pkt raw rtp packet.
     * @return true if the packet is the end of a frame, false otherwise.
     *
     * FIXME(gp) conceptually this belongs to the {@link VideoMediaStreamImpl},
     * but I don't want to be obliged to cast to use this method.
     */
    public boolean isEndOfFrame(RawPacket pkt)
    {
        if (!RTPPacketPredicate.INSTANCE.test(pkt)) {
            return false;
        }

        if (frameMarkingsExtensionId != -1) {
            RawPacket.HeaderExtension fmhe = pkt.getHeaderExtension((byte) frameMarkingsExtensionId);
            if (fmhe != null) {
                return FrameMarkingHeaderExtension.isEndOfFrame(fmhe);
            }
            // Note that we go on and try to use the payload itself. We may want
            // to change this behaviour in the future, because it will give
            // wrong results if the payload is encrypted.
        }

        REDBlock redBlock = getPrimaryREDBlock(pkt);
        if (redBlock == null || redBlock.getLength() == 0) {
            return false;
        }

        final byte vp9PT = getDynamicRTPPayloadType(Constants.VP9);
        if (redBlock.getPayloadType() == vp9PT) {
            return org.atalk.impl.neomedia.codec.video.vp9.DePacketizer.VP9PayloadDescriptor.isEndOfFrame(
                    redBlock.getBuffer(), redBlock.getOffset(), redBlock.getLength());
        }
        else {
            return RawPacket.isPacketMarked(pkt);
        }
    }

    /**
     * {@inheritDoc}
     *
     * This is absolutely terrible, but we need a RawPacket and the method is
     * used from RTPTranslator, which doesn't work with RawPacket.
     */
    public boolean isKeyFrame(byte[] buf, int off, int len)
    {
        return isKeyFrame(new RawPacket(buf, off, len));
    }

    /**
     * {@inheritDoc}
     */
    public boolean isKeyFrame(RawPacket pkt)
    {
        // XXX merge with GenericAdaptiveTrackProjectionContext.isKeyframe().
        if (!RTPPacketPredicate.INSTANCE.test(pkt)) {
            return false;
        }

        if (frameMarkingsExtensionId != -1) {
            RawPacket.HeaderExtension fmhe = pkt.getHeaderExtension((byte) frameMarkingsExtensionId);
            if (fmhe != null) {
                return FrameMarkingHeaderExtension.isKeyframe(fmhe);
            }
            // Note that we go on and try to use the payload itself. We may want
            // to change this behaviour in the future, because it will give
            // wrong results if the payload is encrypted.
        }

        REDBlock redBlock = getPrimaryREDBlock(pkt);
        if (redBlock == null || redBlock.getLength() == 0) {
            return false;
        }

        final byte vp8PT = getDynamicRTPPayloadType(Constants.VP8);
        final byte vp9PT = getDynamicRTPPayloadType(Constants.VP9);
        final byte h264PT = getDynamicRTPPayloadType(Constants.H264);

        if (redBlock.getPayloadType() == vp8PT) {
            return org.atalk.impl.neomedia.codec.video.vp8.DePacketizer.isKeyFrame(
                    redBlock.getBuffer(), redBlock.getOffset(), redBlock.getLength());
        }
        else if (redBlock.getPayloadType() == vp9PT) {
            return org.atalk.impl.neomedia.codec.video.vp9.DePacketizer.isKeyFrame(
                    redBlock.getBuffer(), redBlock.getOffset(), redBlock.getLength());
        }
        else if (redBlock.getPayloadType() == h264PT) {
            return org.atalk.impl.neomedia.codec.video.h264.DePacketizer.isKeyFrame(
                    redBlock.getBuffer(), redBlock.getOffset(), redBlock.getLength());
        }
        else {
            return false;
        }
    }

    /**
     * Gets the {@link CachingTransformer} which (optionally) caches outgoing
     * packets for this {@link MediaStreamImpl}, if it exists.
     *
     * @return the {@link CachingTransformer} for this {@link MediaStreamImpl}.
     */
    public CachingTransformer getCachingTransformer()
    {
        return cachingTransformer;
    }

    /**
     * {@inheritDoc}
     */
    public RetransmissionRequester getRetransmissionRequester()
    {
        return retransmissionRequester;
    }

    /**
     * {@inheritDoc}
     * <br/>
     * Note that the chain is only initialized when a {@link StreamConnector} is set for the
     * {@link MediaStreamImpl} via {@link #setConnector(StreamConnector)} or by passing a
     * non-null connector to the constructor. Until the chain is initialized, this method will return null.
     */
    @Override
    public TransformEngineChain getTransformEngineChain()
    {
        return transformEngineChain;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public REDBlock getPrimaryREDBlock(ByteArrayBuffer baf)
    {
        return getPrimaryREDBlock(new RawPacket(baf.getBuffer(), baf.getOffset(), baf.getLength()));
    }

    /**
     * Gets the {@link REDBlock} that contains the payload of the packet passed in as a parameter.
     *
     * @param pkt the packet from which we want to get the primary RED block
     * @return the {@link REDBlock} that contains the payload of the packet
     * passed in as a parameter, or null if the buffer is invalid.
     */
    @Override
    public REDBlock getPrimaryREDBlock(RawPacket pkt)
    {
        if (pkt == null || pkt.getLength() < RawPacket.FIXED_HEADER_SIZE) {
            return null;
        }

        final byte redPT = getDynamicRTPPayloadType(Constants.RED), pktPT = pkt.getPayloadType();
        if (redPT == pktPT) {
            return REDBlockIterator.getPrimaryBlock(pkt.getBuffer(), pkt.getOffset(), pkt.getLength());
        }
        else {
            return new REDBlock(pkt.getBuffer(), pkt.getPayloadOffset(), pkt.getPayloadLength(), pktPT);
        }
    }

    /**
     * Gets the {@code RtxTransformer}, if any, used by the {@code MediaStream}.
     *
     * @return the {@code RtxTransformer} used by the {@code MediaStream} or {@code null}
     */
    public RtxTransformer getRtxTransformer()
    {
        return null;
    }

    /**
     * Creates the {@link DiscardTransformEngine} for this stream. Allows extenders to override.
     */
    protected DiscardTransformEngine createDiscardEngine()
    {
        return null;
    }

    /**
     * Gets the RTCP termination for this {@link MediaStreamImpl}.
     */
    protected TransformEngine getRTCPTermination()
    {
        return null;
    }

    /**
     * Gets the {@link PaddingTermination} for this {@link MediaStreamImpl}.
     */
    protected PaddingTermination getPaddingTermination()
    {
        return null;
    }

    /**
     * Gets the <code>RemoteBitrateEstimator</code> of this <code>VideoMediaStream</code>.
     *
     * @return the <code>RemoteBitrateEstimator</code> of this
     * <code>VideoMediaStream</code> if any; otherwise, <code>null</code>
     */
    public RemoteBitrateEstimatorWrapper getRemoteBitrateEstimator()
    {
        return null;
    }

    /**
     * Code that runs when the dynamic payload types change.
     */
    private void onDynamicPayloadTypesChanged()
    {
        RtxTransformer rtxTransformer = getRtxTransformer();
        if (rtxTransformer != null) {
            rtxTransformer.onDynamicPayloadTypesChanged();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setTransportCCEngine(TransportCCEngine engine)
    {
        if (transportCCEngine != null) {
            transportCCEngine.removeMediaStream(this);
        }

        this.transportCCEngine = engine;
        if (transportCCEngine != null) {
            transportCCEngine.addMediaStream(this);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setRTPTranslator(RTPTranslator rtpTranslator)
    {
        super.setRTPTranslator(rtpTranslator);
        if (this.deviceSession != null) {
            this.deviceSession.setUseTranslator(rtpTranslator != null);
        }
    }
}
