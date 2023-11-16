/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device;

import org.atalk.impl.neomedia.jmfext.media.renderer.audio.AbstractAudioRenderer;
import org.atalk.service.configuration.ConfigurationService;
import org.atalk.service.libjitsi.LibJitsi;
import org.atalk.service.resources.ResourceManagementService;
import org.atalk.util.MediaType;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.media.CaptureDeviceInfo;
import javax.media.MediaLocator;
import javax.media.Renderer;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.UnsupportedAudioFileException;

import timber.log.Timber;

/**
 * Represents a <code>DeviceSystem</code> which provides support for the devices to capture and play
 * back audio (media). Examples include implementations which integrate the native PortAudio,
 * PulseAudio libraries.
 *
 * @author Lyubomir Marinov
 * @author Vincent Lucas
 * @author Timothy Price
 * @author Eng Chong Meng
 */
public abstract class AudioSystem extends DeviceSystem
{
    /**
     * Enumerates the different types of media data flow of <code>CaptureDeviceInfo2</code>s contributed
     * by an <code>AudioSystem</code>.
     *
     * @author Lyubomir Marinov
     */
    public enum DataFlow
    {
        CAPTURE, NOTIFY, PLAYBACK
    }

    /**
     * The constant/flag (to be) returned by {@link #getFeatures()} in order to indicate that the
     * respective <code>AudioSystem</code> supports toggling its automatic gain control (AGC)
     * functionality between on and off. The UI will look for the presence of the flag in order to
     * determine whether a check box is to be shown to the user to enable toggling the automatic
     * gain control (AGC) functionality.
     */
    public static final int FEATURE_AGC = 1 << 4;

    /**
     * The constant/flag (to be) returned by {@link #getFeatures()} in order to indicate that the
     * respective <code>AudioSystem</code> supports toggling its denoise functionality between on and
     * off. The UI will look for the presence of the flag in order to determine whether a check box
     * is to be shown to the user to enable toggling the denoise functionality.
     */
    public static final int FEATURE_DENOISE = 1 << 1;

    /**
     * The constant/flag (to be) returned by {@link #getFeatures()} in order to indicate that the
     * respective <code>AudioSystem</code> supports toggling its echo cancellation functionality between
     * on and off. The UI will look for the presence of the flag in order to determine whether a
     * check box is to be shown to the user to enable toggling the echo cancellation functionality.
     */
    public static final int FEATURE_ECHO_CANCELLATION = 1 << 2;

    /**
     * The constant/flag (to be) returned by {@link #getFeatures()} in order to indicate that the
     * respective <code>AudioSystem</code> differentiates between playback and notification audio
     * devices. The UI, for example, will look for the presence of the flag in order to determine
     * whether separate combo boxes are to be shown to the user to allow the configuration of the
     * preferred playback and notification audio devices.
     */
    public static final int FEATURE_NOTIFY_AND_PLAYBACK_DEVICES = 1 << 3;

    /**
     * The protocol of the <code>MediaLocator</code>s identifying <code>AudioRecord</code> capture devices.
     */
    public static final String LOCATOR_PROTOCOL_AUDIORECORD = "audiorecord";

    public static final String LOCATOR_PROTOCOL_AUDIOSILENCE = "audiosilence";

    public static final String LOCATOR_PROTOCOL_JAVASOUND = "javasound";

    /**
     * The protocol of the <code>MediaLocator</code>s identifying <code>CaptureDeviceInfo</code>s
     * contributed by <code>MacCoreaudioSystem</code>.
     */
    public static final String LOCATOR_PROTOCOL_MACCOREAUDIO = "maccoreaudio";

    /**
     * The protocol of the <code>MediaLocator</code>s identifying OpenSL ES capture devices.
     */
    public static final String LOCATOR_PROTOCOL_OPENSLES = "opensles";

    public static final String LOCATOR_PROTOCOL_PORTAUDIO = "portaudio";

    public static final String LOCATOR_PROTOCOL_PULSEAUDIO = "pulseaudio";

    /**
     * The protocol of the <code>MediaLocator</code>s identifying <code>CaptureDeviceInfo</code>s
     * contributed by <code>WASAPISystem</code>.
     */
    public static final String LOCATOR_PROTOCOL_WASAPI = "wasapi";

    /**
     * The (base) name of the <code>ConfigurationService</code> property which indicates whether
     * automatic gain control (AGC) is to be performed for the captured audio.
     */
    private static final String PNAME_AGC = "automaticgaincontrol";

