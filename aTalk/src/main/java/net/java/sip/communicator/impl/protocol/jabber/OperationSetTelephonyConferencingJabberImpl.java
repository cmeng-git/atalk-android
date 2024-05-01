/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import java.util.Arrays;
import java.util.Iterator;

import net.java.sip.communicator.service.protocol.Call;
import net.java.sip.communicator.service.protocol.CallPeer;
import net.java.sip.communicator.service.protocol.CallPeerState;
import net.java.sip.communicator.service.protocol.CallState;
import net.java.sip.communicator.service.protocol.ChatRoom;
import net.java.sip.communicator.service.protocol.ConferenceDescription;
import net.java.sip.communicator.service.protocol.OperationFailedException;
import net.java.sip.communicator.service.protocol.OperationSetMultiUserChat;
import net.java.sip.communicator.service.protocol.OperationSetVideoBridge;
import net.java.sip.communicator.service.protocol.RegistrationState;
import net.java.sip.communicator.service.protocol.event.CallChangeEvent;
import net.java.sip.communicator.service.protocol.event.CallChangeListener;
import net.java.sip.communicator.service.protocol.event.CallPeerEvent;
import net.java.sip.communicator.service.protocol.event.RegistrationStateChangeEvent;
import net.java.sip.communicator.service.protocol.event.RegistrationStateChangeListener;
import net.java.sip.communicator.service.protocol.media.AbstractOperationSetTelephonyConferencing;
import net.java.sip.communicator.service.protocol.media.ConferenceInfoDocument;
import net.java.sip.communicator.service.protocol.media.MediaAwareCallConference;
import net.java.sip.communicator.service.protocol.media.MediaAwareCallPeer;

import org.atalk.util.xml.XMLException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.filter.StanzaFilter;
import org.jivesoftware.smack.iqrequest.AbstractIqRequestHandler;
import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.IQ.Type;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.packet.StanzaError;
import org.jivesoftware.smack.util.XmlStringBuilder;
import org.jivesoftware.smackx.coin.CoinExtension;
import org.jivesoftware.smackx.coin.CoinIQ;
import org.jivesoftware.smackx.disco.packet.DiscoverInfo;
import org.jivesoftware.smackx.jingle.JingleManager;
import org.jivesoftware.smackx.jingle.element.Jingle;
import org.jxmpp.jid.BareJid;
import org.jxmpp.jid.EntityFullJid;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;

import timber.log.Timber;

