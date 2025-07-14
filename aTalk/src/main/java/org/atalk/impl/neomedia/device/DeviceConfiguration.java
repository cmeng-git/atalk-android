/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device;

import java.awt.Dimension;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import javax.media.CaptureDeviceInfo;
import javax.media.CaptureDeviceManager;
import javax.media.Format;
import javax.media.PlugInManager;
import javax.media.Renderer;
import javax.media.format.VideoFormat;

import org.atalk.impl.neomedia.MediaServiceImpl;
import org.atalk.impl.neomedia.codec.video.AVFrameFormat;
import org.atalk.impl.timberlog.TimberLog;
import org.atalk.service.configuration.ConfigurationService;
import org.atalk.service.libjitsi.LibJitsi;
import org.atalk.service.neomedia.MediaUseCase;
import org.atalk.service.neomedia.codec.Constants;
import org.atalk.util.MediaType;
import org.atalk.util.OSUtils;
import org.atalk.util.event.PropertyChangeNotifier;

import timber.log.Timber;

/**
 * This class aims to provide a simple configuration interface for JMF. It retrieves stored
 * configuration when started or listens to ConfigurationEvent for property changes and configures
 * the JMF accordingly.
 *
 * @author Martin Andre
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Vincent Lucas
 * @author Eng Chong Meng
 */
public class DeviceConfiguration extends PropertyChangeNotifier implements PropertyChangeListener {
    /**
     * The name of the <code>DeviceConfiguration</code> property which represents the device used by
     * <code>DeviceConfiguration</code> for audio capture.
     */
    public static final String AUDIO_CAPTURE_DEVICE = CaptureDevices.PROP_DEVICE;

    /**
     * The name of the <code>DeviceConfiguration</code> property which represents the device used by
     * <code>DeviceConfiguration</code> for audio notify.
     */
    public static final String AUDIO_NOTIFY_DEVICE = NotifyDevices.PROP_DEVICE;

    /**
     * The name of the <code>DeviceConfiguration</code> property which represents the device used by
     * <code>DeviceConfiguration</code> for audio playback.
     */
    public static final String AUDIO_PLAYBACK_DEVICE = PlaybackDevices.PROP_DEVICE;

    /**
     * The list of class names of custom <code>Renderer</code> implementations to be registered with JMF.
     */
    private static final String[] CUSTOM_RENDERERS = new String[]{
            ".audio.AudioTrackRenderer",
            ".audio.OpenSLESRenderer",
            ".video.SurfaceRenderer",
            ".video.JAWTRenderer"
    };

    /**
     * The default value to be used for the {@link //#PROP_AUDIO_DENOISE} property when it does not have a value.
     */
    public static final boolean DEFAULT_AUDIO_DENOISE = true;

    /**
     * The default value to be used for the {@link //#PROP_AUDIO_ECHOCANCEL} property when it does not have a value.
     */
    public static final boolean DEFAULT_AUDIO_ECHOCANCEL = true;

    /**
     * The default value to be used for the {@link #PROP_AUDIO_ECHOCANCEL_FILTER_LENGTH_IN_MILLIS}
     * property when it does not have a value. The recommended filter length is approximately the
     * third of the room reverberation time. For example, in a small room, reverberation time is in
     * the order of 300 ms, so a filter length of 100 ms is a good choice (800 samples at 8000 Hz sampling rate).
     */
    public static final long DEFAULT_AUDIO_ECHOCANCEL_FILTER_LENGTH_IN_MILLIS = 100;

    /**
     * The default value for video codec bitrate.
     */
    public static final int DEFAULT_VIDEO_BITRATE = 128;

    /**
     * The default frame rate, <code>-1</code> unlimited.
     */
    public static final int DEFAULT_VIDEO_FRAMERATE = -1;

    /**
     * The default video width.
     */
    public static final int DEFAULT_VIDEO_WIDTH = 720;

    /**
     * The default video height.
     */
    public static final int DEFAULT_VIDEO_HEIGHT = 480;

    /**
     * The default value for video maximum bandwidth.
     */
    public static final int DEFAULT_VIDEO_RTP_PACING_THRESHOLD = 256;

    /**
     * The name of the <code>long</code> property which determines the filter length in milliseconds to
     * be used by the echo cancellation implementation. The recommended filter length is
     * approximately the third of the room reverberation time. For example, in a small room,
     * reverberation time is in the order of 300 ms, so a filter length of 100 ms is a good choice
     * (800 samples at 8000 Hz sampling rate).
     */
    private static final String PROP_AUDIO_ECHOCANCEL_FILTER_LENGTH_IN_MILLIS
            = "neomedia.echocancel.filterLengthInMillis";

    public static final String PROP_AUDIO_SYSTEM = "neomedia.audioSystem";

    public static final String PROP_AUDIO_SYSTEM_DEVICES = PROP_AUDIO_SYSTEM + "." + DeviceSystem.PROP_DEVICES;

