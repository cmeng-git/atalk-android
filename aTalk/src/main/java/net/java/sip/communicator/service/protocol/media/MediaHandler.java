/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.media;

import static org.atalk.impl.neomedia.transform.zrtp.ZrtpControlImpl.generateMyZid;

import net.java.sip.communicator.service.protocol.AccountID;
import net.java.sip.communicator.service.protocol.OperationFailedException;
import net.java.sip.communicator.service.protocol.event.DTMFListener;
import net.java.sip.communicator.service.protocol.event.DTMFReceivedEvent;

import org.atalk.impl.timberlog.TimberLog;
import org.atalk.impl.neomedia.NeomediaServiceUtils;
import org.atalk.service.neomedia.AudioMediaStream;
import org.atalk.service.neomedia.DTMFTone;
import org.atalk.service.neomedia.MediaDirection;
import org.atalk.service.neomedia.MediaService;
import org.atalk.service.neomedia.MediaStream;
import org.atalk.service.neomedia.MediaStreamTarget;
import org.atalk.service.neomedia.RTPExtension;
import org.atalk.service.neomedia.SrtpControl;
import org.atalk.service.neomedia.SrtpControlType;
import org.atalk.service.neomedia.StreamConnector;
import org.atalk.service.neomedia.VideoMediaStream;
import org.atalk.service.neomedia.VolumeControl;
import org.atalk.service.neomedia.control.KeyFrameControl;
import org.atalk.service.neomedia.device.MediaDevice;
import org.atalk.service.neomedia.event.CsrcAudioLevelListener;
import org.atalk.service.neomedia.event.DTMFToneEvent;
import org.atalk.service.neomedia.event.SimpleAudioLevelListener;
import org.atalk.service.neomedia.event.SrtpListener;
import org.atalk.service.neomedia.format.MediaFormat;
import org.atalk.util.MediaType;
import org.atalk.util.event.PropertyChangeNotifier;
import org.atalk.util.event.VideoEvent;
import org.atalk.util.event.VideoListener;
import org.atalk.util.event.VideoNotifierSupport;

import java.awt.Component;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import timber.log.Timber;

/**
 * Implements media control code which allows state sharing among multiple <code>CallPeerMediaHandler</code>s.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 * @author MilanKral
 */
public class MediaHandler extends PropertyChangeNotifier
{
    /**
     * The <code>AudioMediaStream</code> which this instance uses to send and receive audio.
     */
    private AudioMediaStream audioStream;

    /**
     * The <code>CsrcAudioLevelListener</code> that this instance sets on its {@link #audioStream} if
     * {@link #csrcAudioLevelListeners} is not empty.
     */
    private final CsrcAudioLevelListener csrcAudioLevelListener = MediaHandler.this::audioLevelsReceived;

    /**
     * The <code>Object</code> which synchronizes the access to {@link #csrcAudioLevelListener} and
     * {@link #csrcAudioLevelListeners}.
     */
    private final Object csrcAudioLevelListenerLock = new Object();

    /**
     * The list of <code>CsrcAudioLevelListener</code>s to be notified about audio level-related
     * information received from the remote peer(s).
     */
    private List<CsrcAudioLevelListener> csrcAudioLevelListeners = Collections.emptyList();

    /**
     * The <code>KeyFrameControl</code> currently known to this <code>MediaHandler</code> and made
     * available by {@link #mVideoStream}.
     */
    private KeyFrameControl keyFrameControl;

    /**
     * The <code>KeyFrameRequester</code> implemented by this <code>MediaHandler</code> and provided to {@link #keyFrameControl} .
     */
    private final KeyFrameControl.KeyFrameRequester keyFrameRequester = MediaHandler.this::requestKeyFrame;

    private final List<KeyFrameControl.KeyFrameRequester> keyFrameRequesters = new LinkedList<>();

    /**
     * The last-known local SSRCs of the <code>MediaStream</code>s of this instance indexed by <code>MediaType</code> ordinal.
     */
    private final long[] localSSRCs;

    /**
     * The <code>SimpleAudioLeveListener</code> that this instance sets on its {@link #audioStream} if
     * {@link #localUserAudioLevelListeners} is not empty in order to listen to changes in the
     * levels of the audio sent from the local user/peer to the remote peer(s).
     */
    private final SimpleAudioLevelListener localUserAudioLevelListener = new SimpleAudioLevelListener()
    {
        public void audioLevelChanged(int level)
        {
            MediaHandler.this.audioLevelChanged(localUserAudioLevelListenerLock, localUserAudioLevelListeners, level);
        }
    };

    /**
     * The <code>Object</code> which synchronizes the access to {@link #localUserAudioLevelListener}
     * and {@link #localUserAudioLevelListeners}.
     */
    private final Object localUserAudioLevelListenerLock = new Object();

    /**
     * The list of <code>SimpleAudioLevelListener</code>s to be notified about changes in the level of
     * the audio sent from the local peer/user to the remote peer(s).
     */
    private List<SimpleAudioLevelListener> localUserAudioLevelListeners = Collections.emptyList();

    /**
     * The last-known remote SSRCs of the <code>MediaStream</code>s of this instance indexed by <code>MediaType</code> ordinal.
     */
    private final long[] remoteSSRCs;

    /**
     * The <code>SrtpControl</code>s of the <code>MediaStream</code>s of this instance.
     */
    private final SrtpControls srtpControls = new SrtpControls();

    private final SrtpListener srtpListener = new SrtpListener()
    {
        public void securityMessageReceived(String messageType, String i18nMessage, int severity)
        {
            for (SrtpListener listener : getSrtpListeners()) {
                listener.securityMessageReceived(messageType, i18nMessage, severity);
            }
        }

        public void securityNegotiationStarted(MediaType mediaType, SrtpControl sender)
        {
            for (SrtpListener listener : getSrtpListeners())
                listener.securityNegotiationStarted(mediaType, sender);
        }

        public void securityTimeout(MediaType mediaType)
        {
            for (SrtpListener listener : getSrtpListeners())
                listener.securityTimeout(mediaType);
        }

        public void securityTurnedOff(MediaType mediaType)
        {
            for (SrtpListener listener : getSrtpListeners())
                listener.securityTurnedOff(mediaType);
        }

        public void securityTurnedOn(MediaType mediaType, String cipher, SrtpControl sender)
        {
            for (SrtpListener listener : getSrtpListeners())
                listener.securityTurnedOn(mediaType, cipher, sender);
        }
    };

