/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device;

import org.atalk.impl.neomedia.audiolevel.AudioLevelEventDispatcher;
import org.atalk.impl.neomedia.audiolevel.AudioLevelMap;
import org.atalk.impl.neomedia.conference.AudioMixer;
import org.atalk.impl.neomedia.conference.AudioMixingPushBufferDataSource;
import org.atalk.impl.neomedia.conference.DataSourceFilter;
import org.atalk.impl.neomedia.protocol.PushBufferDataSourceDelegate;
import org.atalk.impl.neomedia.protocol.TranscodingDataSource;
import org.atalk.service.neomedia.MediaDirection;
import org.atalk.service.neomedia.QualityPreset;
import org.atalk.service.neomedia.RTPExtension;
import org.atalk.service.neomedia.VolumeControl;
import org.atalk.service.neomedia.codec.EncodingConfiguration;
import org.atalk.service.neomedia.device.MediaDevice;
import org.atalk.service.neomedia.device.MediaDeviceWrapper;
import org.atalk.service.neomedia.event.SimpleAudioLevelListener;
import org.atalk.service.neomedia.format.MediaFormat;
import org.atalk.util.MediaType;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.media.Buffer;
import javax.media.Player;
import javax.media.Processor;
import javax.media.Renderer;
import javax.media.control.TrackControl;
import javax.media.protocol.DataSource;
import javax.media.protocol.PushBufferStream;
import javax.media.rtp.ReceiveStream;

import timber.log.Timber;