    /**
     * The property we use to store the settings for video codec bitrate.
     */
    private static final String PROP_VIDEO_BITRATE = "neomedia.video.bitrate";

    /**
     * The <code>ConfigurationService</code> property which stores the device used by
     * <code>DeviceConfiguration</code> for video capture.
     */
    private static final String PROP_VIDEO_DEVICE = "neomedia.videoDevice";

    /**
     * The property we use to store the video frame rate settings.
     */
    private static final String PROP_VIDEO_FRAMERATE = "neomedia.video.framerate";

    /**
     * The name of the property which specifies the height of the video.
     */
    private static final String PROP_VIDEO_HEIGHT = "neomedia.video.height";

    /**
     * The property we use to store the settings for maximum allowed video bandwidth (used to
     * normalize RTP traffic, and not in codec configuration)
     */
    public static final String PROP_VIDEO_RTP_PACING_THRESHOLD = "neomedia.video.maxbandwidth";

    /**
     * The name of the property which specifies the width of the video.
     */
    private static final String PROP_VIDEO_WIDTH = "neomedia.video.width";

    /**
     * The currently supported resolutions we will show as option for user selection.
     */
    public static final Dimension[] SUPPORTED_RESOLUTIONS = new Dimension[]{
            // new Dimension(160, 120), // 4:3 QQVGA
            // new Dimension(176, 144), // QCIF
            new Dimension(320, 240),    // 4:3 QVGA
            // new Dimension(352, 288), // CIF
            new Dimension(640, 480),    // 4:3 VGA
            new Dimension(720, 480),    // DV NTSC
            // new Dimension(768, 480),    // 16:10 Wide VGA
            // new Dimension(800, 450), // < QHD - not support by camera preview
            new Dimension(960, 720),    // Panasonic DVCPRO100
            new Dimension(1280, 720),   // 16:9 HDTV
            new Dimension(1440, 1080),  // HDV 1080
            new Dimension(1920, 1080)   // 16:9 HD
    };

    /**
     * The name of the <code>DeviceConfiguration</code> property which represents the device used by
     * <code>DeviceConfiguration</code> for video capture.
     */
    public static final String VIDEO_CAPTURE_DEVICE = "VIDEO_CAPTURE_DEVICE";

    /**
     * Fixes the list of <code>Renderer</code>s registered with FMJ in order to resolve operating system-specific issues.
     */
    private static void fixRenderers() {
        @SuppressWarnings("unchecked")
        Vector<String> renderers = PlugInManager.getPlugInList(null, null, PlugInManager.RENDERER);

        /*
         * JMF is no longer in use, FMJ is used in its place. FMJ has its own JavaSoundRenderer
         * which is also extended into a JMF-compatible one.
         */
        PlugInManager.removePlugIn("com.sun.media.renderer.audio.JavaSoundRenderer", PlugInManager.RENDERER);

        if (OSUtils.IS_WINDOWS) {
            if (OSUtils.IS_WINDOWS32) {
                /*
                 * DDRenderer will cause 32-bit Windows Vista/7 to switch its theme from Aero to
                 * Vista Basic so try to pick up a different Renderer.
                 */
                if (renderers.contains("com.sun.media.renderer.video.GDIRenderer")) {
                    PlugInManager.removePlugIn("com.sun.media.renderer.video.DDRenderer", PlugInManager.RENDERER);
                }
            }
            else if (OSUtils.IS_WINDOWS64) {
                /*
                 * Remove the native Renderers for 64-bit Windows because native JMF libs are not
                 * available for 64-bit machines.
                 */
                PlugInManager.removePlugIn("com.sun.media.renderer.video.GDIRenderer", PlugInManager.RENDERER);
                PlugInManager.removePlugIn("com.sun.media.renderer.video.DDRenderer", PlugInManager.RENDERER);
            }
        }
        else if (!OSUtils.IS_LINUX32) {
            if (renderers.contains("com.sun.media.renderer.video.LightWeightRenderer")
                    || renderers.contains("com.sun.media.renderer.video.AWTRenderer")) {
                // Remove XLibRenderer because it is native and JMF is supported on 32-bit machines only.
                PlugInManager.removePlugIn("com.sun.media.renderer.video.XLibRenderer", PlugInManager.RENDERER);
            }
        }
    }

    /**
     * The currently selected audio system.
     */
    private AudioSystem audioSystem;

    /**
     * The frame rate.
     */
    private int frameRate = DEFAULT_VIDEO_FRAMERATE;

    /**
     * The value of the <code>ConfigurationService</code> property
     * {@link MediaServiceImpl#DISABLE_SET_AUDIO_SYSTEM_PNAME} at the time of the initialization of this instance.
     */
    private final boolean setAudioSystemIsDisabled;

