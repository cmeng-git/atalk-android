/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import static org.atalk.impl.neomedia.format.MediaFormatImpl.FORMAT_PARAMETER_ATTR_IMAGEATTR;
import static org.atalk.impl.neomedia.transform.dtls.DtlsControlImpl.DEFAULT_SIGNATURE_ALGORITHM;
import static org.atalk.impl.neomedia.transform.zrtp.ZrtpControlImpl.generateMyZid;

import net.java.sip.communicator.service.protocol.AccountID;
import net.java.sip.communicator.service.protocol.Call;
import net.java.sip.communicator.service.protocol.CallConference;
import net.java.sip.communicator.service.protocol.CallPeerState;
import net.java.sip.communicator.service.protocol.OperationFailedException;
import net.java.sip.communicator.service.protocol.ProtocolProviderFactory;
import net.java.sip.communicator.service.protocol.media.CallPeerMediaHandler;
import net.java.sip.communicator.service.protocol.media.SrtpControls;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.call.VideoCallActivity;
import org.atalk.android.gui.dialogs.DialogActivity;
import org.atalk.impl.neomedia.format.MediaFormatImpl;
import org.atalk.impl.neomedia.transform.dtls.DtlsControlImpl;
import org.atalk.service.libjitsi.LibJitsi;
import org.atalk.service.neomedia.DtlsControl;
import org.atalk.service.neomedia.MediaDirection;
import org.atalk.service.neomedia.MediaStream;
import org.atalk.service.neomedia.MediaStreamTarget;
import org.atalk.service.neomedia.QualityControl;
import org.atalk.service.neomedia.QualityPreset;
import org.atalk.service.neomedia.RTPExtension;
import org.atalk.service.neomedia.SDesControl;
import org.atalk.service.neomedia.SrtpControl;
import org.atalk.service.neomedia.SrtpControlType;
import org.atalk.service.neomedia.StreamConnector;
import org.atalk.service.neomedia.VideoMediaStream;
import org.atalk.service.neomedia.ZrtpControl;
import org.atalk.service.neomedia.device.MediaDevice;
import org.atalk.service.neomedia.format.MediaFormat;
import org.atalk.util.MediaType;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smackx.colibri.ColibriConferenceIQ;
import org.jivesoftware.smackx.disco.packet.DiscoverInfo;
import org.jivesoftware.smackx.jingle.element.JingleContent;
import org.jivesoftware.smackx.jingle.element.JingleContent.Senders;
import org.jivesoftware.smackx.jingle_rtp.JingleUtils;
import org.jivesoftware.smackx.jingle_rtp.element.IceUdpTransport;
import org.jivesoftware.smackx.jingle_rtp.element.InputEvent;
import org.jivesoftware.smackx.jingle_rtp.element.ParameterElement;
import org.jivesoftware.smackx.jingle_rtp.element.PayloadType;
import org.jivesoftware.smackx.jingle_rtp.element.RtcpMux;
import org.jivesoftware.smackx.jingle_rtp.element.RtpDescription;
import org.jivesoftware.smackx.jingle_rtp.element.SdpCrypto;
import org.jivesoftware.smackx.jingle_rtp.element.SdpSource;
import org.jivesoftware.smackx.jingle_rtp.element.SrtpEncryption;
import org.jivesoftware.smackx.jingle_rtp.element.SrtpFingerprint;
import org.jivesoftware.smackx.jingle_rtp.element.ZrtpHash;
import org.jxmpp.jid.Jid;

import java.awt.Component;
import java.beans.PropertyChangeEvent;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import ch.imvs.sdes4j.srtp.SrtpCryptoAttribute;
import timber.log.Timber;

/**
 * An XMPP specific extension of the generic media handler.
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Hristo Terezov
 * @author Boris Grozev
 * @author Eng Chong Meng
 * @author MilanKral
 */
public class CallPeerMediaHandlerJabberImpl extends CallPeerMediaHandler<CallPeerJabberImpl>
{
    /**
     * Determines whether a specific XMPP feature is supported by both a specific
     * <code>ScServiceDiscoveryManager</code> (may be referred to as the local peer) and a specific
     * <code>DiscoverInfo</code> (may be thought of as the remote peer).
     *
     * @param discoveryManager the <code>ScServiceDiscoveryManager</code> to be checked whether it includes
     * the specified feature
     * @param discoverInfo the <code>DiscoveryInfo</code> which is to be checked whether it contains the specified
     * feature. If <code>discoverInfo</code> is <code>null</code>, it is considered to contain the specified feature.
     * @param feature the feature to be determined whether it is supported by both the specified
     * <code>discoveryManager</code> and the specified <code>discoverInfo</code>
     * @return <code>true</code> if the specified <code>feature</code> is supported by both the specified
     * <code>discoveryManager</code> and the specified <code>discoverInfo</code>; otherwise, <code>false</code>
     */
    private static boolean isFeatureSupported(ScServiceDiscoveryManager discoveryManager,
            DiscoverInfo discoverInfo, String feature)
    {
        return discoveryManager.includesFeature(feature)
                && ((discoverInfo == null) || discoverInfo.containsFeature(feature));
    }

    /**
     * The current description of the streams that we have going toward the remote side. We use
     * {@link LinkedHashMap}s to make sure that we preserve the order of the individual content extensions.
     */
    private final Map<String, JingleContent> localContentMap = new LinkedHashMap<>();

    /**
     * The <code>QualityControl</code> of this <code>CallPeerMediaHandler</code>.
     */
    private final QualityControlWrapper qualityControls;

    /**
     * The current description of the streams that the remote side has with us. We use
     * {@link LinkedHashMap}s to make sure that we preserve the order of the individual content extensions.
     */
    private final Map<String, JingleContent> remoteContentMap = new LinkedHashMap<>();

    /**
     * Indicates whether the remote party has placed us on hold.
     */
    private boolean remotelyOnHold = false;

    /**
     * Whether other party is able to change video quality settings. Normally it's whether we have
     * detected existence of imageattr in sdp.
     *
     * @see MediaFormatImpl.FORMAT_PARAMETER_ATTR_IMAGEATTR
     */
    private boolean supportQualityControls = false;

    /**
     * The <code>TransportManager</code> implementation handling our address management.
     */
    private TransportManagerJabberImpl transportManager;

    /**
     * The <code>Object</code> which is used for synchronization (e.g. <code>wait</code> and
     * <code>notify</code>) related to {@link #transportManager}.
     */
    private final Object transportManagerSyncRoot = new Object();

    /**
     * The ordered by preference array of the XML namespaces of the jingle transports that this
     * peer supports. If it is non-null, it will be used instead of checking disco#info in order
     * to select an appropriate transport manager.
     */
    private String[] supportedTransports = null;

    /**
     * Object used to synchronize access to <code>supportedTransports</code>
     */
    private final Object supportedTransportsSyncRoot = new Object();

    /**
     * Indicates if the <code>CallPeer</code> will support </code>inputevt</code>
     * extension (i.e. will be able to be remote-controlled).
     */
    private boolean localInputEvtAware = false;

    /**
     * Creates a new handler that will be managing media streams for <code>peer</code>.
     *
     * @param peer that <code>CallPeerJabberImpl</code> instance that we will be managing media for.
     */
    public CallPeerMediaHandlerJabberImpl(CallPeerJabberImpl peer)
    {
        super(peer, peer);
        qualityControls = new QualityControlWrapper(peer);
    }

    /**
     * Determines the direction that a stream, which has been placed on hold by the remote party,
     * would need to go back to after being re-activated. If the stream is not currently on hold
     * (i.e. it is still sending media), this method simply returns its current direction.
     *
     * @param stream the {@link MediaStreamTarget} whose post-hold direction we'd like to determine.
     * @return the {@link MediaDirection} that we need to set on <code>stream</code> once it is reactivate.
     */
    private MediaDirection calculatePostHoldDirection(MediaStream stream)
    {
        MediaDirection streamDirection = stream.getDirection();
        if (streamDirection.allowsSending())
            return streamDirection;

        /*
         * When calculating a direction we need to take into account 1) what direction the remote
         * party had asked for before putting us on hold, 2) what the user preference is for the
         * stream's media type, 3) our local hold status, 4) the direction supported by the device
         * this stream is reading from.
         */

        // 1. what the remote party originally told us (from our perspective)
        JingleContent content = remoteContentMap.get(stream.getName());
        MediaDirection postHoldDir = JingleUtils.getDirection(content, !mPeer.isInitiator());

        // 2. the user preference
        MediaDevice device = stream.getDevice();
        postHoldDir = postHoldDir.and(getDirectionUserPreference(device.getMediaType()));

        // 3. our local hold status
        if (isLocallyOnHold())
            postHoldDir = postHoldDir.and(MediaDirection.SENDONLY);

        // 4. the device direction
        postHoldDir = postHoldDir.and(device.getDirection());
        return postHoldDir;
    }

    /**
     * Closes the <code>CallPeerMediaHandler</code>.
     */
    @Override
    public synchronized void close()
    {
        super.close();
//        OperationSetDesktopSharingClientJabberImpl client = (OperationSetDesktopSharingClientJabberImpl)
//                peer.getProtocolProvider().getOperationSet(OperationSetDesktopSharingClient.class);
//        if (client != null)
//            client.fireRemoteControlRevoked(peer);
    }

    /**
     * Creates a {@link JingleContent}s of the streams for a specific <code>MediaDevice</code>.
     *
     * @param dev <code>MediaDevice</code>
     * @return the {@link JingleContent}s of stream that this handler is prepared to initiate.
     * @throws OperationFailedException if we fail to create the descriptions for reasons like problems with device
     * interaction, allocating ports, etc.
     */
    private JingleContent createContent(MediaDevice dev)
            throws OperationFailedException
    {
        MediaType mediaType = dev.getMediaType();
        // this is the direction to be used in the jingle session
        MediaDirection direction = dev.getDirection();

        /*
         * In the case of RTP translation performed by the conference focus, the conference
         * focus is not required to capture media.
         */
        if (!(MediaType.VIDEO.equals(mediaType) && isRTPTranslationEnabled(mediaType)))
            direction = direction.and(getDirectionUserPreference(mediaType));

        /*
         * Check if we need to announce sending on behalf of other peers
         */
        CallJabberImpl call = mPeer.getCall();
        if (call.isConferenceFocus()) {
            for (CallPeerJabberImpl anotherPeer : call.getCallPeerList()) {
                if ((anotherPeer != mPeer) && anotherPeer.getDirection(mediaType).allowsReceiving()) {
                    direction = direction.or(MediaDirection.SENDONLY);
                    break;
                }
            }
        }

        if (isLocallyOnHold())
            direction = direction.and(MediaDirection.SENDONLY);

        QualityPreset sendQualityPreset = null;
        QualityPreset receiveQualityPreset = null;

        if (qualityControls != null) {
            // the one we will send is the one the remote has announced as receive
            sendQualityPreset = qualityControls.getRemoteReceivePreset();
            // the one we want to receive is the one the remote can send
            receiveQualityPreset = qualityControls.getRemoteSendMaxPreset();
        }

        if (direction != MediaDirection.INACTIVE) {
            JingleContent content = createContentForOffer(getLocallySupportedFormats(dev, sendQualityPreset,
                    receiveQualityPreset), direction, dev.getSupportedExtensions());
            RtpDescription description = content.getFirstChildElement(RtpDescription.class);

            // DTLS-SRTP
            setDtlsEncryptionOnContent(mediaType, content, null);
            /*
             * Neither SDES nor ZRTP is supported in telephony conferences utilizing the
             * server-side technology Jitsi Videobridge yet.
             */
            if (!mPeer.isJitsiVideobridge()) {
                // SDES - It is important to set SDES before ZRTP in order to make GTALK
                // application able to work with SDES.
                setSdesEncryptionOnDescription(mediaType, description, null);
                // ZRTP
                setZrtpEncryptionOnDescription(mediaType, description, null);
            }
            return content;
        }
        return null;
    }

    /**
     * Creates a {@link JingleContent} for a particular stream.
     *
     * @param mediaType <code>MediaType</code> of the content
     * @return a {@link JingleContent}
     * @throws OperationFailedException if we fail to create the descriptions for reasons like
     * - problems with device interaction, allocating ports, etc.
     */
    public JingleContent createContentForMedia(MediaType mediaType)
            throws OperationFailedException
    {
        MediaDevice dev = getDefaultDevice(mediaType);
        if (isDeviceActive(dev))
            return createContent(dev);
        return null;
    }

