/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol;

import net.java.sip.communicator.service.protocol.event.CallPeerAdapter;
import net.java.sip.communicator.service.protocol.event.CallPeerChangeEvent;

import java.util.Iterator;

import timber.log.Timber;

/**
 * An Abstract Operation Set defining option to unconditionally auto answer incoming calls.
 *
 * @author Damian Minkov
 * @author Vincent Lucas
 * @author Eng Chong Meng
 */
public abstract class AbstractOperationSetBasicAutoAnswer implements OperationSetBasicAutoAnswer
{
    /**
     * The parent Protocol Provider.
     */
    protected final ProtocolProviderService mPPS;

    /**
     * Should we unconditionally answer.
     */
    protected boolean answerOnJingleMessageAccept = false;

    /**
     * Should we unconditionally answer.
     */
    protected boolean mAnswerUnconditional = false;

    /**
     * Should we answer video calls with video.
     */
    protected boolean mAnswerWithVideo = false;

    /**
     * Creates this operation set, loads stored values, populating local variable settings.
     *
     * @param protocolProvider the parent Protocol Provider.
     */
    public AbstractOperationSetBasicAutoAnswer(ProtocolProviderService protocolProvider)
    {
        this.mPPS = protocolProvider;
    }

    /**
     * Load values from account properties.
     */
    protected void load()
    {
        AccountID acc = mPPS.getAccountID();

        mAnswerUnconditional = acc.getAccountPropertyBoolean(AUTO_ANSWER_UNCOND_PROP, false);
        mAnswerWithVideo = acc.getAccountPropertyBoolean(AUTO_ANSWER_WITH_VIDEO_PROP, false);
    }

    /**
     * Save values to account properties.
     */
    protected abstract void save();

    /**
     * Clear local settings.
     */
    protected void clearLocal()
    {
        mAnswerUnconditional = false;
    }

    /**
     * Clear any previous settings.
     */
    public void clear()
    {
        clearLocal();
        mAnswerWithVideo = false;
        save();
    }

    /**
     * Make a check after creating call locally, should we answer it.
     *
     * @param call The new incoming call to auto-answer if needed.
     * @param isVideoCall Indicates if the remote peer which has created this call wish to have a video call.
     * @return <code>true</code> if we have processed and no further processing is needed, <code>false</code> otherwise.
     */
    public boolean autoAnswer(Call call, boolean isVideoCall)
    {
        if (answerOnJingleMessageAccept || mAnswerUnconditional || satisfyAutoAnswerConditions(call)) {
            this.answerCall(call, isVideoCall);
            return true;
        }
        return false;
    }

    /**
     * Answers call if peer in correct state or wait for it.
     *
     * @param call The new incoming call to auto-answer if needed.
     * @param isVideoCall Indicates if the remote peer which has created this call wish to have a video call.
     */
    protected void answerCall(Call call, boolean isVideoCall)
    {
        // We are here because we satisfy the conditional, or unconditional is true.
        Iterator<? extends CallPeer> peers = call.getCallPeers();

        while (peers.hasNext()) {
            new AutoAnswerThread(peers.next(), isVideoCall);
        }
    }

    /**
     * Checks if the call satisfy the auto answer conditions.
     *
     * @param call The new incoming call to auto-answer if needed.
     * @return <code>true</code> if the call satisfy the auto answer conditions. <code>False</code> otherwise.
     */
    protected abstract boolean satisfyAutoAnswerConditions(Call call);

    /**
     * Sets the auto answer option to unconditionally answer all incoming calls.
     */
    public void setAutoAnswerUnconditional()
    {
        clearLocal();
        mAnswerUnconditional = true;
        save();
    }

    /**
     * Is the auto answer option set to unconditionally answer all incoming calls.
     *
     * @return is auto answer set to unconditional.
     */
    public boolean isAutoAnswerUnconditionalSet()
    {
        return mAnswerUnconditional;
    }

    /**
     * Sets the auto answer with video to video calls.
     *
     * @param answerWithVideo A boolean set to true to activate the auto answer with video
     * when receiving a video call. False otherwise.
     */
    public void setAutoAnswerWithVideo(boolean answerWithVideo)
    {
        this.mAnswerWithVideo = answerWithVideo;
        this.save();
    }

    /**
     * Returns if the auto answer with video to video calls is activated.
     *
     * @return A boolean set to true if the auto answer with video when receiving a video call is
     * activated. False otherwise.
     */
    public boolean isAutoAnswerWithVideoSet()
    {
        return this.mAnswerWithVideo;
    }

    /**
     * Waits for peer to switch into INCOMING_CALL state, before auto-answering the call in a new thread.
     */
    private class AutoAnswerThread extends CallPeerAdapter implements Runnable
    {
        /**
         * The call peer which has generated the call.
         */
        private CallPeer peer;

        /**
         * Indicates if the remote peer which has created this call wish to have a video call.
         */
        private boolean isVideoCall;

        /**
         * Wait for peer to switch into INCOMING_CALL state, before auto-answering the call in a new thread.
         *
         * @param peer The call peer which has generated the call.
         * @param isVideoCall Indicates if the remote peer which has created this call wish to have a video call.
         */
        public AutoAnswerThread(CallPeer peer, boolean isVideoCall)
        {
            this.peer = peer;
            this.isVideoCall = isVideoCall;

            if (peer.getState() == CallPeerState.INCOMING_CALL) {
                new Thread(this).start();
            }
            else {
                peer.addCallPeerListener(this);
            }
        }

        /**
         * Answers the call.
         */
        public void run()
        {
            OperationSetBasicTelephony<?> opSetBasicTelephony = mPPS.getOperationSet(OperationSetBasicTelephony.class);
            OperationSetVideoTelephony opSetVideoTelephony = mPPS.getOperationSet(OperationSetVideoTelephony.class);
            try {
                // If user has configured to answer video call with video, then create a video call.
                // Always answer with video for incoming video call via Jingle Message accept.
                if (this.isVideoCall && (answerOnJingleMessageAccept || mAnswerWithVideo) && (opSetVideoTelephony != null)) {
                    opSetVideoTelephony.answerVideoCallPeer(peer);
                }
                // Else send only audio to the remote peer (remote peer is still able to send us its video stream).
                else if (opSetBasicTelephony != null) {
                    opSetBasicTelephony.answerCallPeer(peer);
                }
            } catch (OperationFailedException e) {
                Timber.e("Failed to auto answer call from: %s; %s", peer, e.getMessage());
            }
        }

        /**
         * If our peer was not in proper state wait for it and then answer.
         *
         * @param evt the <code>CallPeerChangeEvent</code> instance containing the
         */
        @Override
        public void peerStateChanged(CallPeerChangeEvent evt)
        {
            CallPeerState newState = (CallPeerState) evt.getNewValue();

            if (newState == CallPeerState.INCOMING_CALL) {
                evt.getSourceCallPeer().removeCallPeerListener(this);
                new Thread(this).start();
            }
            else if (newState == CallPeerState.DISCONNECTED || newState == CallPeerState.FAILED) {
                evt.getSourceCallPeer().removeCallPeerListener(this);
            }
        }
    }
}
