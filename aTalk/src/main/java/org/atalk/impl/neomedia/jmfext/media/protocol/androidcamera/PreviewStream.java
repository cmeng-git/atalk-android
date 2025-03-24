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

import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.view.Surface;
import android.view.SurfaceHolder;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedList;

import javax.media.Buffer;
import javax.media.control.FormatControl;

import org.atalk.android.gui.call.VideoCallActivity;
import org.atalk.android.gui.call.VideoHandlerFragment;
import org.atalk.impl.neomedia.codec.AbstractCodec2;
import org.atalk.impl.neomedia.device.util.PreviewSurfaceProvider;
import org.atalk.impl.timberlog.TimberLog;

import timber.log.Timber;

/**
 * The video stream captures frames using camera2 OnImageAvailableListener callbacks in YUV420_888 format
 * as input; and covert it from multi-plane to single plane. The output is transformed/rotated according
 * to the camera orientation See {@link #YUV420PlanarRotate(Image, byte[], int, int)}.
 *
 * @author Eng Chong Meng
 */
public class PreviewStream extends CameraStreamBase {
    /**
     * Buffers queue for camera2 YUV420_888 multi plan image buffered data
     */
    final private LinkedList<Image> bufferQueue = new LinkedList<>();

    private PreviewSurfaceProvider mSurfaceProvider;

    /**
     * Creates a new instance of <code>PreviewStream</code>.
     *
     * @param dataSource parent <code>DataSource</code>.
     * @param formatControl format control used by this instance.
     */
    public PreviewStream(DataSource dataSource, FormatControl formatControl) {
        super(dataSource, formatControl);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start()
            throws IOException {
        super.start();
        startImpl();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop()
            throws IOException {
        super.stop();
        // close the local video preview surface
        if (mSurfaceProvider != null)
            mSurfaceProvider.onObjectReleased();
    }

    /**
     * {@inheritDoc}
     * aTalk native camera acquired YUV420 preview is always in landscape mode
     */
    @Override
    protected void onInitPreview() {
        try {
            /*
             * set up the target surfaces for local video preview display; Before calling obtainObject(),
             * must setViewSize() for use in surfaceHolder.setFixedSize() on surfaceCreated
             * Then only set the local previewSurface size by calling initLocalPreviewContainer()
             * Note: Do not change the following execution order
             */
            VideoHandlerFragment videoFragment = VideoCallActivity.getVideoFragment();
            mSurfaceProvider = videoFragment.localPreviewSurface;
            mSurfaceProvider.setVideoSize(optimizedSize);
            Timber.d("Set surfaceSize (PreviewStream): %s", optimizedSize);

            SurfaceHolder surfaceHolder = mSurfaceProvider.obtainObject(); // this will create the surfaceView
            videoFragment.initLocalPreviewContainer(mSurfaceProvider);
            Surface previewSurface = surfaceHolder.getSurface();

            // Setup ImageReader to retrieve image data for remote video streaming; maxImages = 3 and acquireLatestImage();
            // to fix problem with android camera2 API implementation in throwing waitForFreeSlotThenRelock on fast android devices.
            mImageReader = ImageReader.newInstance(optimizedSize.width, optimizedSize.height, ImageFormat.YUV_420_888, 3);
            mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);

            // Need to add both the surface and the ImageReader surface as targets to the preview capture request:
            mCaptureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mCaptureBuilder.addTarget(previewSurface);
            mCaptureBuilder.addTarget(mImageReader.getSurface());
            // For picture taking only
            // mPreviewRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, mSensorOrientation);

            mCameraDevice.createCaptureSession(Arrays.asList(previewSurface, mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            mCaptureSession = session;
                            updateCaptureRequest();
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Timber.e("Camera capture session config failed: %s", session);
                        }
                    }, null);
        } catch (CameraAccessException e) {
            Timber.e("Camera capture session create exception: %s", e.getMessage());
        }
    }

    /**
     * Update the camera capture request.
     * Start the camera capture session with repeating request for smoother video streaming.
     */
    protected void updateCaptureRequest() {
        // The camera is already closed, so abort
        if (null == mCameraDevice) {
            Timber.e("Camera capture session config - camera closed, return");
            return;
        }
        try {
            // Auto focus should be continuous for camera preview.
            mCaptureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            mCaptureSession.setRepeatingRequest(mCaptureBuilder.build(), null, mBackgroundHandler);
            // Has sluggish video streaming performance, do not use
            // mCaptureSession.capture(mPreviewBuilder.build(), null, mBackgroundHandler);
            inTransition = false;
        } catch (CameraAccessException e) {
            Timber.e("Update capture request exception: %s", e.getMessage());
        }
    }

    /**
     * To fix problem with android camera2 API implementation in throwing waitForFreeSlotThenRelock on fast android devices;
     * Setup ImageReader to retrieve image data for remote video streaming; maxImages = 3 and acquireLatestImage();
     * Use try wth resource in reader.acquireLatestImage() for any IllegalStateException;
     * Call #close to release buffer before camera can acquiring more.
     * Note: The acquired image is always in landscape mode e.g. 1280x720.
     */
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = reader -> {
        if (inTransition)
            return;

        try (Image image = reader.acquireLatestImage()) {
            if ((image != null) && ImageFormat.YUV_420_888 == image.getFormat()) {
                // Seem to be cleaner without reading the image data; so skip below code
                // if (inTransition) {
                //     Timber.w("Discarded acquired image in transition @ ImageReader!");
                //     image.close();
                //     return;
                // }

                if (TimberLog.isTraceEnable)
                    calcStats(); // Calculate statistics for average frame rate if enable
                synchronized (bufferQueue) {
                    bufferQueue.addFirst(image);
                }
                transferHandler.transferData(this);
            }
        } catch (Exception e) {
            Timber.e(e, "OnImage available exception: %s", e.getMessage());
        }
    };