    /**
     * Generates an Jingle {@link JingleContent} for the specified {@link MediaFormat}
     * list, direction and RTP extensions taking account the local streaming preference for the
     * corresponding media type.
     *
     * @param supportedFormats the list of <code>MediaFormats</code> that we'd like to advertise.
     * @param direction the <code>MediaDirection</code> that we'd like to establish the stream in.
     * @param supportedExtensions the list of <code>RTPExtension</code>s that we'd like to advertise in the
     * <code>MediaDescription</code>.
     * @return a newly created {@link JingleContent} representing streams that we'd be able to handle.
     */
    private JingleContent createContentForOffer(List<MediaFormat> supportedFormats,
            MediaDirection direction, List<RTPExtension> supportedExtensions)
    {
        JingleContent content = JingleUtils.createDescription(
                JingleContent.Creator.initiator,
                supportedFormats.get(0).getMediaType().toString(),
                JingleUtils.getSenders(direction, !mPeer.isInitiator()),
                supportedFormats, supportedExtensions, getDynamicPayloadTypes(),
                getRtpExtensionsRegistry(), getTransportManager().isRtcpmux(), isImageattr(mPeer));

        this.localContentMap.put(content.getName(), content);
        return content;
    }

    /**
     * Creates a <code>List</code> containing the {@link JingleContent}s of the streams that
     * this handler is prepared to initiate depending on available <code>MediaDevice</code>s and local
     * on-hold and video transmission preferences.
     *
     * @return a {@link List} containing the {@link JingleContent}s of streams that this
     * handler is prepared to initiate.
     * @throws OperationFailedException if we fail to create the descriptions for reasons like problems
     * with device interaction, allocating ports, etc.
     */
    public List<JingleContent> createContentList()
            throws OperationFailedException
    {
        // Describe the media.
        List<JingleContent> mediaDescs = new ArrayList<>();
        for (MediaType mediaType : MediaType.values()) {
            MediaDevice dev = getDefaultDevice(mediaType);

            if (isDeviceActive(dev)) {
                MediaDirection direction = dev.getDirection();

                /*
                 * In the case of RTP translation performed by the conference focus, the conference
                 * focus is not required to capture media.
                 */
                if (!(MediaType.VIDEO.equals(mediaType) && isRTPTranslationEnabled(mediaType))) {
                    direction = direction.and(getDirectionUserPreference(mediaType));
                }
                if (isLocallyOnHold())
                    direction = direction.and(MediaDirection.SENDONLY);

                /*
                 * If we're only able to receive, we don't have to offer it at all. For example, we
                 * have to offer audio and no video when we start an audio call.
                 */
                if (MediaDirection.RECVONLY.equals(direction))
                    direction = MediaDirection.INACTIVE;

                if (direction != MediaDirection.INACTIVE) {
                    JingleContent content = createContentForOffer(getLocallySupportedFormats(dev),
                            direction, dev.getSupportedExtensions());
                    RtpDescription description = content.getFirstChildElement(RtpDescription.class);

                    // DTLS-SRTP
                    setDtlsEncryptionOnContent(mediaType, content, null);
                    /*
                     * Neither SDES nor ZRTP is supported in telephony conferences utilizing the
                     * server-side technology Jitsi Videobridge yet.
                     */
                    if (!mPeer.isJitsiVideobridge()) {
                        // SDES: It is important to set SDES before ZRTP in order to make GTALK
                        // application able to work with
                        setSdesEncryptionOnDescription(mediaType, description, null);
                        // ZRTP
                        setZrtpEncryptionOnDescription(mediaType, description, null);
                    }

                    // we request a desktop sharing session so add the inputevt extension in the "video" content
                    if (description.getMedia().equals(MediaType.VIDEO.toString()) && getLocalInputEvtAware()) {
                        content.addChildElement(InputEvent.getBuilder().build());
                    }
                    mediaDescs.add(content);
                }
            }
        }

        // Fail if no media content/description element (e.g. all devices are inactive).
        if (mediaDescs.isEmpty()) {
            ProtocolProviderServiceJabberImpl.throwOperationFailedException(
                    aTalkApp.getResString(R.string.service_gui_CALL_NO_ACTIVE_DEVICE),
                    OperationFailedException.GENERAL_ERROR, null);
        }

        // Add transport-info to the media contents
        return harvestCandidates(null, mediaDescs, null);
    }

    /**
     * Creates a <code>List</code> containing the {@link JingleContent}s of the streams of a
     * specific <code>MediaType</code> that this handler is prepared to initiate depending on available
     * <code>MediaDevice</code>s and local on-hold and video transmission preferences.
     *
     * @param mediaType <code>MediaType</code> of the content
     * @return a {@link List} containing the {@link JingleContent}s of streams that this
     * handler is prepared to initiate.
     * @throws OperationFailedException if we fail to create the descriptions for reasons like - problems with device
     * interaction, allocating ports, etc.
     */
    public List<JingleContent> createContentList(MediaType mediaType)
            throws OperationFailedException
    {
        MediaDevice dev = getDefaultDevice(mediaType);
        List<JingleContent> mediaDescs = new ArrayList<>();

        if (isDeviceActive(dev)) {
            JingleContent content = createContent(dev);
            if (content != null)
                mediaDescs.add(content);
        }

        // Fail if no media is described (e.g. all devices are inactive).
        if (mediaDescs.isEmpty()) {
            ProtocolProviderServiceJabberImpl.throwOperationFailedException(
                    aTalkApp.getResString(R.string.service_gui_CALL_NO_ACTIVE_DEVICE),
                    OperationFailedException.GENERAL_ERROR, null);
        }
        // Describe the transport(s).
        return harvestCandidates(null, mediaDescs, null);
    }

    /**
     * Overrides to give access to the transport manager to send events about ICE state changes.
     *
     * @param property the name of the property of this <code>PropertyChangeNotifier</code> which had its value changed
     * @param oldValue the value of the property with the specified name before the change
     * @param newValue the value of the property with the specified name after
     */
    @Override
    protected void firePropertyChange(String property, Object oldValue, Object newValue)
    {
        super.firePropertyChange(property, oldValue, newValue);
    }

    /**
     * Wraps up any ongoing candidate harvests and returns our response to the last offer we've
     * received, so that the peer could use it to send a <code>session-accept</code>.
     *
     * @return the last generated list of {@link JingleContent}s that the call peer could
     * use to send a <code>session-accept</code>.
     * @throws OperationFailedException if we fail to configure the media stream
     */
    public Iterable<JingleContent> generateSessionAccept()
            throws OperationFailedException
    {
        TransportManagerJabberImpl transportManager = getTransportManager();
        Iterable<JingleContent> sessionAccept = transportManager.wrapupCandidateHarvest();

        // user answered an incoming call, so we go through whatever content entries we are
        // initializing and init their corresponding streams

        // First parse content so we know how many streams and what type of content we have
        Map<JingleContent, RtpDescription> contents = new HashMap<>();

        for (JingleContent ourContent : sessionAccept) {
            RtpDescription description = ourContent.getFirstChildElement(RtpDescription.class);
            contents.put(ourContent, description);
        }

        boolean masterStreamSet = false;
        for (Map.Entry<JingleContent, RtpDescription> en : contents.entrySet()) {
            JingleContent ourContent = en.getKey();
            RtpDescription description = en.getValue();
            MediaType type = MediaType.parseString(description.getMedia());

            // stream connector
            StreamConnector connector = transportManager.getStreamConnector(type);

            // the device this stream would be reading from and writing to.
            MediaDevice dev = getDefaultDevice(type);
            if (!isDeviceActive(dev))
                continue;

            // stream target
            MediaStreamTarget target = transportManager.getStreamTarget(type);

            // stream direction
            MediaDirection direction = JingleUtils.getDirection(ourContent, !mPeer.isInitiator());

            // if we answer with video, tell remotePeer that video direction is sendrecv, and
            // whether video device can capture/send
            if (MediaType.VIDEO.equals(type)
                    && (isLocalVideoTransmissionEnabled() || isRTPTranslationEnabled(type))
                    && dev.getDirection().allowsSending()) {
                direction = MediaDirection.SENDRECV;
                ourContent.setSenders(Senders.both);
            }

            // let's now see what was the format we announced as first and configure the stream with it.
            String contentName = ourContent.getName();
            JingleContent theirContent = this.remoteContentMap.get(contentName);
            RtpDescription theirDescription = theirContent.getFirstChildElement(RtpDescription.class);

            MediaFormat format = null;
            List<MediaFormat> localFormats = getLocallySupportedFormats(dev);
            for (PayloadType payload : theirDescription.getChildElements(PayloadType.class)) {
                MediaFormat remoteFormat = JingleUtils.payloadTypeToMediaFormat(payload, getDynamicPayloadTypes());
                if ((remoteFormat != null) && (format = findMediaFormat(localFormats, remoteFormat)) != null) {
                    break;
                }
            }
            if (format == null) {
                ProtocolProviderServiceJabberImpl.throwOperationFailedException("No matching codec.",
                        OperationFailedException.ILLEGAL_ARGUMENT, null);
            }

            // extract the extensions that we are advertising: check whether we will be exchanging any RTP extensions.
            List<RTPExtension> rtpExtensions = JingleUtils.extractRTPExtensions(description, this.getRtpExtensionsRegistry());
            supportQualityControls = format.hasParameter(FORMAT_PARAMETER_ATTR_IMAGEATTR);

            boolean masterStream = false;
            // if we have more than one stream, lets the audio be the master
            if (!masterStreamSet) {
                if (contents.size() > 1) {
                    if (type.equals(MediaType.AUDIO)) {
                        masterStream = true;
                        masterStreamSet = true;
                    }
                }
                else {
                    masterStream = true;
                    masterStreamSet = true;
                }
            }
            // create the corresponding stream...
            MediaStream stream
                    = initStream(contentName, connector, dev, format, target, direction, rtpExtensions, masterStream);

            long ourSsrc = stream.getLocalSourceID();
            if (direction.allowsSending() && ourSsrc != -1) {
                description.setSsrc(Long.toString(ourSsrc));
                addSourceExtension(description, ourSsrc);
            }
        }
        return sessionAccept;
    }

    /**
     * Adds a <code>SdpSourceGroup</code> as a child element of <code>description</code>. See XEP-0339.
     *
     * @param description the <code>RtpDescriptionExtensionElement</code> to which a child element will be added.
     * @param ssrc the SSRC for the <code>SdpSourceGroup</code> to use.
     */
    private void addSourceExtension(RtpDescription description, long ssrc)
    {
        MediaType type = MediaType.parseString(description.getMedia());
        SdpSource.Builder srcBuilder = SdpSource.getBuilder()
                .setSsrc(ssrc)
                .addParameter(ParameterElement.builder(SdpSource.NAMESPACE)
                        .setNameValue("cname", LibJitsi.getMediaService().getRtpCname())
                        .build())
                .addParameter(ParameterElement.builder(SdpSource.NAMESPACE)
                        .setNameValue("msid", getMsid(type))
                        .build())
                .addParameter(ParameterElement.builder(SdpSource.NAMESPACE)
                        .setNameValue("mslabel", getMsLabel())
                        .build())
                .addParameter(ParameterElement.builder(SdpSource.NAMESPACE)
                        .setNameValue("label", getLabel(type))
                        .build());

        description.addChildElement(srcBuilder.build());
    }

    /**
     * Returns the local content of a specific content type (like audio or video).
     *
     * @param contentType content type name
     * @return remote <code>JingleContent</code> or null if not found
     */
    public JingleContent getLocalContent(String contentType)
    {
        for (String key : localContentMap.keySet()) {
            JingleContent content = localContentMap.get(key);
            if (content != null) {
                RtpDescription description = content.getFirstChildElement(RtpDescription.class);
                if (description.getMedia().equals(contentType))
                    return content;
            }
        }
        return null;
    }

    /**
     * Returns a complete list of call currently known local content-s.
     *
     * @return a list of {@link JingleContent} <code>null</code> if not found
     */
    public Iterable<JingleContent> getLocalContentList()
    {
        return localContentMap.values();
    }

    /**
     * Returns the quality control for video calls if any.
     *
     * @return the implemented quality control.
     */
    public QualityControl getQualityControl()
    {
        if (supportQualityControls) {
            return qualityControls;
        }
        else {
            // we have detected that its not supported and return null and control ui won't be visible
            return null;
        }
    }

