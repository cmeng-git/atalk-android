/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import android.text.TextUtils;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.service.protocol.jabber.JabberAccountID;
import net.java.sip.communicator.service.protocol.media.AbstractOperationSetBasicTelephony;
import net.java.sip.communicator.service.protocol.media.MediaAwareCallPeer;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.plugin.timberlog.TimberLog;
import org.jivesoftware.smack.SmackException.NoResponseException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.StanzaFilter;
import org.jivesoftware.smack.iqrequest.AbstractIqRequestHandler;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smackx.confdesc.CallIdExtension;
import org.jivesoftware.smackx.disco.packet.DiscoverInfo;
import org.jivesoftware.smackx.jingle.*;
import org.jivesoftware.smackx.jingle.element.*;
import org.jivesoftware.smackx.jingle.element.JingleReason.Reason;
import org.jxmpp.jid.*;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;
import org.jxmpp.util.XmppStringUtils;

import java.util.*;

import timber.log.Timber;

/**
 * Implements all call management logic and exports basic telephony support by implementing
 * <code>OperationSetBasicTelephony</code>.
 *
 * @author Emil Ivov
 * @author Symphorien Wanko
 * @author Lyubomir Marinov
 * @author Sebastien Vincent
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
public class OperationSetBasicTelephonyJabberImpl
        extends AbstractOperationSetBasicTelephony<ProtocolProviderServiceJabberImpl>
        implements RegistrationStateChangeListener, StanzaFilter, OperationSetSecureSDesTelephony,
        OperationSetSecureZrtpTelephony, OperationSetAdvancedTelephony<ProtocolProviderServiceJabberImpl>
{
    /**
     * A reference to the <code>ProtocolProviderServiceJabberImpl</code> instance that created us.
     */
    private final ProtocolProviderServiceJabberImpl protocolProvider;

    private XMPPConnection mConnection = null;

    /**
     * Contains references for all currently active (non ended) calls.
     */
    private final ActiveCallsRepositoryJabberImpl activeCallsRepository = new ActiveCallsRepositoryJabberImpl(this);

    /**
     * Jingle IQ set stanza processor
     */
    private final JingleIqSetRequestHandler setRequestHandler = new JingleIqSetRequestHandler();

    /**
     * Google Voice domain.
     */
    public static final String GOOGLE_VOICE_DOMAIN = "voice.google.com";

    /**
     * Creates a new instance.
     *
     * @param protocolProvider a reference to the <code>ProtocolProviderServiceJabberImpl</code> instance that created us.
     */
    public OperationSetBasicTelephonyJabberImpl(ProtocolProviderServiceJabberImpl protocolProvider)
    {
        this.protocolProvider = protocolProvider;
        this.protocolProvider.addRegistrationStateChangeListener(this);
    }

    /**
     * Implementation of method <code>registrationStateChange</code> from interface RegistrationStateChangeListener
     * for setting up (or down) our <code>JingleManager</code> when an <code>XMPPConnection</code> is available
     *
     * @param evt the event received
     */
    public void registrationStateChanged(RegistrationStateChangeEvent evt)
    {
        RegistrationState registrationState = evt.getNewState();
        if (registrationState == RegistrationState.REGISTERING) {
            mConnection = protocolProvider.getConnection();
            subscribeForJinglePackets();
            Timber.i("Jingle : ON");
        }
        else if (registrationState == RegistrationState.UNREGISTERED) {
            unsubscribeForJinglePackets();
            Timber.i("Jingle : OFF");
        }
    }

    /**
     * Creates a new <code>Call</code> and invites a specific <code>CallPeer</code> to it given by her <code>String</code> URI.
     *
     * @param callee the address of the callee who we should invite to a new <code>Call</code>
     * @param conference the <code>CallConference</code> in which the newly-created <code>Call</code> is to participate
     * @return a newly created <code>Call</code>. The specified <code>callee</code> is available in the
     * <code>Call</code> as a <code>CallPeer</code>
     * @throws OperationFailedException with the corresponding code if we fail to create the call
     * @see OperationSetBasicTelephony#createCall(String)
     */
    public Call createCall(String callee, CallConference conference)
            throws OperationFailedException
    {
        CallJabberImpl call = new CallJabberImpl(this, Jingle.generateSid());
        if (conference != null)
            call.setConference(conference);

        CallPeer callPeer = createOutgoingCall(call, callee);
        if (callPeer == null) {
            throw new OperationFailedException("Failed to create outgoing call because no peer was created",
                    OperationFailedException.INTERNAL_ERROR);
        }

        Call callOfCallPeer = callPeer.getCall();
        // We may have a Google Talk call here.
        if ((callOfCallPeer != call) && (conference != null))
            callOfCallPeer.setConference(conference);

        return callOfCallPeer;
    }

    /**
     * {@inheritDoc}
     *
     * Creates a new <code>CallJabberImpl</code> and initiates a jingle session to the JID obtained
     * from the <code>uri</code> of <code>cd</code>.
     *
     * If <code>cd</code> contains a <code>callid</code>, adds the "callid" element as an extension to the session-initiate IQ.
     * Uses the supported transports of <code>cd</code>
     */
    @Override
    public CallJabberImpl createCall(ConferenceDescription cd, final ChatRoom chatRoom)
            throws OperationFailedException
    {
        final CallJabberImpl call = new CallJabberImpl(this, Jingle.generateSid());
        ((ChatRoomJabberImpl) chatRoom).addConferenceCall(call);

        call.addCallChangeListener(new CallChangeListener()
        {
            @Override
            public void callPeerAdded(CallPeerEvent ev)
            {
            }

            @Override
            public void callPeerRemoved(CallPeerEvent ev)
            {
            }

            @Override
            public void callStateChanged(CallChangeEvent ev)
            {
                if (CallState.CALL_ENDED.equals(ev.getNewValue())) {
                    ((ChatRoomJabberImpl) chatRoom).removeConferenceCall(call);
                }
            }
        });

        String remoteUri = cd.getUri();
        if (remoteUri.startsWith("xmpp:"))
            remoteUri = remoteUri.substring(5);

        FullJid remoteJid;
        try {
            remoteJid = JidCreate.fullFrom(remoteUri);
        } catch (XmppStringprepException e) {
            throw new OperationFailedException("Invalid remote JID", OperationFailedException.GENERAL_ERROR, e);
        }
        List<ExtensionElement> sessionInitiateExtensions = new ArrayList<>(2);

        String callid = cd.getCallId();
        if (callid != null) {
            sessionInitiateExtensions.add(new CallIdExtension(callid));
        }

        // String password = cd.getPassword();
        // if (password != null)
        // extensions.add(new PasswordPacketExtension(password));

        call.initiateSession(remoteJid, null, sessionInitiateExtensions, cd.getSupportedTransports());
        return call;
    }

    /**
     * Init and establish the specified call.
     *
     * @param call the <code>CallJabberImpl</code> that will be used to initiate the call
     * @param calleeAddress the address of the callee that we'd like to connect with.
     * @return the <code>CallPeer</code> that represented by the specified uri. All following state
     * change events will be delivered through that call peer. The <code>Call</code> that this
     * peer is a member of could be retrieved from the <code>CallPeer</code> instance with the
     * use of the corresponding method.
     * @throws OperationFailedException with the corresponding code if we fail to create the call.
     */
    AbstractCallPeer<?, ?> createOutgoingCall(CallJabberImpl call, String calleeAddress)
            throws OperationFailedException
    {
        return createOutgoingCall(call, calleeAddress, null);
    }

    /**
     * Init and establish the specified call.
     *
     * @param call the <code>CallJabberImpl</code> that will be used to initiate the call
     * @param calleeAddress the address of the callee that we'd like to connect with.
     * @param sessionInitiateExtensions a collection of additional and optional <code>ExtensionElement</code>s to be
     * added to the <code>session-initiate</code> {@link Jingle} which is to init the specified <code>call</code>
     * @return the <code>CallPeer</code> that represented by the specified uri. All following state
     * change events will be delivered through that call peer. The <code>Call</code> that this
     * peer is a member of could be retrieved from the <code>CallPeer</code> instance with the
     * use of the corresponding method.
     * @throws OperationFailedException with the corresponding code if we fail to create the call.
     */
    AbstractCallPeer<?, ?> createOutgoingCall(CallJabberImpl call, String calleeAddress,
            Iterable<ExtensionElement> sessionInitiateExtensions)
            throws OperationFailedException
    {
        FullJid calleeJid = null;
        if (calleeAddress.contains("/")) {
            try {
                calleeJid = JidCreate.fullFrom(calleeAddress);
            } catch (XmppStringprepException e) {
                e.printStackTrace();
            }
        }
        return createOutgoingCall(call, calleeAddress, calleeJid, sessionInitiateExtensions);
    }

    /**
     * Init and establish the specified call.
     *
     * @param call the <code>CallJabberImpl</code> that will be used to initiate the call
     * @param calleeAddress the address of the callee that we'd like to connect with.
     * @param fullCalleeURI the full Jid address, which if specified would explicitly initiate a call to this full address
     * @param sessionInitiateExtensions a collection of additional and optional <code>ExtensionElement</code>s to be
     * added to the <code>session-initiate</code> {@link Jingle} which is to init the specified <code>call</code>
     * @return the <code>CallPeer</code> that represented by the specified uri. All following state
     * change events will be delivered through that call peer. The <code>Call</code> that this
     * peer is a member of could be retrieved from the <code>CallPeer</code> instance with the
     * use of the corresponding method.
     * @throws OperationFailedException with the corresponding code if we fail to create the call.
     */
    AbstractCallPeer<?, ?> createOutgoingCall(CallJabberImpl call, String calleeAddress,
            FullJid fullCalleeURI, Iterable<ExtensionElement> sessionInitiateExtensions)
            throws OperationFailedException
    {
        Timber.i("Creating outgoing call to %s", calleeAddress);
        if (mConnection == null || call == null) {
            throw new OperationFailedException("Failed to create Outgoing Jingle Session - NO valid XMPPConnection.",
                    OperationFailedException.INTERNAL_ERROR);
        }

        boolean isGoogle = protocolProvider.isGmailOrGoogleAppsAccount();
        boolean isGoogleVoice = calleeAddress.endsWith(GOOGLE_VOICE_DOMAIN);
        if (isGoogle && !calleeAddress.contains("@")) {
            calleeAddress += "@" + GOOGLE_VOICE_DOMAIN;
            isGoogleVoice = true;
        }

        // if address is not suffixed by @domain, append address with the domain corresponding
        // to the OVERRIDE_PHONE_SUFFIX property if defined or user account service domain
        JabberAccountID accountJID = (JabberAccountID) getProtocolProvider().getAccountID();
        String telephonyDomain = accountJID.getOverridePhoneSuffix();
        if (!calleeAddress.contains("@")) {
            String serviceName = telephonyDomain;
            if (TextUtils.isEmpty(serviceName))
                serviceName = XmppStringUtils.parseDomain(accountJID.getUserID());
            calleeAddress += "@" + serviceName;
        }
        boolean isTelephonyCall = ((telephonyDomain != null) && calleeAddress.endsWith(telephonyDomain));

        // getAccountPropertyString(JabberAccountID.TELEPHONY_BYPASS_GTALK_CAPS);
        String bypassDomain = accountJID.getTelephonyDomainBypassCaps();
        boolean alwaysCallGtalk = ((bypassDomain != null)
                && bypassDomain.equals(XmppStringUtils.parseDomain(calleeAddress))) || isGoogleVoice;

        boolean isPrivateMessagingContact = false;
        OperationSetMultiUserChat mucOpSet = getProtocolProvider().getOperationSet(OperationSetMultiUserChat.class);
        if (mucOpSet != null)
            isPrivateMessagingContact = mucOpSet.isPrivateMessagingContact(calleeAddress);

        Jid calleeJid = null;
        try {
            calleeJid = JidCreate.from(calleeAddress);
        } catch (XmppStringprepException | IllegalArgumentException e) {
            e.printStackTrace();
        }

        // Throw exception if the call is none of the above criteria check
        if ((!Roster.getInstanceFor(mConnection).contains(calleeJid.asBareJid())
                && !isPrivateMessagingContact) && !alwaysCallGtalk && !isTelephonyCall) {
            throw new OperationFailedException(aTalkApp.getResString(R.string.service_gui_NOT_IN_CALLGROUP,
                    calleeAddress), OperationFailedException.FORBIDDEN);
        }

        Jid fullCalleeJid = fullCalleeURI;
        Jid discoInfoJid = fullCalleeURI;
        if (fullCalleeURI == null) {
            /*
             * @ see XEP-0100: Gateway Interaction - must also confirm to XEP-0115 for smack to accept disco#info
             * 1. use calleeJid for fullCalleeURI
             * 2. use gateway Jid to get disco#info from server
             */
            if (isTelephonyCall) {
                fullCalleeJid = calleeJid;

                // pbx gateway has only the domain part and without resource being specified.
                try {
                    discoInfoJid = JidCreate.domainBareFrom(telephonyDomain);
                } catch (XmppStringprepException e) {
                    Timber.w("DomainJid creation failed for: %s", telephonyDomain);
                }

                if (discoInfoJid == null) {
                    throw new OperationFailedException(aTalkApp.getResString(R.string.service_gui_NOT_IN_ROSTER,
                            telephonyDomain), OperationFailedException.ILLEGAL_ARGUMENT);
                }
            }
            // If there's no fullCalleeURI specified we'll discover the most connected one with highest priority.
            else {
                fullCalleeURI = discoverFullJid(calleeJid);
                if (fullCalleeURI != null) {
                    fullCalleeJid = fullCalleeURI;
                    discoInfoJid = fullCalleeURI;
                }
                else {
                    if (telephonyDomain != null)
                        throw new OperationFailedException(aTalkApp.getResString(R.string.service_gui_NOT_IN_ROSTER,
                                telephonyDomain), OperationFailedException.ILLEGAL_ARGUMENT);
                    else
                        throw new OperationFailedException(aTalkApp.getResString(R.string.service_gui_INVALID_ADDRESS,
                                fullCalleeJid), OperationFailedException.ILLEGAL_ARGUMENT);
                }
            }
        }

        DiscoverInfo di = null;
        try {
            // check if the remote client supports telephony.
            di = protocolProvider.getDiscoveryManager().discoverInfo(discoInfoJid);
        } catch (XMPPException | NoResponseException | NotConnectedException | InterruptedException ex) {
            Timber.w(ex, "could not retrieve info for %s", discoInfoJid);
        }

        if (di == null) {
            Timber.i("%s: jingle not supported?", discoInfoJid);
            throw new OperationFailedException(aTalkApp.getResString(R.string.service_gui_NO_JINGLE_SUPPORT,
                    discoInfoJid), OperationFailedException.NOT_SUPPORTED_OPERATION);
        }

        /*
         * in case we figure that calling people without a resource id is impossible, we'll have to
         * uncomment the following lines. keep in mind that this would mean - no calls to pstn
         * though if (fullCalleeURI.indexOf('/') < 0) { throw new OperationFailedException(
         * "Failed to create OutgoingJingleSession.\n" + "User " + calleeAddress +
         * " is unknown to us." , OperationFailedException.INTERNAL_ERROR); }
         */

        // initiate call
        AbstractCallPeer<?, ?> peer;
        try {
            peer = call.initiateSession(fullCalleeJid.asFullJidIfPossible(), di, sessionInitiateExtensions, null);
        } catch (Throwable t) {
            /*
             * The Javadoc on ThreadDeath says: If ThreadDeath is caught by a method, it is
             * important that it be rethrown so that the thread actually dies.
             */
            if (t instanceof ThreadDeath)
                throw (ThreadDeath) t;
            else {
                if (telephonyDomain != null)
                    throw new OperationFailedException(aTalkApp.getResString(R.string.service_gui_NOT_IN_ROSTER,
                            telephonyDomain), OperationFailedException.ILLEGAL_ARGUMENT);
                else {
                    String message = null;
                    if (t.getCause() != null) {
                        message = t.getCause().getMessage();
                    }
                    else if (t.getMessage() != null) {
                        message = t.getMessage();
                    }

                    Timber.e("Initiate call session Exception: %s", message);
                    throw new OperationFailedException(message, OperationFailedException.NETWORK_FAILURE);
                }
            }
        }
        return peer;
    }

    /**
     * Discovers the resource for <code>calleeAddress</code> with the highest priority which supports
     * either Jingle or Gtalk. Returns the full JID.
     *
     * @param calleeAddress the bareJid of the callee
     * @return the full callee URI (Jid)
     */
    private EntityFullJid discoverFullJid(Jid calleeAddress)
    {
        DiscoverInfo discoverInfo = null;
        PresenceStatus jabberStatus = null;
        int bestPriority = -1;
        Jid calleeURI;
        Jid fullCalleeURI = null;

        List<Presence> it = Roster.getInstanceFor(mConnection).getPresences(calleeAddress.asBareJid());
        for (Presence presence : it) {
            int priority = (presence.getPriority() == Integer.MIN_VALUE) ? 0 : presence.getPriority();
            calleeURI = presence.getFrom();
            try {
                // check if the remote client supports telephony.
                discoverInfo = protocolProvider.getDiscoveryManager().discoverInfo(calleeURI);
            } catch (XMPPException | NoResponseException | NotConnectedException | InterruptedException ex) {
                Timber.w(ex, "could not retrieve info for: %s", fullCalleeURI);
            }

            if ((discoverInfo != null) && discoverInfo.containsFeature(
                    ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE)) {
                if (priority > bestPriority) {
                    bestPriority = priority;
                    fullCalleeURI = calleeURI;
                    jabberStatus = OperationSetPersistentPresenceJabberImpl
                            .jabberStatusToPresenceStatus(presence, protocolProvider);
                }
                else if (priority == bestPriority && jabberStatus != null) {
                    PresenceStatus tempStatus = OperationSetPersistentPresenceJabberImpl
                            .jabberStatusToPresenceStatus(presence, protocolProvider);
                    if (tempStatus.compareTo(jabberStatus) > 0) {
                        fullCalleeURI = calleeURI;
                        jabberStatus = tempStatus;
                    }
                }
            }
        }
        Timber.i("Callee jid for outgoing call: %s, priority %s", fullCalleeURI, bestPriority);
        return (fullCalleeURI != null) ? fullCalleeURI.asEntityFullJidIfPossible() : null;
    }

    /**
     * Gets the full callee URI for a specific callee address.
     *
     * @param calleeAddress the callee address to get the full callee URI for
     * @return the full callee URI for the specified <code>calleeAddress</code>
     */
    EntityFullJid getFullCalleeURI(Jid calleeAddress)
    {
        return (calleeAddress.isEntityFullJid()) ? calleeAddress.asEntityFullJidOrThrow()
                : Roster.getInstanceFor(mConnection).getPresence(calleeAddress.asBareJid()).getFrom().asEntityFullJidOrThrow();
    }

    /**
     * Returns an iterator over all currently active calls.
     *
     * @return an iterator over all currently active calls.
     */
    public Iterator<CallJabberImpl> getActiveCalls()
    {
        return activeCallsRepository.getActiveCalls();
    }

    /**
     * Returns the active call peer corresponding to the given sid.
     *
     * @param sid the Jingle session ID of the active <code>Call</code> between the local peer and the
     * callee in the case of attended transfer; <code>null</code> in the case of unattended transfer
     * @return The active call peer corresponding to the given sid. "null" if there is no such call.
     */
    public CallPeerJabberImpl getActiveCallPeer(String sid)
    {
        return activeCallsRepository.findCallPeer(sid);
    }

    /**
     * Resumes communication with a call peer previously put on hold.
     *
     * @param peer the call peer to put on hold.
     * @throws OperationFailedException if we fail to send the "hold" message.
     */
    public synchronized void putOffHold(CallPeer peer)
            throws OperationFailedException
    {
        putOnHold(peer, false);
    }

    /**
     * Puts the specified CallPeer "on hold".
     *
     * @param peer the peer that we'd like to put on hold.
     * @throws OperationFailedException if we fail to send the "hold" message.
     */
    public synchronized void putOnHold(CallPeer peer)
            throws OperationFailedException
    {
        putOnHold(peer, true);
    }

    /**
     * Puts the specified <code>CallPeer</code> on or off hold.
     *
     * @param peer the <code>CallPeer</code> to be put on or off hold
     * @param on <code>true</code> to have the specified <code>CallPeer</code> put on hold; <code>false</code>, otherwise
     * @throws OperationFailedException if we fail to send the "hold" message.
     */
    private void putOnHold(CallPeer peer, boolean on)
            throws OperationFailedException
    {
        if (peer instanceof CallPeerJabberImpl)
            ((CallPeerJabberImpl) peer).putOnHold(on);
    }

    /**
     * Ends the call with the specified <code>peer</code>.
     *
     * @param peer the peer that we'd like to hang up on.
     * @throws ClassCastException if peer is not an instance of this CallPeerSipImpl.
     * @throws OperationFailedException if we fail to terminate the call.
     */
    public synchronized void hangupCallPeer(CallPeer peer)
            throws ClassCastException, OperationFailedException
    {
        hangupCallPeer(peer, HANGUP_REASON_NORMAL_CLEARING, null);
    }

    /**
     * Ends the call with the specified <code>peer</code>.
     *
     * @param peer the peer that we'd like to hang up on.
     * @param reasonCode indicates if the hangup is following to a call failure or simply a disconnect indicate
     * by the reason.
     * @param reasonText the reason of the hangup. If the hangup is due to a call failure, then this string
     * could indicate the reason of the failure
     */
    public void hangupCallPeer(CallPeer peer, int reasonCode, String reasonText)
            throws OperationFailedException
    {
        boolean failed = (reasonCode != HANGUP_REASON_NORMAL_CLEARING);

        // if we are failing a peer and have a reason, add the reason packet extension
        JingleReason jingleReason = null;

        if (failed && (reasonText != null)) {
            Reason reason = convertReasonCodeToSIPCode(reasonCode);
            if (reason != null) {
                jingleReason = new JingleReason(reason, reasonText, null);
            }
        }

        // XXX maybe add answer/hangup abstract method to MediaAwareCallPeer
        if (peer instanceof CallPeerJabberImpl) {
            try {
                ((CallPeerJabberImpl) peer).hangup(failed, reasonText, jingleReason);
            } catch (NotConnectedException | InterruptedException e) {
                throw new OperationFailedException("Could not hang up", OperationFailedException.GENERAL_ERROR, e);
            }
        }
    }

    /**
     * Converts the codes for hangup from OperationSetBasicTelephony one to the jabber reasons.
     *
     * @param reasonCode the reason code.
     * @return the jabber Response.
     */
    private static Reason convertReasonCodeToSIPCode(int reasonCode)
    {
        switch (reasonCode) {
            case HANGUP_REASON_NORMAL_CLEARING:
                return Reason.success;
            case HANGUP_REASON_ENCRYPTION_REQUIRED:
                return Reason.security_error;
            case HANGUP_REASON_TIMEOUT:
                return Reason.timeout;
            case HANGUP_REASON_BUSY_HERE:
                return Reason.busy;
            default:
                return null;
        }
    }

    /**
     * Implements method <code>answerCallPeer</code> from <code>OperationSetBasicTelephony</code>.
     *
     * @param peer the call peer that we want to answer
     * @throws OperationFailedException if we fail to answer
     */
    public void answerCallPeer(CallPeer peer)
            throws OperationFailedException
    {
        // XXX maybe add answer/hangup abstract method to MediaAwareCallPeer
        if (peer instanceof CallPeerJabberImpl)
            ((CallPeerJabberImpl) peer).answer();
    }

    /**
     * Closes all active calls. And releases resources.
     */
    public void shutdown()
    {
        Timber.log(TimberLog.FINER, "Ending all active calls.");
        Iterator<CallJabberImpl> activeCalls = this.activeCallsRepository.getActiveCalls();

        // this is fast, but events aren't triggered ...
        // jingleManager.disconnectAllSessions();

        // go through all active calls.
        while (activeCalls.hasNext()) {
            CallJabberImpl call = activeCalls.next();
            Iterator<CallPeerJabberImpl> callPeers = call.getCallPeers();

            // go through all call peers and say bye to every one.
            while (callPeers.hasNext()) {
                CallPeer peer = callPeers.next();
                try {
                    hangupCallPeer(peer);
                } catch (Exception ex) {
                    Timber.w(ex, "Failed to properly hangup peer %s", peer);
                }
            }
        }
    }

    /**
     * Subscribes us to notifications about incoming jingle packets.
     */
    private void subscribeForJinglePackets()
    {
        mConnection.registerIQRequestHandler(setRequestHandler);
    }

    /**
     * Unsubscribes us from notifications about incoming jingle packets.
     */
    private void unsubscribeForJinglePackets()
    {
        if ((mConnection != null) && (setRequestHandler != null)) {
            mConnection.unregisterIQRequestHandler(setRequestHandler);
        }
    }

    /**
     * Tests whether or not the specified packet should be handled by this operation set. This
     * method is called by smack prior to packet delivery and it would only accept
     * <code>Jingle</code>s that are either session initiations with RTP content or belong to
     * sessions that are already handled by this operation set.
     *
     * @param packet the packet to test.
     * @return true if and only if <code>packet</code> passes the filter.
     */
    @Override
    public boolean accept(Stanza packet)
    {
        // We handle Jingle and SessionIQ.
        if (!(packet instanceof Jingle)) {
            String packetID = packet.getStanzaId();
            AbstractCallPeer<?, ?> callPeer = activeCallsRepository.findCallPeerBySessInitPacketID(packetID);

            if (callPeer != null) {
                /*
                 * packet is a response to a Jingle call but is not a Jingle so it is for sure an
                 * error (peer does not support Jingle or does not belong to our roster)
                 */
                StanzaError error = packet.getError();
                if (error != null) {
                    String errorMessage = error.getConditionText();
                    Timber.e("Received an error: code = %s: message = %s", error.getCondition(), errorMessage);

                    String message;
                    if (errorMessage == null) {
                        Roster roster = Roster.getInstanceFor(mConnection);
                        BareJid packetFrom = packet.getFrom().asBareJid();

                        message = "Service unavailable";
                        if (!roster.contains(packetFrom)) {
                            message += ": try adding the contact " + packetFrom.toString() + " to your contact list first.";
                        }
                    }
                    else
                        message = errorMessage;
                    callPeer.setState(CallPeerState.FAILED, message);
                }
            }
            return false;
        }
        // we accept only session-initiate dealing with RtpDescription
        Jingle jingle = (Jingle) packet;
        if (jingle.getAction() == JingleAction.session_initiate) {
            for (JingleContent content : jingle.getContents()) {
                if (content.getFirstChildElement(RtpDescription.class) != null)
                    return true;
            }
            return false;

        }

        // if this is not a session-initiate, we'll only take it if we've already seen its session ID.
        String sid = jingle.getSid();
        return (activeCallsRepository.findSID(sid) != null);
    }

    /**
     * Handler for Jabber incoming Jingle request.
     */
    private class JingleIqSetRequestHandler extends AbstractIqRequestHandler
    {
        // setup for Jingle IQRequest event
        protected JingleIqSetRequestHandler()
        {
            super(Jingle.ELEMENT, Jingle.NAMESPACE, IQ.Type.set, Mode.async);
        }

        @Override
        public IQ handleIQRequest(IQ iq)
        {
            /*
             * To prevent hijacking sessions from other Jingle-based features such as file
             * transfer, we should send the ack only if this is a session-initiate with RTP
             * content or if we are the owners of the packet's SID.
             */

            // first ack all Jingle "set" requests.
            IQ ack = IQ.createResultIQ(iq);
            try {
                mConnection.sendStanza(ack);
            } catch (NotConnectedException | InterruptedException e) {
                Timber.e("Error while sending Jingle IQ reply: %s", e.getMessage());
            }

            try {
                if (iq instanceof Jingle) {
                    Jingle jingle = (Jingle) iq;

                    // let's first see whether we have a peer that's concerned by this IQ
                    CallPeerJabberImpl callPeer = activeCallsRepository.findCallPeer(jingle.getSid());
                    if (callPeer == null) {
                        processJingleSynchronize(jingle);
                    }
                    else {
                        processJingle(jingle, callPeer);
                    }
                }
            } catch (Throwable t) {
                String packetClass = iq.getClass().getSimpleName();
                Timber.e(t, "Error handling incoming IQ: %s (%s)", packetClass, iq.getStanzaId());
                /*
                 * The Javadoc on ThreadDeath says: If ThreadDeath is caught by a method, it is
                 * important that it be rethrown so that the thread actually dies.
                 */
                if (t instanceof ThreadDeath)
                    throw (ThreadDeath) t;
            }
            return null;
        }
    }

    /**
     * Analyzes the <code>jingle</code>'s action and passes it to the corresponding handler.
     *
     * @param jingle the {@link Jingle} packet we need to be analyzing.
     */
    private synchronized void processJingleSynchronize(final Jingle jingle)
    {
        JingleAction action = jingle.getAction();
        Timber.d("### Processing Jingle IQ  %s: (%s) synchronized", jingle.getStanzaId(), jingle.getAction());
        switch (action) {
            case session_initiate:
                // Initiator attribute is RECOMMENDED but not REQUIRED attribute for Jingle "session-initiate".
                // When Initiator attribute is not present copy the value from IQ "from" attribute. Allow per XEP-0166
                if (jingle.getInitiator() == null) {
                    jingle.setInitiator(jingle.getFrom().asEntityFullJidIfPossible());
                }

//                StartMutedExtension startMutedExt = jingle.getExtension(StartMutedExtension.class);
//                if (startMutedExt != null) {
//                    ProtocolProviderServiceJabberImpl protocolProvider = getProtocolProvider();
//
//                    OperationSetJitsiMeetToolsJabberImpl operationSetJitsiMeetTools
//                            = (OperationSetJitsiMeetToolsJabberImpl) protocolProvider.getOperationSet(OperationSetJitsiMeetTools.class);
//
//                    if (operationSetJitsiMeetTools != null) {
//                        boolean[] startMutedFlags = {
//                                Boolean.parseBoolean(startMutedExt.getAttributeValue(
//                                        StartMutedExtension.AUDIO_ATTRIBUTE_NAME)),
//
//                                Boolean.parseBoolean(startMutedExt.getAttributeValue(
//                                        StartMutedExtension.VIDEO_ATTRIBUTE_NAME))
//                        };
//                        operationSetJitsiMeetTools.notifySessionStartMuted(startMutedFlags);
//                    }
//                    else {
//                        Timber.w("StartMutedPacketExtension not handled! OperationSetJitsiMeetTools not available.");
//                    }
//                }

                CallJabberImpl call = null;
                SdpTransfer transfer = jingle.getExtension(SdpTransfer.class);
                if (transfer != null) {
                    String sid = transfer.getSid();
                    if (sid != null) {
                        CallJabberImpl attendantCall = activeCallsRepository.findSID(sid);
                        if (attendantCall != null) {
                            CallPeerJabberImpl attendant = attendantCall.getPeer(sid);
                            // Check and proceed if we are legally involved in the session.
                            if ((attendant != null)
                                    && transfer.getFrom().isParentOf(attendant.getPeerJid())
                                    && transfer.getTo().isParentOf(protocolProvider.getOurJID())) {
                                call = attendantCall;
                            }
                        }
                    }
                }

                CallIdExtension callidExt = jingle.getExtension(CallIdExtension.class);
                if (callidExt != null) {
                    String callid = callidExt.getText();
                    if (callid != null)
                        call = activeCallsRepository.findCallId(callid);
                }
                if ((transfer != null) && (callidExt != null))
                    Timber.w("Received a session-initiate with both 'transfer' and 'callid' extensions. Ignored 'transfer' and used 'callid'.");

                // start init new call if not already in call conference
                if (call == null) {
                    call = new CallJabberImpl(this, jingle.getSid());
                    /*
                     * cmeng: 20200622
                     * Must deployed method synchronized and update the new callPeer to activeCallsRepository asap;
                     * Otherwise peer sending trailing standalone transport-info (not part of session-initiate)
                     * (e.g. conversations ~ 60ms) will be in race-condition; the transport-info is received before
                     * the callPeer has been initialized and hence not being processed at all.
                     */
                    FullJid remoteParty = jingle.getInitiator();
                    final CallPeerJabberImpl callPeer = new CallPeerJabberImpl(remoteParty, call, jingle);
                    call.addCallPeer(callPeer);

                    /*
                     * cmeng (20200611): change to merged trailing transport-info's with the session-initiate
                     * before processing i.e. not using the next synchronous method call.
                     */
                    this.processSessionInitiate(call, callPeer, jingle);

                    /*
                     * cmeng (20200602) - must process to completion if transport-info is send separately,
                     * otherwise IceUdpTransportManager#startConnectivityEstablishment will fail
                     * (cpeList is empty) and Ice Agent for the media is not initialized properly
                     */
                    // call.processSessionInitiate(jingle, callPeer);
                }
                break;

            case transport_info:
                // Assume callPeer has been setup in synchronise session-initiate; However callPeer may be null if
                // the caller prematurely terminate the call or caller sends transport-info before session-initiate.
                CallPeerJabberImpl callPeer = activeCallsRepository.findCallPeer(jingle.getSid());
                processTransportInfo(callPeer, jingle);
                break;

            case session_terminate:
                // From remote: nothing else needs to do if session is already own terminated
                contentMedias = null;
                break;

            // Currently not handle session_info e.g. ringing; when the call is terminated
            case session_info:
            default:
                Timber.w("Received unhandled Jingle IQ: %s. Action: %s", jingle.getStanzaId(), action);
        }
    }

    /**
     * Analyzes the <code>jingle</code>'s action and passes it to the corresponding handler.
     * the rest of these cases deal with exiting peers
     *
     * @param jingle the {@link Jingle} packet we need to be analyzing.
     */
    private void processJingle(final Jingle jingle, final CallPeerJabberImpl callPeer)
            throws NotConnectedException, InterruptedException
    {
        JingleAction action = jingle.getAction();
        Timber.d("### Processing Jingle IQ (%s); callPeer: %s", action, callPeer.getAddress());
        switch (action) {
            case session_terminate:
                callPeer.processSessionTerminate(jingle);
                break;
            case session_accept:
                jingleSI = null;
                processSessionAccept(callPeer, jingle);
                break;
            case security_info:
                SessionInfo info = jingle.getSessionInfo();
                // change status.
                if (info != null) {
                    callPeer.processSessionInfo(info);
                }
                else {
                    SdpTransfer transfer = jingle.getExtension(SdpTransfer.class);
                    if (transfer != null) {
                        if (transfer.getFrom() == null) {
                            transfer = SdpTransfer.builder()
                                    .addAttributes(transfer.getAttributes())
                                    .setFrom(jingle.getFrom())
                                    .build();
                        }
                        try {
                            callPeer.processTransfer(transfer);
                        } catch (OperationFailedException ofe) {
                            Timber.e(ofe, "Failed to transfer to %s", transfer.getTo());
                        }
                    }
                    CoinExtension coinExt = jingle.getExtension(CoinExtension.class);
                    if (coinExt != null) {
                        callPeer.setConferenceFocus(coinExt.isFocus());
                    }
                }
                break;

            case content_accept:
                callPeer.processContentAccept(jingle);
                break;

            case content_add:
                callPeer.processContentAdd(jingle);
                break;

            case content_modify:
                callPeer.processContentModify(jingle);
                break;

            case content_reject:
                callPeer.processContentReject(jingle);
                break;

            case content_remove:
                callPeer.processContentRemove(jingle);
                break;

            // Transport_info send by peer e.g. conversations is too fast and before callPeer is properly initialized
            case transport_info:
                processTransportInfo(callPeer, jingle);
                break;

            case source_add:
                callPeer.processSourceAdd(jingle);
                break;

            case source_remove:
                callPeer.processSourceRemove(jingle);
                break;

            case session_info:
                // Currently not handle e.g. ringing
                break;

            default:
                Timber.e("Received unhandled Jingle IQ id: %s. Action: %s", jingle.getStanzaId(), action);
        }
    }

    /**
     * For the following parameters usages:
     *
     * @see #processSessionAccept(CallPeerJabberImpl, Jingle)
     * @see #processSessionInitiate(CallJabberImpl, CallPeerJabberImpl, Jingle)
     * @see #processTransportInfo(CallPeerJabberImpl, Jingle)
     */
    private Jingle jingleSI = null;
    // Use to cache transport-info's received prior to session-initiate from conversations
    private Map<String, IceUdpTransport> mediaTransports = null;

    // Flag to indicate if the transport-info for session-accept has been processed.
    // Only start to process jingleSA after at least one candidate/mediaType has been processed.
    private boolean mTIProcess = false;
    private Jingle jingleSA = null;

    // A reference of the media to be processed for transport-info to avoid media prune by ice4j;
    private List<String> contentMedias = null;
    // Use as cache for at least one candidate/mediaType before process.
    // private Jingle jingleTransports = null;

    /**
     * Proceed to process session-accept only after at least one candidate/mediaType has been process.
     * This is to avoid media stream being pruned by ice4j;
     * Conversations sends only one candidate per transport-info stanza before and/or after session-accept;
     *
     * @param callPeer CallPeerJabberImpl
     * @param jingle Jingle element of session-accept
     * @see #processTransportInfo(CallPeerJabberImpl, Jingle)
     * @see IceUdpTransportManager#startConnectivityEstablishment(Map remote)
     */
    private void processSessionAccept(CallPeerJabberImpl callPeer, Jingle jingle)
    {
        if (!mTIProcess) {
            jingleSA = jingle;
        }
        else {
            jingleSA = null;
            try {
                callPeer.processSessionAccept(jingle);
            } catch (NotConnectedException | InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Proceed to process session-initiate upon received, independent whether it contains the required transport candidates;
     * Conversations sends only one candidate per transport-info stanza before and after session-initiate;
     * aTalk is able to handle trailing transport-info conditions even the processSessionInitiate() has started
     * For leading transport-info's: if any, merged cached mediaTransports() before processing; need priority sorting
     *
     * Note: Process in new thread so jingleSI can get updated with trailing transport-info received. Found that
     * actual startConnectivityEstablishment happen much later:
     * a. ~4s if transport-info is embedded in session-initiate jingle
     * b. ~10 if trailing transport-info is used e.g. conversations with last candidate at ~8s
     *
     * @param call CallJabberImpl
     * @param callPeer CallPeerJabberImpl
     * @param jingle Jingle element of session-initiate
     * @see IceUdpTransportManager#startConnectivityEstablishment(Map remote)
     * @see #processTransportInfo(CallPeerJabberImpl, Jingle)
     */
    private void processSessionInitiate(CallJabberImpl call, CallPeerJabberImpl callPeer, Jingle jingle)
    {
        jingleSI = jingle;
        new Thread()
        {
            @Override
            public void run()
            {
                call.processSessionInitiate(jingle, callPeer);
                jingleSI = null;
            }
        }.start();
    }

    /**
     * Process the remote transport-info received during call for both initiator and responder
     * # For incoming session-initiate: merge the pre/trailing transport-info candidates before process the stanza;
     * allowing a time window of 200ms for collections; late comer will handle individually
     *
     * # For session-accept: all the transport-info's must be combined and processed in one go
     * i.e. combined all transport-info before calling processOfferTransportInfo();
     * otherwise ice4j will prune any unspecified media from the ice agent
     *
     * 1. aTalk/Jitsi implementations:
     * a. transport-info's are embedded within session-initiate stanza;
     * b. transport-info's are sent prior to session-accept stanza; - can be processed individually without problem
     *
     * 2. Conversations implementations:
     * a. transport-info's are sent in single candidate/transport-info per media after session-initiate stanza;
     * b. transport-info's are sent in single candidate/transport-info per media prior to session-accept stanza;
     *
     * @param jingleTransport Jingle IQ stanza with action='transport-info'
     * @param callPeer CallPeerJabberImpl to be targeted
     */
    private void processTransportInfo(CallPeerJabberImpl callPeer, Jingle jingleTransport)
    {
        // Cached all transport-info received prior to session-initiate e.g. Conversations sends transport-info's before session-initiate
        if (callPeer == null) {
            if (mediaTransports == null) {
                mediaTransports = new LinkedHashMap<>();
            }
            for (JingleContent content : jingleTransport.getContents()) {
                String contentName = content.getName();
                IceUdpTransport mediaTransport = mediaTransports.get(contentName);
                IceUdpTransport contentTransport = content.getFirstChildElement(IceUdpTransport.class);

                if (mediaTransport == null) {
                    mediaTransports.put(contentName, contentTransport);
                }
                else {
                    for (IceUdpTransportCandidate candidate : contentTransport.getCandidateList()) {
                        mediaTransport.addChildElement(candidate);
                    }
                }
            }
            return;
        }

        // Merge the remote transport-info candidate's to the session-initiate of the same content element
        // for both leading and trailing transport-info's for session-initiate processing
        if (jingleSI != null) {
            if (mediaTransports != null) {
                Timber.d("### Process session-initiate transport (leading): %s", mediaTransports.keySet());
                for (JingleContent contentSI : jingleSI.getContents()) {
                    IceUdpTransport mediaTransport = mediaTransports.get(contentSI.getName());
                    if (mediaTransport != null) {
                        IceUdpTransport contentTransport = contentSI.getFirstChildElement(IceUdpTransport.class);
                        for (IceUdpTransportCandidate candidate : mediaTransport.getCandidateList()) {
                            contentTransport.addChildElement(candidate);
                        }
                    }
                }
                mediaTransports = null;
            }

            Timber.d("### Process session-initiate transport (trailing): %s", jingleTransport.getStanzaId());
            for (JingleContent content : jingleTransport.getContents()) {
                String contentName = content.getName();
                for (JingleContent contentSI : jingleSI.getContents()) {
                    if (contentName.equals(contentSI.getName())) {
                        IceUdpTransport contentTransport = contentSI.getFirstChildElement(IceUdpTransport.class);
                        for (IceUdpTransport transport : content.getChildElements(IceUdpTransport.class)) {
                            for (IceUdpTransportCandidate candidate : transport.getCandidateList()) {
                                contentTransport.addChildElement(candidate);
                            }
                        }
                    }
                }
            }
        }
        else {
            if (contentMedias == null)
                contentMedias = callPeer.getContentMedia();

            for (JingleContent content : jingleTransport.getContents()) {
                contentMedias.remove(content.getName());
            }
            mTIProcess = contentMedias.isEmpty();

            try {
                Timber.d("### Process Jingle transport-info (session-accept) media: %s; %s",
                        contentMedias, jingleSA);
                callPeer.processOfferTransportInfo(jingleTransport);
                if (jingleSA != null) {
                    callPeer.processSessionAccept(jingleSA);
                    jingleSA = null;
                }
            } catch (NotConnectedException | InterruptedException e) {
                Timber.w("Process transport-info error: %s", e.getMessage());
            }

            // Change to use the above approach (clearer and faster)
//            if (contentMedias.size() > 1) {
//                if (jingleTransports == null) {
//                    jingleTransports = jingleTransport;
//                }
//                else {
//                    boolean added = false;
//                    for (JingleContent contentL : jingleTransports.getContents()) {
//                        for (JingleContent contentR : jingleTransport.getContents()) {
//                            if (contentR.getName().equals(contentL.getName())) {
//                                IceUdpTransport transportL = contentL.getFirstChildElement(IceUdpTransport.class);
//                                IceUdpTransport transportR = contentR.getFirstChildElement(IceUdpTransport.class);
//                                for (IceUdpTransportCandidate candidate : transportR.getCandidateList()) {
//                                    transportL.addChildElement(candidate);
//                                    added = true;
//                                }
//                            }
//                        }
//                        if (added)
//                            break;
//                    }
//                    if (!added) {
//                        for (JingleContent contentR : jingleTransport.getContents()) {
//                            jingleTransports.addJingleContent(contentR);
//                        }
//                    }
//                }
//            }
//            else {
//                jingleTransports = jingleTransport;
//            }
//
//            if ((jingleTransports != null) && jingleTransports.getContents().size() >= contentMedias.size()) {
//                Timber.d("### Process session-accept transport media: %s; %s,", contentMedias, jingleSA);
//                // Must cleanup after processed, so subsequent transport-info is handled individually
//                contentMedias.clear();
//                mTIProcess = true;
//                try {
//                    callPeer.processOfferTransportInfo(jingleTransports);
//                    if (jingleSA != null) {
//                        callPeer.processSessionAccept(jingleSA);
//                        jingleSA = null;
//                    }
//                } catch (NotConnectedException | InterruptedException e) {
//                    Timber.w("Process transport-info error: %s", e.getMessage());
//                }
//            }
        }
    }

    /**
     * Returns a reference to the {@link ActiveCallsRepositoryJabberImpl} that we are currently using.
     *
     * @return a reference to the {@link ActiveCallsRepositoryJabberImpl} that we are currently using.
     */
    protected ActiveCallsRepositoryJabberImpl getActiveCallsRepository()
    {
        return activeCallsRepository;
    }

    /**
     * Returns the protocol provider that this operation set belongs to.
     *
     * @return a reference to the <code>ProtocolProviderService</code> that created this operation set.
     */
    public ProtocolProviderServiceJabberImpl getProtocolProvider()
    {
        return protocolProvider;
    }

    /**
     * Gets the secure state of the call session in which a specific peer is involved
     *
     * @param peer the peer for who the call state is required
     * @return the call state
     */
    public boolean isSecure(CallPeer peer)
    {
        return ((MediaAwareCallPeer<?, ?, ?>) peer).getMediaHandler().isSecure();
    }

    /**
     * Transfers (in the sense of call transfer) a specific <code>CallPeer</code> to a specific callee
     * address which already participates in an active <code>Call</code>.
     *
     * The method is suitable for providing the implementation of attended call transfer (though no
     * such requirement is imposed).
     *
     * @param peer the <code>CallPeer</code> to be transferred to the specified callee address
     * @param target the address in the form of <code>CallPeer</code> of the callee to transfer <code>peer</code> to
     * @throws OperationFailedException if something goes wrong
     * @see OperationSetAdvancedTelephony#transfer(CallPeer, CallPeer)
     */
    public void transfer(CallPeer peer, CallPeer target)
            throws OperationFailedException
    {
        CallPeerJabberImpl jabberTarget = (CallPeerJabberImpl) target;
        EntityFullJid to = getFullCalleeURI(jabberTarget.getPeerJid());
        /*
         * XEP-0251: Jingle Session Transfer says: Before doing [attended transfer], the attendant
         * SHOULD verify that the callee supports Jingle session transfer.
         */
        try {
            DiscoverInfo discoverInfo = protocolProvider.getDiscoveryManager().discoverInfo(to);
            if (!discoverInfo.containsFeature(
                    ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE_TRANSFER_0)) {
                throw new OperationFailedException("Callee " + to + " does not support"
                        + " XEP-0251: Jingle Session Transfer", OperationFailedException.INTERNAL_ERROR);
            }
        } catch (XMPPException
                | InterruptedException
                | NoResponseException
                | NotConnectedException xmppe) {
            Timber.w(xmppe, "Failed to retrieve DiscoverInfo for %s", to);
        }
        transfer(peer, to, jabberTarget.getSid());
    }

    /**
     * Transfers (in the sense of call transfer) a specific <code>CallPeer</code> to a specific callee
     * address which may or may not already be participating in an active <code>Call</code>.
     *
     * The method is suitable for providing the implementation of unattended call transfer (though
     * no such requirement is imposed).
     *
     * @param peer the <code>CallPeer</code> to be transferred to the specified callee address
     * @param target the address of the callee to transfer <code>peer</code> to
     * @throws OperationFailedException if something goes wrong
     * @see OperationSetAdvancedTelephony#transfer(CallPeer, String)
     */
    public void transfer(CallPeer peer, String target)
            throws OperationFailedException
    {
        EntityFullJid targetJid = getFullCalleeURI(peer.getPeerJid());
        transfer(peer, targetJid, null);
    }

    /**
     * Transfer (in the sense of call transfer) a specific <code>CallPeer</code> to a specific callee
     * address which may optionally be participating in an active <code>Call</code>.
     *
     * @param peer the <code>CallPeer</code> to be transferred to the specified callee address
     * @param to the address of the callee to transfer <code>peer</code> to
     * @param sid the Jingle session ID of the active <code>Call</code> between the local peer and the
     * callee in the case of attended transfer; <code>null</code> in the case of unattended transfer
     * @throws OperationFailedException if something goes wrong
     */
    private void transfer(CallPeer peer, EntityFullJid to, String sid)
            throws OperationFailedException
    {
        EntityFullJid caller = getFullCalleeURI(peer.getPeerJid());
        try {
            DiscoverInfo discoverInfo = protocolProvider.getDiscoveryManager().discoverInfo(caller);
            if (!discoverInfo.containsFeature(
                    ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE_TRANSFER_0)) {
                throw new OperationFailedException("Caller " + caller + " does not support"
                        + " XEP-0251: Jingle Session Transfer", OperationFailedException.INTERNAL_ERROR);
            }
        } catch (XMPPException
                | InterruptedException
                | NoResponseException
                | NotConnectedException xmppe) {
            Timber.w(xmppe, "Failed to retrieve DiscoverInfo for %s", to);
        }
        ((CallPeerJabberImpl) peer).transfer(to, sid);
    }

    /**
     * Transfer authority used for interacting with user for unknown calls and the requests for transfer.
     *
     * @param authority transfer authority.
     */
    public void setTransferAuthority(TransferAuthority authority)
    {
    }
}