    /**
     * Current setting for video codec bitrate.
     */
    private int videoBitrate = -1;

    /**
     * The device that we'll be using for video capture.
     */
    private CaptureDeviceInfo videoCaptureDevice;

    /**
     * Current setting for video maximum bandwidth.
     */
    private int videoMaxBandwidth = -1;

    /**
     * The current resolution settings.
     */
    private Dimension videoSize;

    /**
     * Initializes a new <code>DeviceConfiguration</code> instance.
     */
    public DeviceConfiguration() {
        ConfigurationService cfg = LibJitsi.getConfigurationService();

        setAudioSystemIsDisabled = (cfg != null)
                && cfg.getBoolean(MediaServiceImpl.DISABLE_SET_AUDIO_SYSTEM_PNAME, false);

        // Seem to be throwing exceptions randomly, so we'll blindly catch them for now
        try {
            DeviceSystem.initializeDeviceSystems();
            extractConfiguredCaptureDevices();
        } catch (Exception ex) {
            Timber.e(ex, "Failed to initialize media.");
        }

        if (cfg != null) {
            cfg.addPropertyChangeListener(PROP_VIDEO_WIDTH, this);
            cfg.addPropertyChangeListener(PROP_VIDEO_HEIGHT, this);
            cfg.addPropertyChangeListener(PROP_VIDEO_FRAMERATE, this);
            cfg.addPropertyChangeListener(PROP_VIDEO_RTP_PACING_THRESHOLD, this);
        }

        registerCustomRenderers();
        fixRenderers();

        /*
         * Adds this instance as a PropertyChangeListener to all DeviceSystems which support
         * reinitialization/reloading in order to be able, for example, to switch from a
         * default/automatic selection of "None" to a DeviceSystem which has started providing at
         * least one device at runtime.
         */
        addDeviceSystemPropertyChangeListener();
    }

    /**
     * Adds this instance as a <code>PropertyChangeListener</code> to all <code>DeviceSystem</code>s which
     * support reinitialization/reloading in order to be able, for example, to switch from a
     * default/automatic selection of &quot;None&quot; to an <code>DeviceSystem</code> which has started
     * providing at least one device at runtime.
     */
    private void addDeviceSystemPropertyChangeListener() {
        // Track all kinds of DeviceSystems i.e audio and video.
        for (MediaType mediaType : MediaType.values()) {
            DeviceSystem[] deviceSystems = DeviceSystem.getDeviceSystems(mediaType);

            if (deviceSystems.length != 0) {
                for (DeviceSystem deviceSystem : deviceSystems) {
                    // It only makes sense to track DeviceSystems which support reinitialization/reloading.
                    if ((deviceSystem.getFeatures() & DeviceSystem.FEATURE_REINITIALIZE) != 0) {
                        deviceSystem.addPropertyChangeListener(this);
                    }
                }
            }
        }
    }

    /**
     * Detects audio capture devices configured through JMF and disable audio if none was found.
     */
    private void extractConfiguredAudioCaptureDevices() {
        if (!MediaServiceImpl.isMediaTypeSupportEnabled(MediaType.AUDIO))
            return;

        Timber.i("Looking for configured audio devices.");
        AudioSystem[] availableAudioSystems = getAvailableAudioSystems();

        if ((availableAudioSystems != null) && (availableAudioSystems.length != 0)) {
            AudioSystem audioSystem = getAudioSystem();

            if (audioSystem != null) {
                boolean audioSystemIsAvailable = setAudioSystemIsDisabled;

                /*
                 * XXX Presently, the method is used in execution paths which require the user's
                 * selection (i.e. the value of the associated ConfigurationService property) to be
                 * respected or execute too early in the life of the library/application to
                 * necessitate the preservation of the audioSystem value.
                 */
                // for (AudioSystem availableAudioSystem : availableAudioSystems) {
                //     if (!NoneAudioSystem.LOCATOR_PROTOCOL.equals(
                //           availableAudioSystem.getLocatorProtocol())
                //            && availableAudioSystem.equals(audioSystem)) {
                //        audioSystemIsAvailable = true;
                //        break;
                //    }
                // }
                if (!audioSystemIsAvailable)
                    audioSystem = null;
            }

            if (audioSystem == null) {
                ConfigurationService cfg = LibJitsi.getConfigurationService();

                if (cfg != null) {
                    String locatorProtocol = cfg.getString(PROP_AUDIO_SYSTEM);

                    if (locatorProtocol != null) {
                        for (AudioSystem availableAudioSystem : availableAudioSystems) {
                            if (locatorProtocol.equalsIgnoreCase(availableAudioSystem.getLocatorProtocol())) {
                                audioSystem = availableAudioSystem;
                                break;
                            }
                        }
                        /*
                         * If the user is not presented with any user interface which allows the
                         * selection of a particular AudioSystem, always use the configured
                         * AudioSystem regardless of whether it is available.
                         */
                        if (setAudioSystemIsDisabled && (audioSystem == null)) {
                            audioSystem = AudioSystem.getAudioSystem(locatorProtocol);
                        }
                    }
                }

                if (audioSystem == null)
                    audioSystem = availableAudioSystems[0];

                setAudioSystem(audioSystem, false);
            }
        }
    }