    /**
     * Get the remote content of a specific content type (like audio or video).
     *
     * @param contentType content type name
     * @return remote <code>JingleContent</code> or null if not found
     */
    public JingleContent getRemoteContent(String contentType)
    {
        for (String key : remoteContentMap.keySet()) {
            JingleContent content = remoteContentMap.get(key);
            RtpDescription description = content.getFirstChildElement(RtpDescription.class);

            if (description.getMedia().equals(contentType))
                return content;
        }
        return null;
    }

    /**
     * {@inheritDoc}
     *
     * In the case of a telephony conference organized by the local peer/user via the Jitsi
     * Videobridge server-side technology, returns an SSRC reported by the server as received on
     * the channel allocated by the local peer/user for the purposes of communicating with the
     * <code>CallPeer</code> associated with this instance.
     */
    @Override
    public long getRemoteSSRC(MediaType mediaType)
    {
        int[] ssrcs = getRemoteSSRCs(mediaType);

        /*
         * A peer (regardless of whether it is local or remote) may send multiple RTP streams at
         * any time. In such a case, it is not clear which one of their SSRCs is to be returned.
         * Anyway, the super says that the returned is the last known. We will presume that the
         * last known in the list reported by the Jitsi Videobridge server is the last.
         */
        if (ssrcs.length != 0)
            return 0xFFFFFFFFL & ssrcs[ssrcs.length - 1];

        /*
         * XXX In the case of Jitsi Videobridge, the super implementation of
         * getRemoteSSRC(MediaType) cannot be trusted because there is a single VideoMediaStream
         * with multiple ReceiveStreams.
         */
        return mPeer.isJitsiVideobridge() ? SSRC_UNKNOWN : super.getRemoteSSRC(mediaType);
    }

    /**
     * Gets the SSRCs of RTP streams with a specific <code>MediaType</code> known to be received by a
     * <code>MediaStream</code> associated with this instance.
     *
     * <b>Warning</b>: The method may return only one of the many possible remote SSRCs in the case
     * of no utilization of the Jitsi Videobridge server-side technology because the super
     * implementation does not currently provide support for keeping track of multiple remote SSRCs.
     *
     * @param mediaType the <code>MediaType</code> of the RTP streams the SSRCs of which are to be returned
     * @return an array of <code>int</code> values which represent the SSRCs of RTP streams with the
     * specified <code>mediaType</code> known to be received by a <code>MediaStream</code> associated with this instance
     */
    private int[] getRemoteSSRCs(MediaType mediaType)
    {
        /*
         * If the Jitsi Videobridge server-side technology is utilized, a single MediaStream (per
         * MediaType) is shared among the participating CallPeers and, consequently, the remote
         * SSRCs cannot be associated with the CallPeers from which they are actually being sent.
         * That's why the server will report them to the conference focus.
         */
        ColibriConferenceIQ.Channel channel = getColibriChannel(mediaType);
        if (channel != null)
            return channel.getSSRCs();

        /*
         * XXX The fallback to the super implementation that follows may lead to unexpected
         * behavior due to the lack of ability to keep track of multiple remote SSRCs.
         */
        long ssrc = super.getRemoteSSRC(mediaType);
        return (ssrc == SSRC_UNKNOWN) ? ColibriConferenceIQ.NO_SSRCS : new int[]{(int) ssrc};
    }

    /**
     * Get the <code>TransportManager</code> implementation handling our address management.
     *
     * TODO: this method can and should be simplified.
     *
     * @return the <code>TransportManager</code> implementation handling our address management
     * @see CallPeerMediaHandler#getTransportManager()
     */
    @Override
    protected synchronized TransportManagerJabberImpl getTransportManager()
    {
        if (transportManager == null) {
            if (mPeer.isInitiator()) {
                synchronized (transportManagerSyncRoot) {
                    try {
                        transportManagerSyncRoot.wait(5000);
                    } catch (InterruptedException e) {
                        Timber.e("transportManagerSyncRoot Exception: %s", e.getMessage());
                    }
                }
                if (transportManager == null) {
                    throw new IllegalStateException("The initiator is expected to specify the transport in their offer.");
                }
                else
                    return transportManager;
            }
            else {
                ProtocolProviderServiceJabberImpl protocolProvider = mPeer.getProtocolProvider();
                ScServiceDiscoveryManager discoveryManager = protocolProvider.getDiscoveryManager();
                DiscoverInfo peerDiscoverInfo = mPeer.getDiscoveryInfo();

                /*
                 * If this.supportedTransports has been explicitly set, we use it to select the
                 * transport manager -- we use the first transport in the list which we recognize
                 * (e.g. the first that is either ice or raw-udp
                 */
                synchronized (supportedTransportsSyncRoot) {
                    if (supportedTransports != null && supportedTransports.length > 0) {
                        for (String supportedTransport : supportedTransports) {
                            if (ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE_ICE_UDP_1.equals(supportedTransport)) {
                                transportManager = new IceUdpTransportManager(mPeer);
                                break;
                            }
                            else if (ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE_RAW_UDP_0.equals(supportedTransport)) {
                                transportManager = new RawUdpTransportManager(mPeer);
                                break;
                            }
                        }
                        if (transportManager == null) {
                            Timber.w("Could not find a supported TransportManager in supportedTransports. Will try to select one based on disco#info.");
                        }
                    }
                }

                if (transportManager == null) {
                    /*
                     * The list of possible transports ordered by decreasing preference.
                     */
                    String[] transports = new String[]{
                            ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE_ICE_UDP_1,
                            ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE_RAW_UDP_0};

                    /*
                     * If Jitsi Videobridge is to be employed, pick up a Jingle transport supported by it.
                     */
                    if (mPeer.isJitsiVideobridge()) {
                        CallJabberImpl call = mPeer.getCall();

                        if (call != null) {
                            Jid jitsiVideobridge = mPeer.getCall().getJitsiVideobridge();

                            /*
                             * Jitsi Videobridge supports the Jingle Raw UDP transport from its
                             * inception. But that is not the case with the Jingle ICE-UDP transport.
                             */
                            if ((jitsiVideobridge != null)
                                    && !protocolProvider.isFeatureSupported(jitsiVideobridge,
                                    ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE_ICE_UDP_1)) {
                                for (int i = transports.length - 1; i >= 0; i--) {
                                    if (ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE_ICE_UDP_1.equals(transports[i])) {
                                        transports[i] = null;
                                    }
                                }
                            }
                        }
                    }

                    /*
                     * Select the first transport from the list of possible transports ordered by
                     * decreasing preference which is supported by the local and the remote peers.
                     */
                    for (String transport : transports) {
                        if (transport == null)
                            continue;
                        if (isFeatureSupported(discoveryManager, peerDiscoverInfo, transport)) {
                            if (ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE_ICE_UDP_1.equals(transport)) {
                                transportManager = new IceUdpTransportManager(mPeer);
                            }
                            else if (ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE_RAW_UDP_0.equals(transport)) {
                                transportManager = new RawUdpTransportManager(mPeer);
                            }
                            if (transportManager != null)
                                break;
                        }
                    }
                    if (transportManager == null) {
                        aTalkApp.showToastMessage("No known Jingle transport supported by Jabber call peer " + mPeer);
                    }
                }
            }
        }
        return transportManager;
    }

    /**
     * {@inheritDoc}
     *
     * @see CallPeerMediaHandler#queryTransportManager()
     */
    @Override
    protected synchronized TransportManagerJabberImpl queryTransportManager()
    {
        return transportManager;
    }

    /**
     * {@inheritDoc}
     *
     * In the case of utilization of the Jitsi Videobridge server-side technology, returns the
     * visual <code>Component</code> s which display RTP video streams reported by the server to be
     * sent by the remote peer represented by this instance.
     */
    @Override
    public List<Component> getVisualComponents()
    {
        /*
         * TODO The super is currently unable to provide the complete set of remote SSRCs (i.e. in
         * the case of no utilization of the Jitsi Videobridge server-side technology) so we
         * have to explicitly check for Jitsi Videobridge instead of just relying on the
         * implementation of the getRemoteSSRCs(MediaType) method to abstract away that detail.
         */
        if (mPeer.isJitsiVideobridge()) {
            MediaStream stream = getStream(MediaType.VIDEO);

            if (stream == null)
                return Collections.emptyList();
            else {
                int[] remoteSSRCs = getRemoteSSRCs(MediaType.VIDEO);

                if (remoteSSRCs.length == 0)
                    return Collections.emptyList();
                else {
                    VideoMediaStream videoStream = (VideoMediaStream) stream;
                    List<Component> visualComponents = new LinkedList<>();

                    for (int remoteSSRC : remoteSSRCs) {
                        Component visualComponent = videoStream.getVisualComponent(0xFFFFFFFFL & remoteSSRC);
                        if (visualComponent != null)
                            visualComponents.add(visualComponent);
                    }
                    return visualComponents;
                }
            }
        }
        return super.getVisualComponents();
    }

    /**
     * Gathers local candidate addresses.
     *
     * @param remote the media descriptions received from the remote peer if any or <code>null</code> if
     * <code>local</code> represents an offer from the local peer to be sent to the remote peer
     * @param local the media descriptions sent or to be sent from the local peer to the remote peer. If
     * <code>remote</code> is <code>null</code>, <code>local</code> represents an offer from the local
     * peer to be sent to the remote peer
     * @param transportInfoSender the <code>TransportInfoSender</code> to be used by this
     * <code>TransportManagerJabberImpl</code> to send <code>transport-info</code> <code>Jingle</code>s
     * from the local peer to the remote peer if this <code>TransportManagerJabberImpl</code>
     * wishes to utilize <code>transport-info</code>
     * @return the media descriptions of the local peer after the local candidate addresses have
     * been gathered as returned by
     * {@link TransportManagerJabberImpl#wrapupCandidateHarvest()}
     * @throws OperationFailedException if anything goes wrong while starting or wrapping up the gathering
     * of local candidate addresses
     */
    private List<JingleContent> harvestCandidates(List<JingleContent> remote,
            List<JingleContent> local, TransportInfoSender transportInfoSender)
            throws OperationFailedException
    {
        long startCandidateHarvestTime = System.currentTimeMillis();
        TransportManagerJabberImpl transportManager = getTransportManager();
        // Do not proceed if transport is null => NPE
        if (transportManager == null)
            return Collections.emptyList();

        /*
         * aTalk Session-initiate will include the transport-info's in contents,
         * So it doesn't make sense to send them by transportInfoSender.
         */
        if ((remote == null) && (transportInfoSender != null)) {
            throw new IllegalArgumentException("transportInfoSender not required in session-initiate offer");
        }

        // Setup TransportManger for (rtcp-mux) per callPeer capability support
        transportManager.setRtcpmux(isRtpcMux(mPeer));
        transportManager.startCandidateHarvest(remote, local, transportInfoSender);

        long stopCandidateHarvestTime = System.currentTimeMillis();
        long candidateHarvestTime = stopCandidateHarvestTime - startCandidateHarvestTime;
        Timber.i("End candidate harvest within %s ms", candidateHarvestTime);

        setDtlsEncryptionOnTransports(remote, local);
        if (transportManager.startConnectivityEstablishmentWithJitsiVideobridge) {
            Map<String, IceUdpTransport> mediaTransport = new LinkedHashMap<>();

            for (MediaType mediaType : MediaType.values()) {
                ColibriConferenceIQ.Channel channel = transportManager.getColibriChannel(mediaType, true /* local */);

                if (channel != null) {
                    IceUdpTransport transport = channel.getTransport();
                    if (transport != null)
                        mediaTransport.put(mediaType.toString(), transport);
                }
            }
            if (!mediaTransport.isEmpty()) {
                transportManager.startConnectivityEstablishmentWithJitsiVideobridge = false;
                transportManager.startConnectivityEstablishment(mediaTransport);
            }
        }

        /*
         * TODO Ideally, we wouldn't wrap up that quickly. We need to revisit this.
         */
        return transportManager.wrapupCandidateHarvest();
    }

