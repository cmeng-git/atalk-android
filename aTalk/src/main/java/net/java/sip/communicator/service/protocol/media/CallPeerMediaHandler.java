/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.media;

import static org.atalk.impl.neomedia.format.MediaFormatImpl.FORMAT_PARAMETER_ATTR_IMAGEATTR;

import androidx.annotation.NonNull;

import net.java.sip.communicator.service.protocol.CallPeer;
import net.java.sip.communicator.service.protocol.CallPeerState;
import net.java.sip.communicator.service.protocol.OperationFailedException;
import net.java.sip.communicator.service.protocol.OperationSetVideoTelephony;
import net.java.sip.communicator.service.protocol.ProtocolProviderFactory;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.dialogs.DialogActivity;
import org.atalk.impl.neomedia.format.MediaFormatImpl;
import org.atalk.service.neomedia.AudioMediaStream;
import org.atalk.service.neomedia.MediaDirection;
import org.atalk.service.neomedia.MediaStream;
import org.atalk.service.neomedia.MediaStreamTarget;
import org.atalk.service.neomedia.QualityPreset;
import org.atalk.service.neomedia.RTPExtension;
import org.atalk.service.neomedia.RawPacket;
import org.atalk.service.neomedia.SrtpControl;
import org.atalk.service.neomedia.SrtpControlType;
import org.atalk.service.neomedia.StreamConnector;
import org.atalk.service.neomedia.VideoMediaStream;
import org.atalk.service.neomedia.codec.Constants;
import org.atalk.service.neomedia.codec.EncodingConfiguration;
import org.atalk.service.neomedia.control.KeyFrameControl;
import org.atalk.service.neomedia.device.MediaDevice;
import org.atalk.service.neomedia.device.MediaDeviceWrapper;
import org.atalk.service.neomedia.event.CsrcAudioLevelListener;
import org.atalk.service.neomedia.event.SimpleAudioLevelListener;
import org.atalk.service.neomedia.event.SrtpListener;
import org.atalk.service.neomedia.format.MediaFormat;
import org.atalk.util.MediaType;
import org.atalk.util.event.PropertyChangeNotifier;
import org.atalk.util.event.VideoEvent;
import org.atalk.util.event.VideoListener;
import org.atalk.util.event.VideoNotifierSupport;
import org.jxmpp.jid.Jid;

import java.awt.Component;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import timber.log.Timber;