    private final List<SrtpListener> srtpListeners = new LinkedList<>();

    /**
     * The set of listeners in the application (<code>Jitsi</code>) which are to be notified of DTMF events.
     */
    private final Set<DTMFListener> dtmfListeners = new HashSet<>();

    /**
     * The listener registered to receive DTMF events from {@link #audioStream}.
     */
    private final MyDTMFListener dtmfListener = new MyDTMFListener();

    /**
     * The <code>SimpleAudioLeveListener</code> that this instance sets on its {@link #audioStream} if
     * {@link #streamAudioLevelListeners} is not empty in order to listen to changes in the levels
     * of the audio received from the remote peer(s) to the local user/peer.
     */
    private final SimpleAudioLevelListener streamAudioLevelListener = new SimpleAudioLevelListener()
    {
        public void audioLevelChanged(int level)
        {
            MediaHandler.this.audioLevelChanged(streamAudioLevelListenerLock,
                    streamAudioLevelListeners, level);
        }
    };

    /**
     * The <code>Object</code> which synchronizes the access to {@link #streamAudioLevelListener} and
     * {@link #streamAudioLevelListeners}.
     */
    private final Object streamAudioLevelListenerLock = new Object();

    /**
     * The list of <code>SimpleAudioLevelListener</code>s to be notified about changes in the level of
     * the audio sent from remote peer(s) to the local peer/user.
     */
    private List<SimpleAudioLevelListener> streamAudioLevelListeners = Collections.emptyList();

    /**
     * The <code>PropertyChangeListener</code> which listens to changes in the values of the properties
     * of the <code>MediaStream</code>s of this instance.
     */
    private final PropertyChangeListener streamPropertyChangeListener = new PropertyChangeListener()
    {
        /**
         * Notifies this <code>PropertyChangeListener</code> that the value of a specific
         * property of the notifier it is registered with has changed.
         *
         * @param evt a <code>PropertyChangeEvent</code> which describes the source of the event, the name
         * of the property which has changed its value and the old and new values of the property
         * @see PropertyChangeListener#propertyChange(PropertyChangeEvent)
         */
        public void propertyChange(PropertyChangeEvent evt)
        {
            String propertyName = evt.getPropertyName();

            if (MediaStream.PNAME_LOCAL_SSRC.equals(propertyName)) {
                Object source = evt.getSource();

                if (source == audioStream) {
                    setLocalSSRC(MediaType.AUDIO, audioStream.getLocalSourceID());
                }
                else if (source == mVideoStream) {
                    setLocalSSRC(MediaType.VIDEO, mVideoStream.getLocalSourceID());
                }
            }
            else if (MediaStream.PNAME_REMOTE_SSRC.equals(propertyName)) {
                Object source = evt.getSource();

                if (source == audioStream) {
                    setRemoteSSRC(MediaType.AUDIO, audioStream.getRemoteSourceID());
                }
                else if (source == mVideoStream) {
                    setRemoteSSRC(MediaType.VIDEO, mVideoStream.getRemoteSourceID());
                }
            }
        }
    };

    /**
     * The number of references to the <code>MediaStream</code>s of this instance returned by
     * {@link #configureStream(CallPeerMediaHandler, MediaDevice, MediaFormat, MediaStreamTarget,
     * MediaDirection, List, MediaStream, boolean)} to {@link CallPeerMediaHandler}s as new instances.
     */
    private final int[] streamReferenceCounts;

    private final VideoNotifierSupport videoNotifierSupport = new VideoNotifierSupport(this, true);

    /**
     * The <code>VideoMediaStream</code> which this instance uses to send and receive video.
     */
    private VideoMediaStream mVideoStream;

    /**
     * The <code>VideoListener</code> which listens to {@link #mVideoStream} for changes in the
     * availability of visual <code>Component</code>s displaying remote video and re-fires them as
     * originating from this instance.
     */
    private final VideoListener videoStreamVideoListener = new VideoListener()
    {
        public void videoAdded(VideoEvent event)
        {
            VideoEvent clone = event.clone(MediaHandler.this);
            fireVideoEvent(clone);
            if (clone.isConsumed())
                event.consume();
        }

        public void videoRemoved(VideoEvent event)
        {
            // Forwarded in the same way as VIDEO_ADDED.
            videoAdded(event);
        }

        public void videoUpdate(VideoEvent event)
        {
            // Forwarded in the same way as VIDEO_ADDED.
            videoAdded(event);
        }
    };

    public MediaHandler()
    {
        int mediaTypeValueCount = MediaType.values().length;

        localSSRCs = new long[mediaTypeValueCount];
        Arrays.fill(localSSRCs, CallPeerMediaHandler.SSRC_UNKNOWN);
        remoteSSRCs = new long[mediaTypeValueCount];
        Arrays.fill(remoteSSRCs, CallPeerMediaHandler.SSRC_UNKNOWN);

        streamReferenceCounts = new int[mediaTypeValueCount];
    }

    /**
     * Adds a specific <code>CsrcAudioLevelListener</code> to the list of
     * <code>CsrcAudioLevelListener</code>s to be notified about audio level-related information
     * received from the remote peer(s).
     *
     * @param listener the <code>CsrcAudioLevelListener</code> to add to the list of
     * <code>CsrcAudioLevelListener</code>s to be notified about audio level-related information
     * received from the remote peer(s)
     */
    void addCsrcAudioLevelListener(CsrcAudioLevelListener listener)
    {
        if (listener == null)
            throw new NullPointerException("listener");

        synchronized (csrcAudioLevelListenerLock) {
            if (!csrcAudioLevelListeners.contains(listener)) {
                csrcAudioLevelListeners
                        = new ArrayList<>(csrcAudioLevelListeners);
                if (csrcAudioLevelListeners.add(listener)
                        && (csrcAudioLevelListeners.size() == 1)) {
                    AudioMediaStream audioStream = this.audioStream;

                    if (audioStream != null) {
                        audioStream.setCsrcAudioLevelListener(csrcAudioLevelListener);
                    }
                }
            }
        }
    }

