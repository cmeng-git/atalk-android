/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia;

import com.sun.media.util.Registry;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.impl.neomedia.codec.EncodingConfigurationConfigImpl;
import org.atalk.impl.neomedia.codec.EncodingConfigurationImpl;
import org.atalk.impl.neomedia.codec.FFmpeg;
import org.atalk.impl.neomedia.codec.FMJPlugInConfiguration;
import org.atalk.impl.neomedia.codec.video.AVFrameFormat;
import org.atalk.impl.neomedia.codec.video.HFlip;
import org.atalk.impl.neomedia.codec.video.SwScale;
import org.atalk.impl.neomedia.device.AudioMediaDeviceImpl;
import org.atalk.impl.neomedia.device.AudioMixerMediaDevice;
import org.atalk.impl.neomedia.device.AudioSystem;
import org.atalk.impl.neomedia.device.DeviceConfiguration;
import org.atalk.impl.neomedia.device.DeviceSystem;
import org.atalk.impl.neomedia.device.MediaDeviceImpl;
import org.atalk.impl.neomedia.device.ScreenDeviceImpl;
import org.atalk.impl.neomedia.device.VideoTranslatorMediaDevice;
import org.atalk.impl.neomedia.format.MediaFormatFactoryImpl;
import org.atalk.impl.neomedia.format.MediaFormatImpl;
import org.atalk.impl.neomedia.format.VideoMediaFormatImpl;
import org.atalk.impl.neomedia.recording.RecorderEventHandlerJSONImpl;
import org.atalk.impl.neomedia.recording.RecorderImpl;
import org.atalk.impl.neomedia.recording.RecorderRtpImpl;
import org.atalk.impl.neomedia.rtp.translator.RTPTranslatorImpl;
import org.atalk.impl.neomedia.transform.dtls.DtlsControlImpl;
import org.atalk.impl.neomedia.transform.sdes.SDesControlImpl;
import org.atalk.impl.neomedia.transform.zrtp.ZrtpControlImpl;
import org.atalk.service.configuration.ConfigurationService;
import org.atalk.service.libjitsi.LibJitsi;
import org.atalk.service.neomedia.BasicVolumeControl;
import org.atalk.service.neomedia.MediaService;
import org.atalk.service.neomedia.MediaStream;
import org.atalk.service.neomedia.MediaUseCase;
import org.atalk.service.neomedia.RTPTranslator;
import org.atalk.service.neomedia.SrtpControl;
import org.atalk.service.neomedia.SrtpControlType;
import org.atalk.service.neomedia.StreamConnector;
import org.atalk.service.neomedia.VolumeControl;
import org.atalk.service.neomedia.codec.EncodingConfiguration;
import org.atalk.service.neomedia.device.MediaDevice;
import org.atalk.service.neomedia.device.ScreenDevice;
import org.atalk.service.neomedia.format.MediaFormat;
import org.atalk.service.neomedia.format.MediaFormatFactory;
import org.atalk.service.neomedia.recording.Recorder;
import org.atalk.service.neomedia.recording.RecorderEventHandler;
import org.atalk.util.OSUtils;
import org.atalk.util.swing.VideoContainer;
import org.atalk.util.MediaType;
import org.atalk.util.event.PropertyChangeNotifier;
import org.json.JSONObject;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.media.CaptureDeviceInfo;
import javax.media.Codec;
import javax.media.ConfigureCompleteEvent;
import javax.media.ControllerEvent;
import javax.media.ControllerListener;
import javax.media.Format;
import javax.media.Manager;
import javax.media.MediaLocator;
import javax.media.NotConfiguredError;
import javax.media.Player;
import javax.media.Processor;
import javax.media.RealizeCompleteEvent;
import javax.media.UnsupportedPlugInException;
import javax.media.control.TrackControl;
import javax.media.format.RGBFormat;
import javax.media.protocol.DataSource;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import timber.log.Timber;

/**
 * Implements <code>MediaService</code> for JMF.
 *
 * @author Lyubomir Marinov
 * @author Dmitri Melnikov
 * @author Eng Chong Meng
 * @author MilanKral
 */
public class MediaServiceImpl extends PropertyChangeNotifier implements MediaService
{
    /**
     * The name of the <code>boolean</code> <code>ConfigurationService</code> property which indicates
     * whether the detection of audio <code>CaptureDevice</code>s is to be disabled. The default value
     * is <code>false</code> i.e. the audio <code>CaptureDevice</code>s are detected.
     */
    public static final String DISABLE_AUDIO_SUPPORT_PNAME = "media.DISABLE_AUDIO_SUPPORT";

    /**
     * The name of the <code>boolean</code> <code>ConfigurationService</code> property which indicates
     * whether the method {@link DeviceConfiguration#setAudioSystem(AudioSystem, boolean)} is to be
     * considered disabled for the user i.e. the user is not presented with user interface which
     * allows selecting a particular <code>AudioSystem</code>.
     */
    public static final String DISABLE_SET_AUDIO_SYSTEM_PNAME = "neomedia.audiosystem.DISABLED";

    /**
     * The name of the <code>boolean</code> <code>ConfigurationService</code> property which indicates
     * whether the detection of video <code>CaptureDevice</code>s is to be disabled. The default value
     * is <code>false</code> i.e. the video <code>CaptureDevice</code>s are detected.
     */
    public static final String DISABLE_VIDEO_SUPPORT_PNAME = "media.DISABLE_VIDEO_SUPPORT";

    /**
     * The prefix of the property names the values of which specify the dynamic payload type preferences.
     */
    private static final String DYNAMIC_PAYLOAD_TYPE_PREFERENCES_PNAME_PREFIX
            = "neomedia.dynamicPayloadTypePreferences";

    /**
     * The value of the <code>devices</code> property of <code>MediaServiceImpl</code> when no
     * <code>MediaDevice</code>s are available. Explicitly defined in order to reduce unnecessary allocations.
     */
    private static final List<MediaDevice> EMPTY_DEVICES = Collections.emptyList();

    /**
     * The name of the <code>System</code> boolean property which specifies whether the committing of
     * the JMF/FMJ <code>Registry</code> is to be disabled.
     */
    private static final String JMF_REGISTRY_DISABLE_COMMIT = "JmfRegistry.disableCommit";

    /**
     * The name of the <code>System</code> boolean property which specifies whether the loading of the
     * JMF/FMJ <code>Registry</code> is to be disabled.
     */
    private static final String JMF_REGISTRY_DISABLE_LOAD = "JmfRegistry.disableLoad";

    /**
     * The indicator which determines whether the loading of the JMF/FMJ <code>Registry</code> is disabled.
     */
    private static boolean jmfRegistryDisableLoad;

    /**
     * The indicator which determined whether {@link #postInitializeOnce(MediaServiceImpl)} has
     * been executed in order to perform one-time initialization after initializing the first
     * instance of <code>MediaServiceImpl</code>.
     */
    private static boolean postInitializeOnce;