    /**
     * Detects capture devices configured through JMF and disable audio and/or video transmission if none were found.
     */
    private void extractConfiguredCaptureDevices() {
        extractConfiguredAudioCaptureDevices();
        extractConfiguredVideoCaptureDevices();
    }

    /**
     * Returns the configured video capture device with the specified output format.
     *
     * @param formats the output format array of the video format.
     *
     * @return CaptureDeviceInfo for the video device.
     */
    private CaptureDeviceInfo extractConfiguredVideoCaptureDevice(Format[] formats) {
        ConfigurationService cfg = LibJitsi.getConfigurationService();
        String videoDevName = (cfg == null) ? null : cfg.getString(PROP_VIDEO_DEVICE);
        CaptureDeviceInfo videoCaptureDevice = null;
        CaptureDeviceInfo tmpDevice = null;

        for (Format format : formats) {
            @SuppressWarnings("unchecked")
            List<CaptureDeviceInfo> videoCaptureDevices = CaptureDeviceManager.getDeviceList(format);
            if (videoCaptureDevices.size() > 0) {
                if (videoDevName == null) {
                    tmpDevice = videoCaptureDevices.get(0);
                    break;
                }
                else {
                    for (CaptureDeviceInfo captureDeviceInfo : videoCaptureDevices) {
                        if (videoDevName.equals(captureDeviceInfo.getName())) {
                            videoCaptureDevice = captureDeviceInfo;
                            Timber.i("Found video capture device: '%s'; format: '%s'", videoCaptureDevice.getName(), format);
                            break;
                        }
                    }
                }
                tmpDevice = videoCaptureDevices.get(0);
            }
        }

        if ((videoCaptureDevice == null) && (tmpDevice != null) && (cfg != null)) {
            // incorrect value specified in DB, so force and save to use last found valid videoCaptureDevice
            videoCaptureDevice = tmpDevice;
            cfg.setProperty(PROP_VIDEO_DEVICE, videoCaptureDevice.getName());
        }
        return videoCaptureDevice;
    }

    /**
     * Detects video capture devices configured through JMF and disable video if none was found.
     */
    public void extractConfiguredVideoCaptureDevices() {
        if (!MediaServiceImpl.isMediaTypeSupportEnabled(MediaType.VIDEO))
            return;

        ConfigurationService cfg = LibJitsi.getConfigurationService();
        String videoDevName = (cfg == null) ? null : cfg.getString(PROP_VIDEO_DEVICE);

        if (NoneAudioSystem.LOCATOR_PROTOCOL.equalsIgnoreCase(videoDevName)) {
            videoCaptureDevice = null;
        }
        else {
            Format[] formats = new Format[]{
                    new AVFrameFormat(),
                    new VideoFormat(Constants.ANDROID_SURFACE),
                    new VideoFormat(VideoFormat.RGB),
                    new VideoFormat(VideoFormat.YUV),
                    new VideoFormat(Constants.H264)
            };

            videoCaptureDevice = extractConfiguredVideoCaptureDevice(formats);
            if (videoCaptureDevice != null)
                Timber.i("Found configured video device: %s <= %s.", videoDevName, videoCaptureDevice.getName());
            else
                Timber.w("No Video Device was found for %s.", videoDevName);
        }
    }

    /**
     * Returns a device that we could use for audio capture.
     *
     * @return the CaptureDeviceInfo of a device that we could use for audio capture.
     */
    public CaptureDeviceInfo2 getAudioCaptureDevice() {
        AudioSystem audioSystem = getAudioSystem();
        return (audioSystem == null) ? null : audioSystem.getSelectedDevice(AudioSystem.DataFlow.CAPTURE);
    }

    /**
     * @return the audioNotifyDevice
     */
    public CaptureDeviceInfo getAudioNotifyDevice() {
        AudioSystem audioSystem = getAudioSystem();
        return (audioSystem == null) ? null : audioSystem.getSelectedDevice(AudioSystem.DataFlow.NOTIFY);
    }

    public AudioSystem getAudioSystem() {
        return audioSystem;
    }

    /**
     * Gets the list of audio capture devices which are available through this
     * <code>DeviceConfiguration</code>, amongst which is {@link #getAudioCaptureDevice()} and represent
     * acceptable values for {@link //#setAudioCaptureDevice(CaptureDeviceInfo, boolean)}
     *
     * @return an array of <code>CaptureDeviceInfo</code> describing the audio capture devices available
     * through this <code>DeviceConfiguration</code>
     */
    public List<CaptureDeviceInfo2> getAvailableAudioCaptureDevices() {
        return audioSystem.getDevices(AudioSystem.DataFlow.CAPTURE);
    }

