/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.text.TextUtils;
import android.view.Surface;

import net.java.sip.communicator.util.UtilActivator;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.util.BackgroundManager;
import org.atalk.android.util.java.awt.Dimension;
import org.atalk.impl.neomedia.NeomediaActivator;
import org.atalk.impl.neomedia.codec.video.AndroidEncoder;
import org.atalk.impl.neomedia.device.util.AndroidCamera;
import org.atalk.impl.neomedia.device.util.CameraUtils;
import org.atalk.service.configuration.ConfigurationService;
import org.atalk.service.neomedia.codec.Constants;
import org.atalk.util.MediaType;

import java.util.ArrayList;
import java.util.List;

import javax.media.*;
import javax.media.format.VideoFormat;
import javax.media.format.YUVFormat;

import androidx.core.content.ContextCompat;
import timber.log.Timber;

/**
 * Device system that provides YUV and Surface format camera data source. YUV frames are captured
 * using camera preview callback. Surface is passed directly through static methods to encoders.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class AndroidCameraSystem extends DeviceSystem implements BackgroundManager.Listener
{
    private static final String VIDEO_SIZE = ".video.size";
    private static final String PREVIEW_FORMAT = ".preview.format";

    public static boolean isCameraInitialized = false;

    private static BackgroundManager backgroundManager = BackgroundManager.getInstance();

    /**
     * Creates a new instance of <tt>AndroidCameraSystem</tt>.
     *
     * @throws Exception from super
     */
    public AndroidCameraSystem()
            throws Exception
    {
        super(MediaType.VIDEO, LOCATOR_PROTOCOL_ANDROIDCAMERA);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doInitialize()
    {
        if (isCameraInitialized || (ContextCompat.checkSelfPermission(aTalkApp.getGlobalContext(),
                Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)) {
            return;
        }
        // Register the listener to re-init AndroidCameraSystem when aTalk returns to Foreground
        else if (backgroundManager.isAppInBackground()) {
            Timber.i(" Registered a listener to re-init AndroidCameraSystem when aTalk returns to Foreground");
            backgroundManager.registerListener(this);
            return;
        }

        int cameraCount = Camera.getNumberOfCameras();
        Timber.d("Number of android cameras: %s", cameraCount);
        if (cameraCount < 1) {
            return;
        }

        ConfigurationService mConfig = UtilActivator.getConfigurationService();
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();

        for (int cameraId = 0; cameraId < cameraCount; cameraId++) {
            // create a locator with camera id and its facing direction (cameraInfo)
            Camera.getCameraInfo(cameraId, cameraInfo);
            MediaLocator locator = AndroidCamera.constructLocator(LOCATOR_PROTOCOL_ANDROIDCAMERA, cameraId, cameraInfo);

            // Pick up the preferred sizes which is supported by the Camera.
            List<Dimension> sizes = new ArrayList<>();
            List<Integer> camFormats = new ArrayList<>();

            String pFormat = mConfig.getString(locator + PREVIEW_FORMAT, null);
            if (!TextUtils.isEmpty(pFormat)) {
                String[] pfs = pFormat.split(", ");
                for (String pfx : pfs) {
                    if ("YV12".equals(pfx))
                        camFormats.add(ImageFormat.YV12);
                    else if ("NV21".equals(pfx))
                        camFormats.add(ImageFormat.NV21);
                }
            }

            String vSize = mConfig.getString(locator + VIDEO_SIZE, null);
            if (TextUtils.isEmpty(pFormat) || !CameraUtils.getSupportedSizes(vSize, sizes)) {
                Camera camera = null;
                try {
                    // Pick up the preferred sizes which are supported by the Camera.
                    camera = Camera.open(cameraId);
                    Camera.Parameters params = camera.getParameters();

                    List<Camera.Size> previewSizes = params.getSupportedVideoSizes();
                    if (previewSizes == null) {
                        /*
                         * The video size is the same as the preview size.
                         * MediaRecorder.setVideoSize(int,int) will most likely fail, print a line in
                         * logcat and not throw an exception (in DataSource.doStart()).
                         */
                        Timber.w("getSupportedVideoSizes returned null for camera: %s", cameraId);
                        previewSizes = params.getSupportedPreviewSizes();
                    }

                    vSize = CameraUtils.cameraSizesToString(previewSizes);
                    mConfig.setProperty(locator + VIDEO_SIZE, vSize);

                    // Selects only compatible dimensions
                    for (Camera.Size s : previewSizes) {
                        Dimension candidate = new Dimension(s.width, s.height);
                        if (CameraUtils.isPreferredSize(candidate)) {
                            sizes.add(candidate);
                        }
                    }
                    camFormats = params.getSupportedPreviewFormats();
                    pFormat = CameraUtils.cameraImgFormatsToString(camFormats);
                    mConfig.setProperty(locator + PREVIEW_FORMAT, pFormat);
                } finally {
                    if (camera != null)
                        camera.release();
                }
            }
            Timber.i("#Video supported: %s (%s)\nsupported: %s\npreferred: %s", locator, pFormat, vSize,
                    CameraUtils.dimensionsToString(sizes));

            // Surface format
            List<Format> formats = new ArrayList<>();
            if (AndroidEncoder.isDirectSurfaceEnabled()) {
                // TODO: camera will not be detected if only surface format is reported
                for (Dimension size : sizes) {
                    formats.add(new VideoFormat(Constants.ANDROID_SURFACE, size,
                            Format.NOT_SPECIFIED, Surface.class, Format.NOT_SPECIFIED));
                }
                /*
                 * VideoFormat surfaceFormat = new VideoFormat( Constants.ANDROID_SURFACE, //new
                 * Dimension(176,144), //new Dimension(352,288), new Dimension(1280,720),
                 * Format.NOT_SPECIFIED, Surface.class, Format.NOT_SPECIFIED);
                 * formats.add(surfaceFormat);
                 */
            }

            // YUV format
            if (camFormats.contains(ImageFormat.YV12)) {
                // Image formats
                for (Dimension size : sizes) {
                    formats.add(new YUVFormat(size, Format.NOT_SPECIFIED, Format.byteArray,
                            YUVFormat.YUV_420, Format.NOT_SPECIFIED, Format.NOT_SPECIFIED,
                            Format.NOT_SPECIFIED, Format.NOT_SPECIFIED, Format.NOT_SPECIFIED,
                            Format.NOT_SPECIFIED));
                }
                // 40x30, 176x144, 320x240, 352x288, 640x480,
                // 704x576, 720x480, 720x576, 768x432, 1280x720
                /*
                 * Format newFormat = new YUVFormat( //new Dimension(40,30), //new
                 * Dimension(176,144), //new Dimension(320,240), new Dimension(352,288), //new
                 * Dimension(640,480), //new Dimension(704,576), //new Dimension(720,480), //new
                 * Dimension(720,576), //new Dimension(768,432), //new Dimension(1280,720),
                 * Format.NOT_SPECIFIED, Format.byteArray, YUVFormat.YUV_420, Format.NOT_SPECIFIED,
                 * Format.NOT_SPECIFIED, Format.NOT_SPECIFIED, Format.NOT_SPECIFIED,
                 * Format.NOT_SPECIFIED, Format.NOT_SPECIFIED); formats.add(newFormat);
                 */
            }

            // Construct display name
            Camera.getCameraInfo(cameraId, cameraInfo);
            String name = (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT)
                    ? aTalkApp.getResString(R.string.service_gui_settings_CAMERA_FRONT)
                    : aTalkApp.getResString(R.string.service_gui_settings_CAMERA_BACK);
            name += " (AndroidCamera#" + cameraId + ")";
            if (formats.isEmpty()) {
                Timber.e("No supported formats reported by camera: %s", locator);
                continue;
            }
            AndroidCamera device = new AndroidCamera(name, locator, formats.toArray(new Format[0]));
            CaptureDeviceManager.addDevice(device);
        }
        isCameraInitialized = true;
    }

    /*
     * Re-init all deviceSystem that are associated with camera when app returns to foreground.
     * Must update VideoCaptureDevices() at the end of re-init for system to start using them
     * Unregister listener on completion.
     */
    @Override
    public void onAppForeground()
    {
        try {
            Timber.w("Starting re-int android camera on returning to foreground!");
            initializeDeviceSystems(MediaType.VIDEO);
            if (isCameraInitialized) {
                DeviceConfiguration devConfig = NeomediaActivator.getMediaServiceImpl().getDeviceConfiguration();
                devConfig.extractConfiguredVideoCaptureDevices();
                backgroundManager.unregisterListener(this);
            }
        } catch (Exception e) {
            Timber.w("Failed to re-init android camera");
        }
    }

    @Override
    public void onAppBackground()
    {
    }
}