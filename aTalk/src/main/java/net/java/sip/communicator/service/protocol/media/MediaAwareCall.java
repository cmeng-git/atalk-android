/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.media;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.java.sip.communicator.service.protocol.AbstractCall;
import net.java.sip.communicator.service.protocol.Call;
import net.java.sip.communicator.service.protocol.CallConference;
import net.java.sip.communicator.service.protocol.CallPeer;
import net.java.sip.communicator.service.protocol.CallPeerState;
import net.java.sip.communicator.service.protocol.CallState;
import net.java.sip.communicator.service.protocol.OperationFailedException;
import net.java.sip.communicator.service.protocol.OperationSetBasicTelephony;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;
import net.java.sip.communicator.service.protocol.event.CallChangeEvent;
import net.java.sip.communicator.service.protocol.event.CallChangeListener;
import net.java.sip.communicator.service.protocol.event.CallPeerChangeEvent;
import net.java.sip.communicator.service.protocol.event.CallPeerEvent;
import net.java.sip.communicator.service.protocol.event.CallPeerListener;
import net.java.sip.communicator.service.protocol.event.DTMFListener;
import net.java.sip.communicator.service.protocol.event.DTMFReceivedEvent;
import net.java.sip.communicator.service.protocol.event.SoundLevelListener;

import org.atalk.service.neomedia.MediaUseCase;
import org.atalk.service.neomedia.RTPTranslator;
import org.atalk.service.neomedia.device.MediaDevice;
import org.atalk.service.neomedia.event.SimpleAudioLevelListener;
import org.atalk.service.neomedia.recording.Recorder;
import org.atalk.util.MediaType;