/**
 * Implements a <code>MediaDevice</code> which performs audio mixing using {@link AudioMixer}.
 *
 * @author Lyubomir Marinov
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
public class AudioMixerMediaDevice extends AbstractMediaDevice
        implements MediaDeviceWrapper
{
    /**
     * The <code>AudioMixer</code> which performs audio mixing in this <code>MediaDevice</code> (and rather
     * the session that it represents).
     */
    private AudioMixer audioMixer;

    /**
     * The actual <code>AudioMediaDeviceImpl</code> wrapped by this instance for the purposes of audio
     * mixing and used by {@link #audioMixer} as its <code>CaptureDevice</code>.
     */
    private final AudioMediaDeviceImpl device;

    /**
     * The <code>MediaDeviceSession</code> of this <code>AudioMixer</code> with {@link #device}.
     */
    private AudioMixerMediaDeviceSession deviceSession;

    /**
     * The <code>SimpleAudioLevelListener</code> which is registered (or is to be registered) with
     * {@link #localUserAudioLevelDispatcher} and which delivers each of the audio level changes to
     * {@link #localUserAudioLevelListeners}.
     */
    private final SimpleAudioLevelListener localUserAudioLevelDelegate = new SimpleAudioLevelListener()
    {
        public void audioLevelChanged(int level)
        {
            lastMeasuredLocalUserAudioLevel = level;
            fireLocalUserAudioLevelChanged(level);
        }
    };

    /**
     * The dispatcher that delivers to listeners calculations of the local audio level.
     */
    private final AudioLevelEventDispatcher localUserAudioLevelDispatcher = new AudioLevelEventDispatcher(
            "Local User Audio Level Dispatcher (Mixer Edition)");

    /**
     * The <code>List</code> where we store all listeners interested in changes of the local audio
     * level and the number of times each one of them has been added. We wrap listeners because
     * we may have multiple subscriptions with the same listener and we would only store it once.
     * If one of the multiple subscriptions of a particular listener is removed, however, we
     * wouldn't want to reset the listener to <code>null</code> as there are others still interested,
     * and hence the <code>referenceCount</code> in the wrapper.
     * <p>
     * <b>Note</b>: <code>localUserAudioLevelListeners</code> is a copy-on-write storage and access to
     * it is synchronized by {@link #localUserAudioLevelListenersSyncRoot}.
     * </p>
     */
    private List<SimpleAudioLevelListenerWrapper> localUserAudioLevelListeners = new ArrayList<>();

    /**
     * The <code>Object</code> which synchronizes the access to {@link #localUserAudioLevelListeners}.
     */
    private final Object localUserAudioLevelListenersSyncRoot = new Object();

    /**
     * The levels map that we use to cache last measured audio levels for all streams associated with this mixer.
     */
    private final AudioLevelMap audioLevelCache = new AudioLevelMap();

    /**
     * The most recently measured level of the locally captured audio stream.
     */
    private int lastMeasuredLocalUserAudioLevel = 0;

    /**
     * The <code>List</code> of RTP extensions supported by this device (at the time of writing this
     * list is only filled for audio devices and is <code>null</code> otherwise).
     */
    private List<RTPExtension> rtpExtensions = null;

    /**
     * The <code>Map</code> where we store audio level dispatchers and the streams they are interested
     * in.
     */
    private final Map<ReceiveStream, AudioLevelEventDispatcher> streamAudioLevelListeners = new HashMap<>();

    /**
     * The <code>ReceiveStreamBufferListener</code> which gets notified when this
     * <code>MediaDevice</code> reads from the <code>CaptureDevice</code> to the <code>AudioMixer</code>
     */
    private ReceiveStreamBufferListener receiveStreamBufferListener;

    /**
     * Initializes a new <code>AudioMixerMediaDevice</code> instance which is to enable audio mixing on
     * a specific <code>AudioMediaDeviceImpl</code> .
     *
     * @param device the <code>AudioMediaDeviceImpl</code> which the new instance is to enable audio mixing on
     */
    public AudioMixerMediaDevice(AudioMediaDeviceImpl device)
    {
        /*
         * AudioMixer is initialized with a CaptureDevice so we have to be sure that the wrapped
         * device can provide one.
         */
        if (!device.getDirection().allowsSending())
            throw new IllegalArgumentException("device must be able to capture");

        this.device = device;
    }

    /**
     * Connects to a specific <code>CaptureDevice</code> given in the form of a <code>DataSource</code>.
     *
     * @param captureDevice the <code>CaptureDevice</code> to be connected to
     * @throws IOException if anything wrong happens while connecting to the specified <code>captureDevice</code>
     * @see AbstractMediaDevice#connect(DataSource)
     */
    @Override
    public void connect(DataSource captureDevice)
            throws IOException
    {
        DataSource effectiveCaptureDevice = captureDevice;

        /*
         * Unwrap wrappers of the captureDevice until AudioMixingPushBufferDataSource is found.
         */
        if (captureDevice instanceof PushBufferDataSourceDelegate<?>)
            captureDevice = ((PushBufferDataSourceDelegate<?>) captureDevice).getDataSource();

        /*
         * AudioMixingPushBufferDataSource is definitely not a CaptureDevice and does not need the
         * special connecting defined by AbstractMediaDevice and MediaDeviceImpl.
         */
        if (captureDevice instanceof AudioMixingPushBufferDataSource)
            effectiveCaptureDevice.connect();
        else
            device.connect(effectiveCaptureDevice);
    }

    /**
     * Creates a <code>DataSource</code> instance for this <code>MediaDevice</code> which gives access to
     * the captured media.
     *
     * @return a <code>DataSource</code> instance which gives access to the media captured by this
     * <code>MediaDevice</code>
     * @see AbstractMediaDevice#createOutputDataSource()
     */
    @Override
    public AudioMixingPushBufferDataSource createOutputDataSource()
    {
        return getAudioMixer().createOutDataSource();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Delegates to the {@link AbstractMediaDevice#createPlayer(DataSource)} implementation of the
     * <code>MediaDevice</code> on which this instance enables mixing i.e. {@link #getWrappedDevice()}.
     */
    @Override
    protected Processor createPlayer(DataSource dataSource)
            throws Exception
    {
        return device.createPlayer(dataSource);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Delegates to the {@link AbstractMediaDevice#createRenderer()} implementation of the
     * <code>MediaDevice</code> on which this instance enables mixing i.e. {@link #getWrappedDevice()}.
     */
    @Override
    protected Renderer createRenderer()
    {
        return device.createRenderer();
    }

    /**
     * Creates a new <code>MediaDeviceSession</code> instance which is to represent the use of this
     * <code>MediaDevice</code> by a <code>MediaStream</code>.
     *
     * @return a new <code>MediaDeviceSession</code> instance which is to represent the use of this
     * <code>MediaDevice</code> by a <code>MediaStream</code>
     * @see AbstractMediaDevice#createSession()
     */
    @Override
    public synchronized MediaDeviceSession createSession()
    {
        if (deviceSession == null)
            deviceSession = new AudioMixerMediaDeviceSession();
        return new MediaStreamMediaDeviceSession(deviceSession);
    }

    /**
     * Notifies the <code>SimpleAudioLevelListener</code>s registered with this instance about the
     * new/current audio level of the local media stream.
     *
     * @param level the new/current audio level of the local media stream.
     */
    private void fireLocalUserAudioLevelChanged(int level)
    {
        List<SimpleAudioLevelListenerWrapper> localUserAudioLevelListeners;

        synchronized (localUserAudioLevelListenersSyncRoot) {
            /*
             * It is safe to not copy the localUserAudioLevelListeners of this instance here
             * because it is a copy-on-write storage.
             */
            localUserAudioLevelListeners = this.localUserAudioLevelListeners;
        }

        /*
         * XXX These events are going to happen veeery often (~50 times per sec) and we'd like to
         * avoid creating an iterator every time.
         */
        int localUserAudioLevelListenerCount = localUserAudioLevelListeners.size();

        for (int i = 0; i < localUserAudioLevelListenerCount; i++) {
            localUserAudioLevelListeners.get(i).listener.audioLevelChanged(level);
        }
    }

    /**
     * Gets the <code>AudioMixer</code> which performs audio mixing in this <code>MediaDevice</code>
     * (and rather the session it represents). If it still does not exist, it is created.
     *
     * @return the <code>AudioMixer</code> which performs audio mixing in this <code>MediaDevice</code>
     * (and rather the session it represents)
     */
    private synchronized AudioMixer getAudioMixer()
    {
        if (audioMixer == null) {
            audioMixer = new AudioMixer(device.createCaptureDevice())
            {
                @Override
                protected void connect(DataSource dataSource, DataSource inputDataSource)
                        throws IOException
                {
                    /*
                     * CaptureDevice needs special connecting as defined by AbstractMediaDevice
                     * and, especially, MediaDeviceImpl.
                     */
                    if (inputDataSource == captureDevice)
                        AudioMixerMediaDevice.this.connect(dataSource);
                    else
                        super.connect(dataSource, inputDataSource);
                }

                @Override
                protected void read(PushBufferStream stream, Buffer buffer, DataSource dataSource)
                        throws IOException
                {
                    super.read(stream, buffer, dataSource);

                    /*
                     * XXX The audio read from the specified stream has not been made available to
                     * the mixing yet. Slow code here is likely to degrade the performance of the
                     * whole mixer.
                     */

                    if (dataSource == captureDevice) {
                        /*
                         * The audio of the very CaptureDevice to be contributed to the mix.
                         */
                        synchronized (localUserAudioLevelListenersSyncRoot) {
                            if (localUserAudioLevelListeners.isEmpty())
                                return;
                        }
                        localUserAudioLevelDispatcher.addData(buffer);

                    }
                    else if (dataSource instanceof ReceiveStreamPushBufferDataSource) {
                        /*
                         * The audio of a ReceiveStream to be contributed to the mix.
                         */
                        ReceiveStream receiveStream
                                = ((ReceiveStreamPushBufferDataSource) dataSource).getReceiveStream();
                        AudioLevelEventDispatcher streamEventDispatcher;

                        synchronized (streamAudioLevelListeners) {
                            streamEventDispatcher = streamAudioLevelListeners.get(receiveStream);
                        }
                        if ((streamEventDispatcher != null) && !buffer.isDiscard()
                                && (buffer.getLength() > 0) && (buffer.getData() != null)) {
                            streamEventDispatcher.addData(buffer);
                        }

                        ReceiveStreamBufferListener receiveStreamBufferListener
                                = AudioMixerMediaDevice.this.receiveStreamBufferListener;

                        if ((receiveStreamBufferListener != null)
                                && !buffer.isDiscard()
                                && (buffer.getLength() > 0)
                                && (buffer.getData() != null)) {
                            receiveStreamBufferListener.bufferReceived(receiveStream, buffer);
                        }
                    }
                }
            };
        }
        return audioMixer;
    }

    /**
     * Returns the <code>MediaDirection</code> supported by this device.
     *
     * @return {@link MediaDirection#SENDONLY} if this is a read-only device,
     * {@link MediaDirection#RECVONLY} if this is a write-only device or
     * {@link MediaDirection#SENDRECV} if this <code>MediaDevice</code> can both capture and
     * render media
     * @see MediaDevice#getDirection()
     */
    public MediaDirection getDirection()
    {
        return device.getDirection();
    }

    /**
     * Gets the <code>MediaFormat</code> in which this <t>MediaDevice</code> captures media.
     *
     * @return the <code>MediaFormat</code> in which this <code>MediaDevice</code> captures media
     * @see MediaDevice#getFormat()
     */
    public MediaFormat getFormat()
    {
        return device.getFormat();
    }

    /**
     * Gets the <code>MediaType</code> that this device supports.
     *
     * @return {@link MediaType#AUDIO} if this is an audio device or {@link MediaType#VIDEO} if
     * this is a video device
     * @see MediaDevice#getMediaType()
     */
    public MediaType getMediaType()
    {
        return device.getMediaType();
    }

    /**
     * Returns a <code>List</code> containing (at the time of writing) a single extension descriptor
     * indicating <code>SENDRECV</code> for mixer-to-client audio levels.
     *
     * @return a <code>List</code> containing the <code>CSRC_AUDIO_LEVEL_URN</code> extension descriptor.
     */
    @Override
    public List<RTPExtension> getSupportedExtensions()
    {
        if (rtpExtensions == null) {
            rtpExtensions = new ArrayList<RTPExtension>(2);

            URI csrcAudioLevelURN;
            URI ssrcAudioLevelURN;
            try {
                csrcAudioLevelURN = new URI(RTPExtension.CSRC_AUDIO_LEVEL_URN);
                ssrcAudioLevelURN = new URI(RTPExtension.SSRC_AUDIO_LEVEL_URN);
            } catch (URISyntaxException e) {
                // can't happen since CSRC_AUDIO_LEVEL_URN is a valid URI and never changes.
                csrcAudioLevelURN = null;
                ssrcAudioLevelURN = null;
                Timber.i(e, "Aha! Someone messed with the source!");
            }

            if (csrcAudioLevelURN != null) {
                rtpExtensions.add(new RTPExtension(csrcAudioLevelURN, MediaDirection.SENDRECV));
            }
            if (ssrcAudioLevelURN != null) {
                rtpExtensions.add(new RTPExtension(ssrcAudioLevelURN, MediaDirection.SENDRECV));
            }
        }
        return rtpExtensions;
    }

    /**
     * Gets the list of <code>MediaFormat</code>s supported by this <code>MediaDevice</code>.
     *
     * @param sendPreset not used
     * @param receivePreset not used
     * @return the list of <code>MediaFormat</code>s supported by this <code>MediaDevice</code>
     * @see MediaDevice#getSupportedFormats()
     */
    public List<MediaFormat> getSupportedFormats(QualityPreset sendPreset, QualityPreset receivePreset)
    {
        return device.getSupportedFormats();
    }

    /**
     * Set the listener which gets notified when this <code>MediaDevice</code>
     * reads data from a <code>ReceiveStream</code>
     *
     * @param listener the <code>ReceiveStreamBufferListener</code> which gets notified
     */
    public void setReceiveStreamBufferListener(ReceiveStreamBufferListener listener)
    {
        this.receiveStreamBufferListener = listener;
    }

    /**
     * Gets the list of <code>MediaFormat</code>s supported by this <code>MediaDevice</code> and enabled in
     * <code>encodingConfiguration</code>.
     *
     * @param sendPreset not used
     * @param receivePreset not used
     * @param encodingConfiguration the <code>EncodingConfiguration</code> instance to use
     * @return the list of <code>MediaFormat</code>s supported by this <code>MediaDevice</code> and enabled
     * in <code>encodingConfiguration</code>.
     * @see MediaDevice#getSupportedFormats(QualityPreset, QualityPreset, EncodingConfiguration)
     */
    public List<MediaFormat> getSupportedFormats(QualityPreset sendPreset,
            QualityPreset receivePreset, EncodingConfiguration encodingConfiguration)
    {
        return device.getSupportedFormats(encodingConfiguration);
    }

    /**
     * Gets the actual <code>MediaDevice</code> which this <code>MediaDevice</code> is effectively built on
     * top of and forwarding to.
     *
     * @return the actual <code>MediaDevice</code> which this <code>MediaDevice</code> is effectively built
     * on top of and forwarding to
     * @see MediaDeviceWrapper#getWrappedDevice()
     */
    public MediaDevice getWrappedDevice()
    {
        return device;
    }

    /**
     * Removes the <code>DataSource</code> accepted by a specific <code>DataSourceFilter</code> from the
     * list of input <code>DataSource</code> of the <code>AudioMixer</code> of this
     * <code>AudioMixerMediaDevice</code> from which it reads audio to be mixed.
     *
     * @param dataSourceFilter the <code>DataSourceFilter</code> which selects the <code>DataSource</code>s to be removed
     */
    private void removeInputDataSources(DataSourceFilter dataSourceFilter)
    {
        AudioMixer audioMixer = this.audioMixer;
        if (audioMixer != null)
            audioMixer.removeInDataSources(dataSourceFilter);
    }

    /**
     * Represents the one and only <code>MediaDeviceSession</code> with the <code>MediaDevice</code> of
     * this <code>AudioMixer</code>
     */
    private class AudioMixerMediaDeviceSession extends MediaDeviceSession
    {
        /**
         * The list of <code>MediaDeviceSession</code>s of <code>MediaStream</code>s which use this
         * <code>AudioMixer</code>.
         */
        private final List<MediaStreamMediaDeviceSession> mediaStreamMediaDeviceSessions = new LinkedList<>();

        /**
         * The <code>VolumeControl</code> which is to control the volume (level) of the audio (to be)
         * played back by this instance.
         */
        private VolumeControl outputVolumeControl;

        /**
         * Initializes a new <code>AudioMixingMediaDeviceSession</code> which is to represent the
         * <code>MediaDeviceSession</code> of this <code>AudioMixer</code> with its <code>MediaDevice</code>
         */
        public AudioMixerMediaDeviceSession()
        {
            super(AudioMixerMediaDevice.this);
        }

        /**
         * Adds <code>l</code> to the list of listeners that are being notified of new local audio
         * levels as they change. If <code>l</code> is added multiple times it would only be registered  once.
         *
         * @param l the listener we'd like to add.
         */
        void addLocalUserAudioLevelListener(SimpleAudioLevelListener l)
        {
            // If the listener is null, we have nothing more to do here.
            if (l == null)
                return;

            synchronized (localUserAudioLevelListenersSyncRoot) {
                // if this is the first listener that we are seeing then we also
                // need to create the dispatcher.
                if (localUserAudioLevelListeners.isEmpty()) {
                    localUserAudioLevelDispatcher.setAudioLevelListener(localUserAudioLevelDelegate);
                }

                // check if this listener has already been added.
                SimpleAudioLevelListenerWrapper wrapper = new SimpleAudioLevelListenerWrapper(l);
                int index = localUserAudioLevelListeners.indexOf(wrapper);

                if (index != -1) {
                    wrapper = localUserAudioLevelListeners.get(index);
                    wrapper.referenceCount++;
                }
                else {
                    /*
                     * XXX localUserAudioLevelListeners must be a copy-on-write storage so that
                     * firing events to its SimpleAudioLevelListeners can happen outside a block
                     * synchronized by localUserAudioLevelListenersSyncRoot and thus reduce the
                     * chances for a deadlock (which was, otherwise, observed in practice).
                     */
                    localUserAudioLevelListeners = new ArrayList<>(localUserAudioLevelListeners);
                    localUserAudioLevelListeners.add(wrapper);
                }
            }
        }

        /**
         * Adds a specific <code>MediaStreamMediaDeviceSession</code> to the mix represented by this
         * instance so that it knows when it is in use.
         *
         * @param mediaStreamMediaDeviceSession the <code>MediaStreamMediaDeviceSession</code> to be added to the mix represented by
         * this instance
         */
        void addMediaStreamMediaDeviceSession(
                MediaStreamMediaDeviceSession mediaStreamMediaDeviceSession)
        {
            if (mediaStreamMediaDeviceSession == null)
                throw new NullPointerException("mediaStreamMediaDeviceSession");

            synchronized (mediaStreamMediaDeviceSessions) {
                if (!mediaStreamMediaDeviceSessions.contains(mediaStreamMediaDeviceSession))
                    mediaStreamMediaDeviceSessions.add(mediaStreamMediaDeviceSession);
            }
        }

        /**
         * Adds a specific <code>DataSource</code> providing remote audio to the mix produced by the
         * associated <code>MediaDevice</code>.
         *
         * @param playbackDataSource the <code>DataSource</code> providing remote audio to be added to the mix produced by
         * the associated <code>MediaDevice</code>
         */
        @Override
        public void addPlaybackDataSource(DataSource playbackDataSource)
        {
            /*
             * We don't play back the contributions of the conference members separately, we have a
             * single playback of the mix of all contributions but ours.
             */
            super.addPlaybackDataSource(getCaptureDevice());
        }

        /**
         * Adds a specific <code>ReceiveStream</code> to the list of <code>ReceiveStream</code>s known to
         * this instance to be contributing audio to the mix produced by its associated
         * <code>AudioMixer</code>.
         *
         * @param receiveStream the <code>ReceiveStream</code> to be added to the list of <code>ReceiveStream</code>s
         * known to this instance to be contributing audio to the mix produced by its
         * associated <code>AudioMixer</code>
         */
        @Override
        public void addReceiveStream(ReceiveStream receiveStream)
        {
            addSSRC(0xFFFFFFFFL & receiveStream.getSSRC());
        }

        /**
         * Creates the <code>DataSource</code> that this instance is to read captured media from. Since
         * this is the <code>MediaDeviceSession</code> of this <code>AudioMixer</code> with its
         * <code>MediaDevice</code>, returns the <code>localOutputDataSource</code> of the
         * <code>AudioMixer</code> i.e. the <code>DataSource</code> which represents the mix of all
         * <code>ReceiveStream</code>s and excludes the captured data from the <code>MediaDevice</code> of
         * the <code>AudioMixer</code>.
         *
         * @return the <code>DataSource</code> that this instance is to read captured media from
         * @see MediaDeviceSession#createCaptureDevice()
         */
        @Override
        protected DataSource createCaptureDevice()
        {
            return getAudioMixer().getLocalOutDataSource();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected Player createPlayer(DataSource dataSource)
        {
            /*
             * TODO AudioMixerMediaDevice wraps a MediaDevice so AudioMixerMediaDeviceSession
             * should wrap a MediaDeviceSession of that same wrapped MediaDevice.
             */
            return super.createPlayer(dataSource);
        }

        /**
         * Sets the <code>VolumeControl</code> which is to control the volume (level) of the audio (to
         * be) played back by this instance.
         *
         * @param outputVolumeControl the <code>VolumeControl</code> which is to be control the volume (level) of the audio
         * (to be) played back by this instance
         */
        void setOutputVolumeControl(VolumeControl outputVolumeControl)
        {
            this.outputVolumeControl = outputVolumeControl;
        }

        /**
         * Sets <code>listener</code> as the list of listeners that will receive notifications of audio
         * level event changes in the data arriving from <code>stream</code>.
         *
         * @param stream the stream that <code>l</code> would like to register as an audio level listener for.
         * @param listener the listener we'd like to register for notifications from <code>stream</code>.
         */
        void setStreamAudioLevelListener(ReceiveStream stream, SimpleAudioLevelListener listener)
        {
            synchronized (streamAudioLevelListeners) {
                AudioLevelEventDispatcher dispatcher = streamAudioLevelListeners.get(stream);

                if (listener == null) {
                    if (dispatcher != null) {
                        try {
                            dispatcher.setAudioLevelListener(null);
                            dispatcher.setAudioLevelCache(null, -1);
                        } finally {
                            streamAudioLevelListeners.remove(stream);
                        }
                    }
                }
                else {
                    if (dispatcher == null) {
                        dispatcher = new AudioLevelEventDispatcher(
                                "Stream Audio Level Dispatcher (Mixer Edition)");
                        dispatcher.setAudioLevelCache(audioLevelCache, 0xFFFFFFFFL & stream.getSSRC());
                        streamAudioLevelListeners.put(stream, dispatcher);
                    }
                    dispatcher.setAudioLevelListener(listener);
                }
            }
        }

        /**
         * {@inheritDoc}
         * <p>
         * Overrides the super implementation in order to configure the <code>VolumeControl</code> of
         * the returned <code>Renderer</code> for the purposes of having call/telephony
         * conference-specific volume (levels).
         */
        @Override
        protected Renderer createRenderer(Player player, TrackControl trackControl)
        {
            Renderer renderer = super.createRenderer(player, trackControl);
            if (renderer != null) {
                AudioMediaDeviceSession.setVolumeControl(renderer, outputVolumeControl);
            }
            return renderer;
        }

        /**
         * Removes <code>l</code> from the list of listeners that are being notified of local audio
         * levels.If <code>l</code> is not in the list, the method has no effect.
         *
         * @param l the listener we'd like to remove.
         */
        void removeLocalUserAudioLevelListener(SimpleAudioLevelListener l)
        {
            synchronized (localUserAudioLevelListenersSyncRoot) {
                // check if this listener has already been added.
                int index = localUserAudioLevelListeners.indexOf(new SimpleAudioLevelListenerWrapper(l));

                if (index != -1) {
                    SimpleAudioLevelListenerWrapper wrapper
                            = localUserAudioLevelListeners.get(index);

                    if (wrapper.referenceCount > 1)
                        wrapper.referenceCount--;
                    else {
                        /*
                         * XXX localUserAudioLevelListeners must be a copy-on-write storage so that
                         * firing events to its SimpleAudioLevelListeners can happen outside a
                         * block
                         * synchronized by localUserAudioLevelListenersSyncRoot and thus reduce the
                         * chances for a deadlock (whic was, otherwise, observed in practice).
                         */
                        localUserAudioLevelListeners
                                = new ArrayList<>(localUserAudioLevelListeners);
                        localUserAudioLevelListeners.remove(wrapper);
                    }
                }

                // if this was the last listener then we also need to remove the dispatcher
                if (localUserAudioLevelListeners.isEmpty())
                    localUserAudioLevelDispatcher.setAudioLevelListener(null);
            }
        }

        /**
         * Removes a specific <code>MediaStreamMediaDeviceSession</code> from the mix represented by
         * this instance. When the last <code>MediaStreamMediaDeviceSession</code> is removed from this
         * instance, it is no longer in use and closes itself thus signaling to its
         * <code>MediaDevice</code> that it is no longer in use.
         *
         * @param mediaStreamMediaDeviceSession the <code>MediaStreamMediaDeviceSession</code> to be removed from the mix represented
         * by this instance
         */
        void removeMediaStreamMediaDeviceSession(
                MediaStreamMediaDeviceSession mediaStreamMediaDeviceSession)
        {
            if (mediaStreamMediaDeviceSession != null) {
                synchronized (mediaStreamMediaDeviceSessions) {
                    if (mediaStreamMediaDeviceSessions.remove(mediaStreamMediaDeviceSession)
                            && mediaStreamMediaDeviceSessions.isEmpty())
                        close(MediaDirection.SENDRECV);
                }
            }
        }

        /**
         * Removes a specific <code>DataSource</code> providing remote audio from the mix produced by
         * the associated <code>AudioMixer</code>.
         *
         * @param playbackDataSource the <code>DataSource</code> providing remote audio to be removed from the mix produced
         * by the associated <code>AudioMixer</code>
         */
        @Override
        public void removePlaybackDataSource(final DataSource playbackDataSource)
        {
            removeInputDataSources(new DataSourceFilter()
            {
                @Override
                public boolean accept(DataSource dataSource)
                {
                    return dataSource.equals(playbackDataSource);
                }
            });
        }

        /**
         * Removes a specific <code>ReceiveStream</code> from the list of <code>ReceiveStream</code>s known
         * to this instance to be contributing audio to the mix produced by its associated
         * <code>AudioMixer</code>.
         *
         * @param receiveStream the <code>ReceiveStream</code> to be removed from the list of <code>ReceiveStream</code>s
         * known to this instance to be contributing audio to the mix produced by its
         * associated <code>AudioMixer</code>
         */
        @Override
        public void removeReceiveStream(ReceiveStream receiveStream)
        {
            long ssrc = 0xFFFFFFFFL & receiveStream.getSSRC();
            removeSSRC(ssrc);

            // make sure we no longer cache levels for that stream.
            audioLevelCache.removeLevel(ssrc);
        }
    }

    /**
     * Represents the work of a <code>MediaStream</code> with the <code>MediaDevice</code> of an
     * <code>AudioMixer</code> and the contribution of that <code>MediaStream</code> to the mix.
     */
    private static class MediaStreamMediaDeviceSession extends AudioMediaDeviceSession
            implements PropertyChangeListener
    {
        /**
         * The <code>MediaDeviceSession</code> of the <code>AudioMixer</code> that this instance exposes to
         * a <code>MediaStream</code>. While there are multiple
         * <code>MediaStreamMediaDeviceSession<code>s each servicing a specific
         * <code>MediaStream</code>, they all share and delegate to one and the same
         * <code>AudioMixerMediaDeviceSession</code> so that they all contribute to the mix.
         */
        private final AudioMixerMediaDeviceSession audioMixerMediaDeviceSession;

        /**
         * We use this field to keep a reference to the listener that we've registered with the
         * audio mixer for local audio level notifications. We use this reference so that we could
         * unregister it if someone resets it or sets it to <code>null</code>.
         */
        private SimpleAudioLevelListener localUserAudioLevelListener = null;

        /**
         * We use this field to keep a reference to the listener that we've registered with the
         * audio mixer for stream audio level notifications. We use this reference so because at
         * the time we get it from the <code>MediaStream</code> it might be too early to register it with
         * the mixer as it is like that we don't have a receive stream yet. If that's the case, we
         * hold on to the listener and register it only when we get the <code>ReceiveStream</code>.
         */
        private SimpleAudioLevelListener streamAudioLevelListener = null;

        /**
         * The <code>Object</code> that we use to lock operations on <code>streamAudioLevelListener</code>.
         */
        private final Object streamAudioLevelListenerLock = new Object();

        /**
         * Initializes a new <code>MediaStreamMediaDeviceSession</code> which is to represent the work
         * of a <code>MediaStream</code> with the <code>MediaDevice</code> of this <code>AudioMixer</code> and
         * its contribution to the mix.
         *
         * @param audioMixerMediaDeviceSession the <code>MediaDeviceSession</code> of the <code>AudioMixer</code> with its
         * <code>MediaDevice</code> which the new instance is to delegate to in order to
         * contribute to the mix
         */
        public MediaStreamMediaDeviceSession(
                AudioMixerMediaDeviceSession audioMixerMediaDeviceSession)
        {
            super(audioMixerMediaDeviceSession.getDevice());

            this.audioMixerMediaDeviceSession = audioMixerMediaDeviceSession;
            this.audioMixerMediaDeviceSession.addMediaStreamMediaDeviceSession(this);
            this.audioMixerMediaDeviceSession.addPropertyChangeListener(this);
        }

        /**
         * Releases the resources allocated by this instance in the course of its execution and
         * prepares it to be garbage collected.
         *
         * @see MediaDeviceSession#close(MediaDirection)
         */
        @Override
        public void close(MediaDirection direction)
        {
            try {
                super.close(direction);
            } finally {
                audioMixerMediaDeviceSession.removeMediaStreamMediaDeviceSession(this);
            }
        }

        /**
         * Creates a new <code>Player</code> for a specific <code>DataSource</code> so that it is played
         * back on the <code>MediaDevice</code> represented by this instance.
         *
         * @param dataSource the <code>DataSource</code> to create a new <code>Player</code> for
         * @return a new <code>Player</code> for the specified <code>dataSource</code>
         * @see MediaDeviceSession#createPlayer(DataSource)
         */
        @Override
        protected Player createPlayer(DataSource dataSource)
        {
            /*
             * We don't want the contribution of each conference member played back separately, we
             * want the one and only mix of all contributions but ours to be played back once for
             * all of them.
             */
            return null;
        }

        /**
         * Returns the list of SSRC identifiers that are directly contributing to the media flows
         * that we are sending out. Note that since this is a pseudo device we would simply be
         * delegating the call to the corresponding method of the master mixer device session.
         *
         * @return a <code>long[]</code> array of SSRC identifiers that are currently contributing to
         * the mixer encapsulated by this device session.
         */
        @Override
        public long[] getRemoteSSRCList()
        {
            return audioMixerMediaDeviceSession.getRemoteSSRCList();
        }

        /**
         * Notifies this <code>MediaDeviceSession</code> that a <code>DataSource</code> has been added for
         * playback on the represented <code>MediaDevice</code>.
         *
         * @param playbackDataSource the <code>DataSource</code> which has been added for playback on the represented
         * <code>MediaDevice</code>
         * @see MediaDeviceSession#playbackDataSourceAdded(DataSource)
         */
        @Override
        protected void playbackDataSourceAdded(DataSource playbackDataSource)
        {
            super.playbackDataSourceAdded(playbackDataSource);
            DataSource captureDevice = getCaptureDevice();

            /*
             * Unwrap wrappers of the captureDevice until AudioMixingPushBufferDataSource is found.
             */
            if (captureDevice instanceof PushBufferDataSourceDelegate<?>)
                captureDevice = ((PushBufferDataSourceDelegate<?>) captureDevice).getDataSource();
            if (captureDevice instanceof AudioMixingPushBufferDataSource)
                ((AudioMixingPushBufferDataSource) captureDevice)
                        .addInDataSource(playbackDataSource);
            audioMixerMediaDeviceSession.addPlaybackDataSource(playbackDataSource);
        }

        /**
         * Notifies this <code>MediaDeviceSession</code> that a <code>DataSource</code> has been removed
         * from playback on the represented <code>MediaDevice</code>.
         *
         * @param playbackDataSource the <code>DataSource</code> which has been removed from playback on the represented
         * <code>MediaDevice</code>
         * @see MediaDeviceSession#playbackDataSourceRemoved(DataSource)
         */
        @Override
        protected void playbackDataSourceRemoved(DataSource playbackDataSource)
        {
            super.playbackDataSourceRemoved(playbackDataSource);
            audioMixerMediaDeviceSession.removePlaybackDataSource(playbackDataSource);
        }

        /**
         * Notifies this <code>MediaDeviceSession</code> that a <code>DataSource</code> has been updated.
         *
         * @param playbackDataSource the <code>DataSource</code> which has been updated.
         * @see MediaDeviceSession#playbackDataSourceUpdated(DataSource)
         */
        @Override
        protected void playbackDataSourceUpdated(DataSource playbackDataSource)
        {
            super.playbackDataSourceUpdated(playbackDataSource);
            DataSource captureDevice = getCaptureDevice();

            /*
             * Unwrap wrappers of the captureDevice until AudioMixingPushBufferDataSource is found.
             */
            if (captureDevice instanceof PushBufferDataSourceDelegate<?>)
                captureDevice = ((PushBufferDataSourceDelegate<?>) captureDevice).getDataSource();
            if (captureDevice instanceof AudioMixingPushBufferDataSource) {
                ((AudioMixingPushBufferDataSource) captureDevice)
                        .updateInDataSource(playbackDataSource);
            }
        }

        /**
         * The method relays <code>PropertyChangeEvent</code>s indicating a change in the SSRC_LIST in
         * the encapsulated mixer device so that the <code>MediaStream</code> that uses this device
         * session can update its CSRC list.
         *
         * @param evt that <code>PropertyChangeEvent</code> whose old and new value we will be relaying to
         * the stream.
         */
        public void propertyChange(PropertyChangeEvent evt)
        {
            if (MediaDeviceSession.SSRC_LIST.equals(evt.getPropertyName())) {
                firePropertyChange(MediaDeviceSession.SSRC_LIST, evt.getOldValue(),
                        evt.getNewValue());
            }
        }

        /**
         * Notifies this instance that a specific <code>ReceiveStream</code> has been added to the list
         * of playbacks of <code>ReceiveStream</code>s and/or <code>DataSource</code>s performed by
         * respective <code>Player</code>s on the <code>MediaDevice</code> represented by this instance.
         *
         * @param receiveStream the <code>ReceiveStream</code> which has been added to the list of playbacks of
         * <code>ReceiveStream</code>s and/or <code>DataSource</code>s performed by respective
         * <code>Player</code>s on the <code>MediaDevice</code> represented by this instance
         */
        @Override
        protected void receiveStreamAdded(ReceiveStream receiveStream)
        {
            super.receiveStreamAdded(receiveStream);

            /*
             * If someone registered a stream level listener, we can now add it since we have the
             * stream that it's supposed to listen to.
             */
            synchronized (streamAudioLevelListenerLock) {
                if (streamAudioLevelListener != null)
                    audioMixerMediaDeviceSession.setStreamAudioLevelListener(
                            receiveStream, streamAudioLevelListener);
            }

            audioMixerMediaDeviceSession.addReceiveStream(receiveStream);
        }

        /**
         * Notifies this instance that a specific <code>ReceiveStream</code> has been removed from the
         * list of playbacks of <code>ReceiveStream</code>s and/or <code>DataSource</code>s performed by
         * respective <code>Player</code>s on the <code>MediaDevice</code> represented by this instance.
         *
         * @param receiveStream the <code>ReceiveStream</code> which has been removed from the list of playbacks of
         * <code>ReceiveStream</code> s and/or <code>DataSource</code>s performed by respective
         * <code>Player</code>s on the <code>MediaDevice</code> represented by this instance
         */
        @Override
        protected void receiveStreamRemoved(ReceiveStream receiveStream)
        {
            super.receiveStreamRemoved(receiveStream);
            audioMixerMediaDeviceSession.removeReceiveStream(receiveStream);
        }

        /**
         * Override it here cause we won't register effects to that stream cause we already have
         * one.
         *
         * @param processor the processor.
         */
        @Override
        protected void registerLocalUserAudioLevelEffect(Processor processor)
        {
        }

        /**
         * Adds a specific <code>SoundLevelListener</code> to the list of listeners interested in and
         * notified about changes in local sound level related information.
         *
         * @param l the <code>SoundLevelListener</code> to add
         */
        @Override
        public void setLocalUserAudioLevelListener(SimpleAudioLevelListener l)
        {
            if (localUserAudioLevelListener != null) {
                audioMixerMediaDeviceSession.removeLocalUserAudioLevelListener(localUserAudioLevelListener);
                localUserAudioLevelListener = null;
            }

            if (l != null) {
                localUserAudioLevelListener = l;

                // add the listener only if we are not muted this happens when holding a
                // conversation, stream is muted and when recreated listener is again set
                if (!isMute()) {
                    audioMixerMediaDeviceSession.addLocalUserAudioLevelListener(l);
                }
            }
        }

        /**
         * {@inheritDoc}
         * <p>
         * Overrides the super implementation to redirect/delegate the invocation to the
         * master/audioMixerMediaDeviceSession because <code>MediaStreamMediaDeviceSession</code> does
         * not perform playback/rendering.
         */
        @Override
        public void setOutputVolumeControl(VolumeControl outputVolumeControl)
        {
            audioMixerMediaDeviceSession.setOutputVolumeControl(outputVolumeControl);
        }

        /**
         * Adds <code>listener</code> to the list of <code>SimpleAudioLevelListener</code>s registered with
         * the mixer session that this "slave session" encapsulates. This class does not keep a
         * reference to <code>listener</code>.
         *
         * @param listener the <code>SimpleAudioLevelListener</code> that we are to pass to the mixer device
         * session or <code>null</code> if we are trying to unregister it.
         */
        @Override
        public void setStreamAudioLevelListener(SimpleAudioLevelListener listener)
        {
            synchronized (streamAudioLevelListenerLock) {
                streamAudioLevelListener = listener;

                for (ReceiveStream receiveStream : getReceiveStreams()) {
                    /*
                     * If we already have a ReceiveStream, register the listener with the mixer;
                     * otherwise, wait till we get one.
                     */
                    audioMixerMediaDeviceSession.setStreamAudioLevelListener(
                            receiveStream, streamAudioLevelListener);
                }
            }
        }

        /**
         * Returns the last audio level that was measured by the underlying mixer for the specified
         * <code>csrc</code>.
         *
         * @param csrc the CSRC ID whose last measured audio level we'd like to retrieve.
         * @return the audio level that was last measured by the underlying mixer for the specified
         * <code>csrc</code> or <code>-1</code> if the <code>csrc</code> does not belong to neither of
         * the conference participants.
         */
        @Override
        public int getLastMeasuredAudioLevel(long csrc)
        {
            return ((AudioMixerMediaDevice) getDevice()).audioLevelCache.getLevel(csrc);
        }

        /**
         * Returns the last audio level that was measured by the underlying mixer for local user.
         *
         * @return the audio level that was last measured for the local user.
         */
        @Override
        public int getLastMeasuredLocalUserAudioLevel()
        {
            return ((AudioMixerMediaDevice) getDevice()).lastMeasuredLocalUserAudioLevel;
        }

        /**
         * Sets the indicator which determines whether this <code>MediaDeviceSession</code> is set to
         * output "silence" instead of the actual media fed from its <code>CaptureDevice</code>. If we
         * are muted we just remove the local level listener from the session.
         *
         * @param mute <code>true</code> to set this <code>MediaDeviceSession</code> to output "silence" instead
         * of the actual media fed from its <code>CaptureDevice</code>; otherwise, <code>false</code>
         */
        @Override
        public void setMute(boolean mute)
        {
            boolean oldValue = isMute();
            super.setMute(mute);
            boolean newValue = isMute();

            if (oldValue != newValue) {
                if (newValue) {
                    audioMixerMediaDeviceSession
                            .removeLocalUserAudioLevelListener(localUserAudioLevelListener);
                }
                else {
                    audioMixerMediaDeviceSession
                            .addLocalUserAudioLevelListener(localUserAudioLevelListener);
                }
            }
        }
    }

    /**
     * A very lightweight wrapper that allows us to track the number of times that a particular
     * listener was added.
     */
    private static class SimpleAudioLevelListenerWrapper
    {
        /**
         * The listener being wrapped by this wrapper.
         */
        public final SimpleAudioLevelListener listener;

        /**
         * The number of times this listener has been added.
         */
        int referenceCount;

        /**
         * Creates a wrapper of the <code>l</code> listener.
         *
         * @param l the listener we'd like to wrap;
         */
        public SimpleAudioLevelListenerWrapper(SimpleAudioLevelListener l)
        {
            this.listener = l;
            this.referenceCount = 1;
        }

        /**
         * Returns <code>true</code> if <code>obj</code> is a wrapping the same listener as ours.
         *
         * @param obj the wrapper we'd like to compare to this instance
         * @return <code>true</code> if <code>obj</code> is a wrapping the same listener as ours.
         */
        @Override
        public boolean equals(Object obj)
        {
            return (obj instanceof SimpleAudioLevelListenerWrapper)
                    && ((SimpleAudioLevelListenerWrapper) obj).listener == listener;
        }

        /**
         * Returns a hash code value for this instance for the benefit of hashtables.
         *
         * @return a hash code value for this instance for the benefit of hashtables
         */
        @Override
        public int hashCode()
        {
            /*
             * Equality is based on the listener field only so its hashCode is enough. Besides,
             * it's the only immutable of this instance i.e. the only field appropriate for the
             * calculation of the hashCode.
             */
            return listener.hashCode();
        }
    }

    /**
     * Returns the <code>TranscodingDataSource</code> associated with <code>inputDataSource</code> in this
     * object's <code>AudioMixer</code>.
     *
     * @param inputDataSource the <code>DataSource</code> to search for
     * @return Returns the <code>TranscodingDataSource</code> associated with <code>inputDataSource</code>
     * in this object's <code>AudioMixer</code>
     * @see AudioMixer#getTranscodingDataSource(javax.media.protocol.DataSource)
     */
    public TranscodingDataSource getTranscodingDataSource(DataSource inputDataSource)
    {
        return getAudioMixer().getTranscodingDataSource(inputDataSource);
    }
}
