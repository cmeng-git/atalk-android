/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device;

import androidx.annotation.NonNull;

import org.atalk.impl.timberlog.TimberLog;
import org.atalk.impl.neomedia.MediaServiceImpl;
import org.atalk.util.MediaType;
import org.atalk.util.OSUtils;
import org.atalk.util.event.PropertyChangeNotifier;

import java.io.IOException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import javax.media.CaptureDeviceInfo;
import javax.media.CaptureDeviceManager;
import javax.media.Format;
import javax.media.MediaLocator;
import javax.media.Renderer;
import javax.media.format.AudioFormat;
import javax.media.format.VideoFormat;

import timber.log.Timber;

/**
 * Represents the base of a supported device system/backend. A <code>DeviceSystem</code> is initialized at a certain time.
 * (usually, during the initialization of the <code>MediaService</code> implementation which is going to
 * use it) and it registers with FMJ the <code>CaptureDevice</code>s it will provide. In addition to
 * providing the devices for the purposes of capture, a <code>DeviceSystem</code> also provides the
 * devices on which playback is to be performed i.e. it acts as a <code>Renderer</code> factory via its
 * {@link #createRenderer()} method.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public abstract class DeviceSystem extends PropertyChangeNotifier {
    /**
     * The list of <code>DeviceSystem</code>s which have been initialized.
     */
    private static final List<DeviceSystem> deviceSystems = new LinkedList<>();

    /**
     * The constant/flag (to be) returned by {@link #getFeatures()} in order to indicate that the
     * respective <code>DeviceSystem</code> supports invoking its {@link #initialize()} more than once.
     */
    public static final int FEATURE_REINITIALIZE = 1;

    public static final String LOCATOR_PROTOCOL_ANDROIDCAMERA = "androidcamera";

    public static final String LOCATOR_PROTOCOL_CIVIL = "civil";

    public static final String LOCATOR_PROTOCOL_DIRECTSHOW = "directshow";

    public static final String LOCATOR_PROTOCOL_IMGSTREAMING = "imgstreaming";

    /**
     * The protocol of the <code>MediaLocator</code>s identifying <code>MediaRecorder</code> capture devices.
     */
    public static final String LOCATOR_PROTOCOL_MEDIARECORDER = "mediarecorder";

    public static final String LOCATOR_PROTOCOL_QUICKTIME = "quicktime";

    public static final String LOCATOR_PROTOCOL_VIDEO4LINUX2 = "video4linux2";

    /**
     * The list of <code>CaptureDeviceInfo</code>s representing the devices of this instance at the time
     * its {@link #preInitialize()} method was last invoked.
     */
    private static List<CaptureDeviceInfo> preInitializeDevices;

    public static final String PROP_DEVICES = "devices";

    /**
     * Returns a <code>List</code> of <code>CaptureDeviceInfo</code>s which are elements of a specific
     * <code>List</code> of <code>CaptureDeviceInfo</code>s and have a specific <code>MediaLocator</code> protocol.
     *
     * @param deviceList the <code>List</code> of <code>CaptureDeviceInfo</code> which are to be filtered based on the
     * specified <code>MediaLocator</code> protocol
     * @param locatorProtocol the protocol of the <code>MediaLocator</code>s of the <code>CaptureDeviceInfo</code>s
     * which are to be returned
     *
     * @return a <code>List</code> of <code>CaptureDeviceInfo</code>s which are elements of the specified
     * <code>deviceList</code> and have the specified <code>locatorProtocol</code>
     */
    protected static List<CaptureDeviceInfo> filterDeviceListByLocatorProtocol(
            List<CaptureDeviceInfo> deviceList, String locatorProtocol) {
        if ((deviceList != null) && (deviceList.size() > 0)) {
            Iterator<CaptureDeviceInfo> deviceListIter = deviceList.iterator();

            while (deviceListIter.hasNext()) {
                MediaLocator locator = deviceListIter.next().getLocator();

                if ((locator == null) || !locatorProtocol.equalsIgnoreCase(locator.getProtocol())) {
                    deviceListIter.remove();
                }
            }
        }
        return deviceList;
    }

    public static DeviceSystem[] getDeviceSystems(MediaType mediaType) {
        List<DeviceSystem> ret;
        synchronized (deviceSystems) {
            ret = new ArrayList<>(deviceSystems.size());
            for (DeviceSystem deviceSystem : deviceSystems)
                if (deviceSystem.getMediaType().equals(mediaType))
                    ret.add(deviceSystem);
        }
        return ret.toArray(new DeviceSystem[0]);
    }

    /**
     * Initializes the <code>DeviceSystem</code> instances which are to represent the supported device
     * systems/backends. The method may be invoked multiple times.
     * If a <code>DeviceSystem</code> has been initialized by a
     * previous invocation of the method, its {@link #initialize()} method will be called again as
     * part of the subsequent invocation only if the <code>DeviceSystem</code> in question returns a set
     * of flags from its {@link #getFeatures()} method which contains the constant/flag
     * {@link #FEATURE_REINITIALIZE}.
     */
    public static void initializeDeviceSystems() {
        /*
         * Detect the audio capture devices unless the configuration explicitly states that they are
         * to not be detected.
         */
        if (MediaServiceImpl.isMediaTypeSupportEnabled(MediaType.AUDIO)) {
            Timber.i("Initializing audio devices");
            initializeDeviceSystems(MediaType.AUDIO);
        }

        /*
         * Detect the video capture devices unless the configuration explicitly states that they are
         * to not be detected.
         */
        if (MediaServiceImpl.isMediaTypeSupportEnabled(MediaType.VIDEO)) {
            Timber.i("Initializing video devices");
            initializeDeviceSystems(MediaType.VIDEO);
        }
    }

    /**
     * Initializes the <code>DeviceSystem</code> instances which are to represent the supported device
     * systems/backends which are to capable of capturing and playing back media of a specific type
     * such as audio or video.
     *
     * @param mediaType the <code>MediaType</code> of the <code>DeviceSystem</code>s to be initialized
     */
    public static void initializeDeviceSystems(MediaType mediaType) {
        /*
         * The list of supported DeviceSystem implementations if hard-coded. The order of the
         * classes is significant and represents a decreasing preference with respect to which
         * DeviceSystem is to be picked up as the default one (for the specified mediaType, of course).
         */
        final String[] classNames;

        switch (mediaType) {
            case AUDIO:
                classNames = new String[]{
                        ".AudioRecordSystem",
                        ".OpenSLESSystem",
                        ".AudioSilenceSystem",
                        ".NoneAudioSystem"
                };
                break;
            case VIDEO:
                classNames = new String[]{
                        // MediaRecorderSystem not working for API-23; so remove the support
                        // OSUtils.IS_ANDROID ? ".MediaRecorderSystem" : null,
                        ".AndroidCameraSystem",
                        ".ImgStreamingSystem"
                };
                break;
            default:
                throw new IllegalArgumentException("mediaType");
        }
        initializeDeviceSystems(classNames);
    }

    /**
     * Initializes the <code>DeviceSystem</code> instances specified by the names of the classes which
     * implement them. If a <code>DeviceSystem</code> instance has already been initialized for a
     * specific class name, no new instance of the class in question will be initialized and rather
     * the {@link #initialize()} method of the existing <code>DeviceSystem</code> instance will be
     * invoked if the <code>DeviceSystem</code> instance returns a set of flags from its
     * {@link #getFeatures()} which contains {@link #FEATURE_REINITIALIZE}.
     *
     * @param classNames the names of the classes which extend the <code>DeviceSystem</code> class
     * and instances of which are to be initialized.
     */
    private static void initializeDeviceSystems(String[] classNames) {
        synchronized (deviceSystems) {
            String packageName = null;

            for (String className : classNames) {
                if (className == null)
                    continue;

                if (className.startsWith(".")) {
                    if (packageName == null)
                        packageName = DeviceSystem.class.getPackage().getName();
                    className = packageName + className;
                }

                // we can explicitly disable an audio system
                if (Boolean.getBoolean(className + ".disabled"))
                    continue;

                // Initialize a single instance per className.
                DeviceSystem deviceSystem = null;

                for (DeviceSystem aDeviceSystem : deviceSystems) {
                    if (aDeviceSystem.getClass().getName().equals(className)) {
                        deviceSystem = aDeviceSystem;
                        break;
                    }
                }

                boolean reinitialize;
                if (deviceSystem == null) {
                    reinitialize = false;

                    Object o = null;
                    try {
                        o = Class.forName(className).newInstance();
                    } catch (ClassNotFoundException e) {
                        Timber.e("Class not found: %s", e.getMessage());
                    } catch (Throwable t) {
                        if (t instanceof ThreadDeath) {
                            Timber.e("Fatal error while initialize Device Systems: %s; %s", className, t.getMessage());
                            throw (ThreadDeath) t;
                        }
                        else {
                            Timber.w("Failed to initialize %s; %s", className, t.getMessage());
                        }
                    }
                    if (o instanceof DeviceSystem) {
                        deviceSystem = (DeviceSystem) o;
                        if (!deviceSystems.contains(deviceSystem))
                            deviceSystems.add(deviceSystem);
                    }
                }
                else {
                    reinitialize = true;
                }

                // Reinitializing is an optional feature.
                if (reinitialize && ((deviceSystem.getFeatures() & FEATURE_REINITIALIZE) != 0)) {
                    try {
                        invokeDeviceSystemInitialize(deviceSystem);
                    } catch (Throwable t) {
                        if (t instanceof ThreadDeath) {
                            Timber.e("Fatal error while initialize Device Systems: %s; %s", className, t.getMessage());
                            throw (ThreadDeath) t;
                        }
                        else {
                            Timber.w("Failed to initialize %s; %s", className, t.getMessage());
                        }
                    }
                }
            }
        }
    }

    /**
     * Invokes {@link #initialize()} on a specific <code>DeviceSystem</code>. The method returns after
     * the invocation returns.
     *
     * @param deviceSystem the <code>DeviceSystem</code> to invoke <code>initialize()</code> on
     *
     * @throws Exception if an error occurs during the initialization of <code>initialize()</code> on the
     * specified <code>deviceSystem</code>
     */
    static void invokeDeviceSystemInitialize(DeviceSystem deviceSystem)
            throws Exception {
        invokeDeviceSystemInitialize(deviceSystem, false);
    }

    /**
     * Invokes {@link #initialize()} on a specific <code>DeviceSystem</code>.
     *
     * @param deviceSystem the <code>DeviceSystem</code> to invoke <code>initialize()</code> on
     * @param asynchronous <code>true</code> if the invocation is to be performed in a separate thread and the method
     * is to return immediately without waiting for the invocation to return; otherwise, <code>false</code>
     *
     * @throws Exception if an error occurs during the initialization of <code>initialize()</code> on the
     * specified <code>deviceSystem</code>
     */
    private static void invokeDeviceSystemInitialize(final DeviceSystem deviceSystem, boolean asynchronous)
            throws Exception {
        if (OSUtils.IS_WINDOWS || asynchronous) {
            /*
             * The use of Component Object Model (COM) technology is common on Windows. The
             * initialization of the COM library is done per thread. However, there are multiple
             * concurrency models which may interfere among themselves. Dedicate a new thread on
             * which the COM library has surely not been initialized per invocation of initialize().
             */

            final String className = deviceSystem.getClass().getName();
            final Throwable[] exception = new Throwable[1];
            Thread thread = new Thread(className + ".initialize()") {
                @Override
                public void run() {
                    try {
                        Timber.log(TimberLog.FINER, "Will initialize %s", className);
                        deviceSystem.initialize();
                        Timber.log(TimberLog.FINER, "Did initialize %s", className);
                    } catch (Throwable t) {
                        exception[0] = t;
                        if (t instanceof ThreadDeath)
                            throw (ThreadDeath) t;
                    }
                }
            };

            thread.setDaemon(true);
            thread.start();

            if (asynchronous)
                return;

            /*
             * Wait for the initialize() invocation on deviceSystem to return i.e. the thread to die.
             */
            boolean interrupted = false;
            while (thread.isAlive()) {
                try {
                    thread.join();
                } catch (InterruptedException ie) {
                    interrupted = true;
                }
            }
            if (interrupted)
                Thread.currentThread().interrupt();

            /* Re-throw any exception thrown by the thread. */
            Throwable t = exception[0];

            if (t != null) {
                if (t instanceof Exception)
                    throw (Exception) t;
                else
                    throw new UndeclaredThrowableException(t);
            }
        }
        else {
            deviceSystem.initialize();
        }
    }

    /**
     * The set of flags indicating which optional features are supported by this
     * <code>DeviceSystem</code>. For example, the presence of the flag {@link #FEATURE_REINITIALIZE}
     * indicates that this instance is able to deal with multiple consecutive invocations of its
     * {@link #initialize()} method.
     */
    private final int features;

    /**
     * The protocol of the <code>MediaLocator</code> of the <code>CaptureDeviceInfo</code>s (to be)
     * registered (with FMJ) by this <code>DeviceSystem</code>. The protocol is a unique identifier of a
     * <code>DeviceSystem</code>.
     */
    private final String locatorProtocol;

    /**
     * The <code>MediaType</code> of this <code>DeviceSystem</code> i.e. the type of the media that this
     * instance supports for capture and playback such as audio or video.
     */
    private final MediaType mediaType;

    protected DeviceSystem(MediaType mediaType, String locatorProtocol)
            throws Exception {
        this(mediaType, locatorProtocol, 0);
    }

    protected DeviceSystem(MediaType mediaType, String locatorProtocol, int features)
            throws Exception {
        if (mediaType == null)
            throw new NullPointerException("mediaType");
        if (locatorProtocol == null)
            throw new NullPointerException("locatorProtocol");

        this.mediaType = mediaType;
        this.locatorProtocol = locatorProtocol;
        this.features = features;
        invokeDeviceSystemInitialize(this);
    }

    /**
     * Initializes a new <code>Renderer</code> instance which is to perform playback on a device
     * contributed by this system.
     *
     * @return a new <code>Renderer</code> instance which is to perform playback on a device contributed
     * by this system or <code>null</code>
     */
    public Renderer createRenderer() {
        String className = getRendererClassName();
        if (className != null) {
            try {
                return (Renderer) Class.forName(className).newInstance();
            } catch (Throwable t) {
                if (t instanceof ThreadDeath)
                    throw (ThreadDeath) t;
                else {
                    Timber.e(t, "Failed to initialize a new %s instance", className);
                }
            }
        }
        return null;
    }

    /**
     * Invoked by {@link #initialize()} to perform the very logic of the initialization of this
     * <code>DeviceSystem</code>. This instance has been prepared for initialization by an earlier call
     * to {@link #preInitialize()} and the initialization will be completed with a subsequent call
     * to {@link #postInitialize()}.
     *
     * @throws Exception if an error occurs during the initialization of this instance. The initialization of
     * this instance will be completed with a subsequent call to <code>postInitialize()</code>
     * regardless of any <code>Exception</code> thrown by <code>doInitialize()</code>.
     */
    protected abstract void doInitialize()
            throws Exception;

    /**
     * Gets the flags indicating the optional features supported by this <code>DeviceSystem</code>.
     *
     * @return the flags indicating the optional features supported by this <code>DeviceSystem</code>.
     * The possible flags are among the <code>FEATURE_XXX</code> constants defined by the
     * <code>DeviceSystem</code> class and its extenders.
     */
    public final int getFeatures() {
        return features;
    }

    /**
     * Returns the format depending on the media type: AudioFormat for AUDIO, VideoFormat for VIDEO.
     * Otherwise, returns null.
     *
     * @return The format depending on the media type: AudioFormat for AUDIO, VideoFormat for VIDEO.
     * Otherwise, returns null.
     */
    public Format getFormat() {
        Format format;
        switch (getMediaType()) {
            case AUDIO:
                format = new AudioFormat(null);
                break;
            case VIDEO:
                format = new VideoFormat(null);
                break;
            default:
                format = null;
                break;
        }
        return format;
    }

    /**
     * Gets the protocol of the <code>MediaLocator</code>s of the <code>CaptureDeviceInfo</code>s (to be)
     * registered (with FMJ) by this <code>DeviceSystem</code>. The protocol is a unique identifier of a
     * <code>DeviceSystem</code>.
     *
     * @return the protocol of the <code>MediaLocator</code>s of the <code>CaptureDeviceInfo</code>s (to be)
     * registered (with FMJ) by this <code>DeviceSystem</code>
     */
    public final String getLocatorProtocol() {
        return locatorProtocol;
    }

    public final MediaType getMediaType() {
        return mediaType;
    }

    /**
     * Gets the name of the class which implements the <code>Renderer</code> interface to render media
     * on a playback or notification device associated with this <code>DeviceSystem</code>. Invoked by
     * {@link #createRenderer()}.
     *
     * @return the name of the class which implements the <code>Renderer</code> interface to render
     * media on a playback or notification device associated with this <code>DeviceSystem</code>
     * or <code>null</code> if no <code>Renderer</code> instance is to be created by the
     * <code>DeviceSystem</code> implementation or <code>createRenderer(boolean) is overridden.
     */
    protected String getRendererClassName() {
        return null;
    }

    /**
     * Initializes this <code>DeviceSystem</code> i.e. represents the native/system devices in the terms
     * of the application so that they may be utilized. For example, the capture devices are
     * represented as <code>CaptureDeviceInfo</code> instances registered with FMJ.
     * <p>
     * <b>Note</b>: The method is synchronized on this instance in order to guarantee that the whole
     * initialization procedure (which includes {@link #doInitialize()}) executes once at any given time.
     * </p>
     *
     * @throws Exception if an error occurs during the initialization of this <code>DeviceSystem</code>
     */
    protected final synchronized void initialize()
            throws Exception {
        preInitialize();
        try {
            doInitialize();
        } finally {
            postInitialize();
        }
    }

    /**
     * Invoked as part of the execution of {@link #initialize()} after the execution of
     * {@link #doInitialize()} regardless of whether the latter completed successfully. The
     * implementation of <code>DeviceSystem</code> fires a new <code>PropertyChangeEvent</code> to notify
     * that the value of the property {@link #PROP_DEVICES} of this instance may have changed i.e.
     * that the list of devices detected by this instance may have changed.
     */
    protected void postInitialize()
            throws Exception {
        try {
            Format format = getFormat();
            if (format != null) {
                /*
                 * Calculate the lists of old and new devices and report them in a
                 * PropertyChangeEvent about PROP_DEVICES.
                 */
                @SuppressWarnings("unchecked")
                List<CaptureDeviceInfo> cdis = CaptureDeviceManager.getDeviceList(format);
                List<CaptureDeviceInfo> postInitializeDevices = new ArrayList<>(cdis);

                if (preInitializeDevices != null) {
                    for (Iterator<CaptureDeviceInfo> preIter = preInitializeDevices.iterator(); preIter.hasNext(); ) {
                        if (postInitializeDevices.remove(preIter.next()))
                            preIter.remove();
                    }
                }
                /*
                 * Fire a PropertyChangeEvent but only if there is an actual change in the value of
                 * the property.
                 */
                int preInitializeDeviceCount = (preInitializeDevices == null) ? 0 : preInitializeDevices.size();
                if ((preInitializeDeviceCount != 0) || (postInitializeDevices.size() != 0)) {
                    firePropertyChange(PROP_DEVICES, preInitializeDevices, postInitializeDevices);
                }
            }
        } finally {
            preInitializeDevices = null;
        }
    }

    /**
     * Invoked as part of the execution of {@link #initialize()} before the execution of
     * {@link #doInitialize()}. The implementation of <code>DeviceSystem</code> removes from FMJ's
     * <code>CaptureDeviceManager</code> the <code>CaptureDeviceInfo</code>s whose <code>MediaLocator</code> has
     * the same protocol as {@link #getLocatorProtocol()} of this instance.
     */
    protected void preInitialize()
            throws Exception {
        Format format = getFormat();
        if (format != null) {
            @SuppressWarnings("unchecked")
            List<CaptureDeviceInfo> cdis = CaptureDeviceManager.getDeviceList(format);
            preInitializeDevices = new ArrayList<>(cdis);

            if (cdis.size() > 0) {
                boolean commit = false;

                for (CaptureDeviceInfo cdi : filterDeviceListByLocatorProtocol(cdis, getLocatorProtocol())) {
                    CaptureDeviceManager.removeDevice(cdi);
                    commit = true;
                }
                if (commit && !MediaServiceImpl.isJmfRegistryDisableLoad()) {
                    try {
                        CaptureDeviceManager.commit();
                    } catch (IOException ioe) {
                        /*
                         * We do not really need commit but we have it for historical reasons.
                         */
                        Timber.d(ioe, "Failed to commit CaptureDeviceManager");
                    }
                }
            }
        }
    }

    /**
     * Returns a human-readable representation of this <code>DeviceSystem</code>. The implementation of
     * <code>DeviceSystem</code> returns the protocol of the <code>MediaLocator</code>s of the
     * <code>CaptureDeviceInfo</code>s (to be) registered by this <code>DeviceSystem</code>.
     *
     * @return a <code>String</code> which represents this <code>DeviceSystem</code> in a human-readable form
     */
    @NonNull
    @Override
    public String toString() {
        return getLocatorProtocol();
    }
}
