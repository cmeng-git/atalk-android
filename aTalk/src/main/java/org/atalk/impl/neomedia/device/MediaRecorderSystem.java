/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.Resources;
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
import org.atalk.service.neomedia.MediaType;
import org.atalk.service.neomedia.codec.Constants;

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

    private ConfigurationService mConfig = null;

    /**
     * Initializes a new <tt>MediaRecorderSystem</tt> instance which discovers and registers
     * <tt>MediaRecorder</tt> capture devices with FMJ.
     *
     * @throws Exception if anything goes wrong while discovering and registering <tt>MediaRecorder</tt>
     * capture devices with FMJ
     */
    public MediaRecorderSystem()
            throws Exception
    {
        super(MediaType.VIDEO, LOCATOR_PROTOCOL_MEDIARECORDER);
    }

    protected void doInitialize()
    {
        int cameraCount = Camera.getNumberOfCameras();
        // Re-init of MediaRecorderSystem is handled with AndroidCameraSystem
        if (backgroundManager.isAppInBackground() || (cameraCount < 1) || isMediaRecorderInitialized
                || (ContextCompat.checkSelfPermission(aTalkApp.getGlobalContext(),
                Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)) {
            if (backgroundManager.isAppInBackground()) {
                Timber.w("Unable to initialize media recorder while in background #: %s;", cameraCount);
            }
            return;
        }
        mConfig = UtilActivator.getConfigurationService();
        List<CaptureDeviceInfo> captureDevices = new LinkedList<>();
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();

        for (int cameraId = 0; cameraId < cameraCount; cameraId++) {
            // to remove obsolete properties for locator facing back if exist
            MediaLocator locator0 = AndroidCamera.constructLocator(LOCATOR_PROTOCOL_MEDIARECORDER, cameraId, cameraInfo);

            // create locator with camera id and its facing direction (cameraInfo)
            Camera.getCameraInfo(cameraId, cameraInfo);
            MediaLocator locator = AndroidCamera.constructLocator(LOCATOR_PROTOCOL_MEDIARECORDER, cameraId, cameraInfo);

            // Pick up the preferred sizes which is supported by the Camera.
            List<Dimension> sizes = new ArrayList<>();

            String vs = mConfig.getString(locator + VIDEO_SIZE, null);
            if (!CameraUtils.getSupportedSizes(vs, sizes)) {
                // Added in v2.1.6: remove obsolete/incorrect property; to be removed in future release
                mConfig.setProperty(locator0 + VIDEO_SIZE, null);

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
                    ? aTalkApp.getResString(R.string.service_gui_settings_FRONT_CAMERA)
                    : aTalkApp.getResString(R.string.service_gui_settings_BACK_CAMERA);
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
}
