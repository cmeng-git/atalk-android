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
import org.jivesoftware.smackx.disco.packet.DiscoverInfo;
import org.jxmpp.jid.*;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;
import org.jxmpp.util.XmppStringUtils;
import org.xmpp.extensions.condesc.CallIdExtension;
import org.xmpp.extensions.jingle.*;
import org.xmpp.extensions.jingle.element.*;
import org.xmpp.extensions.jitsimeet.StartMutedExtension;

import java.util.*;

import timber.log.Timber;

/**
 * Implements all call management logic and exports basic telephony support by implementing
 * <tt>OperationSetBasicTelephony</tt>.
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
     * A reference to the <tt>ProtocolProviderServiceJabberImpl</tt> instance that created us.
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
     * @param protocolProvider a reference to the <tt>ProtocolProviderServiceJabberImpl</tt> instance that created us.
     */
    public OperationSetBasicTelephonyJabberImpl(ProtocolProviderServiceJabberImpl protocolProvider)
    {
        this.protocolProvider = protocolProvider;
        this.protocolProvider.addRegistrationStateChangeListener(this);
    }

    /**
     * Implementation of method <tt>registrationStateChange</tt> from interface RegistrationStateChangeListener
     * for setting up (or down) our <tt>JingleManager</tt> when an <tt>XMPPConnection</tt> is available
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
     * Creates a new <tt>Call</tt> and invites a specific <tt>CallPeer</tt> to it given by her <tt>String</tt> URI.
     *
     * @param callee the address of the callee who we should invite to a new <tt>Call</tt>
     * @param conference the <tt>CallConference</tt> in which the newly-created <tt>Call</tt> is to participate
     * @return a newly created <tt>Call</tt>. The specified <tt>callee</tt> is available in the
     * <tt>Call</tt> as a <tt>CallPeer</tt>
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
     * Creates a new <tt>CallJabberImpl</tt> and initiates a jingle session to the JID obtained
     * from the <tt>uri</tt> of <tt>cd</tt>.
     *
     * If <tt>cd</tt> contains a <tt>callid</tt>, adds the "callid" element as an extension to the session-initiate IQ.
     * Uses the supported transports of <tt>cd</tt>
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
     * @param call the <tt>CallJabberImpl</tt> that will be used to initiate the call
     * @param calleeAddress the address of the callee that we'd like to connect with.
     * @return the <tt>CallPeer</tt> that represented by the specified uri. All following state
     * change events will be delivered through that call peer. The <tt>Call</tt> that this
     * peer is a member of could be retrieved from the <tt>CallPeer</tt> instance with the
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
     * @param call the <tt>CallJabberImpl</tt> that will be used to initiate the call
     * @param calleeAddress the address of the callee that we'd like to connect with.
     * @param sessionInitiateExtensions a collection of additional and optional <tt>ExtensionElement</tt>s to be
     * added to the <tt>session-initiate</tt> {@link Jingle} which is to init the specified <tt>call</tt>
     * @return the <tt>CallPeer</tt> that represented by the specified uri. All following state
     * change events will be delivered through that call peer. The <tt>Call</tt> that this
     * peer is a member of could be retrieved from the <tt>CallPeer</tt> instance with the
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
     * @param call the <tt>CallJabberImpl</tt> that will be used to initiate the call
     * @param calleeAddress the address of the callee that we'd like to connect with.
     * @param fullCalleeURI the full Jid address, which if specified would explicitly initiate a call to this full address
     * @param sessionInitiateExtensions a collection of additional and optional <tt>ExtensionElement</tt>s to be
     * added to the <tt>session-initiate</tt> {@link Jingle} which is to init the specified <tt>call</tt>
     * @return the <tt>CallPeer</tt> that represented by the specified uri. All following state
     * change events will be delivered through that call peer. The <tt>Call</tt> that this
     * peer is a member of could be retrieved from the <tt>CallPeer</tt> instance with the
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
     * Discovers the resource for <tt>calleeAddress</tt> with the highest priority which supports
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
     * @return the full callee URI for the specified <tt>calleeAddress</tt>
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
     * @param sid the Jingle session ID of the active <tt>Call</tt> between the local peer and the
     * callee in the case of attended transfer; <tt>null</tt> in the case of unattended transfer
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
     * Puts the specified <tt>CallPeer</tt> on or off hold.
     *
     * @param peer the <tt>CallPeer</tt> to be put on or off hold
     * @param on <tt>true</tt> to have the specified <tt>CallPeer</tt> put on hold; <tt>false</tt>, otherwise
     * @throws OperationFailedException if we fail to send the "hold" message.
     */
    private void putOnHold(CallPeer peer, boolean on)
            throws OperationFailedException
    {
        if (peer instanceof CallPeerJabberImpl)
            ((CallPeerJabberImpl) peer).putOnHold(on);
    }

    /**
     * Ends the call with the specified <tt>peer</tt>.
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
     * Ends the call with the specified <tt>peer</tt>.
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
        JingleReason reasonPacketExt = null;

        if (failed && (reasonText != null)) {
            Reason reason = convertReasonCodeToSIPCode(reasonCode);
            if (reason != null) {
                reasonPacketExt = new JingleReason(reason, reasonText, null);
            }
        }

        // XXX maybe add answer/hangup abstract method to MediaAwareCallPeer
        if (peer instanceof CallPeerJabberImpl) {
            try {
                ((CallPeerJabberImpl) peer).hangup(failed, reasonText, reasonPacketExt);
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
                return Reason.SUCCESS;
            case HANGUP_REASON_ENCRYPTION_REQUIRED:
                return Reason.SECURITY_ERROR;
            case HANGUP_REASON_TIMEOUT:
                return Reason.TIMEOUT;
            case HANGUP_REASON_BUSY_HERE:
                return Reason.BUSY;
            default:
                return null;
        }
    }

    /**
     * Implements method <tt>answerCallPeer</tt> from <tt>OperationSetBasicTelephony</tt>.
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
     * <tt>Jingle</tt>s that are either session initiations with RTP content or belong to
     * sessions that are already handled by this operation set.
     *
     * @param packet the packet to test.
     * @return true if and only if <tt>packet</tt> passes the filter.
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

        Jingle jingle = (Jingle) packet;

        if (jingle.getAction() == JingleAction.SESSION_INITIATE) {
            // we only accept session-initiate-s dealing RTP
            return jingle.containsContentChildOfType(RtpDescriptionExtension.class);
        }

        String sid = jingle.getSid();

        // if this is not a session-initiate we'll only take it if we've already seen its session ID.
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
                Timber.e(t, "Error while handling incoming: %s", packetClass);
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
     * Analyzes the <tt>jingle</tt>'s action and passes it to the corresponding handler.
     *
     * @param jingle the {@link Jingle} packet we need to be analyzing.
     */
    private synchronized void processJingleSynchronize(final Jingle jingle)
    {
        JingleAction action = jingle.getAction();
        Timber.d("### Processing Jingle IQ (%s) synchronized", jingle.getAction());
        switch (action) {
            case SESSION_INITIATE:

                // Initiator attribute is RECOMMENDED but not REQUIRED attribute for Jingle "session-initiate".
                // When Initiator attribute is not present copy the value from IQ "from" attribute. Allow per XEP-0166
                if (jingle.getInitiator() == null) {
                    jingle.setInitiator(jingle.getFrom().asEntityFullJidIfPossible());
                }

//                StartMutedExtension startMutedExt = jingle.getExtension(StartMutedExtension.class);
//                if (startMutedExt != null) {
//                    ProtocolProviderServiceJabberImpl protocolProvider
//                            = getProtocolProvider();
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
//
//                        operationSetJitsiMeetTools.notifySessionStartMuted(startMutedFlags);
//                    }
//                    else {
//                        Timber.w("StartMutedPacketExtension not handled! OperationSetJitsiMeetTools not available.");
//                    }
//                }

                CallJabberImpl call = null;
                TransferExtension transfer = jingle.getExtension(TransferExtension.class);
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
                    setContentMedia(jingle);

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

            case TRANSPORT_INFO:
                // Assume callPeer has been set in synchronise session-initiate.
                CallPeerJabberImpl callPeer = activeCallsRepository.findCallPeer(jingle.getSid());
                processTransportInfo(callPeer, jingle);
                break;

            default:
                Timber.e("Received unhandled Jingle IQ id: %s. Action: %s", jingle.getStanzaId(), action);
        }
    }


    /**
     * Analyzes the <tt>jingle</tt>'s action and passes it to the corresponding handler.
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
            case SESSION_TERMINATE:
                callPeer.processSessionTerminate(jingle);
                break;
            case SESSION_ACCEPT:
                setContentMedia(jingle);
                callPeer.processSessionAccept(jingle);
                break;
            case SESSION_INFO:
                SessionInfoExtension info = jingle.getSessionInfo();
                // change status.
                if (info != null) {
                    callPeer.processSessionInfo(info);
                }
                else {
                    TransferExtension transfer = jingle.getExtension(TransferExtension.class);
                    if (transfer != null) {
                        if (transfer.getFrom() == null)
                            transfer.setFrom(jingle.getFrom());
                        try {
                            callPeer.processTransfer(transfer);
                        } catch (OperationFailedException ofe) {
                            Timber.e(ofe, "Failed to transfer to %s", transfer.getTo());
                        }
                    }

                    CoinExtension coinExt = jingle.getExtension(CoinExtension.class);
                    if (coinExt != null) {
                        callPeer.setConferenceFocus(Boolean.parseBoolean(
                                coinExt.getAttributeAsString(CoinExtension.ISFOCUS_ATTR_NAME)));
                    }
                }
                break;

            case CONTENT_ACCEPT:
                callPeer.processContentAccept(jingle);
                break;

            case CONTENT_ADD:
                callPeer.processContentAdd(jingle);
                break;

            case CONTENT_MODIFY:
                callPeer.processContentModify(jingle);
                break;

            case CONTENT_REJECT:
                callPeer.processContentReject(jingle);
                break;

            case CONTENT_REMOVE:
                callPeer.processContentRemove(jingle);
                break;

            // Transport_info send by peer e.g. conversations is too fast and before callPeer is properly initialized
            case TRANSPORT_INFO:
                processTransportInfo(callPeer, jingle);
                break;

            case SOURCEADD:
                callPeer.processSourceAdd(jingle);
                break;

            case SOURCEREMOVE:
                callPeer.processSourceRemove(jingle);
                break;

            default:
                Timber.e("Received unhandled Jingle IQ id: %s. Action: %s", jingle.getStanzaId(), action);
        }
    }

    private static CallJabberImpl mCall = null;
    private static Jingle jingleSI;
    private static Jingle jingleTransports = null;
    private static List<String> contentMedias = new ArrayList<>();

    /**
     * Keep a reference of the media to be processed for transport-info to avoid media prune;
     * Applicable for training transport-info sedning only e.g. conversations
     *
     * @param jingle Jingle element of session-initiate or session-accept
     * @see #processTransportInfo(CallPeerJabberImpl, Jingle)
     */
    private void setContentMedia(Jingle jingle)
    {
        contentMedias.clear();
        List<JingleContent> jingleContents = jingle.getContents();
        for (JingleContent content : jingleContents) {
            contentMedias.add(content.getName());
        }
    }

    /**
     * If the session-initiate jingle does not contain the transport candidates, then wait for the trailing
     * transport-info's before process, otherwise proceed to execute in new thread
     *
     * @param call CallJabberImpl
     * @param callPeer CallPeerJabberImpl
     * @param jingle Jingle element of session-initiate or session-accept
     */
    private void processSessionInitiate(CallJabberImpl call, CallPeerJabberImpl callPeer, Jingle jingle)
    {
        jingleSI = null;
        IceUdpTransportExtension transportExtension = jingle.getContents().get(0).getChildExtension(IceUdpTransportExtension.class);
        if ((transportExtension != null) && !transportExtension.getCandidateList().isEmpty()) {
            new Thread()
            {
                @Override
                public void run()
                {
                    call.processSessionInitiate(jingle, callPeer);
                }
            }.start();
        }
        else {
            mCall = call;
            jingleSI = jingle;
        }
    }

    /**
     * For session-initiate: merge the trailing transport-info candidates before process the stanza.
     * For session-accept: all the transport-info's must be combined and processed in one go;
     * otherwise ice4j will prune any unspecified media from the ice agent i.e.
     * Combined all transport-info before calling processOfferTransportInfo();
     *
     * 1. aTalk/Jitsi implementations:
     * a. transport-info's are embedded within session-initiate stanza;
     * b. transport-info's are sent prior to session-accept stanza; - can be processed individually without problem
     *
     * 2. Conversations implementations:
     * a. transport-info's are sent per media after session-initiate stanza;
     * b. transport-info's are sent per media after session-accept stanza;
     *
     * @param jingleTransport Jingle transport-info stanza
     * @param callPeer CallPeerJabberImpl to be targeted
     */
    private void processTransportInfo(CallPeerJabberImpl callPeer, Jingle jingleTransport)
    {
        if (contentMedias.size() > 1) {
            if (jingleTransports == null) {
                jingleTransports = jingleTransport;
            }
            else {
                jingleTransports.addContent(jingleTransport.getContents().get(0));
            }
        }
        else {
            jingleTransports = jingleTransport;
        }

        // Merge the transport-info to the session-initiate before process (20200616: confirmed by sendStanza())
        if (jingleSI != null) {
            for (JingleContent contents : jingleTransport.getContents()) {
                String nameContent = contents.getName();
                for (JingleContent contentSI : jingleSI.getContents()) {
                    if (nameContent.equals(contentSI.getName())) {
                        IceUdpTransportExtension contentTransport
                                = contentSI.getFirstChildOfType(IceUdpTransportExtension.class);
                        for (IceUdpTransportExtension transport
                                : contents.getChildExtensionsOfType(IceUdpTransportExtension.class)) {
                            for (CandidateExtension candidate : transport.getCandidateList()) {
                                contentTransport.addCandidate(candidate);
                            }
                        }
                    }
                }
            }
        }

        if (jingleTransports.getContents().size() >= contentMedias.size()) {
            if (jingleSI != null) {
                Timber.d("### Process Jingle session-initiate (merged): %s = %s", contentMedias, jingleSI);
                new Thread()
                {
                    @Override
                    public void run()
                    {
                        mCall.processSessionInitiate(jingleSI, callPeer);
                        jingleSI = null;
                    }
                }.start();
            }
            else {
                Timber.d("### Process transport-info (merged) for media type: %s: %s,", contentMedias, jingleTransports.getContents());
                try {
                    callPeer.processOfferTransportInfo(jingleTransports);
                } catch (NotConnectedException | InterruptedException e) {
                    Timber.w("Process transport-info error: %s", e.getMessage());
                }
            }

            // Must cleanup after use for next round
            jingleTransports = null;
            contentMedias.clear();
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
     * @return a reference to the <tt>ProtocolProviderService</tt> that created this operation set.
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
     * Transfers (in the sense of call transfer) a specific <tt>CallPeer</tt> to a specific callee
     * address which already participates in an active <tt>Call</tt>.
     *
     * The method is suitable for providing the implementation of attended call transfer (though no
     * such requirement is imposed).
     *
     * @param peer the <tt>CallPeer</tt> to be transferred to the specified callee address
     * @param target the address in the form of <tt>CallPeer</tt> of the callee to transfer <tt>peer</tt> to
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
     * Transfers (in the sense of call transfer) a specific <tt>CallPeer</tt> to a specific callee
     * address which may or may not already be participating in an active <tt>Call</tt>.
     *
     * The method is suitable for providing the implementation of unattended call transfer (though
     * no such requirement is imposed).
     *
     * @param peer the <tt>CallPeer</tt> to be transferred to the specified callee address
     * @param target the address of the callee to transfer <tt>peer</tt> to
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
     * Transfer (in the sense of call transfer) a specific <tt>CallPeer</tt> to a specific callee
     * address which may optionally be participating in an active <tt>Call</tt>.
     *
     * @param peer the <tt>CallPeer</tt> to be transferred to the specified callee address
     * @param to the address of the callee to transfer <tt>peer</tt> to
     * @param sid the Jingle session ID of the active <tt>Call</tt> between the local peer and the
     * callee in the case of attended transfer; <tt>null</tt> in the case of unattended transfer
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