    boolean addKeyFrameRequester(int index, KeyFrameControl.KeyFrameRequester keyFrameRequester)
    {
        if (keyFrameRequester == null)
            throw new NullPointerException("keyFrameRequester");
        else {
            synchronized (keyFrameRequesters) {
                if (keyFrameRequesters.contains(keyFrameRequester))
                    return false;
                else {
                    keyFrameRequesters.add((index == -1) ? keyFrameRequesters.size() : index,
                            keyFrameRequester);
                    return true;
                }
            }
        }
    }

    /**
     * Adds a specific <code>SimpleAudioLevelListener</code> to the list of
     * <code>SimpleAudioLevelListener</code>s to be notified about changes in the level of the audio
     * sent from the local peer/user to the remote peer(s).
     *
     * @param listener the <code>SimpleAudioLevelListener</code> to add to the list of
     * <code>SimpleAudioLevelListener</code>s to be notified about changes in the level of the
     * audio sent from the local peer/user to the remote peer(s)
     */
    void addLocalUserAudioLevelListener(SimpleAudioLevelListener listener)
    {
        if (listener == null)
            throw new NullPointerException("listener");

        synchronized (localUserAudioLevelListenerLock) {
            if (!localUserAudioLevelListeners.contains(listener)) {
                localUserAudioLevelListeners = new ArrayList<>(
                        localUserAudioLevelListeners);
                if (localUserAudioLevelListeners.add(listener)
                        && (localUserAudioLevelListeners.size() == 1)) {
                    AudioMediaStream audioStream = this.audioStream;

                    if (audioStream != null) {
                        audioStream.setLocalUserAudioLevelListener(localUserAudioLevelListener);
                    }
                }
            }
        }
    }

    void addSrtpListener(SrtpListener listener)
    {
        if (listener == null)
            throw new NullPointerException("listener");
        else {
            synchronized (srtpListeners) {
                if (!srtpListeners.contains(listener))
                    srtpListeners.add(listener);
            }
        }
    }

    /**
     * Adds a <code>DTMFListener</code> which will be notified when DTMF events are received from the
     * <code>MediaHandler</code> 's audio stream.
     *
     * @param listener the listener to add.
     */
    void addDtmfListener(DTMFListener listener)
    {
        if (listener != null)
            dtmfListeners.add(listener);
    }

    /**
     * Removes a <code>DTMFListener</code> from the set of listeners to be notified for DTMF events
     * from this <code>MediaHandler</code>'s audio steam.
     *
     * @param listener the listener to remove.
     */
    void removeDtmfListener(DTMFListener listener)
    {
        dtmfListeners.remove(listener);
    }

    /**
     * Adds a specific <code>SimpleAudioLevelListener</code> to the list of
     * <code>SimpleAudioLevelListener</code>s to be notified about changes in the level of the audio
     * sent from remote peer(s) to the local peer/user.
     *
     * @param listener the <code>SimpleAudioLevelListener</code> to add to the list of
     * <code>SimpleAudioLevelListener</code>s to be notified about changes in the level of the
     * audio sent from the remote peer(s) to the local peer/user
     */
    void addStreamAudioLevelListener(SimpleAudioLevelListener listener)
    {
        if (listener == null)
            throw new NullPointerException("listener");

        synchronized (streamAudioLevelListenerLock) {
            if (!streamAudioLevelListeners.contains(listener)) {
                streamAudioLevelListeners = new ArrayList<>(streamAudioLevelListeners);
                if (streamAudioLevelListeners.add(listener)
                        && (streamAudioLevelListeners.size() == 1)) {
                    AudioMediaStream audioStream = this.audioStream;

                    if (audioStream != null) {
                        audioStream.setStreamAudioLevelListener(streamAudioLevelListener);
                    }
                }
            }
        }
    }

    /**
     * Registers a specific <code>VideoListener</code> with this instance so that it starts receiving
     * notifications from it about changes in the availability of visual <code>Component</code>s
     * displaying video.
     *
     * @param listener the <code>VideoListener</code> to be registered with this instance and to start receiving
     * notifications from it about changes in the availability of visual <code>Component</code>s
     * displaying video
     */
    void addVideoListener(VideoListener listener)
    {
        videoNotifierSupport.addVideoListener(listener);
    }

    /**
     * Notifies this instance that a <code>SimpleAudioLevelListener</code> has been invoked. Forwards
     * the notification to a specific list of <code>SimpleAudioLevelListener</code>s.
     *
     * @param lock the <code>Object</code> which is to be used to synchronize the access to
     * <code>listeners</code>.
     * @param listeners the list of <code>SimpleAudioLevelListener</code>s to forward the notification to
     * @param level the value of the audio level to notify <code>listeners</code> about
     */
    private void audioLevelChanged(Object lock, List<SimpleAudioLevelListener> listeners,
            int level)
    {
        List<SimpleAudioLevelListener> ls;

        synchronized (lock) {
            if (listeners.isEmpty())
                return;
            else
                ls = listeners;
        }
        for (int i = 0, count = ls.size(); i < count; i++) {
            ls.get(i).audioLevelChanged(level);
        }
    }

    /**
     * Notifies this instance that audio level-related information has been received from the
     * remote peer(s). The method forwards the notification to {@link #csrcAudioLevelListeners}.
     *
     * @param audioLevels the audio level-related information received from the remote peer(s)
     */
    private void audioLevelsReceived(long[] audioLevels)
    {
        List<CsrcAudioLevelListener> listeners;

        synchronized (csrcAudioLevelListenerLock) {
            if (csrcAudioLevelListeners.isEmpty())
                return;
            else
                listeners = csrcAudioLevelListeners;
        }
        for (int i = 0, count = listeners.size(); i < count; i++) {
            listeners.get(i).audioLevelsReceived(audioLevels);
        }
    }

