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

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.util.BackgroundManager;
import org.atalk.android.util.java.awt.Dimension;
import org.atalk.impl.neomedia.device.util.AndroidCamera;
import org.atalk.impl.neomedia.device.util.CameraUtils;
import org.atalk.impl.neomedia.format.ParameterizedVideoFormat;
import org.atalk.impl.neomedia.format.VideoMediaFormatImpl;
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
    private static final String CAMERA_FACING_BACK = "CAMERA_FACING_BACK";

    private static final String CAMERA_FACING_FRONT = "CAMERA_FACING_FRONT";

    /**
     * The protocol of the <tt>MediaLocator</tt>s identifying <tt>MediaRecorder</tt> capture devices.
     */
    private static final String LOCATOR_PROTOCOL = LOCATOR_PROTOCOL_MEDIARECORDER;

    public static Dimension[] SUPPORTED_SIZES = new Dimension[]{};

    private static boolean isMediaRecorderInitialized = false;

    private static BackgroundManager backgroundManager = BackgroundManager.getInstance();

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
        super(MediaType.VIDEO, LOCATOR_PROTOCOL);
    }

    /**
     * Constructs a <tt>String</tt> representation of a specific <tt>Iterable</tt> of
     * <tt>Dimension</tt>s. The elements of the specified <tt>Iterable</tt> are delimited by &quot;,
     * &quot;. The method has been introduced to match {@link CameraUtils#cameraSizesToString(Iterable)}.
     *
     * @param sizes the <tt>Iterable</tt> of <tt>Dimension</tt>s which is to be represented as a
     * human-readable <tt>String</tt>
     * @return the human-readable <tt>String</tt> representation of the specified <tt>sizes</tt>
     */
    private static String dimensionsToString(Iterable<Dimension> sizes)
    {
        StringBuilder s = new StringBuilder();
        for (Dimension size : sizes) {
            if (s.length() != 0)
                s.append(", ");
            s.append(size.width).append('x').append(size.height);
        }
        return s.toString();
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

        Camera.CameraInfo cameraInfo = null;
        List<CaptureDeviceInfo> captureDevices = new LinkedList<>();

        for (int cameraId = 0; cameraId < cameraCount; cameraId++) {
            if (cameraInfo == null)
                cameraInfo = new Camera.CameraInfo();
            Camera.getCameraInfo(cameraId, cameraInfo);
            String facing;

            switch (cameraInfo.facing) {
                case Camera.CameraInfo.CAMERA_FACING_BACK:
                    facing = CAMERA_FACING_BACK;
                    break;
                case Camera.CameraInfo.CAMERA_FACING_FRONT:
                    facing = CAMERA_FACING_FRONT;
                    break;
                default:
                    facing = "";
                    break;
            }
            // Pick up the preferred sizes which is supported by the Camera.
            List<Dimension> sizes = new ArrayList<>(CameraUtils.PREFERRED_SIZES.length);

            // Locator protocol contains camera id and it's facing
            MediaLocator locator = AndroidCamera.constructLocator(LOCATOR_PROTOCOL, cameraId, cameraInfo);
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
                        Timber.d("Preview sizes supported by %s: %s",
                                locator, CameraUtils.cameraSizesToString(supportedSizes));
                    }
                }
                else {
                    Timber.d("Video sizes supported by %s: %s",
                            locator, CameraUtils.cameraSizesToString(supportedSizes));
                }
                if (supportedSizes != null) {
                    // Keep a copy of the video resolution supportSizes for cameraId
                    CameraUtils.setCameraSupportSize(cameraId, supportedSizes);

                    for (Dimension preferredSize : CameraUtils.PREFERRED_SIZES) {
                        for (Camera.Size supportedSize : supportedSizes) {
                            if ((preferredSize.height == supportedSize.height)
                                    && (preferredSize.width == supportedSize.width)) {
                                sizes.add(preferredSize);
                            }
                        }
                    }
                    Timber.d("Sizes supported by %s: %s", locator, dimensionsToString(sizes));
                }
            } finally {
                if (camera != null)
                    camera.release();
            }

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
            Resources res = aTalkApp.getAppResources();
            String name = facing.equals(CAMERA_FACING_FRONT) ? res
                    .getString(R.string.service_gui_settings_FRONT_CAMERA) : res
                    .getString(R.string.service_gui_settings_BACK_CAMERA);
            name += " (MediaRecorder)";

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