    /**
     * The (base) name of the <code>ConfigurationService</code> property which indicates whether noise
     * suppression is to be performed for the captured audio.
     */
    protected static final String PNAME_DENOISE = "denoise";

    /**
     * The (base) name of the <code>ConfigurationService</code> property which indicates whether noise
     * cancellation is to be performed for the captured audio.
     */
    protected static final String PNAME_ECHOCANCEL = "echocancel";

    public static AudioSystem getAudioSystem(String locatorProtocol)
    {
        AudioSystem[] audioSystems = getAudioSystems();
        AudioSystem audioSystemWithLocatorProtocol = null;

        if (audioSystems != null) {
            for (AudioSystem audioSystem : audioSystems) {
                if (audioSystem.getLocatorProtocol().equalsIgnoreCase(locatorProtocol)) {
                    audioSystemWithLocatorProtocol = audioSystem;
                    break;
                }
            }
        }
        return audioSystemWithLocatorProtocol;
    }

    public static AudioSystem[] getAudioSystems()
    {
        DeviceSystem[] deviceSystems = getDeviceSystems(MediaType.AUDIO);
        List<AudioSystem> audioSystems;

        if (deviceSystems == null)
            audioSystems = null;
        else {
            audioSystems = new ArrayList<>(deviceSystems.length);
            for (DeviceSystem deviceSystem : deviceSystems)
                if (deviceSystem instanceof AudioSystem)
                    audioSystems.add((AudioSystem) deviceSystem);
        }
        return (audioSystems == null)
                ? null : audioSystems.toArray(new AudioSystem[0]);
    }

    /**
     * The list of devices detected by this <code>AudioSystem</code> indexed by their category which is
     * among {@link DataFlow#CAPTURE}, {@link DataFlow#NOTIFY} and {@link DataFlow#PLAYBACK}.
     */
    private Devices[] devices;

    protected AudioSystem(String locatorProtocol)
            throws Exception
    {
        this(locatorProtocol, 0);
    }

    protected AudioSystem(String locatorProtocol, int features)
            throws Exception
    {
        super(MediaType.AUDIO, locatorProtocol, features);
    }

    /**
     * {@inheritDoc}
     *
     * Delegates to {@link #createRenderer(boolean)} with the value of the <code>playback</code> argument set to true.
     */
    @Override
    public Renderer createRenderer()
    {
        return createRenderer(true);
    }

    /**
     * Initializes a new <code>Renderer</code> instance which is to either perform playback on or sound
     * a notification through a device contributed by this system. The (default) implementation of
     * <code>AudioSystem</code> ignores the value of the <code>playback</code> argument and delegates to
     * {@link DeviceSystem#createRenderer()}.
     *
     * @param playback <code>true</code> if the new instance is to perform playback or <code>false</code> if the new
     * instance is to sound a notification
     * @return a new <code>Renderer</code> instance which is to either perform playback on or sound a
     * notification through a device contributed by this system
     */
    public Renderer createRenderer(boolean playback)
    {
        String className = getRendererClassName();
        Renderer renderer;

        if (className == null) {
            /*
             * There is no point in delegating to the super's createRenderer() because it will not
             * have a class to instantiate.
             */
            renderer = null;
        }
        else {
            Class<?> clazz;
            try {
                clazz = Class.forName(className);
            } catch (Throwable t) {
                if (t instanceof ThreadDeath)
                    throw (ThreadDeath) t;
                else {
                    clazz = null;
                    Timber.e(t, "Failed to get class %s", className);
                }
            }
            if (clazz == null) {
                /*
                 * There is no point in delegating to the super's createRenderer() because it will fail to get the class.
                 */
                renderer = null;
            }
            else if (!Renderer.class.isAssignableFrom(clazz)) {
                /*
                 * There is no point in delegating to the super's createRenderer() because it will
                 * fail to cast the new instance to a Renderer.
                 */
                renderer = null;
            }
            else {
                boolean superCreateRenderer;
                if (((getFeatures() & FEATURE_NOTIFY_AND_PLAYBACK_DEVICES) != 0)
                        && AbstractAudioRenderer.class.isAssignableFrom(clazz)) {
                    Constructor<?> constructor = null;

                    try {
                        constructor = clazz.getConstructor(boolean.class);
                    } catch (NoSuchMethodException nsme) {
                        /*
                         * Such a constructor is optional; so the failure to get it will be allowed,
                         * and the super's createRenderer() will be invoked.
                         */
                    } catch (SecurityException se) {
                        Timber.e(se, "SecurityException: Failed to initialize %s instance", className);
                    }
                    if ((constructor != null)) {
                        superCreateRenderer = false;
                        try {
                            renderer = (Renderer) constructor.newInstance(playback);
                        } catch (Throwable t) {
                            if (t instanceof ThreadDeath)
                                throw (ThreadDeath) t;
                            else {
                                renderer = null;
                                Timber.e(t, "Failed to initialize a new %s instance", className);
                            }
                        }
                        if ((renderer != null) && !playback) {
                            CaptureDeviceInfo device = getSelectedDevice(DataFlow.NOTIFY);
                            if (device == null) {
                                /*
                                 * If there is no notification device, then no notification is to be
                                 * sounded.
                                 */
                                renderer = null;
                            }
                            else {
                                MediaLocator locator = device.getLocator();
                                if (locator != null) {
                                    ((AbstractAudioRenderer<?>) renderer).setLocator(locator);
                                }
                            }
                        }
                    }
                    else {
                        /*
                         * The super's createRenderer() will be invoked because either there is no
                         * non-default constructor, or it is not meant to be invoked by the public.
                         */
                        superCreateRenderer = true;
                        renderer = null;
                    }
                }
                else {
                    /*
                     * The super's createRenderer() will be invoked because either this AudioSystem
                     * does not distinguish between playback and notify data flows, or the Renderer
                     * implementation class in not familiar.
                     */
                    superCreateRenderer = true;
                    renderer = null;
                }

                if (superCreateRenderer && (renderer == null))
                    renderer = super.createRenderer();
            }
        }
        return renderer;
    }

