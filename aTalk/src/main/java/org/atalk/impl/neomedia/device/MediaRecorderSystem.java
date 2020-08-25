/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.Camera;

import net.java.sip.communicator.util.UtilActivator;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.util.BackgroundManager;
import org.atalk.android.util.java.awt.Dimension;
import org.atalk.impl.neomedia.device.util.AndroidCamera;
import org.atalk.impl.neomedia.device.util.CameraUtils;
import org.atalk.impl.neomedia.format.ParameterizedVideoFormat;
import org.atalk.impl.neomedia.format.VideoMediaFormatImpl;
import org.atalk.service.configuration.ConfigurationService;
import org.atalk.service.neomedia.codec.Constants;
import org.atalk.util.MediaType;

import java.util.*;

import javax.media.*;

import androidx.core.content.ContextCompat;
import timber.log.Timber;

/**
 * Discovers and registers <tt>MediaRecorder</tt> capture devices with FMJ.
 *
 * @author Lyubomir Marinov
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class MediaRecorderSystem extends DeviceSystem
{
    private static final String VIDEO_SIZE = ".video.size";

    public static Dimension[] SUPPORTED_SIZES = new Dimension[]{};

    private static boolean isMediaRecorderInitialized = false;

    private static BackgroundManager backgroundManager = BackgroundManager.getInstance();

    /**
     * Initializes a new <tt>MediaRecorderSystem</tt> instance which discovers and registers
     * <tt>MediaRecorder</tt> capture devices with FMJ.
     *
     * @throws Exception if anything goes wrong while discovering and registering
     * <tt>MediaRecorder</tt> capture devices with FMJ
     */
    public MediaRecorderSystem()
            throws Exception
    {
        super(MediaType.VIDEO, LOCATOR_PROTOCOL_MEDIARECORDER);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doInitialize()
    {
        if (isMediaRecorderInitialized || (ContextCompat.checkSelfPermission(aTalkApp.getGlobalContext(),
                Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)) {
            return;
        }
        // Re-init of MediaRecorderSystem is handled within AndroidCameraSystem when app returns to Foreground
        else if (backgroundManager.isAppInBackground()) {
            Timber.w("Unable to initialize media recorder while in background");
            return;
        }

        // cleanup camera properties messed up by camera2
        // cleanMediaDB();

        int cameraCount = Camera.getNumberOfCameras();
        Timber.d("Number of cameras for MediaRecorder: %s", cameraCount);
        if (cameraCount < 1) {
            return;
        }

        ConfigurationService mConfig = UtilActivator.getConfigurationService();
        List<CaptureDeviceInfo> captureDevices = new LinkedList<>();
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();

        for (int cameraId = 0; cameraId < cameraCount; cameraId++) {
            // create a locator with camera id and its facing direction (cameraInfo)
            Camera.getCameraInfo(cameraId, cameraInfo);
            MediaLocator locator = AndroidCamera.constructLocator(LOCATOR_PROTOCOL_MEDIARECORDER, cameraId, cameraInfo);

            // Init the preferred sizes which is supported by the Camera.
            List<Dimension> sizes = new ArrayList<>();

            String vs = mConfig.getString(locator + VIDEO_SIZE, null);
            if (!CameraUtils.getSupportedSizes(vs, sizes)) {
                Camera camera = null;
                try {
                    camera = Camera.open(cameraId);
                    Camera.Parameters params = camera.getParameters();
                    List<Camera.Size> supportedSizes = params.getSupportedVideoSizes();
                    if (supportedSizes == null) {
                        /*
                         * The video size is the same as the preview size.
                         * MediaRecorder.setVideoSize(int,int) will most likely fail, print a line in
                         * logcat and not throw an exception (in DataSource.doStart()).
                         */
                        supportedSizes = params.getSupportedPreviewSizes();
                        if (supportedSizes != null) {
                            Timber.i("Preview sizes supported by %s: %s",
                                    locator, CameraUtils.cameraSizesToString(supportedSizes));
                        }
                    }
                    else {
                        Timber.i("Video sizes supported by %s: %s",
                                locator, CameraUtils.cameraSizesToString(supportedSizes));
                    }
                    mConfig.setProperty(locator + VIDEO_SIZE, CameraUtils.cameraSizesToString(supportedSizes));

                    // Keep a copy of the video resolution supportSizes for cameraId
                    CameraUtils.setCameraSupportSize(cameraId, supportedSizes);

                    // Selects only compatible dimensions
                    for (Camera.Size s : supportedSizes) {
                        Dimension candidate = new Dimension(s.width, s.height);
                        if (CameraUtils.isPreferredSize(candidate)) {
                            sizes.add(candidate);
                        }
                    }
                } finally {
                    if (camera != null)
                        camera.release();
                }
            }
            Timber.i("Video preferred: %s: %s", locator, CameraUtils.dimensionsToString(sizes));

            int count = sizes.size();
            if (count == 0)
                continue;

            // Saves supported video sizes
            Dimension[] array = new Dimension[count];
            sizes.toArray(array);
            SUPPORTED_SIZES = array;

            Format[] formats = new Format[count];
            for (int i = 0; i < count; i++) {
                formats[i] = new ParameterizedVideoFormat(Constants.H264, sizes.get(i),
                        Format.NOT_SPECIFIED /* maxDataLength */, Format.byteArray,
                        Format.NOT_SPECIFIED /* frameRate */, ParameterizedVideoFormat.toMap(
                        VideoMediaFormatImpl.H264_PACKETIZATION_MODE_FMTP, "1"));
            }

            // Create display name
            Camera.getCameraInfo(cameraId, cameraInfo);
            String name = cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT
                    ? aTalkApp.getResString(R.string.service_gui_settings_CAMERA_FRONT)
                    : aTalkApp.getResString(R.string.service_gui_settings_CAMERA_BACK);
            name += " (MediaRecoder#" + cameraId + ")";

            AndroidCamera device = new AndroidCamera(name, locator, formats);

            // XXX Prefer the front-facing camera over the back-facing one.
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT)
                captureDevices.add(0, device);
            else
                captureDevices.add(device);
        }

        if (!captureDevices.isEmpty()) {
            for (CaptureDeviceInfo captureDevice : captureDevices)
                CaptureDeviceManager.addDevice(captureDevice);
        }
        isMediaRecorderInitialized = true;
    }

    public static void cleanMediaDB()
    {
        String[] prefixes = new String[]{LOCATOR_PROTOCOL_MEDIARECORDER, LOCATOR_PROTOCOL_ANDROIDCAMERA};

        ConfigurationService cs = UtilActivator.getConfigurationService();
        for (String prefix : prefixes) {
            List<String> mediaProperties = cs.getPropertyNamesByPrefix(prefix, false);
            for (String property : mediaProperties) {
                cs.setProperty(property, null);
            }
        }
    }
}
