/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.protocol.androidcamera;

import android.hardware.Camera;
import android.view.SurfaceHolder;

import org.atalk.impl.neomedia.codec.AbstractCodec2;
import org.atalk.impl.neomedia.device.util.CameraUtils;

import java.io.IOException;
import java.util.LinkedList;

import javax.media.Buffer;
import javax.media.control.FormatControl;

import timber.log.Timber;

/**
 * Video stream that captures frames using camera preview callbacks in YUV format. As an input
 * Android YV12 format is used which is almost YUV420 planar except that for some dimensions padding
 * is added to U,V strides. See {@link #//YV12toYUV420Planar(byte[], byte[], int, int)}.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
@SuppressWarnings("deprecation")
public class PreviewStream extends CameraStreamBase implements Camera.PreviewCallback
{
    /**
     * Buffers queue
     */
    final private LinkedList<byte[]> bufferQueue = new LinkedList<>();

    /* In use camera rotation - for video streaming */
    private int cameraRotation;

    /**
     * Creates a new instance of <tt>PreviewStream</tt>.
     *
     * @param dataSource parent <tt>DataSource</tt>.
     * @param formatControl format control used by this instance.
     */
    public PreviewStream(DataSource dataSource, FormatControl formatControl)
    {
        super(dataSource, formatControl);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start()
            throws IOException
    {
        super.start();
        startImpl();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onInitPreview()
            throws IOException
    {
        // Display Orientation, FrontCam, BackCam
        // 0, 270, 90
        // 90, 180, 180
        // 270, 360, 0
        Camera.CameraInfo camInfo = new Camera.CameraInfo();
        Camera.getCameraInfo(mCameraId, camInfo);
        if (camInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            cameraRotation = (360 - mRotation) % 360;
        }
        else {
            cameraRotation = mRotation;
        }
        boolean swap = (mRotation == 90) || (mRotation == 270);

        // Alloc two buffers
        Camera.Parameters params = mCamera.getParameters();
        Camera.Size previewSize = params.getPreviewSize();
        int bufferSize = calcYV12Size(previewSize, swap);

        Timber.i("Camera captured preview = %dx%d @%d-DEG with buffer size: %d for image format: %08x",
                previewSize.width, previewSize.height, cameraRotation, bufferSize, params.getPreviewFormat());

        mCamera.addCallbackBuffer(new byte[bufferSize]);

        SurfaceHolder previewSurface = CameraUtils.obtainPreviewSurface();
        mCamera.setPreviewDisplay(previewSurface);
        mCamera.setPreviewCallbackWithBuffer(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void read(Buffer buffer)
            throws IOException
    {
        byte[] data;
        synchronized (bufferQueue) {
            data = bufferQueue.removeLast();
        }

        // cmeng - Camera actual preview dimension may not necessary be same as set format when rotated
        int w = mFormat.getSize().width;
        int h = mFormat.getSize().height;
        int outLen = (w * h * 12) / 8;

        byte[] copy = AbstractCodec2.validateByteArraySize(buffer, outLen, false);
        YV12toYUV420PlanarRotate(data, copy, w, h, cameraRotation);
        buffer.setLength(outLen);
        buffer.setFlags(Buffer.FLAG_LIVE_DATA | Buffer.FLAG_RELATIVE_TIME);
        buffer.setTimeStamp(System.currentTimeMillis());

        // Put the buffer for reuse
        if (mCamera != null)
            mCamera.addCallbackBuffer(data);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onPreviewFrame(byte[] data, Camera camera)
    {
        if (data == null) {
            Timber.e("Null data received on callback, invalid buffer size?");
            return;
        }
        // Calculate statistics for average frame rate
        calcStats();

        // Convert image format
        synchronized (bufferQueue) {
            bufferQueue.addFirst(data);
        }
        transferHandler.transferData(this);
    }

    /**
     * http://www.wordsaretoys.com/2013/10/25/roll-that-camera-zombie-rotation-and-coversion-from-yv12-to-yuv420planar/
     * Original code has been modified and optimised for aTalk rotation without image stretching
     *
     * Converts Android YV12 format to YUV420 planar and rotate according to camera orientation.
     * ## Swap: means swapping the x & y coordinates, which provides a 90-degree anticlockwise rotation,
     * ## Flip: means mirroring the image for a 180-degree rotation.
     * Note: Android does have condition with Swap && Flip in display orientation
     *
     * @param input input buffer - YV12 frame image bytes.
     * @param output output buffer - YUV420 frame format.
     * @param width final output stream image width.
     * @param height final output stream image height.
     * @param rotation camera/preview rotation: 270 = phone rotated clockwise 90 degree (portrait mode)
     */
    private static void YV12toYUV420PlanarRotate(byte[] input, byte[] output, int width, int height, int rotation)
    {
        boolean swap = (rotation == 90 || rotation == 270);
        boolean flip = (rotation == 90 || rotation == 180);

        // Init w * h parameters: Assuming input preview buffer dimension when rotate is same as
        // output buffer without need to stretch
        int wi = width - 1;
        int hi = height - 1;

        // Actual input preview frame dimension - for calculating YV12 indexes for input data buffer
        int wp = width;
        int hp = height;
        if (swap) {
            wp = height;
            hp = width;
        }

        // Constants for UV planes transformation on input preview frame buffer
        int yStride = (int) Math.ceil(wp / 16.0) * 16;
        int uvStride = (int) Math.ceil((yStride / 2) / 16.0) * 16;
        int ySize = yStride * hp;
        int uvSize = uvStride * (hp >> 1);

        int I420uvSize = (width * height >> 2);

        // Performance input to output buffer transformation iterate over output buffer
        for (int yo = 0; yo < height; yo++) {
            int uv420YIndex = ySize + (width >> 1) * (yo >> 1);

            for (int xo = 0; xo < width; xo++) {
                // default input index for direct 1:1 transform
                int xi = xo, yi = yo;

                // video frame: w and h are swapped at input frame - no image stretch required
                if (swap && flip) {
                    xi = yo;
                    yi = wi - xo;
                }
                else if (swap) {
                    xi = hi - yo;
                    yi = xo;
                }
                else if (flip) {
                    xi = wi - xi;
                    yi = hi - yi;
                }

                // Transform Y data bytes from input to output
                output[width * yo + xo] = input[wp * yi + xi];

                /*
                 * ## Transform UV data bytes - UV has only 1/4 of Y bytes :
                 * To locate a pixel in these planes, divide all the x and y coordinates by two. Using
                 * width as the stride, so it also gets divided by two i.e. wi2. Planar offsets into
                 * the image can be found by using the size of the Y plane ySize = (width * height) and
                 * the size of a chroma plane qs = (ySize / 4). Must swap the UV planes between input and
                 * output for YV12 to YVU420 transformation
                 */
                if ((yo % 2) + (xo % 2) == 0) // 1 vu byte for 2x2 Y data bytes
                {
                    int uo = uv420YIndex + (xo >> 1);
                    int vo = I420uvSize + uo;

                    int ui = ySize + uvStride * (yi >> 1) + (xi >> 1);
                    int vi = uvSize + ui;

                    output[uo] = input[vi]; // Cb (U)
                    output[vo] = input[ui]; // Cr (V)
                }
            }
        }
    }

    /**
     * Calculates YV12 image data bufferSize in bytes.
     * The buffer is used to receive the generated preview streaming data,
     * hence need to consider when image is swap (rotated)
     *
     * @param previewSize camera preview size.
     * @param swap indicate the camera is rotated, and width and height must be swapped to get the buffer size
     * @return YV12 image data size in bytes.
     */
    private static int calcYV12Size(Camera.Size previewSize, boolean swap)
    {
        int wp = previewSize.width;
        int hp = previewSize.height;
        if (swap) {
            wp = previewSize.height;
            hp = previewSize.width;
        }

        float yStride = (int) Math.ceil(wp / 16.0) * 16;
        float uvStride = (int) Math.ceil((yStride / 2) / 16.0) * 16;
        float ySize = yStride * hp;
        float uvSize = uvStride * hp / 2;
        // float yRowIndex = yStride * y;
        // float uRowIndex = ySize + uvSize + uvStride * c;
        // float vRowIndex = ySize + uvStride * c;
        return (int) (ySize + uvSize * 2);
    }

    //    /**
    //     * Converts Android YV12 format to YUV420 planar without rotation support
    //     *
    //     * @param input
    //     *         input YV12 image bytes.
    //     * @param output
    //     *         output buffer.
    //     * @param width
    //     *         image width.
    //     * @param height
    //     *         image height.
    //     */
    //    static void YV12toYUV420Planar(final byte[] input, final byte[] output, final int width, final int height)
    //    {
    //        if (width % 16 != 0)
    //            throw new IllegalArgumentException("Unsupported width: " + width);
    //
    //        int yStride = (int) Math.ceil(width / 16.0) * 16;
    //        int uvStride = (int) Math.ceil((yStride / 2) / 16.0) * 16;
    //        int ySize = yStride * height;
    //        int uvSize = uvStride * height / 2;
    //
    //        int I420uvStride = (int) (((yStride / 2) / 16.0) * 16);
    //        int I420uvSize = width * height / 4;
    //        int uvStridePadding = uvStride - I420uvStride;
    //
    //        System.arraycopy(input, 0, output, 0, ySize); // Y
    //
    //        // If padding is 0 then just swap U and V planes
    //        if (uvStridePadding == 0) {
    //            System.arraycopy(input, ySize, output, ySize + uvSize, uvSize); // Cr (V)
    //            System.arraycopy(input, ySize + uvSize, output, ySize, uvSize); // Cb (U)
    //        }
    //        else {
    //            Timber.w("Not recommended resolution: %sx%s", width, height);
    //            int src = ySize;
    //            int dst = ySize;
    //            // Copy without padding
    //            for (int y = 0; y < height / 2; y++) {
    //                System.arraycopy(input, src + uvSize, output, dst, I420uvStride); // Cb (U)
    //                System.arraycopy(input, src, output, dst + I420uvSize, I420uvStride); // Cr (V)
    //                src += uvStride;
    //                dst += I420uvStride;
    //            }
    //        }
    //    }
}
