/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.protocol.androidcamera;

import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.Camera.Size;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.plugin.timberlog.TimberLog;
import org.atalk.android.util.java.awt.Dimension;
import org.atalk.impl.neomedia.NeomediaServiceUtils;
import org.atalk.impl.neomedia.device.DeviceConfiguration;
import org.atalk.impl.neomedia.device.util.AndroidCamera;
import org.atalk.impl.neomedia.device.util.CameraUtils;
import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractPushBufferStream;

import java.io.IOException;
import java.util.List;

import javax.media.Format;
import javax.media.control.FormatControl;
import javax.media.format.VideoFormat;

import timber.log.Timber;

/**
 * Base class for camera streams.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */

@SuppressWarnings("deprecation")
abstract class CameraStreamBase extends AbstractPushBufferStream<DataSource>
{
    /**
     * ID of the camera used by this instance.
     */
    protected static int mCameraId = -1;

    /**
     * Camera object.
     */
    protected Camera mCamera;

    /**
     *  In use camera rotation, adjusted for camera lens facing direction  - for video streaming
     */
    protected int mPreviewOrientation;

    /**
     * Format of this stream.
     */
    protected VideoFormat mFormat;

    /**
     * Best closer match for the user selected to the camera available resolution
     */
    protected Dimension optimizedSize;

    /**
     * Final previewSize (with respect to orientation) use for streaming
     */
    protected static Dimension mPreviewSize;

    protected DataSource dataSource;

    /**
     * Fps statistics
     */
    private long last = System.currentTimeMillis();
    private final long[] avg = new long[10];
    private int idx = 0;

    /**
     * Create a new instance of <tt>CameraStreamBase</tt>.
     *
     * @param parent parent <tt>DataSource</tt>.
     * @param formatControl format control used by this stream.
     */
    CameraStreamBase(DataSource parent, FormatControl formatControl)
    {
        super(parent, formatControl);
        dataSource = parent;
        mCameraId = AndroidCamera.getCameraId(parent.getLocator());
        // Timber.e(new Exception("Camera Id (debugging only): " + mCameraId));
    }

    /**
     * Method should be called by extending classes in order to start the camera:
     *
     * Obtain optimized dimension from the device supported preview sizes with the given desired size.
     * - Final stream/send video dimension is always in landscape mode.
     * - Local preview dimension must follow current display orientation to maintain image aspect ratio
     * and width and height is interchanged if necessary. Transformation of preview stream video for sending
     * is carried out in {@link PreviewStream#YV12toYUV420PlanarRotate(byte[], byte[], int, int, int)}
     *
     * Note: Samsung Note8 has the problem of supporting the rotated 4:3 dimension in portrait mode. So use 1:1
     *
     * @throws IOException IO exception
     */
    protected void startImpl()
            throws IOException
    {
        try {
            // Reuse if already acquired add not release previously
            // if (mCamera == null) ???
            mCamera = Camera.open(mCameraId);
            Camera.Parameters params = mCamera.getParameters();

            // Get user selected default video resolution on start
            DeviceConfiguration deviceConfig = NeomediaServiceUtils.getMediaServiceImpl().getDeviceConfiguration();
            Dimension videoSize = deviceConfig.getVideoSize();

            // Find optimised video resolution with user selected against device support formats
            List<Size> supportedPreviewSizes = params.getSupportedPreviewSizes();
            optimizedSize = CameraUtils.getOptimalPreviewSize(videoSize, supportedPreviewSizes);

            // Set preview display orientation according to device rotation
            mPreviewOrientation = CameraUtils.getPreviewOrientation(mCameraId);
            boolean swap = (mPreviewOrientation == 90) || (mPreviewOrientation == 270);
            if (swap) {
                mPreviewSize = new Dimension(optimizedSize.height, optimizedSize.width);
            }
            else {
                mPreviewSize = optimizedSize;
            }

            // Streaming video always send in dimension according to the phone orientation
            Format[] streamFormats = getStreamFormats();
            mFormat = (VideoFormat) streamFormats[0];
            mFormat.setVideoSize(mPreviewSize);
            Timber.d("Camera data stream format: %s=>%s", videoSize, mFormat); // params.get("preview-size-values"));

            params.setRotation(mPreviewOrientation);
            // Always set camera capture in its native dimension - otherwise may not be supported.
            params.setPreviewSize(optimizedSize.width, optimizedSize.height);
            // // Change camera preview format to YV12. (default = NV21)
            params.setPreviewFormat(ImageFormat.YV12);
            mCamera.setParameters(params);
			// Camera.setDisplayOrientation() does not affect the camera output, just the view display.
            mCamera.setDisplayOrientation(mPreviewOrientation);

            onInitPreview();
            mCamera.startPreview();
        } catch (Exception e) {
            Timber.e("Set camera preview failed: %s", e.getMessage());
            aTalkApp.showGenericError(R.string.service_gui_DEVICE_VIDEO_FORMAT_NOT_SUPPORTED, mPreviewSize, e.getMessage());

            // Close camera on error
            if (mCamera != null) {
                mCamera.reconnect();
                mCamera.release();
                mCamera = null;
            }
            // throw new IOException(error);
        }
    }

    /**
     * Method called before camera preview is started. Extending classes should configure the preview at this point.
     *
     * @throws IOException ioException on error
     */
    protected abstract void onInitPreview()
            throws IOException;

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
            streamFormats[i] = format;
        }
        return streamFormats;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Format doGetFormat()
    {
        return mFormat;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop()
            throws IOException
    {
        super.stop();
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.setPreviewCallback(null);
            CameraUtils.releaseCamera(mCamera);
            mCamera = null;
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
        if (TimberLog.isTraceEnable) {
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
        return 0;
    }

    /**
     * @return the current selected cameraId
     */
    public static int getCameraId()
    {
        return mCameraId;
    }
}