    /**
     * Returns a list of available <code>AudioSystem</code>s. By default, an <code>AudioSystem</code> is
     * considered available if it reports at least one device.
     *
     * @return an array of available <code>AudioSystem</code>s
     */
    public AudioSystem[] getAvailableAudioSystems() {
        AudioSystem[] audioSystems = AudioSystem.getAudioSystems();
        if ((audioSystems == null) || (audioSystems.length == 0))
            return audioSystems;
        else {
            List<AudioSystem> audioSystemsWithDevices = new ArrayList<>();

            for (AudioSystem audioSystem : audioSystems) {
                if (!NoneAudioSystem.LOCATOR_PROTOCOL.equalsIgnoreCase(audioSystem.getLocatorProtocol())) {
                    List<CaptureDeviceInfo2> captureDevices = audioSystem.getDevices(AudioSystem.DataFlow.CAPTURE);

                    if ((captureDevices == null) || (captureDevices.size() <= 0)) {
                        if ((AudioSystem.FEATURE_NOTIFY_AND_PLAYBACK_DEVICES & audioSystem.getFeatures()) == 0) {
                            continue;
                        }
                        else {
                            List<CaptureDeviceInfo2> notifyDevices
                                    = audioSystem.getDevices(AudioSystem.DataFlow.NOTIFY);

                            if ((notifyDevices == null) || (notifyDevices.size() <= 0)) {
                                List<CaptureDeviceInfo2> playbackDevices
                                        = audioSystem.getDevices(AudioSystem.DataFlow.PLAYBACK);

                                if ((playbackDevices == null) || (playbackDevices.size() <= 0)) {
                                    continue;
                                }
                            }
                        }
                    }
                }
                audioSystemsWithDevices.add(audioSystem);
            }

            int audioSystemsWithDevicesCount = audioSystemsWithDevices.size();
            return (audioSystemsWithDevicesCount == audioSystems.length) ? audioSystems
                    : audioSystemsWithDevices.toArray(new AudioSystem[audioSystemsWithDevicesCount]);
        }
    }

    /**
     * Gets the list of video capture devices which are available through this
     * <code>DeviceConfiguration</code>, amongst which is {@link #getVideoCaptureDevice(MediaUseCase)}
     * and represent acceptable values for {@link #setVideoCaptureDevice(CaptureDeviceInfo, boolean)}
     *
     * @param useCase extract video capture devices that correspond to this <code>MediaUseCase</code>
     *
     * @return an array of <code>CaptureDeviceInfo</code> describing the video capture devices available
     * through this <code>DeviceConfiguration</code>
     */
    public List<CaptureDeviceInfo> getAvailableVideoCaptureDevices(MediaUseCase useCase) {
        Format[] formats = new Format[]{
                new AVFrameFormat(),
                new VideoFormat(Constants.ANDROID_SURFACE),
                new VideoFormat(VideoFormat.RGB),
                new VideoFormat(VideoFormat.YUV),
                new VideoFormat(Constants.H264)
        };

        Set<CaptureDeviceInfo> videoCaptureDevices = new HashSet<>();
        for (Format format : formats) {
            @SuppressWarnings("unchecked")
            Vector<CaptureDeviceInfo> cdis = CaptureDeviceManager.getDeviceList(format);

            if (useCase != MediaUseCase.ANY) {
                for (CaptureDeviceInfo cdi : cdis) {
                    MediaUseCase cdiUseCase
                            = DeviceSystem.LOCATOR_PROTOCOL_IMGSTREAMING.equalsIgnoreCase(cdi.getLocator().getProtocol())
                            ? MediaUseCase.DESKTOP : MediaUseCase.CALL;

                    if (cdiUseCase.equals(useCase))
                        videoCaptureDevices.add(cdi);
                }
            }
            else {
                videoCaptureDevices.addAll(cdis);
            }
        }
        return new ArrayList<>(videoCaptureDevices);
    }

    /**
     * Get the echo cancellation filter length (in milliseconds).
     *
     * @return echo cancel filter length in milliseconds
     */
    public long getEchoCancelFilterLengthInMillis() {
        ConfigurationService cfg = LibJitsi.getConfigurationService();
        long value = DEFAULT_AUDIO_ECHOCANCEL_FILTER_LENGTH_IN_MILLIS;

        if (cfg != null) {
            value = cfg.getLong(PROP_AUDIO_ECHOCANCEL_FILTER_LENGTH_IN_MILLIS, value);
        }
        return value;
    }

