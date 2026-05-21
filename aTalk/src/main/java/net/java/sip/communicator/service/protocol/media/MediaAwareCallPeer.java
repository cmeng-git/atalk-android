/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.media;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import net.java.sip.communicator.service.protocol.AbstractCallPeer;
import net.java.sip.communicator.service.protocol.CallPeerState;
import net.java.sip.communicator.service.protocol.CallState;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;
import net.java.sip.communicator.service.protocol.event.CallPeerChangeEvent;
import net.java.sip.communicator.service.protocol.event.CallPeerEvent;
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityNegotiationStartedEvent;
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityOffEvent;
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityOnEvent;
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityStatusEvent;
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityTimeoutEvent;
import net.java.sip.communicator.service.protocol.event.SoundLevelListener;

import org.atalk.service.neomedia.MediaDirection;
import org.atalk.service.neomedia.SrtpControl;
import org.atalk.service.neomedia.event.SimpleAudioLevelListener;
import org.atalk.service.neomedia.event.SrtpListener;
import org.atalk.util.MediaType;

import org.jivesoftware.smack.XMPPConnection;

import timber.log.Timber;

/**
 * A utility class implementing media control code shared between current telephony implementations.
 * This class is only meant for use by protocol implementations and should/could not be accessed by
 * bundles that are simply using the telephony functionalities.
 *
 * @param <T> the peer extension class like for example <code>CallSipImpl</code> or <code>CallJabberImpl</code>
 * @param <U> the media handler extension class like for example <code>CallPeerMediaHandlerSipImpl</code> or
 * <code>CallPeerMediaHandlerJabberImpl</code>
 * @param <V> the provider extension class like for example <code>ProtocolProviderServiceSipImpl</code> or
 * <code>ProtocolProviderServiceJabberImpl</code>
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Boris Grozev
 */
