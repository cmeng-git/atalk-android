/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import net.java.sip.communicator.impl.protocol.jabber.jinglesdp.JingleUtils;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.CallPeerChangeEvent;
import net.java.sip.communicator.service.protocol.media.MediaAwareCallPeer;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.plugin.timberlog.TimberLog;
import org.atalk.service.neomedia.*;
import org.atalk.util.MediaType;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smackx.disco.packet.DiscoverInfo;
import org.jxmpp.jid.FullJid;
import org.jxmpp.jid.Jid;
import org.xmpp.extensions.colibri.ColibriConferenceIQ;
import org.xmpp.extensions.colibri.SourceExtension;
import org.xmpp.extensions.jingle.*;
import org.xmpp.extensions.jingle.element.*;
import org.xmpp.extensions.jingle.element.JingleContent.Senders;
import org.xmpp.extensions.jitsimeet.SSRCInfoExtension;

import java.lang.reflect.UndeclaredThrowableException;
import java.util.*;

import timber.log.Timber;

/**
 * Implements a Jabber <tt>CallPeer</tt>.
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Boris Grozev
 * @author Eng Chong Meng
 */

public class CallPeerJabberImpl
        extends MediaAwareCallPeer<CallJabberImpl, CallPeerMediaHandlerJabberImpl, ProtocolProviderServiceJabberImpl>
{
    /**
     * If the call is cancelled before session-initiate is sent.
     */
    private boolean cancelled = false;

    /**
     * Synchronization object for candidates available.
     */
    private final Object candSyncRoot = new Object();

    /**
     * If the content-add does not contain candidates.
     */
    private boolean contentAddWithNoCands = false;

    /**
     * If we have processed the session initiate.
     */
    private boolean sessionInitiateProcessed = false;

    /**
     * If we have processed the session accept.
     */
    private boolean sessionAcceptProcessed = false;

    /**
     * Synchronization object. Jingle transport-info processes are hold in waiting state until
     * session-initiate is completed (notifyAll).
     */
    private final Object sessionInitiateSyncRoot = new Object();

    /**
     * Synchronization object for SID.
     */
    private final Object sidSyncRoot = new Object();

    /**
     * The current value of the 'senders' field of the audio content in the Jingle session with this
     * <tt>CallPeer</tt>. <tt>null</tt> should be interpreted as 'both', which is the default in
     * Jingle if the XML attribute is missing.
     */
    private Senders audioSenders = Senders.none;

    /**
     * The current value of the 'senders' field of the video content in the Jingle session with this
     * <tt>CallPeer</tt>. <tt>null</tt> should be interpreted as 'both', which is the default in
     * Jingle if the XML attribute is missing.
     */
    private Senders videoSenders = Senders.none;

    /**
     * Any discovery information that we have for this peer.
     */
    private DiscoverInfo discoverInfo;

    /**
     * The indicator which determines whether this peer has initiated the session.
     */
    private boolean initiator = false;

    /**
     * The jabber address of this peer
     */
    private FullJid mPeerJid;

    /**
     * The {@link IQ} that created the session that this call represents.
     */
    private Jingle sessionInitIQ;

    private XMPPConnection mConnection;

    /**
     * Creates a new call peer with address <tt>peerAddress</tt>.
     *
     * @param peerAddress the Jabber address of the new call peer.
     * @param owningCall the call that contains this call peer.
     */
    public CallPeerJabberImpl(FullJid peerAddress, CallJabberImpl owningCall)
    {
        super(owningCall);
        mPeerJid = peerAddress;
        setMediaHandler(new CallPeerMediaHandlerJabberImpl(this));
        mConnection = getProtocolProvider().getConnection();
    }

    /**
     * Creates a new call peer with address <tt>peerAddress</tt>.
     *
     * @param peerAddress the Jabber address of the new call peer.
     * @param owningCall the call that contains this call peer.
     * @param sessionIQ The session-initiate <tt>Jingle</tt> which was received from <tt>peerAddress</tt>
     * and caused the creation of this <tt>CallPeerJabberImpl</tt>
     */
    public CallPeerJabberImpl(FullJid peerAddress, CallJabberImpl owningCall, Jingle sessionIQ)
    {
        this(peerAddress, owningCall);
        this.sessionInitIQ = sessionIQ;
    }

    /**
     * Send a session-accept <tt>Jingle</tt> to this <tt>CallPeer</tt>
     */
    public synchronized void answer()
            throws OperationFailedException
    {
        Iterable<JingleContent> answer;
        CallPeerMediaHandlerJabberImpl mediaHandler = getMediaHandler();

        // cmeng: added to end ring tone when call from Conversations
        setState(CallPeerState.CONNECTING_INCOMING_CALL);
        try {
            mediaHandler.getTransportManager().wrapupConnectivityEstablishment();
            answer = mediaHandler.generateSessionAccept();
            for (JingleContent c : answer) {
                setSenders(getMediaType(c), c.getSenders());
            }
        } catch (Exception exc) {
            Timber.e(exc, "Failed to answer an incoming call");

            // send an error response
            String reasonText = "Error: " + exc.getMessage();
            Jingle errResp = JingleUtil.createSessionTerminate(
                    mConnection.getUser(), sessionInitIQ.getInitiator(), sessionInitIQ.getSid(),
                    Reason.FAILED_APPLICATION, reasonText);

            setState(CallPeerState.FAILED, reasonText);
            try {
                mConnection.sendStanza(errResp);
            } catch (NotConnectedException | InterruptedException e) {
                throw new OperationFailedException("Could not send session terminate after failing to answer a call",
                        OperationFailedException.REGISTRATION_REQUIRED, e);
            }
            return;
        }

        // Send the session-accept first and start the stream later in case the
        // media relay needs to see it before letting hole punching techniques through.
        // Timber.w(new Exception("Create session accept"));
        Jingle response = JingleUtil.createSessionAccept(mConnection.getUser(), sessionInitIQ, answer);
        try {
            mConnection.sendStanza(response);
        } catch (NotConnectedException | InterruptedException e1) {
            throw new OperationFailedException("Could not send session-accept",
                    OperationFailedException.REGISTRATION_REQUIRED, e1);
        }

        try {
            mediaHandler.start();
        } catch (UndeclaredThrowableException e) {
            Throwable exc = e.getUndeclaredThrowable();
            Timber.i(exc, "Failed to establish a connection");

            // send an error response
            String reasonText = "Error: " + exc.getMessage();
            Jingle errResp = JingleUtil.createSessionTerminate(
                    mConnection.getUser(),
                    sessionInitIQ.getInitiator(),
                    sessionInitIQ.getSid(),
                    Reason.GENERAL_ERROR, reasonText);

            setState(CallPeerState.FAILED, reasonText);
            try {
                mConnection.sendStanza(errResp);
            } catch (NotConnectedException | InterruptedException e1) {
                throw new OperationFailedException("Could not send session terminate after failing to start media handler",
                        OperationFailedException.REGISTRATION_REQUIRED, e);
            }
            return;
        }
        // tell everyone we are connected so that the audio notifications would stop
        setState(CallPeerState.CONNECTED);
    }

    /**
     * Returns the session ID of the Jingle session associated with this call.
     *
     * @return the session ID of the Jingle session associated with this call.
     */
    public String getSid()
    {
        // cmeng: (2016/09/14) if (sid == null) means some implementation problem => cause smack exception.
        return (sessionInitIQ != null) ? sessionInitIQ.getSid() : null;
    }

    /**
     * Returns the IQ ID of the Jingle session-initiate packet associated with this call.
     *
     * @return the IQ ID of the Jingle session-initiate packet associated with this call.
     */
    public Jingle getSessionIQ()
    {
        return sessionInitIQ;
    }

    /**
     * Ends the call with this <tt>CallPeer</tt>. Depending on the state of the peer the method
     * would send a CANCEL, BYE, or BUSY_HERE message and set the new state to DISCONNECTED.
     *
     * @param failed indicates if the hangup is following to a call failure or simply a disconnect
     * @param reasonText the text, if any, to be set on the <tt>JingleReason</tt> as the value of its
     * @param reasonOtherExtension the <tt>ExtensionElement</tt>, if any, to be set on the
     * <tt>JingleReason</tt> as the value of its <tt>otherExtension</tt> property
     */
    public void hangup(boolean failed, String reasonText, ExtensionElement reasonOtherExtension)
            throws NotConnectedException, InterruptedException
    {
        CallPeerState prevPeerState = getState();

        // do nothing if the call is already ended
        if (CallPeerState.DISCONNECTED.equals(prevPeerState)
                || CallPeerState.FAILED.equals(prevPeerState)) {
            Timber.d("Ignoring a request to hangup a call peer that is already DISCONNECTED");
            return;
        }

        // User hang up call, set reason code == NORMAL_CALL_CLEARING to stop missed call notification fired.
        setState(failed ? CallPeerState.FAILED : CallPeerState.DISCONNECTED, reasonText,
                CallPeerChangeEvent.NORMAL_CALL_CLEARING);

        Jingle responseIQ = null;
        if (prevPeerState.equals(CallPeerState.CONNECTED)
                || CallPeerState.isOnHold(prevPeerState)) {
            responseIQ = JingleUtil.createSessionTerminateSuccess(mPPS.getOurJID(), mPeerJid, getSid());
        }
        else if (CallPeerState.CONNECTING.equals(prevPeerState)
                || CallPeerState.CONNECTING_WITH_EARLY_MEDIA.equals(prevPeerState)
                || CallPeerState.ALERTING_REMOTE_SIDE.equals(prevPeerState)) {
            String jingleSID = getSid();

            if (jingleSID == null) {
                synchronized (sidSyncRoot) {
                    // we cancelled the call too early because the jingleSID is null (i.e. the
                    // session-initiate has not been created) and no need to send the session-terminate
                    cancelled = true;
                    return;
                }
            }
            responseIQ = JingleUtil.createSessionTerminateCancel(mPPS.getOurJID(), mPeerJid, getSid());
        }
        else if (prevPeerState.equals(CallPeerState.INCOMING_CALL)) {
            responseIQ = JingleUtil.createSessionTerminateBusy(mPPS.getOurJID(), mPeerJid, getSid());
        }
        else if (prevPeerState.equals(CallPeerState.BUSY)
                || prevPeerState.equals(CallPeerState.FAILED)) {
            // For FAILED and BUSY we only need to update CALL_STATUS as everything else has been done already.
        }
        else {
            Timber.i("Could not determine call peer state!");
        }

        if (responseIQ != null) {
            if (reasonOtherExtension != null) {
                JingleReason reason = responseIQ.getExtension(JingleReason.class);

                if (reason != null) {
                    reason.setOtherExtension(reasonOtherExtension);
                }
                else if (reasonOtherExtension instanceof JingleReason) {
                    responseIQ.setReason((JingleReason) reasonOtherExtension);
                }
            }
            mConnection.sendStanza(responseIQ);
        }
    }

    /**
     * Creates and sends a session-initiate {@link Jingle}.
     *
     * @param sessionInitiateExtensions a collection of additional and optional <tt>ExtensionElement</tt>s
     * to be added to the <tt>session-initiate</tt>;
     * {@link Jingle} which is to initiate the session with this <tt>CallPeerJabberImpl</tt>
     * @param sid The session-initiate sid, must be the same as in Jingle Message id if call is init from 'proceed'
     * @throws OperationFailedException exception
     */
    protected synchronized void initiateSession(Iterable<ExtensionElement> sessionInitiateExtensions, String sid)
            throws OperationFailedException
    {
        initiator = false;

        // Create the media description that we'd like to send to the other side.
        List<JingleContent> offer = getMediaHandler().createContentList();

        synchronized (sidSyncRoot) {
            sessionInitIQ = JingleUtil.createSessionInitiate(mPPS.getOurJID(), mPeerJid, sid, offer);

            if (cancelled) {
                // we cancelled the call too early so no need to send the session-initiate to peer
                getMediaHandler().getTransportManager().close();
                return;
            }
        }

        if (sessionInitiateExtensions != null) {
            for (ExtensionElement sessionInitiateExtension : sessionInitiateExtensions) {
                sessionInitIQ.addExtension(sessionInitiateExtension);
            }
        }

        try {
            mConnection.sendStanza(sessionInitIQ);
        } catch (NotConnectedException | InterruptedException e) {
            throw new OperationFailedException(aTalkApp.getResString(R.string.service_gui_CREATE_CALL_FAILED),
                    OperationFailedException.REGISTRATION_REQUIRED);
        }
    }

    /**
     * Notifies this instance that a specific <tt>ColibriConferenceIQ</tt> has been received.
     * This <tt>CallPeerJabberImpl</tt> uses the part of the information provided in the specified
     * <tt>conferenceIQ</tt> which concerns it only.
     *
     * @param conferenceIQ the <tt>ColibriConferenceIQ</tt> which has been received
     */
    void processColibriConferenceIQ(ColibriConferenceIQ conferenceIQ)
    {
        /*
         * CallPeerJabberImpl does not itself/directly know the specifics related to the channels
         * allocated on the Jitsi Videobridge server. The channels contain transport and
         * media-related information so forward the notification to CallPeerMediaHandlerJabberImpl.
         */
        getMediaHandler().processColibriConferenceIQ(conferenceIQ);
    }

    /**
     * Processes the content-accept {@link Jingle}.
     *
     * @param content The {@link Jingle} that contains content that remote peer has accepted
     */
    public void processContentAccept(Jingle content)
            throws NotConnectedException, InterruptedException
    {
        List<JingleContent> contents = content.getContents();
        CallPeerMediaHandlerJabberImpl mediaHandler = getMediaHandler();

        try {
            mediaHandler.getTransportManager().wrapupConnectivityEstablishment();
            mediaHandler.processSessionAcceptContent(contents);
            for (JingleContent c : contents)
                setSenders(getMediaType(c), c.getSenders());
        } catch (Exception e) {
            Timber.w(e, "Failed to process a content-accept");

            // Send an error response.
            String reason = "Error: " + e.getMessage();
            Jingle errResp = JingleUtil.createSessionTerminate(
                    mPPS.getOurJID(), mPeerJid, sessionInitIQ.getSid(),
                    Reason.INCOMPATIBLE_PARAMETERS, reason);

            setState(CallPeerState.FAILED, reason);
            mConnection.sendStanza(errResp);
            return;
        }
        mediaHandler.start();
    }

    /**
     * Processes the content-add {@link Jingle}.
     *
     * @param content The {@link Jingle} that contains content that remote peer wants to be added
     */
    public void processContentAdd(final Jingle content)
            throws NotConnectedException, InterruptedException
    {
        CallPeerMediaHandlerJabberImpl mediaHandler = getMediaHandler();
        List<JingleContent> contents = content.getContents();
        Iterable<JingleContent> answerContents;
        Jingle contentIQ;
        boolean noCands = false;
        MediaStream oldVideoStream = mediaHandler.getStream(MediaType.VIDEO);

        Timber.i("Looking for candidates in content-add.");
        try {
            if (!contentAddWithNoCands) {
                mediaHandler.processOffer(contents);

                // Jingle transport will not put candidate in session-initiate and content-add.
                for (JingleContent c : contents) {
                    if (JingleUtils.getFirstCandidate(c, 1) == null) {
                        contentAddWithNoCands = true;
                        noCands = true;
                    }
                }
            }
            // if no candidates are present, launch a new Thread which will process and wait for the
            // connectivity establishment (otherwise the existing thread will be blocked and thus
            // cannot receive transport-info with candidates
            if (noCands) {
                new Thread()
                {
                    @Override
                    public void run()
                    {
                        try {
                            synchronized (candSyncRoot) {
                                candSyncRoot.wait(1000);
                                contentAddWithNoCands = false;
                            }
                            processContentAdd(content);
                        } catch (InterruptedException | NotConnectedException ex) {
                            ex.printStackTrace();
                        }
                    }
                }.start();
                Timber.i("No candidates found in content-add, started new thread.");
                return;
            }

            mediaHandler.getTransportManager().wrapupConnectivityEstablishment();
            Timber.i("Wrapping up connectivity establishment");
            answerContents = mediaHandler.generateSessionAccept();
            contentIQ = null;
        } catch (Exception e) {
            Timber.w(e, "Exception occurred");
            answerContents = null;
            contentIQ = JingleUtil.createContentReject(mPPS.getOurJID(), mPeerJid, getSid(), answerContents);
        }

        if (contentIQ == null) {
            /* send content-accept */
            contentIQ = JingleUtil.createContentAccept(mPPS.getOurJID(), mPeerJid, getSid(), answerContents);
            for (JingleContent c : answerContents)
                setSenders(getMediaType(c), c.getSenders());
        }
        mConnection.sendStanza(contentIQ);
        mediaHandler.start();

        /*
         * If a remote peer turns her video on in a conference which is hosted by the local peer and
         * the local peer is not streaming her local video, re-invite the other remote peers to
         * enable RTP translation.
         */
        if (oldVideoStream == null) {
            MediaStream newVideoStream = mediaHandler.getStream(MediaType.VIDEO);

            if ((newVideoStream != null) && mediaHandler.isRTPTranslationEnabled(MediaType.VIDEO)) {
                try {
                    getCall().modifyVideoContent();
                } catch (OperationFailedException ofe) {
                    Timber.e(ofe, "Failed to enable RTP translation");
                }
            }
        }
    }

    /**
     * Processes the content-modify {@link Jingle}.
     *
     * @param content The {@link Jingle} that contains content that remote peer wants to be modified
     */
    public void processContentModify(Jingle content)
            throws NotConnectedException, InterruptedException
    {
        JingleContent ext = content.getContents().get(0);
        MediaType mediaType = getMediaType(ext);

        try {
            boolean modify = (ext.getFirstChildOfType(RtpDescriptionExtension.class) != null);
            getMediaHandler().reinitContent(ext.getName(), ext, modify);
            setSenders(mediaType, ext.getSenders());

            if (MediaType.VIDEO.equals(mediaType))
                getCall().modifyVideoContent();
        } catch (Exception e) {
            Timber.i(e, "Failed to process an incoming content-modify");

            // Send an error response.
            String reason = "Error: " + e.getMessage();
            Jingle errResp = JingleUtil.createSessionTerminate(
                    mPPS.getOurJID(), mPeerJid, sessionInitIQ.getSid(), Reason.INCOMPATIBLE_PARAMETERS, reason);

            setState(CallPeerState.FAILED, reason);
            mConnection.sendStanza(errResp);
        }
    }

    /**
     * Processes the content-reject {@link Jingle}.
     *
     * @param content The {@link Jingle}
     */
    public void processContentReject(Jingle content)
            throws NotConnectedException, InterruptedException
    {
        if (content.getContents().isEmpty()) {
            // send an error response;
            Jingle errResp = JingleUtil.createSessionTerminate(mConnection.getUser(),
                    sessionInitIQ.getInitiator(), sessionInitIQ.getSid(), Reason.INCOMPATIBLE_PARAMETERS,
                    "Error: content rejected");

            setState(CallPeerState.FAILED, "Error: content rejected");
            mConnection.sendStanza(errResp);
        }
    }

    /**
     * Processes the content-remove {@link Jingle}.
     *
     * @param content The {@link Jingle} that contains content that remote peer wants to be removed
     */
    public void processContentRemove(Jingle content)
    {
        List<JingleContent> contents = content.getContents();
        boolean videoContentRemoved = false;

        if (!contents.isEmpty()) {
            CallPeerMediaHandlerJabberImpl mediaHandler = getMediaHandler();

            for (JingleContent c : contents) {
                mediaHandler.removeContent(c.getName());

                MediaType mediaType = getMediaType(c);
                setSenders(mediaType, Senders.none);

                if (MediaType.VIDEO.equals(mediaType))
                    videoContentRemoved = true;
            }
            /*
             * TODO XEP-0166: Jingle says: If the content-remove results in zero content definitions
             * for the session, the entity that receives the content-remove SHOULD send a
             * session-terminate action to the other party (since a session with no content
             * definitions is void).
             */
        }

        if (videoContentRemoved) {
            // removing of the video content might affect the other sessions in the call
            try {
                getCall().modifyVideoContent();
            } catch (Exception e) {
                Timber.w("Failed to update Jingle sessions");
            }
        }
    }

    /**
     * Processes a session-accept {@link Jingle}.
     *
     * @param sessionInitIQ The session-accept {@link Jingle} to process.
     */
    public void processSessionAccept(Jingle sessionInitIQ)
            throws NotConnectedException, InterruptedException
    {
        if (sessionAcceptProcessed) {
            Timber.w("Ignore multiple session-accept received from: %s", this);
            return;
        }

        this.sessionInitIQ = sessionInitIQ;
        /*
         * Session-accept contentList request may contains both audio and video requests e.g.
         * <content creator='initiator' name='audio'>
         * <content creator='initiator' name='video' senders='both'>
         */
        List<JingleContent> contentList = sessionInitIQ.getContents();
        CallPeerMediaHandlerJabberImpl mediaHandler = getMediaHandler();

        try {
            TransportManagerJabberImpl transportManager = mediaHandler.getTransportManager();
            if (transportManager == null)
                throw new Exception("No available transport manager to process session-accept!");

            sessionAcceptProcessed = true;
            transportManager.wrapupConnectivityEstablishment();
            mediaHandler.processSessionAcceptContent(contentList);
            for (JingleContent c : contentList)
                setSenders(getMediaType(c), c.getSenders());
        } catch (Exception exc) {
            Timber.w(exc, "Failed to process a session-accept");

            // send an error response;
            Jingle errResp = JingleUtil.createSessionTerminate(
                    mConnection.getUser(),
                    sessionInitIQ.getInitiator(),
                    sessionInitIQ.getSid(),
                    Reason.INCOMPATIBLE_PARAMETERS,
                    exc.getClass().getName() + ": " + exc.getMessage());

            setState(CallPeerState.FAILED, "Error: " + exc.getMessage());
            mConnection.sendStanza(errResp);
            return;
        }

        // tell everyone we are connected so that the audio notifications would stop
        setState(CallPeerState.CONNECTED);
        mediaHandler.start();

        /*
         * If video was added to the call after we sent the session-initiate to this peer, it needs
         * to be added to this peer's session with a content-add.
         */
        sendModifyVideoContent();
    }

    /**
     * Handles the specified session <tt>info</tt> packet according to its content.
     *
     * @param info the {@link SessionInfoExtension} that we just received.
     */
    public void processSessionInfo(SessionInfoExtension info)
            throws NotConnectedException, InterruptedException
    {
        switch (info.getType()) {
            case ringing:
                setState(CallPeerState.ALERTING_REMOTE_SIDE);
                break;
            case hold:
                getMediaHandler().setRemotelyOnHold(true);
                reevalRemoteHoldStatus();
                break;
            case unhold:
            case active:
                getMediaHandler().setRemotelyOnHold(false);
                reevalRemoteHoldStatus();
                break;
            default:
                Timber.w("Received SessionInfoExtensionElement of unknown type");
        }
    }

    /**
     * Processes the session initiation {@link Jingle} that we were created with, passing its
     * content to the media handler and then sends either a "session-info/ringing" or a
     * "session-terminate" response.
     *
     * @param sessionInitIQ The {@link Jingle} that created the session that we are handling here.
     */
    protected synchronized void processSessionInitiate(Jingle sessionInitIQ)
            throws NotConnectedException, InterruptedException
    {
        // Do initiate the session.
        this.sessionInitIQ = sessionInitIQ;
        this.initiator = true;

        // This is the SDP offer that came from the initial session-initiate.
        // Contrary to SIP, we are guaranteed to have content because
        // XEP-0166 says: "A session consists of at least one content type at a time."
        List<JingleContent> offer = sessionInitIQ.getContents();
        try {
            getMediaHandler().processOffer(offer);

            CoinExtension coin = null;
            for (ExtensionElement ext : sessionInitIQ.getExtensions()) {
                if (ext.getElementName().equals(CoinExtension.ELEMENT)) {
                    coin = (CoinExtension) ext;
                    break;
                }
            }

            /* Does the call peer acts as a conference focus ? */
            if (coin != null) {
                setConferenceFocus(Boolean.parseBoolean((String) coin.getAttribute("isfocus")));
            }
        } catch (Exception ex) {
            Timber.w(ex, "Failed to process an incoming session initiate");

            // send an error response;
            String reasonText = "Error: " + ex.getMessage();
            Jingle errResp = JingleUtil.createSessionTerminate(
                    mConnection.getUser(),
                    sessionInitIQ.getInitiator(),
                    sessionInitIQ.getSid(),
                    Reason.INCOMPATIBLE_PARAMETERS, reasonText);

            setState(CallPeerState.FAILED, reasonText);
            mConnection.sendStanza(errResp);
            return;
        }

        // If we do not get the info about the remote peer yet. Get it right now.
        if (this.getDiscoveryInfo() == null) {
            Jid calleeURI = sessionInitIQ.getFrom();
            retrieveDiscoveryInfo(calleeURI);
        }

        // send a ringing response; cmeng??? what about auto-answer
        Timber.log(TimberLog.FINER, "Will send ringing response.");
        mConnection.sendStanza(JingleUtil.createRinging(sessionInitIQ));

        // set flag to indicate that session-initiate process has completed.
        synchronized (sessionInitiateSyncRoot) {
            sessionInitiateProcessed = true;
            // cmeng - Importance: must notifyAll as there are multiple transport-info's on waiting
            sessionInitiateSyncRoot.notifyAll();
        }

        // if this is a 3264 initiator, let's give them an early peek at our answer so that they could
        // start ICE (SIP-2-Jingle gateways won't be able to send their candidates unless they have this)
        DiscoverInfo discoverInfo = getDiscoveryInfo();
        if ((discoverInfo != null)
                && discoverInfo.containsFeature(ProtocolProviderServiceJabberImpl.URN_IETF_RFC_3264)) {
            mConnection.sendStanza(JingleUtil.createDescriptionInfo(
                    mConnection.getUser(), sessionInitIQ, getMediaHandler().getLocalContentList()));
        }

        // process members if any
        processSourceAdd(sessionInitIQ);
    }

    /**
     * Puts this peer into a {@link CallPeerState#DISCONNECTED}, indicating a reason to the user, if there is one.
     *
     * @param jingle the {@link Jingle} that's terminating our session.
     */
    public void processSessionTerminate(Jingle jingle)
    {
        String reasonStr = "Call ended by remote side.";
        JingleReason reasonExt = jingle.getReason();

        if (reasonExt != null) {
            Reason reason = reasonExt.getReason();
            if (reason != null)
                reasonStr += "\nReason: " + reason.toString() + ".";

            String text = reasonExt.getText();
            if (text != null)
                reasonStr += "\n" + text;
        }
        setState(CallPeerState.DISCONNECTED, reasonStr);
    }

    /**
     * Processes a specific "XEP-0251: Jingle Session Transfer" <tt>transfer</tt> packet (extension).
     *
     * @param transfer the "XEP-0251: Jingle Session Transfer" transfer packet (extension) to process
     * @throws OperationFailedException if anything goes wrong while processing the specified <tt>transfer</tt>
     * packet (extension)
     */
    public void processTransfer(TransferExtension transfer)
            throws OperationFailedException
    {
        Jid attendantAddress = transfer.getFrom();
        if (attendantAddress == null) {
            throw new OperationFailedException("Session transfer must contain a 'from' attribute value.",
                    OperationFailedException.ILLEGAL_ARGUMENT);
        }

        Jid calleeAddress = transfer.getTo();
        if (calleeAddress == null) {
            throw new OperationFailedException("Session transfer must contain a 'to' attribute value.",
                    OperationFailedException.ILLEGAL_ARGUMENT);
        }

        // Checks if the transfer remote peer is contained by the roster of this account.
        Roster roster = Roster.getInstanceFor(mConnection);
        if (!roster.contains(calleeAddress.asBareJid())) {
            String failedMessage = "Transfer impossible:\nAccount roster does not contain transfer peer: "
                    + calleeAddress.asBareJid();
            setState(CallPeerState.FAILED, failedMessage);
            Timber.i(failedMessage);
        }

        OperationSetBasicTelephonyJabberImpl basicTelephony = (OperationSetBasicTelephonyJabberImpl)
                mPPS.getOperationSet(OperationSetBasicTelephony.class);
        CallJabberImpl calleeCall = new CallJabberImpl(basicTelephony, transfer.getSid());
        TransferExtension calleeTransfer = new TransferExtension();
        String sid = transfer.getSid();

        calleeTransfer.setFrom(attendantAddress);
        if (sid != null) {
            calleeTransfer.setSID(sid);
            calleeTransfer.setTo(calleeAddress);
        }
        basicTelephony.createOutgoingCall(calleeCall, calleeAddress.toString(),
                Arrays.asList(new ExtensionElement[]{calleeTransfer}));
    }

    /**
     * Processes the offered remote <tt>transport-info</tt> {@link Jingle}.
     *
     * @param jingle containing the <tt>transport-info</tt> {@link Jingle} to be processed.
     */
    public void processOfferTransportInfo(Jingle jingle)
            throws NotConnectedException, InterruptedException
    {
        // The transport-info is used to exchange transport candidates for mediaHandler.
        try {
            if (isInitiator()) {
                synchronized (sessionInitiateSyncRoot) {
                    if (!sessionInitiateProcessed) {
                        try {
                            // wait for session-initiate (1000ms max) to complete before start transport-info handling
                            sessionInitiateSyncRoot.wait(1000);
                        } catch (InterruptedException ex) {
                            ex.printStackTrace();
                        }
                    }
                }
            }

            List<JingleContent> jingleContents = jingle.getContents();
            List<String> media = new ArrayList<>();
            for (JingleContent jingleContent : jingleContents) {
                media.add(jingleContent.getName());
            }
            Timber.d("### Processing Jingle IQ (transport-info) for media: %s", media);
            getMediaHandler().processTransportInfo(jingleContents);
        } catch (OperationFailedException ofe) {
            Timber.w(ofe, "Failed to process an incoming transport-info");

            // send an error response
            String reasonText = "Error: " + ofe.getMessage();
            Jingle errResp = JingleUtil.createSessionTerminate(
                    mPPS.getOurJID(),
                    mPeerJid,
                    sessionInitIQ.getSid(),
                    Reason.GENERAL_ERROR, reasonText);

            setState(CallPeerState.FAILED, reasonText);
            mConnection.sendStanza(errResp);
            return;
        }
        synchronized (candSyncRoot) {
            candSyncRoot.notifyAll();
        }
    }

    /**
     * Puts the <tt>CallPeer</tt> represented by this instance on or off hold.
     *
     * @param onHold <tt>true</tt> to have the <tt>CallPeer</tt> put on hold; <tt>false</tt>, otherwise
     * @throws OperationFailedException if we fail to construct or send the INVITE request putting the
     * remote side on/off hold.
     */
    public void putOnHold(boolean onHold)
            throws OperationFailedException
    {
        CallPeerMediaHandlerJabberImpl mediaHandler = getMediaHandler();
        mediaHandler.setLocallyOnHold(onHold);
        SessionInfoType type;
        if (onHold)
            type = SessionInfoType.hold;
        else {
            type = SessionInfoType.unhold;
            getMediaHandler().reinitAllContents();
        }

        // we are now on hold and need to realize this before potentially
        // spoiling it all with an exception while sending the packet :).
        reevalLocalHoldStatus();
        Jingle onHoldIQ = JingleUtil.createSessionInfo(
                mPPS.getOurJID(),
                mPeerJid,
                getSid(),
                type);
        try {
            mConnection.sendStanza(onHoldIQ);
        } catch (NotConnectedException | InterruptedException e) {
            throw new OperationFailedException("Could not send session info",
                    OperationFailedException.REGISTRATION_REQUIRED, e);
        }
    }

    /**
     * Send a <tt>content-add</tt> to add video setup.
     */
    private void sendAddVideoContent()
            throws NotConnectedException, InterruptedException
    {
        List<JingleContent> contents;
        try {
            contents = getMediaHandler().createContentList(MediaType.VIDEO);
        } catch (Exception exc) {
            Timber.w(exc, "Failed to gather content for video type");
            return;
        }

        Jingle contentIQ = JingleUtil.createContentAdd(mPPS.getOurJID(), mPeerJid, getSid(), contents);
        mConnection.sendStanza(contentIQ);
    }

    /**
     * Sends a <tt>content</tt> message to reflect changes in the setup such as the local peer/user
     * becoming a conference focus.
     */
    public void sendCoinSessionInfo()
            throws NotConnectedException, InterruptedException
    {
        Jingle sessionInfoIQ = JingleUtil.createSessionInfo(mPPS.getOurJID(), mPeerJid, getSid());
        CoinExtension coinExt = new CoinExtension(getCall().isConferenceFocus());

        sessionInfoIQ.addExtension(coinExt);
        mConnection.sendStanza(sessionInfoIQ);
    }

    /**
     * Returns the <tt>MediaDirection</tt> that should be set for the content of type <tt>mediaType</tt>
     * in the Jingle session for this <tt>CallPeer</tt>. If we are the focus of a conference and are doing
     * RTP translation, takes into account the other <tt>CallPeer</tt>s in the <tt>Call</tt>.
     *
     * @param mediaType the <tt>MediaType</tt> for which to return the <tt>MediaDirection</tt>
     * @return the <tt>MediaDirection</tt> that should be used for the content of type
     * <tt>mediaType</tt> in the Jingle session for this <tt>CallPeer</tt>.
     */
    private MediaDirection getDirectionForJingle(MediaType mediaType)
    {
        MediaDirection direction = MediaDirection.INACTIVE;
        CallPeerMediaHandlerJabberImpl mediaHandler = getMediaHandler();

        // If we are streaming media, the direction should allow sending
        if ((MediaType.AUDIO == mediaType && mediaHandler.isLocalAudioTransmissionEnabled())
                || ((MediaType.VIDEO == mediaType) && isLocalVideoStreaming()))
            direction = direction.or(MediaDirection.SENDONLY);

        // If we are receiving media from this CallPeer, the direction should allow receiving
        Senders senders = getSenders(mediaType);
        if (senders == null || senders == Senders.both
                || (isInitiator() && senders == Senders.initiator)
                || (!isInitiator() && senders == Senders.responder))
            direction = direction.or(MediaDirection.RECVONLY);

        // If we are the focus of a conference and we are receiving media from
        // another CallPeer in the same Call, the direction should allow sending
        CallJabberImpl call = getCall();
        if (call != null && call.isConferenceFocus()) {
            for (CallPeerJabberImpl peer : call.getCallPeerList()) {
                if (peer != this) {
                    senders = peer.getSenders(mediaType);
                    if (senders == null || senders == Senders.both
                            || (peer.isInitiator() && senders == Senders.initiator)
                            || (!peer.isInitiator() && senders == Senders.responder)) {
                        direction = direction.or(MediaDirection.SENDONLY);
                        break;
                    }
                }
            }
        }
        return direction;
    }

    /**
     * Send, if necessary, a jingle <tt>content</tt> message to reflect change in video setup.
     * Whether the jingle session should have a video content, and if so, the value of the
     * <tt>senders</tt> field is determined based on whether we are streaming local video and, if we
     * are the focus of a conference, on the other peers in the conference. The message can be
     * content-modify if video content exists (and the <tt>senders</tt> field changes), content-add
     * or content-remove.
     *
     * @return <tt>true</tt> if a jingle <tt>content</tt> message was sent.
     */
    public boolean sendModifyVideoContent()
            throws NotConnectedException, InterruptedException
    {
        CallPeerMediaHandlerJabberImpl mediaHandler = getMediaHandler();
        MediaDirection direction = getDirectionForJingle(MediaType.VIDEO);

        JingleContent remoteContent = mediaHandler.getLocalContent(MediaType.VIDEO.toString());
        if (remoteContent == null) {
            if (direction == MediaDirection.INACTIVE) {
                // no video content, none needed
                return false;
            }
            else { // no remote video and local video added
                if (getState() == CallPeerState.CONNECTED) {
                    Timber.i("Adding video content for %s", this);
                    sendAddVideoContent();
                    return true;
                }
                return false;
            }
        }
        else {
            if (direction == MediaDirection.INACTIVE) {
                sendRemoveVideoContent();
                return true;
            }
        }

        Senders senders = getSenders(MediaType.VIDEO);
        if (senders == null)
            senders = JingleContent.Senders.both;

        Senders newSenders = Senders.none;
        if (MediaDirection.SENDRECV == direction)
            newSenders = Senders.both;
        else if (MediaDirection.RECVONLY == direction)
            newSenders = isInitiator() ? Senders.initiator : Senders.responder;
        else if (MediaDirection.SENDONLY == direction)
            newSenders = isInitiator() ? Senders.responder : Senders.initiator;

        /*
         * Send Content-Modify
         */
        JingleContent ext = new JingleContent();
        String remoteContentName = remoteContent.getName();

        ext.setSenders(newSenders);
        ext.setCreator(remoteContent.getCreator());
        ext.setName(remoteContentName);

        // cmeng (2016/9/14) only send content-modify if there is a change in own video streaming state
        if (newSenders != senders) {
            Timber.i("Sending content modify, senders: %s -> %s", senders, newSenders);

            // cmeng: must update local videoSenders for content-modify
            setSenders(MediaType.VIDEO, newSenders);

            Jingle contentIQ = JingleUtil.createContentModify(mPPS.getOurJID(), mPeerJid, getSid(), ext);
            mConnection.sendStanza(contentIQ);
        }

        try {
            mediaHandler.reinitContent(remoteContentName, ext, false);
            mediaHandler.start();
        } catch (Exception e) {
            Timber.w(e, "Exception occurred during media reinitialization");
        }
        return (newSenders != senders);
    }

    /**
     * Send a <tt>content</tt> message to reflect change in video setup (start or stop).
     */
    public void sendModifyVideoResolutionContent()
            throws NotConnectedException, InterruptedException
    {
        CallPeerMediaHandlerJabberImpl mediaHandler = getMediaHandler();
        JingleContent remoteContent = mediaHandler.getRemoteContent(MediaType.VIDEO.toString());
        JingleContent content;
        Timber.i("send modify-content to change resolution");

        // send content-modify with RTP description
        // create content list with resolution
        try {
            content = mediaHandler.createContentForMedia(MediaType.VIDEO);
        } catch (Exception e) {
            Timber.w(e, "Failed to gather content for video type");
            return;
        }

        // if we are only receiving video senders is null
        Senders senders = remoteContent.getSenders();
        if (senders != null)
            content.setSenders(senders);

        Jingle contentIQ = JingleUtil.createContentModify(mPPS.getOurJID(), mPeerJid, getSid(), content);
        mConnection.sendStanza(contentIQ);

        try {
            mediaHandler.reinitContent(remoteContent.getName(), content, false);
            mediaHandler.start();
        } catch (Exception e) {
            Timber.w(e, "Exception occurred when media reinitialization");
        }
    }

    /**
     * Send a <tt>content-remove</tt> to remove video setup.
     */
    private void sendRemoveVideoContent()
            throws NotConnectedException, InterruptedException
    {
        CallPeerMediaHandlerJabberImpl mediaHandler = getMediaHandler();

        JingleContent content = new JingleContent();
        JingleContent remoteContent = mediaHandler.getRemoteContent(MediaType.VIDEO.toString());
        if (remoteContent == null)
            return;
        String remoteContentName = remoteContent.getName();

        content.setName(remoteContentName);
        content.setCreator(remoteContent.getCreator());
        content.setSenders(remoteContent.getSenders());

        Jingle contentIQ = JingleUtil.createContentRemove(mPPS.getOurJID(), mPeerJid, getSid(),
                Collections.singletonList(content));
        mConnection.sendStanza(contentIQ);
        mediaHandler.removeContent(remoteContentName);
        setSenders(MediaType.VIDEO, Senders.none);
    }

    /**
     * Sends local candidate addresses from the local peer to the remote peer using the
     * <tt>transport-info</tt> {@link Jingle}.
     *
     * @param contents the local candidate addresses to be sent from the local peer to the remote peer using
     * the <tt>transport-info</tt> {@link Jingle}
     */
    protected void sendTransportInfo(Iterable<JingleContent> contents)
            throws NotConnectedException, InterruptedException
    {
        // if the call is canceled, do not start sending candidates in transport-info.
        if (cancelled)
            return;

        Jingle transportInfo = new Jingle(JingleAction.TRANSPORT_INFO, getSid());

        for (JingleContent content : contents)
            transportInfo.addContent(content);

        transportInfo.setFrom(mPPS.getOurJID());
        transportInfo.setTo(mPeerJid);
        transportInfo.setType(IQ.Type.set);

        StanzaCollector collector = mConnection.createStanzaCollectorAndSend(transportInfo);
        try {
            collector.nextResult();
        } finally {
            collector.cancel();
        }
    }

    @Override
    public void setState(CallPeerState newState, String reason, int reasonCode)
    {
        CallPeerState oldState = getState();
        try {
            /*
             * We need to dispose of the transport manager before the 'call' field is set to null,
             * because if Jitsi Videobridge is in use, it (the call) is needed in order to expire
             * the Videobridge channels.
             */
            if (CallPeerState.DISCONNECTED.equals(newState) || CallPeerState.FAILED.equals(newState)) {
                CallPeerMediaHandlerJabberImpl mediaHandler = getMediaHandler();
                if (mediaHandler != null) {
                    TransportManagerJabberImpl transportManager = mediaHandler.getTransportManager();
                    if (transportManager != null) {
                        transportManager.close();
                    }
                }
            }
        } finally {
            super.setState(newState, reason, reasonCode);
        }

        if (CallPeerState.isOnHold(oldState) && CallPeerState.CONNECTED.equals(newState)) {
            try {
                getCall().modifyVideoContent();
            } catch (OperationFailedException ofe) {
                Timber.e("Failed to update call video state after 'hold' status removed for %s", this);
            }
        }
    }

    /**
     * Transfer (in the sense of call transfer) this <tt>CallPeer</tt> to a specific callee address
     * which may optionally be participating in an active <tt>Call</tt>.
     *
     * @param to the address of the callee to transfer this <tt>CallPeer</tt> to
     * @param sid the Jingle session ID of the active <tt>Call</tt> between the local peer and the
     * callee in the case of attended transfer; <tt>null</tt> in the case of unattended transfer
     * @throws OperationFailedException if something goes wrong
     */
    protected void transfer(Jid to, String sid)
            throws OperationFailedException
    {
        Jingle transferSessionInfo = new Jingle(JingleAction.SESSION_INFO, getSid());
        transferSessionInfo.setFrom(mPPS.getOurJID());
        transferSessionInfo.setTo(mPeerJid);
        transferSessionInfo.setType(IQ.Type.set);

        TransferExtension transfer = new TransferExtension();
        // Attended transfer.
        if (sid != null) {
            /*
             * Not really sure what the value of the "from" attribute of the "transfer" element
             * should be but the examples in "XEP-0251: Jingle Session Transfer" has it in the case
             * of attended transfer.
             */
            transfer.setFrom(mPPS.getOurJID());
            transfer.setSID(sid);

            // Puts on hold the 2 calls before making the attended transfer.
            OperationSetBasicTelephonyJabberImpl basicTelephony
                    = (OperationSetBasicTelephonyJabberImpl) mPPS.getOperationSet(OperationSetBasicTelephony.class);
            CallPeerJabberImpl callPeer = basicTelephony.getActiveCallPeer(sid);
            if (callPeer != null) {
                if (!CallPeerState.isOnHold(callPeer.getState())) {
                    callPeer.putOnHold(true);
                }
            }

            if (!CallPeerState.isOnHold(this.getState())) {
                this.putOnHold(true);
            }
        }
        transfer.setTo(to);
        transferSessionInfo.addExtension(transfer);

        try {
            StanzaCollector collector = mConnection.createStanzaCollectorAndSend(transferSessionInfo);
            try {
                collector.nextResultOrThrow();
            } finally {
                collector.cancel();
            }
        } catch (NotConnectedException | InterruptedException e) {
            throw new OperationFailedException("Could not send transfer session info",
                    OperationFailedException.REGISTRATION_REQUIRED, e);
        } catch (XMPPException.XMPPErrorException e1) {
            // Log the failed transfer call and notify the user.
            throw new OperationFailedException("Remote peer does not manage call 'transfer'. "
                    + e1.getStanzaError(), OperationFailedException.ILLEGAL_ARGUMENT);
        } catch (SmackException.NoResponseException e1) {
            // Log the failed transfer call and notify the user.
            throw new OperationFailedException("No response to the 'transfer' request.",
                    OperationFailedException.ILLEGAL_ARGUMENT);
        }

        String message = ((sid == null) ? "Unattended" : "Attended") + " transfer to: " + to;
        // Implements the SIP behavior: once the transfer is accepted, the current call is closed.
        try {
            hangup(false, message, new JingleReason(Reason.SUCCESS, message,
                    new TransferredExtension()));
        } catch (NotConnectedException | InterruptedException e) {
            throw new OperationFailedException("Could not send transfer", 0, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    public String getEntity()
    {
        return getAddress();
    }

    /**
     * {@inheritDoc}
     *
     * In Jingle there isn't an actual "direction" parameter. We use the <tt>senders</tt> field to
     * calculate the direction.
     */
    @Override
    public MediaDirection getDirection(MediaType mediaType)
    {
        Senders senders = getSenders(mediaType);

        if (senders == Senders.none) {
            return MediaDirection.INACTIVE;
        }
        else if (senders == null || senders == Senders.both) {
            return MediaDirection.SENDRECV;
        }
        else if (senders == Senders.initiator) {
            return isInitiator() ? MediaDirection.RECVONLY : MediaDirection.SENDONLY;
        }
        else { // senders == Senders.responder
            return isInitiator() ? MediaDirection.SENDONLY : MediaDirection.RECVONLY;
        }
    }

    /**
     * Gets the current value of the <tt>senders</tt> field of the content with name
     * <tt>mediaType</tt> in the Jingle session with this <tt>CallPeer</tt>.
     *
     * @param mediaType the <tt>MediaType</tt> for which to get the current value of the <tt>senders</tt> field.
     * @return the current value of the <tt>senders</tt> field of the content with name
     * <tt>mediaType</tt> in the Jingle session with this <tt>CallPeer</tt>.
     */
    public JingleContent.Senders getSenders(MediaType mediaType)
    {
        switch (mediaType) {
            case AUDIO:
                return audioSenders;
            case VIDEO:
                return videoSenders;
            default:
                return Senders.none;
        }
    }

    /**
     * Set the current value of the <tt>senders</tt> field of the content with name
     * <tt>mediaType</tt> in the Jingle session with this <tt>CallPeer</tt>
     *
     * @param mediaType the <tt>MediaType</tt> for which to get the current value of the <tt>senders</tt> field.
     * @param senders the value to set.
     */
    private void setSenders(MediaType mediaType, Senders senders)
    {
        switch (mediaType) {
            case AUDIO:
                this.audioSenders = senders;
                break;
            case VIDEO:
                this.videoSenders = senders;
                break;
            default:
                throw new IllegalArgumentException("mediaType");
        }
    }

    /**
     * Gets the <tt>MediaType</tt> of <tt>content</tt>. If <tt>content</tt> does not have a
     * <tt>description</tt> child and therefore not <tt>MediaType</tt> can be associated with it,
     * tries to take the <tt>MediaType</tt> from the session's already established contents with the
     * same name as <tt>content</tt>
     *
     * @param content the <tt>JingleContent</tt> for which to get the <tt>MediaType</tt>
     * @return the <tt>MediaType</tt> of <tt>content</tt>.
     */
    public MediaType getMediaType(JingleContent content)
    {
        String contentName = content.getName();
        if (contentName == null)
            return null;

        MediaType mediaType = JingleUtils.getMediaType(content);
        if (mediaType == null) {
            CallPeerMediaHandlerJabberImpl mediaHandler = getMediaHandler();
            for (MediaType m : MediaType.values()) {
                JingleContent sessionContent = mediaHandler.getRemoteContent(m.toString());
                if (sessionContent == null)
                    sessionContent = mediaHandler.getLocalContent(m.toString());

                if (sessionContent != null && contentName.equals(sessionContent.getName())) {
                    mediaType = m;
                    break;
                }
            }
        }
        return mediaType;
    }

    /**
     * Processes the source-add {@link Jingle} action used in Jitsi-Meet.
     * For now processing only audio, as we use single ssrc for audio and
     * using multiple ssrcs for video. ConferenceMember currently support single
     * ssrc for audio and video and adding multiple ssrcs will need a large refactor.
     *
     * @param content The {@link Jingle} that contains content that remote
     * peer wants to be added
     */
    public void processSourceAdd(final Jingle content)
    {
        for (JingleContent c : content.getContents()) {
            // we are parsing only audio
            if (!MediaType.AUDIO.equals(JingleUtils.getMediaType(c))) {
                continue;
            }

            RtpDescriptionExtension rtpDesc = JingleUtils.getRtpDescription(c);

            // for (MediaPresenceExtension.Source src : rtpDesc.getChildExtensionsOfType(MediaPresenceExtension.Source.class)) {
            for (SourceExtension src : rtpDesc.getChildExtensionsOfType(SourceExtension.class)) {
                SSRCInfoExtension ssrcInfo = src.getFirstChildOfType(SSRCInfoExtension.class);
                if (ssrcInfo == null)
                    continue;

                Jid owner = ssrcInfo.getOwner();
                if (owner == null)
                    continue;

                AbstractConferenceMember member = findConferenceMemberByAddress(owner);
                if (member == null) {
                    member = new AbstractConferenceMember(this, owner.toString());
                    this.addConferenceMember(member);
                }
                member.setAudioSsrc(src.getSSRC());
            }
        }
    }

    /**
     * Processes the source-remove {@link Jingle} action used in Jitsi-Meet.
     * For now processing only audio, as we use single ssrc for audio and
     * using multiple ssrcs for video. ConferenceMember currently support single
     * ssrc for audio and video and adding multiple ssrcs will need a large refactor.
     *
     * @param content The {@link Jingle} that contains content that remote peer wants to be removed
     */
    public void processSourceRemove(final Jingle content)
    {
        for (JingleContent c : content.getContents()) {
            // we are parsing only audio
            if (!MediaType.AUDIO.equals(JingleUtils.getMediaType(c))) {
                continue;
            }

            RtpDescriptionExtension rtpDesc = JingleUtils.getRtpDescription(c);
            for (SourceExtension src : rtpDesc.getChildExtensionsOfType(SourceExtension.class)) {
                SSRCInfoExtension ssrcInfo = src.getFirstChildOfType(SSRCInfoExtension.class);

                if (ssrcInfo == null)
                    continue;

                Jid owner = ssrcInfo.getOwner();
                if (owner == null)
                    continue;

                ConferenceMember member = findConferenceMemberByAddress(owner);
                if (member != null)
                    this.removeConferenceMember(member);
            }
        }
    }

    /**
     * Finds <tt>ConferenceMember</tt> by its address.
     *
     * @param address the address to look for
     * @return <tt>ConferenceMember</tt> with <tt>address</tt> or null if not found.
     */
    private AbstractConferenceMember findConferenceMemberByAddress(Jid address)
    {
        for (ConferenceMember member : getConferenceMembers()) {
            if (member.getAddress().equals(address.toString())) {
                return (AbstractConferenceMember) member;
            }
        }
        return null;
    }

    /**
     * Returns a String locator for that peer.
     *
     * @return the peer's address or phone number.
     */
    public String getAddress()
    {
        return mPeerJid.toString();
    }

    public Jid getPeerJid()
    {
        return mPeerJid;
    }

    /**
     * Returns the contact corresponding to this peer or null if no
     * particular contact has been associated.
     *
     * @return the <tt>Contact</tt> corresponding to this peer or null if no particular contact has been associated.
     */
    public Contact getContact()
    {
        OperationSetPresence presence = getProtocolProvider().getOperationSet(OperationSetPresence.class);
        return (presence == null) ? null : presence.findContactByJid(mPeerJid);
    }

    /**
     * Returns the service discovery information that we have for this peer.
     *
     * @return the service discovery information that we have for this peer.
     */
    public DiscoverInfo getDiscoveryInfo()
    {
        return discoverInfo;
    }

    /**
     * Returns a human readable name representing this peer.
     *
     * @return a String containing a name for that peer.
     */
    public String getDisplayName()
    {
        if (getCall() != null) {
            Contact contact = getContact();
            if (contact != null)
                return contact.getDisplayName();
        }
        return mPeerJid.toString();
    }

    /**
     * Returns full URI of the address.
     *
     * @return full URI of the address
     */
    public String getURI()
    {
        return "xmpp:" + mPeerJid;
    }

    /**
     * Determines whether this peer initiated the session. Note that if this
     * peer is the initiator of the session, then we are the responder!
     *
     * @return <tt>true</tt> if this peer initiated the session; <tt>false</tt>, otherwise
     * (i.e. if _we_ initiated the session).
     */
    public boolean isInitiator()
    {
        return initiator;
    }

    /**
     * Retrieves the DiscoverInfo for a given peer identified by its URI.
     *
     * @param calleeURI The URI of the call peer.
     */
    private void retrieveDiscoveryInfo(Jid calleeURI)
    {
        try {
            DiscoverInfo discoveryInfo = mPPS.getDiscoveryManager().discoverInfo(calleeURI);
            if (discoveryInfo != null)
                setDiscoveryInfo(discoveryInfo);
        } catch (XMPPException
                | InterruptedException
                | SmackException.NoResponseException
                | NotConnectedException xmppex) {
            Timber.w(xmppex, "Could not retrieve info for %s", calleeURI);
        }
    }

    /**
     * Specifies the address, phone number, or other protocol specific
     * identifier that represents this call peer. This method is to be
     * used by service users and MUST NOT be called by the implementation.
     *
     * @param address The address of this call peer.
     */
    public void setAddress(FullJid address)
    {
        if (!mPeerJid.equals(address)) {
            String oldAddress = getAddress();
            mPeerJid = address;
            fireCallPeerChangeEvent(CallPeerChangeEvent.CALL_PEER_ADDRESS_CHANGE, oldAddress, address);
        }
    }

    /**
     * Sets the service discovery information that we have for this peer.
     *
     * @param discoverInfo the discovery information that we have obtained for this peer.
     */
    public void setDiscoveryInfo(DiscoverInfo discoverInfo)
    {
        this.discoverInfo = discoverInfo;
    }

    /**
     * Returns the IQ ID of the Jingle session-initiate packet associated with this call.
     *
     * @return the IQ ID of the Jingle session-initiate packet associated with this call.
     */
    public String getSessInitID()
    {
        return sessionInitIQ != null ? sessionInitIQ.getStanzaId() : null;
    }
}
