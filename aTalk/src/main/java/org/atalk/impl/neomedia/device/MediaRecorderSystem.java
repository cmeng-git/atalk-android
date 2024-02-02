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

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.text.TextUtils;
import android.util.Size;

import androidx.core.content.ContextCompat;

import net.java.sip.communicator.util.UtilActivator;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.impl.neomedia.device.util.AndroidCamera;
import org.atalk.impl.neomedia.device.util.CameraUtils;
import org.atalk.impl.neomedia.format.ParameterizedVideoFormat;
import org.atalk.impl.neomedia.format.VideoMediaFormatImpl;
import org.atalk.service.configuration.ConfigurationService;
import org.atalk.service.neomedia.codec.Constants;
import org.atalk.util.MediaType;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import javax.media.CaptureDeviceManager;
import javax.media.Format;
import javax.media.MediaLocator;

import timber.log.Timber;

/**
 * Discovers and registers <code>MediaRecorder</code> capture devices with FMJ.
 * Not further use in aTalk since v2.8.0; after android API 23, android drops support for non-seekable
 * file descriptors i.e. mediaRecorder.setOutputFile(createLocalSocket());
 *
 * @author Eng Chong Meng
 */
public class MediaRecorderSystem extends DeviceSystem
{
    private static final String VIDEO_SIZE = ".video.size";

    /**
     * Supported preview sizes by android camera for user selection
     */
    public static Dimension[] SUPPORTED_SIZES = new Dimension[]{};

    private static boolean isMediaRecorderInitialized = false;

    /**
     * Initializes a new <code>MediaRecorderSystem</code> instance which discovers and registers
     * <code>MediaRecorder</code> capture devices with FMJ.
     *
     * @throws Exception if anything goes wrong while discovering and registering
     * <code>MediaRecorder</code> capture devices with FMJ
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
        if (isMediaRecorderInitialized || (ContextCompat.checkSelfPermission(aTalkApp.getInstance(),
                Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)) {
            return;
        }

        try {
            CameraManager cameraManager = aTalkApp.getCameraManager();
            String[] cameraIdList = cameraManager.getCameraIdList();

            // Timber.d("Number of android cameras: %s", cameraIdList.length);
            if (cameraIdList.length == 0) {
                return;
            }

            ConfigurationService mConfig = UtilActivator.getConfigurationService();
            List<AndroidCamera> captureDevices = new LinkedList<>();

            for (String cameraId : cameraIdList) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }

                // create a locator with camera id and its facing direction (cameraInfo)
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                MediaLocator locator = AndroidCamera.constructLocator(LOCATOR_PROTOCOL_MEDIARECORDER, cameraId, facing);

                // List of preferred resolutions which is supported by the Camera.
                List<Dimension> sizes = new ArrayList<>();

                String vSize = mConfig.getString(locator + VIDEO_SIZE, null);
                if (TextUtils.isEmpty(vSize) || !CameraUtils.getSupportedSizes(vSize, sizes)) {
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
                        Timber.w("Camera API2 is supported for camera: %s; Level: %s", cameraId, sLevel);
                    }

                    Size[] supportedSizes = map.getOutputSizes(SurfaceTexture.class);
                    if (supportedSizes == null) {
                        /*
                         * The video size is the same as the preview size.
                         * MediaRecorder.setVideoSize(int,int) will most likely fail, print a line in
                         * logcat and not throw an exception (in DataSource.doStart()).
                         */
                        Timber.w("get Output Preview Sizes returned null for camera: %s", cameraId);
                        continue;
                    }
                    else {
                        Timber.i("Video sizes supported by %s: %s",
                                locator, CameraUtils.cameraSizesToString(supportedSizes));
                    }

                    // Save to DB and keep a copy of the video resolution supportSizes for cameraId
                    mConfig.setProperty(locator + VIDEO_SIZE, CameraUtils.cameraSizesToString(supportedSizes));
                    CameraUtils.setCameraSupportSize(cameraId, supportedSizes);

                    // Selects only compatible dimensions
                    for (Size candidate : supportedSizes) {
                        if (CameraUtils.isPreferredSize(candidate)) {
                            sizes.add(new Dimension(candidate.getWidth(), candidate.getHeight()));
                        }
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
                String name = (facing == CameraCharacteristics.LENS_FACING_FRONT)
                        ? aTalkApp.getResString(R.string.service_gui_settings_CAMERA_FRONT)
                        : aTalkApp.getResString(R.string.service_gui_settings_CAMERA_BACK);
                name += " (MediaRecoder#" + cameraId + ")";

                // XXX Prefer the front-facing camera over the back-facing one.
                AndroidCamera device = new AndroidCamera(name, locator, formats);
                if (facing == CameraCharacteristics.LENS_FACING_FRONT)
                    captureDevices.add(0, device);
                else
                    captureDevices.add(device);
            }

            if (!captureDevices.isEmpty()) {
                for (AndroidCamera captureDevice : captureDevices)
                    CaptureDeviceManager.addDevice(captureDevice);
            }
            isMediaRecorderInitialized = true;
        } catch (CameraAccessException e) {
            Timber.w("Exception in MediaRecorderSystem init: %s", e.getMessage());
        }
    }
}