    /**
     * Closes the <code>MediaStream</code> that this instance uses for a specific <code>MediaType</code>
     * and prepares it for garbage collection.
     *
     * @param mediaType the <code>MediaType</code> that we'd like to stop a stream for.
     */
    protected void closeStream(CallPeerMediaHandler<?> callPeerMediaHandler, MediaType mediaType)
    {
        int index = mediaType.ordinal();
        int streamReferenceCount = streamReferenceCounts[index];

        /*
         * The streamReferenceCounts should not fall into an invalid state but anyway...
         */
        if (streamReferenceCount <= 0)
            return;

        streamReferenceCount--;
        streamReferenceCounts[index] = streamReferenceCount;

        /*
         * The MediaStream of the specified mediaType is still referenced by other
         * CallPeerMediaHandlers so it is not to be closed yet.
         */
        if (streamReferenceCount > 0)
            return;

        switch (mediaType) {
            case AUDIO:
                setAudioStream(null);
                break;
            case VIDEO:
                setVideoStream(null);
                break;
        }

        // Clean up the SRTP controls used for the associated Call.
        callPeerMediaHandler.removeAndCleanupOtherSrtpControls(mediaType, null);
    }

    /**
     * Configures <code>stream</code> to use the specified <code>device</code>, <code>format</code>,
     * <code>target</code>, <code>direction</code>, etc.
     *
     * @param device the <code>MediaDevice</code> to be used by <code>stream</code> for capture and playback
     * @param format the <code>MediaFormat</code> that we'd like the new stream to transmit in.
     * @param target the <code>MediaStreamTarget</code> containing the RTP and RTCP address:port couples that
     * the new stream would be sending packets to.
     * @param direction the <code>MediaDirection</code> that we'd like the new stream to use (i.e. sendonly,
     * sendrecv, recvonly, or inactive).
     * @param rtpExtensions the list of <code>RTPExtension</code>s that should be enabled for this stream.
     * @param stream the <code>MediaStream</code> that we'd like to configure.
     * @param masterStream whether the stream to be used as master if secured
     * @return the <code>MediaStream</code> that we received as a parameter (for convenience reasons).
     * @throws OperationFailedException if setting the <code>MediaFormat</code> or connecting to the specified
     * <code>MediaDevice</code> fails for some reason.
     */
    protected MediaStream configureStream(CallPeerMediaHandler<?> callPeerMediaHandler,
            MediaDevice device, MediaFormat format, MediaStreamTarget target, MediaDirection direction,
            List<RTPExtension> rtpExtensions, MediaStream stream, boolean masterStream)
            throws OperationFailedException
    {
        registerDynamicPTsWithStream(callPeerMediaHandler, stream);
        registerRTPExtensionsWithStream(callPeerMediaHandler, rtpExtensions, stream);

        stream.setDevice(device);
        stream.setTarget(target);
        stream.setDirection(direction);
        stream.setFormat(format);

        // cmeng: call has NPE during testing. Received content-reject while processing content-add;
        // Call terminated? Just return with original stream if so ??
        // cmeng: 20200518 - just return stream instead of causing NPE
        MediaAwareCall<?, ?, ?> call = callPeerMediaHandler.getPeer().getCall();
        if (call == null)
            return stream;

        MediaType mediaType = (stream instanceof AudioMediaStream)
                ? MediaType.AUDIO : MediaType.VIDEO;
        stream.setRTPTranslator(call.getRTPTranslator(mediaType));

        switch (mediaType) {
            case AUDIO:
                AudioMediaStream audioStream = (AudioMediaStream) stream;

                /*
                 * The volume (level) of the audio played back in calls should be call-specific
                 * i.e. it should be able to change the volume (level) of a call without
                 * affecting any other simultaneous calls.
                 */
                setOutputVolumeControl(audioStream, call);
                setAudioStream(audioStream);
                break;

            case VIDEO:
                setVideoStream((VideoMediaStream) stream);
                break;
        }

        if (call.isDefaultEncrypted()) {
            /*
             * We'll use the audio stream as the master stream when using SRTP multistreams.
             */
            SrtpControl srtpControl = stream.getSrtpControl();

            srtpControl.setMasterSession(masterStream);
            srtpControl.setSrtpListener(srtpListener);
            srtpControl.start(mediaType);
        }

        /*
         * If the specified callPeerMediaHandler is going to see the stream as a new instance,
         * count a new reference to it so that this MediaHandler knows when it really needs to
         * close the stream later on upon calls to #closeStream(CallPeerMediaHandler<?>,
         * MediaType).
         */
        if (stream != callPeerMediaHandler.getStream(mediaType))
            streamReferenceCounts[mediaType.ordinal()]++;

        return stream;
    }

    /**
     * Notifies the <code>VideoListener</code>s registered with this <code>MediaHandler</code> about a
     * specific type of change in the availability of a specific visual <code>Component</code>
     * depicting video.
     *
     * @param type the type of change as defined by <code>VideoEvent</code> in the availability of the
     * specified visual <code>Component</code> depicting video
     * @param visualComponent the visual <code>Component</code> depicting video which has been added or removed in this
     * <code>MediaHandler</code>
     * @param origin {@link VideoEvent#LOCAL} if the origin of the video is local (e.g. it is being locally
     * captured); {@link VideoEvent#REMOTE} if the origin of the video is remote (e.g. a
     * remote peer is streaming it)
     * @return <code>true</code> if this event and, more specifically, the visual <code>Component</code> it
     * describes have been consumed and should be considered owned, referenced (which is
     * important because <code>Component</code>s belong to a single <code>Container</code> at a
     * time); otherwise, <code>false</code>
     */
    protected boolean fireVideoEvent(int type, Component visualComponent, int origin)
    {
        return videoNotifierSupport.fireVideoEvent(type, visualComponent, origin, true);
    }

    /**
     * Notifies the <code>VideoListener</code>s registered with this <code>MediaHandler</code> about a
     * specific <code>VideoEvent</code>.
     *
     * @param event the <code>VideoEvent</code> to fire to the <code>VideoListener</code>s registered with this
     * <code>MediaHandler</code>
     */
    protected void fireVideoEvent(VideoEvent event)
    {
        videoNotifierSupport.fireVideoEvent(event, true);
    }