    /**
     * Creates if necessary, and configures the stream that this <code>MediaHandler</code> is using for
     * the <code>MediaType</code> matching the one of the <code>MediaDevice</code>. This method extends the
     * one already available by adding a stream name, corresponding to a stream's content name.
     *
     * @param streamName the name of the stream as indicated in the XMPP <code>content</code> element.
     * @param connector the <code>MediaConnector</code> that we'd like to bind the newly created stream to.
     * @param device the <code>MediaDevice</code> that we'd like to attach the newly created <code>MediaStream</code> to.
     * @param format the <code>MediaFormat</code> that we'd like the new <code>MediaStream</code> to be set to transmit in.
     * @param target the <code>MediaStreamTarget</code> containing the RTP and RTCP address:port couples that
     * the new stream would be sending packets to.
     * @param direction the <code>MediaDirection</code> that we'd like the new stream to use (i.e. sendonly,
     * sendrecv, recvonly, or inactive).
     * @param rtpExtensions the list of <code>RTPExtension</code>s that should be enabled for this stream.
     * @param masterStream whether the stream to be used as master if secured
     * @return the newly created <code>MediaStream</code>.
     * @throws OperationFailedException if creating the stream fails for any reason (like for example accessing
     * the device or setting the format).
     */
    protected MediaStream initStream(String streamName, StreamConnector connector,
            MediaDevice device, MediaFormat format, MediaStreamTarget target,
            MediaDirection direction, List<RTPExtension> rtpExtensions, boolean masterStream)
            throws OperationFailedException
    {
        MediaStream stream = super.initStream(connector, device, format, target, direction, rtpExtensions, masterStream);
        if (stream != null)
            stream.setName(streamName);
        return stream;
    }

    /**
     * {@inheritDoc}
     *
     * In the case of a telephony conference organized by the local peer/user and utilizing the
     * Jitsi Videobridge server-side technology, a single <code>MediaHandler</code> is shared by
     * multiple <code>CallPeerMediaHandler</code>s in order to have a single <code>AudioMediaStream</code>
     * and a single <code>VideoMediaStream</code>. However, <code>CallPeerMediaHandlerJabberImpl</code> has
     * redefined the reading/getting the remote audio and video SSRCs. Consequently,
     * <code>CallPeerMediaHandlerJabberImpl</code> has to COMPLETELY redefine the writing/setting as
     * well i.e. it has to stop related <code>PropertyChangeEvent</code>s fired by the super.
     */
    @Override
    protected void mediaHandlerPropertyChange(PropertyChangeEvent ev)
    {
        String propertyName = ev.getPropertyName();

        if ((AUDIO_REMOTE_SSRC.equals(propertyName) || VIDEO_REMOTE_SSRC.equals(propertyName))
                && mPeer.isJitsiVideobridge())
            return;

        super.mediaHandlerPropertyChange(ev);
    }

    /**
     * Handles the specified <code>answer</code> by creating and initializing the corresponding <code>MediaStream</code>s.
     *
     * @param contentList the Jingle answer
     * @throws OperationFailedException if we fail to handle <code>answer</code> for reasons like failing
     * to initialize media devices or streams.
     * @throws IllegalArgumentException if there's a problem with the syntax or the semantics of <code>answer</code>.
     * Method is synchronized in order to avoid closing mediaHandler when we are currently in process of initializing,
     * configuring and starting streams and anybody interested in this operation can synchronize to the mediaHandler
     * instance to wait processing to stop (method setState in CallPeer).
     */
    public void processSessionAcceptContent(List<JingleContent> contentList)
            throws OperationFailedException, IllegalArgumentException
    {
        /*
         * The answer given in session-accept may contain transport-related information compatible
         * with that carried in transport-info.
         */
        processTransportInfo(contentList);

        boolean masterStreamSet = false;
        for (JingleContent content : contentList) {
            remoteContentMap.put(content.getName(), content);

            boolean masterStream = false;
            // if we have more than one stream, let the audio be the master
            if (!masterStreamSet) {
                if (contentList.size() > 1) {
                    RtpDescription description = content.getFirstChildElement(RtpDescription.class);
                    if (MediaType.AUDIO.toString().equals(description.getMedia())) {
                        masterStream = true;
                        masterStreamSet = true;
                    }
                }
                else {
                    masterStream = true;
                    masterStreamSet = true;
                }
            }
            processContent(content, false, masterStream);
        }
    }

    /**
     * Notifies this instance that a specific <code>ColibriConferenceIQ</code> has been received. This
     * <code>CallPeerMediaHandler</code> uses the part of the information provided in the specified
     * <code>conferenceIQ</code> which concerns it only.
     *
     * @param conferenceIQ the <code>ColibriConferenceIQ</code> which has been received
     */
    void processColibriConferenceIQ(ColibriConferenceIQ conferenceIQ)
    {
        /*
         * This CallPeerMediaHandler stores the media information but it does not store the colibri
         * Channels (which contain both media and transport information). The TransportManager
         * associated with this instance stores the colibri Channels but does not store media
         * information (such as the remote SSRCs). An design/implementation choice has to be made
         * though and the present one is to have this CallPeerMediaHandler transparently (with
         * respect to the TransportManager) store the media information inside the
         * TransportManager.
         */
        TransportManagerJabberImpl transportManager = this.transportManager;

        if (transportManager != null) {
            long oldAudioRemoteSSRC = getRemoteSSRC(MediaType.AUDIO);
            long oldVideoRemoteSSRC = getRemoteSSRC(MediaType.VIDEO);

            for (MediaType mediaType : MediaType.values()) {
                ColibriConferenceIQ.Channel dst = transportManager.getColibriChannel(mediaType, false /* remote */);

                if (dst != null) {
                    ColibriConferenceIQ.Content content = conferenceIQ.getContent(mediaType.toString());

                    if (content != null) {
                        ColibriConferenceIQ.Channel src = content.getChannel(dst.getID());
                        if (src != null) {
                            int[] ssrcs = src.getSSRCs();
                            int[] dstSSRCs = dst.getSSRCs();

                            if (!Arrays.equals(dstSSRCs, ssrcs))
                                dst.setSSRCs(ssrcs);
                        }
                    }
                }
            }

            /*
             * Do fire new PropertyChangeEvents for the properties AUDIO_REMOTE_SSRC and
             * VIDEO_REMOTE_SSRC if necessary.
             */
            long newAudioRemoteSSRC = getRemoteSSRC(MediaType.AUDIO);
            long newVideoRemoteSSRC = getRemoteSSRC(MediaType.VIDEO);

            if (oldAudioRemoteSSRC != newAudioRemoteSSRC) {
                firePropertyChange(AUDIO_REMOTE_SSRC, oldAudioRemoteSSRC, newAudioRemoteSSRC);
            }
            if (oldVideoRemoteSSRC != newVideoRemoteSSRC) {
                firePropertyChange(VIDEO_REMOTE_SSRC, oldVideoRemoteSSRC, newVideoRemoteSSRC);
            }
        }
    }

    /**
     * Process a <code>JingleContent</code> and initialize its corresponding <code>MediaStream</code>.
     * Each Jingle session-accept can contain both audio and video; and will be process individually
     *
     * @param content a <code>JingleContent</code>
     * @param modify if it corresponds to a content-modify for resolution change
     * @param masterStream whether the stream to be used as master
     * @throws OperationFailedException if we fail to handle <code>content</code> for reasons like failing to
     * initialize media devices or streams.
     * @throws IllegalArgumentException if there's a problem with the syntax or the semantics of <code>content</code>.
     * The method is synchronized in order to avoid closing mediaHandler when we are currently in
     * process of initializing, configuring and starting streams and anybody interested in
     * this operation can synchronize to the mediaHandler instance to wait processing to
     * stop (method setState in CallPeer).
     */
    private void processContent(JingleContent content, boolean modify, boolean masterStream)
            throws OperationFailedException, IllegalArgumentException
    {
        RtpDescription description = content.getFirstChildElement(RtpDescription.class);
        MediaType mediaType = JingleUtils.getMediaType(content);

        // if sender has paused the video temporary, then set backToChat flag to avoid checkReplay failure on resume
        Senders sender = content.getSenders();
        if (Senders.responder == sender) {
            VideoCallActivity.setBackToChat(true);
        }

        // stream targeted transport-info rtp/rtcp
        TransportManagerJabberImpl transportManager = getTransportManager();
        MediaStreamTarget target = transportManager.getStreamTarget(mediaType);

        /*
         * If transport and session-accept/content-accept are received one after the other, then must wait for transport
         * processing to be completed before attempt again. Otherwise, getStream(MediaType) will always return nul
         */
        if (target == null) {
            Timber.e("### Waiting transport processing to complete, bind mediaStream is null for: %s", mediaType);
            transportManager.wrapupConnectivityEstablishment();
            target = transportManager.getStreamTarget(mediaType);
        }

        // cmeng - get transport candidate from session-accept may produce null as <transport/> child element can
        // be null if candidates are sent separately. No reliable, fixed with above
        // if (target == null)
        //    target = JingleUtils.extractDefaultTarget(content);
        Timber.d("### Process media content for: sender = %s: %s => %s", sender, mediaType, target);

        // aborted if associated target address is not available: Process transport-info completed
        // ~120ms lead time on Note-10 (aTalk-initiator) send from Note-3 (conversations-responder)
        if ((target == null) || (target.getDataAddress() == null)) {
            closeStream(mediaType);
            return;
        }

        // Check to ensure we have the appropriate device to handle the received mediaType
        MediaDevice dev = getDefaultDevice(mediaType);
        if (!isDeviceActive(dev)) {
            closeStream(mediaType);
            return;
        }

        // Take the preference of the user with respect to streaming mediaType into account.
        MediaDirection devDirection = (dev == null) ? MediaDirection.INACTIVE : dev.getDirection();
        devDirection = devDirection.and(getDirectionUserPreference(mediaType));

        List<MediaFormat> supportedFormats = JingleUtils.extractFormats(description, getDynamicPayloadTypes());
        if (supportedFormats.isEmpty()) {
            // remote party must have messed up our Jingle description. throw an exception.
            ProtocolProviderServiceJabberImpl.throwOperationFailedException(
                    "Remote party sent an invalid Jingle Content stanza.",
                    OperationFailedException.ILLEGAL_ARGUMENT, null);
        }

        /*
         * Neither SDES nor ZRTP is supported in telephony conferences utilizing the server-side
         * technology Jitsi Videobridge yet.
         */
        CallJabberImpl call = mPeer.getCall();
        CallConference conference = (call == null) ? null : call.getConference();

        if ((conference == null) || !conference.isJitsiVideobridge()) {
            addZrtpAdvertisedEncryption(true, description, mediaType);
            addSDesAdvertisedEncryption(true, description, mediaType);
        }
        addDtlsSrtpAdvertisedEncryption(true, content, mediaType, false);

        /*
         * Determine the direction that we need to announce.
         * If we are the focus of a conference, we need to take into account the other participants.
         */
        MediaDirection remoteDirection = JingleUtils.getDirection(content, mPeer.isInitiator());
        if ((conference != null) && conference.isConferenceFocus()) {
            for (CallPeerJabberImpl peer : call.getCallPeerList()) {
                Senders senders = peer.getSenders(mediaType);
                boolean initiator = peer.isInitiator();
                // check if the direction of the jingle session we have with this peer allows us
                // receiving media. If senders is null, assume the default of 'both'
                if ((senders == null) || (Senders.both == senders)
                        || (initiator && Senders.initiator == senders)
                        || (!initiator && Senders.responder == senders)) {
                    remoteDirection = remoteDirection.or(MediaDirection.SENDONLY);
                }
            }
        }
        MediaDirection direction = devDirection.getDirectionForAnswer(remoteDirection);

        // update the RTP extensions that we will be exchanging.
        List<RTPExtension> remoteRTPExtensions = JingleUtils.extractRTPExtensions(description, getRtpExtensionsRegistry());
        List<RTPExtension> supportedExtensions = getExtensionsForType(mediaType);
        List<RTPExtension> rtpExtensions = intersectRTPExtensions(remoteRTPExtensions, supportedExtensions);

        // Media format offer priority is send according in the sequence; use sender first preferred choice
        MediaFormat offerFormat = supportedFormats.get(0);
        supportQualityControls = offerFormat.hasParameter(FORMAT_PARAMETER_ATTR_IMAGEATTR);

        // check for video options from remote party and set them locally
        if (mediaType.equals(MediaType.VIDEO) && modify) {
            MediaStream stream = getStream(MediaType.VIDEO);

            if (stream != null && dev != null) {
                MediaFormat fmt = supportedFormats.get(0);
                ((VideoMediaStream) stream).updateQualityControl(fmt.getAdvancedAttributes());
            }

            if (qualityControls != null) {
                QualityPreset receiveQualityPreset = qualityControls.getRemoteReceivePreset();
                QualityPreset sendQualityPreset = qualityControls.getRemoteSendMaxPreset();
                supportedFormats = (dev == null) ? null : intersectFormats(supportedFormats,
                        getLocallySupportedFormats(dev, sendQualityPreset, receiveQualityPreset));
            }
        }

        // create the corresponding stream... with the first preferred format matching our capabilities.
        if (supportedFormats != null && !supportedFormats.isEmpty()) {
            StreamConnector connector = transportManager.getStreamConnector(mediaType);
            initStream(content.getName(), connector, dev, supportedFormats.get(0), target, direction,
                    rtpExtensions, masterStream);
        }
        else {
            // remote party must have messed up our Jingle description. throw an exception.
            ProtocolProviderServiceJabberImpl.throwOperationFailedException(
                    "No matching media format supported.", OperationFailedException.ILLEGAL_ARGUMENT, null);
        }
    }