    /**
     * Gets the frame rate set on this <code>DeviceConfiguration</code>.
     *
     * @return the frame rate set on this <code>DeviceConfiguration</code>. The default value is
     * {@link #DEFAULT_VIDEO_FRAMERATE}
     */
    public int getFrameRate() {
        if (frameRate == -1) {
            ConfigurationService cfg = LibJitsi.getConfigurationService();
            int value = DEFAULT_VIDEO_FRAMERATE;

            if (cfg != null)
                value = cfg.getInt(PROP_VIDEO_FRAMERATE, value);
            frameRate = value;
        }
        return frameRate;
    }

    /**
     * Gets the video bitrate.
     *
     * @return the video codec bitrate. The default value is {@link #DEFAULT_VIDEO_BITRATE}.
     */
    public int getVideoBitrate() {
        if (videoBitrate == -1) {
            ConfigurationService cfg = LibJitsi.getConfigurationService();
            int value = DEFAULT_VIDEO_BITRATE;

            if (cfg != null)
                value = cfg.getInt(PROP_VIDEO_BITRATE, value);

            if (value > 0)
                videoBitrate = value;
            else
                videoBitrate = DEFAULT_VIDEO_BITRATE;
        }
        return videoBitrate;
    }

    /**
     * Returns a device that we could use for video capture.
     *
     * @param useCase <code>MediaUseCase</code> that will determined device we will use
     *
     * @return the CaptureDeviceInfo of a device that we could use for video capture.
     */
    public CaptureDeviceInfo getVideoCaptureDevice(MediaUseCase useCase) {
        CaptureDeviceInfo dev = null;

        switch (useCase) {
            case ANY:
            case CALL:
                dev = videoCaptureDevice;
                break;
            case DESKTOP:
                List<CaptureDeviceInfo> devs = getAvailableVideoCaptureDevices(MediaUseCase.DESKTOP);
                if (devs.size() > 0)
                    dev = devs.get(0);
                break;
            default:
                break;
        }
        return dev;
    }

    /**
     * Gets the maximum allowed video bandwidth.
     *
     * @return the maximum allowed video bandwidth. The default value is {@link #DEFAULT_VIDEO_RTP_PACING_THRESHOLD}.
     */
    public int getVideoRTPPacingThreshold() {
        if (videoMaxBandwidth == -1) {
            ConfigurationService cfg = LibJitsi.getConfigurationService();
            int value = DEFAULT_VIDEO_RTP_PACING_THRESHOLD;

            if (cfg != null)
                value = cfg.getInt(PROP_VIDEO_RTP_PACING_THRESHOLD, value);

            if (value > 0)
                videoMaxBandwidth = value;
            else
                videoMaxBandwidth = DEFAULT_VIDEO_RTP_PACING_THRESHOLD;
        }
        return videoMaxBandwidth;
    }

    /**
     * Gets the video size set on this <code>DeviceConfiguration</code>.
     *
     * @return the video size set on this <code>DeviceConfiguration</code>
     */
    public Dimension getVideoSize() {
        if (videoSize == null) {
            ConfigurationService cfg = LibJitsi.getConfigurationService();
            int width = DEFAULT_VIDEO_WIDTH;
            int height = DEFAULT_VIDEO_HEIGHT;

            if (cfg != null) {
                width = cfg.getInt(PROP_VIDEO_WIDTH, width);
                height = cfg.getInt(PROP_VIDEO_HEIGHT, height);
            }
            videoSize = new Dimension(width, height);
        }
        return videoSize;
    }

    /**
     * Notifies this <code>PropertyChangeListener</code> about <code>PropertyChangeEvent</code>s fired by,
     * for example, the <code>ConfigurationService</code> and the <code>DeviceSystem</code>s which support
     * reinitialization/reloading.
     *
     * @param ev the <code>PropertyChangeEvent</code> to notify this <code>PropertyChangeListener</code> about
     * and which describes the source and other specifics of the notification
     */
    public void propertyChange(PropertyChangeEvent ev) {
        String propertyName = ev.getPropertyName();

        if (AUDIO_CAPTURE_DEVICE.equals(propertyName)
                || AUDIO_NOTIFY_DEVICE.equals(propertyName)
                || AUDIO_PLAYBACK_DEVICE.equals(propertyName)) {
            /*
             * The current audioSystem may represent a default/automatic selection which may have
             * been selected because the user's selection may have been unavailable at the time.
             * Make sure that the user's selection is respected if possible.
             */
            extractConfiguredAudioCaptureDevices();

            /*
             * The specified PropertyChangeEvent has been fired by a DeviceSystem i.e. a certain
             * DeviceSystem is the source. Translate it to a PropertyChangeEvent fired by this
             * instance.
             */
            AudioSystem audioSystem = getAudioSystem();

            if (audioSystem != null) {
                CaptureDeviceInfo oldValue = (CaptureDeviceInfo) ev.getOldValue();
                CaptureDeviceInfo newValue = (CaptureDeviceInfo) ev.getNewValue();
                CaptureDeviceInfo device = (oldValue == null) ? newValue : oldValue;

                // Fire an event on the selected device only if the event is
                // generated by the selected audio system.
                if ((device == null)
                        || device.getLocator().getProtocol().equals(audioSystem.getLocatorProtocol())) {
                    firePropertyChange(propertyName, oldValue, newValue);
                }
            }
        }
        else if (DeviceSystem.PROP_DEVICES.equals(propertyName)) {
            if (ev.getSource() instanceof AudioSystem) {
                /*
                 * The current audioSystem may represent a default/automatic selection which may
                 * have been selected because the user's selection may have been unavailable at the
                 * time. Make sure that the user's selection is respected if possible.
                 */
                extractConfiguredAudioCaptureDevices();

                @SuppressWarnings("unchecked")
                List<CaptureDeviceInfo> newValue = (List<CaptureDeviceInfo>) ev.getNewValue();

                firePropertyChange(PROP_AUDIO_SYSTEM_DEVICES, ev.getOldValue(), newValue);
            }
        }
        else if (PROP_VIDEO_FRAMERATE.equals(propertyName)) {
            frameRate = -1;
        }
        else if (PROP_VIDEO_HEIGHT.equals(propertyName) || PROP_VIDEO_WIDTH.equals(propertyName)) {
            videoSize = null;
        }
        else if (PROP_VIDEO_RTP_PACING_THRESHOLD.equals(propertyName)) {
            videoMaxBandwidth = -1;
        }
    }