    /**
     * Gets the SRTP control type used for a given media type.
     *
     * @param mediaType the <code>MediaType</code> to get the SRTP control type for
     * @return the SRTP control type (MIKEY, SDES, ZRTP) used for the given media type or
     * <code>null</code> if SRTP is not enabled for the given media type
     */
    SrtpControl getEncryptionMethod(CallPeerMediaHandler<?> callPeerMediaHandler, MediaType mediaType)
    {
        /*
         * Find the first existing SRTP control type for the specified media type which is active
         * i.e. secures the communication.
         */
        for (SrtpControlType srtpControlType : SrtpControlType.values()) {
            SrtpControl srtpControl = getSrtpControls(callPeerMediaHandler).get(mediaType, srtpControlType);

            if ((srtpControl != null) && srtpControl.getSecureCommunicationStatus()) {
                return srtpControl;
            }
        }
        return null;
    }

    long getRemoteSSRC(CallPeerMediaHandler<?> callPeerMediaHandler, MediaType mediaType)
    {
        return remoteSSRCs[mediaType.ordinal()];
    }

    /**
     * Gets the <code>SrtpControl</code>s of the <code>MediaStream</code>s of this instance.
     *
     * @return the <code>SrtpControl</code>s of the <code>MediaStream</code>s of this instance
     */
    SrtpControls getSrtpControls(CallPeerMediaHandler<?> callPeerMediaHandler)
    {
        return srtpControls;
    }

    private SrtpListener[] getSrtpListeners()
    {
        synchronized (srtpListeners) {
            return srtpListeners.toArray(new SrtpListener[0]);
        }
    }

    /**
     * Gets the <code>MediaStream</code> of this instance which is of a specific <code>MediaType</code>. If
     * this instance doesn't have such a <code>MediaStream</code>, returns <code>null</code>
     *
     * @param mediaType the <code>MediaType</code> of the <code>MediaStream</code> to retrieve
     * @return the <code>MediaStream</code> of this <code>CallPeerMediaHandler</code> which is of the
     * specified <code>mediaType</code> if this instance has such a <code>MediaStream</code>;
     * otherwise, <code>null</code>
     */
    MediaStream getStream(CallPeerMediaHandler<?> callPeerMediaHandler, MediaType mediaType)
    {
        switch (mediaType) {
            case AUDIO:
                return audioStream;
            case VIDEO:
                return mVideoStream;
            default:
                throw new IllegalArgumentException("mediaType");
        }
    }

    /**
     * Creates if necessary, and configures the stream that this <code>MediaHandler</code> is using for
     * the <code>MediaType</code> matching the one of the <code>MediaDevice</code>.
     *
     * @param connector the <code>MediaConnector</code> that we'd like to bind the newly created stream to.
     * @param device the <code>MediaDevice</code> that we'd like to attach the newly created
     * <code>MediaStream</code> to.
     * @param format the <code>MediaFormat</code> that we'd like the new <code>MediaStream</code> to be set to
     * transmit in.
     * @param target the <code>MediaStreamTarget</code> containing the RTP and RTCP address:port couples that
     * the new stream would be sending packets to.
     * @param direction the <code>MediaDirection</code> that we'd like the new stream to use (i.e. sendonly,
     * sendrecv, recvonly, or inactive).
     * @param rtpExtensions the list of <code>RTPExtension</code>s that should be enabled for this stream.
     * @param masterStream whether the stream to be used as master if secured
     * @return the newly created <code>MediaStream</code>.
     * @throws OperationFailedException if creating the stream fails for any reason (like for example accessing
     * the device or setting the format).
     */
    MediaStream initStream(CallPeerMediaHandler<?> callPeerMediaHandler, StreamConnector connector,
            MediaDevice device, MediaFormat format, MediaStreamTarget target, MediaDirection direction,
            List<RTPExtension> rtpExtensions, boolean masterStream)
            throws OperationFailedException
    {
        MediaType mediaType = device.getMediaType();
        MediaStream stream = getStream(callPeerMediaHandler, mediaType);

        if (stream == null) {
            if (mediaType != format.getMediaType())
                Timber.log(TimberLog.FINER, "The media types of device and format differ: " + mediaType.toString());

            MediaService mediaService = ProtocolMediaActivator.getMediaService();
            SrtpControl srtpControl = srtpControls.findFirst(mediaType);

            // If a SrtpControl does not exist yet, create a default one.
            if (srtpControl == null) {

                /*
                 * The default SrtpControl is currently ZRTP without the hello-hash.
                 * It needs to be linked to the srtpControls Map.
                 */

                final AccountID accountID = callPeerMediaHandler.getPeer().getProtocolProvider().getAccountID();
                final byte[] myZid = generateMyZid(accountID, callPeerMediaHandler.getPeer().getPeerJid().asBareJid());
                srtpControl = NeomediaServiceUtils.getMediaServiceImpl().createSrtpControl(SrtpControlType.ZRTP, myZid);

                srtpControls.set(mediaType, srtpControl);
            }

            stream = mediaService.createMediaStream(connector, device, srtpControl);
        }
        else {
            Timber.log(TimberLog.FINER, "Reinitializing stream: " + stream);
        }
        // cmeng: stream can still be null in testing. why?
        return configureStream(callPeerMediaHandler, device, format, target, direction,
                rtpExtensions, stream, masterStream);
    }

    /**
     * Processes a request for a (video) key frame from a remote peer to the local peer.
     *
     * @return <code>true</code> if the request for a (video) key frame has been honored by the local
     * peer; otherwise, <code>false</code>
     */
    boolean processKeyFrameRequest(CallPeerMediaHandler<?> callPeerMediaHandler)
    {
        KeyFrameControl keyFrameControl = this.keyFrameControl;
        return (keyFrameControl != null) && keyFrameControl.keyFrameRequest();
    }