    /**
     * Parses and handles the specified <code>offer</code> and returns a content extension representing
     * the current state of this media handler. This method MUST only be called when <code>offer</code>
     * is the first session description that this <code>MediaHandler</code> is seeing.
     *
     * @param offer the offer that we'd like to parse, handle and get an answer for.
     * @throws OperationFailedException if we have a problem satisfying the description received in
     * <code>offer</code> (e.g. failed to open a device or initialize a stream ...).
     * @throws IllegalArgumentException if there's a problem with <code>offer</code>'s format or semantics.
     */
    public void processOffer(final List<JingleContent> offer)
            throws OperationFailedException, IllegalArgumentException
    {
        // prepare to generate answers to all the incoming descriptions
        final List<JingleContent> answer = new ArrayList<>(offer.size());
        boolean atLeastOneValidDescription = false;
        List<MediaFormat> remoteFormats = Collections.EMPTY_LIST;

        for (JingleContent content : offer) {
            remoteContentMap.put(content.getName(), content);

            RtpDescription description = content.getFirstChildElement(RtpDescription.class);
            MediaType mediaType = JingleUtils.getMediaType(content);

            remoteFormats = JingleUtils.extractFormats(description, getDynamicPayloadTypes());
            MediaDevice dev = getDefaultDevice(mediaType);
            MediaDirection devDirection = (dev == null) ? MediaDirection.INACTIVE : dev.getDirection();

            // Take the preference of the user with respect to streaming mediaType into account.
            devDirection = devDirection.and(getDirectionUserPreference(mediaType));

            // determine the direction that we need to announce.
            MediaDirection remoteDirection = JingleUtils.getDirection(content, mPeer.isInitiator());
            MediaDirection direction = devDirection.getDirectionForAnswer(remoteDirection);

            // intersect the MediaFormats of our device with remote ones
            List<MediaFormat> mutuallySupportedFormats = intersectFormats(remoteFormats, getLocallySupportedFormats(dev));

            // check whether we will be exchanging any RTP extensions.
            List<RTPExtension> offeredRTPExtensions = JingleUtils.extractRTPExtensions(description,
                    this.getRtpExtensionsRegistry());
            List<RTPExtension> supportedExtensions = getExtensionsForType(mediaType);
            List<RTPExtension> rtpExtensions = intersectRTPExtensions(offeredRTPExtensions, supportedExtensions);

            /*
             * Transport: RawUdpTransport extends IceUdpTransport so getting IceUdpTransport should suffice.
             */
            IceUdpTransport transport = content.getFirstChildElement(IceUdpTransport.class);

            // stream target
            MediaStreamTarget target = null;

            try {
                target = JingleUtils.extractDefaultTarget(content);
            } catch (IllegalArgumentException e) {
                Timber.w(e, "Fail to extract default target");
            }

            // according to XEP-176, transport element in session-initiate "MAY instead be empty
            // (with each candidate to be sent as the payload of a transport-info message)".
            int targetDataPort = (target == null && transport != null)
                    ? -1 : (target != null) ? target.getDataAddress().getPort() : 0;

            /*
             * TODO If the offered transport is not supported, attempt to fall back to a supported
             * one using transport-replace.
             */
            setTransportManager(transport.getNamespace());

            // RtcpMux per XEP-0167: Jingle RTP Sessions 1.2.0 (2020-04-22) and patch for jitsi
            boolean rtcpmux = false;
            if (!description.getChildElements(RtcpMux.class).isEmpty()
                    || !transport.getChildElements(RtcpMux.class).isEmpty()) {
                rtcpmux = true;
            }
            // getTransportManager().setRtcpmux(rtcpmux);
            rtcpMuxes.put(mPeer.getPeerJid(), rtcpmux);

            if (mutuallySupportedFormats.isEmpty() || (devDirection == MediaDirection.INACTIVE)
                    || (targetDataPort == 0)) {
                // skip stream and continue. contrary to sip we don't seem to need to send
                // per-stream disabling answer and only one at the end.

                // close the stream in case it already exists
                closeStream(mediaType);
                continue;
            }

            JingleContent.Senders senders = JingleUtils.getSenders(direction, !mPeer.isInitiator());
            // create the answer description
            JingleContent ourContent = JingleUtils.createDescription(content.getCreator(),
                    content.getName(), senders, mutuallySupportedFormats, rtpExtensions,
                    getDynamicPayloadTypes(), getRtpExtensionsRegistry(), rtcpmux, isImageattr(mPeer));

            /*
             * Sets ZRTP, SDES or DTLS-SRTP depending on the preferences for this media call.
             */
            setAndAddPreferredEncryptionProtocol(mediaType, ourContent, content, rtcpmux);

            // Got a content which has InputEvent. It means that the peer requests
            // a desktop sharing session so tell it we support InputEvent.
            if (content.getChildElements(InputEvent.class) != null) {
                ourContent.addChildElement(InputEvent.getBuilder().build());
            }
            answer.add(ourContent);
            localContentMap.put(content.getName(), ourContent);
            atLeastOneValidDescription = true;
        }

        if (!atLeastOneValidDescription) {
            // don't just throw exception. Must inform user to take action
            DialogActivity.showDialog(aTalkApp.getGlobalContext(), R.string.service_gui_CALL,
                    R.string.service_gui_CALL_NO_MATCHING_FORMAT_H, remoteFormats.toString());

            ProtocolProviderServiceJabberImpl.throwOperationFailedException(
                    "Offer contained no media formats or no valid media descriptions.",
                    OperationFailedException.ILLEGAL_ARGUMENT, null);
        }

        harvestCandidates(offer, answer, contents -> {
            try {
                mPeer.sendTransportInfo(contents);
            } catch (SmackException.NotConnectedException | InterruptedException e) {
                Timber.e(e, "Could not send transport info");
            }
        });

        /*
         * cmeng (20210405): with mux on RTP channel has greatly improved the connection speed.
         * cmeng: newly added (20200112)? need to check if it helps - may not be needed anymore as stated above.
         * In order to minimize post-pickup delay, start establishing the connectivity prior to ringing.
         */
        // harvestCandidates(offer, answer, infoSender);

        /*
         * While it may sound like we can completely eliminate the post-pickup delay by waiting for
         * the connectivity establishment to finish, it may not be possible in all cases. We are
         * the Jingle session responder so, in the case of the ICE UDP transport, we are not the
         * controlling ICE Agent and we cannot be sure when the controlling ICE Agent will perform
         * the nomination. It could, for example, choose to wait for our session-accept to perform
         * the nomination which will deadlock us if we have chosen to wait for the connectivity
         * establishment to finish before we begin ringing and send session-accept.
         */
        getTransportManager().startConnectivityEstablishment(offer);
    }

    /**
     * Processes the transport-related information provided by the remote <code>peer</code> in a
     * specific set of <code>JingleContent</code>s.
     *
     * @param contents the <code>JingleContent</code>s provided by the remote <code>peer</code> and
     * containing the transport-related information to be processed
     * @throws OperationFailedException if anything goes wrong while processing the transport-related information
     * provided by the remote <code>peer</code> in the specified set of <code>JingleContent</code>s
     */
    public void processTransportInfo(Iterable<JingleContent> contents)
            throws OperationFailedException
    {
        transportManager = getTransportManager();
        if (transportManager != null) {
            transportManager.startConnectivityEstablishment(contents);
        }
    }

    /**
     * Reinitialize all media contents.
     *
     * @throws OperationFailedException if we fail to handle <code>content</code> for reasons like failing
     * to initialize media devices or streams.
     * @throws IllegalArgumentException if there's a problem with the syntax or the semantics of <code>content</code>.
     * Method is synchronized in order to avoid closing mediaHandler when we are currently in process
     * of initializing, configuring and starting streams and anybody interested in this
     * operation can synchronize to the mediaHandler instance to wait processing to stop (method setState in CallPeer).
     */
    public void reinitAllContents()
            throws OperationFailedException, IllegalArgumentException
    {
        boolean masterStreamSet = false;
        for (String key : remoteContentMap.keySet()) {
            JingleContent content = remoteContentMap.get(key);

            boolean masterStream = false;
            // if we have more than one stream, lets the audio be the master
            if (!masterStreamSet) {
                RtpDescription description = content.getFirstChildElement(RtpDescription.class);
                MediaType mediaType = MediaType.parseString(description.getMedia());

                if (remoteContentMap.size() > 1) {
                    if (mediaType.equals(MediaType.AUDIO)) {
                        masterStream = true;
                        masterStreamSet = true;
                    }
                }
                else {
                    masterStream = true;
                    masterStreamSet = true;
                }
            }
            if (content != null)
                processContent(content, false, masterStream);
        }
    }

    /**
     * Reinitialize a media content such as video.
     *
     * @param name name of the Jingle content
     * @param content media content
     * @param modify if it corresponds to a content-modify for resolution change
     * @throws OperationFailedException if we fail to handle <code>content</code> for reasons like failing to
     * initialize media devices or streams.
     * @throws IllegalArgumentException if there's a problem with the syntax or the semantics of <code>content</code>.
     * Method is synchronized in order to avoid closing mediaHandler when we are currently in process of initializing,
     * configuring and starting streams and anybody interested in this operation can synchronize to the mediaHandler
     * instance to wait processing to stop (method setState in CallPeer).
     */
    public void reinitContent(String name, JingleContent content, boolean modify)
            throws OperationFailedException, IllegalArgumentException
    {
        JingleContent remoteContent = remoteContentMap.get(name);

        // Timber.w("Reinit Content: " + name + "; remoteContent: " + content + "; modify: " + modify);
        if (remoteContent != null) {
            if (modify) {
                processContent(content, modify, false);
                remoteContentMap.put(name, content);
            }
            else {
                remoteContent.setSenders(content.getSenders());
                processContent(remoteContent, modify, false);
                remoteContentMap.put(name, remoteContent);
            }
        }
    }

    /**
     * Removes a media content with a specific name from the session represented by this
     * <code>CallPeerMediaHandlerJabberImpl</code> and closes its associated media stream.
     *
     * @param contentMap the <code>Map</code> in which the specified <code>name</code> has an association with the
     * media content to be removed
     * @param name the name of the media content to be removed from this session
     */
    private void removeContent(Map<String, JingleContent> contentMap, String name)
    {
        JingleContent content = contentMap.remove(name);
        if (content != null) {
            RtpDescription description = content.getFirstChildElement(RtpDescription.class);
            String media = description.getMedia();
            if (media != null)
                closeStream(MediaType.parseString(media));
        }
    }

    /**
     * Removes a media content with a specific name from the session represented by this
     * <code>CallPeerMediaHandlerJabberImpl</code> and closes its associated media stream.
     *
     * @param name the name of the media content to be removed from this session
     */
    public void removeContent(String name)
    {
        removeContent(localContentMap, name);
        removeContent(remoteContentMap, name);

        TransportManagerJabberImpl transportManager = queryTransportManager();

        if (transportManager != null)
            transportManager.removeContent(name);
    }

    /**
     * Acts upon a notification received from the remote party indicating that they've put us on/off hold.
     *
     * @param onHold <code>true</code> if the remote party has put us on hold and <code>false</code> if they've
     * just put us off hold.
     */
    public void setRemotelyOnHold(boolean onHold)
            throws SmackException.NotConnectedException, InterruptedException
    {
        this.remotelyOnHold = onHold;

        for (MediaType mediaType : MediaType.values()) {
            MediaStream stream = getStream(mediaType);

            if (stream == null)
                continue;

            if (mPeer.isJitsiVideobridge()) {
                /*
                 * If we are the focus of a Videobridge conference, we need to ask the Videobridge
                 * to change the stream direction on our behalf.
                 */
                ColibriConferenceIQ.Channel channel = getColibriChannel(mediaType);
                MediaDirection direction;

                if (remotelyOnHold) {
                    direction = MediaDirection.INACTIVE;
                }
                else {
                    // TODO Does SENDRECV always make sense?
                    direction = MediaDirection.SENDRECV;
                }
                mPeer.getCall().setChannelDirection(channel.getID(), mediaType, direction);
            }
            else { // no Videobridge
                if (remotelyOnHold) {
                    /*
                     * In conferences we use INACTIVE to prevent, for example, on-hold music from
                     * being played to all the participants.
                     */
                    MediaDirection newDirection = mPeer.getCall().isConferenceFocus()
                            ? MediaDirection.INACTIVE
                            : stream.getDirection().and(MediaDirection.RECVONLY);
                    stream.setDirection(newDirection);
                }
                else {
                    stream.setDirection(calculatePostHoldDirection(stream));
                }
            }
        }
    }