    /**
     * Obtains an audio input stream from the URL provided.
     *
     * @param uri a valid uri to a sound resource.
     * @return the input stream to audio data.
     * @throws IOException if an I/O exception occurs
     */
    public InputStream getAudioInputStream(String uri)
            throws IOException
    {
        ResourceManagementService resources = LibJitsi.getResourceManagementService();
        URL url = (resources == null) ? null : resources.getSoundURLForPath(uri);

        AudioInputStream audioStream = null;
        try {
            // Not found by the class loader? Perhaps it is a local file.
            if (url == null)
                url = new URL(uri);
            audioStream = javax.sound.sampled.AudioSystem.getAudioInputStream(url);
        } catch (MalformedURLException murle) {
            // Do nothing, the value of audioStream will remain equal to null.
        } catch (UnsupportedAudioFileException uafe) {
            Timber.e(uafe, "Unsupported format of audio stream %s", url);
        }
        return audioStream;
    }

    /**
     * Gets a <code>CaptureDeviceInfo2</code> which has been contributed by this <code>AudioSystem</code>,
     * supports a specific flow of media data (i.e. capture, notify or playback) and is identified
     * by a specific <code>MediaLocator</code>.
     *
     * @param dataFlow the flow of the media data supported by the <code>CaptureDeviceInfo2</code> to be returned
     * @param locator the <code>MediaLocator</code> of the <code>CaptureDeviceInfo2</code> to be returned
     * @return a <code>CaptureDeviceInfo2</code> which has been contributed by this instance, supports
     * the specified <code>dataFlow</code> and is identified by the specified <code>locator</code>
     */
    public CaptureDeviceInfo2 getDevice(DataFlow dataFlow, MediaLocator locator)
    {
        return devices[dataFlow.ordinal()].getDevice(locator);
    }

    /**
     * Gets the list of devices with a specific data flow: capture, notify or playback.
     *
     * @param dataFlow the data flow of the devices to retrieve: capture, notify or playback
     * @return the list of devices with the specified <code>dataFlow</code>
     */
    public List<CaptureDeviceInfo2> getDevices(DataFlow dataFlow)
    {
        return devices[dataFlow.ordinal()].getDevices();
    }

    /**
     * Returns the FMJ format of a specific <code>InputStream</code> providing audio media.
     *
     * @param audioInputStream the <code>InputStream</code> providing audio media to determine the FMJ format of
     * @return the FMJ format of the specified <code>audioInputStream</code> or <code>null</code> if such an
     * FMJ format could not be determined
     */
    public javax.media.format.AudioFormat getFormat(InputStream audioInputStream)
    {
        if ((audioInputStream instanceof AudioInputStream)) {
            AudioFormat af = ((AudioInputStream) audioInputStream).getFormat();

            return new javax.media.format.AudioFormat(javax.media.format.AudioFormat.LINEAR,
                    af.getSampleRate(), af.getSampleSizeInBits(), af.getChannels());
        }
        return null;
    }

