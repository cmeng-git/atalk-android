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
package org.atalk.impl.neomedia.jmfext.media.protocol.androidcamera;

import android.annotation.SuppressLint;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;

import androidx.annotation.NonNull;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.call.VideoCallActivity;
import org.atalk.android.gui.call.VideoHandlerFragment;
import org.atalk.impl.timberlog.TimberLog;
import org.atalk.impl.neomedia.NeomediaServiceUtils;
import org.atalk.impl.neomedia.device.DeviceConfiguration;
import org.atalk.impl.neomedia.device.util.AndroidCamera;
import org.atalk.impl.neomedia.device.util.CameraUtils;
import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractPushBufferStream;

import java.awt.Dimension;
import java.io.IOException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import javax.media.Format;
import javax.media.MediaLocator;
import javax.media.control.FormatControl;
import javax.media.format.VideoFormat;

import timber.log.Timber;

/**
 * Base class for android camera streams.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public abstract class CameraStreamBase extends AbstractPushBufferStream<DataSource>
{
    private static CameraStreamBase mInstance;

    /**
     * ID of the current {@link CameraDevice}.
     */
    protected String mCameraId;

    /**
     * A reference to the opened {@link CameraDevice}.
     */
    protected static CameraDevice mCameraDevice;

    /**
     * The fixed properties for a given CameraDevice, and can be queried through the CameraManager interface
     * with CameraManager.getCameraCharacteristics.
     */
    protected CameraCharacteristics mCameraCharacteristics;

    /**
     * In use camera rotation, adjusted for camera lens facing direction  - for video streaming
     */
    protected int mSensorOrientation;

    /**
     * In use camera rotation, adjusted for camera lens facing direction  and device orientation - for video streaming
     */
    protected int mPreviewOrientation;

    /**
     * {@link CaptureRequest.Builder} for the camera preview
     */
    protected CaptureRequest.Builder mCaptureBuilder;

    /**
     * A {@link CameraCaptureSession } for camera preview.
     */
    protected CameraCaptureSession mCaptureSession;

    /**
     * An {@link ImageReader} that handles still image capture.
     */
    protected ImageReader mImageReader;

    /**
     * Format of this stream: must use a clone copy of the reference format given in streamFormats[];
     * i.e. mFormat = (VideoFormat) streamFormats[0].clone(); otherwise mFormat.setVideoSize(mPreviewSize) will change
     * the actual item in the formatControl.getSupportedFormats(); causing problem in VideoMediaStreamImpl#selectVideoSize()
     * to fail with no matched item, and androidCodec to work on first instance only
     */
    protected VideoFormat mFormat;

    /**
     * Best closer match for the user selected to the camera available resolution
     */
    protected Dimension optimizedSize = null;

    /**
     * Final previewSize (with respect to orientation) use for streaming
     */
    protected Dimension mPreviewSize = null;

    /**
     * The swap and flip state for the preview transformation for video streaming
     */
    protected boolean mSwap = true;
    protected boolean mFlip = true;

    /**
     * Flag indicates the system is in the process of shutting down the camera and ImageReader:
     * Do not access the ImageReader else: https://issuetracker.google.com/issues/203238264
     */
    protected boolean inTransition = true;

    // protected ViewDependentProvider<?> mPreviewSurfaceProvider;

    protected DataSource dataSource;

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private HandlerThread backgroundThread;

    /**
     * A {@link Handler} for running tasks in the background.
     */
    protected Handler mBackgroundHandler;

    /**
     * A {@link Semaphore} to prevent the app from exiting before closing the camera.
     */
    private final Semaphore mCameraOpenCloseLock = new Semaphore(1);

    /**
     * Fps statistics
     */
    private long last = System.currentTimeMillis();
    private final long[] avg = new long[10];
    private int idx = 0;

    /**
     * Create a new instance of <code>CameraStreamBase</code>.
     *
     * @param parent parent <code>DataSource</code>.
     * @param formatControl format control used by this stream.
     */
    CameraStreamBase(DataSource parent, FormatControl formatControl)
    {
        super(parent, formatControl);
        dataSource = parent;
        mCameraId = AndroidCamera.getCameraId(parent.getLocator());
        mInstance = this;
    }

    public static CameraStreamBase getInstance()
    {
        return mInstance;
    }

    /**
     * Method should be called by extending classes in order to start the camera.
     * Obtain optimized dimension from the device supported preview sizes with the given desired size.
     * a. always set camera preview captured dimension in its native orientation (landscape) - otherwise may not be supported.
     * b. Local preview dimension must follow current display orientation to maintain image aspect ratio
     * and width and height is interchanged if necessary. Transformation of preview stream video for sending
     * is carried out in:
     *
     * @throws IOException IO exception
     * @see PreviewStream#YUV420PlanarRotate(Image, byte[], int, int)
     */
    @SuppressLint("MissingPermission")
    protected void startImpl()
            throws IOException
    {
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }

            // Get user selected default video resolution
            DeviceConfiguration deviceConfig = NeomediaServiceUtils.getMediaServiceImpl().getDeviceConfiguration();
            Dimension videoSize = deviceConfig.getVideoSize();

            CameraManager cameraManager = aTalkApp.getCameraManager();
            mCameraCharacteristics = cameraManager.getCameraCharacteristics(mCameraId);
            StreamConfigurationMap map = mCameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) {
                throw new RuntimeException("Cannot get camera available characteristics!");
            }
            startBackgroundThread();

            // Find optimised video resolution with user selected against device support image format sizes
            Size[] supportedPreviewSizes = map.getOutputSizes(ImageFormat.YUV_420_888);
            optimizedSize = CameraUtils.getOptimalPreviewSize(videoSize, supportedPreviewSizes);

            Format[] streamFormats = getStreamFormats();
            mFormat = (VideoFormat) streamFormats[0].clone();
            Timber.d("Camera data stream format #2: %s=>%s", videoSize, mFormat);
            initPreviewOrientation(true);

            cameraManager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
        } catch (SecurityException e) {
            Timber.e("openCamera: %s", e.getMessage());
        } catch (CameraAccessException e) {
            Timber.e("openCamera: Cannot access the camera.");
        } catch (NullPointerException e) {
            Timber.e("Camera2API is not supported on the device.");
        } catch (InterruptedException e) {
            // throw new RuntimeException("Interrupted while trying to lock camera opening.");
            Timber.e("Exception in start camera init: %s", e.getMessage());
        }
    }

    /**
     * Update swap and flip for YUV420PlanarRotate();
     * Set local preview display orientation according to device rotation and sensor orientation
     * Currently android phone device has 90/270 for back and front cameras native orientation
     *
     * Note: valid Sensor orientations: 0, 90, 270; 180 is not reported by android camera sensors
     *
     * @param initFormat Sending video orientation always in upright position when set to true;
     * Set to false on device rotation requires the remote device to rotate accordingly to view image upright
     */
    public void initPreviewOrientation(boolean initFormat)
    {
        // Set preview display orientation according to device rotation
        if (initFormat) {
            mPreviewOrientation = CameraUtils.getPreviewOrientation(mCameraId);
        }
        else {
            mPreviewOrientation = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        }

        // Streaming video always send in the user selected dimension and image view in upright orientation
        mSwap = (mPreviewOrientation == 90) || (mPreviewOrientation == 270);

        if (mSwap) {
            mPreviewSize = new Dimension(optimizedSize.height, optimizedSize.width);
        }
        else {
            mPreviewSize = optimizedSize;
        }
        mFormat.setVideoSize(mPreviewSize);

        // front-facing camera; take care android flip the video for front facing lens camera
        Integer facing = mCameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
        if (CameraCharacteristics.LENS_FACING_FRONT == facing) {
            mFlip = (mPreviewOrientation == 180) || (!aTalkApp.isPortrait && mPreviewOrientation == 270);
        }
        else {
            mFlip = (mPreviewOrientation == 90 || mPreviewOrientation == 180);
        }
        Timber.d("Camera preview orientation: %s; portrait: %s; swap: %s; flip: %s; format: %s",
                mPreviewOrientation, aTalkApp.isPortrait, mSwap, mFlip, mFormat);
    }

    /**
     * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its status.
     */
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback()
    {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice)
        {
            mCameraDevice = cameraDevice;
            onInitPreview();
            mCameraOpenCloseLock.release();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice)
        {
            mCameraDevice.close();
            mCameraDevice = null;
            mCameraOpenCloseLock.release();
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error)
        {
            String errMessage;
            switch (error) {
                case ERROR_CAMERA_IN_USE:
                    errMessage = "Camera in use";
                    break;
                case ERROR_MAX_CAMERAS_IN_USE:
                    errMessage = "Maximum cameras in use";
                    break;
                case ERROR_CAMERA_DISABLED:
                    errMessage = "Device policy";
                    break;
                case ERROR_CAMERA_DEVICE:
                    errMessage = "Fatal (device)";
                    break;
                case ERROR_CAMERA_SERVICE:
                    errMessage = "Fatal (service)";
                    break;
                default:
                    errMessage = "UnKnown";
            }
            Timber.e("Set camera preview failed: %s", errMessage);
            aTalkApp.showGenericError(R.string.video_format_not_supported, mPreviewSize, errMessage);

            mCameraDevice.close();
            mCameraDevice = null;
            mCameraOpenCloseLock.release();
        }
    };

    /**
     * Method called before camera preview is started. Extending classes should configure preview at this point.
     */
    protected abstract void onInitPreview();

    /**
     * Method called to start camera image capture.
     */
    protected abstract void updateCaptureRequest();

    /**
     * Selects stream formats.
     *
     * @return stream formats.
     */
    private Format[] getStreamFormats()
    {
        FormatControl[] formatControls = dataSource.getFormatControls();
        final int count = formatControls.length;
        Format[] streamFormats = new Format[count];

        for (int i = 0; i < count; i++) {
            FormatControl formatControl = formatControls[i];
            Format format = formatControl.getFormat();

            if (format == null) {
                Format[] supportedFormats = formatControl.getSupportedFormats();
                if ((supportedFormats != null) && (supportedFormats.length > 0)) {
                    format = supportedFormats[0];
                }
            }
            // Timber.d("getStreamFormats Idx: %s/%s; format: %s", i, count, format);
            streamFormats[i] = format;
        }
        return streamFormats;
    }