    /**
     * Sometimes as initiating a call with custom preset can set and we force that quality controls is supported.
     *
     * @param value whether quality controls is supported..
     */
    public void setSupportQualityControls(boolean value)
    {
        this.supportQualityControls = value;
    }

    /**
     * Sets the <code>TransportManager</code> implementation to handle our address management by Jingle
     * transport XML namespace.
     *
     * @param xmlns the Jingle transport XML namespace specifying the <code>TransportManager</code>
     * implementation type to be set on this instance to handle our address management
     * @throws IllegalArgumentException if the specified <code>xmlns</code> does not specify a (supported)
     * <code>TransportManager</code> implementation type
     */
    private void setTransportManager(String xmlns)
            throws IllegalArgumentException
    {
        // Is this really going to be an actual change?
        if ((transportManager != null) && transportManager.getXmlNamespace().equals(xmlns)) {
            return;
        }

        if (!mPeer.getProtocolProvider().getDiscoveryManager().includesFeature(xmlns)) {
            throw new IllegalArgumentException("Unsupported Jingle transport " + xmlns);
        }

        /*
         * TODO The transportManager is going to be changed so it may need to be disposed of prior to the change.
         */

        switch (xmlns) {
            case ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE_ICE_UDP_1:
                transportManager = new IceUdpTransportManager(mPeer);
                break;
            case ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE_RAW_UDP_0:
                transportManager = new RawUdpTransportManager(mPeer);
                break;
            default:
                throw new IllegalArgumentException("Unsupported Jingle transport " + xmlns);
        }

        synchronized (transportManagerSyncRoot) {
            transportManagerSyncRoot.notify();
        }
    }

    /**
     * Waits for the associated <code>TransportManagerJabberImpl</code> to conclude any started
     * connectivity establishment and then starts this <code>CallPeerMediaHandler</code>.
     *
     * @throws IllegalStateException if no offer or answer has been provided or generated earlier
     */
    @Override
    public void start()
            throws IllegalStateException
    {
        try {
            wrapupConnectivityEstablishment();
        } catch (OperationFailedException ofe) {
            throw new UndeclaredThrowableException(ofe);
        }
        super.start();
    }

    /**
     * Lets the underlying implementation take note of this error and only then throws it to the
     * using bundles.
     *
     * @param message the message to be logged and then wrapped in a new <code>OperationFailedException</code>
     * @param errorCode the error code to be assigned to the new <code>OperationFailedException</code>
     * @param cause the <code>Throwable</code> that has caused the necessity to log an error and have a new
     * <code>OperationFailedException</code> thrown
     * @throws OperationFailedException the exception that we wanted this method to throw.
     */
    @Override
    protected void throwOperationFailedException(String message, int errorCode, Throwable cause)
            throws OperationFailedException
    {
        ProtocolProviderServiceJabberImpl.throwOperationFailedException(message, errorCode, cause);
    }

    /**
     * Notifies the associated <code>TransportManagerJabberImpl</code> that it should conclude any
     * connectivity establishment, waits for it to actually do so and sets the <code>connector</code>s
     * and <code>target</code>s of the <code>MediaStream</code>s managed by this
     * <code>CallPeerMediaHandler</code>.
     *
     * @throws OperationFailedException if anything goes wrong while setting the <code>connector</code>s and/or <code>target</code>s
     * of the <code>MediaStream</code>s managed by this <code>CallPeerMediaHandler</code>
     */
    private void wrapupConnectivityEstablishment()
            throws OperationFailedException
    {
        TransportManagerJabberImpl transportManager = getTransportManager();
        transportManager.wrapupConnectivityEstablishment();

        for (MediaType mediaType : MediaType.values()) {
            MediaStream stream = getStream(mediaType);
            if (stream != null) {
                stream.setConnector(transportManager.getStreamConnector(mediaType));
                stream.setTarget(transportManager.getStreamTarget(mediaType));
            }
        }
    }

    /**
     * If Jitsi Videobridge is in use, returns the <code>ColibriConferenceIQ.Channel</code> that this
     * <code>CallPeerMediaHandler</code> uses for media of type <code>mediaType</code>. Otherwise, returns <code>null</code>
     *
     * @param mediaType the <code>MediaType</code> for which to return a <code>ColibriConferenceIQ.Channel</code>
     * @return the <code>ColibriConferenceIQ.Channel</code> that this <code>CallPeerMediaHandler</code>
     * uses for media of type <code>mediaType</code> or <code>null</code>.
     */
    private ColibriConferenceIQ.Channel getColibriChannel(MediaType mediaType)
    {
        ColibriConferenceIQ.Channel channel = null;

        if (mPeer.isJitsiVideobridge()) {
            TransportManagerJabberImpl transportManager = this.transportManager;
            if (transportManager != null) {
                channel = transportManager.getColibriChannel(mediaType, false /* remote */);
            }
        }
        return channel;
    }

    /**
     * {@inheritDoc}
     *
     * The super implementation relies on the direction of the streams and is therefore not
     * accurate when we use a Videobridge.
     */
    @Override
    public boolean isRemotelyOnHold()
    {
        return remotelyOnHold;
    }

    /**
     * {@inheritDoc}
     *
     * Handles the case when a Videobridge is in use.
     *
     * @param locallyOnHold <code>true</code> if we are to make our streams stop transmitting and <code>false</code> if we
     * are to start transmitting
     */
    @Override
    public void setLocallyOnHold(boolean locallyOnHold)
            throws OperationFailedException
    {
        if (mPeer.isJitsiVideobridge()) {
            this.locallyOnHold = locallyOnHold;

            if (locallyOnHold || !CallPeerState.ON_HOLD_MUTUALLY.equals(mPeer.getState())) {
                for (MediaType mediaType : MediaType.values()) {
                    ColibriConferenceIQ.Channel channel = getColibriChannel(mediaType);

                    if (channel != null) {
                        MediaDirection direction = locallyOnHold ? MediaDirection.INACTIVE : MediaDirection.SENDRECV;
                        try {
                            mPeer.getCall().setChannelDirection(channel.getID(), mediaType, direction);
                        } catch (SmackException.NotConnectedException | InterruptedException e) {
                            throw new OperationFailedException("Could not send the channel direction",
                                    OperationFailedException.GENERAL_ERROR, e);
                        }
                    }
                }
            }
        }
        else {
            super.setLocallyOnHold(locallyOnHold);
        }
    }

    /**
     * Detects and adds DTLS-SRTP available encryption method present in the content (description)
     * given in parameter.
     *
     * @param isInitiator <code>true</code> if the local call instance is the initiator of the call; <code>false</code>,
     * otherwise.
     * @param content The CONTENT element of the JINGLE element which contains the TRANSPORT element
     * @param mediaType The type of media (AUDIO or VIDEO).
     */
    private boolean addDtlsSrtpAdvertisedEncryption(boolean isInitiator,
            JingleContent content, MediaType mediaType, boolean rtcpmux)
    {
        if (mPeer.isJitsiVideobridge()) {
            return false;
        }
        else {
            IceUdpTransport remoteTransport = content.getFirstChildElement(IceUdpTransport.class);
            return addDtlsSrtpAdvertisedEncryption(isInitiator, remoteTransport, mediaType, rtcpmux);
        }
    }

    /**
     * Detects and adds DTLS-SRTP available encryption method present in the transport (description) given in parameter.
     *
     * @param isInitiator <code>true</code> if the local call instance is the initiator of the call; <code>false</code>, otherwise.
     * @param remoteTransport the TRANSPORT element
     * @param mediaType The type of media (AUDIO or VIDEO).
     */
    boolean addDtlsSrtpAdvertisedEncryption(boolean isInitiator,
            IceUdpTransport remoteTransport, MediaType mediaType, boolean rtcpmux)
    {
        SrtpControls srtpControls = getSrtpControls();
        boolean b = false;

        if (remoteTransport != null) {
            List<SrtpFingerprint> remoteFingerprintPEs
                    = remoteTransport.getChildElements(SrtpFingerprint.class);

            if (!remoteFingerprintPEs.isEmpty()) {
                AccountID accountID = mPeer.getProtocolProvider().getAccountID();

                if (accountID.getAccountPropertyBoolean(ProtocolProviderFactory.DEFAULT_ENCRYPTION, true)
                        && accountID.isEncryptionProtocolEnabled(SrtpControlType.DTLS_SRTP)) {
                    Map<String, String> remoteFingerprints = new LinkedHashMap<>();

                    for (SrtpFingerprint remoteFingerprintPE : remoteFingerprintPEs) {
                        String remoteFingerprint = remoteFingerprintPE.getFingerprint();
                        String remoteHash = remoteFingerprintPE.getHash();
                        remoteFingerprints.put(remoteHash, remoteFingerprint);
                    }

                    // TODO Read the setup from the remote DTLS fingerprint elementExtension.
                    DtlsControl dtlsControl;
                    DtlsControl.Setup setup;
                    if (isInitiator) {
                        dtlsControl = (DtlsControl) srtpControls.get(mediaType, SrtpControlType.DTLS_SRTP);
                        setup = DtlsControl.Setup.ACTPASS;
                    }
                    else { // cmeng: must update transport-info with ufrag and pwd
                        String tlsCertSA = accountID.getAccountPropertyString(ProtocolProviderFactory.DTLS_CERT_SIGNATURE_ALGORITHM, DEFAULT_SIGNATURE_ALGORITHM);
                        DtlsControlImpl.setTlsCertificateSA(tlsCertSA);

                        dtlsControl = (DtlsControl) srtpControls.getOrCreate(mediaType, SrtpControlType.DTLS_SRTP, null);
                        setup = DtlsControl.Setup.ACTIVE;
                    }

                    if (dtlsControl != null) {
                        dtlsControl.setRemoteFingerprints(remoteFingerprints);
                        dtlsControl.setSetup(setup);
                        if (rtcpmux) {
                            dtlsControl.setRtcpmux(true);
                        }
                        removeAndCleanupOtherSrtpControls(mediaType, SrtpControlType.DTLS_SRTP);
                        addAdvertisedEncryptionMethod(SrtpControlType.DTLS_SRTP);
                        b = true;
                    }
                }
            }
        }
        /*
         * If they haven't advertised DTLS-SRTP in their (media) description, then DTLS-SRTP
         * shouldn't be functioning as far as we're concerned.
         */
        if (!b) {
            SrtpControl dtlsControl = srtpControls.get(mediaType, SrtpControlType.DTLS_SRTP);

            if (dtlsControl != null) {
                srtpControls.remove(mediaType, SrtpControlType.DTLS_SRTP);
                dtlsControl.cleanup(null);
            }
        }
        return b;
    }

    /**
     * Selects the preferred encryption protocol (only used by the callee).
     *
     * @param mediaType The type of media (AUDIO or VIDEO).
     * @param localContent The element containing the media DESCRIPTION and its encryption.
     * @param remoteContent The element containing the media DESCRIPTION and its encryption for the remote peer;
     * <code>null</code> if the local peer is the initiator of the call.
     */
    private void setAndAddPreferredEncryptionProtocol(MediaType mediaType,
            JingleContent localContent, JingleContent remoteContent, boolean rtcpmux)
    {
        List<SrtpControlType> preferredEncryptionProtocols
                = mPeer.getProtocolProvider().getAccountID().getSortedEnabledEncryptionProtocolList();

        for (SrtpControlType srtpControlType : preferredEncryptionProtocols) {
            // DTLS-SRTP
            if (srtpControlType == SrtpControlType.DTLS_SRTP) {
                addDtlsSrtpAdvertisedEncryption(false, remoteContent, mediaType, rtcpmux);
                if (setDtlsEncryptionOnContent(mediaType, localContent, remoteContent)) {
                    // Stop once an encryption advertisement has been chosen.
                    return;
                }
            }
            else {
                RtpDescription localDescription = (localContent == null)
                        ? null : localContent.getFirstChildElement(RtpDescription.class);
                RtpDescription remoteDescription = (remoteContent == null)
                        ? null : remoteContent.getFirstChildElement(RtpDescription.class);

                if (setAndAddPreferredEncryptionProtocol(srtpControlType, mediaType, localDescription, remoteDescription)) {
                    // Stop once an encryption advertisement has been chosen.
                    return;
                }
            }
        }
    }