    /**
     * The prefix that is used to store configuration for encodings preference.
     */
    private static final String ENCODING_CONFIG_PROP_PREFIX = "neomedia.codec.EncodingConfiguration";

    /**
     * The value which will be used for the canonical end-point identifier (CNAME) in RTCP packets
     * sent by this running instance of libjitsi.
     */
    private static final String rtpCname = UUID.randomUUID().toString();

    /**
     * The <code>CaptureDevice</code> user choices such as the default audio and video capture devices.
     */
    private final DeviceConfiguration deviceConfiguration = new DeviceConfiguration();

    /**
     * The <code>PropertyChangeListener</code> which listens to {@link #deviceConfiguration}.
     */
    private final PropertyChangeListener deviceConfigurationPropertyChangeListener
            = event -> deviceConfigurationPropertyChange(event);

    /**
     * The list of audio <code>MediaDevice</code>s reported by this instance when its
     * {@link MediaService#getDevices(MediaType, MediaUseCase)} method is called with an argument
     * {@link MediaType#AUDIO}.
     */
    private final List<MediaDeviceImpl> audioDevices = new ArrayList<>();

    /**
     * The {@link EncodingConfiguration} instance that holds the current (global) list of formats and their preference.
     */
    private final EncodingConfiguration currentEncodingConfiguration;

    /**
     * The <code>MediaFormatFactory</code> through which <code>MediaFormat</code> instances may be created
     * for the purposes of working with the <code>MediaStream</code>s created by this <code>MediaService</code>.
     */
    private MediaFormatFactory formatFactory;

    /**
     * The one and only <code>MediaDevice</code> instance with <code>MediaDirection</code> not allowing
     * sending and <code>MediaType</code> equal to <code>AUDIO</code>.
     */
    private MediaDevice nonSendAudioDevice;

    /**
     * The one and only <code>MediaDevice</code> instance with <code>MediaDirection</code> not allowing
     * sending and <code>MediaType</code> equal to <code>VIDEO</code>.
     */
    private MediaDevice nonSendVideoDevice;

    /**
     * The list of video <code>MediaDevice</code>s reported by this instance when its
     * {@link MediaService#getDevices(MediaType, MediaUseCase)} method is called with an argument
     * {@link MediaType#VIDEO}.
     */
    private final List<MediaDeviceImpl> videoDevices = new ArrayList<>();

    /**
     * A {@link Map} that binds indicates whatever preferences this media service implementation
     * may have for the RTP payload type numbers that get dynamically assigned to
     * {@link MediaFormat}s with no static payload type. The method is useful for formats such as
     * "telephone-event" for example that is statically assigned the 101 payload type by some
     * legacy systems. Signalling protocol implementations such as SIP and XMPP should make sure
     * whenever this is possible, they assign to format the dynamic payload type returned in this {@link Map}.
     */
    private static Map<MediaFormat, Byte> dynamicPayloadTypePreferences;

    /**
     * The volume control of the media service playback.
     */
    private static VolumeControl outputVolumeControl;

    /**
     * The volume control of the media service capture.
     */
    private static VolumeControl inputVolumeControl;

    /**
     * Listeners interested in Recorder events without the need to have access to their instances.
     */
    private final List<Recorder.Listener> recorderListeners = new ArrayList<>();

    static {
        setupFMJ();
    }

    /**
     * Initializes a new <code>MediaServiceImpl</code> instance.
     */
    public MediaServiceImpl()
    {
        /*
         * XXX The deviceConfiguration is initialized and referenced by this instance so adding
         * deviceConfigurationPropertyChangeListener does not need a matching removal.
         */
        deviceConfiguration.addPropertyChangeListener(deviceConfigurationPropertyChangeListener);
        currentEncodingConfiguration = new EncodingConfigurationConfigImpl(ENCODING_CONFIG_PROP_PREFIX);

        /*
         * Perform one-time initialization after initializing the first instance of MediaServiceImpl.
         */
        synchronized (MediaServiceImpl.class) {
            if (!postInitializeOnce) {
                postInitializeOnce = true;
                postInitializeOnce(this);
            }
        }
    }

    /**
     * Create a <code>MediaStream</code> which will use a specific <code>MediaDevice</code> for capture and
     * playback of media. The new instance will not have a <code>StreamConnector</code> at the time of
     * its construction, and a <code>StreamConnector</code> will be specified later on in order to
     * enable the new instance to send and receive media.
     *
     * @param device the <code>MediaDevice</code> to be used by the new instance for capture and playback of media
     * @return a newly-created <code>MediaStream</code> which will use the specified <code>device</code>
     * for capture and playback of media
     * @see MediaService#createMediaStream(MediaDevice)
     */
    public MediaStream createMediaStream(MediaDevice device)
    {
        return createMediaStream(null, device);
    }

    /**
     * {@inheritDoc}
     *
     * Implements {@link MediaService#createMediaStream(MediaType)}. Initializes a new
     * <code>AudioMediaStreamImpl</code> or <code>VideoMediaStreamImpl</code> in accord with <code>mediaType</code>
     */
    public MediaStream createMediaStream(MediaType mediaType)
    {
        return createMediaStream(mediaType, null, null, null);
    }

    /**
     * Creates a new <code>MediaStream</code> instance which will use the specified <code>MediaDevice</code>
     * for both capture and playback of media exchanged via the specified <code>StreamConnector</code>.
     *
     * @param connector the <code>StreamConnector</code> that the new <code>MediaStream</code> instance is to use for
     * sending and receiving media
     * @param device the <code>MediaDevice</code> that the new <code>MediaStream</code> instance is to use for both
     * capture and playback of media exchanged via the specified <code>connector</code>
     * @return a new <code>MediaStream</code> instance
     * @see MediaService#createMediaStream(StreamConnector, MediaDevice)
     */
    public MediaStream createMediaStream(StreamConnector connector, MediaDevice device)
    {
        return createMediaStream(connector, device, null);
    }

    /**
     * {@inheritDoc}
     */
    public MediaStream createMediaStream(StreamConnector connector, MediaType mediaType)
    {
        return createMediaStream(connector, mediaType, null);
    }

    /**
     * Creates a new <code>MediaStream</code> instance which will use the specified <code>MediaDevice</code>
     * for both capture and playback of media exchanged via the specified <code>StreamConnector</code>.
     *
     * @param connector the <code>StreamConnector</code> that the new <code>MediaStream</code> instance is to use for
     * sending and receiving media
     * @param device the <code>MediaDevice</code> that the new <code>MediaStream</code> instance is to use for both
     * capture and playback of media exchanged via the specified <code>connector</code>
     * @param srtpControl a control which is already created, used to control the SRTP operations.
     * @return a new <code>MediaStream</code> instance
     * @see MediaService#createMediaStream(StreamConnector, MediaDevice)
     */
    public MediaStream createMediaStream(StreamConnector connector, MediaDevice device, SrtpControl srtpControl)
    {
        return createMediaStream(null, connector, device, srtpControl);
    }