    /**
     * Registers all dynamic payload mappings and any payload type overrides that are known to this
     * <code>MediaHandler</code> with the specified <code>MediaStream</code>.
     *
     * @param stream the <code>MediaStream</code> that we'd like to register our dynamic payload mappings with.
     */
    private void registerDynamicPTsWithStream(CallPeerMediaHandler<?> callPeerMediaHandler, MediaStream stream)
    {
        DynamicPayloadTypeRegistry dynamicPayloadTypes = callPeerMediaHandler.getDynamicPayloadTypes();

        // first register the mappings
        StringBuffer dbgMessage = new StringBuffer("Dynamic PT map: ");
        for (Map.Entry<MediaFormat, Byte> mapEntry : dynamicPayloadTypes.getMappings().entrySet()) {
            byte pt = mapEntry.getValue();
            MediaFormat fmt = mapEntry.getKey();

            dbgMessage.append(pt).append("=").append(fmt).append("; ");
            stream.addDynamicRTPPayloadType(pt, fmt);
        }
        Timber.i("%s", dbgMessage);

        dbgMessage = new StringBuffer("PT overrides [");
        // now register whatever overrides we have for the above mappings
        for (Map.Entry<Byte, Byte> overrideEntry : dynamicPayloadTypes.getMappingOverrides().entrySet()) {
            byte originalPt = overrideEntry.getKey();
            byte overridePt = overrideEntry.getValue();

            dbgMessage.append(originalPt).append("->").append(overridePt).append(" ");
            stream.addDynamicRTPPayloadTypeOverride(originalPt, overridePt);
        }
        dbgMessage.append("]");
        Timber.i("%s", dbgMessage);

    }

    /**
     * Registers with the specified <code>MediaStream</code> all RTP extensions negotiated by this
     * <code>MediaHandler</code>.
     *
     * @param stream the <code>MediaStream</code> that we'd like to register our <code>RTPExtension</code>s with.
     * @param rtpExtensions the list of <code>RTPExtension</code>s that should be enabled for <code>stream</code>.
     */
    private void registerRTPExtensionsWithStream(CallPeerMediaHandler<?> callPeerMediaHandler,
            List<RTPExtension> rtpExtensions, MediaStream stream)
    {
        DynamicRTPExtensionsRegistry rtpExtensionsRegistry = callPeerMediaHandler.getRtpExtensionsRegistry();

        for (RTPExtension rtpExtension : rtpExtensions) {
            byte extensionID = rtpExtensionsRegistry.getExtensionMapping(rtpExtension);
            stream.addRTPExtension(extensionID, rtpExtension);
        }
    }

    /**
     * Removes a specific <code>CsrcAudioLevelListener</code> to the list of
     * <code>CsrcAudioLevelListener</code>s to be notified about audio level-related information
     * received from the remote peer(s).
     *
     * @param listener the <code>CsrcAudioLevelListener</code> to remove from the list of
     * <code>CsrcAudioLevelListener</code>s to be notified about audio level-related information
     * received from the remote peer(s)
     */
    void removeCsrcAudioLevelListener(CsrcAudioLevelListener listener)
    {
        if (listener == null)
            return;

        synchronized (csrcAudioLevelListenerLock) {
            if (csrcAudioLevelListeners.contains(listener)) {
                csrcAudioLevelListeners = new ArrayList<>(csrcAudioLevelListeners);
                if (csrcAudioLevelListeners.remove(listener)
                        && csrcAudioLevelListeners.isEmpty()) {
                    AudioMediaStream audioStream = this.audioStream;

                    if (audioStream != null)
                        audioStream.setCsrcAudioLevelListener(null);
                }
            }
        }
    }

    boolean removeKeyFrameRequester(KeyFrameControl.KeyFrameRequester keyFrameRequester)
    {
        if (keyFrameRequester == null)
            return false;
        else {
            synchronized (keyFrameRequesters) {
                return keyFrameRequesters.remove(keyFrameRequester);
            }
        }
    }

    /**
     * Removes a specific <code>SimpleAudioLevelListener</code> to the list of
     * <code>SimpleAudioLevelListener</code>s to be notified about changes in the level of the audio
     * sent from the local peer/user to the remote peer(s).
     *
     * @param listener the <code>SimpleAudioLevelListener</code> to remove from the list of
     * <code>SimpleAudioLevelListener</code>s to be notified about changes in the level of the
     * audio sent from the local peer/user to the remote peer(s)
     */
    void removeLocalUserAudioLevelListener(SimpleAudioLevelListener listener)
    {
        if (listener == null)
            return;

        synchronized (localUserAudioLevelListenerLock) {
            if (localUserAudioLevelListeners.contains(listener)) {
                localUserAudioLevelListeners = new ArrayList<>(localUserAudioLevelListeners);
                if (localUserAudioLevelListeners.remove(listener)
                        && localUserAudioLevelListeners.isEmpty()) {
                    AudioMediaStream audioStream = this.audioStream;

                    if (audioStream != null)
                        audioStream.setLocalUserAudioLevelListener(null);
                }
            }
        }
    }

    void removeSrtpListener(SrtpListener listener)
    {
        if (listener != null) {
            synchronized (srtpListeners) {
                srtpListeners.remove(listener);
            }
        }
    }

    /**
     * Removes a specific <code>SimpleAudioLevelListener</code> to the list of
     * <code>SimpleAudioLevelListener</code>s to be notified about changes in the level of the audio
     * sent from remote peer(s) to the local peer/user.
     *
     * @param listener the <code>SimpleAudioLevelListener</code> to remote from the list of
     * <code>SimpleAudioLevelListener</code>s to be notified about changes in the level of the
     * audio sent from the remote peer(s) to the local peer/user
     */
    void removeStreamAudioLevelListener(SimpleAudioLevelListener listener)
    {
        if (listener == null)
            return;

        synchronized (streamAudioLevelListenerLock) {
            if (streamAudioLevelListeners.contains(listener)) {
                streamAudioLevelListeners = new ArrayList<>(streamAudioLevelListeners);
                if (streamAudioLevelListeners.remove(listener)
                        && streamAudioLevelListeners.isEmpty()) {
                    AudioMediaStream audioStream = this.audioStream;

                    if (audioStream != null)
                        audioStream.setStreamAudioLevelListener(null);
                }
            }
        }
    }

    /**
     * Unregisters a specific <code>VideoListener</code> from this instance so that it stops receiving
     * notifications from it about changes in the availability of visual <code>Component</code>s displaying video.
     *
     * @param listener the <code>VideoListener</code> to be unregistered from this instance and to stop receiving
     * notifications from it about changes in the availability of visual <code>Component</code>s displaying video
     */
    void removeVideoListener(VideoListener listener)
    {
        videoNotifierSupport.removeVideoListener(listener);
    }