    /**
     * Sets DTLS-SRTP element(s) to the TRANSPORT element of the CONTENT for a given media.
     *
     * @param mediaType The type of media we are modifying the CONTENT to integrate the DTLS-SRTP element(s).
     * @param localContent The element containing the media CONTENT and its TRANSPORT.
     * @param remoteContent The element containing the media CONTENT and its TRANSPORT for the remote peer. Null,
     * if the local peer is the initiator of the call.
     * @return <code>true</code> if any DTLS-SRTP element has been added to the specified
     * <code>localContent</code>; <code>false</code>, otherwise.
     */
    private boolean setDtlsEncryptionOnContent(MediaType mediaType, JingleContent localContent, JingleContent remoteContent)
    {
        boolean b = false;
        if (mPeer.isJitsiVideobridge()) {
            b = setDtlsEncryptionOnTransport(mediaType, localContent, remoteContent);
            return b;
        }

        ProtocolProviderServiceJabberImpl protocolProvider = mPeer.getProtocolProvider();
        AccountID accountID = protocolProvider.getAccountID();
        SrtpControls srtpControls = getSrtpControls();

        if (accountID.getAccountPropertyBoolean(ProtocolProviderFactory.DEFAULT_ENCRYPTION, true)
                && accountID.isEncryptionProtocolEnabled(SrtpControlType.DTLS_SRTP)) {
            boolean addFingerprintToLocalTransport;

            // initiator
            if (remoteContent == null) {
                addFingerprintToLocalTransport = protocolProvider.isFeatureSupported(mPeer.getPeerJid(),
                        ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE_DTLS_SRTP);
            }
            // responder
            else {
                addFingerprintToLocalTransport = addDtlsSrtpAdvertisedEncryption(false, remoteContent, mediaType, false);
            }
            if (addFingerprintToLocalTransport) {
                String tlsCertSA = accountID.getAccountPropertyString(ProtocolProviderFactory.DTLS_CERT_SIGNATURE_ALGORITHM, DEFAULT_SIGNATURE_ALGORITHM);
                DtlsControlImpl.setTlsCertificateSA(tlsCertSA);

                DtlsControl dtlsControl = (DtlsControl) srtpControls.getOrCreate(mediaType, SrtpControlType.DTLS_SRTP, null);
                if (dtlsControl != null) {
                    DtlsControl.Setup setup = (remoteContent == null)
                            ? DtlsControl.Setup.ACTPASS : DtlsControl.Setup.ACTIVE;

                    dtlsControl.setSetup(setup);
                    b = true;
                    setDtlsEncryptionOnTransport(mediaType, localContent, remoteContent);
                }
            }
        }
        /*
         * If we haven't advertised DTLS-SRTP in our (media) description, then DTLS-SRTP shouldn't
         * be functioning as far as we're concerned.
         */
        if (!b) {
            SrtpControl dtlsControl = srtpControls.get(mediaType, SrtpControlType.DTLS_SRTP);
            if (dtlsControl != null) {
                srtpControls.remove(mediaType, SrtpControlType.DTLS_SRTP);
                dtlsControl.cleanup(null);
            }
        }
        return b;
    }

    /**
     * Sets DTLS-SRTP element(s) to the TRANSPORT element of the CONTENT for a given media.
     *
     * @param mediaType The type of media we are modifying the CONTENT to integrate the DTLS-SRTP element(s).
     * @param localContent The element containing the media CONTENT and its TRANSPORT.
     */
    private boolean setDtlsEncryptionOnTransport(MediaType mediaType,
            JingleContent localContent, JingleContent remoteContent)
    {
        IceUdpTransport localTransport = localContent.getFirstChildElement(IceUdpTransport.class);
        boolean b = false;
        if (localTransport == null)
            return b;

        if (mPeer.isJitsiVideobridge()) {
            ProtocolProviderServiceJabberImpl protocolProvider = mPeer.getProtocolProvider();
            AccountID accountID = protocolProvider.getAccountID();

            if (accountID.getAccountPropertyBoolean(ProtocolProviderFactory.DEFAULT_ENCRYPTION,
                    true) && accountID.isEncryptionProtocolEnabled(SrtpControlType.DTLS_SRTP)) {
                // Gather the local fingerprints to be sent to the remote peer.
                ColibriConferenceIQ.Channel channel = getColibriChannel(mediaType);
                List<SrtpFingerprint> localFingerprints = null;

                if (channel != null) {
                    IceUdpTransport transport = channel.getTransport();
                    if (transport != null) {
                        localFingerprints = transport.getChildElements(SrtpFingerprint.class);
                    }
                }
                /*
                 * Determine whether the local fingerprints are to be sent to the remote peer.
                 */
                if ((localFingerprints != null) && !localFingerprints.isEmpty()) {
                    if (remoteContent == null) { // initiator
                        if (!protocolProvider.isFeatureSupported(mPeer.getPeerJid(),
                                ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE_DTLS_SRTP)) {
                            localFingerprints = null;
                        }
                    }
                    else { // responder
                        IceUdpTransport transport = remoteContent.getFirstChildElement(IceUdpTransport.class);
                        if (transport == null) {
                            localFingerprints = null;
                        }
                        else {
                            List<SrtpFingerprint> remoteFingerprints = transport.getChildElements(SrtpFingerprint.class);
                            if (remoteFingerprints.isEmpty())
                                localFingerprints = null;
                        }
                    }
                    // Send the local fingerprints to the remote peer.
                    if (localFingerprints != null) {
                        List<SrtpFingerprint> fingerprintPEs = localTransport.getChildElements(SrtpFingerprint.class);

                        if (fingerprintPEs.isEmpty()) {
                            for (SrtpFingerprint localFingerprint : localFingerprints) {
                                SrtpFingerprint srtpFingerPrint = SrtpFingerprint.getBuilder()
                                        .setFingerprint(localFingerprint.getFingerprint())
                                        .setHash(localFingerprint.getHash())
                                        .setSetup(localFingerprint.getSetup())
                                        .build();
                                localTransport.addChildElement(srtpFingerPrint);
                            }
                        }
                        b = true;
                    }
                }
            }
        }
        else {
            SrtpControls srtpControls = getSrtpControls();
            DtlsControl dtlsControl = (DtlsControl) srtpControls.get(mediaType, SrtpControlType.DTLS_SRTP);

            if (dtlsControl != null) {
                CallJabberImpl.setDtlsEncryptionOnTransport(dtlsControl, localTransport);
                b = true;
            }
        }
        return b;
    }

    /**
     * Sets DTLS-SRTP element(s) to the TRANSPORT element of a specified list of CONTENT elements.
     *
     * @param localContents The elements containing the media CONTENT elements and their respective TRANSPORT elements.
     */
    private void setDtlsEncryptionOnTransports(List<JingleContent> remoteContents,
            List<JingleContent> localContents)
    {
        for (JingleContent localContent : localContents) {
            RtpDescription description = localContent.getFirstChildElement(RtpDescription.class);

            if (description != null) {
                MediaType mediaType = JingleUtils.getMediaType(localContent);
                if (mediaType != null) {
                    JingleContent remoteContent = (remoteContents == null)
                            ? null : TransportManagerJabberImpl.findContentByName(remoteContents, localContent.getName());
                    setDtlsEncryptionOnTransport(mediaType, localContent, remoteContent);
                }
            }
        }
    }

    /**
     * Sets the jingle transports that this <code>CallPeerMediaHandlerJabberImpl</code> supports.
     * Unknown transports are ignored, and the <code>transports</code> <code>Collection</code> is put into
     * order depending on local preference.
     *
     * Currently only ice and raw-udp are recognized, with ice being preferred over raw-udp
     *
     * @param transports A <code>Collection</code> of XML namespaces of jingle transport elements to be set as the
     * supported jingle transports for this <code>CallPeerMediaHandlerJabberImpl</code>
     */
    public void setSupportedTransports(Collection<String> transports)
    {
        if (transports == null)
            return;

        String ice = ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE_ICE_UDP_1;
        String rawUdp = ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE_RAW_UDP_0;

        int size = 0;
        for (String transport : transports)
            if (ice.equals(transport) || rawUdp.equals(transport))
                size++;

        if (size > 0) {
            synchronized (supportedTransportsSyncRoot) {
                supportedTransports = new String[size];
                int i = 0;

                // we prefer ice over raw-udp
                if (transports.contains(ice)) {
                    supportedTransports[i] = ice;
                    i++;
                }
                if (transports.contains(rawUdp)) {
                    supportedTransports[i] = rawUdp;
                    i++;
                }
            }
        }
    }

    /**
     * Gets the <code>inputevt</code> support: true for enable, false for disable.
     *
     * @return The state of inputevt support: true for enable, false for disable.
     */
    public boolean getLocalInputEvtAware()
    {
        return this.localInputEvtAware;
    }

    /**
     * Enable or disable <code>inputevt</code> support (remote-control).
     *
     * @param enable new state of inputevt support
     */
    public void setLocalInputEvtAware(boolean enable)
    {
        localInputEvtAware = enable;
    }

    /**
     * Detects and adds ZRTP available encryption method present in the description given in parameter.
     *
     * @param isInitiator True if the local call instance is the initiator of the call. False otherwise.
     * @param description The DESCRIPTION element of the JINGLE element which
     * contains the PAYLOAD-TYPE and (more important here) the ENCRYPTION.
     * @param mediaType The type of media (AUDIO or VIDEO).
     */
    private void addZrtpAdvertisedEncryption(boolean isInitiator, RtpDescription description, MediaType mediaType)
    {
        // ZRTP is not supported in telephony conferences utilizing the
        // server-side technology Jitsi Videobridge yet.
        if (mPeer.isJitsiVideobridge())
            return;

        // Conforming to XEP-0167 schema there is 0 or 1 <encryption/> element for a given <description/>>.
        SrtpEncryption srtpEncryption = description.getFirstChildElement(SrtpEncryption.class);

        if (srtpEncryption != null) {
            AccountID accountID = mPeer.getProtocolProvider().getAccountID();
            Call call = mPeer.getCall();

            if (accountID.getAccountPropertyBoolean(ProtocolProviderFactory.DEFAULT_ENCRYPTION, true)
                    && accountID.isEncryptionProtocolEnabled(SrtpControlType.ZRTP) && call.isSipZrtpAttribute()) {

                // ZRTP
                ZrtpHash zrtpHash = srtpEncryption.getFirstChildElement(ZrtpHash.class);
                if ((zrtpHash != null) && (zrtpHash.getHashValue() != null)) {
                    addAdvertisedEncryptionMethod(SrtpControlType.ZRTP);

                    ZrtpControl zrtpControl = (ZrtpControl) getSrtpControls().get(mediaType, SrtpControlType.ZRTP);
                    if (zrtpControl != null) {
                        zrtpControl.setReceivedSignaledZRTPVersion(zrtpHash.getVersion());
                        zrtpControl.setReceivedSignaledZRTPHashValue(zrtpHash.getHashValue());
                    }
                }
            }
        }
    }

    /**
     * Detects and adds SDES available encryption method present in the description given in parameter.
     *
     * @param isInitiator True if the local call instance is the initiator of the call. False otherwise.
     * @param description The DESCRIPTION element of the JINGLE element which
     * contains the PAYLOAD-TYPE and (more important here) the ENCRYPTION.
     * @param mediaType The type of media (AUDIO or VIDEO).
     */
    private void addSDesAdvertisedEncryption(boolean isInitiator, RtpDescription description, MediaType mediaType)
    {
        // SDES is not supported in telephony conferences utilizing the
        // server-side technology Jitsi Videobridge yet.
        if (mPeer.isJitsiVideobridge())
            return;

        // Conforming to XEP-0167 schema there is 0 or 1 ENCRYPTION element for a given DESCRIPTION.
        SrtpEncryption srtpEncryption = description.getFirstChildElement(SrtpEncryption.class);

        if (srtpEncryption != null) {
            AccountID accountID = mPeer.getProtocolProvider().getAccountID();

            // SDES
            if (accountID.getAccountPropertyBoolean(ProtocolProviderFactory.DEFAULT_ENCRYPTION, true)
                    && accountID.isEncryptionProtocolEnabled(SrtpControlType.SDES)) {
                SrtpControls srtpControls = getSrtpControls();
                SDesControl sdesControl = (SDesControl) srtpControls.getOrCreate(mediaType, SrtpControlType.SDES, null);
                SrtpCryptoAttribute selectedSdes = selectSdesCryptoSuite(isInitiator, sdesControl, srtpEncryption);

                if (selectedSdes != null) {
                    //found an SDES answer, remove all other controls
                    removeAndCleanupOtherSrtpControls(mediaType, SrtpControlType.SDES);
                    addAdvertisedEncryptionMethod(SrtpControlType.SDES);
                }
                else {
                    sdesControl.cleanup(null);
                    srtpControls.remove(mediaType, SrtpControlType.SDES);
                }
            }
        }
        // If we were initiating the encryption, and the remote peer does not
        // manage it, then we must remove the unusable SDES srtpControl.
        else if (isInitiator) {
            // SDES
            SrtpControl sdesControl = getSrtpControls().remove(mediaType, SrtpControlType.SDES);

            if (sdesControl != null)
                sdesControl.cleanup(null);
        }
    }