/**
 * A utility class implementing media control code shared between current telephony implementations.
 * This class is only meant for use by protocol implementations and should not be accessed by
 * bundles that are simply using the telephony functionality.
 *
 * @param <T> the peer extension class like for example <code>CallPeerSipImpl</code> or <code>CallPeerJabberImpl</code>
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
public abstract class CallPeerMediaHandler<T extends MediaAwareCallPeer<?, ?, ?>> extends PropertyChangeNotifier
{
    /**
     * The name of the <code>CallPeerMediaHandler</code> property which specifies the local SSRC of its
     * audio <code>MediaStream</code>.
     */
    public static final String AUDIO_LOCAL_SSRC = "AUDIO_LOCAL_SSRC";

    /**
     * The name of the <code>CallPeerMediaHandler</code> property which specifies the remote SSRC of
     * its audio <code>MediaStream</code>.
     */
    public static final String AUDIO_REMOTE_SSRC = "AUDIO_REMOTE_SSRC";

    /**
     * The constant which signals that a SSRC value is unknown.
     */
    public static final long SSRC_UNKNOWN = -1;

    /**
     * The name of the <code>CallPeerMediaHandler</code> property which specifies the local SSRC of its
     * video <code>MediaStream</code>.
     */
    public static final String VIDEO_LOCAL_SSRC = "VIDEO_LOCAL_SSRC";

    /**
     * The name of the <code>CallPeerMediaHandler</code> property which specifies the remote SSRC of
     * its video <code>MediaStream</code>.
     */
    public static final String VIDEO_REMOTE_SSRC = "VIDEO_REMOTE_SSRC";

    /**
     * The initial content of a hole punch packet. It has some fields pre-set.
     * Like rtp version, sequence number and timestamp.
     */
    private static final byte[] HOLE_PUNCH_PACKET = {
            (byte) 0x80, 0x00, 0x02, (byte) 0x9E, 0x00, 0x09,
            (byte) 0xD0, (byte) 0x80, 0x00, 0x00, 0x00, (byte) 0x00,
    };

    /**
     * Whether hole punching is disabled, by default it is enabled.
     */
    private boolean disableHolePunching = false;

    /**
     * Map of callPeer FullJid and its rtcp-mux capable
     * True to denote the callPeer supports rtcp-mux operation.
     */
    protected static final Map<Jid, Boolean> rtcpMuxes = new HashMap<>();

    /**
     * Map of callPeer FullJid and its imgattr support (non xep standard defined by jitsi)
     * True to include parameter imgattr in media format; conversation will reject value with whitespace.
     */
    protected static final Map<Jid, Boolean> imageAttrs = new HashMap<>();

    /**
     * List of advertised encryption methods. Indicated before establishing the call.
     */
    private final List<SrtpControlType> advertisedEncryptionMethods = new ArrayList<>();

    /**
     * Determines whether or not streaming local audio is currently enabled.
     */
    private MediaDirection audioDirectionUserPreference = MediaDirection.SENDRECV;

    /**
     * The <code>AudioMediaStream</code> which this instance uses to send and receive audio.
     */
    private AudioMediaStream audioStream;

    /**
     * The <code>PropertyChangeListener</code> which listens to changes in the values of the properties
     * of the <code>Call</code> of {@link #mPeer}.
     */
    private final CallPropertyChangeListener callPropertyChangeListener;

    /**
     * The listener that our <code>CallPeer</code> registers for CSRC audio level events.
     */
    private CsrcAudioLevelListener csrcAudioLevelListener;

    /**
     * The object that we are using to sync operations on <code>csrcAudioLevelListener</code>.
     */
    private final Object csrcAudioLevelListenerLock = new Object();

    /**
     * Contains all dynamic payload type mappings that have been made for this call.
     */
    private final DynamicPayloadTypeRegistry dynamicPayloadTypes = new DynamicPayloadTypeRegistry();

    /**
     * The <code>KeyFrameRequester</code> implemented by this <code>CallPeerMediaHandler</code>.
     */
    private final KeyFrameControl.KeyFrameRequester keyFrameRequester
            = CallPeerMediaHandler.this::requestKeyFrame;

    /**
     * Determines whether we have placed the call on hold locally.
     */
    protected boolean locallyOnHold = false;

    /**
     * The listener that the <code>CallPeer</code> registered for local user audio level events.
     */
    private SimpleAudioLevelListener localUserAudioLevelListener;

    /**
     * The object that we are using to sync operations on <code>localAudioLevelListener</code>.
     */
    private final Object localUserAudioLevelListenerLock = new Object();

    /**
     * The state of this instance which may be shared with multiple other <code>CallPeerMediaHandler</code>s.
     */
    private MediaHandler mediaHandler;

    /**
     * The <code>PropertyChangeListener</code> which listens to changes in the values of the properties
     * of the <code>MediaStream</code>s of this instance. Since <code>CallPeerMediaHandler</code> wraps
     * around/shares a <code>MediaHandler</code>, <code>mediaHandlerPropertyChangeListener</code> actually
     * listens to <code>PropertyChangeEvent</code>s fired by the <code>MediaHandler</code> in question and
     * forwards them as its own.
     */
    private final PropertyChangeListener mediaHandlerPropertyChangeListener = new PropertyChangeListener()
    {
        /**
         * Notifies this <code>PropertyChangeListener</code> that the value of a specific property of
         * the notifier it is registered with has changed.
         *
         * @param ev
         *        a <code>PropertyChangeEvent</code> which describes the source of the event, the name
         *        of the property which has changed its value and the old and new values of the property
         * @see PropertyChangeListener#propertyChange(PropertyChangeEvent)
         */
        public void propertyChange(PropertyChangeEvent ev)
        {
            mediaHandlerPropertyChange(ev);
        }
    };

    /**
     * A reference to the CallPeer instance that this handler is managing media streams for.
     */
    protected final T mPeer;

    /**
     * Contains all RTP extension mappings (those made through the extmap attribute) that have been
     * bound during this call.
     */
    private final DynamicRTPExtensionsRegistry rtpExtensionsRegistry = new DynamicRTPExtensionsRegistry();

    /**
     * The <code>SrtpListener</code> which is responsible for the SRTP control. Most often than not,
     * it is the <code>peer</code> itself.
     */
    private final SrtpListener srtpListener;

    /**
     * The listener that our <code>CallPeer</code> registered for stream audio level events.
     */
    private SimpleAudioLevelListener streamAudioLevelListener;

    /**
     * The object that we are using to sync operations on <code>streamAudioLevelListener</code>.
     */
    private final Object streamAudioLevelListenerLock = new Object();

    /**
     * Determines whether streaming local video is currently enabled. Default is RECVONLY.
     * We tried to have INACTIVE at one point but it was breaking incoming reINVITEs for video calls.
     */
    private MediaDirection videoDirectionUserPreference = MediaDirection.RECVONLY;

    /**
     * The aid which implements the boilerplate related to adding and removing
     * <code>VideoListener</code>s and firing <code>VideoEvent</code>s to them on behalf of this instance.
     */
    private final VideoNotifierSupport videoNotifierSupport = new VideoNotifierSupport(this, true);

    /**
     * The <code>VideoMediaStream</code> which this instance uses to send and receive video.
     */
    private VideoMediaStream videoStream;

    /**
     * Identifier used to group the audio stream and video stream towards the <code>CallPeer</code> in SDP.
     */
    private final String msLabel = UUID.randomUUID().toString();

    /**
     * The <code>VideoListener</code> which listens to the video <code>MediaStream</code> of this instance
     * for changes in the availability of visual <code>Component</code>s displaying remote video and
     * re-fires them as originating from this instance.
     */
    private final VideoListener videoStreamVideoListener = new VideoListener()
    {
        /**
         * Notifies this <code>VideoListener</code> about a specific <code>VideoEvent</code>. Fires a new
         * <code>VideoEvent</code> which has this <code>CallPeerMediaHandler</code> as its source and
         * carries the same information as the specified <code>event</code> i.e. translates the specified
         * <code>event</code> into a <code>VideoEvent</code> fired by this <code>CallPeerMediaHandler</code>.
         *
         * @param event the <code>VideoEvent</code> to notify this <code>VideoListener</code> about
         */
        private void onVideoEvent(VideoEvent event)
        {
            VideoEvent clone = event.clone(CallPeerMediaHandler.this);
            fireVideoEvent(clone);
            if (clone.isConsumed())
                event.consume();
        }

        public void videoAdded(@NonNull VideoEvent event)
        {
            onVideoEvent(event);
        }

        public void videoRemoved(@NonNull VideoEvent event)
        {
            onVideoEvent(event);
        }

        public void videoUpdate(@NonNull VideoEvent event)
        {
            onVideoEvent(event);
        }
    };

    /**
     * Creates a new handler that will be managing media streams for <code>peer</code>.
     *
     * @param peer the <code>CallPeer</code> instance that we will be managing media for.
     * @param srtpListener the object that receives SRTP security events.
     */
    public CallPeerMediaHandler(T peer, SrtpListener srtpListener)
    {
        mPeer = peer;
        this.srtpListener = srtpListener;
        setMediaHandler(new MediaHandler());

        /*
         * Listen to the call of peer in order to track the user's choice with respect to the default audio device.
         */
        MediaAwareCall<?, ?, ?> call = mPeer.getCall();
        if (call == null)
            callPropertyChangeListener = null;
        else {
            callPropertyChangeListener = new CallPropertyChangeListener(call);
            call.addPropertyChangeListener(callPropertyChangeListener);
        }
    }

    /**
     * Adds encryption method to the list of advertised secure methods.
     *
     * @param encryptionMethod the method to add.
     */
    public void addAdvertisedEncryptionMethod(SrtpControlType encryptionMethod)
    {
        if (!advertisedEncryptionMethods.contains(encryptionMethod))
            advertisedEncryptionMethods.add(encryptionMethod);
    }

    /**
     * Registers a specific <code>VideoListener</code> with this instance so that it starts receiving
     * notifications from it about changes in the availability of visual <code>Component</code>s displaying video.
     *
     * @param listener the <code>VideoListener</code> to be registered with this instance and to start receiving
     * notifications from it about changes in the availability of visual <code>Component</code>s displaying video
     */
    public void addVideoListener(VideoListener listener)
    {
        videoNotifierSupport.addVideoListener(listener);
    }

    /**
     * Notifies this instance that a value of a specific property of the <code>Call</code> of
     * {@link #mPeer} has changed from a specific old value to a specific new value.
     *
     * @param ev a <code>PropertyChangeEvent</code> which specified the property which had its value
     * changed and the old and new values of that property
     */
    private void callPropertyChange(PropertyChangeEvent ev)
    {
        String propertyName = ev.getPropertyName();
        boolean callConferenceChange = MediaAwareCall.CONFERENCE.equals(propertyName);

        if (callConferenceChange || MediaAwareCall.DEFAULT_DEVICE.equals(propertyName)) {
            MediaAwareCall<?, ?, ?> call = mPeer.getCall();
            if (call == null)
                return;

            for (MediaType mediaType : new MediaType[]{MediaType.AUDIO, MediaType.VIDEO}) {
                MediaStream stream = getStream(mediaType);
                if (stream == null)
                    continue;

                // Update the stream device, if necessary.
                MediaDevice oldDevice = stream.getDevice();
                if (oldDevice != null) {
                    /*
                     * DEFAULT_DEVICE signals that the actual/hardware device has been changed and
                     * we will make sure that is the case in order to avoid unnecessary changes.
                     * CONFERENCE signals that the associated Call has been moved to a new
                     * telephony conference and we have to move its MediaStreams to the respective mixers.
                     */
                    MediaDevice oldValue = (!callConferenceChange && (oldDevice instanceof MediaDeviceWrapper))
                            ? ((MediaDeviceWrapper) oldDevice).getWrappedDevice() : oldDevice;
                    MediaDevice newDevice = getDefaultDevice(mediaType);
                    MediaDevice newValue = (!callConferenceChange && (newDevice instanceof MediaDeviceWrapper))
                            ? ((MediaDeviceWrapper) newDevice).getWrappedDevice() : newDevice;

                    if (oldValue != newValue)
                        stream.setDevice(newDevice);
                }
                stream.setRTPTranslator(call.getRTPTranslator(mediaType));
            }
        }
    }

    /**
     * Closes and nullifies all streams and connectors and readies this media handler for garbage
     * collection (or reuse). Synchronized if any other stream operations are in process we won't interrupt them.
     */
    public synchronized void close()
    {
        closeStream(MediaType.AUDIO);
        closeStream(MediaType.VIDEO);

        locallyOnHold = false;
        if (callPropertyChangeListener != null)
            callPropertyChangeListener.removePropertyChangeListener();

        setMediaHandler(null);
    }

    /**
     * Closes the <code>MediaStream</code> that this instance uses for a specific <code>MediaType</code>
     * and prepares it for garbage collection.
     *
     * @param mediaType the <code>MediaType</code> that we'd like to stop a stream for.
     */
    protected void closeStream(MediaType mediaType)
    {
        Timber.d("Closing %s stream for %s", mediaType, mPeer);
        /*
         * This CallPeerMediaHandler releases its reference to the MediaStream it has initialized via #initStream().
         */
        boolean mediaHandlerCloseStream = false;
        switch (mediaType) {
            case AUDIO:
                if (audioStream != null) {
                    audioStream = null;
                    mediaHandlerCloseStream = true;
                }
                break;
            case VIDEO:
                if (videoStream != null) {
                    videoStream = null;
                    mediaHandlerCloseStream = true;
                }
                break;
        }
        if (mediaHandlerCloseStream)
            mediaHandler.closeStream(this, mediaType);

        TransportManager<?> transportManager = queryTransportManager();
        if (transportManager != null)
            transportManager.closeStreamConnector(mediaType);
    }

    /**
     * Returns the first <code>RTPExtension</code> in <code>extList</code> that uses the specified
     * <code>extensionURN</code> or <code>null</code> if <code>extList</code> did not contain such an extension.
     *
     * @param extList the <code>List</code> that we will be looking through.
     * @param extensionURN the URN of the <code>RTPExtension</code> that we are looking for.
     * @return the first <code>RTPExtension</code> in <code>extList</code> that uses the specified
     * <code>extensionURN</code> or <code>null</code> if <code>extList</code> did not contain such an extension.
     */
    private RTPExtension findExtension(List<RTPExtension> extList, String extensionURN)
    {
        for (RTPExtension rtpExt : extList) {
            if (rtpExt.getURI().toASCIIString().equals(extensionURN))
                return rtpExt;
        }
        return null;
    }

    /**
     * Finds a <code>MediaFormat</code> in a specific list of <code>MediaFormat</code>s which matches
     * a specific <code>MediaFormat</code>.
     *
     * @param formats the list of <code>MediaFormat</code>s to find the specified matching <code>MediaFormat</code> into
     * @param format encoding of the <code>MediaFormat</code> to find
     * @return the <code>MediaFormat</code> from <code>formats</code> which matches <code>format</code> if such
     * a match exists in <code>formats</code>; otherwise, <code>null</code>
     */
    protected MediaFormat findMediaFormat(List<MediaFormat> formats, MediaFormat format)
    {
        for (MediaFormat mFormat : formats) {
            if (mFormat.matches(format)) {
                return mFormat;
            }
        }
        return null;
    }

    /**
     * Notifies the <code>VideoListener</code>s registered with this <code>CallPeerMediaHandler</code>
     * about a specific type of change in the availability of a specific visual <code>Component</code> depicting video.
     *
     * @param type the type of change as defined by <code>VideoEvent</code> in the availability of the
     * specified visual <code>Component</code> depicting video
     * @param visualComponent the visual <code>Component</code> depicting video which has been added or removed in this
     * <code>CallPeerMediaHandler</code>
     * @param origin {@link VideoEvent#LOCAL} if the origin of the video is local (e.g. it is being locally captured);
     * {@link VideoEvent#REMOTE} if the origin of the video is remote (e.g. a remote peer is streaming it)
     * @return <code>true</code> if this event and, more specifically, the visual <code>Component</code> it
     * describes have been consumed and should be considered owned, referenced (which is important because
     * <code>Component</code>s belong to a single <code>Container</code> at a time); otherwise, <code>false</code>
     */
    protected boolean fireVideoEvent(int type, Component visualComponent, int origin)
    {
        return videoNotifierSupport.fireVideoEvent(type, visualComponent, origin, true);
    }

    /**
     * Notifies the <code>VideoListener</code>s registered with this <code>CallPeerMediaHandler</code>
     * about a specific <code>VideoEvent</code>.
     *
     * @param event the <code>VideoEvent</code> to fire to the <code>VideoListener</code>s registered with this
     * <code>CallPeerMediaHandler</code>
     */
    public void fireVideoEvent(VideoEvent event)
    {
        videoNotifierSupport.fireVideoEvent(event, true);
    }

    /**
     * Returns the advertised methods for securing the call, this are the methods like SDES, ZRTP
     * that are indicated in the initial session initialization. Missing here doesn't mean the
     * other party don't support it.
     *
     * @return the advertised encryption methods.
     */
    public SrtpControlType[] getAdvertisedEncryptionMethods()
    {
        return advertisedEncryptionMethods.toArray(new SrtpControlType[0]);
    }

    /**
     * Gets a <code>MediaDevice</code> which is capable of capture and/or playback media of the
     * specified <code>MediaType</code>, is the default choice of the user for a <code>MediaDevice</code>
     * with the specified <code>MediaType</code> and is appropriate for the current states of the
     * associated <code>CallPeer</code> and <code>Call</code>.
     *
     * For example, when the local peer is acting as a conference focus in the <code>Call</code> of the
     * associated <code>CallPeer</code>, the audio device must be a mixer.
     *
     * @param mediaType the <code>MediaType</code> in which the retrieved <code>MediaDevice</code> is to capture
     * and/or play back media
     * @return a <code>MediaDevice</code> which is capable of capture and/or playback of media of the
     * specified <code>mediaType</code>, is the default choice of the user for a
     * <code>MediaDevice</code> with the specified <code>mediaType</code> and is appropriate for the
     * current states of the associated <code>CallPeer</code> and <code>Call</code>
     */
    protected MediaDevice getDefaultDevice(MediaType mediaType)
    {
        // cmeng (20210504): Call with/initiated by Conversation may get terminated abruptly, when h264 video codec is used
        if (mPeer.getCall() == null) {
            Timber.w("Get default device with null call: %s %s", mPeer, mediaType);
            aTalkApp.showToastMessage(R.string.call_ended, mPeer.getEntity());
            return null;
        }

        return mPeer.getCall().getDefaultDevice(mediaType);
    }

    /**
     * Gets the <code>MediaDirection</code> value which represents the preference of the user with
     * respect to streaming media of the specified <code>MediaType</code>.
     *
     * @param mediaType the <code>MediaType</code> to retrieve the user preference for
     * @return a <code>MediaDirection</code> value which represents the preference of the user with
     * respect to streaming media of the specified <code>mediaType</code>
     */
    protected MediaDirection getDirectionUserPreference(MediaType mediaType)
    {
        switch (mediaType) {
            case AUDIO:
                return audioDirectionUserPreference;
            case VIDEO:
                return videoDirectionUserPreference;
            default:
                return MediaDirection.INACTIVE;
        }
    }

    /**
     * Returns the {@link DynamicPayloadTypeRegistry} instance we are currently using.
     *
     * @return the {@link DynamicPayloadTypeRegistry} instance we are currently using.
     */
    protected DynamicPayloadTypeRegistry getDynamicPayloadTypes()
    {
        return this.dynamicPayloadTypes;
    }

    /**
     * Gets the SRTP control type used for a given media type.
     *
     * @param mediaType the <code>MediaType</code> to get the SRTP control type for
     * @return the SRTP control type (MIKEY, SDES, ZRTP) used for the given media type or
     * <code>null</code> if SRTP is not enabled for the given media type
     */
    public SrtpControl getEncryptionMethod(MediaType mediaType)
    {
        return mediaHandler.getEncryptionMethod(this, mediaType);
    }

    /**
     * Returns a (possibly empty) <code>List</code> of <code>RTPExtension</code>s supported by the device
     * that this media handler uses to handle media of the specified <code>type</code>.
     *
     * @param type the <code>MediaType</code> of the device whose <code>RTPExtension</code>s we are interested in.
     * @return a (possibly empty) <code>List</code> of <code>RTPExtension</code>s supported by the device
     * that this media handler uses to handle media of the specified <code>type</code>.
     */
    protected List<RTPExtension> getExtensionsForType(MediaType type)
    {
        MediaDevice device = getDefaultDevice(type);
        return (device != null) ? device.getSupportedExtensions() : new ArrayList<>();
    }

    /**
     * Returns the harvesting time (in ms) for the harvester given in parameter.
     *
     * @param harvesterName The class name if the harvester.
     * @return The harvesting time (in ms) for the harvester given in parameter. 0 if this
     * harvester does not exists, if the ICE agent is null, or if the agent has never
     * harvested with this harvester.
     */
    public long getHarvestingTime(String harvesterName)
    {
        TransportManager<?> transportManager = queryTransportManager();
        return (transportManager == null) ? 0 : transportManager.getHarvestingTime(harvesterName);
    }

    /**
     * Returns the extended type of the candidate selected if this transport manager is using ICE.
     *
     * @param streamName The stream name (AUDIO, VIDEO);
     * @return The extended type of the candidate selected if this transport manager is using ICE. Otherwise return null.
     */
    public String getICECandidateExtendedType(String streamName)
    {
        TransportManager<?> transportManager = queryTransportManager();
        return (transportManager == null) ? null : transportManager.getICECandidateExtendedType(streamName);
    }

    /**
     * Returns the ICE local host address.
     *
     * @param streamName The stream name (AUDIO, VIDEO);
     * @return the ICE local host address if this transport manager is using ICE. Otherwise, returns null.
     */
    public InetSocketAddress getICELocalHostAddress(String streamName)
    {
        TransportManager<?> transportManager = queryTransportManager();
        return (transportManager == null) ? null : transportManager.getICELocalHostAddress(streamName);
    }

    /**
     * Returns the ICE local reflexive address (server or peer reflexive).
     *
     * @param streamName The stream name (AUDIO, VIDEO);
     * @return the ICE local reflexive address. May be null if this transport manager is not using
     * ICE or if there is no reflexive address for the local candidate used.
     */
    public InetSocketAddress getICELocalReflexiveAddress(String streamName)
    {
        TransportManager<?> transportManager = queryTransportManager();
        return (transportManager == null) ? null : transportManager.getICELocalReflexiveAddress(streamName);
    }

    /**
     * Returns the ICE local relayed address (server or peer relayed).
     *
     * @param streamName The stream name (AUDIO, VIDEO);
     * @return the ICE local relayed address. May be null if this transport manager is not using ICE
     * or if there is no relayed address for the local candidate used.
     */
    public InetSocketAddress getICELocalRelayedAddress(String streamName)
    {
        TransportManager<?> transportManager = queryTransportManager();
        return (transportManager == null) ? null : transportManager.getICELocalRelayedAddress(streamName);
    }

    /**
     * Returns the ICE remote host address.
     *
     * @param streamName The stream name (AUDIO, VIDEO);
     * @return the ICE remote host address if this transport manager is using ICE. Otherwise, returns null.
     */
    public InetSocketAddress getICERemoteHostAddress(String streamName)
    {
        TransportManager<?> transportManager = queryTransportManager();
        return (transportManager == null) ? null : transportManager.getICERemoteHostAddress(streamName);
    }

    /**
     * Returns the ICE remote reflexive address (server or peer reflexive).
     *
     * @param streamName The stream name (AUDIO, VIDEO);
     * @return the ICE remote reflexive address. May be null if this transport manager is not using
     * ICE or if there is no reflexive address for the remote candidate used.
     */
    public InetSocketAddress getICERemoteReflexiveAddress(String streamName)
    {
        TransportManager<?> transportManager = queryTransportManager();
        return (transportManager == null) ? null : transportManager.getICERemoteReflexiveAddress(streamName);
    }

    /**
     * Returns the ICE remote relayed address (server or peer relayed).
     *
     * @param streamName The stream name (AUDIO, VIDEO);
     * @return the ICE remote relayed address. May be null if this transport manager is not using
     * ICE or if there is no relayed address for the remote candidate used.
     */
    public InetSocketAddress getICERemoteRelayedAddress(String streamName)
    {
        TransportManager<?> transportManager = queryTransportManager();
        return (transportManager == null) ? null : transportManager.getICERemoteRelayedAddress(streamName);
    }

    /**
     * Returns the current state of ICE processing.
     *
     * @return the current state of ICE processing if this transport manager is using ICE. Otherwise, returns null.
     */
    public String getICEState()
    {
        TransportManager<?> transportManager = queryTransportManager();
        return (transportManager == null) ? null : transportManager.getICEState();
    }

    /**
     * Returns a list of locally supported <code>MediaFormat</code>s for the given
     * <code>MediaDevice</code>, ordered in descending priority. Takes into account the configuration
     * obtained from the <code>ProtocolProvider</code> instance associated this media handler -- if
     * its set up to override the global encoding settings, uses that configuration, otherwise
     * uses the global configuration.
     *
     * @param mediaDevice the <code>MediaDevice</code>.
     * @return a non-null list of locally supported <code>MediaFormat</code>s for <code>mediaDevice</code>,
     * in decreasing order of priority.
     * @see CallPeerMediaHandler#getLocallySupportedFormats(MediaDevice, QualityPreset, QualityPreset)
     */
    public List<MediaFormat> getLocallySupportedFormats(MediaDevice mediaDevice)
    {
        return getLocallySupportedFormats(mediaDevice, null, null);
    }

    /**
     * Returns a list of locally supported <code>MediaFormat</code>s for the given
     * <code>MediaDevice</code>, ordered in descending priority. Takes into account the configuration
     * obtained from the <code>ProtocolProvider</code> instance associated this media handler -- if
     * its set up to override the global encoding settings, uses that configuration, otherwise
     * uses the global configuration.
     *
     * @param mediaDevice the <code>MediaDevice</code>.
     * @param sendPreset the preset used to set some of the format parameters, used for video and settings.
     * @param receivePreset the preset used to set the receive format parameters, used for video and settings.
     * @return a non-null list of locally supported <code>MediaFormat</code>s for <code>mediaDevice</code>,
     * in decreasing order of priority.
     */
    public List<MediaFormat> getLocallySupportedFormats(MediaDevice mediaDevice,
            QualityPreset sendPreset, QualityPreset receivePreset)
    {
        List<MediaFormat> supportFormats = Collections.emptyList();
        if (mediaDevice != null) {
            Map<String, String> accountProperties = mPeer.getProtocolProvider().getAccountID().getAccountProperties();
            String overrideEncodings = accountProperties.get(ProtocolProviderFactory.OVERRIDE_ENCODINGS);

            if (Boolean.parseBoolean(overrideEncodings)) {
                /*
                 * The account properties associated with the CallPeer of this CallPeerMediaHandler
                 * override the global EncodingConfiguration.
                 */
                EncodingConfiguration encodingConfiguration
                        = ProtocolMediaActivator.getMediaService().createEmptyEncodingConfiguration();

                encodingConfiguration.loadProperties(accountProperties, ProtocolProviderFactory.ENCODING_PROP_PREFIX);
                supportFormats = mediaDevice.getSupportedFormats(sendPreset, receivePreset, encodingConfiguration);
            }
            else {
                /* The global EncodingConfiguration is in effect. */
                supportFormats = mediaDevice.getSupportedFormats(sendPreset, receivePreset);
            }
        }

        if (supportFormats.size() == 0) {
            DialogActivity.showDialog(aTalkApp.getInstance(),
                    R.string.call_audio, R.string.call_no_device_codec_H,
                    (mediaDevice != null) ? mediaDevice.getMediaType().toString() : "Unknown");
        }
        return supportFormats;
    }

    /**
     * Gets the visual <code>Component</code>, if any, depicting the video streamed from the local peer
     * to the remote peer.
     *
     * @return the visual <code>Component</code> depicting the local video if local video is actually
     * being streamed from the local peer to the remote peer; otherwise, <code>null</code>
     */
    public Component getLocalVisualComponent()
    {
        MediaStream videoStream = getStream(MediaType.VIDEO);
        return ((videoStream == null) || !isLocalVideoTransmissionEnabled())
                ? null : ((VideoMediaStream) videoStream).getLocalVisualComponent();
    }

    public MediaHandler getMediaHandler()
    {
        return mediaHandler;
    }

    /**
     * Returns the number of harvesting for this agent.
     *
     * @return The number of harvesting for this agent.
     */
    public int getNbHarvesting()
    {
        TransportManager<?> transportManager = queryTransportManager();
        return (transportManager == null) ? 0 : transportManager.getNbHarvesting();
    }

    /**
     * Returns the number of harvesting time for the harvester given in parameter.
     *
     * @param harvesterName The class name if the harvester.
     * @return The number of harvesting time for the harvester given in parameter.
     */
    public int getNbHarvesting(String harvesterName)
    {
        TransportManager<?> transportManager = queryTransportManager();
        return (transportManager == null) ? 0 : transportManager.getNbHarvesting(harvesterName);
    }

    /**
     * Returns the peer that is this media handler's "raison d'etre".
     *
     * @return the {@link MediaAwareCallPeer} that this handler is servicing.
     */
    public T getPeer()
    {
        return mPeer;
    }

    /**
     * Gets the last-known SSRC of an RTP stream with a specific <code>MediaType</code> received by a
     * <code>MediaStream</code> of this instance.
     *
     * @return the last-known SSRC of an RTP stream with a specific <code>MediaType</code> received
     * by a <code>MediaStream</code> of this instance
     */
    public long getRemoteSSRC(MediaType mediaType)
    {
        return mediaHandler.getRemoteSSRC(this, mediaType);
    }

    /**
     * Returns the {@link DynamicRTPExtensionsRegistry} instance we are currently using.
     *
     * @return the {@link DynamicRTPExtensionsRegistry} instance we are currently using.
     */
    protected DynamicRTPExtensionsRegistry getRtpExtensionsRegistry()
    {
        return this.rtpExtensionsRegistry;
    }

    /**
     * Gets the <code>SrtpControl</code>s of the <code>MediaStream</code>s of this instance.
     *
     * @return the <code>SrtpControl</code>s of the <code>MediaStream</code>s of this instance
     */
    public SrtpControls getSrtpControls()
    {
        return (mediaHandler != null)  // NPE from field ?
                ? mediaHandler.getSrtpControls(this) : new SrtpControls();
    }

    /**
     * Gets the <code>MediaStream</code> of this <code>CallPeerMediaHandler</code> which is of a specific
     * <code>MediaType</code>. If this instance doesn't have such a <code>MediaStream</code>, returns <code>null</code>
     *
     * @param mediaType the <code>MediaType</code> of the <code>MediaStream</code> to retrieve
     * @return the <code>MediaStream</code> of this <code>CallPeerMediaHandler</code> which is of the
     * specified <code>mediaType</code> if this instance has such a <code>MediaStream</code>; otherwise, <code>null</code>
     */
    public MediaStream getStream(MediaType mediaType)
    {
        switch (mediaType) {
            case AUDIO:
                return audioStream;
            case VIDEO:
                return videoStream;
            default:
                return null;
        }
    }

    /**
     * Returns the total harvesting time (in ms) for all harvesters.
     *
     * @return The total harvesting time (in ms) for all the harvesters. 0 if the ICE agent is
     * null, or if the agent has never harvested.
     */
    public long getTotalHarvestingTime()
    {
        TransportManager<?> transportManager = queryTransportManager();
        return (transportManager == null) ? 0 : transportManager.getTotalHarvestingTime();
    }

    /**
     * Gets the <code>TransportManager</code> implementation handling our address management. If the
     * <code>TransportManager</code> does not exist yet, it is created.
     *
     * @return the <code>TransportManager</code> implementation handling our address management
     */
    protected abstract TransportManager<T> getTransportManager();

    /**
     * Gets the <code>TransportManager</code> implementation handling our address management. If the
     * <code>TransportManager</code> does not exist yet, it is not created.
     *
     * @return the <code>TransportManager</code> implementation handling our address management
     */
    protected abstract TransportManager<T> queryTransportManager();

    /**
     * Gets the visual <code>Component</code> in which video from the remote peer is currently being
     * rendered or <code>null</code> if there is currently no video streaming from the remote peer.
     *
     * @return the visual <code>Component</code> in which video from the remote peer is currently being
     * rendered or <code>null</code> if there is currently no video streaming from the remote peer
     */
    @Deprecated
    public Component getVisualComponent()
    {
        List<Component> visualComponents = getVisualComponents();
        return visualComponents.isEmpty() ? null : visualComponents.get(0);
    }

    /**
     * Gets the visual <code>Component</code>s in which videos from the remote peer are currently being rendered.
     *
     * @return the visual <code>Component</code>s in which videos from the remote peer are currently
     * being rendered
     */
    public List<Component> getVisualComponents()
    {
        MediaStream videoStream = getStream(MediaType.VIDEO);
        List<Component> visualComponents;

        if (videoStream == null)
            visualComponents = Collections.emptyList();
        else {
            visualComponents = ((VideoMediaStream) videoStream).getVisualComponents();
        }
        return visualComponents;
    }

    /**
     * Creates if necessary, and configures the stream that this <code>MediaHandler</code> is using for
     * the <code>MediaType</code> matching the one of the <code>MediaDevice</code>.
     *
     * @param connector the <code>MediaConnector</code> that we'd like to bind the newly created stream to.
     * @param device the <code>MediaDevice</code> that we'd like to attach the newly created <code>MediaStream</code> to.
     * @param format the <code>MediaFormat</code> that we'd like the new <code>MediaStream</code> to be set to transmit in.
     * @param target the <code>MediaStreamTarget</code> containing the RTP and RTCP address:port couples that
     * the new stream would be sending packets to.
     * @param direction the <code>MediaDirection</code> that we'd like the new stream to use
     * (i.e. sendonly, sendrecv, recvonly, or inactive).
     * @param rtpExtensions the list of <code>RTPExtension</code>s that should be enabled for this stream.
     * @param masterStream whether the stream to be used as master if secured
     * @return the newly created <code>MediaStream</code>.
     * @throws OperationFailedException if creating the stream fails for any reason (like, for example,
     * accessing the device or setting the format).
     */
    protected MediaStream initStream(StreamConnector connector, MediaDevice device, MediaFormat format,
            MediaStreamTarget target, MediaDirection direction, List<RTPExtension> rtpExtensions, boolean masterStream)
            throws OperationFailedException
    {
        MediaType mediaType = device.getMediaType();
        Timber.d("Initializing %s stream: %s; peer: %s", mediaType, format, mPeer);

        /*
         * Do make sure no unintentional streaming of media generated by the user without prior consent will happen.
         */
        direction = direction.and(getDirectionUserPreference(mediaType));
        /*
         * If the device does not support a direction, there is really nothing to be done at this point to make it use it.
         */
        direction = direction.and(device.getDirection());
        MediaStream stream = mediaHandler.initStream(this, connector, device, format, target,
                direction, rtpExtensions, masterStream);

        switch (mediaType) {
            case AUDIO:
                audioStream = (AudioMediaStream) stream;
                break;
            case VIDEO:
                videoStream = (VideoMediaStream) stream;
                break;
        }
        return stream;
    }

    /**
     * Compares a list of <code>MediaFormat</code>s offered by a remote party to the list of locally
     * supported <code>RTPExtension</code>s as returned by one of our local <code>MediaDevice</code>s and
     * returns a third <code>List</code> that contains their intersection.
     *
     * At the same time remove FORMAT_PARAMETER_ATTR_IMAGEATTR from localFormat if not found in remoteFormat
     *
     * @param remoteFormats remote <code>MediaFormat</code>'s found in the SDP message
     * @param localFormats local supported <code>MediaFormat</code> of our device
     * @return intersection between our local and remote <code>MediaFormat</code>
     *
     * Note that it also treats telephone-event as a special case and puts it to the end of the
     * intersection, if there is any intersection.
     * @see MediaFormatImpl#FORMAT_PARAMETER_ATTR_IMAGEATTR
     */
    protected List<MediaFormat> intersectFormats(List<MediaFormat> remoteFormats, List<MediaFormat> localFormats)
    {
        List<MediaFormat> ret = new ArrayList<>();
        MediaFormat telephoneEvents = null;
        MediaFormat red = null;
        MediaFormat ulpfec = null;

        for (MediaFormat remoteFormat : remoteFormats) {
            MediaFormat localFormat = findMediaFormat(localFormats, remoteFormat);

            if (localFormat != null) {
                // We ignore telephone-event, red and ulpfec here as they are not real media formats.
                // Therefore, we don't want to decide to use any of them as our preferred format.
                // We'll add them back later if we find a common media format.
                //
                // Note if there are multiple telephone-event (or red, or ulpfec) formats, we'll
                // lose all but the last one. That's fine because it's meaningless to have
                // multiple repeated formats.
                String encoding = localFormat.getEncoding();
                if (Constants.TELEPHONE_EVENT.equals(encoding)) {
                    telephoneEvents = localFormat;
                    continue;
                }
                else if (Constants.RED.equals(encoding)) {
                    red = localFormat;
                    continue;
                }
                else if (Constants.ULPFEC.equals(encoding)) {
                    ulpfec = localFormat;
                    continue;
                }

                // remove FORMAT_PARAMETER_ATTR_IMAGEATTR if not found in offer formats
                if (localFormat.hasParameter(FORMAT_PARAMETER_ATTR_IMAGEATTR)) {
                    if (!remoteFormat.hasParameter(FORMAT_PARAMETER_ATTR_IMAGEATTR)) {
                        localFormat.removeParameter(FORMAT_PARAMETER_ATTR_IMAGEATTR);
                        Timber.d("Media format advance parameter (%s) removed; remote: %s; local: %s",
                                FORMAT_PARAMETER_ATTR_IMAGEATTR, remoteFormat, localFormat);
                    }
                    else
                        imageAttrs.put(mPeer.getPeerJid(), true);
                }
                ret.add(localFormat);
            }
        }

        // If we've found some compatible formats, add telephone-event, red and ulpfec back into
        // the end of the list (if we removed any of them) above. If we didn't find any
        // compatible formats, we don't want to add any of these formats as the only entries in
        // the list because there would be no media.
        if (!ret.isEmpty()) {
            if (telephoneEvents != null)
                ret.add(telephoneEvents);
            if (red != null)
                ret.add(red);
            if (ulpfec != null)
                ret.add(ulpfec);
        }
        return ret;
    }

    /**
     * Compares a list of <code>RTPExtension</code>s offered by a remote party to the list of locally
     * supported <code>RTPExtension</code>s as returned by one of our local <code>MediaDevice</code>s and
     * returns a third <code>List</code> that contains their intersection. The returned <code>List</code>
     * contains extensions supported by both the remote party and the local device that we are
     * dealing with. Direction attributes of both lists are also intersected, and the returned
     * <code>RTPExtension</code>s have directions valid from a local perspective. In other words, if
     * <code>remoteExtensions</code> contains an extension that the remote party supports in a
     * <code>SENDONLY</code> mode, and we support that extension in a <code>SENDRECV</code> mode, the
     * corresponding entry in the returned list will have a <code>RECVONLY</code> direction.
     *
     * @param remoteExtensions the <code>List</code> of <code>RTPExtension</code>s as advertised by the remote party.
     * @param supportedExtensions the <code>List</code> of <code>RTPExtension</code>s that a local <code>MediaDevice</code>
     * returned s supported.
     * @return the (possibly empty) intersection of both of the extensions lists in a form that can
     * be used for generating an SDP media description or for configuring a stream.
     */
    protected List<RTPExtension> intersectRTPExtensions(List<RTPExtension> remoteExtensions,
            List<RTPExtension> supportedExtensions)
    {
        if (remoteExtensions == null || supportedExtensions == null)
            return new ArrayList<>();

        List<RTPExtension> intersection = new ArrayList<>(Math.min(remoteExtensions.size(), supportedExtensions.size()));

        // loop through the list that the remote party sent
        for (RTPExtension remoteExtension : remoteExtensions) {
            RTPExtension localExtension = findExtension(supportedExtensions, remoteExtension.getURI().toString());
            if (localExtension == null)
                continue;

            MediaDirection localDir = localExtension.getDirection();
            MediaDirection remoteDir = remoteExtension.getDirection();

            RTPExtension intersected = new RTPExtension(localExtension.getURI(),
                    localDir.getDirectionForAnswer(remoteDir), remoteExtension.getExtensionAttributes());
            intersection.add(intersected);
        }
        return intersection;
    }

    /**
     * Check to see if the callPeer allow sending of imgattr parameter
     *
     * @param callPeer the callPeer
     * @return default to false if null, else the actual state
     */
    public static boolean isImageattr(CallPeer callPeer)
    {
        Boolean imgattr = imageAttrs.get(callPeer.getPeerJid());
        return (imgattr != null) && imgattr;
    }

    /**
     * Check to see if the callPeer support <rtcp-mux/>
     *
     * @param callPeer the callPeer
     * @return default to true if null, else the actual state
     */
    public static boolean isRtpcMux(CallPeer callPeer)
    {
        Boolean rtcpmux = rtcpMuxes.get(callPeer.getPeerJid());
        return (rtcpmux == null) || rtcpmux;
    }

    /**
     * Checks whether <code>dev</code> can be used for a call.
     *
     * @return <code>true</code> if the device is not null, and it has at least one enabled format. Otherwise <code>false</code>
     */
    public boolean isDeviceActive(MediaDevice dev)
    {
        return ((dev != null) && !getLocallySupportedFormats(dev).isEmpty());
    }

    /**
     * Checks whether <code>dev</code> can be used for a call, using <code>sendPreset</code> and <code>receivePreset</code>
     *
     * @return <code>true</code> if the device is not null, and it has at least one enabled format. Otherwise <code>false</code>
     */
    public boolean isDeviceActive(MediaDevice dev, QualityPreset sendPreset, QualityPreset receivePreset)
    {
        return ((dev != null) && !getLocallySupportedFormats(dev, sendPreset, receivePreset).isEmpty());
    }

    /**
     * Determines whether this media handler is currently set to transmit local audio.
     *
     * @return <code>true</code> if the media handler is set to transmit local audio and <code>false</code> otherwise.
     */
    public boolean isLocalAudioTransmissionEnabled()
    {
        return audioDirectionUserPreference.allowsSending();
    }

    /**
     * Determines whether this handler's streams have been placed on hold.
     *
     * @return <code>true</code> if this handler's streams have been placed on hold and <code>false</code> otherwise.
     */
    public boolean isLocallyOnHold()
    {
        return locallyOnHold;
        // no need to actually check stream directions because we only update them through the setLocallyOnHold()
        // method so if the value of the locallyOnHold field has changed, so have stream directions.
    }

    /**
     * Determines whether this media handler is currently set to transmit local video.
     *
     * @return <code>true</code> if the media handler is set to transmit local video and false otherwise.
     */
    public boolean isLocalVideoTransmissionEnabled()
    {
        return videoDirectionUserPreference.allowsSending();
    }

    /**
     * Determines whether the audio stream of this media handler is currently on mute.
     *
     * @return <code>true</code> if local audio transmission is currently on mute and <code>false</code> otherwise.
     */
    public boolean isMute()
    {
        MediaStream audioStream = getStream(MediaType.AUDIO);
        return (audioStream != null) && audioStream.isMute();
    }

    /**
     * Determines whether the remote party has placed all our streams on hold.
     *
     * @return <code>true</code> if all our streams have been placed on hold (i.e. if none of them is
     * currently sending and <code>false</code> otherwise.
     */
    public boolean isRemotelyOnHold()
    {
        for (MediaType mediaType : MediaType.values()) {
            MediaStream stream = getStream(mediaType);
            if ((stream != null) && stream.getDirection().allowsSending())
                return false;
        }
        return true;
    }

    /**
     * Determines whether RTP translation is enabled for the <code>CallPeer</code> represented by this
     * <code>CallPeerMediaHandler</code> and for a specific <code>MediaType</code>.
     *
     * @param mediaType the <code>MediaType</code> for which it is to be determined whether RTP translation is
     * enabled for the <tT>CallPeeer</code> represented by this <code>CallPeerMediaHandler</code>
     * @return <code>true</code> if RTP translation is enabled for the <code>CallPeer</code> represented by
     * this <code>CallPeerMediaHandler</code> and for the specified <code>mediaType</code>; otherwise, <code>false</code>
     */
    public boolean isRTPTranslationEnabled(MediaType mediaType)
    {
        MediaAwareCall<?, ?, ?> call = mPeer.getCall();
        if ((call != null) && call.isConferenceFocus() && !call.isLocalVideoStreaming()) {
            Iterator<?> callPeerIt = call.getCallPeers();

            while (callPeerIt.hasNext()) {
                MediaAwareCallPeer<?, ?, ?> callPeer = (MediaAwareCallPeer<?, ?, ?>) callPeerIt.next();
                MediaStream stream = callPeer.getMediaHandler().getStream(mediaType);

                if (stream != null)
                    return true;
            }
        }
        return false;
    }

    /**
     * Returns the secure state of the call. If both audio and video is secured.
     *
     * @return the call secure state
     */
    public boolean isSecure()
    {
        for (MediaType mediaType : MediaType.values()) {
            MediaStream stream = getStream(mediaType);

            /*
             * If a stream for a specific MediaType does not exist, it's considered secure.
             */
            if ((stream != null) && !stream.getSrtpControl().getSecureCommunicationStatus())
                return false;
        }
        return true;
    }

    /**
     * Notifies this instance about a <code>PropertyChangeEvent</code> fired by the associated
     * {@link MediaHandler}. Since this instance wraps around the associated <code>MediaHandler</code>,
     * it forwards the property changes as its own. Allows extenders to override.
     *
     * @param ev the <code>PropertyChangeEvent</code> fired by the associated <code>MediaHandler</code>
     */
    protected void mediaHandlerPropertyChange(PropertyChangeEvent ev)
    {
        firePropertyChange(ev.getPropertyName(), ev.getOldValue(), ev.getNewValue());
    }

    /**
     * Processes a request for a (video) key frame from the remote peer to the local peer.
     *
     * @return <code>true</code> if the request for a (video) key frame has been honored by the local
     * peer; otherwise, <code>false</code>
     */
    public boolean processKeyFrameRequest()
    {
        return mediaHandler.processKeyFrameRequest(this);
    }

    /**
     * Removes from this instance and cleans up the <code>SrtpControl</code> which are not of a
     * specific <code>SrtpControlType</code>.
     *
     * @param mediaType the <code>MediaType</code> of the <code>SrtpControl</code> to be examined
     * @param srtpControlType the <code>SrtpControlType</code> of the <code>SrtpControl</code>s to not be removed from this
     * instance and cleaned up. If <code>null</code>, all <code>SrtpControl</code>s are removed from
     * this instance and cleaned up
     */
    protected void removeAndCleanupOtherSrtpControls(MediaType mediaType, SrtpControlType srtpControlType)
    {
        SrtpControls srtpControls = getSrtpControls();

        for (SrtpControlType i : SrtpControlType.values()) {
            if (!i.equals(srtpControlType)) {
                SrtpControl e = srtpControls.remove(mediaType, i);
                if (e != null)
                    e.cleanup(null);
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
    public void removeVideoListener(VideoListener listener)
    {
        videoNotifierSupport.removeVideoListener(listener);
    }

    /**
     * Requests a key frame from the remote peer of the associated <code>VideoMediaStream</code> of
     * this <code>CallPeerMediaHandler</code>. The default implementation provided by
     * <code>CallPeerMediaHandler</code> always returns <code>false</code>.
     *
     * @return <code>true</code> if this <code>CallPeerMediaHandler</code> has indeed requested a key frame
     * from the remote peer of its associated <code>VideoMediaStream</code> in response to the
     * call; otherwise, <code>false</code>
     */
    protected boolean requestKeyFrame()
    {
        return false;
    }

    /**
     * Sends empty UDP packets to target destination data/control ports in order to open port on
     * NAT or RTP proxy if any. In order to be really efficient, this method should be called
     * after we send our offer or answer.
     *
     * @param stream <code>MediaStream</code> non-null stream
     * @param mediaType <code>MediaType</code>
     */
    protected void sendHolePunchPacket(MediaStream stream, MediaType mediaType)
    {
        if (disableHolePunching)
            return;

        // send as a hole punch packet a constructed rtp packet has the correct payload type and ssrc
        RawPacket packet = new RawPacket(HOLE_PUNCH_PACKET.clone(), 0, HOLE_PUNCH_PACKET.length);

        MediaFormat format = stream.getFormat();
        byte payloadType = format.getRTPPayloadType();
        // is this a dynamic payload type.
        if (payloadType == MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN) {
            payloadType = dynamicPayloadTypes.getPayloadType(format);
        }

        packet.setPayloadType(payloadType);
        packet.setSSRC((int) stream.getLocalSourceID());

        // cmeng (20181008): sending the defined HOLE_PUNCH_PACKET causing problem in transmit mic data being muted???
        // Disable it for aTalk always i.e. not using defined packet i.e. packet => null
        getTransportManager().sendHolePunchPacket(stream.getTarget(), mediaType, null);
    }

    /**
     * Sets <code>csrcAudioLevelListener</code> as the listener that will be receiving notifications
     * for changes in the audio levels of the remote participants that our peer is mixing.
     *
     * @param listener the <code>CsrcAudioLevelListener</code> to set to our audio stream.
     */
    public void setCsrcAudioLevelListener(CsrcAudioLevelListener listener)
    {
        synchronized (csrcAudioLevelListenerLock) {
            if (this.csrcAudioLevelListener != listener) {
                MediaHandler mediaHandler = getMediaHandler();

                if ((mediaHandler != null) && (this.csrcAudioLevelListener != null)) {
                    mediaHandler.removeCsrcAudioLevelListener(this.csrcAudioLevelListener);
                }

                this.csrcAudioLevelListener = listener;
                if ((mediaHandler != null) && (this.csrcAudioLevelListener != null)) {
                    mediaHandler.addCsrcAudioLevelListener(this.csrcAudioLevelListener);
                }
            }
        }
    }

    /**
     * Specifies whether this media handler should be allowed to transmit local audio.
     *
     * @param enabled <code>true</code> if the media handler should transmit local audio and <code>false</code> otherwise.
     */
    public void setLocalAudioTransmissionEnabled(boolean enabled)
    {
        audioDirectionUserPreference = enabled ? MediaDirection.SENDRECV : MediaDirection.RECVONLY;
    }

    /**
     * Puts all <code>MediaStream</code>s in this handler locally on or off hold (according to the
     * value of <code>locallyOnHold</code>). This would also be taken into account when the next
     * update offer is generated.
     *
     * @param locallyOnHold <code>true</code> if we are to make our streams stop transmitting and <code>false</code> if we
     * are to start transmitting again.
     */
    public void setLocallyOnHold(boolean locallyOnHold)
            throws OperationFailedException
    {
        Timber.d("Setting locally on hold: %s", locallyOnHold);

        this.locallyOnHold = locallyOnHold;
        // On hold.
        if (locallyOnHold) {
            MediaStream audioStream = getStream(MediaType.AUDIO);
            MediaDirection direction = (mPeer.getCall().isConferenceFocus() || audioStream == null)
                    ? MediaDirection.INACTIVE : audioStream.getDirection().and(MediaDirection.SENDONLY);

            // the direction in situation where audioStream is null is ignored (just avoiding NPE)
            if (audioStream != null) {
                audioStream.setDirection(direction);
                audioStream.setMute(true);
            }

            MediaStream videoStream = getStream(MediaType.VIDEO);
            if (videoStream != null) {
                direction = mPeer.getCall().isConferenceFocus()
                        ? MediaDirection.INACTIVE : videoStream.getDirection().and(MediaDirection.SENDONLY);
                videoStream.setDirection(direction);
                videoStream.setMute(true);
            }
        }
        /*
         * Off hold. Make sure that we re-enable sending only if other party is not on hold.
         */
        else if (!CallPeerState.ON_HOLD_MUTUALLY.equals(mPeer.getState())) {
            MediaStream audioStream = getStream(MediaType.AUDIO);

            if (audioStream != null) {
                audioStream.setDirection(audioStream.getDirection().or(MediaDirection.SENDONLY));
                audioStream.setMute(false);
            }

            MediaStream videoStream = getStream(MediaType.VIDEO);
            if ((videoStream != null) && (videoStream.getDirection() != MediaDirection.INACTIVE)) {
                videoStream.setDirection(videoStream.getDirection().or(MediaDirection.SENDONLY));
                videoStream.setMute(false);
            }
        }
    }

    /**
     * If the local <code>AudioMediaStream</code> has already been created, sets <code>listener</code> as
     * the <code>SimpleAudioLevelListener</code> that it should notify for local user level events.
     * Otherwise stores a reference to <code>listener</code> so that we could add it once we create the stream.
     *
     * @param listener the <code>SimpleAudioLevelListener</code> to add or <code>null</code> if we are trying to
     * remove it.
     */
    public void setLocalUserAudioLevelListener(SimpleAudioLevelListener listener)
    {
        synchronized (localUserAudioLevelListenerLock) {
            if (this.localUserAudioLevelListener != listener) {
                MediaHandler mediaHandler = getMediaHandler();

                if ((mediaHandler != null) && (this.localUserAudioLevelListener != null)) {
                    mediaHandler.removeLocalUserAudioLevelListener(this.localUserAudioLevelListener);
                }

                this.localUserAudioLevelListener = listener;
                if ((mediaHandler != null) && (this.localUserAudioLevelListener != null)) {
                    mediaHandler.addLocalUserAudioLevelListener(this.localUserAudioLevelListener);
                }
            }
        }
    }

    /**
     * Specifies whether this media handler should be allowed to transmit local video.
     *
     * @param enabled <code>true</code> if the media handler should transmit local video and <code>false</code> otherwise.
     */
    public void setLocalVideoTransmissionEnabled(boolean enabled)
    {
        Timber.d("Setting local video transmission enabled: %s", enabled);
        MediaDirection oldValue = videoDirectionUserPreference;

        videoDirectionUserPreference = enabled ? MediaDirection.SENDRECV : MediaDirection.RECVONLY;
        MediaDirection newValue = videoDirectionUserPreference;

        /*
         * Do not send an event here if the local video is enabled because the video stream
         * needs to start before the correct MediaDevice is set in VideoMediaDeviceSession.
         */
        if (!enabled) {
            firePropertyChange(OperationSetVideoTelephony.LOCAL_VIDEO_STREAMING, oldValue, newValue);
        }
    }

    public void setMediaHandler(MediaHandler mediaHandler)
    {
        if (this.mediaHandler != mediaHandler) {
            if (this.mediaHandler != null) {
                synchronized (csrcAudioLevelListenerLock) {
                    if (csrcAudioLevelListener != null) {
                        this.mediaHandler.removeCsrcAudioLevelListener(csrcAudioLevelListener);
                    }
                }
                synchronized (localUserAudioLevelListenerLock) {
                    if (localUserAudioLevelListener != null) {
                        this.mediaHandler.removeLocalUserAudioLevelListener(localUserAudioLevelListener);
                    }
                }
                synchronized (streamAudioLevelListenerLock) {
                    if (streamAudioLevelListener != null) {
                        this.mediaHandler.removeStreamAudioLevelListener(streamAudioLevelListener);
                    }
                }

                this.mediaHandler.removeKeyFrameRequester(keyFrameRequester);
                this.mediaHandler.removePropertyChangeListener(mediaHandlerPropertyChangeListener);
                if (srtpListener != null)
                    this.mediaHandler.removeSrtpListener(srtpListener);
                this.mediaHandler.removeVideoListener(videoStreamVideoListener);

                // We intentionally do not remove our Call from the list of DTMF listeners. It
                // should stay there as long as the MediaHandler is used by at least one
                // CallPeer/CPMH. this.mediaHandler.removeDtmfListener(mPeer.getCall());
            }

            this.mediaHandler = mediaHandler;
            if (this.mediaHandler != null) {
                synchronized (csrcAudioLevelListenerLock) {
                    if (csrcAudioLevelListener != null) {
                        this.mediaHandler.addCsrcAudioLevelListener(csrcAudioLevelListener);
                    }
                }
                synchronized (localUserAudioLevelListenerLock) {
                    if (localUserAudioLevelListener != null) {
                        this.mediaHandler.addLocalUserAudioLevelListener(localUserAudioLevelListener);
                    }
                }
                synchronized (streamAudioLevelListenerLock) {
                    if (streamAudioLevelListener != null) {
                        this.mediaHandler.addStreamAudioLevelListener(streamAudioLevelListener);
                    }
                }

                this.mediaHandler.addKeyFrameRequester(-1, keyFrameRequester);
                this.mediaHandler.addPropertyChangeListener(mediaHandlerPropertyChangeListener);
                if (srtpListener != null)
                    this.mediaHandler.addSrtpListener(srtpListener);
                this.mediaHandler.addVideoListener(videoStreamVideoListener);
                this.mediaHandler.addDtmfListener(mPeer.getCall());
            }
        }
    }

    /**
     * Causes this handler's <code>AudioMediaStream</code> to stop transmitting the audio being fed
     * from this stream's <code>MediaDevice</code> and transmit silence instead.
     *
     * @param mute <code>true</code> if we are to make our audio stream start transmitting silence and <code>false</code>
     * if we are to end the transmission of silence and use our stream's <code>MediaDevice</code> again.
     */
    public void setMute(boolean mute)
    {
        MediaStream audioStream = getStream(MediaType.AUDIO);
        if (audioStream != null)
            audioStream.setMute(mute);
    }

    /**
     * If the local <code>AudioMediaStream</code> has already been created, sets <code>listener</code> as
     * the <code>SimpleAudioLevelListener</code> that it should notify for stream user level events.
     * Otherwise stores a reference to <code>listener</code> so that we could add it once we create the stream.
     *
     * @param listener the <code>SimpleAudioLevelListener</code> to add or <code>null</code> if we are trying to remove it.
     */
    public void setStreamAudioLevelListener(SimpleAudioLevelListener listener)
    {
        synchronized (streamAudioLevelListenerLock) {
            if (this.streamAudioLevelListener != listener) {
                MediaHandler mediaHandler = getMediaHandler();
                if ((mediaHandler != null) && (this.streamAudioLevelListener != null)) {
                    mediaHandler.removeStreamAudioLevelListener(this.streamAudioLevelListener);
                }

                this.streamAudioLevelListener = listener;
                if ((mediaHandler != null) && (this.streamAudioLevelListener != null)) {
                    mediaHandler.addStreamAudioLevelListener(this.streamAudioLevelListener);
                }
            }
        }
    }

    /**
     * Starts this <code>CallPeerMediaHandler</code>. If it has already been started, does nothing.
     *
     * @throws IllegalStateException if this method is called without this handler having first seen
     * a media description or having generated an offer.
     */
    public void start()
            throws IllegalStateException
    {
        MediaStream stream;
        stream = getStream(MediaType.AUDIO);
        if ((stream != null) && !stream.isStarted() && isLocalAudioTransmissionEnabled()) {
            Timber.i("Starting callPeer media handler for: %s, %s", stream.getName(), stream.isStarted());
            getTransportManager().setTrafficClass(stream.getTarget(), MediaType.AUDIO);
            stream.start();
            sendHolePunchPacket(stream, MediaType.AUDIO);
        }

        stream = getStream(MediaType.VIDEO);
        if (stream != null) {
            Timber.i("Starting callPeer media handler for: %s (%s) %s",
                    stream.getName(), stream.getFormat(), stream.isStarted());

            /*
             * Inform the listener of LOCAL_VIDEO_STREAMING only once the video stream is available so that VideoMediaDeviceSession
             * has correct MediaDevice set (switch from desktop streaming to webcam video or vice-versa issue)
             */
            firePropertyChange(OperationSetVideoTelephony.LOCAL_VIDEO_STREAMING, null, videoDirectionUserPreference);

            if (!stream.isStarted()) {
                getTransportManager().setTrafficClass(stream.getTarget(), MediaType.VIDEO);
                stream.start();

                /*
                 * Send an empty packet to unblock some kinds of RTP proxies. Do not consult
                 * whether the local video should be streamed and send the hole-punch packet
                 * anyway to let the remote video reach this local peer.
                 */
                sendHolePunchPacket(stream, MediaType.VIDEO);
            }
        }
    }

    /**
     * Passes <code>multiStreamData</code> to the video stream that we are using in this media handler
     * (if any) so that the underlying SRTP lib could properly handle stream security.
     *
     * @param master the data that we are supposed to pass to our video stream.
     */
    public void startSrtpMultistream(SrtpControl master)
    {
        MediaStream videoStream = getStream(MediaType.VIDEO);
        if (videoStream != null)
            videoStream.getSrtpControl().setMultistream(master);
    }

    /**
     * Lets the underlying implementation take note of this error and only then throws it to the
     * using bundles.
     *
     * @param message the message to be logged and then wrapped in a new <code>OperationFailedException</code>
     * @param errorCode the error code to be assigned to the new <code>OperationFailedException</code>
     * @param cause the <code>Throwable</code> that has caused the necessity to log an error and have a new
     * <code>OperationFailedException</code> thrown
     * @throws OperationFailedException the exception that we wanted this method to throw.
     */
    protected abstract void throwOperationFailedException(String message, int errorCode, Throwable cause)
            throws OperationFailedException;

    /**
     * Returns the value to use for the 'msid' source-specific SDP media attribute (RFC5576) for
     * the stream of type <code>mediaType</code> towards the <code>CallPeer</code>. It consist of a group
     * identifier (shared between the local audio and video streams towards the <code>CallPeer</code>)
     * and an identifier for the particular stream, separated by a space.
     *
     * {@see https://tools.ietf.org/html/draft-ietf-mmusic-msid}
     *
     * @param mediaType the media type of the stream for which to return the value for 'msid'
     * @return the value to use for the 'msid' source-specific SDP media attribute (RFC5576) for
     * the stream of type <code>mediaType</code> towards the <code>CallPeer</code>.
     */
    public String getMsid(MediaType mediaType)
    {
        return msLabel + " " + getLabel(mediaType);
    }

    /**
     * Returns the value to use for the 'label' source-specific SDP media attribute (RFC5576) for
     * the stream of type <code>mediaType</code> towards the <code>CallPeer</code>.
     *
     * @param mediaType the media type of the stream for which to return the value for 'label'
     * @return the value to use for the 'label' source-specific SDP media attribute (RFC5576) for
     * the stream of type <code>mediaType</code> towards the <code>CallPeer</code>.
     */
    public String getLabel(MediaType mediaType)
    {
        return mediaType.toString() + hashCode();
    }

    /**
     * Returns the value to use for the 'mslabel' source-specific SDP media attribute (RFC5576).
     *
     * @return the value to use for the 'mslabel' source-specific SDP media attribute (RFC5576).
     */
    public String getMsLabel()
    {
        return msLabel;
    }

    /**
     * Changes whether hole punching is enabled/disabled.
     *
     * @param disableHolePunching the new value
     */
    public void setDisableHolePunching(boolean disableHolePunching)
    {
        this.disableHolePunching = disableHolePunching;
    }

    /**
     * Represents the <code>PropertyChangeListener</code> which listens to changes in the values of the
     * properties of the <code>Call</code> of {@link #mPeer}. Remembers the <code>Call</code> it has been added to
     * because <code>peer</code> does not have a <code>call</code> anymore at the time {@link #close()} is called.
     */
    private class CallPropertyChangeListener implements PropertyChangeListener
    {
        /**
         * The <code>Call</code> this <code>PropertyChangeListener</code> will be or is already added to.
         */
        private final MediaAwareCall<?, ?, ?> call;

        /**
         * Initializes a new <code>CallPropertyChangeListener</code> which is to be added to a specific <code>Call</code>.
         *
         * @param call the <code>Call</code> the new instance is to be added to
         */
        public CallPropertyChangeListener(MediaAwareCall<?, ?, ?> call)
        {
            this.call = call;
        }

        /**
         * Notifies this instance that the value of a specific property of {@link #call} has
         * changed from a specific old value to a specific new value.
         *
         * @param event a <code>PropertyChangeEvent</code> which specifies the name of the property which had
         * its value changed and the old and new values
         */
        public void propertyChange(PropertyChangeEvent event)
        {
            callPropertyChange(event);
        }

        /**
         * Removes this <code>PropertyChangeListener</code> from its associated <code>Call</code>.
         */
        public void removePropertyChangeListener()
        {
            call.removePropertyChangeListener(this);
        }
    }
}