//    /**
//      * {@inheritDoc}
//      * cmeng: mFormat is always null in all doGetFormat; serving no purpose
//      * Instead via buffer.setFormat(mFormat) in PreviewStream#read(Buffer buffer); a safer approach
//     */
//    @Override
//    protected Format doGetFormat()
//    {
//        Timber.e(new Exception("Camera data stream format #1: " + mPreviewSize + "=>" + mFormat));
//        return mFormat;
//    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop()
            throws IOException
    {
        closeCamera();
        stopBackgroundThread();
        super.stop();
    }

    /**
     * Closes the current {@link CameraDevice}.
     */
    protected void closeCamera()
    {
        if (mCameraDevice != null) {
            try {
                inTransition = true;
                mCameraOpenCloseLock.acquire();
                if (null != mCaptureSession) {
                    mCaptureSession.stopRepeating();
                    // mCaptureSession.abortCaptures();
                    mCaptureSession.close();
                    mCaptureSession = null;
                }

                if (null != mCameraDevice) {
                    mCameraDevice.close();
                    mCameraDevice = null;
                }

                // (PreviewStream.java:165): OnImage available exception: buffer is inaccessible
                if (null != mImageReader) {
                    mImageReader.close();
                    mImageReader = null;
                }
            } catch (InterruptedException | CameraAccessException e) {
                throw new RuntimeException("Interrupted while trying to close camera.", e);
            } finally {
                mCameraOpenCloseLock.release();
            }
        }
    }

    /**
     * Triggered on device rotation to init the remote video orientation sending;
     * initFormat == true has synchronised problem between imageReader data and YUV swap if not handled properly
     *
     * On Samsung J7 implementation, seems at times mCaptureSession and mCameraDevice can be null etc;
     * If exception happen, then reInit the whole camera sequence
     *
     * @param initFormat Sending video orientation always in upright position when set to true;
     * Set to false on device rotation requires the remote device to rotate accordingly to view image upright
     */
    public void initPreviewOnRotation(boolean initFormat)
    {
        if (initFormat) {
            inTransition = true;
            try {
                if (null != mCaptureSession) {
                    mCaptureSession.stopRepeating();
                    // mCaptureSession.abortCaptures();
                }
                initPreviewOrientation(true);
                updateCaptureRequest();
                return;
            } catch (Exception e) {
                Timber.e("Close capture session exception: %s", e.getMessage());
            }
            reInitCamera();
        }
        else {
            initPreviewOrientation(false);
        }
    }

    private void reInitCamera()
    {
        VideoHandlerFragment videoFragment = VideoCallActivity.getVideoFragment();
        closeCamera();

        if (videoFragment.isLocalVideoEnabled()) {
            try {
                start();
            } catch (IOException e) {
                aTalkApp.showToastMessage(R.string.video_format_not_supported, mCameraId, e.getMessage());
            }
        }
    }

    /**
     * Switch to the user selected lens facing camera. Start data streaming only if local video is enabled
     * User needs to enable the local video to send the video stream to remote user.
     *
     * @param cameraLocator MediaLocator
     * @param isLocalVideoEnable true is local video is enabled for sending
     */
    public void switchCamera(MediaLocator cameraLocator, boolean isLocalVideoEnable)
    {
        AndroidCamera.setSelectedCamera(cameraLocator);
        mCameraId = AndroidCamera.getCameraId(cameraLocator);

        // Stop preview and release the current camera if any before switching, otherwise app will crash
        Timber.d("Switching camera: %s", cameraLocator.toString());

        closeCamera();

        if (isLocalVideoEnable) {
            try {
                start();
            } catch (IOException e) {
                aTalkApp.showToastMessage(R.string.video_format_not_supported, cameraLocator, e.getMessage());
            }
        }
    }

    /**
     * Calculates fps statistics.
     *
     * @return time elapsed in millis between subsequent calls to this method.
     */
    protected long calcStats()
    {
        // Measure moving average
        long current = System.currentTimeMillis();
        long delay = (current - last);
        last = System.currentTimeMillis();
        avg[idx] = delay;
        if (++idx == avg.length)
            idx = 0;
        long movAvg = 0;
        for (long anAvg : avg) {
            movAvg += anAvg;
        }
        Timber.log(TimberLog.FINER, "Avg frame rate: %d", (1000 / (movAvg / avg.length)));
        return delay;
    }

    // ===============================================================
    private void startBackgroundThread()
    {
        if (backgroundThread == null) {
            backgroundThread = new HandlerThread("CameraBackground");
            backgroundThread.start();
            mBackgroundHandler = new Handler(backgroundThread.getLooper());
        }
    }

    private void stopBackgroundThread()
    {
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
                backgroundThread = null;
                mBackgroundHandler = null;
            } catch (InterruptedException e) {
                Timber.e(e, "Stop background thread exception: %s", e.getMessage());
            }
        }
    }
}