    /**
     * Returns the selected SDES crypto suite selected.
     *
     * @param isInitiator True if the local call instance is the initiator of the call. False otherwise.
     * @param sDesControl The SDES based SRTP MediaStream encryption control.
     * @param srtpEncryption The ENCRYPTION element received from the
     * remote peer. This may contain the SDES crypto suites available for the remote peer.
     * @return The selected SDES crypto suite supported by both the local and
     * the remote peer. Or null, if there is no crypto suite supported by both of the peers.
     */
    private SrtpCryptoAttribute selectSdesCryptoSuite(boolean isInitiator, SDesControl sDesControl,
            SrtpEncryption srtpEncryption)
    {
        List<SdpCrypto> sdpCryptos = srtpEncryption.getCryptoList();
        List<SrtpCryptoAttribute> peerAttributes = new ArrayList<>(sdpCryptos.size());

        for (SdpCrypto cpe : sdpCryptos)
            peerAttributes.add(SrtpCryptoAttribute.create(cpe.getTag(), cpe.getCryptoSuite(),
                    cpe.getKeyParams(), cpe.getSessionParams()));

        return isInitiator ? sDesControl.initiatorSelectAttribute(peerAttributes)
                : sDesControl.responderSelectAttribute(peerAttributes);
    }

    /**
     * Returns if the remote peer supports ZRTP.
     *
     * @param srtpEncryption The ENCRYPTION element received from
     * the remote peer. This may contain the ZRTP packet element for the remote peer.
     * @return True if the remote peer supports ZRTP. False, otherwise.
     */
    private boolean isRemoteZrtpCapable(SrtpEncryption srtpEncryption)
    {
        return (srtpEncryption.getChildElements(ZrtpHash.class) != null);
    }

    /**
     * Sets ZRTP element to the ENCRYPTION element of the DESCRIPTION for a given media.
     *
     * @param mediaType The type of media we are modifying the DESCRIPTION to integrate the ENCRYPTION element.
     * @param description The element containing the media DESCRIPTION and its encryption.
     * @param remoteDescription The element containing the media DESCRIPTION and
     * its encryption for the remote peer. Null, if the local peer is the initiator of the call.
     * @return True if the ZRTP element has been added to encryption. False, otherwise.
     */
    private boolean setZrtpEncryptionOnDescription(MediaType mediaType, RtpDescription description,
            RtpDescription remoteDescription)
    {
        // ZRTP is not supported in telephony conferences utilizing the server-side technology Jitsi Videobridge yet.
        if (mPeer.isJitsiVideobridge())
            return false;

        boolean isRemoteZrtpCapable;
        if (remoteDescription == null)
            isRemoteZrtpCapable = true;
        else {
            // Conforming to XEP-0167 schema there is 0 or 1 ENCRYPTION element for a given DESCRIPTION.
            SrtpEncryption remoteSrtpEncryption = remoteDescription.getFirstChildElement(SrtpEncryption.class);
            isRemoteZrtpCapable = (remoteSrtpEncryption != null) && isRemoteZrtpCapable(remoteSrtpEncryption);
        }

        boolean zrtpHashSet = false; // Will become true if at least one is set.
        if (isRemoteZrtpCapable) {
            AccountID accountID = mPeer.getProtocolProvider().getAccountID();
            Call call = mPeer.getCall();

            if (accountID.getAccountPropertyBoolean(ProtocolProviderFactory.DEFAULT_ENCRYPTION, true)
                    && accountID.isEncryptionProtocolEnabled(SrtpControlType.ZRTP)
                    && call.isSipZrtpAttribute()) {
                final byte[] myZid = generateMyZid(accountID, mPeer.getPeerJid().asBareJid());

                ZrtpControl zrtpControl = (ZrtpControl) getSrtpControls().getOrCreate(mediaType, SrtpControlType.ZRTP, myZid);
                int numberSupportedVersions = zrtpControl.getNumberSupportedVersions();

                // Try to get the remote ZRTP version and hash value
                if (remoteDescription != null) {
                    SrtpEncryption remoteSrtpEncryption = remoteDescription.getFirstChildElement(SrtpEncryption.class);

                    if (remoteSrtpEncryption != null) {
                        ZrtpHash zrtpHash = remoteSrtpEncryption.getFirstChildElement(ZrtpHash.class);

                        if ((zrtpHash != null) && (zrtpHash.getHashValue() != null)) {
                            zrtpControl.setReceivedSignaledZRTPVersion(zrtpHash.getVersion());
                            zrtpControl.setReceivedSignaledZRTPHashValue(zrtpHash.getHashValue());
                        }
                    }
                }

                for (int i = 0; i < numberSupportedVersions; i++) {
                    String[] helloHash = zrtpControl.getHelloHashSep(i);

                    if ((helloHash != null) && (helloHash[1].length() > 0)) {
                        ZrtpHash zrtpHash = ZrtpHash.getBuilder()
                                .setVersion(helloHash[0])
                                .setHashValue(helloHash[1])
                                .build();

                        SrtpEncryption srtpEncryption = description.getFirstChildElement(SrtpEncryption.class);
                        if (srtpEncryption == null) {
                            description.addChildElement(SrtpEncryption.getBuilder()
                                    .addChildElement(zrtpHash)
                                    .build());
                        }
                        else {
                            srtpEncryption.addChildElement(zrtpHash);
                        }
                        zrtpHashSet = true;
                    }
                }
            }
        }
        return zrtpHashSet;
    }

    /**
     * Sets SDES element(s) to the ENCRYPTION element of the DESCRIPTION for a given media.
     *
     * @param mediaType The type of media we are modifying the DESCRIPTION to integrate the ENCRYPTION element.
     * @param localDescription The element containing the media DESCRIPTION and its encryption.
     * @param remoteDescription The element containing the media DESCRIPTION and
     * its encryption for the remote peer. Null, if the local peer is the initiator of the call.
     * @return True if the crypto element has been added to encryption. False, otherwise.
     */
    private boolean setSdesEncryptionOnDescription(MediaType mediaType, RtpDescription localDescription,
            RtpDescription remoteDescription)
    {
        /*
         * SDES is not supported in telephony conferences utilizing the server-side technology Jitsi Videobridge yet.
         */
        if (mPeer.isJitsiVideobridge())
            return false;

        AccountID accountID = mPeer.getProtocolProvider().getAccountID();

        // check if SDES and encryption is enabled at all
        if (accountID.getAccountPropertyBoolean(ProtocolProviderFactory.DEFAULT_ENCRYPTION, true)
                && accountID.isEncryptionProtocolEnabled(SrtpControlType.SDES)) {
            // get or create the control
            SrtpControls srtpControls = getSrtpControls();
            SDesControl sdesControl = (SDesControl) srtpControls.getOrCreate(mediaType, SrtpControlType.SDES, null);
            // set the enabled ciphers suites (must remove any unwanted spaces)
            String ciphers = accountID.getAccountPropertyString(ProtocolProviderFactory.SDES_CIPHER_SUITES);

            if (ciphers == null) {
                ciphers = JabberActivator.getResources().getSettingsString(SDesControl.SDES_CIPHER_SUITES);
            }
            sdesControl.setEnabledCiphers(Arrays.asList(ciphers.split(",")));

            // act as initiator
            if (remoteDescription == null) {
                SrtpEncryption.Builder srtpBuilder = SrtpEncryption.getBuilder();
                SrtpEncryption localSrtpEncryption = localDescription.getFirstChildElement(SrtpEncryption.class);
                if (localSrtpEncryption != null) {
                    srtpBuilder.addChildElements(localSrtpEncryption.getCryptoList());
                    localDescription.removeChildElement(localSrtpEncryption);
                }

                for (SrtpCryptoAttribute ca : sdesControl.getInitiatorCryptoAttributes()) {
                    SdpCrypto crypto = SdpCrypto.getBuilder()
                            .setCrypto(
                                    ca.getTag(), ca.getCryptoSuite().encode(),
                                    ca.getKeyParamsString(), ca.getSessionParamsString())
                            .build();
                    srtpBuilder.addChildElement(crypto);
                }

                localDescription.addChildElement(srtpBuilder.build());
                return true;
            }
            // act as responder
            else {
                // Conforming to XEP-0167 schema there is 0 or 1 ENCRYPTION element for a given DESCRIPTION.
                SrtpEncryption remoteSrtpEncryption = remoteDescription.getFirstChildElement(SrtpEncryption.class);

                if (remoteSrtpEncryption != null) {
                    SrtpCryptoAttribute selectedSdes = selectSdesCryptoSuite(false, sdesControl, remoteSrtpEncryption);

                    if (selectedSdes != null) {
                        SrtpEncryption localSrtpEncryption = localDescription.getFirstChildElement(SrtpEncryption.class);

                        SrtpEncryption.Builder srtpBuilder = SrtpEncryption.getBuilder();
                        if (localSrtpEncryption != null) {
                            srtpBuilder.addChildElements(localSrtpEncryption.getCryptoList());
                            localDescription.removeChildElement(localSrtpEncryption);
                        }

                        SdpCrypto crypto = SdpCrypto.getBuilder()
                                .setCrypto(
                                        selectedSdes.getTag(), selectedSdes.getCryptoSuite().encode(),
                                        selectedSdes.getKeyParamsString(), selectedSdes.getSessionParamsString())
                                .build();
                        srtpBuilder.addChildElement(crypto);

                        localDescription.addChildElement(srtpBuilder.build());
                        return true;
                    }
                    else {
                        // none of the offered suites match, destroy the sdes control
                        sdesControl.cleanup(null);
                        srtpControls.remove(mediaType, SrtpControlType.SDES);
                        Timber.w("Received unsupported sdes crypto attribute");
                    }
                }
                else {
                    // peer doesn't offer any SDES attribute, destroy the sdes control
                    sdesControl.cleanup(null);
                    srtpControls.remove(mediaType, SrtpControlType.SDES);
                }
            }
        }
        return false;
    }

    /**
     * Selects a specific encryption protocol if it is the preferred (only used by the callee).
     *
     * @param mediaType The type of media (AUDIO or VIDEO).
     * @param localDescription The element containing the media DESCRIPTION and its encryption.
     * @param remoteDescription The element containing the media DESCRIPTION and
     * its encryption for the remote peer; <code>null</code> if the local peer is the initiator of the call.
     * @return <code>true</code> if the specified encryption protocol has been selected; <code>false</code>, otherwise
     */
    private boolean setAndAddPreferredEncryptionProtocol(SrtpControlType srtpControlType, MediaType mediaType,
            RtpDescription localDescription, RtpDescription remoteDescription)
    {
        /*
         * Neither SDES nor ZRTP is supported in telephony conferences utilizing the server-side technology
         * Jitsi Videobridge yet.
         */
        if (mPeer.isJitsiVideobridge())
            return false;

        // SDES
        if (srtpControlType == SrtpControlType.SDES) {
            addSDesAdvertisedEncryption(false, remoteDescription, mediaType);
            if (setSdesEncryptionOnDescription(mediaType, localDescription, remoteDescription)) {
                // Stop once an encryption advertisement has been chosen.
                return true;
            }
        }

        // ZRTP
        else if (srtpControlType == SrtpControlType.ZRTP) {
            if (setZrtpEncryptionOnDescription(mediaType, localDescription, remoteDescription)) {
                addZrtpAdvertisedEncryption(false, remoteDescription, mediaType);
                // Stop once an encryption advertisement has been chosen.
                return true;
            }
        }
        return false;
    }
}
