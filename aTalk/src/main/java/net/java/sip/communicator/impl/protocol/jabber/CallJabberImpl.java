/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.call.JingleMessageSessionImpl;
import org.atalk.impl.neomedia.transform.dtls.DtlsControlImpl;
import org.atalk.service.neomedia.DtlsControl;
import org.atalk.service.neomedia.MediaDirection;
import org.atalk.util.MediaType;

import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.packet.XmlElement;

import org.jivesoftware.smackx.disco.packet.DiscoverInfo;
import org.jivesoftware.smackx.jingle.element.Jingle;
import org.jivesoftware.smackx.jingle.element.JingleContent;
import org.jivesoftware.smackx.jingle.element.JingleReason;
import org.jivesoftware.smackx.jingle_rtp.JingleCallSessionImpl;
import org.jivesoftware.smackx.jingle_rtp.JingleUtils;
import org.jivesoftware.smackx.jingle_rtp.element.IceUdpTransport;
import org.jivesoftware.smackx.jingle_rtp.element.RtpDescription;
import org.jivesoftware.smackx.jingle_rtp.element.SdpTransfer;
import org.jivesoftware.smackx.jingle_rtp.element.SrtpFingerprint;

import org.jxmpp.jid.FullJid;

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
        OperationSetBasicTelephonyJabberImpl, ProtocolProviderServiceJabberImpl> {
    /**
     * Indicates if the <code>CallPeer</code> will support <code>inputevt</code>
     * extension (i.e. will be able to be remote-controlled).
     */
    private boolean localInputEvtAware = false;

    /**
     * Initializes a new <code>CallJabberImpl</code> instance.
     *
     * @param parentOpSet the {@link OperationSetBasicTelephonyJabberImpl} instance in the context
     * of which this call has been created.
     * @param sid the Jingle session-initiate id provided.
     */
    public CallJabberImpl(OperationSetBasicTelephonyJabberImpl parentOpSet, String sid) {
        super(parentOpSet, sid);
        // let's add ourselves to the calls repo. we are doing it ourselves
        // just to make sure that no one ever forgets.
        parentOpSet.getActiveCallsRepository().addCall(this);
    }

    /**
     * Creates a <code>CallPeerJabberImpl</code> from <code>calleeJID</code> and sends them <code>session-initiate</code> IQ request.
     *
     * @param calleeJid the party that we would like to invite to this call.
     * @param discoverInfo any discovery information that we have for the jid we are trying to reach and
     * that we are passing in order to avoid having to ask for it again.
     * @param sessionInitiateExtensions a collection of additional and optional <code>XmlElement</code>s to be
     * added to the <code>session-initiate</code> {@link Jingle} which is to init this <code>CallJabberImpl</code>
     * @param supportedTransports the XML namespaces of the jingle transports to use.
     *
     * @return the newly created <code>CallPeerJabberImpl</code> corresponding to <code>calleeJID</code>.
     * All following state change events will be delivered through this call peer.
     *
     * @throws OperationFailedException with the corresponding code if we fail to create the call.
     */
    public CallPeerJabberImpl initiateSession(FullJid calleeJid, DiscoverInfo discoverInfo,
            Iterable<XmlElement> sessionInitiateExtensions, Collection<String> supportedTransports)
            throws OperationFailedException {
        // create the session-initiate IQ
        CallPeerJabberImpl callPeer = new CallPeerJabberImpl(calleeJid, this);
        callPeer.setDiscoveryInfo(discoverInfo);
        addCallPeer(callPeer);

        callPeer.setState(CallPeerState.INITIATING_CALL);

        // If this is the first peer we added in this call, then the call is new;
        // then we need to notify everyone of its creation.
        if (getCallPeerCount() == 1)
            parentOpSet.fireCallEvent(CallEvent.CALL_INITIATED, this);

        // set the supported transports before the transport manager is being created
        CallPeerMediaHandlerJabberImpl mediaHandler = callPeer.getMediaHandler();
        mediaHandler.setSupportedTransports(supportedTransports);

        /* enable video if it is a video call */
        mediaHandler.setLocalVideoTransmissionEnabled(localVideoAllowed);

        /* enable remote-control if it is a desktop sharing session - cmeng: get and set back???*/
        //  mMediaHandler.setLocalInputEvtAware(mMediaHandler.isLocalInputEvtAware());

        /*
         * Set call state to connecting so that the user interface would start playing the tones.
         * We do that here because we may be harvesting STUN/TURN addresses in initiateSession()
         * which would take a while.
         */
        callPeer.setState(CallPeerState.CONNECTING);

        // if initializing session fails, set peer to failed by default
        boolean sessionInitiated = false;
        try {
            callPeer.initiateSession(sessionInitiateExtensions, getCallId());
            sessionInitiated = true;
        }
        finally {
            // if initialization throws an exception
            if (!sessionInitiated)
                callPeer.setState(CallPeerState.FAILED);
        }
        return callPeer;
    }

    /**
     * Updates the Jingle sessions for the <code>CallPeer</code>s of this <code>Call</code>, to reflect the
     * current state of the video contents of this <code>Call</code>. Sends a <code>content-modify</code>,
     * <code>content-add</code> or <code>content-remove</code> message to each of the current <code>CallPeer</code>s.
     * <p>
     * cmeng (20210321): Approach aborted due to complexity and NewReceiveStreamEvent not alway gets triggered:
     * - content-remove/content-add are not used on device orientation changed - use blocking impl is aborted due
     * to its complexity.
     * - @see CallPeerJabberImpl#getDirectionForJingle(MediaType)
     *
     * @throws OperationFailedException if a problem occurred during message generation or there was a network problem
     */
    public void modifyVideoContent()
            throws OperationFailedException {
        Timber.d("Updating video content for %s", this);
        boolean change = false;
        for (CallPeerJabberImpl peer : getCallPeerList()) {
            try {
                // cmeng (2016/09/14): Never send 'sendModifyVideoContent' before it is connected => Smack Exception
                if (peer.getState() == CallPeerState.CONNECTED)
                    change |= peer.sendModifyVideoContent();
            }
            catch (SmackException.NotConnectedException | InterruptedException e) {
                throw new OperationFailedException("Could send modify video content to " + peer.getAddress(), 0, e);
            }
        }
        if (change)
            fireCallChangeEvent(CallChangeEvent.CALL_PARTICIPANTS_CHANGE, null, null);
    }


    /**
     * Creates a new call peer upon receiving session-initiate, and sends a RINGING response if required.
     * Handle addCallPeer() in caller to avoid race condition as code here is handled on a new thread;
     * required when transport-info is sent separately
     *
     * @param callPeer the {@link CallPeerJabberImpl}: the one that sent the INVITE.
     * @param jingle the {@link Jingle} that created the session.
     *
     * @see OperationSetBasicTelephonyJabberImpl#processJingleSynchronize(Jingle) in session-initiate
     */
    public void processSessionInitiate(final CallPeerJabberImpl callPeer, Jingle jingle, JingleCallSessionImpl session) {
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
        SdpTransfer transfer = jingle.getExtension(SdpTransfer.class);
        if (transfer != null) {
            String sid = transfer.getSid();

            if (sid != null) {
                ProtocolProviderServiceJabberImpl protocolProvider = getProtocolProvider();
                basicTelephony = (OperationSetBasicTelephonyJabberImpl)
                        protocolProvider.getOperationSet(OperationSetBasicTelephony.class);
                CallJabberImpl attendantCall = basicTelephony.getActiveCallsRepository().findBySid(sid);

                if (attendantCall != null) {
                    attendant = attendantCall.getPeerBySid(sid);
                    if ((attendant != null)
                            && basicTelephony.getFullCalleeURI(attendant.getPeerJid()).equals(transfer.getFrom())
                            && protocolProvider.getOurJid().equals(transfer.getTo())) {
                        autoAnswer = true;
                    }
                }
            }
        }

        // before notifying about this incoming call, make sure the session-initiate looks alright
        try {
            callPeer.processSessionInitiate(jingle);
        }
        catch (SmackException.NotConnectedException | InterruptedException e) {
            callPeer.setState(CallPeerState.INCOMING_CALL);
            return;
        }

        // if DEFAULT_ENCRYPTION is set, to accept the call we need to know that the other party has support for media encryption
        if (getProtocolProvider().getAccountID().getAccountPropertyBoolean(ProtocolProviderFactory.DEFAULT_ENCRYPTION, true)
                && callPeer.getMediaHandler().getAdvertisedEncryptionMethods().length == 0) {

            // send an error response;
            String reasonText = aTalkApp.getResString(R.string.security_encryption_required);
            JingleReason jingleReason = new JingleReason(JingleReason.Reason.security_error, reasonText, null);
            callPeer.hangup(true, jingleReason);
            return;
        }

        if (callPeer.getState() == CallPeerState.FAILED)
            return;
        callPeer.setState(CallPeerState.INCOMING_CALL);

        // in case of attended transfer, auto answer the call.
        if (autoAnswer) {
            // hang up the call before answer, else may terminate as busy.
            try {
                basicTelephony.hangupCallPeer(attendant);
            }
            catch (OperationFailedException e) {
                Timber.w("Failed to hang up on attendant as part of session transfer");
            }

            /* answer directly */
            try {
                callPeer.answer();
            }
            catch (Exception e) {
                Timber.w("Exception occurred while answer transferred call");
            }
            return;
        }

        /*
         * see if offer contains audio and video so that we can propose option to the user
         * (i.e. answer with video if it is a video call...)
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
		    // CALL_RECEIVED_JM stpps the firing of incoming call a second time on session-init
            int callEventId = JingleMessageSessionImpl.isJingleMessageSession(jingle.getSid()) ?
                    CallEvent.CALL_RECEIVED_JM : CallEvent.CALL_RECEIVED;
            parentOpSet.fireCallEvent(callEventId, this, directions);
        }

        // Manages auto answer with "audio only", or "audio/video" answer.
        OperationSetAutoAnswerJabberImpl autoAnswerOpSet = (OperationSetAutoAnswerJabberImpl)
                getProtocolProvider().getOperationSet(OperationSetBasicAutoAnswer.class);

        // See AppCallListener#onCallEvent(): For auto-answer to work properly for JingleMessage incoming call:
        // Setting answerOnJingleMessageAccept flag gets trigger only after <ringing/>; as this method is handled via separate thread;
        if (autoAnswerOpSet != null) {
            autoAnswerOpSet.autoAnswer(this, directions, jingle);
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
    static void setDtlsEncryptionOnTransport(DtlsControl dtlsControl, IceUdpTransport localTransport) {
        String fingerprint = dtlsControl.getLocalFingerprint();
        String hash = dtlsControl.getLocalFingerprintHashFunction();
        String setup = ((DtlsControlImpl) dtlsControl).getSetup().toString();

        localTransport.addChildElement(SrtpFingerprint.getBuilder()
                .setFingerprint(fingerprint)
                .setHash(hash)
                .setSetup(setup)
                .build()
        );
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
    public void toneReceived(DTMFReceivedEvent evt) {
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
    public void setLocalInputEvtAware(boolean enable) {
        localInputEvtAware = enable;
    }

    /**
     * Returns if the call support <code>inputevt</code> (remote control).
     *
     * @return true if the call support <code>inputevt</code>, false otherwise
     */
    public boolean getLocalInputEvtAware() {
        return localInputEvtAware;
    }

    /**
     * Returns the peer whose corresponding session has the specified <code>sid</code>.
     *
     * @param sid the ID of the session whose peer we are looking for.
     *
     * @return the {@link CallPeerJabberImpl} with the specified jingle
     * <code>sid</code> and <code>null</code> if no such peer exists in this call.
     */
    public CallPeerJabberImpl getPeerBySid(String sid) {
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
     *
     * @return <code>true</code> if this call contains a peer with the specified jingle <code>sid</code> and false otherwise.
     */
    public boolean containsSid(String sid) {
        return (getPeerBySid(sid) != null);
    }

    /**
     * Returns the peer associated session-initiate JingleIQ has the specified <code>stanzaId</code>.
     *
     * @param stanzaId the Stanza Id of the session-initiate JingleIQ whose peer we are looking for.
     *
     * @return the {@link CallPeerJabberImpl} with the specified IQ <code>stanzaId</code>
     * and <code>null</code> if no such peer exists in this call.
     */
    public CallPeerJabberImpl getPeerByJingleIQStanzaId(String stanzaId) {
        if (stanzaId == null)
            return null;

        for (CallPeerJabberImpl peer : getCallPeerList()) {
            if (stanzaId.equals(peer.getJingleIQStanzaId()))
                return peer;
        }
        return null;
    }
}
