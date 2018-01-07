/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.protocol.androidcamera;

import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.os.Build;
import android.widget.Toast;

import net.java.sip.communicator.util.Logger;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.aTalk;
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
     * The logger
     */
    private final static Logger logger = Logger.getLogger(CameraStreamBase.class);

    /**
     * ID of the camera used by this instance.
     */
    protected final int mCameraId;

    /**
     * Camera object.
     */
    protected Camera mCamera;

    /**
     * Format of this stream.
     */
    protected VideoFormat mFormat;

    /**
     * Final previewSize use for streaming
     */
    protected Dimension mPreviewSize;

    // Camera rotation
    protected int mRotation;

    protected DataSource dataSource;

    /**
     * Fps statistics
     */
    private long last = System.currentTimeMillis();
    private long[] avg = new long[10];
    private int idx = 0;

    /**
     * Creates new instance of <tt>CameraStreamBase</tt>.
     *
     * @param parent
     *         parent <tt>DataSource</tt>.
     * @param formatControl
     *         format control used by this stream.
     */
    CameraStreamBase(DataSource parent, FormatControl formatControl)
    {
        super(parent, formatControl);
        dataSource = parent;
        mCameraId = AndroidCamera.getCameraId(parent.getLocator());
    }

    /**
     * Method should be called by extending classes in order to start the camera:
     *
     * Obtain optimized dimension from device supported preview sizes with the given desired size.
     * - Final stream/send video dimension is always in landscape mode.
     * - Local preview dimension must follow current display orientation to maintain image aspect ratio
     * and width and height is interchanged if necessary. Transformation of preview stream video for sending
     * is carried out in {@link PreviewStream#YV12toYUV420PlanarRotate(byte[], byte[], int, int, int)}
     *
     * Note: Samsung note8 has problem of supporting the rotated 4:3 dimension in portrait mode. So use 1:1
     *
     * @throws IOException
     */
    protected void startImpl()
            throws IOException
    {
        Exception error = null;
        try {
            mCamera = Camera.open(mCameraId);
            mRotation = CameraUtils.getCameraDisplayRotation(mCameraId);
            boolean swap = (mRotation == 90) || (mRotation == 270);
            Camera.Parameters params = mCamera.getParameters();

            Format[] streamFormats = getStreamFormats();
            mFormat = (VideoFormat) streamFormats[0];

            // Get user selected default video resolution on start
            DeviceConfiguration deviceConfig = NeomediaServiceUtils.getMediaServiceImpl().getDeviceConfiguration();
            Dimension videoSize = deviceConfig.getVideoSize();

            // note8 cannot support rotated landscape preview dimension
            if (Build.MODEL.contains("N950F") && swap) {
                videoSize = new Dimension(736, 736);
                // Can't create handler inside thread that has not called Looper.prepare()
//                Toast.makeText(aTalkApp.getCurrentActivity(),
//                        R.string.service_gui_DEVICE_VIDEO_PROTRAIT_NOT_SUPPORTED, Toast.LENGTH_SHORT).show();
            }

            // Find optimised video resolution with user selected against device support formats
            mPreviewSize = videoSize;
            List<Size> previewSizes = params.getSupportedPreviewSizes();
            Dimension optimizedSize = CameraUtils.getOptimalPreviewSize(previewSizes, mPreviewSize, swap);

            // Set preview display orientation according to device rotation
            if (swap) {
                mPreviewSize = new Dimension(optimizedSize.height, optimizedSize.width);
            }
            else {
                mPreviewSize = optimizedSize;
            }

            // Streaming video always send in landscape mode irrespectively of phone orientation
            mFormat.setVideoSize(optimizedSize);
            logger.info("Camera stream format: " + mFormat);

            params.setRotation(mRotation);
            params.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
            // Change camera preview default format NV21 to YV12.
            params.setPreviewFormat(ImageFormat.YV12);
            mCamera.setParameters(params);
            mCamera.setDisplayOrientation(mRotation);

            onInitPreview();
            mCamera.startPreview();
        } catch (Exception e) {
            logger.error(e, e);
            error = e;
        }
        // Close camera on error
        if ((error != null) && (mCamera != null)) {
            mCamera.reconnect();
            mCamera.release();
            mCamera = null;
            throw new IOException(error);
        }
    }

    /**
     * Method called before camera preview is started. Extending classes should configure preview at
     * this point.
     *
     * @throws IOException
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
        long current = System.currentTimeMillis();
        long delay = (current - last);
        last = System.currentTimeMillis();
        // Measure moving average
        if (logger.isDebugEnabled()) {
            avg[idx] = delay;
            if (++idx == avg.length)
                idx = 0;
            long movAvg = 0;
            for (long anAvg : avg) {
                movAvg += anAvg;
            }
            logger.debug("Avg frame rate: " + (1000 / (movAvg / avg.length)));
        }
        return delay;
    }
}