/**
 * Implements <code>OperationSetTelephonyConferencing</code> for Jabber.
 *
 * @author Lyubomir Marinov
 * @author Sebastien Vincent
 * @author Boris Grozev
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class OperationSetTelephonyConferencingJabberImpl
        extends AbstractOperationSetTelephonyConferencing<ProtocolProviderServiceJabberImpl,
        OperationSetBasicTelephonyJabberImpl, CallJabberImpl, CallPeerJabberImpl, String>
        implements RegistrationStateChangeListener, StanzaFilter {
    /**
     * The Jabber Incoming Conference Call IQRequest Handler.
     */
    private IQRequestHandler iqRequestHandler = null;
    /**
     * The minimum interval in milliseconds between COINs sent to a single <code>CallPeer</code>.
     */
    private static final int COIN_MIN_INTERVAL = 200;

    /**
     * Property used to disable COIN notifications.
     */
    public static final String DISABLE_COIN_PROP_NAME = "protocol.jabber.DISABLE_COIN";

    /**
     * Synchronization object.
     */
    private final Object lock = new Object();

    /**
     * Field indicates whether COIN notification are disabled or not.
     */
    private boolean isCoinDisabled;

    /**
     * Initializes a new <code>OperationSetTelephonyConferencingJabberImpl</code> instance which is to
     * provide telephony conferencing services for the specified Jabber
     * <code>ProtocolProviderService</code> implementation.
     *
     * @param parentProvider the Jabber <code>ProtocolProviderService</code> implementation which has requested the
     * creation of the new instance and for which the new instance is to provide telephony
     * conferencing services
     */
    public OperationSetTelephonyConferencingJabberImpl(ProtocolProviderServiceJabberImpl parentProvider) {
        super(parentProvider);
        this.mPPS.addRegistrationStateChangeListener(this);
        this.isCoinDisabled = JabberActivator.getConfigurationService().getBoolean(DISABLE_COIN_PROP_NAME, false);
    }

    /**
     * Implementation of method <code>registrationStateChange</code> from interface
     * RegistrationStateChangeListener for setting up (or down) our <code>JingleManager</code> when an
     * <code>XMPPConnection</code> is available
     *
     * @param evt the event received
     */
    @Override
    public void registrationStateChanged(RegistrationStateChangeEvent evt) {
        super.registrationStateChanged(evt);

        RegistrationState registrationState = evt.getNewState();
        if (RegistrationState.REGISTERED.equals(registrationState)) {
            subscribeForCoinPackets();
            Timber.d("Subscribes to Coin packets");
        }
        else if (RegistrationState.UNREGISTERED.equals(registrationState)) {
            unsubscribeForCoinPackets();
            Timber.d("Unsubscribes to Coin packets");
        }
    }

    /**
     * Notifies all <code>CallPeer</code>s associated with a specific <code>Call</code> about changes in the
     * telephony conference-related information. In contrast, {@link #notifyAll()} notifies all
     * <code>CallPeer</code>s associated with the telephony conference in which a specific <code>Call</code>
     * is participating.
     *
     * @param call the <code>Call</code> whose <code>CallPeer</code>s are to be notified about changes in the
     * telephony conference-related information
     */
    @Override
    protected void notifyCallPeers(Call call) {
        if (!isCoinDisabled && call.isConferenceFocus()) {
            synchronized (lock) {
                // send conference-info to all CallPeers of the specified call.
                for (Iterator<? extends CallPeer> i = call.getCallPeers(); i.hasNext(); ) {
                    notify(i.next());
                }
            }
        }
    }

    /**
     * Notifies a specific <code>CallPeer</code> about changes in the telephony conference-related
     * information.
     *
     * @param callPeer the <code>CallPeer</code> to notify.
     */
    private void notify(CallPeer callPeer) {
        if (!(callPeer instanceof CallPeerJabberImpl))
            return;

        // Don't send COINs to peers with might not be ready to accept COINs yet
        CallPeerState peerState = callPeer.getState();
        if ((peerState == CallPeerState.CONNECTING)
                || (peerState == CallPeerState.UNKNOWN)
                || (peerState == CallPeerState.INITIATING_CALL)
                || (peerState == CallPeerState.DISCONNECTED)
                || (peerState == CallPeerState.FAILED))
            return;

        final CallPeerJabberImpl callPeerJabber = (CallPeerJabberImpl) callPeer;
        final long timeSinceLastCoin = System.currentTimeMillis() - callPeerJabber.getLastConferenceInfoSentTimestamp();
        if (timeSinceLastCoin < COIN_MIN_INTERVAL) {
            if (callPeerJabber.isConfInfoScheduled())
                return;

            Timber.i("Scheduling to send a COIN to %s", callPeerJabber);
            callPeerJabber.setConfInfoScheduled(true);
            new Thread(() -> {
                try {
                    Thread.sleep(1 + COIN_MIN_INTERVAL - timeSinceLastCoin);
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
                OperationSetTelephonyConferencingJabberImpl.this.notify(callPeerJabber);
            }).start();
            return;
        }

        // check that callPeer supports COIN before sending him a conference-info
        EntityFullJid to = getBasicTelephony().getFullCalleeURI(callPeer.getPeerJid());

        // XXX if this generates actual disco#info requests we might want to cache it.
        DiscoverInfo discoverInfo = mPPS.getScHelper().discoverInfo(to);
        if (discoverInfo == null || !discoverInfo.containsFeature(ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE_COIN)) {
            Timber.i("%s does not support COIN", callPeer.getAddress());
            callPeerJabber.setConfInfoScheduled(false);
            return;
        }

        ConferenceInfoDocument currentConfInfo = getCurrentConferenceInfo(callPeerJabber);
        ConferenceInfoDocument lastSentConfInfo = callPeerJabber.getLastConferenceInfoSent();
        ConferenceInfoDocument diff;
        if (lastSentConfInfo == null)
            diff = currentConfInfo;
        else
            diff = getConferenceInfoDiff(lastSentConfInfo, currentConfInfo);

        if (diff != null) {
            int newVersion = (lastSentConfInfo == null) ? 1 : lastSentConfInfo.getVersion() + 1;
            diff.setVersion(newVersion);

            CoinIQ iq = getConferenceInfo(callPeerJabber, diff);
            if (iq != null) {
                try {
                    mPPS.getConnection().sendStanza(iq);
                } catch (NotConnectedException | InterruptedException e) {
                    Timber.e(e, "Could not send conference IQ");
                    return;
                }

                // We save currentConfInfo, because it is of state "full", while diff could be a partial
                currentConfInfo.setVersion(newVersion);
                callPeerJabber.setLastConferenceInfoSent(currentConfInfo);
                callPeerJabber.setLastConferenceInfoSentTimestamp(System.currentTimeMillis());
            }
        }
        callPeerJabber.setConfInfoScheduled(false);
    }

    /**
     * Generates the conference-info IQ to be sent to a specific <code>CallPeer</code> in order to
     * notify it of the current state of the conference managed by the local peer.
     *
     * @param callPeer the <code>CallPeer</code> to generate conference-info XML for
     * @param confInfo the <code>ConferenceInformationDocument</code> which is to be included in the IQ
     *
     * @return the conference-info IQ to be sent to the specified <code>callPeer</code> in order to
     * notify it of the current state of the conference managed by the local peer
     */
    private CoinIQ getConferenceInfo(CallPeerJabberImpl callPeer, final ConferenceInfoDocument confInfo) {
        String callPeerSID = callPeer.getSid();
        if (callPeerSID == null)
            return null;

        CoinIQ coinIQ = new CoinIQ() {
            @Override
            public IQChildElementXmlStringBuilder getIQChildElementBuilder(IQChildElementXmlStringBuilder xml) {
                // strip both "conference-info" ends' tags and rebuild xml;
                String xmlString = confInfo.toString()
                        .replace("<conference-info xmlns=\"urn:ietf:params:xml:ns:conference-info\"", "")
                        .replace("</conference-info>", "");
                return (IQChildElementXmlStringBuilder) xml.append(xmlString);
            }
        };

        CallJabberImpl call = callPeer.getCall();
        coinIQ.setFrom(call.getProtocolProvider().getOurJid());
        coinIQ.setTo(callPeer.getPeerJid());
        coinIQ.setType(Type.set);

        return coinIQ;
    }

    /**
     * Creates a new outgoing <code>Call</code> into which conference callees are to be invited by this
     * <code>OperationSetTelephonyConferencing</code>.
     *
     * @return a new outgoing <code>Call</code> into which conference callees are to be invited by this
     * <code>OperationSetTelephonyConferencing</code>
     *
     * @throws OperationFailedException if anything goes wrong
     */
    @Override
    protected CallJabberImpl createOutgoingCall()
            throws OperationFailedException {
        return new CallJabberImpl(getBasicTelephony(), JingleManager.randomId());
    }

    /**
     * {@inheritDoc}
     * <p/>
     * Implements the protocol-dependent part of the logic of inviting a callee to a <code>Call</code>.
     * The protocol-independent part of that logic is implemented by
     * {@link AbstractOperationSetTelephonyConferencing#inviteCalleeToCall(String, Call)}.
     */
    @Override
    protected CallPeer doInviteCalleeToCall(String calleeAddress, CallJabberImpl call)
            throws OperationFailedException {
        return getBasicTelephony().createOutgoingCall(call, calleeAddress,
                Arrays.asList(new ExtensionElement[]{
                        CoinExtension.getBuilder()
                                .setFocus(true)
                                .build()
                }));
    }

    /**
     * Parses a <code>String</code> value which represents a callee address specified by the user into
     * an object which is to actually represent the callee during the invitation to a conference
     * <code>Call</code>.
     *
     * @param calleeAddressString a <code>String</code> value which represents a callee address to be parsed into an object
     * which is to actually represent the callee during the invitation to a conference
     * <code>Call</code>
     *
     * @return an object which is to actually represent the specified <code>calleeAddressString</code>
     * during the invitation to a conference <code>Call</code>
     *
     * @throws OperationFailedException if parsing the specified <code>calleeAddressString</code> fails
     */
    @Override
    protected String parseAddressString(String calleeAddressString)
            throws OperationFailedException {
        try {
            return getBasicTelephony().getFullCalleeURI(JidCreate.from(calleeAddressString)).toString();
        } catch (XmppStringprepException | IllegalArgumentException e) {
            throw new OperationFailedException("Could not parse: " + calleeAddressString, 0, e);
        }
    }

    /**
     * Subscribes us to notifications about incoming Coin packets.
     */
    private void subscribeForCoinPackets() {
        iqRequestHandler = new IQRequestHandler();
        mPPS.getConnection().registerIQRequestHandler(iqRequestHandler);
    }

    /**
     * UnSubscribes us from notifications about incoming Coin packets.
     */
    private void unsubscribeForCoinPackets() {
        XMPPConnection connection = mPPS.getConnection();
        if ((connection != null) && (iqRequestHandler != null)) {
            connection.unregisterIQRequestHandler(iqRequestHandler);
        }
    }

    /**
     * Tests whether or not the specified packet should be handled by this operation set. This
     * method is called by smack prior to packet delivery and it would only accept <code>CoinIQ</code>s.
     *
     * @param packet the packet to test.
     *
     * @return true if and only if <code>packet</code> passes the filter.
     */
    @Override
    public boolean accept(Stanza packet) {
        return (packet instanceof CoinIQ);
    }

    /**
     * Handles incoming coin packets and passes them to the corresponding method based on their
     * action.
     */
    private class IQRequestHandler extends AbstractIqRequestHandler {
        // setup for Coin Request Handler
        protected IQRequestHandler() {
            super(CoinIQ.ELEMENT, CoinIQ.NAMESPACE, IQ.Type.set, IQRequestHandler.Mode.async);
        }

        @Override
        public IQ handleIQRequest(IQ iq) {
            CoinIQ coinIQ = (CoinIQ) iq;
            String errorMessage = "";

            /*
             * To prevent hijacking sessions from others, we should send the ack only if this is a
             * session-initiate with RTP content or if we are the owners of the packet's SID.
             */// first ack all coin "set" requests.
            IQ.Type type = coinIQ.getType();
            if (type == IQ.Type.set) {
                IQ ack = IQ.createResultIQ(coinIQ);
                try {
                    mPPS.getConnection().sendStanza(ack);
                } catch (NotConnectedException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
            else if (type == IQ.Type.error) {
                StanzaError error = coinIQ.getError();
                if (error != null) {
                    String msg = error.getConditionText();
                    errorMessage = ((msg != null) ? (msg + " ") : "") + "Error code: " + error.getCondition();
                }
                Timber.e("Received error in COIN packet. %s", errorMessage);
                return null;
            }

            String sid = coinIQ.getSid();
            if (sid != null) {
                CallPeerJabberImpl callPeer = getBasicTelephony().getActiveCallsRepository().findCallPeerBySid(sid);
                if (callPeer != null) {
                    if (type == IQ.Type.error) {
                        callPeer.fireConferenceMemberErrorEvent(errorMessage);
                    }
                    else {
                        handleCoin(callPeer, coinIQ);
                        Timber.d("Processing COIN from %s (version = %s)", coinIQ.getFrom(), coinIQ.getVersion());
                    }
                }
            }
            return null;
        }

        /**
         * Handles a specific <code>CoinIQ</code> sent from a specific <code>CallPeer</code>.
         *
         * @param callPeer the <code>CallPeer</code> from which the specified <code>CoinIQ</code> was sent
         * @param coinIQ the <code>CoinIQ</code> which was sent from the specified <code>callPeer</code>
         */
        private void handleCoin(CallPeerJabberImpl callPeer, CoinIQ coinIQ) {
            XmlStringBuilder mCoinIQ = coinIQ.getChildElementXML();
            try {
                setConferenceInfoXML(callPeer, mCoinIQ.toString());
            } catch (XMLException e) {
                Timber.e("Could not handle received COIN from %s; ResultCoinIQ: %s", callPeer, mCoinIQ);
            }
        }
    }

    /**
     * {@inheritDoc}
     * <p/>
     * For COINs (XEP-0298), we use the attributes of the <code>conference-info</code> element to
     * piggyback a Jingle SID. This is temporary and should be removed once we choose a better way
     * to pass the SID.
     */
    @Override
    protected ConferenceInfoDocument getCurrentConferenceInfo(MediaAwareCallPeer<?, ?, ?> callPeer) {
        ConferenceInfoDocument confInfo = super.getCurrentConferenceInfo(callPeer);

        if (callPeer instanceof CallPeerJabberImpl && confInfo != null) {
            confInfo.setSid(((CallPeerJabberImpl) callPeer).getSid());
        }
        return confInfo;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getLocalEntity(CallPeer callPeer) {
        Jingle sessionIQ = ((CallPeerJabberImpl) callPeer).getSessionIQ();
        Jid from = sessionIQ.getFrom();

        // cmeng - local callPeer IQ's (from == null)
        if (from != null) {
            BareJid chatRoomName = from.asBareJid();
            OperationSetMultiUserChatJabberImpl opSetMUC = (OperationSetMultiUserChatJabberImpl) mPPS
                    .getOperationSet(OperationSetMultiUserChat.class);
            ChatRoom room = null;
            if (opSetMUC != null)
                room = opSetMUC.getChatRoom(chatRoomName);

            Timber.i("### RoomName creation (result): %s (%s)", callPeer.toString(), chatRoomName);
            if (room != null)
                return "xmpp:" + chatRoomName + "/" + room.getUserNickname();
        }
        return "xmpp:" + mPPS.getOurJid();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getLocalDisplayName() {
        return null;
    }

    /**
     * {@inheritDoc}
     * <p/>
     * The URI of the returned <code>ConferenceDescription</code> is the occupant JID with which we have
     * joined the room.
     * <p/>
     * If a Videobridge is available for our <code>ProtocolProviderService</code> we use it. TODO: this
     * should be relaxed when we refactor the Videobridge implementation, so that any Videobridge
     * (on any protocol provider) can be used.
     */
    @Override
    public ConferenceDescription setupConference(final ChatRoom chatRoom) {
        OperationSetVideoBridge videoBridge = mPPS.getOperationSet(OperationSetVideoBridge.class);
        boolean isVideobridge = (videoBridge != null) && videoBridge.isActive();

        CallJabberImpl call = new CallJabberImpl(getBasicTelephony(), JingleManager.randomId());
        call.setAutoAnswer(true);

        String uri = "xmpp:" + chatRoom.getIdentifier() + "/" + chatRoom.getUserNickname();
        ConferenceDescription cd = new ConferenceDescription(uri, call.getCallId());

        call.addCallChangeListener(new CallChangeListener() {
            @Override
            public void callStateChanged(CallChangeEvent ev) {
                if (CallState.CALL_ENDED.equals(ev.getNewValue()))
                    chatRoom.publishConference(null, null);
            }

            @Override
            public void callPeerRemoved(CallPeerEvent ev) {
            }

            @Override
            public void callPeerAdded(CallPeerEvent ev) {
            }
        });
        if (isVideobridge) {
            call.setConference(new MediaAwareCallConference(true));

            // For Jitsi Videobridge we set the transports to RAW-UDP, otherwise
            // we leave them empty (meaning both RAW-UDP and ICE could be used)
            cd.addTransport(ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE_RAW_UDP_0);
        }
        Timber.i("Setup a conference with uri = %s and callid = %s. Videobridge in use: %s",
                uri, call.getCallId(), isVideobridge);
        return cd;
    }
}