    /**
     * {@inheritDocs}
     */
    public MediaStream createMediaStream(StreamConnector connector, MediaType mediaType, SrtpControl srtpControl)
    {
        return createMediaStream(mediaType, connector, null, srtpControl);
    }

    /**
     * Initializes a new <code>MediaStream</code> instance. The method is the actual implementation to
     * which the public <code>createMediaStream</code> methods of <code>MediaServiceImpl</code> delegate.
     *
     * @param mediaType the <code>MediaType</code> of the new <code>MediaStream</code> instance to be initialized. If
     * <code>null</code>, <code>device</code> must be non- <code>null</code> and its
     * {@link MediaDevice#getMediaType()} will be used to determine the <code>MediaType</code> of
     * the new instance. If non-<code>null</code>, <code>device</code> may be <code>null</code>. If non-
     * <code>null</code> and <code>device</code> is non- <code>null</code>, the <code>MediaType</code> of
     * <code>device</code> must be (equal to) <code>mediaType</code>.
     * @param connector the <code>StreamConnector</code> to be used by the new instance if non-<code>null</code>
     * @param device the <code>MediaDevice</code> to be used by the instance if non- <code>null</code>
     * @param srtpControl the <code>SrtpControl</code> to be used by the new instance if non- <code>null</code>
     * @return a new <code>MediaStream</code> instance
     */
    private MediaStream createMediaStream(MediaType mediaType, StreamConnector connector,
            MediaDevice device, SrtpControl srtpControl)
    {
        // Make sure that mediaType and device are in accord.
        if (mediaType == null) {
            if (device == null)
                throw new NullPointerException("device");
            else
                mediaType = device.getMediaType();
        }
        else if ((device != null) && !mediaType.equals(device.getMediaType()))
            throw new IllegalArgumentException("device");

        switch (mediaType) {
            case AUDIO:
                return new AudioMediaStreamImpl(connector, device, srtpControl);
            case VIDEO:
                return new VideoMediaStreamImpl(connector, device, srtpControl);
            default:
                return null;
        }
    }

    /**
     * Creates a new <code>MediaDevice</code> which uses a specific <code>MediaDevice</code> to capture and
     * play back media and performs mixing of the captured media and the media played back by any
     * other users of the returned <code>MediaDevice</code>. For the <code>AUDIO</code> <code>MediaType</code>,
     * the returned device is commonly referred to as an audio mixer. The <code>MediaType</code> of the
     * returned <code>MediaDevice</code> is the same as the <code>MediaType</code> of the specified <code>device</code>.
     *
     * @param device the <code>MediaDevice</code> which is to be used by the returned <code>MediaDevice</code> to
     * actually capture and play back media
     * @return a new <code>MediaDevice</code> instance which uses <code>device</code> to capture and play
     * back media and performs mixing of the captured media and the media played back by any
     * other users of the returned <code>MediaDevice</code> instance
     * @see MediaService#createMixer(MediaDevice)
     */
    public MediaDevice createMixer(MediaDevice device)
    {
        switch (device.getMediaType()) {
            case AUDIO:
                return new AudioMixerMediaDevice((AudioMediaDeviceImpl) device);
            case VIDEO:
                return new VideoTranslatorMediaDevice((MediaDeviceImpl) device);
            default:
                /*
                 * TODO If we do not support mixing, should we return null or rather a MediaDevice
                 * with INACTIVE MediaDirection?
                 */
                return null;
        }
    }

    /**
     * Gets the default <code>MediaDevice</code> for the specified <code>MediaType</code>.
     *
     * @param mediaType a <code>MediaType</code> value indicating the type of media to be handled by the
     * <code>MediaDevice</code> to be obtained
     * @param useCase the <code>MediaUseCase</code> to obtain the <code>MediaDevice</code> list for
     * @return the default <code>MediaDevice</code> for the specified <code>mediaType</code> if such a
     * <code>MediaDevice</code> exists; otherwise, <code>null</code>
     * @see MediaService#getDefaultDevice(MediaType, MediaUseCase)
     */
    public MediaDevice getDefaultDevice(MediaType mediaType, MediaUseCase useCase)
    {
        CaptureDeviceInfo captureDeviceInfo;
        switch (mediaType) {
            case AUDIO:
                captureDeviceInfo = getDeviceConfiguration().getAudioCaptureDevice();
                break;
            case VIDEO:
                captureDeviceInfo = getDeviceConfiguration().getVideoCaptureDevice(useCase);
                break;
            default:
                captureDeviceInfo = null;
                break;
        }

        MediaDevice defaultDevice = null;
        if (captureDeviceInfo != null) {
            for (MediaDevice device : getDevices(mediaType, useCase)) {
                if ((device instanceof MediaDeviceImpl)
                        && captureDeviceInfo.equals(((MediaDeviceImpl) device).getCaptureDeviceInfo())) {
                    defaultDevice = device;
                    break;
                }
            }
        }
        if (defaultDevice == null) {
            switch (mediaType) {
                case AUDIO:
                    defaultDevice = getNonSendAudioDevice();
                    break;
                case VIDEO:
                    defaultDevice = getNonSendVideoDevice();
                    break;
                default:
                    /*
                     * There is no MediaDevice with direction which does not allow sending and
                     * mediaType other than AUDIO and VIDEO.
                     */
                    break;
            }
        }
        return defaultDevice;
    }

    /**
     * Gets the <code>CaptureDevice</code> user choices such as the default audio and video capture devices.
     *
     * @return the <code>CaptureDevice</code> user choices such as the default audio and video capture devices.
     */
    public DeviceConfiguration getDeviceConfiguration()
    {
        return deviceConfiguration;
    }