    /**
     * Gets the (full) name of the <code>ConfigurationService</code> property which is associated with a
     * (base) <code>AudioSystem</code>-specific property name.
     *
     * @param basePropertyName the (base) <code>AudioSystem</code>-specific property name of which the associated (full)
     * <code>ConfigurationService</code> property name is to be returned
     * @return the (full) name of the <code>ConfigurationService</code> property which is associated
     * with the (base) <code>AudioSystem</code> -specific property name
     */
    protected String getPropertyName(String basePropertyName)
    {
        return DeviceConfiguration.PROP_AUDIO_SYSTEM + "." + getLocatorProtocol() + "." + basePropertyName;
    }

    /**
     * Gets the selected device for a specific data flow: capture, notify or playback.
     *
     * @param dataFlow the data flow of the selected device to retrieve: capture, notify or playback.
     * @return the selected device for the specified <code>dataFlow</code>
     */
    public CaptureDeviceInfo2 getSelectedDevice(DataFlow dataFlow)
    {
        return devices[dataFlow.ordinal()].getSelectedDevice(getDevices(dataFlow));
    }

    /**
     * Gets the indicator which determines whether automatic gain control (AGC) is to be performed
     * for captured audio.
     *
     * @return <code>true</code> if automatic gain control (AGC) is to be performed for captured audio;
     * otherwise, <code>false</code>
     */
    public boolean isAutomaticGainControl()
    {
        ConfigurationService cfg = LibJitsi.getConfigurationService();
        boolean value = ((getFeatures() & FEATURE_AGC) == FEATURE_AGC);

        if (cfg != null)
            value = cfg.getBoolean(getPropertyName(PNAME_AGC), value);
        return value;
    }

    /**
     * Gets the indicator which determines whether noise suppression is to be performed for captured
     * audio.
     *
     * @return <code>true</code> if noise suppression is to be performed for captured audio; otherwise,
     * <code>false</code>
     */
    public boolean isDenoise()
    {
        ConfigurationService cfg = LibJitsi.getConfigurationService();
        boolean value = ((getFeatures() & FEATURE_DENOISE) == FEATURE_DENOISE);

        if (cfg != null)
            value = cfg.getBoolean(getPropertyName(PNAME_DENOISE), value);
        return value;
    }

    /**
     * Gets the indicator which determines whether echo cancellation is to be performed for captured
     * audio.
     *
     * @return <code>true</code> if echo cancellation is to be performed for captured audio; otherwise,
     * <code>false</code>
     */
    public boolean isEchoCancel()
    {
        ConfigurationService cfg = LibJitsi.getConfigurationService();
        boolean value = ((getFeatures() & FEATURE_ECHO_CANCELLATION) == FEATURE_ECHO_CANCELLATION);

        if (cfg != null)
            value = cfg.getBoolean(getPropertyName(PNAME_ECHOCANCEL), value);
        return value;
    }

    /**
     * {@inheritDoc}
     *
     * Because <code>AudioSystem</code> may support playback and notification audio devices apart from
     * capture audio devices, fires more specific <code>PropertyChangeEvent</code>s than
     * <code>DeviceSystem</code>
     */
    @Override
    protected void postInitialize()
            throws Exception
    {
        try {
            try {
                postInitializeSpecificDevices(DataFlow.CAPTURE);
            } finally {
                if ((FEATURE_NOTIFY_AND_PLAYBACK_DEVICES & getFeatures()) != 0) {
                    try {
                        postInitializeSpecificDevices(DataFlow.NOTIFY);
                    } finally {
                        postInitializeSpecificDevices(DataFlow.PLAYBACK);
                    }
                }
            }
        } finally {
            super.postInitialize();
        }
    }

    /**
     * Sets the device lists after the different audio systems (PortAudio, PulseAudio, etc) have
     * finished detecting their devices.
     *
     * @param dataFlow the data flow of the devices to perform post-initialization on
     */
    protected void postInitializeSpecificDevices(DataFlow dataFlow)
    {
        // Gets all current active devices.
        List<CaptureDeviceInfo2> activeDevices = getDevices(dataFlow);
        // Gets the default device.
        Devices devices = this.devices[dataFlow.ordinal()];
        CaptureDeviceInfo2 selectedActiveDevice = devices.getSelectedDevice(activeDevices);

        // Sets the default device as selected. The function will fire a
        // property change only if the device has changed
        // from a previous configuration. The "set" part is important because
        // only the fired property event provides a
        // way to get the hotplugged devices working during a call.
        devices.setDevice(selectedActiveDevice, false);
    }