public abstract class MediaAwareCallPeer<
        T extends MediaAwareCall<?, ?, V>,
        U extends CallPeerMediaHandler<?>,
        V extends ProtocolProviderService> extends AbstractCallPeer<T, V>
        implements SrtpListener, SimpleAudioLevelListener {
    /**
     * The call this peer belongs to.
     */
    private T call;

    /**
     * A byte array containing the image/photo representing the call peer.
     */
    private byte[] image;

    /**
     * The media handler class handles all media management for a single <code>CallPeer</code>. This
     * includes initializing and configuring streams, generating SDP, handling ICE, etc. One
     * instance of <code>CallPeer</code>always corresponds to exactly one instance of
     * <code>CallPeerMediaHandler</code> and both classes are only separated for reasons of readability.
     */
    private U mediaHandler;

    /**
     * The <code>PropertyChangeListener</code> which listens to {@link CallPeerMediaHandler} for changes
     * in the values of its properties.
     */
    private PropertyChangeListener mediaHandlerPropertyChangeListener;

    /**
     * A string uniquely identifying the peer.
     */
    private String peerID;

    /**
     * The protocol provider that this peer belongs to.
     */
    protected final V mPPS;

    protected final XMPPConnection mConnection;

    /**
     * The list of <code>SoundLevelListener</code>s interested in level changes in the audio we are
     * getting from the remote peer.
     *
     * It is implemented as a copy-on-write storage because the number of additions and removals of
     * <code>SoundLevelListener</code>s is expected to be far smaller than the number of audio level
     * changes. The access to it is to be synchronized using
     * {@link #streamSoundLevelListenersSyncRoot}.
     */
    private List<SoundLevelListener> streamSoundLevelListeners;

    /**
     * The <code>Object</code> to synchronize the access to {@link #streamSoundLevelListeners}.
     */
    private final Object streamSoundLevelListenersSyncRoot = new Object();

    /**
     * The <code>List</code> of <code>PropertyChangeListener</code>s listening to this <code>CallPeer</code> for
     * changes in the values of its properties related to video.
     */
    private final List<PropertyChangeListener> videoPropertyChangeListeners = new LinkedList<>();

    /**
     * Represents the last Conference Information (RFC4575) document sent to this <code>CallPeer</code>.
     * This is always a document with state "full", even if the last document actually sent was a "partial"
     */
    private ConferenceInfoDocument lastConferenceInfoSent = null;

    /**
     * The time (as obtained by <code>System.currentTimeMillis()</code>) at which a Conference
     * Information (RFC4575) document was last sent to this <code>CallPeer</code>.
     */
    private long lastConferenceInfoSentTimestamp = -1;

    /**
     * The last Conference Information (RFC4575) document sent to us by this <code>CallPeer</code>. This
     * is always a document with state "full", which is only gets updated by "partial" or "deleted" documents.
     */
    private ConferenceInfoDocument lastConferenceInfoReceived = null;

    /**
     * Whether a conference-info document has been scheduled to be sent to this <code>CallPeer</code>
     */
    private boolean confInfoScheduled = false;

    /**
     * Synchronization object for confInfoScheduled
     */
    private final Object confInfoScheduledSyncRoot = new Object();

    /**
     * Creates a new call peer with address <code>peerAddress</code>.
     *
     * @param owningCall the call that contains this call peer.
     */
    public MediaAwareCallPeer(T owningCall) {
        this.call = owningCall;
        mPPS = owningCall.getProtocolProvider();
        mConnection = mPPS.getConnection();

        // create the uid
        this.peerID = String.valueOf(System.currentTimeMillis()) + hashCode();
    }

    /**
     * Adds a specific <code>SoundLevelListener</code> to the list of listeners interested in and
     * notified about changes in the sound level of the audio sent by the remote party. When the
     * first listener is being registered the method also registers its single listener with the
     * media handler so that it would receive level change events and delegate them to the listeners
     * that have registered with us.
     *
     * @param listener the <code>SoundLevelListener</code> to add
     */
    public void addStreamSoundLevelListener(SoundLevelListener listener) {
        synchronized (streamSoundLevelListenersSyncRoot) {
            if ((streamSoundLevelListeners == null) || streamSoundLevelListeners.isEmpty()) {
                CallPeerMediaHandler<?> mediaHandler = getMediaHandler();
                /*
                 * If this is the first listener that's being registered with us, we also need
                 * to register ourselves as an audio level listener with the media handler. We
                 * do this so that audio levels would only be calculated if anyone is interested
                 * in receiving them.
                 */
                mediaHandler.setStreamAudioLevelListener(this);
            }
            /*
             * Implement streamAudioLevelListeners as a copy-on-write storage so that iterators over
             * it can iterate without ConcurrentModificationExceptions.
             */
            streamSoundLevelListeners = (streamSoundLevelListeners == null)
                    ? new ArrayList<>() : new ArrayList<>(streamSoundLevelListeners);
            streamSoundLevelListeners.add(listener);
        }
    }

    /**
     * Adds a specific <code>PropertyChangeListener</code> to the list of listeners which get notified
     * when the properties (e.g. LOCAL_VIDEO_STREAMING) associated with this <code>CallPeer</code>
     * change their values.
     *
     * @param listener the <code>PropertyChangeListener</code> to be notified when the properties associated with
     * the specified <code>Call</code> change their values
     */
    public void addVideoPropertyChangeListener(PropertyChangeListener listener) {
        if (listener == null)
            throw new NullPointerException("listener");

        synchronized (videoPropertyChangeListeners) {
            /*
             * The video is part of the media-related functionality and thus it is the
             * responsibility of mediaHandler. So listen to mediaHandler for video-related property
             * changes and re-fire them as originating from this instance.
             */
            if (!videoPropertyChangeListeners.contains(listener)
                    && videoPropertyChangeListeners.add(listener)
                    && (mediaHandlerPropertyChangeListener == null)) {
                mediaHandlerPropertyChangeListener = new PropertyChangeListener() {
                    public void propertyChange(PropertyChangeEvent event) {
                        Iterable<PropertyChangeListener> listeners;

                        synchronized (videoPropertyChangeListeners) {
                            listeners = new LinkedList<>(videoPropertyChangeListeners);
                        }

                        PropertyChangeEvent thisEvent = new PropertyChangeEvent(this,
                                event.getPropertyName(), event.getOldValue(), event.getNewValue());

                        for (PropertyChangeListener listener : listeners)
                            listener.propertyChange(thisEvent);
                    }
                };
                getMediaHandler().addPropertyChangeListener(mediaHandlerPropertyChangeListener);
            }
        }
    }

    /**
     * Notified by its very majesty the media service about changes in the audio level of the stream
     * coming from this peer, the method generates the corresponding events and delivers them to the
     * listeners that have registered here.
     *
     * @param newLevel the new audio level of the audio stream received from the remote peer
     */
    public void audioLevelChanged(int newLevel) {
        fireStreamSoundLevelChanged(newLevel);
    }

    /**
     * Does nothing.
     *
     * @param evt the event.
     */
    public void callPeerAdded(CallPeerEvent evt) {
    }

    /**
     * Does nothing.
     *
     * @param evt the event.
     */
    public void callPeerRemoved(CallPeerEvent evt) {
    }

    /**
     * Invokes {@link SoundLevelListener#soundLevelChanged(Object, int) on the
     * <code>SoundLevelListener</code>s interested in the changes of the audio stream received from the
     * remote peer i.e. in {@link #streamSoundLevelListeners}.
     *
     * @param newLevel the new value of the sound level to notify <code>streamSoundLevelListeners</code> about
     */
    private void fireStreamSoundLevelChanged(int newLevel) {
        List<SoundLevelListener> streamSoundLevelListeners;

        synchronized (streamSoundLevelListenersSyncRoot) {
            /*
             * Since the streamAudioLevelListeners field of this MediaAwareCallPeer is implemented
             * as a copy-on-write storage, just get a reference to it and it should be safe to
             * iterate over it without ConcurrentModificationExceptions.
             */
            streamSoundLevelListeners = this.streamSoundLevelListeners;
        }

        if (streamSoundLevelListeners != null) {
            /*
             * Iterate over streamAudioLevelListeners using an index rather than an Iterator in
             * order to try to reduce the number of allocations (as the number of audio level
             * changes is expected to be very large).
             */
            int streamSoundLevelListenerCount = streamSoundLevelListeners.size();

            for (int i = 0; i < streamSoundLevelListenerCount; i++) {
                streamSoundLevelListeners.get(i).soundLevelChanged(this, newLevel);
            }
        }
    }

    /**
     * Returns a reference to the call that this peer belongs to. Calls are created by underlying
     * telephony protocol implementations.
     *
     * @return a reference to the call containing this peer.
     */
    @Override
    public T getCall() {
        return call;
    }

    /**
     * The method returns an image representation of the call peer if one is available.
     *
     * @return byte[] a byte array containing the image or null if no image is available.
     */
    public byte[] getImage() {
        return image;
    }

    /**
     * Returns a reference to the <code>CallPeerMediaHandler</code> used by this peer. The media handler
     * class handles all media management for a single <code>CallPeer</code>. This includes initializing
     * and configuring streams, generating SDP, handling ICE, etc. One instance of <code>CallPeer</code>
     * always corresponds to exactly one instance of <code>CallPeerMediaHandler</code> and both classes
     * are only separated for reasons of readability.
     *
     * @return a reference to the <code>CallPeerMediaHandler</code> instance that this peer uses for
     * media related tips and tricks.
     */
    public U getMediaHandler() {
        return mediaHandler;
    }

    /**
     * Returns a unique identifier representing this peer.
     *
     * @return an identifier representing this call peer.
     */
    public String getPeerID() {
        return peerID;
    }

    /**
     * Returns the protocol provider that this peer belongs to.
     *
     * @return a reference to the <code>ProtocolProviderService</code> that this peer belongs to.
     */
    @Override
    public V getProtocolProvider() {
        return mPPS;
    }

    /**
     * Determines whether we are currently streaming video toward whoever this
     * <code>MediaAwareCallPeer</code> represents.
     *
     * @return <code>true</code> if we are currently streaming video toward this
     * <code>CallPeer</code> and <code>false</code> otherwise.
     */
    public boolean isLocalVideoStreaming() {
        return getMediaHandler().isLocalVideoTransmissionEnabled();
    }

    /**
     * Determines whether the audio stream (if any) being sent to this peer is mute.
     *
     * @return <code>true</code> if an audio stream is being sent to this peer and it is currently mute;
     * <code>false</code>, otherwise
     */
    @Override
    public boolean isMute() {
        return getMediaHandler().isMute();
    }

    /**
     * Logs <code>message</code> and <code>cause</code> and sets this <code>peer</code>'s state to
     * <code>CallPeerState.FAILED</code>
     *
     * @param message a message to log and display to the user.
     * @param throwable the exception that cause the error we are logging
     */
    public void logAndFail(String message, Throwable throwable) {
        Timber.e(throwable, "%s", message);
        setState(CallPeerState.FAILED, message);
    }

    /**
     * Updates the state of this <code>CallPeer</code> to match the locally-on-hold status of our media handler.
     */
    public void reevalLocalHoldStatus() {
        CallPeerState state = getState();
        boolean locallyOnHold = getMediaHandler().isLocallyOnHold();

        if (CallPeerState.ON_HOLD_LOCALLY.equals(state)) {
            if (!locallyOnHold)
                setState(CallPeerState.CONNECTED);
        }
        else if (CallPeerState.ON_HOLD_MUTUALLY.equals(state)) {
            if (!locallyOnHold)
                setState(CallPeerState.ON_HOLD_REMOTELY);
        }
        else if (CallPeerState.ON_HOLD_REMOTELY.equals(state)) {
            if (locallyOnHold)
                setState(CallPeerState.ON_HOLD_MUTUALLY);
        }
        else if (locallyOnHold) {
            setState(CallPeerState.ON_HOLD_LOCALLY);
        }
    }

    /**
     * Updates the state of this <code>CallPeer</code> to match the remotely-on-hold status of our media handler.
     */
    public void reevalRemoteHoldStatus() {
        boolean remotelyOnHold = getMediaHandler().isRemotelyOnHold();

        CallPeerState state = getState();
        if (CallPeerState.ON_HOLD_LOCALLY.equals(state)) {
            if (remotelyOnHold)
                setState(CallPeerState.ON_HOLD_MUTUALLY);
        }
        else if (CallPeerState.ON_HOLD_MUTUALLY.equals(state)) {
            if (!remotelyOnHold)
                setState(CallPeerState.ON_HOLD_LOCALLY);
        }
        else if (CallPeerState.ON_HOLD_REMOTELY.equals(state)) {
            if (!remotelyOnHold)
                setState(CallPeerState.CONNECTED);
        }
        else if (remotelyOnHold) {
            setState(CallPeerState.ON_HOLD_REMOTELY);
        }
    }

    /**
     * Removes a specific <code>SoundLevelListener</code> of the list of listeners interested in and
     * notified about changes in stream sound level related information.
     *
     * @param listener the <code>SoundLevelListener</code> to remove
     */
    public void removeStreamSoundLevelListener(SoundLevelListener listener) {
        synchronized (streamSoundLevelListenersSyncRoot) {
            /*
             * Implement streamAudioLevelListeners as a copy-on-write storage so that iterators over
             * it can iterate over it without ConcurrentModificationExceptions.
             */
            if (streamSoundLevelListeners != null) {
                streamSoundLevelListeners = new ArrayList<>(streamSoundLevelListeners);
                if (streamSoundLevelListeners.remove(listener)
                        && streamSoundLevelListeners.isEmpty())
                    streamSoundLevelListeners = null;
            }

            if ((streamSoundLevelListeners == null) || streamSoundLevelListeners.isEmpty()) {
                // if this was the last listener then we also need to remove
                // ourselves as an audio level so that audio levels would only
                // be calculated if anyone is interested in receiving them.
                getMediaHandler().setStreamAudioLevelListener(null);
            }
        }
    }

    /**
     * Removes a specific <code>PropertyChangeListener</code> from the list of listeners which get
     * notified when the properties (e.g. LOCAL_VIDEO_STREAMING) associated with this
     * <code>CallPeer</code> change their values.
     *
     * @param listener the <code>PropertyChangeListener</code> to no longer be notified when the properties
     * associated with the specified <code>Call</code> change their values
     */
    public void removeVideoPropertyChangeListener(PropertyChangeListener listener) {
        if (listener != null)
            synchronized (videoPropertyChangeListeners) {
                /*
                 * The video is part of the media-related functionality and thus it is the
                 * responsibility of mediaHandler. So we're listening to mediaHandler for
                 * video-related property changes and w're re-firing them as originating from this
                 * instance. Make sure that we're not listening to mediaHandler if noone is
                 * interested in video-related property changes originating from this instance.
                 */
                if (videoPropertyChangeListeners.remove(listener)
                        && videoPropertyChangeListeners.isEmpty()
                        && (mediaHandlerPropertyChangeListener != null)) {
                    // getMediaHandler()
                    // .removePropertyChangeListener(
                    // mediaHandlerPropertyChangeListener);
                    mediaHandlerPropertyChangeListener = null;
                }
            }
    }

    /**
     * Sets the security message associated with a failure/warning or information coming from the
     * encryption protocol.
     *
     * @param messageType the type of the message.
     * @param i18nMessage the message
     * @param severity severity level
     */
    public void securityMessageReceived(String messageType, String i18nMessage, int severity) {
        fireCallPeerSecurityMessageEvent(messageType, i18nMessage, severity);
    }

    /**
     * Indicates that the other party has timeout replying to our offer to secure the connection.
     *
     * @param mediaType the <code>MediaType</code> of the call session
     * @param sender the security controller that caused the event
     */
    public void securityNegotiationStarted(MediaType mediaType, SrtpControl sender) {
        fireCallPeerSecurityNegotiationStartedEvent(new CallPeerSecurityNegotiationStartedEvent(
                this, toSessionType(mediaType), sender));
    }

    /**
     * Indicates that the other party has timeouted replying to our offer to secure the connection.
     *
     * @param mediaType the <code>MediaType</code> of the call session
     */
    public void securityTimeout(MediaType mediaType) {
        fireCallPeerSecurityTimeoutEvent(new CallPeerSecurityTimeoutEvent(this, toSessionType(mediaType)));
    }

    /**
     * Sets the security status to OFF for this call peer.
     *
     * @param mediaType the <code>MediaType</code> of the call session
     */
    public void securityTurnedOff(MediaType mediaType) {
        // If this event has been triggered because of a call end event and the
        // call is already ended we don't need to alert the user for security off.
        if ((call != null) && !call.getCallState().equals(CallState.CALL_ENDED)) {
            fireCallPeerSecurityOffEvent(new CallPeerSecurityOffEvent(this, toSessionType(mediaType)));
        }
    }

    /**
     * Sets the security status to ON for this call peer.
     *
     * @param mediaType the <code>MediaType</code> of the call session
     * @param cipher the cipher
     * @param sender the security controller that caused the event
     */
    public void securityTurnedOn(MediaType mediaType, String cipher, SrtpControl sender) {
        getMediaHandler().startSrtpMultistream(sender);
        fireCallPeerSecurityOnEvent(new CallPeerSecurityOnEvent(this, toSessionType(mediaType), cipher, sender));
    }

    /**
     * Sets the call containing this peer.
     *
     * @param call the call that this call peer is participating in.
     */
    public void setCall(T call) {
        this.call = call;
    }

    /**
     * Sets the byte array containing an image representation (photo or picture) of the call peer.
     *
     * @param image a byte array containing the image
     */
    public void setImage(byte[] image) {
        byte[] oldImage = getImage();
        this.image = image;

        // Fire the Event
        fireCallPeerChangeEvent(CallPeerChangeEvent.CALL_PEER_IMAGE_CHANGE, oldImage, image);
    }

    /**
     * Modifies the local media setup to reflect the requested setting for the streaming of the
     * local video and then re-invites the peer represented by this class using a corresponding SDP description..
     *
     * @param allowed <code>true</code> if local video transmission is allowed and <code>false</code> otherwise.
     */
    public void setLocalVideoAllowed(boolean allowed) {
        CallPeerMediaHandler<?> mediaHandler = getMediaHandler();

        if (mediaHandler.isLocalVideoTransmissionEnabled() != allowed) {
            // Modify the local media setup to reflect the requested setting for
            // the streaming of the local video.
            mediaHandler.setLocalVideoTransmissionEnabled(allowed);
        }
    }

    /**
     * Sets a reference to the <code>CallPeerMediaHandler</code> used by this peer. The media handler
     * class handles all media management for a single <code>CallPeer</code>. This includes initializing
     * and configuring streams, generating SDP, handling ICE, etc. One instance of <code>CallPeer</code>
     * always corresponds to exactly one instance of <code>CallPeerMediaHandler</code> and both classes
     * are only separated for reasons of readability.
     *
     * @param mediaHandler a reference to the <code>CallPeerMediaHandler</code> instance that this peer uses for
     * media related tips and tricks.
     */
    protected void setMediaHandler(U mediaHandler) {
        this.mediaHandler = mediaHandler;
    }

    /**
     * Sets the mute property for this call peer.
     *
     * @param newMuteValue the new value of the mute property for this call peer
     */
    @Override
    public void setMute(boolean newMuteValue) {
        getMediaHandler().setMute(newMuteValue);
        super.setMute(newMuteValue);
    }

    /**
     * Sets the String that serves as a unique identifier of this CallPeer.
     *
     * @param peerID the ID of this call peer.
     */
    public void setPeerID(String peerID) {
        this.peerID = peerID;
    }

    /**
     * Overrides the parent set state method in order to make sure that we close our media handler
     * whenever we enter a disconnected state.
     *
     * @param newState the <code>CallPeerState</code> that we are about to enter and that we pass to our predecessor.
     * @param reason a reason phrase explaining the state (e.g. if newState indicates a failure) and that
     * we pass to our predecessor.
     * @param reasonCode the code for the reason of the state change.
     */
    @Override
    public void setState(CallPeerState newState, String reason, int reasonCode) {
        // synchronized to mediaHandler if there are currently jobs of
        // initializing, configuring and starting streams (method processSessionAcceptContent
        // of CallPeerMediaHandler) we won't set and fire the current state
        // to Disconnected. Before closing the mediaHandler is setting the state
        // in order to deliver states as quick as possible.
        CallPeerMediaHandler<?> mediaHandler = getMediaHandler();

        synchronized (mediaHandler) {
            try {
                super.setState(newState, reason, reasonCode);
            }
            finally {
                // make sure whatever happens to close the media
                if (CallPeerState.DISCONNECTED.equals(newState)
                        || CallPeerState.FAILED.equals(newState))
                    mediaHandler.close();
            }
        }
    }

    /**
     * Returns the last <code>ConferenceInfoDocument</code> sent by us to this <code>CallPeer</code>. It is
     * a document with state <code>full</code>
     *
     * @return the last <code>ConferenceInfoDocument</code> sent by us to this <code>CallPeer</code>. It is
     * a document with state <code>full</code>
     */
    public ConferenceInfoDocument getLastConferenceInfoSent() {
        return lastConferenceInfoSent;
    }

    /**
     * Sets the last <code>ConferenceInfoDocument</code> sent by us to this <code>CallPeer</code>.
     *
     * @param confInfo the document to set.
     */
    public void setLastConferenceInfoSent(ConferenceInfoDocument confInfo) {
        lastConferenceInfoSent = confInfo;
    }

    /**
     * Gets the time (as obtained by <code>System.currentTimeMillis()</code>) at which we last sent a
     * <code>ConferenceInfoDocument</code> to this <code>CallPeer</code>.
     *
     * @return the time (as obtained by <code>System.currentTimeMillis()</code>) at which we last sent a
     * <code>ConferenceInfoDocument</code> to this <code>CallPeer</code>.
     */
    public long getLastConferenceInfoSentTimestamp() {
        return lastConferenceInfoSentTimestamp;
    }

    /**
     * Sets the time (as obtained by <code>System.currentTimeMillis()</code>) at which we last sent a
     * <code>ConferenceInfoDocument</code> to this <code>CallPeer</code>.
     *
     * @param newTimestamp the time to set
     */
    public void setLastConferenceInfoSentTimestamp(long newTimestamp) {
        lastConferenceInfoSentTimestamp = newTimestamp;
    }

    /**
     * Gets the last <code>ConferenceInfoDocument</code> sent to us by this <code>CallPeer</code>.
     *
     * @return the last <code>ConferenceInfoDocument</code> sent to us by this <code>CallPeer</code>.
     */
    public ConferenceInfoDocument getLastConferenceInfoReceived() {
        return lastConferenceInfoReceived;
    }

    /**
     * Gets the last <code>ConferenceInfoDocument</code> sent to us by this <code>CallPeer</code>.
     */
    public void setLastConferenceInfoReceived(ConferenceInfoDocument confInfo) {
        lastConferenceInfoReceived = confInfo;
    }

    /**
     * Gets the <code>version</code> of the last <code>ConferenceInfoDocument</code> sent to us by this <code>CallPeer</code>,
     * or -1 if we haven't (yet) received a <code>ConferenceInformationDocument</code> from this <code>CallPeer</code>.
     *
     * @return the last <code>ConferenceInfoDocument</code> sent to us by this <code>CallPeer</code>.
     */
    public int getLastConferenceInfoReceivedVersion() {
        return (lastConferenceInfoReceived == null) ? -1 : lastConferenceInfoReceived.getVersion();
    }

    /**
     * Gets the <code>String</code> to be used for this <code>CallPeer</code> when we describe it in a
     * <code>ConferenceInfoDocument</code> (e.g. the <code>entity</code> key attribute which to use for the
     * <code>user</code> element corresponding to this <code>CallPeer</code>)
     *
     * @return the <code>String</code> to be used for this <code>CallPeer</code> when we describe it in a
     * <code>ConferenceInfoDocument</code> (e.g. the <code>entity</code> key attribute which to use
     * for the <code>user</code> element corresponding to this <code>CallPeer</code>)
     */
    public abstract String getEntity();

    /**
     * Check whether a conference-info document is scheduled to be sent to this <code>CallPeer</code>
     * (i.e. there is a thread which will eventually (after sleeping a certain amount of time)
     * trigger a document to be sent)
     *
     * @return <code>true</code> if there is a conference-info document scheduled to be sent to this
     * <code>CallPeer</code> and <code>false</code> otherwise.
     */
    public boolean isConfInfoScheduled() {
        synchronized (confInfoScheduledSyncRoot) {
            return confInfoScheduled;
        }
    }

    /**
     * Sets the property which indicates whether a conference-info document is scheduled to be sent
     * to this <code>CallPeer</code>.
     *
     * @param confInfoScheduled
     */
    public void setConfInfoScheduled(boolean confInfoScheduled) {
        synchronized (confInfoScheduledSyncRoot) {
            this.confInfoScheduled = confInfoScheduled;
        }
    }

    /**
     * Returns the direction of the session for media of type <code>mediaType</code> that we have with
     * this <code>CallPeer</code>. This is the direction of the session negotiated in the signaling
     * protocol, and it may or may not coincide with the direction of the media stream. For example,
     * if we are the focus of a videobridge conference and another peer is sending video to us, we
     * have a <code>RECVONLY</code> video stream, but <code>SENDONLY</code> or <code>SENDRECV</code> (Jingle)
     * sessions with the rest of the conference members. Should always return non-null.
     *
     * @param mediaType the <code>MediaType</code> to use
     *
     * @return Returns the direction of the session for media of type <code>mediaType</code> that we
     * have with this <code>CallPeer</code>.
     */
    public abstract MediaDirection getDirection(MediaType mediaType);

    /**
     * Converts a specific <code>MediaType</code> into a <code>sessionType</code> value in the terms of the
     * <code>CallPeerSecurityStatusEvent</code> class.
     *
     * @param mediaType the <code>MediaType</code> to be converted
     *
     * @return the <code>sessionType</code> value in the terms of the
     * <code>CallPeerSecurityStatusEvent</code> class that is equivalent to the specified <code>mediaType</code>
     */
    private static int toSessionType(MediaType mediaType) {
        switch (mediaType) {
        case AUDIO:
            return CallPeerSecurityStatusEvent.AUDIO_SESSION;
        case VIDEO:
            return CallPeerSecurityStatusEvent.VIDEO_SESSION;
        default:
            throw new IllegalArgumentException("mediaType");
        }
    }
}