    /**
     * Gets a list of the <code>MediaDevice</code>s known to this <code>MediaService</code> and handling
     * the specified <code>MediaType</code>.
     *
     * @param mediaType the <code>MediaType</code> to obtain the <code>MediaDevice</code> list for
     * @param useCase the <code>MediaUseCase</code> to obtain the <code>MediaDevice</code> list for
     * @return a new <code>List</code> of <code>MediaDevice</code>s known to this <code>MediaService</code> and
     * handling the specified <code>MediaType</code>. The returned <code>List</code> is a copy of the
     * internal storage and, consequently, modifications to it do not affect this instance.
     * Despite the fact that a new <code>List</code> instance is returned by each call to this
     * method, the <code>MediaDevice</code> instances are the same if they are still known to this
     * <code>MediaService</code> to be available.
     * @see MediaService#getDevices(MediaType, MediaUseCase)
     */
    public List<MediaDevice> getDevices(MediaType mediaType, MediaUseCase useCase)
    {
        List<? extends CaptureDeviceInfo> cdis;
        List<MediaDeviceImpl> privateDevices;

        if (MediaType.VIDEO.equals(mediaType)) {
            /*
             * In case a video capture device has been added to or removed from system (i.e.
             * webcam, monitor, etc.), rescan the video capture devices.
             */
            DeviceSystem.initializeDeviceSystems(MediaType.VIDEO);
        }

        switch (mediaType) {
            case AUDIO:
                cdis = getDeviceConfiguration().getAvailableAudioCaptureDevices();
                privateDevices = audioDevices;
                break;
            case VIDEO:
                cdis = getDeviceConfiguration().getAvailableVideoCaptureDevices(useCase);
                privateDevices = videoDevices;
                break;
            default:
                /*
                 * MediaService does not understand MediaTypes other than AUDIO and VIDEO.
                 */
                return EMPTY_DEVICES;
        }

        List<MediaDevice> publicDevices;

        synchronized (privateDevices) {
            if ((cdis == null) || (cdis.size() <= 0))
                privateDevices.clear();
            else {
                Iterator<MediaDeviceImpl> deviceIter = privateDevices.iterator();
                while (deviceIter.hasNext()) {
                    Iterator<? extends CaptureDeviceInfo> cdiIter = cdis.iterator();
                    CaptureDeviceInfo captureDeviceInfo = deviceIter.next().getCaptureDeviceInfo();
                    boolean deviceIsFound = false;

                    while (cdiIter.hasNext()) {
                        if (captureDeviceInfo.equals(cdiIter.next())) {
                            deviceIsFound = true;
                            cdiIter.remove();
                            break;
                        }
                    }
                    if (!deviceIsFound)
                        deviceIter.remove();
                }

                for (CaptureDeviceInfo cdi : cdis) {
                    if (cdi == null)
                        continue;

                    MediaDeviceImpl device;

                    switch (mediaType) {
                        case AUDIO:
                            device = new AudioMediaDeviceImpl(cdi);
                            break;
                        case VIDEO:
                            device = new MediaDeviceImpl(cdi, mediaType);
                            break;
                        default:
                            device = null;
                            break;
                    }
                    if (device != null)
                        privateDevices.add(device);
                }
            }
            publicDevices = new ArrayList<MediaDevice>(privateDevices);
        }

        /*
         * If there are no MediaDevice instances of the specified mediaType, make sure that
         * there is at least one MediaDevice which does not allow sending.
         */
        if (publicDevices.isEmpty()) {
            MediaDevice nonSendDevice;
            switch (mediaType) {
                case AUDIO:
                    nonSendDevice = getNonSendAudioDevice();
                    break;
                case VIDEO:
                    nonSendDevice = getNonSendVideoDevice();
                    break;
                default:
                    /*
                     * There is no MediaDevice with direction not allowing sending and mediaType
                     * other than AUDIO and VIDEO.
                     */
                    nonSendDevice = null;
                    break;
            }
            if (nonSendDevice != null)
                publicDevices.add(nonSendDevice);
        }
        return publicDevices;
    }

    /**
     * Returns the current encoding configuration -- the instance that contains the global settings.
     * Note that any changes made to this instance will have immediate effect on the configuration.
     *
     * @return the current encoding configuration -- the instance that contains the global settings.
     */
    public EncodingConfiguration getCurrentEncodingConfiguration()
    {
        return currentEncodingConfiguration;
    }

    /**
     * Gets the <code>MediaFormatFactory</code> through which <code>MediaFormat</code> instances may be
     * created for the purposes of working with the <code>MediaStream</code>s created by this <code>MediaService</code>.
     *
     * @return the <code>MediaFormatFactory</code> through which <code>MediaFormat</code> instances may be
     * created for the purposes of working with the <code>MediaStream</code>s created by this <code>MediaService</code>
     * @see MediaService#getFormatFactory()
     */
    public MediaFormatFactory getFormatFactory()
    {
        if (formatFactory == null)
            formatFactory = new MediaFormatFactoryImpl();
        return formatFactory;
    }

    /**
     * Gets the one and only <code>MediaDevice</code> instance with <code>MediaDirection</code> not
     * allowing sending and <code>MediaType</code> equal to <code>AUDIO</code>.
     *
     * @return the one and only <code>MediaDevice</code> instance with <code>MediaDirection</code> not
     * allowing sending and <code>MediaType</code> equal to <code>AUDIO</code>
     */
    private MediaDevice getNonSendAudioDevice()
    {
        if (nonSendAudioDevice == null)
            nonSendAudioDevice = new AudioMediaDeviceImpl();
        return nonSendAudioDevice;
    }

    /**
     * Gets the one and only <code>MediaDevice</code> instance with <code>MediaDirection</code> not
     * allowing sending and <code>MediaType</code> equal to <code>VIDEO</code>.
     *
     * @return the one and only <code>MediaDevice</code> instance with <code>MediaDirection</code> not
     * allowing sending and <code>MediaType</code> equal to <code>VIDEO</code>
     */
    private MediaDevice getNonSendVideoDevice()
    {
        if (nonSendVideoDevice == null)
            nonSendVideoDevice = new MediaDeviceImpl(MediaType.VIDEO);
        return nonSendVideoDevice;
    }

    /**
     * {@inheritDoc}
     */
    public SrtpControl createSrtpControl(SrtpControlType srtpControlType, final byte[] myZid)
    {
        switch (srtpControlType) {
            case DTLS_SRTP:
                return new DtlsControlImpl();
            case SDES:
                return new SDesControlImpl();
            case ZRTP:
                return new ZrtpControlImpl(myZid);
            default:
                return null;
        }
    }

    /**
     * Gets the <code>VolumeControl</code> which controls the volume level of audio output/playback.
     *
     * @return the <code>VolumeControl</code> which controls the volume level of audio output/playback
     * @see MediaService#getOutputVolumeControl()
     */
    public VolumeControl getOutputVolumeControl()
    {
        if (outputVolumeControl == null) {
            outputVolumeControl = new BasicVolumeControl(VolumeControl.PLAYBACK_VOLUME_LEVEL_PROPERTY_NAME);
        }
        return outputVolumeControl;
    }

    /**
     * Gets the <code>VolumeControl</code> which controls the volume level of audio input/capture.
     *
     * @return the <code>VolumeControl</code> which controls the volume level of audio input/capture
     * @see MediaService#getInputVolumeControl()
     */
    public VolumeControl getInputVolumeControl()
    {
        if (inputVolumeControl == null) {
            // If available, use hardware.
            try {
                inputVolumeControl
                        = new HardwareVolumeControl(this, VolumeControl.CAPTURE_VOLUME_LEVEL_PROPERTY_NAME);
            } catch (Throwable t) {
                if (t instanceof ThreadDeath)
                    throw (ThreadDeath) t;
                else if (t instanceof InterruptedException)
                    Thread.currentThread().interrupt();
            }
            // Otherwise, use software.
            if (inputVolumeControl == null) {
                inputVolumeControl = new BasicVolumeControl(VolumeControl.CAPTURE_VOLUME_LEVEL_PROPERTY_NAME);
            }
        }
        return inputVolumeControl;
    }