    /**
     * {@inheritDoc}
     *
     * Removes any capture, playback and notification devices previously detected by this
     * <code>AudioSystem</code> and prepares it for the execution of its
     * {@link DeviceSystem#doInitialize()} implementation (which detects all devices to be provided
     * by this instance).
     */
    @Override
    protected void preInitialize()
            throws Exception
    {
        super.preInitialize();

        if (devices == null) {
            devices = new Devices[3];
            devices[DataFlow.CAPTURE.ordinal()] = new CaptureDevices(this);
            devices[DataFlow.NOTIFY.ordinal()] = new NotifyDevices(this);
            devices[DataFlow.PLAYBACK.ordinal()] = new PlaybackDevices(this);
        }
    }

    /**
     * Fires a new <code>PropertyChangeEvent</code> to the <code>PropertyChangeListener</code>s registered
     * with this <code>PropertyChangeNotifier</code> in order to notify about a change in the value of a
     * specific property which had its old value modified to a specific new value.
     * <code>PropertyChangeNotifier</code> does not check whether the specified <code>oldValue</code> and
     * <code>newValue</code> are indeed different.
     *
     * @param property the name of the property of this <code>PropertyChangeNotifier</code> which had its value
     * changed
     * @param oldValue the value of the property with the specified name before the change
     * @param newValue the value of the property with the specified name after the change
     */
    void propertyChange(String property, Object oldValue, Object newValue)
    {
        firePropertyChange(property, oldValue, newValue);
    }

    /**
     * Sets the indicator which determines whether automatic gain control (AGC) is to be performed
     * for captured audio.
     *
     * @param automaticGainControl <code>true</code> if automatic gain control (AGC) is to be performed for captured audio;
     * otherwise, <code>false</code>
     */
    public void setAutomaticGainControl(boolean automaticGainControl)
    {
        ConfigurationService cfg = LibJitsi.getConfigurationService();
        if (cfg != null)
            cfg.setProperty(getPropertyName(PNAME_AGC), automaticGainControl);
    }

    /**
     * Sets the list of a kind of devices: capture, notify or playback.
     *
     * @param captureDevices The list of a kind of devices: capture, notify or playback.
     */
    protected void setCaptureDevices(List<CaptureDeviceInfo2> captureDevices)
    {
        devices[DataFlow.CAPTURE.ordinal()].setDevices(captureDevices);
    }

    /**
     * Sets the indicator which determines whether noise suppression is to be performed for captured
     * audio.
     *
     * @param denoise <code>true</code> if noise suppression is to be performed for captured audio; otherwise,
     * <code>false</code>
     */
    public void setDenoise(boolean denoise)
    {
        ConfigurationService cfg = LibJitsi.getConfigurationService();
        if (cfg != null)
            cfg.setProperty(getPropertyName(PNAME_DENOISE), denoise);
    }

    /**
     * Selects the active device.
     *
     * @param dataFlow the data flow of the device to set: capture, notify or playback
     * @param device The selected active device.
     * @param save Flag set to true in order to save this choice in the configuration. False otherwise.
     */
    public void setDevice(DataFlow dataFlow, CaptureDeviceInfo2 device, boolean save)
    {
        devices[dataFlow.ordinal()].setDevice(device, save);
    }

    /**
     * Sets the indicator which determines whether echo cancellation is to be performed for captured
     * audio.
     *
     * @param echoCancel <code>true</code> if echo cancellation is to be performed for captured audio; otherwise,
     * <code>false</code>
     */
    public void setEchoCancel(boolean echoCancel)
    {
        ConfigurationService cfg = LibJitsi.getConfigurationService();
        if (cfg != null)
            cfg.setProperty(getPropertyName(PNAME_ECHOCANCEL), echoCancel);
    }

    /**
     * Sets the list of the active devices.
     *
     * @param playbackDevices The list of the active devices.
     */
    protected void setPlaybackDevices(List<CaptureDeviceInfo2> playbackDevices)
    {
        devices[DataFlow.PLAYBACK.ordinal()].setDevices(playbackDevices);
        // The notify devices are the same as the playback devices.
        devices[DataFlow.NOTIFY.ordinal()].setDevices(playbackDevices);
    }
}