    /**
     * Pop the oldest image in the bufferQueue for processing; i.e.
     * transformation and copy into the buffer for remote video data streaming
     * Note: Sync problem between device rotation with new swap/flip; inTransition get clear with old image data in process.
     * (PreviewStream.java:188)#lambda$new$0$PreviewStream: OnImage available exception: index=345623 out of bounds (limit=345600)
     *
     * @param buffer streaming data buffer to be filled
     *
     * @throws IOException on image buffer not accessible
     */
    @Override
    public void read(Buffer buffer)
            throws IOException {
        Image image;
        synchronized (bufferQueue) {
            image = bufferQueue.removeLast();
        }

        if (inTransition) {
            Timber.w("Discarded acquired image in transition @ packet read!");
            buffer.setDiscard(true);
        }
        else {
            // Camera actual preview dimension may not necessary be same as set remote video format when rotated
            int w = mFormat.getSize().width;
            int h = mFormat.getSize().height;
            int outLen = (w * h * 12) / 8;

            // Set the buffer timeStamp before YUV processing, as it may take some times
            // On J7: Timestamp seems implausible relative to expectedPresent if performs after the process
            buffer.setFormat(mFormat);
            buffer.setLength(outLen);
            buffer.setFlags(Buffer.FLAG_LIVE_DATA | Buffer.FLAG_RELATIVE_TIME);
            buffer.setTimeStamp(System.currentTimeMillis());

            byte[] copy = AbstractCodec2.validateByteArraySize(buffer, outLen, false);
            try {
                YUV420PlanarRotate(image, copy, w, h);
            } catch (Exception e) {
                Timber.w("YUV420Planar Rotate exception: %s", e.getMessage());
                buffer.setDiscard(true);
            }
        }
        image.close();
    }

    /**
     * <a href="http://www.wordsaretoys.com/2013/10/25/roll-that-camera-zombie-rotation-and-coversion-from-yv12-to-yuv420planar/">...</a>
     * Original code has been modified for camera2 UV420_888 and optimised for aTalk rotation without stretching the image
     * <p>
     * Transform android YUV420_888 image orientation according to camera orientation.
     * ## Swap: means swapping the x & y coordinates, which provides a 90-degree anticlockwise rotation,
     * ## Flip: means mirroring the image for a 180-degree rotation, adjusted for inversion by for camera2
     * Note: Android does have condition with Swap && Flip in display orientation
     *
     * @param image input image with multi-plane YUV428_888 format.
     * @param width final output stream image width.
     * @param height final output stream image height.
     */
    private void YUV420PlanarRotate(Image image, byte[] output, int width, int height) {
        // Init w * h parameters: Assuming input preview buffer dimension is always in landscape mode
        int wi = width - 1;
        int hi = height - 1;

        // Output buffer: I420uvSize is a 1/4 of the Y size
        int ySize = width * height;
        int I420uvSize = ySize >> 2;

        // Input image buffer parameters
        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();

        int yRowStride = image.getPlanes()[0].getRowStride();
        int yPixelStride = image.getPlanes()[0].getPixelStride();

        // The U/V planes are guaranteed to have the same row stride and pixel stride.
        int uvRowStride = image.getPlanes()[1].getRowStride();
        int uvPixelStride = image.getPlanes()[1].getPixelStride();

        // Performance input to output buffer transformation iterate over output buffer;
        // input index xi & yi are transformed according to swap & flip
        for (int yo = 0; yo < height; yo++) {
            for (int xo = 0; xo < width; xo++) {
                // default input index for direct 1:1 transform
                int xi = xo, yi = yo;

                // The video frame: w and h are swapped at input frame - no image stretch required
                if (mSwap && mFlip) {
                    xi = yo;
                    yi = wi - xo;
                }
                else if (mSwap) {
                    xi = hi - yo;
                    yi = xo;
                }
                else if (mFlip) {
                    xi = wi - xi;
                    yi = hi - yi;
                }
                // Transform Y luminous data bytes from input to output
                output[width * yo + xo] = yBuffer.get(yRowStride * yi + yPixelStride * xi);

                /*
                 * ## Transform UV data bytes - UV has only 1/4 of Y bytes:
                 * To locate a pixel in these planes, divide all the xi and yi coordinates by two;
                 * and using the UV parameters i.e. uvRowStride and uvPixelStride
                 */
                if ((yo % 2) + (xo % 2) == 0) // 1 UV byte for 2x2 Y data bytes
                {
                    int uv420YIndex = ySize + (width >> 1) * (yo >> 1);
                    int uo = uv420YIndex + (xo >> 1);
                    int vo = I420uvSize + uo;

                    int uvIdx = (uvRowStride * (yi >> 1)) + (uvPixelStride * (xi >> 1));

                    output[uo] = uBuffer.get(uvIdx); // Cb (U)
                    output[vo] = vBuffer.get(uvIdx); // Cr (V)
                }
            }
        }
    }
}