    /**
     * Get available screens.
     *
     * @return screens
     */
    public List<ScreenDevice> getAvailableScreenDevices()
    {
        ScreenDevice[] screens = ScreenDeviceImpl.getAvailableScreenDevices();
        List<ScreenDevice> screenList;

        if ((screens != null) && (screens.length != 0))
            screenList = new ArrayList<>(Arrays.asList(screens));
        else
            screenList = Collections.emptyList();
        return screenList;
    }

    /**
     * Get default screen device.
     *
     * @return default screen device
     */
    public ScreenDevice getDefaultScreenDevice()
    {
        return ScreenDeviceImpl.getDefaultScreenDevice();
    }

    /**
     * Creates a new <code>Recorder</code> instance that can be used to record a call which captures
     * and plays back media using a specific <code>MediaDevice</code>.
     *
     * @param device the <code>MediaDevice</code> which is used for media capture and playback by the call to be recorded
     * @return a new <code>Recorder</code> instance that can be used to record a call which captures
     * and plays back media using the specified <code>MediaDevice</code>
     * @see MediaService#createRecorder(MediaDevice)
     */
    public Recorder createRecorder(MediaDevice device)
    {
        if (device instanceof AudioMixerMediaDevice)
            return new RecorderImpl((AudioMixerMediaDevice) device);
        else
            return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Recorder createRecorder(RTPTranslator translator)
    {
        return new RecorderRtpImpl(translator);
    }

    /**
     * Returns a {@link Map} that binds indicates whatever preferences this media service
     * implementation may have for the RTP payload type numbers that get dynamically assigned to
     * {@link MediaFormat}s with no static payload type. The method is useful for formats such as
     * "telephone-event" for example that is statically assigned the 101 payload type by some
     * legacy systems. Signaling protocol implementations such as SIP and XMPP should make sure
     * that, whenever this is possible, they assign to formats the dynamic payload type returned
     * in this {@link Map}.
     *
     * @return a {@link Map} binding some formats to a preferred dynamic RTP payload type number.
     */
    @Override
    public Map<MediaFormat, Byte> getDynamicPayloadTypePreferences()
    {
        if (dynamicPayloadTypePreferences == null) {
            dynamicPayloadTypePreferences = new HashMap<>();

            /*
             * Set the dynamicPayloadTypePreferences to their default values. If the user chooses to
             * override them through the ConfigurationService, they will be overwritten later on.
             */
            MediaFormat telephoneEvent = MediaUtils.getMediaFormat("telephone-event", 8000);
            if (telephoneEvent != null)
                dynamicPayloadTypePreferences.put(telephoneEvent, (byte) 101);

            MediaFormat h264 = MediaUtils.getMediaFormat("H264", VideoMediaFormatImpl.DEFAULT_CLOCK_RATE);
            if (h264 != null)
                dynamicPayloadTypePreferences.put(h264, (byte) 99);

            /*
             * Try to load dynamicPayloadTypePreferences from the ConfigurationService.
             */
            ConfigurationService cfg = LibJitsi.getConfigurationService();
            if (cfg != null) {
                String prefix = DYNAMIC_PAYLOAD_TYPE_PREFERENCES_PNAME_PREFIX;
                List<String> propertyNames = cfg.getPropertyNamesByPrefix(prefix, true);

                for (String propertyName : propertyNames) {
                    /*
                     * The dynamic payload type is the name of the property name and the format
                     * which prefers it is the property value.
                     */
                    byte dynamicPayloadTypePreference = 0;
                    Throwable exception = null;

                    try {
                        dynamicPayloadTypePreference = Byte.parseByte(propertyName.substring(prefix.length() + 1));
                    } catch (IndexOutOfBoundsException | NumberFormatException ioobe) {
                        exception = ioobe;
                    }
                    if (exception != null) {
                        Timber.w(exception, "Ignoring dynamic payload type preference which could not be parsed: %s", propertyName);
                        continue;
                    }

                    String source = cfg.getString(propertyName);
                    if ((source != null) && (source.length() != 0)) {
                        try {
                            // JSONObject json = (JSONObject) JSONValue.parseWithException(source);
                            JSONObject json = new JSONObject(source);
                            String encoding = json.getString(MediaFormatImpl.ENCODING_PNAME);
                            long clockRate = json.getLong(MediaFormatImpl.CLOCK_RATE_PNAME);
                            Map<String, String> fmtps = new HashMap<>();

                            if (json.has(MediaFormatImpl.FORMAT_PARAMETERS_PNAME)) {
                                JSONObject jsonFmtps = (JSONObject) json.get(MediaFormatImpl.FORMAT_PARAMETERS_PNAME);
                                Iterator<String> keys = jsonFmtps.keys();
                                while (keys.hasNext()) {
                                    String key = keys.next();
                                    String value = jsonFmtps.getString(key);
                                    fmtps.put(key, value);
                                }
                            }

                            MediaFormat mediaFormat = MediaUtils.getMediaFormat(encoding, clockRate, fmtps);
                            if (mediaFormat != null) {
                                dynamicPayloadTypePreferences.put(mediaFormat, dynamicPayloadTypePreference);
                            }
                        } catch (Throwable jsone) {
                            Timber.w(jsone, "Ignoring dynamic payload type preference which could not be parsed: %s", source);
                        }
                    }
                }
            }
        }
        return dynamicPayloadTypePreferences;
    }

    /**
     * Creates a preview component for the specified device(video device) used to show video
     * preview from that device.
     *
     * @param device the video device
     * @param preferredWidth the width we prefer for the component
     * @param preferredHeight the height we prefer for the component
     * @return the preview component.
     */
    @Override
    public Object getVideoPreviewComponent(MediaDevice device, int preferredWidth, int preferredHeight)
    {
        String noPreviewText = aTalkApp.getResString(R.string.impl_media_configform_NO_PREVIEW);
        JLabel noPreview = new JLabel(noPreviewText);

        noPreview.setHorizontalAlignment(SwingConstants.CENTER);
        noPreview.setVerticalAlignment(SwingConstants.CENTER);
        final JComponent videoContainer = new VideoContainer(noPreview, false);

        if ((preferredWidth > 0) && (preferredHeight > 0)) {
            videoContainer.setPreferredSize(new Dimension(preferredWidth, preferredHeight));
        }

        try {
            CaptureDeviceInfo captureDeviceInfo;
            if ((device != null) && ((captureDeviceInfo = ((MediaDeviceImpl) device).getCaptureDeviceInfo()) != null)) {
                DataSource dataSource = Manager.createDataSource(captureDeviceInfo.getLocator());

                /*
                 * Don't let the size be uselessly small just because the videoContainer has too
                 * small a preferred size.
                 */
                if ((preferredWidth < 128) || (preferredHeight < 96)) {
                    preferredWidth = 128;
                    preferredHeight = 96;
                }
                VideoMediaStreamImpl.selectVideoSize(dataSource, preferredWidth, preferredHeight);

                // A Player is documented to be created on a connected DataSource.
                dataSource.connect();

                Processor player = Manager.createProcessor(dataSource);
                final VideoContainerHierarchyListener listener
                        = new VideoContainerHierarchyListener(videoContainer, player);
                videoContainer.addHierarchyListener(listener);
                final MediaLocator locator = dataSource.getLocator();

                player.addControllerListener(new ControllerListener()
                {
                    public void controllerUpdate(ControllerEvent event)
                    {
                        controllerUpdateForPreview(event, videoContainer, locator, listener);
                    }
                });
                player.configure();
            }
        } catch (Throwable t) {
            if (t instanceof ThreadDeath)
                throw (ThreadDeath) t;
            else
                Timber.e(t, "Failed to create video preview");
        }
        return videoContainer;
    }

    /**
     * Listens and shows the video in the video container when needed.
     *
     * @param event the event when player has ready visual component.
     * @param videoContainer the container.
     * @param locator input DataSource locator
     * @param listener the hierarchy listener we created for the video container.
     */
    private static void controllerUpdateForPreview(ControllerEvent event,
            JComponent videoContainer, MediaLocator locator, VideoContainerHierarchyListener listener)
    {
        if (event instanceof ConfigureCompleteEvent) {
            Processor player = (Processor) event.getSourceController();

            /*
             * Use SwScale for the scaling since it produces an image with better quality and add
             * the "flip" effect to the video.
             */
            TrackControl[] trackControls = player.getTrackControls();

            if ((trackControls != null) && (trackControls.length != 0))
                try {
                    for (TrackControl trackControl : trackControls) {
                        Codec codecs[];
                        SwScale scaler = new SwScale();

                        // do not flip desktop
                        if (DeviceSystem.LOCATOR_PROTOCOL_IMGSTREAMING.equals(locator.getProtocol()))
                            codecs = new Codec[]{scaler};
                        else
                            codecs = new Codec[]{new HFlip(), scaler};

                        trackControl.setCodecChain(codecs);
                        break;
                    }
                } catch (UnsupportedPlugInException upiex) {
                    Timber.w(upiex, "Failed to add SwScale/VideoFlipEffect to codec chain");
                }

            // Turn the Processor into a Player.
            try {
                player.setContentDescriptor(null);
            } catch (NotConfiguredError nce) {
                Timber.e(nce, "Failed to set ContentDescriptor of Processor");
            }
            player.realize();
        }
        else if (event instanceof RealizeCompleteEvent) {
            Player player = (Player) event.getSourceController();
            Component video = player.getVisualComponent();

            // sets the preview to the listener
            listener.setPreview(video);
            showPreview(videoContainer, video, player);
        }
    }

    /**
     * Shows the preview panel.
     *
     * @param previewContainer the container
     * @param preview the preview component.
     * @param player the player.
     */
    private static void showPreview(final JComponent previewContainer, final Component preview, final Player player)
    {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> showPreview(previewContainer, preview, player));
            return;
        }