    /**
     * Requests a key frame from the remote peer of the associated <code>VideoMediaStream</code> of this <code>MediaHandler</code>.
     *
     * @return <code>true</code> if this <code>MediaHandler</code> has indeed requested a key frame from then
     * remote peer of its associated <code>VideoMediaStream</code> in response to the call; otherwise, <code>false</code>
     */
    protected boolean requestKeyFrame()
    {
        KeyFrameControl.KeyFrameRequester[] keyFrameRequesters;

        synchronized (this.keyFrameRequesters) {
            keyFrameRequesters = this.keyFrameRequesters.toArray(new KeyFrameControl.KeyFrameRequester[0]);
        }

        for (KeyFrameControl.KeyFrameRequester keyFrameRequester : keyFrameRequesters) {
            if (keyFrameRequester.requestKeyFrame())
                return true;
        }
        return false;
    }

    /**
     * Sets the <code>AudioMediaStream</code> which this instance is to use to send and receive audio.
     *
     * @param audioStream the <code>AudioMediaStream</code> which this instance is to use to send and receive audio
     */
    private void setAudioStream(AudioMediaStream audioStream)
    {
        if (this.audioStream != audioStream) {
            // Timber.w(new Exception("Set Audio Stream"), "set Audio Stream: %s => %s", this.audioStream, audioStream);

            if (this.audioStream != null) {
                synchronized (csrcAudioLevelListenerLock) {
                    if (!csrcAudioLevelListeners.isEmpty())
                        this.audioStream.setCsrcAudioLevelListener(null);
                }
                synchronized (localUserAudioLevelListenerLock) {
                    if (!localUserAudioLevelListeners.isEmpty())
                        this.audioStream.setLocalUserAudioLevelListener(null);
                }
                synchronized (streamAudioLevelListenerLock) {
                    if (!streamAudioLevelListeners.isEmpty())
                        this.audioStream.setStreamAudioLevelListener(null);
                }

                this.audioStream.removePropertyChangeListener(streamPropertyChangeListener);
                this.audioStream.removeDTMFListener(dtmfListener);
                this.audioStream.close();
            }
            this.audioStream = audioStream;
            long audioLocalSSRC;
            long audioRemoteSSRC;

            if (this.audioStream != null) {
                this.audioStream.addPropertyChangeListener(streamPropertyChangeListener);
                audioLocalSSRC = this.audioStream.getLocalSourceID();
                audioRemoteSSRC = this.audioStream.getRemoteSourceID();

                synchronized (csrcAudioLevelListenerLock) {
                    if (!csrcAudioLevelListeners.isEmpty()) {
                        this.audioStream.setCsrcAudioLevelListener(csrcAudioLevelListener);
                    }
                }
                synchronized (localUserAudioLevelListenerLock) {
                    if (!localUserAudioLevelListeners.isEmpty()) {
                        this.audioStream.setLocalUserAudioLevelListener(localUserAudioLevelListener);
                    }
                }
                synchronized (streamAudioLevelListenerLock) {
                    if (!streamAudioLevelListeners.isEmpty()) {
                        this.audioStream.setStreamAudioLevelListener(streamAudioLevelListener);
                    }
                }
                this.audioStream.addDTMFListener(dtmfListener);
            }
            else {
                audioLocalSSRC = audioRemoteSSRC = CallPeerMediaHandler.SSRC_UNKNOWN;
            }
            setLocalSSRC(MediaType.AUDIO, audioLocalSSRC);
            setRemoteSSRC(MediaType.AUDIO, audioRemoteSSRC);
        }
    }

    /**
     * Sets the <code>KeyFrameControl</code> currently known to this <code>MediaHandler</code> made
     * available by a specific <code>VideoMediaStream</code>.
     *
     * @param videoStream the <code>VideoMediaStream</code> the <code>KeyFrameControl</code> of which is to be set as
     * the currently known to this <code>MediaHandler</code>
     */
    private void setKeyFrameControlFromVideoStream(VideoMediaStream videoStream)
    {
        KeyFrameControl keyFrameControl = (videoStream == null)
                ? null : videoStream.getKeyFrameControl();

        if (this.keyFrameControl != keyFrameControl) {
            if (this.keyFrameControl != null)
                this.keyFrameControl.removeKeyFrameRequester(keyFrameRequester);

            this.keyFrameControl = keyFrameControl;
            if (this.keyFrameControl != null)
                this.keyFrameControl.addKeyFrameRequester(-1, keyFrameRequester);
        }
    }

    /**
     * Sets the last-known local SSRC of the <code>MediaStream</code> of a specific <code>MediaType</code>.
     *
     * @param mediaType the <code>MediaType</code> of the <code>MediaStream</code> to set the last-known local SSRC of
     * @param localSSRC the last-known local SSRC of the <code>MediaStream</code> of the specified <code>mediaType</code>
     */
    private void setLocalSSRC(MediaType mediaType, long localSSRC)
    {
        int index = mediaType.ordinal();
        long oldValue = localSSRCs[index];

        if (oldValue != localSSRC) {
            localSSRCs[index] = localSSRC;
            String property;

            switch (mediaType) {
                case AUDIO:
                    property = CallPeerMediaHandler.AUDIO_LOCAL_SSRC;
                    break;
                case VIDEO:
                    property = CallPeerMediaHandler.VIDEO_LOCAL_SSRC;
                    break;
                default:
                    property = null;
            }
            if (property != null)
                firePropertyChange(property, oldValue, localSSRC);
        }
    }

