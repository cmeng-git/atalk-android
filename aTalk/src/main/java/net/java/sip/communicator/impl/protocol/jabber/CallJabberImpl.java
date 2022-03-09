/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import net.java.sip.communicator.service.protocol.AccountID;
import net.java.sip.communicator.service.protocol.CallPeerState;
import net.java.sip.communicator.service.protocol.OperationFailedException;
import net.java.sip.communicator.service.protocol.OperationSetBasicAutoAnswer;
import net.java.sip.communicator.service.protocol.OperationSetBasicTelephony;
import net.java.sip.communicator.service.protocol.OperationSetIncomingDTMF;
import net.java.sip.communicator.service.protocol.ProtocolProviderFactory;
import net.java.sip.communicator.service.protocol.event.CallChangeEvent;
import net.java.sip.communicator.service.protocol.event.CallEvent;
import net.java.sip.communicator.service.protocol.event.DTMFReceivedEvent;
import net.java.sip.communicator.service.protocol.media.MediaAwareCall;
import net.java.sip.communicator.service.protocol.media.MediaHandler;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.call.JingleMessageHelper;
import org.atalk.impl.neomedia.transform.dtls.DtlsControlImpl;
import org.atalk.service.neomedia.DtlsControl;
import org.atalk.service.neomedia.MediaDirection;
import org.atalk.service.neomedia.SrtpControlType;
import org.atalk.service.neomedia.StreamConnector;
import org.atalk.service.neomedia.StreamConnectorFactory;
import org.atalk.util.MediaType;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.StanzaCollector;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smackx.colibri.ColibriConferenceIQ;
import org.jivesoftware.smackx.disco.packet.DiscoverInfo;
import org.jivesoftware.smackx.jingle.CoinExtension;
import org.jivesoftware.smackx.jingle.IceUdpTransport;
import org.jivesoftware.smackx.jingle.JingleUtil;
import org.jivesoftware.smackx.jingle.JingleUtils;
import org.jivesoftware.smackx.jingle.PayloadType;
import org.jivesoftware.smackx.jingle.RtpDescription;
import org.jivesoftware.smackx.jingle.SdpTransfer;
import org.jivesoftware.smackx.jingle.SrtpFingerprint;
import org.jivesoftware.smackx.jingle.element.Jingle;
import org.jivesoftware.smackx.jingle.element.JingleContent;
import org.jivesoftware.smackx.jingle.element.JingleReason;
import org.jivesoftware.smackx.jinglemessage.packet.JingleMessage;
import org.jxmpp.jid.FullJid;
import org.jxmpp.jid.Jid;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import timber.log.Timber;

/**
 * A Jabber implementation of the <code>Call</code> abstract class encapsulating Jabber jingle sessions.
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Boris Grozev
 * @author Eng Chong Meng
 * @author MilanKral
 */