        previewContainer.removeAll();
        if (preview != null) {
            previewContainer.add(preview);
            player.start();

            if (previewContainer.isDisplayable()) {
                previewContainer.revalidate();
                previewContainer.repaint();
            }
            else
                previewContainer.doLayout();
        }
        else
            disposePlayer(player);
    }

    /**
     * Dispose the player used for the preview.
     *
     * @param player the player.
     */
    private static void disposePlayer(final Player player)
    {
        // launch disposing preview player in separate thread will lock renderer and can produce
        // lock if user has quickly requested preview component and can lock ui thread
        new Thread(() -> {
            player.stop();
            player.deallocate();
            player.close();
        }).start();
    }

    /**
     * Get a <code>MediaDevice</code> for a part of desktop streaming/sharing.
     *
     * @param width width of the part
     * @param height height of the part
     * @param x origin of the x coordinate (relative to the full desktop)
     * @param y origin of the y coordinate (relative to the full desktop)
     * @return <code>MediaDevice</code> representing the part of desktop or null if problem
     */
    public MediaDevice getMediaDeviceForPartialDesktopStreaming(int width, int height, int x, int y)
    {
        MediaDevice device;
        String name = "Partial desktop streaming";
        Dimension size;
        int multiple;
        Point p = new Point((x < 0) ? 0 : x, (y < 0) ? 0 : y);
        ScreenDevice dev = getScreenForPoint(p);
        int display;

        if (dev != null)
            display = dev.getIndex();
        else
            return null;

        /* on Mac OS X, width have to be a multiple of 16 */
        if (OSUtils.IS_MAC) {
            multiple = Math.round(width / 16f);
            width = multiple * 16;
        }
        else {
            /* JMF filter graph seems to not like odd width */
            multiple = Math.round(width / 2f);
            width = multiple * 2;
        }

        /* JMF filter graph seems to not like odd height */
        multiple = Math.round(height / 2f);
        height = multiple * 2;

        size = new Dimension(width, height);

        Format formats[] = new Format[]{
                new AVFrameFormat(
                        size,
                        Format.NOT_SPECIFIED,
                        FFmpeg.PIX_FMT_ARGB,
                        Format.NOT_SPECIFIED),
                new RGBFormat(
                        size, // size
                        Format.NOT_SPECIFIED, // maxDataLength
                        Format.byteArray, // dataType
                        Format.NOT_SPECIFIED, // frameRate
                        32, // bitsPerPixel
                        2 /* red */,
                        3 /* green */,
                        4 /* blue */)
        };

        Rectangle bounds = ((ScreenDeviceImpl) dev).getBounds();
        x -= bounds.x;
        y -= bounds.y;

        CaptureDeviceInfo devInfo
                = new CaptureDeviceInfo(name + " " + display,
                new MediaLocator(
                        DeviceSystem.LOCATOR_PROTOCOL_IMGSTREAMING + ":"
                                + display + "," + x + "," + y), formats);
        device = new MediaDeviceImpl(devInfo, MediaType.VIDEO);
        return device;
    }

    /**
     * If the <code>MediaDevice</code> corresponds to partial desktop streaming device.
     *
     * @param mediaDevice <code>MediaDevice</code>
     * @return true if <code>MediaDevice</code> is a partial desktop streaming device, false otherwise
     */
    public boolean isPartialStreaming(MediaDevice mediaDevice)
    {
        if (mediaDevice == null)
            return false;

        MediaDeviceImpl dev = (MediaDeviceImpl) mediaDevice;
        CaptureDeviceInfo cdi = dev.getCaptureDeviceInfo();
        return (cdi != null) && cdi.getName().startsWith("Partial desktop streaming");
    }

    /**
     * Find the screen device that contains specified point.
     *
     * @param p point coordinates
     * @return screen device that contains point
     */
    public ScreenDevice getScreenForPoint(Point p)
    {
        for (ScreenDevice dev : getAvailableScreenDevices())
            if (dev.containsPoint(p))
                return dev;
        return null;
    }

    /**
     * Gets the origin of a specific desktop streaming device.
     *
     * @param mediaDevice the desktop streaming device to get the origin on
     * @return the origin of the specified desktop streaming device
     */
    public Point getOriginForDesktopStreamingDevice(MediaDevice mediaDevice)
    {
        MediaDeviceImpl dev = (MediaDeviceImpl) mediaDevice;
        CaptureDeviceInfo cdi = dev.getCaptureDeviceInfo();

        if (cdi == null)
            return null;

        MediaLocator locator = cdi.getLocator();
        if (!DeviceSystem.LOCATOR_PROTOCOL_IMGSTREAMING.equals(locator.getProtocol()))
            return null;

        String remainder = locator.getRemainder();
        String split[] = remainder.split(",");
        int index = Integer.parseInt(((split != null) && (split.length > 1)) ? split[0] : remainder);

        List<ScreenDevice> devs = getAvailableScreenDevices();

        if (devs.size() - 1 >= index) {
            Rectangle r = ((ScreenDeviceImpl) devs.get(index)).getBounds();
            return new Point(r.x, r.y);
        }
        return null;
    }

    /**
     * Those interested in Recorder events add listener through MediaService. This way they don't
     * need to have access to the Recorder instance. Adds a new <code>Recorder.Listener</code> to the
     * list of listeners interested in notifications from a <code>Recorder</code>.
     *
     * @param listener the new <code>Recorder.Listener</code> to be added to the list of listeners interested in
     * notifications from <code>Recorder</code>s.
     */
    public void addRecorderListener(Recorder.Listener listener)
    {
        synchronized (recorderListeners) {
            if (!recorderListeners.contains(listener))
                recorderListeners.add(listener);
        }
    }

    /**
     * Removes an existing <code>Recorder.Listener</code> from the list of listeners interested in
     * notifications from <code>Recorder</code>s.
     *
     * @param listener the existing <code>Listener</code> to be removed from the list of listeners interested in
     * notifications from <code>Recorder</code>s
     */
    public void removeRecorderListener(Recorder.Listener listener)
    {
        synchronized (recorderListeners) {
            recorderListeners.remove(listener);
        }
    }

    /**
     * Gives access to currently registered <code>Recorder.Listener</code>s.
     *
     * @return currently registered <code>Recorder.Listener</code>s.
     */
    public Iterator<Recorder.Listener> getRecorderListeners()
    {
        return recorderListeners.iterator();
    }

    /**
     * Notifies this instance that the value of a property of {@link #deviceConfiguration} has changed.
     *
     * @param event a <code>PropertyChangeEvent</code> which specifies the name of the property which had its
     * value changed and the old and the new values of that property
     */
    private void deviceConfigurationPropertyChange(PropertyChangeEvent event)
    {
        String propertyName = event.getPropertyName();

        /*
         * While the AUDIO_CAPTURE_DEVICE is sure to affect the DEFAULT_DEVICE, AUDIO_PLAYBACK_DEVICE is not.
         * Anyway, MediaDevice is supposed to represent the device to be used for capture AND
         * playback (though its current implementation MediaDeviceImpl may be incomplete with
         * respect to the playback representation). Since it is not clear at this point of the
         * execution whether AUDIO_PLAYBACK_DEVICE really affects the DEFAULT_DEVICE and for the
         * sake of completeness, throw in the changes to the AUDIO_NOTIFY_DEVICE as well.
         */
        if (DeviceConfiguration.AUDIO_CAPTURE_DEVICE.equals(propertyName)
                || DeviceConfiguration.AUDIO_NOTIFY_DEVICE.equals(propertyName)
                || DeviceConfiguration.AUDIO_PLAYBACK_DEVICE.equals(propertyName)
                || DeviceConfiguration.VIDEO_CAPTURE_DEVICE.equals(propertyName)) {
            /*
             * We do not know the old value of the property at the time of this writing. We cannot
             * report the new value either because we do not know the MediaType and the MediaUseCase.
             * cmeng (20210322): must not forward the received event values, otherwise toggle camera does not work.
             */
            firePropertyChange(DEFAULT_DEVICE, null, null);
        }
    }

    /**
     * Initializes a new <code>RTPTranslator</code> which is to forward RTP and RTCP traffic between
     * multiple <code>MediaStream</code>s.
     *
     * @return a new <code>RTPTranslator</code> which is to forward RTP and RTCP traffic between
     * multiple <code>MediaStream</code>s
     * @see MediaService#createRTPTranslator()
     */
    public RTPTranslator createRTPTranslator()
    {
        return new RTPTranslatorImpl();
    }

    /**
     * Gets the indicator which determines whether the loading of the JMF/FMJ <code>Registry</code> has
     * been disabled.
     *
     * @return <code>true</code> if the loading of the JMF/FMJ <code>Registry</code> has been disabled;
     * otherwise, <code>false</code>
     */
    public static boolean isJmfRegistryDisableLoad()
    {
        return jmfRegistryDisableLoad;
    }

    /**
     * Performs one-time initialization after initializing the first instance of
     * <code>MediaServiceImpl</code>.
     *
     * @param mediaServiceImpl the <code>MediaServiceImpl</code> instance which has caused the need to perform the
     * one-time initialization
     */
    private static void postInitializeOnce(MediaServiceImpl mediaServiceImpl)
    {
        /*
         * Some SecureRandom() implementations like SHA1PRNG call /dev/random to seed themselves
         * on first use. Call SecureRandom early to avoid blocking when establishing
         * a connection for example.
         */
        SecureRandom rnd = new SecureRandom();
        byte[] b = new byte[20];
        rnd.nextBytes(b);
        Timber.d("Warming up SecureRandom completed.");
    }

    /**
     * Sets up FMJ for execution. For example, sets properties which instruct FMJ whether it is to
     * create a log, where the log is to be created.
     */
    private static void setupFMJ()
    {
        /*
         * FMJ now uses java.util.logging.Logger, but only logs if allowLogging is set in its
         * registry. Since the levels can be configured through properties for the
         * net.sf.fmj.media.Log class, we always enable this (as opposed to only enabling it when
         * this.logger has debug enabled).
         */
        Registry.set("allowLogging", true);

        // ### cmeng - user only fmj codec (+ our custom codec)? - have problem
        // if only set for FMJ option (13 Nov 2015)
        // RegistryDefaults.setDefaultFlags(RegistryDefaults.FMJ);

        /*
         * Disable the loading of .fmj.registry because Kertesz Laszlo has reported that audio
         * input devices duplicate after restarting Jitsi. Besides, Jitsi does not really need
         * .fmj.registry on startup.
         */
        if (System.getProperty(JMF_REGISTRY_DISABLE_LOAD) == null)
            System.setProperty(JMF_REGISTRY_DISABLE_LOAD, "true");
        jmfRegistryDisableLoad = "true".equalsIgnoreCase(System.getProperty(JMF_REGISTRY_DISABLE_LOAD));

        if (System.getProperty(JMF_REGISTRY_DISABLE_COMMIT) == null)
            System.setProperty(JMF_REGISTRY_DISABLE_COMMIT, "true");

        String scHomeDirLocation = System.getProperty(ConfigurationService.PNAME_SC_CACHE_DIR_LOCATION);
        if (scHomeDirLocation != null) {
            String scHomeDirName = System.getProperty(ConfigurationService.PNAME_SC_HOME_DIR_NAME);

            if (scHomeDirName != null) {
                File scHomeDir = new File(scHomeDirLocation, scHomeDirName);

                /* Write FMJ's log in Jitsi's log directory. */
                Registry.set("secure.logDir", new File(scHomeDir, "log").getPath());

                /* Write FMJ's registry in Jitsi's user data directory. */
                String jmfRegistryFilename = "JmfRegistry.filename";

                if (System.getProperty(jmfRegistryFilename) == null) {
                    System.setProperty(jmfRegistryFilename, new File(scHomeDir, ".fmj.registry").getAbsolutePath());
                }
            }
        }

        boolean enableFfmpeg = true;
        ConfigurationService cfg = LibJitsi.getConfigurationService();

        if (cfg != null) {
            enableFfmpeg = cfg.getBoolean(ENABLE_FFMPEG_CODECS_PNAME, enableFfmpeg);
            for (String prop : cfg.getPropertyNamesByPrefix("neomedia.adaptive_jitter_buffer", true)) {
                String suffix = prop.substring(prop.lastIndexOf(".") + 1);
                Registry.set("adaptive_jitter_buffer_" + suffix, cfg.getString(prop));
            }
        }
        FMJPlugInConfiguration.registerCustomPackages();
        FMJPlugInConfiguration.registerCustomCodecs(enableFfmpeg);
        FMJPlugInConfiguration.registerCustomMultiplexers();
    }

    /**
     * Returns a new {@link EncodingConfiguration} instance that can be used by other bundles.
     *
     * @return a new {@link EncodingConfiguration} instance.
     */
    public EncodingConfiguration createEmptyEncodingConfiguration()
    {
        return new EncodingConfigurationImpl();
    }

    /**
     * Determines whether the support for a specific <code>MediaType</code> is enabled. The
     * <code>ConfigurationService</code> and <code>System</code> properties
     * {@link #DISABLE_AUDIO_SUPPORT_PNAME} and {@link #DISABLE_VIDEO_SUPPORT_PNAME} allow
     * disabling the support for, respectively, {@link MediaType#AUDIO} and {@link MediaType#VIDEO}.
     *
     * @param mediaType the <code>MediaType</code> to be determined whether the support for it is enabled
     * @return <code>true</code> if the support for the specified <code>mediaType</code> is enabled; otherwise, <code>false</code>
     */
    public static boolean isMediaTypeSupportEnabled(MediaType mediaType)
    {
        String propertyName;

        switch (mediaType) {
            case AUDIO:
                propertyName = DISABLE_AUDIO_SUPPORT_PNAME;
                break;
            case VIDEO:
                propertyName = DISABLE_VIDEO_SUPPORT_PNAME;
                break;
            default:
                return true;
        }
        ConfigurationService cfg = LibJitsi.getConfigurationService();
        return ((cfg == null) || !cfg.getBoolean(propertyName, false)) && !Boolean.getBoolean(propertyName);
    }

    /**
     * {@inheritDoc}
     */
    public String getRtpCname()
    {
        return rtpCname;
    }

    /**
     * The listener which will be notified for changes in the video container. Whether the
     * container is displayable or not we will stop the player or start it.
     */
    private static class VideoContainerHierarchyListener implements HierarchyListener
    {
        /**
         * The parent window.
         */
        private Window window;

        /**
         * The listener for the parent window. Used to dispose player on close.
         */
        private WindowListener windowListener;

        /**
         * The parent container of our preview.
         */
        private JComponent container;

        /**
         * The player showing the video preview.
         */
        private Player player;

        /**
         * The preview component of the player, must be set once the player has been realized.
         */
        private Component preview = null;

        /**
         * Creates VideoContainerHierarchyListener.
         *
         * @param container the video container.
         * @param player the player.
         */
        VideoContainerHierarchyListener(JComponent container, Player player)
        {
            this.container = container;
            this.player = player;
        }

        /**
         * After the player has been realized the preview can be obtained and supplied to this
         * listener. Normally done on player RealizeCompleteEvent.
         *
         * @param preview the preview.
         */
        void setPreview(Component preview)
        {
            this.preview = preview;
        }

        /**
         * Disposes player and cleans listeners as we will no longer need them.
         */
        public void dispose()
        {
            if (windowListener != null) {
                if (window != null) {
                    window.removeWindowListener(windowListener);
                    window = null;
                }
                windowListener = null;
            }
            container.removeHierarchyListener(this);
            disposePlayer(player);

            /*
             * We've just disposed the player which created the preview component so the preview
             * component is of no use regardless of whether the Media configuration form will be
             * redisplayed or not. And since the preview component appears to be a huge object even
             * after its player is disposed, make sure to not reference it.
             */
            if (preview != null)
                container.remove(preview);
        }

        /**
         * Change in container.
         *
         * @param event the event for the change.
         */
        public void hierarchyChanged(HierarchyEvent event)
        {
            if ((event.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) == 0)
                return;

            if (!container.isDisplayable()) {
                dispose();
                return;
            }
            else {
                // if this is just a change in the video container and preview has not been
                // created yet, do nothing otherwise start the player which will show in preview
                if (preview != null) {
                    player.start();
                }
            }

            if (windowListener == null) {
                window = SwingUtilities.windowForComponent(container);
                if (window != null) {
                    windowListener = new WindowAdapter()
                    {
                        @Override
                        public void windowClosing(WindowEvent event)
                        {
                            dispose();
                        }
                    };
                    window.addWindowListener(windowListener);
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RecorderEventHandler createRecorderEventHandlerJson(String filename)
            throws IOException
    {
        return new RecorderEventHandlerJSONImpl(filename);
    }
}