    /**
     * Registers the custom <code>Renderer</code> implementations defined by class name in
     * {@link #CUSTOM_RENDERERS} with JMF.
     */
    private void registerCustomRenderers() {
        @SuppressWarnings("unchecked")
        Vector<String> renderers = PlugInManager.getPlugInList(null, null, PlugInManager.RENDERER);
        boolean audioSupportIsDisabled = !MediaServiceImpl.isMediaTypeSupportEnabled(MediaType.AUDIO);
        boolean videoSupportIsDisabled = !MediaServiceImpl.isMediaTypeSupportEnabled(MediaType.VIDEO);
        boolean commit = false;

        for (String customRenderer : CUSTOM_RENDERERS) {
            if (customRenderer == null)
                continue;
            if (customRenderer.startsWith(".")) {
                customRenderer = "org.atalk.impl.neomedia" + ".jmfext.media.renderer" + customRenderer;
            }

            /*
             * Respect the MediaServiceImpl properties DISABLE_AUDIO_SUPPORT_PNAME and DISABLE_VIDEO_SUPPORT_PNAME.
             */
            if (audioSupportIsDisabled && customRenderer.contains(".audio."))
                continue;
            if (videoSupportIsDisabled && customRenderer.contains(".video."))
                continue;

            if ((renderers == null) || !renderers.contains(customRenderer)) {
                try {
                    Renderer customRendererInstance = (Renderer) Class.forName(customRenderer).getDeclaredConstructor().newInstance();
                    PlugInManager.addPlugIn(customRenderer, customRendererInstance.getSupportedInputFormats(),
                            null, PlugInManager.RENDERER);
                    commit = true;
                } catch (Throwable t) {
                    Timber.e(t, "Failed to register custom Renderer %s with JMF.", customRenderer);
                }
            }
        }

        /*
         * Just in case, bubble our JMF contributions at the top so that they are considered preferred.
         */
        int pluginType = PlugInManager.RENDERER;
        @SuppressWarnings("unchecked")
        Vector<String> plugins = PlugInManager.getPlugInList(null, null, pluginType);

        if (plugins != null) {
            int pluginCount = plugins.size();
            int pluginBeginIndex = 0;

            for (int pluginIndex = pluginCount - 1; pluginIndex >= pluginBeginIndex; ) {
                String plugin = plugins.get(pluginIndex);

                if (plugin.startsWith("org.atalk.")
                        || plugin.startsWith("net.java.sip.communicator.")) {
                    plugins.remove(pluginIndex);
                    plugins.add(0, plugin);
                    pluginBeginIndex++;
                    commit = true;
                }
                else
                    pluginIndex--;
            }
            PlugInManager.setPlugInList(plugins, pluginType);
            Timber.log(TimberLog.FINER, "Reordered plug-in list:%s", plugins);
        }

        if (commit && !MediaServiceImpl.isJmfRegistryDisableLoad()) {
            try {
                PlugInManager.commit();
            } catch (IOException ioex) {
                Timber.w("Failed to commit changes to the JMF plug-in list.");
            }
        }
    }

