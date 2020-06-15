/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import org.atalk.android.gui.call.JingleMessageHelper;
import org.xmpp.extensions.coin.CoinIQ;
import org.xmpp.extensions.jingle.CoinExtension;
import org.xmpp.extensions.jingle.element.Jingle;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.service.protocol.media.*;

import org.atalk.util.xml.XMLException;
import org.jivesoftware.smack.SmackException.NoResponseException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.StanzaFilter;
import org.jivesoftware.smack.iqrequest.AbstractIqRequestHandler;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smack.packet.IQ.Type;
import org.jivesoftware.smack.util.XmlStringBuilder;
import org.jivesoftware.smackx.disco.packet.DiscoverInfo;
import org.jxmpp.jid.*;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;

import java.util.Arrays;
import java.util.Iterator;

import timber.log.Timber;

/**
 * Implements <tt>OperationSetTelephonyConferencing</tt> for Jabber.
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
        implements RegistrationStateChangeListener, StanzaFilter
{
    /**
     * The Jabber Incoming Conference Call IQRequest Handler.
     */
    private IQRequestHandler iqRequestHandler = null;
    /**
     * The minimum interval in milliseconds between COINs sent to a single <tt>CallPeer</tt>.
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
     * Initializes a new <tt>OperationSetTelephonyConferencingJabberImpl</tt> instance which is to
     * provide telephony conferencing services for the specified Jabber
     * <tt>ProtocolProviderService</tt> implementation.
     *
     * @param parentProvider the Jabber <tt>ProtocolProviderService</tt> implementation which has requested the
     * creation of the new instance and for which the new instance is to provide telephony
     * conferencing services
     */
    public OperationSetTelephonyConferencingJabberImpl(ProtocolProviderServiceJabberImpl parentProvider)
    {
        super(parentProvider);
        this.parentProvider.addRegistrationStateChangeListener(this);
        this.isCoinDisabled = JabberActivator.getConfigurationService().getBoolean(DISABLE_COIN_PROP_NAME, false);
    }

    /**
     * Implementation of method <tt>registrationStateChange</tt> from interface
     * RegistrationStateChangeListener for setting up (or down) our <tt>JingleManager</tt> when an
     * <tt>XMPPConnection</tt> is available
     *
     * @param evt the event received
     */
    @Override
    public void registrationStateChanged(RegistrationStateChangeEvent evt)
    {
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
     * Notifies all <tt>CallPeer</tt>s associated with a specific <tt>Call</tt> about changes in the
     * telephony conference-related information. In contrast, {@link #notifyAll()} notifies all
     * <tt>CallPeer</tt>s associated with the telephony conference in which a specific <tt>Call</tt>
     * is participating.
     *
     * @param call the <tt>Call</tt> whose <tt>CallPeer</tt>s are to be notified about changes in the
     * telephony conference-related information
     */
    @Override
    protected void notifyCallPeers(Call call)
    {
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
     * Notifies a specific <tt>CallPeer</tt> about changes in the telephony conference-related
     * information.
     *
     * @param callPeer the <tt>CallPeer</tt> to notify.
     */
    private void notify(CallPeer callPeer)
    {
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
        try {
            DiscoverInfo discoverInfo = null;
            try {
                discoverInfo = parentProvider.getDiscoveryManager().discoverInfo(to);
            } catch (NoResponseException | NotConnectedException | InterruptedException e) {
                e.printStackTrace();
            }

            if (!discoverInfo.containsFeature(ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE_COIN)) {
                Timber.i("%s does not support COIN", callPeer.getAddress());
                callPeerJabber.setConfInfoScheduled(false);
                return;
            }
        } catch (XMPPException xmppe) {
            Timber.w(xmppe, "Failed to retrieve DiscoverInfo for %s", to);
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
                    parentProvider.getConnection().sendStanza(iq);
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
     * Generates the conference-info IQ to be sent to a specific <tt>CallPeer</tt> in order to
     * notify it of the current state of the conference managed by the local peer.
     *
     * @param callPeer the <tt>CallPeer</tt> to generate conference-info XML for
     * @param confInfo the <tt>ConferenceInformationDocument</tt> which is to be included in the IQ
     * @return the conference-info IQ to be sent to the specified <tt>callPeer</tt> in order to
     * notify it of the current state of the conference managed by the local peer
     */
    private CoinIQ getConferenceInfo(CallPeerJabberImpl callPeer, final ConferenceInfoDocument confInfo)
    {
        String callPeerSID = callPeer.getSid();
        if (callPeerSID == null)
            return null;

        CoinIQ coinIQ = new CoinIQ()
        {
            @Override
            public IQChildElementXmlStringBuilder getIQChildElementBuilder(IQChildElementXmlStringBuilder xml)
            {
                // strip both "conference-info" ends' tags and rebuild xml;
                String xmlString = confInfo.toString()
                        .replace("<conference-info xmlns=\"urn:ietf:params:xml:ns:conference-info\"", "")
                        .replace("</conference-info>", "");
                return (IQChildElementXmlStringBuilder) xml.append(xmlString);
            }
        };

        CallJabberImpl call = callPeer.getCall();
        coinIQ.setFrom(call.getProtocolProvider().getOurJID());
        coinIQ.setTo(callPeer.getPeerJid());
        coinIQ.setType(Type.set);

        return coinIQ;
    }

    /**
     * Creates a new outgoing <tt>Call</tt> into which conference callees are to be invited by this
     * <tt>OperationSetTelephonyConferencing</tt>.
     *
     * @return a new outgoing <tt>Call</tt> into which conference callees are to be invited by this
     * <tt>OperationSetTelephonyConferencing</tt>
     * @throws OperationFailedException if anything goes wrong
     */
    @Override
    protected CallJabberImpl createOutgoingCall()
            throws OperationFailedException
    {
        return new CallJabberImpl(getBasicTelephony(), Jingle.generateSid());
    }

    /**
     * {@inheritDoc}
     * <p/>
     * Implements the protocol-dependent part of the logic of inviting a callee to a <tt>Call</tt>.
     * The protocol-independent part of that logic is implemented by
     * {@link AbstractOperationSetTelephonyConferencing#inviteCalleeToCall(String, Call)}.
     */
    @Override
    protected CallPeer doInviteCalleeToCall(String calleeAddress, CallJabberImpl call)
            throws OperationFailedException
    {
        return getBasicTelephony().createOutgoingCall(call, calleeAddress,
                Arrays.asList(new ExtensionElement[]{new CoinExtension(true)}));
    }

    /**
     * Parses a <tt>String</tt> value which represents a callee address specified by the user into
     * an object which is to actually represent the callee during the invitation to a conference
     * <tt>Call</tt>.
     *
     * @param calleeAddressString a <tt>String</tt> value which represents a callee address to be parsed into an object
     * which is to actually represent the callee during the invitation to a conference
     * <tt>Call</tt>
     * @return an object which is to actually represent the specified <tt>calleeAddressString</tt>
     * during the invitation to a conference <tt>Call</tt>
     * @throws OperationFailedException if parsing the specified <tt>calleeAddressString</tt> fails
     */
    @Override
    protected String parseAddressString(String calleeAddressString)
            throws OperationFailedException
    {
        try {
            return getBasicTelephony().getFullCalleeURI(JidCreate.from(calleeAddressString)).toString();
        } catch (XmppStringprepException | IllegalArgumentException e) {
            throw new OperationFailedException("Could not parse: " + calleeAddressString, 0, e);
        }
    }

    /**
     * Subscribes us to notifications about incoming Coin packets.
     */
    private void subscribeForCoinPackets()
    {
        iqRequestHandler = new IQRequestHandler();
        parentProvider.getConnection().registerIQRequestHandler(iqRequestHandler);
    }

    /**
     * UnSubscribes us from notifications about incoming Coin packets.
     */
    private void unsubscribeForCoinPackets()
    {
        XMPPConnection connection = parentProvider.getConnection();
        if ((connection != null) && (iqRequestHandler != null)) {
            connection.unregisterIQRequestHandler(iqRequestHandler);
        }
    }

    /**
     * Tests whether or not the specified packet should be handled by this operation set. This
     * method is called by smack prior to packet delivery and it would only accept <tt>CoinIQ</tt>s.
     *
     * @param packet the packet to test.
     * @return true if and only if <tt>packet</tt> passes the filter.
     */
    @Override
    public boolean accept(Stanza packet)
    {
        return (packet instanceof CoinIQ);
    }

    /**
     * Handles incoming coin packets and passes them to the corresponding method based on their
     * action.
     */
    private class IQRequestHandler extends AbstractIqRequestHandler
    {
        // setup for Coin Request Handler
        protected IQRequestHandler()
        {
            super(CoinIQ.ELEMENT, CoinIQ.NAMESPACE, IQ.Type.set, IQRequestHandler.Mode.async);
        }

        // public void processStanza(Stanza packet) {
        @Override
        public IQ handleIQRequest(IQ iq)
        {
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
                    parentProvider.getConnection().sendStanza(ack);
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
                CallPeerJabberImpl callPeer = getBasicTelephony().getActiveCallsRepository().findCallPeer(sid);
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
         * Handles a specific <tt>CoinIQ</tt> sent from a specific <tt>CallPeer</tt>.
         *
         * @param callPeer the <tt>CallPeer</tt> from which the specified <tt>CoinIQ</tt> was sent
         * @param coinIQ the <tt>CoinIQ</tt> which was sent from the specified <tt>callPeer</tt>
         */
        private void handleCoin(CallPeerJabberImpl callPeer, CoinIQ coinIQ)
        {
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
     * For COINs (XEP-0298), we use the attributes of the <tt>conference-info</tt> element to
     * piggyback a Jingle SID. This is temporary and should be removed once we choose a better way
     * to pass the SID.
     */
    @Override
    protected ConferenceInfoDocument getCurrentConferenceInfo(MediaAwareCallPeer<?, ?, ?> callPeer)
    {
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
    protected String getLocalEntity(CallPeer callPeer)
    {
        Jingle sessionIQ = ((CallPeerJabberImpl) callPeer).getSessionIQ();
        Jid from = sessionIQ.getFrom();

        // cmeng - local callPeer IQ's (from == null)
        if (from != null) {
            BareJid chatRoomName = from.asBareJid();
            OperationSetMultiUserChatJabberImpl opSetMUC = (OperationSetMultiUserChatJabberImpl) parentProvider
                    .getOperationSet(OperationSetMultiUserChat.class);
            ChatRoom room = null;
            if (opSetMUC != null)
                room = opSetMUC.getChatRoom(chatRoomName);

            Timber.i("### RoomName creation (result): %s (%s)", callPeer.toString(), chatRoomName);
            if (room != null)
                return "xmpp:" + chatRoomName + "/" + room.getUserNickname();
        }
        return "xmpp:" + parentProvider.getOurJID();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getLocalDisplayName()
    {
        return null;
    }

    /**
     * {@inheritDoc}
     * <p/>
     * The URI of the returned <tt>ConferenceDescription</tt> is the occupant JID with which we have
     * joined the room.
     * <p/>
     * If a Videobridge is available for our <tt>ProtocolProviderService</tt> we use it. TODO: this
     * should be relaxed when we refactor the Videobridge implementation, so that any Videobridge
     * (on any protocol provider) can be used.
     */
    @Override
    public ConferenceDescription setupConference(final ChatRoom chatRoom)
    {
        OperationSetVideoBridge videoBridge = parentProvider.getOperationSet(OperationSetVideoBridge.class);
        boolean isVideobridge = (videoBridge != null) && videoBridge.isActive();

        CallJabberImpl call = new CallJabberImpl(getBasicTelephony(), Jingle.generateSid());
        call.setAutoAnswer(true);

        String uri = "xmpp:" + chatRoom.getIdentifier() + "/" + chatRoom.getUserNickname();
        ConferenceDescription cd = new ConferenceDescription(uri, call.getCallId());

        call.addCallChangeListener(new CallChangeListener()
        {
            @Override
            public void callStateChanged(CallChangeEvent ev)
            {
                if (CallState.CALL_ENDED.equals(ev.getNewValue()))
                    chatRoom.publishConference(null, null);
            }

            @Override
            public void callPeerRemoved(CallPeerEvent ev)
            {
            }

            @Override
            public void callPeerAdded(CallPeerEvent ev)
            {
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