/**
 * A utility class implementing media control code shared between current telephony implementations.
 * This class is only meant for use by protocol implementations and should/could not be accessed by
 * bundles that are simply using the telephony functionalities.
 *
 * @param <T> the peer extension class like, for example, <code>CallPeerSipImpl</code> or
 * <code>CallPeerJabberImpl</code>
 * @param <U> the provider extension class like, for example, <code>OperationSetBasicTelephonySipImpl</code>
 * or <code>OperationSetBasicTelephonySipImpl</code>
 * @param <V> the provider extension class like, for example, <code>ProtocolProviderServiceSipImpl</code> or
 * <code>ProtocolProviderServiceJabberImpl</code>
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public abstract class MediaAwareCall<
        T extends MediaAwareCallPeer<?, ?, V>,
        U extends OperationSetBasicTelephony<V>,
        V extends ProtocolProviderService>
        extends AbstractCall<T, V>
        implements CallPeerListener, PropertyChangeListener, DTMFListener {
    /**
     * The name of the property of <code>MediaAwareCall</code> the value of which corresponds to the
     * value returned by {@link #getDefaultDevice(MediaType)}. The <code>oldValue</code> and the
     * <code>newValue</code> of the fired <code>PropertyChangeEvent</code> are not to be relied on and
     * instead a call to <code>getDefaultDevice</code> is to be performed to retrieve the new value.
     */
    public static final String DEFAULT_DEVICE = "defaultDevice";

    /**
     * Our video streaming policy.
     */
    protected boolean localVideoAllowed = false;

    /**
     * The <code>OperationSetBasicTelephony</code> implementation which created us.
     */
    protected final U parentOpSet;

    /**
     * The list of <code>SoundLevelListener</code>s interested in level changes of local audio.
     * <p>
     * It is implemented as a copy-on-write storage because the number of additions and removals of
     * <code>SoundLevelListener</code>s is expected to be far smaller than the number of audio level
     * changes. The access to it is to be synchronized using
     * {@link #localUserAudioLevelListenersSyncRoot}.
     * </p>
     */
    private List<SoundLevelListener> localUserAudioLevelListeners;

    /**
     * The <code>Object</code> to synchronize the access to {@link #localUserAudioLevelListeners}.
     */
    private final Object localUserAudioLevelListenersSyncRoot = new Object();

    /**
     * The indicator which determines whether this <code>Call</code> is set to transmit "silence"
     * instead of the actual media.
     */
    private boolean mute = false;

    /**
     * Device used in call will be chosen according to <code>MediaUseCase</code>.
     */
    protected MediaUseCase mediaUseCase = MediaUseCase.ANY;

    /**
     * The listener that would actually subscribe for level events from the media handler if there's
     * at least one listener in <code>localUserAudioLevelListeners</code>.
     */
    private final SimpleAudioLevelListener localAudioLevelDelegator = this::fireLocalUserAudioLevelChangeEvent;

    /**
     * Crates a <code>Call</code> instance belonging to <code>parentOpSet</code>.
     *
     * @param parentOpSet a reference to the operation set that's creating us and that
     * we would be able to use for even dispatching.
     * @param sid the Jingle session-initiate id if provided.
     */
    protected MediaAwareCall(U parentOpSet, String sid) {
        super(parentOpSet.getProtocolProvider(), sid);
        this.parentOpSet = parentOpSet;
    }

    /**
     * Adds <code>callPeer</code> to the list of peers in this call. If the call peer is already
     * included in the call, the method has no effect.
     *
     * @param callPeer the new <code>CallPeer</code>
     */
    public void addCallPeer(T callPeer) {
        if (!doAddCallPeer(callPeer))
            return;

        callPeer.addCallPeerListener(this);
        synchronized (localUserAudioLevelListenersSyncRoot) {
            /*
             * If there's someone listening for audio level events, then they'd also like to know about the
             * new peer. Make sure the first element is always the one to listen for local audio events.
             */
            List<T> callPeers = getCallPeerList();

            if ((callPeers.size() == 1) && callPeers.get(0).equals(callPeer)) {
                callPeer.getMediaHandler().setLocalUserAudioLevelListener(localAudioLevelDelegator);
            }
        }
        fireCallPeerEvent(callPeer, CallPeerEvent.CALL_PEER_ADDED);
    }

    /**
     * Removes <code>callPeer</code> from the list of peers in this call. The method has no effect if
     * there was no such peer in the call.
     *
     * @param evt the event containing the <code>CallPeer</code> leaving the call and the reason (if any)
     * for the <code>CallPeerChangeEvent</code>. Use the event as the cause for the call state change event.
     */
    private void removeCallPeer(CallPeerChangeEvent evt) {
        @SuppressWarnings("unchecked")
        T callPeer = (T) evt.getSourceCallPeer();

        if (!doRemoveCallPeer(callPeer))
            return;

        callPeer.removeCallPeerListener(this);
        synchronized (localUserAudioLevelListenersSyncRoot) {
            // remove sound level listeners from the peer
            callPeer.getMediaHandler().setLocalUserAudioLevelListener(null);

            // if there are more peers and the peer was the first, the one that listens for local levels,
            // now lets make sure the new first will listen for local level events
            List<T> callPeers = getCallPeerList();

            if (!callPeers.isEmpty()) {
                callPeers.get(0).getMediaHandler().setLocalUserAudioLevelListener(localAudioLevelDelegator);
            }
        }

        try {
            fireCallPeerEvent(callPeer, CallPeerEvent.CALL_PEER_REMOVED, (evt.getReasonString() != null));
        } finally {
            /*
             * The peer should lose its state once it has finished firing the events in order to
             * allow the listeners to undo.
             */
            callPeer.setCall(null);
        }
        if (getCallPeerCount() == 0)
            setCallState(CallState.CALL_ENDED, evt);
    }

    /**
     * Dummy implementation of a method (inherited from CallPeerListener) that we don't need.
     *
     * @param evt unused.
     */
    public void peerImageChanged(CallPeerChangeEvent evt) {
        // does not concern us
    }

    /**
     * Dummy implementation of a method (inherited from CallPeerListener) that we don't need.
     *
     * @param evt unused.
     */
    public void peerAddressChanged(CallPeerChangeEvent evt) {
        // does not concern us
    }

    /**
     * Dummy implementation of a method (inherited from CallPeerListener) that we don't need.
     *
     * @param evt unused.
     */
    public void peerTransportAddressChanged(CallPeerChangeEvent evt) {
        // does not concern us
    }

    /**
     * Dummy implementation of a method (inherited from CallPeerListener) that we don't need.
     *
     * @param evt unused.
     */
    public void peerDisplayNameChanged(CallPeerChangeEvent evt) {
        // does not concern us
    }

    /**
     * Verifies whether the call peer has entered a state.
     *
     * @param evt The <code>CallPeerChangeEvent</code> instance containing the source event as well as its
     * previous and its new status.
     */
    public void peerStateChanged(CallPeerChangeEvent evt) {
        Object newState = evt.getNewValue();

        if (CallPeerState.DISCONNECTED.equals(newState) || CallPeerState.FAILED.equals(newState)) {
            removeCallPeer(evt);
        }
        else if (CallPeerState.CONNECTED.equals(newState)
                || CallPeerState.CONNECTING_WITH_EARLY_MEDIA.equals(newState)) {
            setCallState(CallState.CALL_IN_PROGRESS);
        }
        else if (CallPeerState.REFERRED.equals(newState)) {
            setCallState(CallState.CALL_REFERRED);
        }
    }

    /**
     * Returns a reference to the <code>OperationSetBasicTelephony</code> implementation instance that created this call.
     *
     * @return a reference to the <code>OperationSetBasicTelephony</code> instance that created this call.
     */
    public U getParentOperationSet() {
        return parentOpSet;
    }

    /**
     * Gets the <code>RTPTranslator</code> which forwards RTP and RTCP traffic between the
     * <code>CallPeer</code>s of this <code>Call</code> when the local peer represented by this
     * <code>Call</code> is acting as a conference focus.
     *
     * @param mediaType the <code>MediaType</code> of the <code>MediaStream</code> which RTP
     * and RTCP traffic is to be forwarded between
     *
     * @return the <code>RTPTranslator</code> which forwards RTP and RTCP traffic between the
     * <code>CallPeer</code>s of this <code>Call</code> when the local peer represented by this
     * <code>Call</code> is acting as a conference focus
     */
    public RTPTranslator getRTPTranslator(MediaType mediaType) {
        return getConference().getRTPTranslator(mediaType);
    }

    /**
     * Gets the indicator which determines whether the local peer represented by this <code>Call</code>
     * is acting as a conference focus and thus may need to send the corresponding parameters in its
     * outgoing signaling.
     *
     * @return <code>true</code> if the local peer represented by this <code>Call</code> is acting as a
     * conference focus; otherwise, <code>false</code>
     */
    @Override
    public boolean isConferenceFocus() {
        return getConference().isConferenceFocus();
    }

    /**
     * Gets a <code>MediaDevice</code> which is capable of capture and/or playback of media of the
     * specified <code>MediaType</code>, is the default choice of the user for a <code>MediaDevice</code>
     * with the specified <code>MediaType</code> and is appropriate for the current state of this <code>Call</code>.
     * <p>
     * For example, when the local peer represented by this <code>Call</code> instance is acting as a
     * conference focus, the audio device must be a mixer.
     * </p>
     *
     * @param mediaType the <code>MediaType</code> in which the retrieved <code>MediaDevice</code> is to capture
     * and/or play back media
     *
     * @return a <code>MediaDevice</code> which is capable of capture and/or playback of media of the
     * specified <code>mediaType</code>, is the default choice of the user for a
     * <code>MediaDevice</code> with the specified <code>mediaType</code> and is appropriate for the
     * current state of this <code>Call</code>
     */
    public MediaDevice getDefaultDevice(MediaType mediaType) {
        return getConference().getDefaultDevice(mediaType, mediaUseCase);
    }

    /**
     * Adds a specific <code>SoundLevelListener</code> to the list of listeners interested in and
     * notified about changes in local sound level related information. When the first listener is
     * being registered the method also registers its single listener with the call peer media
     * handlers so that it would receive level change events and delegate them to the listeners that
     * have registered with us.
     *
     * @param l the <code>SoundLevelListener</code> to add
     */
    @Override
    public void addLocalUserSoundLevelListener(SoundLevelListener l) {
        synchronized (localUserAudioLevelListenersSyncRoot) {
            if ((localUserAudioLevelListeners == null) || localUserAudioLevelListeners.isEmpty()) {
                /*
                 * If this is the first listener that's being registered, we also need to register
                 * ourselves as an audio level listener with the MediaHandler. We do this so that
                 * audio level would only be calculated if anyone is interested in receiving them.
                 */
                Iterator<T> callPeers = getCallPeers();
                while (callPeers.hasNext()) {
                    callPeers.next().getMediaHandler().setLocalUserAudioLevelListener(localAudioLevelDelegator);
                }
            }

            /*
             * Implement localUserAudioLevelListeners as a copy-on-write storage so that iterators
             * over it can iterate without ConcurrentModificationExceptions.
             */
            localUserAudioLevelListeners = (localUserAudioLevelListeners == null)
                    ? new ArrayList<>() : new ArrayList<>(localUserAudioLevelListeners);
            localUserAudioLevelListeners.add(l);
        }
    }

    /**
     * Removes a specific <code>SoundLevelListener</code> from the list of listeners interested in and
     * notified about changes in local sound level related information. If <code>l</code> is the last
     * listener that we had here we are also going to unregister our own level event delegate in
     * order to stop level calculations.
     *
     * @param l the <code>SoundLevelListener</code> to remove
     */
    @Override
    public void removeLocalUserSoundLevelListener(SoundLevelListener l) {
        synchronized (localUserAudioLevelListenersSyncRoot) {
            /*
             * Implement localUserAudioLevelListeners as a copy-on-write storage so that iterators
             * over it can iterate over it without ConcurrentModificationExceptions.
             */
            if (localUserAudioLevelListeners != null) {
                localUserAudioLevelListeners = new ArrayList<>(localUserAudioLevelListeners);
                if (localUserAudioLevelListeners.remove(l)
                        && localUserAudioLevelListeners.isEmpty())
                    localUserAudioLevelListeners = null;
            }

            if ((localUserAudioLevelListeners == null) || localUserAudioLevelListeners.isEmpty()) {
                // if this was the last listener that was registered with us then
                // no long need to have a delegator registered with the call
                // peer media handlers. We therefore remove it so that audio
                // level calculations would be ceased.
                Iterator<T> callPeers = getCallPeers();
                while (callPeers.hasNext()) {
                    callPeers.next().getMediaHandler().setLocalUserAudioLevelListener(null);
                }
            }
        }
    }

    /**
     * Notified by its very majesty the media service about changes in the audio level of the local
     * user, this listener generates the corresponding events and delivers them to the listeners
     * that have registered here.
     *
     * @param newLevel the new audio level of the local user.
     */
    private void fireLocalUserAudioLevelChangeEvent(int newLevel) {
        List<SoundLevelListener> localUserAudioLevelListeners;

        synchronized (localUserAudioLevelListenersSyncRoot) {
            /*
             * Since the localUserAudioLevelListeners field of this MediaAwareCall is implemented as
             * a copy-on-write storage, just get a reference to it and it should be safe to iterate
             * over it without ConcurrentModificationExceptions.
             */
            localUserAudioLevelListeners = this.localUserAudioLevelListeners;
        }

        if (localUserAudioLevelListeners != null) {
            /*
             * Iterate over localUserAudioLevelListeners using an index rather than an Iterator in
             * order to try to reduce the number of allocations (as the number of audio level
             * changes is expected to be very large).
             */
            int localUserAudioLevelListenerCount = localUserAudioLevelListeners.size();
            for (int i = 0; i < localUserAudioLevelListenerCount; i++)
                localUserAudioLevelListeners.get(i).soundLevelChanged(this, newLevel);
        }
    }

    /**
     * Determines whether this call is mute.
     *
     * @return <code>true</code> if an audio streams being sent to the call peers are currently muted;
     * <code>false</code>, otherwise
     */
    public boolean isMute() {
        return this.mute;
    }

    /**
     * Sets the mute property for this call.
     *
     * @param mute the new value of the mute property for this call
     */
    public void setMute(boolean mute) {
        if (this.mute != mute) {
            this.mute = mute;

            Iterator<T> callPeers = getCallPeers();
            while (callPeers.hasNext())
                callPeers.next().setMute(this.mute);
        }
    }

    /**
     * Modifies the local media setup of all peers in the call to reflect the requested setting for
     * the streaming of the local video and then passes the setting to the participating
     * <code>MediaAwareCallPeer</code> instances.
     *
     * @param allowed <code>true</code> if local video transmission is allowed and <code>false</code> otherwise.
     * @param useCase the use case of the video (i.e video call or desktop streaming/sharing session)
     *
     * @throws OperationFailedException if video initialization fails.
     */
    public void setLocalVideoAllowed(boolean allowed, MediaUseCase useCase)
            throws OperationFailedException {
        /*
         * If the use case changes and we don't change the device, calls to getDefaultDevice() will
         * return the device for the old use case ( because it cached in MediaAwareCallConference)
         */
        if (mediaUseCase != useCase)
            setVideoDevice(null, useCase);

        localVideoAllowed = allowed;
        mediaUseCase = useCase;

        // Record the setting locally and notify all callPeers.
        Iterator<T> callPeers = getCallPeers();
        while (callPeers.hasNext())
            callPeers.next().setLocalVideoAllowed(allowed);
    }

    /**
     * Get the media use case.
     *
     * @return media use case
     */
    public MediaUseCase getMediaUseCase() {
        return mediaUseCase;
    }

    /**
     * Determines whether the streaming of local video in this <code>Call</code> is currently allowed.
     * The setting does not reflect the availability of actual video capture devices, it just
     * expresses the local policy (or desire of the user) to have the local video streamed in the
     * case the system is actually able to do so.
     *
     * @param useCase the use case of the video (i.e video call or desktop streaming/sharing session)
     *
     * @return <code>true</code> if the streaming of local video for this <code>Call</code> is allowed;
     * otherwise, <code>false</code>
     */
    public boolean isLocalVideoAllowed(MediaUseCase useCase) {
        return mediaUseCase.equals(useCase) && localVideoAllowed;
    }

    /**
     * Determines whether we are currently streaming video toward at least one of the peers in this call.
     *
     * @return <code>true</code> if we are currently streaming video toward at least one of the peers in
     * this call and <code>false</code> otherwise.
     */
    public boolean isLocalVideoStreaming() {
        Iterator<T> callPeers = getCallPeers();
        while (callPeers.hasNext()) {
            if (callPeers.next().isLocalVideoStreaming())
                return true;
        }
        return false;
    }

    /**
     * Registers a <code>listener</code> with all <code>CallPeer</code> currently participating with the
     * call so that it would be notified of changes in video related properties (e.g.
     * <code>LOCAL_VIDEO_STREAMING</code>).
     *
     * @param listener the <code>PropertyChangeListener</code> to be notified when the properties associated with
     * member <code>CallPeer</code>s change their values.
     */
    public void addVideoPropertyChangeListener(PropertyChangeListener listener) {
        Iterator<T> callPeers = getCallPeers();
        while (callPeers.hasNext())
            callPeers.next().addVideoPropertyChangeListener(listener);
    }

    /**
     * Removes <code>listener</code> from all <code>CallPeer</code>s currently participating with the call
     * so that it won't receive further notifications on changes in video related properties (e.g.
     * <code>LOCAL_VIDEO_STREAMING</code>).
     *
     * @param listener the <code>PropertyChangeListener</code> to unregister from member <code>CallPeer</code>s
     * change their values.
     */
    public void removeVideoPropertyChangeListener(PropertyChangeListener listener) {
        Iterator<T> callPeers = getCallPeers();
        while (callPeers.hasNext())
            callPeers.next().removeVideoPropertyChangeListener(listener);
    }

    /**
     * Creates a new <code>Recorder</code> which is to record this <code>Call</code> (into a file which is
     * to be specified when starting the returned <code>Recorder</code>).
     *
     * @return a new <code>Recorder</code> which is to record this <code>Call</code> (into a file which is
     * to be specified when starting the returned <code>Recorder</code>)
     *
     * @throws OperationFailedException if anything goes wrong while creating the new <code>Recorder</code> for this
     * <code>Call</code>
     */
    public Recorder createRecorder()
            throws OperationFailedException {
        final Recorder recorder
                = ProtocolMediaActivator.getMediaService().createRecorder(getDefaultDevice(MediaType.AUDIO));

        if (recorder != null) {
            // listens for mute event to update recorder
            final PropertyChangeListener muteListener = evt -> {
                if (evt.getPropertyName().equals(CallPeer.MUTE_PROPERTY_NAME)) {
                    updateRecorderMuteState(recorder);
                }
            };

            // Make sure the recorder is stopped when this call ends.
            final CallChangeListener callChangeListener = new CallChangeListener() {
                /**
                 * When call ends we stop recording.
                 *
                 * @param evt the <code>CallChangeEvent</code> instance containing the source
                 */
                public void callStateChanged(CallChangeEvent evt) {
                    if (!CallChangeEvent.CALL_STATE_CHANGE.equals(evt.getPropertyName()))
                        return;

                    if (CallState.CALL_ENDED.equals(evt.getNewValue()))
                        recorder.stop();
                }

                /**
                 * We listen for mute on newly added call peers.
                 *
                 * @param evt the <code>CallPeerEvent</code> containing the source call
                 */
                public void callPeerAdded(CallPeerEvent evt) {
                    updateRecorderMuteState(recorder);
                    evt.getSourceCallPeer().addPropertyChangeListener(muteListener);
                }

                /**
                 * We stop listen for mute on removed call peers.
                 *
                 * @param evt he <code>CallPeerEvent</code> containing the source call
                 */
                public void callPeerRemoved(CallPeerEvent evt) {
                    updateRecorderMuteState(recorder);
                    evt.getSourceCallPeer().removePropertyChangeListener(muteListener);
                }
            };

            addCallChangeListener(callChangeListener);

            Iterator<Recorder.Listener> iterListeners
                    = ProtocolMediaActivator.getMediaService().getRecorderListeners();
            while (iterListeners.hasNext())
                recorder.addListener(iterListeners.next());

            /*
             * If the recorder gets stopped earlier than this call ends, don't wait for the end of
             * the call because callChangeListener will keep a reference to the stopped recorder.
             */
            recorder.addListener(recorder1 -> {
                removeCallChangeListener(callChangeListener);
                Iterator<Recorder.Listener> iter = ProtocolMediaActivator.getMediaService().getRecorderListeners();
                while (iter.hasNext())
                    recorder1.removeListener(iter.next());
            });

            // add listener for mute event to all current peers
            Iterator<T> callPeers = getCallPeers();
            while (callPeers.hasNext())
                callPeers.next().addPropertyChangeListener(muteListener);
            updateRecorderMuteState(recorder);
        }
        return recorder;
    }

    /**
     * Updates the recorder mute state by looking at the peers state. If one of the peers is not
     * muted and the recorder is not. If all the peers are muted so must be and the recorder.
     *
     * @param recorder the recorder we are operating on.
     */
    private void updateRecorderMuteState(Recorder recorder) {
        Iterator<T> callPeers = getCallPeers();
        while (callPeers.hasNext()) {
            if (!callPeers.next().isMute()) {
                // one peer is not muted so we unmute.
                recorder.setMute(false);
                return;
            }
        }
        // all peers are muted, so we mute the recorder
        recorder.setMute(true);
    }

    /**
     * Sets the <code>MediaDevice</code> to be used by this <code>Call</code> for audio capture and/or playback.
     *
     * @param audioDevice the <code>MediaDevice</code> to be used by this <code>Call</code> for audio capture and/or playback
     */
    public void setAudioDevice(MediaDevice audioDevice) {
        getConference().setDevice(MediaType.AUDIO, audioDevice);
    }

    /**
     * Sets the <code>MediaDevice</code> to be used by this <code>Call</code> for video capture and/or
     * playback and the use case of the video streaming in this <code>MediaAwareCall</code>.
     *
     * @param videoDevice the <code>MediaDevice</code> to be used by this <code>Call</code> for video capture and/or playback
     * @param useCase the use case of the video streaming to be set on this <code>MediaAwareCall</code> such as
     * webcam capture or desktop sharing/streaming
     */
    public void setVideoDevice(MediaDevice videoDevice, MediaUseCase useCase) {
        /*
         * XXX If the mediaUseCase is changing, it is important to realize the change prior to
         * setting the specified videoDevice on the associated conference because the method
         * MediaAwareCallConference.setDevice will indirectly utilize the mediaUseCase.
         */
        mediaUseCase = useCase;
        getConference().setDevice(MediaType.VIDEO, videoDevice);
    }

    /**
     * Sets the state of this <code>Call</code> and fires a new <code>CallChangeEvent</code> notifying the
     * registered <code>CallChangeListener</code>s about the change of the state.
     *
     * @param newState the <code>CallState</code> into which this <code>Call</code> is to enter
     * @param cause the <code>CallPeerChangeEvent</code> which is the cause for the request to have this
     * <code>Call</code> enter the specified <code>CallState</code>
     *
     * @see Call#setCallState(CallState, CallPeerChangeEvent)
     */
    @Override
    protected void setCallState(CallState newState, CallPeerChangeEvent cause) {
        try {
            super.setCallState(newState, cause);
        } finally {
            if (CallState.CALL_ENDED.equals(getCallState()))
                ProtocolMediaActivator.getMediaService().removePropertyChangeListener(this);
        }
    }

    /**
     * Notifies this instance about a change of the value of a specific property from a specific old
     * value to a specific new value.
     *
     * @param ev a <code>PropertyChangeEvent</code> which specifies the name of the property which has its
     * value changed and the old and new values
     */
    public void propertyChange(PropertyChangeEvent ev) {
        /*
         * Forward PropertyChangeEvents notifying about changes in the values of MediaAwareCall
         * properties which are delegated to MediaAwareCallConference.
         */
        if (ev.getSource() instanceof CallConference) {
            String propertyName = ev.getPropertyName();

            if (CONFERENCE_FOCUS.equals(propertyName)) {
                conferenceFocusChanged((Boolean) ev.getOldValue(), (Boolean) ev.getNewValue());
            }
            else if (DEFAULT_DEVICE.equals(propertyName)) {
                firePropertyChange(DEFAULT_DEVICE, ev.getOldValue(), ev.getNewValue());
            }
        }
    }

    /**
     * Notifies this instance that the value of its property {@link Call#CONFERENCE_FOCUS} has
     * changed from a specific old value to a specific new value. Fires a
     * <code>PropertyChangeEvent</code> to the registered <code>PropertyChangeListener</code>s. Protocol
     * implementations which extend <code>MediaAwareCall</code> will likely want to override in order to
     * add notifying the associated <code>CallPeer</code>s about the change of the property value (e.g.
     * SIP will want to include the &quot;isfocus&quot; parameter in the Contact header while the
     * local peer is acting as a conference focus.)
     *
     * @param oldValue the value of the property <code>CONFERENCE_FOCUS</code> before the change
     * @param newValue the value of the property <code>CONFERENCE_FOCUS</code> after the change
     */
    protected void conferenceFocusChanged(boolean oldValue, boolean newValue) {
        firePropertyChange(CONFERENCE_FOCUS, oldValue, newValue);
    }

    /**
     * {@inheritDoc}
     *
     * Creates a new <code>MediaAwareCallConference</code> to represent the media-specific information
     * associated with the telephony conference-related state of this <code>MediaAwareCall</code>.
     */
    @Override
    protected CallConference createConference() {
        return new MediaAwareCallConference(false, this.useTranslator);
    }

    /**
     * {@inheritDoc}
     *
     * Makes sure that the telephony conference-related state of this <code>MediaAwareCall</code> is
     * represented by a <code>MediaAwareCallConference</code> instance.
     */
    @Override
    public MediaAwareCallConference getConference() {
        return (MediaAwareCallConference) super.getConference();
    }

    /**
     * {@inheritDoc}
     *
     * Listens to the changes in the values of the properties of this <code>Call</code>.
     */
    @Override
    protected void firePropertyChange(String property, Object oldValue, Object newValue) {
        if (oldValue != newValue) {
            /*
             * Listen to the changes in the values of the properties of the telephony
             * conference-related state of this Call. For example, MediaAwareCall delegates some of
             * its properties (e.g. DEFAULT_DEVICE) to its associated MediaAwareCallConference so
             * changes to the values of the properties of the latter should result in
             * PropertyChangeEvents fired by the former (as well).
             */
            if (CONFERENCE.equals(property)) {
                if (oldValue != null) {
                    ((CallConference) oldValue).removePropertyChangeListener(this);
                }
                if (newValue != null) {
                    ((CallConference) newValue).addPropertyChangeListener(this);
                }
            }
        }

        super.firePropertyChange(property, oldValue, newValue);
    }

    /**
     * {@inheritDoc}
     *
     * Makes sure that the telephony conference-related state of this <code>MediaAwareCall</code> is
     * represented by a <code>MediaAwareCallConference</code> instance.
     */
    @Override
    public void setConference(CallConference conference) {
        super.setConference(conference);
    }

    /**
     * {@inheritDoc}
     *
     * Implements
     * {@link DTMFListener#toneReceived(net.java.sip.communicator.service.protocol.event.DTMFReceivedEvent)}
     */
    @Override
    public void toneReceived(DTMFReceivedEvent evt) {
        // Stub
    }
}