    public void setAudioSystem(AudioSystem audioSystem, boolean save) {
        if (this.audioSystem != audioSystem) {
            if (setAudioSystemIsDisabled && save) {
                throw new IllegalStateException(MediaServiceImpl.DISABLE_SET_AUDIO_SYSTEM_PNAME);
            }

            // Removes the registration to change listener only if this audio system does not support reinitialize.
            if ((this.audioSystem != null)
                    && (this.audioSystem.getFeatures() & DeviceSystem.FEATURE_REINITIALIZE) == 0) {
                this.audioSystem.removePropertyChangeListener(this);
            }

            AudioSystem oldValue = this.audioSystem;
            this.audioSystem = audioSystem;

            // Registers the new selected audio system. Even if every
            // FEATURE_REINITIALIZE audio system is registered already, the
            // check for duplicate entries will be done by the
            // addPropertyChangeListener method.
            if (this.audioSystem != null)
                this.audioSystem.addPropertyChangeListener(this);

            if (save) {
                ConfigurationService cfg = LibJitsi.getConfigurationService();

                if (cfg != null) {
                    if (this.audioSystem == null)
                        cfg.removeProperty(PROP_AUDIO_SYSTEM);
                    else
                        cfg.setProperty(PROP_AUDIO_SYSTEM, this.audioSystem.getLocatorProtocol());
                }
            }
            firePropertyChange(PROP_AUDIO_SYSTEM, oldValue, this.audioSystem);
        }
    }

    /**
     * Sets and stores the frame rate.
     *
     * @param frameRate the frame rate to be set on this <code>DeviceConfiguration</code>
     */
    public void setFrameRate(int frameRate) {
        this.frameRate = frameRate;

        ConfigurationService cfg = LibJitsi.getConfigurationService();
        if (cfg != null) {
            if (frameRate != DEFAULT_VIDEO_FRAMERATE)
                cfg.setProperty(PROP_VIDEO_FRAMERATE, frameRate);
            else
                cfg.removeProperty(PROP_VIDEO_FRAMERATE);
        }
    }

    /**
     * Sets and stores the video bitrate.
     *
     * @param videoBitrate the video codec bitrate
     */
    public void setVideoBitrate(int videoBitrate) {
        this.videoBitrate = videoBitrate;

        ConfigurationService cfg = LibJitsi.getConfigurationService();
        if (cfg != null) {
            if (videoBitrate != DEFAULT_VIDEO_BITRATE)
                cfg.setProperty(PROP_VIDEO_BITRATE, videoBitrate);
            else
                cfg.removeProperty(PROP_VIDEO_BITRATE);
        }
    }

    /**
     * Sets the device which is to be used by this <code>DeviceConfiguration</code> for video capture.
     *
     * @param device a <code>CaptureDeviceInfo</code> describing device to be used by this
     * <code>DeviceConfiguration</code> for video capture.
     * @param save whether we will save this option or not.
     */
    public void setVideoCaptureDevice(CaptureDeviceInfo device, boolean save) {
        if (videoCaptureDevice != device) {
            CaptureDeviceInfo oldDevice = videoCaptureDevice;
            videoCaptureDevice = device;

            if (save) {
                ConfigurationService cfg = LibJitsi.getConfigurationService();
                if (cfg != null) {
                    cfg.setProperty(PROP_VIDEO_DEVICE, (videoCaptureDevice == null)
                            ? NoneAudioSystem.LOCATOR_PROTOCOL : videoCaptureDevice.getName());
                }
            }

            // cmeng (20210402), new implementation to switch camera without involving jingle message sending
            // Does not require blocking after the latest change in MediaStreamImpl@setDevice()
            firePropertyChange(VIDEO_CAPTURE_DEVICE, oldDevice, device);
        }
    }

    /**
     * Sets and stores the maximum allowed video bandwidth.
     *
     * @param videoMaxBandwidth the maximum allowed video bandwidth
     */
    public void setVideoRTPPacingThreshold(int videoMaxBandwidth) {
        this.videoMaxBandwidth = videoMaxBandwidth;

        ConfigurationService cfg = LibJitsi.getConfigurationService();
        if (cfg != null) {
            if (videoMaxBandwidth != DEFAULT_VIDEO_RTP_PACING_THRESHOLD) {
                cfg.setProperty(PROP_VIDEO_RTP_PACING_THRESHOLD, videoMaxBandwidth);
            }
            else
                cfg.removeProperty(PROP_VIDEO_RTP_PACING_THRESHOLD);
        }
    }

    /**
     * Sets and stores the video size selected by user
     *
     * @param videoSize the video size to be set on this <code>DeviceConfiguration</code>
     */
    public void setVideoSize(Dimension videoSize) {
        ConfigurationService cfg = LibJitsi.getConfigurationService();
        if (cfg != null) {
            cfg.setProperty(PROP_VIDEO_HEIGHT, videoSize.height);
            cfg.setProperty(PROP_VIDEO_WIDTH, videoSize.width);
        }
        this.videoSize = videoSize;
        firePropertyChange(VIDEO_CAPTURE_DEVICE, videoCaptureDevice, videoCaptureDevice);
    }
}