    /**
     * Sets the <code>VolumeControl</code> which is to control the volume (level) of the audio received in/by a
     * specific <code>AudioMediaStream</code> and played back in order to achieve call-specific volume (level).
     * <p>
     * <b>Note</b>: The implementation makes the volume (level) telephony conference-specific.
     * </p>
     *
     * @param audioStream the <code>AudioMediaStream</code> on which a <code>VolumeControl</code> from the specified
     * <code>call</code> is to be set
     * @param call the <code>MediaAwareCall</code> which provides the <code>VolumeControl</code> to be set on the
     * specified <code>audioStream</code>
     */
    private void setOutputVolumeControl(AudioMediaStream audioStream, MediaAwareCall<?, ?, ?> call)
    {
        /*
         * The volume (level) of the audio played back in calls should be call-specific i.e. it
         * should be able to change the volume (level) of a call without affecting any other
         * simultaneous calls. The implementation makes the volume (level) telephony conference-specific.
         */
        MediaAwareCallConference conference = call.getConference();

        if (conference != null) {
            VolumeControl outputVolumeControl = conference.getOutputVolumeControl();

            if (outputVolumeControl != null)
                audioStream.setOutputVolumeControl(outputVolumeControl);
        }
    }

    /**
     * Sets the last-known remote SSRC of the <code>MediaStream</code> of a specific <code>MediaType</code>.
     *
     * @param mediaType the <code>MediaType</code> of the <code>MediaStream</code> to set the last-known remote SSRC
     * @param remoteSSRC the last-known remote SSRC of the <code>MediaStream</code> of the specified <code>mediaType</code>
     */
    private void setRemoteSSRC(MediaType mediaType, long remoteSSRC)
    {
        int index = mediaType.ordinal();
        long oldValue = remoteSSRCs[index];

        if (oldValue != remoteSSRC) {
            remoteSSRCs[index] = remoteSSRC;
            String property;

            switch (mediaType) {
                case AUDIO:
                    property = CallPeerMediaHandler.AUDIO_REMOTE_SSRC;
                    break;
                case VIDEO:
                    property = CallPeerMediaHandler.VIDEO_REMOTE_SSRC;
                    break;
                default:
                    property = null;
            }
            if (property != null)
                firePropertyChange(property, oldValue, remoteSSRC);
        }
    }

    /**
     * Sets the <code>VideoMediaStream</code> which this instance is to use to send and receive video.
     *
     * @param videoStream the <code>VideoMediaStream</code> which this instance is to use to send and receive video
     */
    private void setVideoStream(VideoMediaStream videoStream)
    {
        if (mVideoStream != videoStream) {
            /*
             * Make sure we will no longer notify the registered VideoListeners about changes in
             * the availability of video in the old videoStream.
             */
            List<Component> oldVisualComponents = null;

            if (mVideoStream != null) {
                mVideoStream.removePropertyChangeListener(streamPropertyChangeListener);

                mVideoStream.removeVideoListener(videoStreamVideoListener);
                oldVisualComponents = mVideoStream.getVisualComponents();

                /*
                 * The current videoStream is going away so this CallPeerMediaHandler should no
                 * longer use its KeyFrameControl.
                 */
                setKeyFrameControlFromVideoStream(null);
                mVideoStream.close();
            }
            mVideoStream = videoStream;

            /*
             * The videoStream has just changed so this CallPeerMediaHandler should use its KeyFrameControl.
             */
            setKeyFrameControlFromVideoStream(mVideoStream);

            long videoLocalSSRC;
            long videoRemoteSSRC;
            /*
             * Make sure we will notify the registered VideoListeners about changes in the
             * availability of video in the new videoStream.
             */
            List<Component> newVisualComponents = null;

            if (mVideoStream != null) {
                mVideoStream.addPropertyChangeListener(streamPropertyChangeListener);
                videoLocalSSRC = mVideoStream.getLocalSourceID();
                videoRemoteSSRC = mVideoStream.getRemoteSourceID();

                mVideoStream.addVideoListener(videoStreamVideoListener);
                newVisualComponents = mVideoStream.getVisualComponents();
            }
            else {
                videoLocalSSRC = videoRemoteSSRC = CallPeerMediaHandler.SSRC_UNKNOWN;
            }

            setLocalSSRC(MediaType.VIDEO, videoLocalSSRC);
            setRemoteSSRC(MediaType.VIDEO, videoRemoteSSRC);

            /*
             * Notify the VideoListeners in case there was a change in the availability of the
             * visual Components displaying remote video.
             */
            if ((oldVisualComponents != null) && !oldVisualComponents.isEmpty()) {
                /*
                 * Discard Components which are present in the old and in the new Lists.
                 */
                if (newVisualComponents == null)
                    newVisualComponents = Collections.emptyList();
                for (Component oldVisualComponent : oldVisualComponents) {
                    if (!newVisualComponents.remove(oldVisualComponent)) {
                        fireVideoEvent(VideoEvent.VIDEO_REMOVED, oldVisualComponent,
                                VideoEvent.REMOTE);
                    }
                }
            }
            if ((newVisualComponents != null) && !newVisualComponents.isEmpty()) {
                for (Component newVisualComponent : newVisualComponents) {
                    fireVideoEvent(VideoEvent.VIDEO_ADDED, newVisualComponent, VideoEvent.REMOTE);
                }
            }
        }
    }

    /**
     * Implements a <code>libjitsi</code> <code>DTMFListener</code>, which receives events from an
     * <code>AudioMediaStream</code>, translate them into <code>Jitsi</code> events (
     * <code>DTMFReceivedEvent</code>s) and forward them to any registered listeners.
     */
    private class MyDTMFListener implements org.atalk.service.neomedia.event.DTMFListener
    {
        /**
         * {@inheritDoc}
         */
        @Override
        public void dtmfToneReceptionStarted(DTMFToneEvent dtmfToneEvent)
        {
            fireEvent(new DTMFReceivedEvent(this,
                    DTMFTone.getDTMFTone(dtmfToneEvent.getDtmfTone().getValue()), true));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void dtmfToneReceptionEnded(DTMFToneEvent dtmfToneEvent)
        {
            fireEvent(new DTMFReceivedEvent(this,
                    DTMFTone.getDTMFTone(dtmfToneEvent.getDtmfTone().getValue()), false));
        }

        /**
         * Sends an <code>DTMFReceivedEvent</code> to all listeners.
         *
         * @param event the event to send.
         */
        private void fireEvent(DTMFReceivedEvent event)
        {
            for (DTMFListener listener : dtmfListeners) {
                listener.toneReceived(event);
            }
        }
    }
}