public class CallJabberImpl extends MediaAwareCall<CallPeerJabberImpl,
        OperationSetBasicTelephonyJabberImpl, ProtocolProviderServiceJabberImpl>
{
    /**
     * The Jitsi Videobridge conference which the local peer represented by this instance is a focus of.
     */
    private ColibriConferenceIQ colibri;

    /**
     * The shared <code>CallPeerMediaHandler</code> state which is to be used by the <code>CallPeer</code>s
     * of this <code>Call</code> which use {@link #colibri}.
     */
    private MediaHandler colibriMediaHandler;

    /**
     * Contains one ColibriStreamConnector for each <code>MediaType</code>
     */
    private final List<WeakReference<ColibriStreamConnector>> colibriStreamConnectors;

    /**
     * The entity ID of the Jitsi Videobridge to be utilized by this <code>Call</code> for the purposes
     * of establishing a server-assisted telephony conference.
     */
    private Jid mJitsiVideobridge;

    private CallPeerMediaHandlerJabberImpl mMediaHandler;

    /**
     * Indicates if the <code>CallPeer</code> will support <code>inputevt</code>
     * extension (i.e. will be able to be remote-controlled).
     */
    private boolean localInputEvtAware = false;

    private final XMPPConnection mConnection;

    /**
     * A map of the callee FullJid to JingleMessage id, id value is used in session-initiate creation,
     * if call is originated from JingleMessage propose. The entry should be removed after its use.
     */
    private static final Map<Jid, String> mJingleCallIds = new WeakHashMap<>();

    /**
     * Initializes a new <code>CallJabberImpl</code> instance.
     *
     * @param parentOpSet the {@link OperationSetBasicTelephonyJabberImpl} instance in the context
     * of which this call has been created.
     * @param sid the Jingle session-initiate id if provided.
     */
    protected CallJabberImpl(OperationSetBasicTelephonyJabberImpl parentOpSet, String sid)
    {
        super(parentOpSet, sid);

        mConnection = getProtocolProvider().getConnection();
        int mediaTypeValueCount = MediaType.values().length;
        colibriStreamConnectors = new ArrayList<>(mediaTypeValueCount);
        for (int i = 0; i < mediaTypeValueCount; i++)
            colibriStreamConnectors.add(null);

        // let's add ourselves to the calls repo. we are doing it ourselves just to make sure that
        // no one ever forgets.
        parentOpSet.getActiveCallsRepository().addCall(this);
    }

    /**
     * Closes a specific <code>ColibriStreamConnector</code> which is associated with a
     * <code>MediaStream</code> of a specific <code>MediaType</code> upon request from a specific <code>CallPeer</code>.
     *
     * @param peer the <code>CallPeer</code> which requests the closing of the specified <code>colibriStreamConnector</code>
     * @param mediaType the <code>MediaType</code> of the <code>MediaStream</code> with which the specified
     * <code>colibriStreamConnector</code> is associated
     * @param colibriStreamConnector the <code>ColibriStreamConnector</code> to close on behalf of the specified <code>peer</code>
     */
    public void closeColibriStreamConnector(CallPeerJabberImpl peer, MediaType mediaType,
            ColibriStreamConnector colibriStreamConnector)
    {
        colibriStreamConnector.close();
        synchronized (colibriStreamConnectors) {
            int index = mediaType.ordinal();
            WeakReference<ColibriStreamConnector> weakReference = colibriStreamConnectors.get(index);
            if (weakReference != null && colibriStreamConnector.equals(weakReference.get())) {
                colibriStreamConnectors.set(index, null);
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * Sends a <code>content</code> message to each of the <code>CallPeer</code>s associated with this
     * <code>CallJabberImpl</code> in order to include/exclude the &quot;isfocus&quot; attribute.
     */
    @Override
    protected void conferenceFocusChanged(boolean oldValue, boolean newValue)
    {
        try {
            Iterator<CallPeerJabberImpl> peers = getCallPeers();
            while (peers.hasNext()) {
                CallPeerJabberImpl callPeer = peers.next();
                if (callPeer.getState() == CallPeerState.CONNECTED)
                    callPeer.sendCoinSessionInfo();
            }
        } catch (NotConnectedException | InterruptedException e) {
            e.printStackTrace();
        } finally {
            super.conferenceFocusChanged(oldValue, newValue);
        }
    }

    /**
     * Allocates colibri (conference) channels for a specific <code>MediaType</code> to be used by a
     * specific <code>CallPeer</code>.
     *
     * @param peer the <code>CallPeer</code> which is to use the allocated colibri (conference) channels
     * @param contentMap the local and remote <code>JingleContent</code>s which specify the
     * <code>MediaType</code>s for which colibri (conference) channels are to be allocated
     * @return a <code>ColibriConferenceIQ</code> which describes the allocated colibri (conference)
     * channels for the specified <code>mediaTypes</code> which are to be used by the specified
     * <code>peer</code>; otherwise, <code>null</code>
     */
    public ColibriConferenceIQ createColibriChannels(CallPeerJabberImpl peer,
            Map<JingleContent, JingleContent> contentMap)
            throws OperationFailedException
    {
        if (!getConference().isJitsiVideobridge())
            return null;

        /*
         * For a colibri conference to work properly, all CallPeers in the conference must share
         * one and the same CallPeerMediaHandler state i.e. they must use a single set of
         * MediaStreams as if there was a single CallPeerMediaHandler.
         */
        CallPeerMediaHandlerJabberImpl peerMediaHandler = peer.getMediaHandler();
        if (peerMediaHandler.getMediaHandler() != colibriMediaHandler) {
            for (MediaType mediaType : MediaType.values()) {
                if (peerMediaHandler.getStream(mediaType) != null)
                    return null;
            }
        }

        ProtocolProviderServiceJabberImpl protocolProvider = getProtocolProvider();
        Jid jvb = (colibri == null) ? getJitsiVideobridge() : colibri.getFrom();
        if ((jvb == null) || (jvb.length() == 0)) {
            Timber.e("Failed to allocate colibri channels: no videobridge found.");
            return null;
        }

        /*
         * The specified CallPeer will participate in the colibri conference organized by this Call so it
         * must use the shared CallPeerMediaHandler state of all CallPeers in the same colibri conference.
         */
        if (colibriMediaHandler == null)
            colibriMediaHandler = new MediaHandler();
        peerMediaHandler.setMediaHandler(colibriMediaHandler);

        ColibriConferenceIQ conferenceRequest = new ColibriConferenceIQ();
        if (colibri != null)
            conferenceRequest.setID(colibri.getID());

        for (Map.Entry<JingleContent, JingleContent> e : contentMap.entrySet()) {
            JingleContent localContent = e.getKey();
            JingleContent remoteContent = e.getValue();
            JingleContent cpe = (remoteContent == null) ? localContent : remoteContent;
            RtpDescription rdpe = cpe.getFirstChildElement(RtpDescription.class);
            String media = rdpe.getMedia();
            MediaType mediaType = MediaType.parseString(media);
            String contentName = mediaType.toString();
            ColibriConferenceIQ.Content contentRequest = new ColibriConferenceIQ.Content(contentName);

            conferenceRequest.addContent(contentRequest);
            boolean requestLocalChannel = true;

            if (colibri != null) {
                ColibriConferenceIQ.Content content = colibri.getContent(contentName);
                if ((content != null) && (content.getChannelCount() > 0))
                    requestLocalChannel = false;
            }

            boolean peerIsInitiator = peer.isInitiator();
            if (requestLocalChannel) {
                ColibriConferenceIQ.Channel localChannelRequest = new ColibriConferenceIQ.Channel();
                localChannelRequest.setEndpoint(protocolProvider.getOurJID().toString());
                localChannelRequest.setInitiator(peerIsInitiator);

                for (PayloadType ptpe : rdpe.getChildElements(PayloadType.class))
                    localChannelRequest.addPayloadType(ptpe);
                setTransportOnChannel(peer, media, localChannelRequest);
                // DTLS-SRTP
                setDtlsEncryptionOnChannel(mJitsiVideobridge, peer, mediaType, localChannelRequest);
                /*
                 * Since Jitsi Videobridge supports multiple Jingle transports, it is a good
                 * idea to indicate which one is expected on a channel.
                 */
                ensureTransportOnChannel(localChannelRequest, peer);
                contentRequest.addChannel(localChannelRequest);
            }

            ColibriConferenceIQ.Channel remoteChannelRequest = new ColibriConferenceIQ.Channel();
            remoteChannelRequest.setEndpoint(peer.getAddress());
            remoteChannelRequest.setInitiator(!peerIsInitiator);

            for (PayloadType ptpe : rdpe.getChildElements(PayloadType.class))
                remoteChannelRequest.addPayloadType(ptpe);
            setTransportOnChannel(media, localContent, remoteContent, peer, remoteChannelRequest);
            // DTLS-SRTP
            setDtlsEncryptionOnChannel(mediaType, localContent, remoteContent, peer, remoteChannelRequest);
            /*
             * Since Jitsi Videobridge supports multiple Jingle transports, it is a good idea to
             * indicate which one is expected on a channel.
             */
            ensureTransportOnChannel(remoteChannelRequest, peer);
            contentRequest.addChannel(remoteChannelRequest);
        }

        conferenceRequest.setTo(mJitsiVideobridge);
        conferenceRequest.setType(IQ.Type.get);
        XMPPConnection connection = protocolProvider.getConnection();
        Stanza response;
        try {
            StanzaCollector stanzaCollector = connection.createStanzaCollectorAndSend(conferenceRequest);
            try {
                response = stanzaCollector.nextResultOrThrow();
            } finally {
                stanzaCollector.cancel();
            }
        } catch (NotConnectedException | InterruptedException e1) {
            throw new OperationFailedException("Could not send the conference request",
                    OperationFailedException.REGISTRATION_REQUIRED, e1);
        } catch (XMPPException.XMPPErrorException e) {
            Timber.e("Failed to allocate colibri channel: %s", e.getMessage());
            return null;
        } catch (SmackException.NoResponseException e) {
            Timber.e("Failed to allocate colibri channels: %s", e.getMessage());
            return null;
        }

        ColibriConferenceIQ conferenceResponse = (ColibriConferenceIQ) response;
        String conferenceResponseID = conferenceResponse.getID();

        /*
         * Update the complete ColibriConferenceIQ representation maintained by this instance with
         * the information given by the (current) response.
         */
        if (colibri == null) {
            colibri = new ColibriConferenceIQ();
            /*
             * XXX We must remember the JID of the Jitsi Videobridge because (1) we do not want to
             * re-discover it in every method invocation on this Call instance and (2) we want to
             * use one and the same for all CallPeers within this Call instance.
             */
            colibri.setFrom(conferenceResponse.getFrom());
        }

        String colibriID = colibri.getID();
        if (colibriID == null)
            colibri.setID(conferenceResponseID);
        else if (!colibriID.equals(conferenceResponseID))
            throw new IllegalStateException("conference.id");

        for (ColibriConferenceIQ.Content contentResponse : conferenceResponse.getContents()) {
            String contentName = contentResponse.getName();
            ColibriConferenceIQ.Content content = colibri.getOrCreateContent(contentName);

            for (ColibriConferenceIQ.Channel channelResponse : contentResponse.getChannels()) {
                int channelIndex = content.getChannelCount();

                content.addChannel(channelResponse);
                if (channelIndex == 0) {
                    TransportManagerJabberImpl transportManager = peerMediaHandler.getTransportManager();

                    transportManager.isEstablishingConnectivityWithJitsiVideobridge = true;
                    transportManager.startConnectivityEstablishmentWithJitsiVideobridge = true;
                    MediaType mediaType = MediaType.parseString(contentName);

                    // DTLS-SRTP
                    addDtlsAdvertisedEncryptions(peer, channelResponse, mediaType);
                }
            }
        }

        /*
         * Formulate the result to be returned to the caller which is a subset of the whole
         * conference information kept by this CallJabberImpl and includes the remote channels
         * explicitly requested by the method caller and their respective local channels.
         */
        ColibriConferenceIQ conferenceResult = new ColibriConferenceIQ();
        conferenceResult.setFrom(colibri.getFrom());
        conferenceResult.setID(conferenceResponseID);

        for (Map.Entry<JingleContent, JingleContent> e : contentMap.entrySet()) {
            JingleContent localContent = e.getKey();
            JingleContent remoteContent = e.getValue();
            JingleContent cpe = (remoteContent == null) ? localContent : remoteContent;
            MediaType mediaType = JingleUtils.getMediaType(cpe);
            ColibriConferenceIQ.Content contentResponse = conferenceResponse.getContent(mediaType.toString());
            if (contentResponse != null) {
                String contentName = contentResponse.getName();
                ColibriConferenceIQ.Content contentResult = new ColibriConferenceIQ.Content(contentName);
                conferenceResult.addContent(contentResult);

                /*
                 * The local channel may have been allocated in a previous method call as part of
                 * the allocation of the first remote channel in the respective content. Anyway,
                 * the current method caller still needs to know about it.
                 */
                ColibriConferenceIQ.Content content = colibri.getContent(contentName);
                ColibriConferenceIQ.Channel localChannel = null;

                if ((content != null) && (content.getChannelCount() > 0)) {
                    localChannel = content.getChannel(0);
                    contentResult.addChannel(localChannel);
                }

                String localChannelID = (localChannel == null) ? null : localChannel.getID();

                for (ColibriConferenceIQ.Channel channelResponse : contentResponse.getChannels()) {
                    if ((localChannelID == null) || !localChannelID.equals(channelResponse.getID()))
                        contentResult.addChannel(channelResponse);
                }
            }
        }
        return conferenceResult;
    }

    /**
     * Initializes a <code>ColibriStreamConnector</code> on behalf of a specific <code>CallPeer</code> to be used
     * in association with a specific <code>ColibriConferenceIQ.Channel</code> of a specific <code>MediaType</code>.
     *
     * @param peer the <code>CallPeer</code> which requests the initialization of a <code>ColibriStreamConnector</code>
     * @param mediaType the <code>MediaType</code> of the stream which is to use the initialized
     * <code>ColibriStreamConnector</code> for RTP and RTCP traffic
     * @param channel the <code>ColibriConferenceIQ.Channel</code> to which RTP and RTCP traffic is to be sent
     * and from which such traffic is to be received via the initialized <code>ColibriStreamConnector</code>
     * @param factory a <code>StreamConnectorFactory</code> implementation which is to allocate the sockets to
     * be used for RTP and RTCP traffic
     * @return a <code>ColibriStreamConnector</code> to be used for RTP and RTCP traffic associated
     * with the specified <code>channel</code>
     */
    public ColibriStreamConnector createColibriStreamConnector(CallPeerJabberImpl peer,
            MediaType mediaType, ColibriConferenceIQ.Channel channel, StreamConnectorFactory factory)
    {
        String channelID = channel.getID();
        if (channelID == null)
            throw new IllegalArgumentException("channel");

        if (colibri == null)
            throw new IllegalStateException("colibri");

        ColibriConferenceIQ.Content content = colibri.getContent(mediaType.toString());

        if (content == null)
            throw new IllegalArgumentException("mediaType");
        if ((content.getChannelCount() < 1)
                || !channelID.equals((channel = content.getChannel(0)).getID()))
            throw new IllegalArgumentException("channel");

        ColibriStreamConnector colibriStreamConnector;

        synchronized (colibriStreamConnectors) {
            int index = mediaType.ordinal();
            WeakReference<ColibriStreamConnector> weakReference = colibriStreamConnectors.get(index);

            colibriStreamConnector = (weakReference == null) ? null : weakReference.get();
            if (colibriStreamConnector == null) {
                StreamConnector streamConnector = factory.createStreamConnector();

                if (streamConnector != null) {
                    colibriStreamConnector = new ColibriStreamConnector(streamConnector);
                    colibriStreamConnectors.set(index, new WeakReference<>(colibriStreamConnector));
                }
            }
        }
        return colibriStreamConnector;
    }

    /**
     * Expires specific (colibri) conference channels used by a specific <code>CallPeer</code>.
     *
     * @param peer the <code>CallPeer</code> which uses the specified (colibri) conference channels to be expired
     * @param conference a <code>ColibriConferenceIQ</code> which specifies the (colibri) conference channels to be expired
     */
    public void expireColibriChannels(CallPeerJabberImpl peer, ColibriConferenceIQ conference)
            throws SmackException.NotConnectedException, InterruptedException
    {
        // Formulate the ColibriConferenceIQ request which is to be sent.
        if (colibri != null) {
            String conferenceID = colibri.getID();

            if (conferenceID.equals(conference.getID())) {
                ColibriConferenceIQ conferenceRequest = new ColibriConferenceIQ();
                conferenceRequest.setID(conferenceID);

                for (ColibriConferenceIQ.Content content : conference.getContents()) {
                    ColibriConferenceIQ.Content colibriContent = colibri.getContent(content.getName());
                    if (colibriContent != null) {
                        ColibriConferenceIQ.Content contentRequest
                                = conferenceRequest.getOrCreateContent(colibriContent.getName());

                        for (ColibriConferenceIQ.Channel channel : content.getChannels()) {
                            ColibriConferenceIQ.Channel colibriChannel
                                    = colibriContent.getChannel(channel.getID());

                            if (colibriChannel != null) {
                                ColibriConferenceIQ.Channel channelRequest = new ColibriConferenceIQ.Channel();
                                channelRequest.setExpire(0);
                                channelRequest.setID(colibriChannel.getID());
                                contentRequest.addChannel(channelRequest);
                            }
                        }
                    }
                }

                /*
                 * Remove the channels which are to be expired from the internal state of the
                 * conference managed by this CallJabberImpl.
                 */
                for (ColibriConferenceIQ.Content contentRequest : conferenceRequest.getContents()) {
                    ColibriConferenceIQ.Content colibriContent = colibri.getContent(contentRequest.getName());

                    for (ColibriConferenceIQ.Channel channelRequest : contentRequest.getChannels()) {
                        ColibriConferenceIQ.Channel colibriChannel
                                = colibriContent.getChannel(channelRequest.getID());

                        colibriContent.removeChannel(colibriChannel);

                        /*
                         * If the last remote channel is to be expired, expire the local channel as well.
                         */
                        if (colibriContent.getChannelCount() == 1) {
                            colibriChannel = colibriContent.getChannel(0);

                            channelRequest = new ColibriConferenceIQ.Channel();
                            channelRequest.setExpire(0);
                            channelRequest.setID(colibriChannel.getID());
                            contentRequest.addChannel(channelRequest);
                            colibriContent.removeChannel(colibriChannel);
                            break;
                        }
                    }
                }

                /*
                 * At long last, send the ColibriConferenceIQ request to expire the channels.
                 */
                conferenceRequest.setTo(colibri.getFrom());
                conferenceRequest.setType(IQ.Type.set);
                getProtocolProvider().getConnection().sendStanza(conferenceRequest);
            }
        }
    }

    /**
     * Sends a <code>ColibriConferenceIQ</code> to the videobridge used by this <code>CallJabberImpl</code>,
     * in order to request the the direction of the <code>channel</code> with ID <code>channelID</code> be
     * set to <code>direction</code>
     *
     * @param channelID the ID of the <code>channel</code> for which to set the direction.
     * @param mediaType the <code>MediaType</code> of the channel (we can deduce this by searching the
     * <code>ColibriConferenceIQ</code>, but it's more convenient to have it)
     * @param direction the <code>MediaDirection</code> to set.
     */
    public void setChannelDirection(String channelID, MediaType mediaType, MediaDirection direction)
            throws SmackException.NotConnectedException, InterruptedException
    {
        if ((colibri != null) && (channelID != null)) {
            ColibriConferenceIQ.Content content = colibri.getContent(mediaType.toString());

            if (content != null) {
                ColibriConferenceIQ.Channel channel = content.getChannel(channelID);

                /*
                 * Note that we send requests even when the local Channel's direction and the
                 * direction we are setting are the same. We can easily avoid this, but we risk not
                 * sending necessary packets if local Channel and the actual channel on the
                 * videobridge are out of sync.
                 */
                if (channel != null) {
                    ColibriConferenceIQ.Channel requestChannel = new ColibriConferenceIQ.Channel();

                    requestChannel.setID(channelID);
                    requestChannel.setDirection(direction);

                    ColibriConferenceIQ.Content requestContent = new ColibriConferenceIQ.Content();

                    requestContent.setName(mediaType.toString());
                    requestContent.addChannel(requestChannel);

                    ColibriConferenceIQ conferenceRequest = new ColibriConferenceIQ();

                    conferenceRequest.setID(colibri.getID());
                    conferenceRequest.setTo(colibri.getFrom());
                    conferenceRequest.setType(IQ.Type.set);
                    conferenceRequest.addContent(requestContent);

                    getProtocolProvider().getConnection().sendStanza(conferenceRequest);
                }
            }
        }
    }

    /**
     * Creates a <code>CallPeerJabberImpl</code> from <code>calleeJID</code> and sends them <code>session-initiate</code> IQ request.
     *
     * @param calleeJid the party that we would like to invite to this call.
     * @param discoverInfo any discovery information that we have for the jid we are trying to reach and
     * that we are passing in order to avoid having to ask for it again.
     * @param sessionInitiateExtensions a collection of additional and optional <code>ExtensionElement</code>s to be
     * added to the <code>session-initiate</code> {@link Jingle} which is to init this <code>CallJabberImpl</code>
     * @param supportedTransports the XML namespaces of the jingle transports to use.
     * @return the newly created <code>CallPeerJabberImpl</code> corresponding to <code>calleeJID</code>.
     * All following state change events will be delivered through this call peer.
     * @throws OperationFailedException with the corresponding code if we fail to create the call.
     */
    public CallPeerJabberImpl initiateSession(FullJid calleeJid, DiscoverInfo discoverInfo,
            Iterable<ExtensionElement> sessionInitiateExtensions, Collection<String> supportedTransports)
            throws OperationFailedException
    {
        // create the session-initiate IQ
        CallPeerJabberImpl callPeer = new CallPeerJabberImpl(calleeJid, this);
        callPeer.setDiscoveryInfo(discoverInfo);
        addCallPeer(callPeer);

        callPeer.setState(CallPeerState.INITIATING_CALL);

        // If this is the first peer we added in this call, then the call is new;
        // then we need to notify everyone of its creation.
        if (getCallPeerCount() == 1)
            parentOpSet.fireCallEvent(CallEvent.CALL_INITIATED, this);

        mMediaHandler = callPeer.getMediaHandler();

        // set the supported transports before the transport manager is being created
        mMediaHandler.setSupportedTransports(supportedTransports);

        /* enable video if it is a video call */
        mMediaHandler.setLocalVideoTransmissionEnabled(localVideoAllowed);

        /* enable remote-control if it is a desktop sharing session - cmeng: get and set back???*/
        //  mMediaHandler.setLocalInputEvtAware(mMediaHandler.getLocalInputEvtAware());

        /*
         * Set call state to connecting so that the user interface would start playing the tones.
         * We do that here because we may be harvesting STUN/TURN addresses in initiateSession()
         * which would take a while.
         */
        callPeer.setState(CallPeerState.CONNECTING);

        /*
         * If the call is init from JingleMessage propose, then JingleMessage id must be used in
         * Jingle session-initiate stanza creation, otherwise the callee will reject the call.
         */
        String id = mJingleCallIds.get(calleeJid);
        if (id == null) {
            id = Jingle.generateSid();
        }
        else {
            // Remove the value after its use
            mJingleCallIds.remove(calleeJid);
        }

        // if initializing session fails, set peer to failed by default
        boolean sessionInitiated = false;
        try {
            callPeer.initiateSession(sessionInitiateExtensions, id);
            sessionInitiated = true;
        } finally {
            // if initialization throws an exception
            if (!sessionInitiated)
                callPeer.setState(CallPeerState.FAILED);
        }
        return callPeer;
    }

    /**
     * Set sid value in Jingle Message proceed stage, use for next session-initiate stanza sending
     * must use the same sid, otherwise callee will reject the call setup
     *
     * @param jid Callee Full Jid
     * @param id to be used for next session-initiate sid
     * @see JingleMessageHelper#onJingleMessageProceed(XMPPConnection, JingleMessage, Message)
     */
    public static void setJingleCallId(Jid jid, String id)
    {
        mJingleCallIds.put(jid, id);
    }

    /**
     * Updates the Jingle sessions for the <code>CallPeer</code>s of this <code>Call</code>, to reflect the
     * current state of the video contents of this <code>Call</code>. Sends a <code>content-modify</code>,
     * <code>content-add</code> or <code>content-remove</code> message to each of the current <code>CallPeer</code>s.
     *
     * cmeng (20210321): Approach aborted due to complexity and NewReceiveStreamEvent not alway gets triggered:
     * - content-remove/content-add are not used on device orientation changed - use blocking impl is aborted due
     * to its complexity.
     * - @see CallPeerJabberImpl#getDirectionForJingle(MediaType)
     *
     * @throws OperationFailedException if a problem occurred during message generation or there was a network problem
     */
    public void modifyVideoContent()
            throws OperationFailedException
    {
        Timber.d("Updating video content for %s", this);
        boolean change = false;
        for (CallPeerJabberImpl peer : getCallPeerList()) {
            try {
                // cmeng (2016/09/14): Never send 'sendModifyVideoContent' before it is connected => Smack Exception
                if (peer.getState() == CallPeerState.CONNECTED)
                    change |= peer.sendModifyVideoContent();
            } catch (SmackException.NotConnectedException | InterruptedException e) {
                throw new OperationFailedException("Could send modify video content to " + peer.getAddress(), 0, e);
            }
        }
        if (change)
            fireCallChangeEvent(CallChangeEvent.CALL_PARTICIPANTS_CHANGE, null, null);
    }

    /**
     * Notifies this instance that a specific <code>ColibriConferenceIQ</code> has been received.
     *
     * @param conferenceIQ the <code>ColibriConferenceIQ</code> which has been received
     * @return <code>true</code> if the specified <code>conferenceIQ</code> was processed by this instance
     * and no further processing is to be performed by other possible processors of
     * <code>ColibriConferenceIQ</code>s; otherwise, <code>false</code>. Because a
     * <code>ColibriConferenceIQ</code> request sent from the Jitsi Videobridge server to the
     * application as its client concerns a specific <code>CallJabberImpl</code> implementation,
     * no further processing by other <code>CallJabberImpl</code> instances is necessary once
     * the <code>ColibriConferenceIQ</code> is processed by the associated <code>CallJabberImpl</code> instance.
     */
    boolean processColibriConferenceIQ(ColibriConferenceIQ conferenceIQ)
    {
        if (colibri == null) {
            /*
             * This instance has not set up any conference using the Jitsi Videobridge server-side
             * technology yet so it cannot be bothered with related requests.
             */
            return false;
        }
        else if (conferenceIQ.getID().equals(colibri.getID())) {
            /*
             * Remove the local Channels (from the specified conferenceIQ) i.e. the Channels on
             * which the local peer/user is sending to the Jitsi Videobridge server because they
             * concern this Call only and not its CallPeers.
             */
            for (MediaType mediaType : MediaType.values()) {
                String contentName = mediaType.toString();
                ColibriConferenceIQ.Content content = conferenceIQ.getContent(contentName);

                if (content != null) {
                    ColibriConferenceIQ.Content thisContent = colibri.getContent(contentName);
                    if ((thisContent != null) && (thisContent.getChannelCount() > 0)) {
                        ColibriConferenceIQ.Channel thisChannel = thisContent.getChannel(0);
                        ColibriConferenceIQ.Channel channel = content.getChannel(thisChannel.getID());
                        if (channel != null)
                            content.removeChannel(channel);
                    }
                }
            }
            for (CallPeerJabberImpl callPeer : getCallPeerList())
                callPeer.processColibriConferenceIQ(conferenceIQ);

            /*
             * We have removed the local Channels from the specified conferenceIQ. Consequently, it
             * is no longer the same and fit for processing by other CallJabberImpl instances.
             */
            return true;
        }
        else {
            /*
             * This instance has set up a conference using the Jitsi Videobridge server-side
             * technology but it is not the one referred to by the specified conferenceIQ i.e. the
             * specified conferenceIQ does not concern this instance.
             */
            return false;
        }
    }

    /**
     * Creates a new call peer upon receiving session-initiate, and sends a RINGING response if required.
     *
     * Handle addCallPeer() in caller to avoid race condition as here is handled on new thread
     * - with transport-info sent separately
     *
     * @param jingle the {@link Jingle} that created the session.
     * @param callPeer the {@link CallPeerJabberImpl}: the one that sent the INVITE.
     * @see OperationSetBasicTelephonyJabberImpl#processJingleSynchronize(Jingle)
     */
    public void processSessionInitiate(Jingle jingle, final CallPeerJabberImpl callPeer)
    {
        /* cmeng (20200528): Must handle addCallPeer() in caller to handle transport-info sent separately */
        // FullJid remoteParty = jingle.getFrom().asFullJidIfPossible();
        // CallPeerJabberImpl callPeer = new CallPeerJabberImpl(remoteParty, this, jingle);
        // addCallPeer(callPeer);

        boolean autoAnswer = false;
        CallPeerJabberImpl attendant = null;
        OperationSetBasicTelephonyJabberImpl basicTelephony = null;

        /*
         * We've already sent the ack to the specified session-initiate so if it has been
         * sent as part of an attended transfer, we have to hang up on the attendant.
         */
        try {
            SdpTransfer transfer = jingle.getExtension(SdpTransfer.class);
            if (transfer != null) {
                String sid = transfer.getSid();

                if (sid != null) {
                    ProtocolProviderServiceJabberImpl protocolProvider = getProtocolProvider();
                    basicTelephony = (OperationSetBasicTelephonyJabberImpl)
                            protocolProvider.getOperationSet(OperationSetBasicTelephony.class);
                    CallJabberImpl attendantCall = basicTelephony.getActiveCallsRepository().findSID(sid);

                    if (attendantCall != null) {
                        attendant = attendantCall.getPeer(sid);
                        if ((attendant != null)
                                && basicTelephony.getFullCalleeURI(attendant.getPeerJid()).equals(transfer.getFrom())
                                && protocolProvider.getOurJID().equals(transfer.getTo())) {
                            // basicTelephony.hangupCallPeer(attendant);
                            autoAnswer = true;
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Timber.e(t, "Failed to hang up on attendant as part of session transfer");

            if (t instanceof ThreadDeath)
                throw (ThreadDeath) t;
        }

        CoinExtension coin = jingle.getExtension(CoinExtension.class);
        if (coin != null) {
            callPeer.setConferenceFocus(coin.isFocus());
        }

        // before notifying about this incoming call, make sure the session-initiate looks alright
        try {
            callPeer.processSessionInitiate(jingle);
        } catch (SmackException.NotConnectedException | InterruptedException e) {
            callPeer.setState(CallPeerState.INCOMING_CALL);
            return;
        }

        // if paranoia is set, to accept the call we need to know that the other party has support for media encryption
        if (getProtocolProvider().getAccountID().getAccountPropertyBoolean(ProtocolProviderFactory.MODE_PARANOIA, false)
                && callPeer.getMediaHandler().getAdvertisedEncryptionMethods().length == 0) {
            // send an error response;
            String reasonText = aTalkApp.getResString(R.string.service_gui_security_ENCRYPTION_REQUIRED);
            JingleUtil jingleUtil = new JingleUtil(mConnection);
            Jingle errResp = jingleUtil.createSessionTerminate(
                    jingle.getInitiator(), jingle.getSid(), JingleReason.Reason.security_error, reasonText);

            callPeer.setState(CallPeerState.FAILED, reasonText);
            try {
                getProtocolProvider().getConnection().sendStanza(errResp);
            } catch (NotConnectedException | InterruptedException e) {
                Timber.e(e, "Could not send session terminate");
            }
            return;
        }

        if (callPeer.getState() == CallPeerState.FAILED)
            return;
        callPeer.setState(CallPeerState.INCOMING_CALL);

        // in case of attended transfer, auto answer the call
        if (autoAnswer) {
            /* answer directly */
            try {
                callPeer.answer();
            } catch (Exception e) {
                Timber.i(e, "Exception occurred while answer transferred call");
            }

            // hang up now
            try {
                basicTelephony.hangupCallPeer(attendant);
            } catch (OperationFailedException e) {
                Timber.e(e, "Failed to hang up on attendant as part of session transfer");
            }
            return;
        }

        /*
         * see if offer contains audio and video so that we can propose option to the user (i.e.
         * answer with video if it is a video call...)
         */
        List<JingleContent> offer = callPeer.getSessionIQ().getContents();
        Map<MediaType, MediaDirection> directions = new HashMap<>();

        directions.put(MediaType.AUDIO, MediaDirection.INACTIVE);
        directions.put(MediaType.VIDEO, MediaDirection.INACTIVE);

        for (JingleContent c : offer) {
            String mediaType = c.getFirstChildElement(RtpDescription.class).getMedia();
            MediaDirection remoteDirection = JingleUtils.getDirection(c, callPeer.isInitiator());

            if (MediaType.AUDIO.toString().equals(mediaType))
                directions.put(MediaType.AUDIO, remoteDirection);
            else if (MediaType.VIDEO.toString().equals(mediaType))
                directions.put(MediaType.VIDEO, remoteDirection);
        }

        // If this was the first peer we added in this call, then the call is new,
        // and we need to notify everyone of its creation.
        if (getCallPeerCount() == 1) {
            parentOpSet.fireCallEvent(CallEvent.CALL_RECEIVED, this, directions);
        }

        // Manages auto answer with "audio only", or "audio/video" answer.
        OperationSetAutoAnswerJabberImpl autoAnswerOpSet = (OperationSetAutoAnswerJabberImpl)
                getProtocolProvider().getOperationSet(OperationSetBasicAutoAnswer.class);

        if (autoAnswerOpSet != null) {
            autoAnswerOpSet.autoAnswer(this, directions, jingle);
        }
    }

    /**
     * Updates the state of the local DTLS-SRTP endpoint (i.e. the local <code>DtlsControl</code>
     * instance) from the state of the remote DTLS-SRTP endpoint represented by a specific
     * <code>ColibriConferenceIQ.Channel</code>.
     *
     * @param peer the <code>CallPeer</code> associated with the method invocation
     * @param channel the <code>ColibriConferenceIQ.Channel</code> which represents the state of the remote
     * DTLS-SRTP endpoint
     * @param mediaType the <code>MediaType</code> of the media to be transmitted over the DTLS-SRTP session
     */
    private boolean addDtlsAdvertisedEncryptions(CallPeerJabberImpl peer,
            ColibriConferenceIQ.Channel channel, MediaType mediaType)
    {
        CallPeerMediaHandlerJabberImpl peerMediaHandler = peer.getMediaHandler();
        DtlsControl dtlsControl
                = (DtlsControl) peerMediaHandler.getSrtpControls().get(mediaType, SrtpControlType.DTLS_SRTP);

        if (dtlsControl != null) {
            dtlsControl.setSetup(peer.isInitiator() ? DtlsControl.Setup.ACTIVE : DtlsControl.Setup.ACTPASS);
        }
        IceUdpTransport remoteTransport = channel.getTransport();
        return peerMediaHandler.addDtlsSrtpAdvertisedEncryption(true, remoteTransport, mediaType, false);
    }

    /**
     * Updates the state of the remote DTLS-SRTP endpoint represented by a specific
     * <code>ColibriConferenceIQ.Channel</code> from the state of the local DTLS-SRTP endpoint. The
     * specified <code>channel</code> is to be used by the conference focus for the purposes of
     * transmitting media between a remote peer and the Jitsi Videobridge server.
     *
     * @param mediaType the <code>MediaType</code> of the media to be transmitted over the DTLS-SRTP session
     * @param localContent the <code>JingleContent</code> of the local peer in the negotiation between the
     * local and the remote peers. If <code>remoteContent</code> is <code>null</code>, represents an
     * offer from the local peer to the remote peer; otherwise, represents an answer from the
     * local peer to an offer from the remote peer.
     * @param remoteContent the <code>JingleContent</code>, if any, of the remote peer in the negotiation
     * between the local and the remote peers. If <code>null</code>, <code>localContent</code>
     * represents an offer from the local peer to the remote peer; otherwise,
     * <code>localContent</code> represents an answer from the local peer to an offer from the remote peer
     * @param peer the <code>CallPeer</code> which represents the remote peer and which is associated with
     * the specified <code>channel</code>
     * @param channel the <code>ColibriConferenceIQ.Channel</code> which represents the state of the remote
     * DTLS-SRTP endpoint.
     */
    private void setDtlsEncryptionOnChannel(MediaType mediaType, JingleContent localContent, JingleContent remoteContent,
            CallPeerJabberImpl peer, ColibriConferenceIQ.Channel channel)
    {
        AccountID accountID = getProtocolProvider().getAccountID();

        if (accountID.getAccountPropertyBoolean(ProtocolProviderFactory.DEFAULT_ENCRYPTION, true)
                && accountID.isEncryptionProtocolEnabled(SrtpControlType.DTLS_SRTP)
                && (remoteContent != null)) {
            IceUdpTransport remoteTransport = remoteContent.getFirstChildElement(IceUdpTransport.class);

            if (remoteTransport != null) {
                List<SrtpFingerprint> remoteFingerprints = remoteTransport.getChildElements(SrtpFingerprint.class);

                if (!remoteFingerprints.isEmpty()) {
                    IceUdpTransport localTransport = ensureTransportOnChannel(channel, peer);
                    if (localTransport != null) {
                        List<SrtpFingerprint> localFingerprints = localTransport.getChildElements(SrtpFingerprint.class);

                        if (localFingerprints.isEmpty()) {
                            for (SrtpFingerprint fingerprint : remoteFingerprints) {
                                localTransport.addChildElement(SrtpFingerprint.builder()
                                        .setFingerprint(fingerprint.getFingerprint())
                                        .setHash(fingerprint.getHash())
                                        .setSetup(fingerprint.getSetup())
                                        .build()
                                );
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Updates the state of the remote DTLS-SRTP endpoint represented by a specific
     * <code>ColibriConferenceIQ.Channel</code> from the state of the local DTLS-SRTP endpoint (i.e.
     * the local <code>DtlsControl</code> instance). The specified <code>channel</code> is to be used by the
     * conference focus for the purposes of transmitting media between the local peer and the Jitsi
     * Videobridge server.
     *
     * @param jvb the address/JID of the Jitsi Videobridge
     * @param peer the <code>CallPeer</code> associated with the method invocation
     * @param mediaType the <code>MediaType</code> of the media to be transmitted over the DTLS-SRTP session
     * @param channel the <code>ColibriConferenceIQ.Channel</code> which represents the state of the remote
     * DTLS-SRTP endpoint.
     */
    private void setDtlsEncryptionOnChannel(Jid jvb, CallPeerJabberImpl peer,
            MediaType mediaType, ColibriConferenceIQ.Channel channel)
    {
        ProtocolProviderServiceJabberImpl protocolProvider = getProtocolProvider();
        AccountID accountID = protocolProvider.getAccountID();

        if (accountID.getAccountPropertyBoolean(ProtocolProviderFactory.DEFAULT_ENCRYPTION, true)
                && accountID.isEncryptionProtocolEnabled(SrtpControlType.DTLS_SRTP)
                && protocolProvider.isFeatureSupported(jvb,
                ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE_DTLS_SRTP)) {
            CallPeerMediaHandlerJabberImpl mediaHandler = peer.getMediaHandler();
            DtlsControl dtlsControl
                    = (DtlsControl) mediaHandler.getSrtpControls().getOrCreate(mediaType, SrtpControlType.DTLS_SRTP, null);

            if (dtlsControl != null) {
                IceUdpTransport transport = ensureTransportOnChannel(channel, peer);
                if (transport != null)
                    setDtlsEncryptionOnTransport(dtlsControl, transport);
            }
        }
    }

    /**
     * Sets the properties (i.e. fingerprint and hash function) of a specific <code>DtlsControl</code>
     * on the specific <code>IceUdpTransport</code>.
     *
     * @param dtlsControl the <code>DtlsControl</code> the properties of which are to be set on the specified
     * <code>localTransport</code>
     * @param localTransport the <code>IceUdpTransport</code> on which the properties of the specified
     * <code>dtlsControl</code> are to be set
     */
    static void setDtlsEncryptionOnTransport(DtlsControl dtlsControl, IceUdpTransport localTransport)
    {
        String fingerprint = dtlsControl.getLocalFingerprint();
        String hash = dtlsControl.getLocalFingerprintHashFunction();
        String setup = ((DtlsControlImpl) dtlsControl).getSetup().toString();

        localTransport.addChildElement(SrtpFingerprint.builder()
                .setFingerprint(fingerprint)
                .setHash(hash)
                .setSetup(setup)
                .build()
        );
    }

    private void setTransportOnChannel(CallPeerJabberImpl peer, String media, ColibriConferenceIQ.Channel channel)
            throws OperationFailedException
    {
        ExtensionElement transport = peer.getMediaHandler().getTransportManager().createTransport(media);
        if (transport instanceof IceUdpTransport)
            channel.setTransport((IceUdpTransport) transport);
    }

    private void setTransportOnChannel(String media, JingleContent localContent,
            JingleContent remoteContent, CallPeerJabberImpl peer, ColibriConferenceIQ.Channel channel)
            throws OperationFailedException
    {
        if (remoteContent != null) {
            IceUdpTransport transport = remoteContent.getFirstChildElement(IceUdpTransport.class);
            channel.setTransport(TransportManagerJabberImpl.cloneTransportAndCandidates(transport));
        }
    }

    /**
     * Makes an attempt to ensure that a specific <code>ColibriConferenceIQ.Channel</code> has a non-
     * <code>null</code> <code>transport</code> set. If the specified <code>channel</code> does not have a
     * <code>transport</code>, the method invokes the <code>TransportManager</code> of the specified
     * <code>CallPeerJabberImpl</code> to initialize a new <code>ExtensionElement</code>.
     *
     * @param channel the <code>ColibriConferenceIQ.Channel</code> to ensure the <code>transport</code> on
     * @param peer the <code>CallPeerJabberImpl</code> which is associated with the specified
     * <code>channel</code> and which specifies the <code>TransportManager</code> to be described in
     * the specified <code>channel</code>
     * @return the <code>transport</code> of the specified <code>channel</code>
     */
    private IceUdpTransport ensureTransportOnChannel(ColibriConferenceIQ.Channel channel, CallPeerJabberImpl peer)
    {
        IceUdpTransport transport = channel.getTransport();

        if (transport == null) {
            ExtensionElement pe = peer.getMediaHandler().getTransportManager().createTransportPacketExtension();
            if (pe instanceof IceUdpTransport) {
                transport = (IceUdpTransport) pe;
                channel.setTransport(transport);
            }
        }
        return transport;
    }

    /**
     * Gets the entity ID of the Jitsi Videobridge to be utilized by this <code>Call</code> for the
     * purposes of establishing a server-assisted telephony conference.
     *
     * @return the entity ID of the Jitsi Videobridge to be utilized by this <code>Call</code> for the
     * purposes of establishing a server-assisted telephony conference.
     */
    public Jid getJitsiVideobridge()
    {
        if ((mJitsiVideobridge == null) && getConference().isJitsiVideobridge()) {
            Jid jvb = getProtocolProvider().getJitsiVideobridge();

            if (jvb != null)
                mJitsiVideobridge = jvb;
        }
        return mJitsiVideobridge;
    }

    /**
     * {@inheritDoc}
     *
     * Implements {@link net.java.sip.communicator.service.protocol.event.DTMFListener
     * #toneReceived(net.java.sip.communicator.service.protocol.event.DTMFReceivedEvent)}
     *
     * Forwards DTMF events to the <code>IncomingDTMF</code> operation set, setting this <code>Call</code> as the source.
     */
    @Override
    public void toneReceived(DTMFReceivedEvent evt)
    {
        OperationSetIncomingDTMF opSet = getProtocolProvider().getOperationSet(OperationSetIncomingDTMF.class);
        if (opSet instanceof OperationSetIncomingDTMFJabberImpl) {
            // Re-fire the event using this Call as the source.
            ((OperationSetIncomingDTMFJabberImpl) opSet).toneReceived(new DTMFReceivedEvent(this,
                    evt.getValue(), evt.getDuration(), evt.getStart()));
        }
    }

    /**
     * Enable or disable <code>inputevt</code> support (remote control).
     *
     * @param enable new state of inputevt support
     */
    public void setLocalInputEvtAware(boolean enable)
    {
        localInputEvtAware = enable;
    }

    /**
     * Returns if the call support <code>inputevt</code> (remote control).
     *
     * @return true if the call support <code>inputevt</code>, false otherwise
     */
    public boolean getLocalInputEvtAware()
    {
        return localInputEvtAware;
    }

    /**
     * Returns the peer whose corresponding session has the specified <code>sid</code>.
     *
     * @param sid the ID of the session whose peer we are looking for.
     * @return the {@link CallPeerJabberImpl} with the specified jingle
     * <code>sid</code> and <code>null</code> if no such peer exists in this call.
     */
    public CallPeerJabberImpl getPeer(String sid)
    {
        if (sid == null)
            return null;

        for (CallPeerJabberImpl peer : getCallPeerList()) {
            if (sid.equals(peer.getSid()))
                return peer;
        }
        return null;
    }

    /**
     * Determines if this call contains a peer whose corresponding session has the specified <code>sid</code>.
     *
     * @param sid the ID of the session whose peer we are looking for.
     * @return <code>true</code> if this call contains a peer with the specified jingle <code>sid</code> and false otherwise.
     */
    public boolean containsSID(String sid)
    {
        return (getPeer(sid) != null);
    }

    /**
     * Returns the peer whose corresponding session-initiate ID has the specified <code>id</code>.
     *
     * @param id the ID of the session-initiate IQ whose peer we are looking for.
     * @return the {@link CallPeerJabberImpl} with the specified IQ
     * <code>id</code> and <code>null</code> if no such peer exists in this call.
     */
    public CallPeerJabberImpl getPeerBySessInitPacketID(String id)
    {
        if (id == null)
            return null;

        for (CallPeerJabberImpl peer : getCallPeerList()) {
            if (id.equals(peer.getSessInitID()))
                return peer;
        }
        return null;
    }
}
