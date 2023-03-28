/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.impl.neomedia.device;

import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.text.TextUtils;
import android.util.Size;
import android.view.Surface;

import net.java.sip.communicator.util.UtilActivator;

import org.apache.commons.lang3.ArrayUtils;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.impl.neomedia.codec.video.AndroidEncoder;
import org.atalk.impl.neomedia.device.util.AndroidCamera;
import org.atalk.impl.neomedia.device.util.CameraUtils;
import org.atalk.service.configuration.ConfigurationService;
import org.atalk.service.neomedia.codec.Constants;
import org.atalk.util.MediaType;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.media.CaptureDeviceManager;
import javax.media.Format;
import javax.media.MediaLocator;
import javax.media.format.VideoFormat;
import javax.media.format.YUVFormat;

import timber.log.Timber;

/**
 * Device system that provides YUV and Surface format camera data source. YUV frames are captured
 * using camera preview callback. Surface is passed directly through static methods to encoders.
 *
 * @author Eng Chong Meng
 */
public class AndroidCameraSystem extends DeviceSystem {
    private static final String VIDEO_SIZE = ".video.size";
    public static final String PREVIEW_FORMAT = ".preview.format";

    /**
     * Supported preview sizes by android camera for user selection
     */
    public static Dimension[] SUPPORTED_SIZES = new Dimension[]{};

    public static boolean isCameraInitialized = false;

    /**
     * Creates a new instance of <code>AndroidCameraSystem</code>.
     *
     * @throws Exception from super
     */
    public AndroidCameraSystem()
            throws Exception {
        super(MediaType.VIDEO, LOCATOR_PROTOCOL_ANDROIDCAMERA);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doInitialize() {
        if (isCameraInitialized) {
            return;
        }

        // cleanup camera properties messed up by camera1/2 testing during development
        // cleanMediaDB();

        try {
            CameraManager cameraManager = aTalkApp.getCameraManager();
            String[] cameraIdList = cameraManager.getCameraIdList();

            // Timber.d("Number of android cameras: %s", cameraIdList.length);
            if (cameraIdList.length == 0) {
                return;
            }

            ConfigurationService mConfig = UtilActivator.getConfigurationService();
            for (String cameraId : cameraIdList) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }

                // create a locator with camera id and its facing direction
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                MediaLocator locator = AndroidCamera.constructLocator(LOCATOR_PROTOCOL_ANDROIDCAMERA, cameraId, facing);

                // Retrieve the camera formats supported by this cameraId from DB
                String sFormat = mConfig.getString(locator + PREVIEW_FORMAT, null);
                List<Integer> cameraFormats = CameraUtils.stringToCameraFormat(sFormat);

                // List of preferred resolutions which is supported by the Camera.
                List<Dimension> sizes = new ArrayList<>();
                String vSize = mConfig.getString(locator + VIDEO_SIZE, null);
                if (TextUtils.isEmpty(sFormat) || !CameraUtils.getSupportedSizes(vSize, sizes)) {
                    /*
                     * Check if the Camera API2 is supported with camCharacteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
                     * Return an int corresponding to the level of support for Camera API2.
                     * If equal to CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY, cameraId does not support Camera API2.
                     */
                    Integer sLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
                    if (CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY == sLevel) {
                        Timber.w("Camera API2 is not supported for camera: %s", cameraId);
                        continue;
                    }
                    else {
                        Timber.d("Camera API2 is supported for camera: %s; Level: %s", cameraId, sLevel);
                    }

                    Size[] previewSizes = map.getOutputSizes(SurfaceTexture.class);
                    if (previewSizes == null) {
                        /*
                         * The video size is the same as the preview size.
                         * MediaRecorder.setVideoSize(int,int) will most likely fail, print a line in
                         * logcat and not throw an exception (in DataSource.doStart()).
                         */
                        Timber.w("Output Preview Sizes returned null for camera: %s", cameraId);
                        continue;
                    }

                    vSize = CameraUtils.cameraSizesToString(previewSizes);
                    // Save to DB and keep a copy of the video resolution supportSizes for cameraId
                    mConfig.setProperty(locator + VIDEO_SIZE, vSize);
                    CameraUtils.setCameraSupportSize(cameraId, previewSizes);

                    // Selects only compatible dimensions
                    sizes.clear();
                    for (Size candidate : previewSizes) {
                        if (CameraUtils.isPreferredSize(candidate)) {
                            sizes.add(new Dimension(candidate.getWidth(), candidate.getHeight()));
                        }
                    }

                    cameraFormats = Arrays.asList(ArrayUtils.toObject(map.getOutputFormats()));
                    sFormat = CameraUtils.cameraImgFormatsToString(cameraFormats);
                    mConfig.setProperty(locator + PREVIEW_FORMAT, sFormat);
                }

                Timber.i("#Video supported: %s (%s)\nsupported: %s\npreferred: %s", locator, sFormat,
                        vSize, CameraUtils.dimensionsToString(sizes));

                int count = sizes.size();
                if (count == 0)
                    continue;

                // Saves supported video sizes
                Dimension[] array = new Dimension[count];
                sizes.toArray(array);
                SUPPORTED_SIZES = array;

                // Surface format
                List<Format> formats = new ArrayList<>();
                if (AndroidEncoder.isDirectSurfaceEnabled()) {
                    // TODO: camera will not be detected if only surface format is reported
                    for (Dimension size : sizes) {
                        formats.add(new VideoFormat(
                                Constants.ANDROID_SURFACE,
                                size,
                                Format.NOT_SPECIFIED,
                                Surface.class,
                                Format.NOT_SPECIFIED));
                    }
                }

                // Add only if YUV_420_888 or YV12 format is supported.  v2.8.0 supports only YUV_420_888
                // Note: YUV_420_888 is supported by camera2, and is mutually exclusive with YV12
                if (cameraFormats.contains(ImageFormat.YUV_420_888) || cameraFormats.contains(ImageFormat.YV12)) {
                    // Image formats
                    for (Dimension size : sizes) {
                        formats.add(new YUVFormat(size,
                                Format.NOT_SPECIFIED,
                                Format.byteArray,
                                YUVFormat.YUV_420,
                                Format.NOT_SPECIFIED,
                                Format.NOT_SPECIFIED,
                                Format.NOT_SPECIFIED,
                                Format.NOT_SPECIFIED,
                                Format.NOT_SPECIFIED,
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
                String name = (facing == CameraCharacteristics.LENS_FACING_FRONT)
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
        } catch (CameraAccessException e) {
            Timber.w("Exception in AndroidCameraSystem init: %s", e.getMessage());
        }
    }

    public static void cleanMediaDB() {
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