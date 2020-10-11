/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device.util;

import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.text.TextUtils;
import android.view.Surface;
import android.view.SurfaceHolder;

import org.atalk.android.util.java.awt.Dimension;
import org.atalk.impl.neomedia.device.DeviceConfiguration;
import org.atalk.impl.neomedia.jmfext.media.protocol.androidcamera.PreviewStream;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

import javax.media.MediaLocator;

/**
 * Utility methods for operations on <tt>Camera</tt> objects. Also shares preview surface provider
 * between <tt>MediaRecorder</tt> and <tt>AndroidCamera</tt> device systems.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
@SuppressWarnings("deprecation")
public class CameraUtils
{
    /**
     * Surface provider used to display camera preview
     */
    private static PreviewSurfaceProvider surfaceProvider;

    /**
     * <tt>OpenGlCtxProvider</tt> that provides Open GL context for local preview rendering. It is
     * used in direct surface encoding mode.
     */
    public static OpenGlCtxProvider localPreviewCtxProvider;

    /**
     * The list of sizes from which the first supported by the respective {@link Camera} is to be
     * chosen as the size of the one and only <tt>Format</tt> supported by the associated
     * <tt>mediarecorder</tt> <tt>CaptureDevice</tt>.
     *
     * User selectable video resolution. The actual resolution use during video call is adjusted so
     * it is within device capability {@link #getOptimalPreviewSize(Dimension, List)
     * Any strides paddings if required, is properly handled in
     * {@link PreviewStream#YV12toYUV420PlanarRotate(byte[], byte[], int, int, int)}
     */
    public static final Dimension[] PREFERRED_SIZES = DeviceConfiguration.SUPPORTED_RESOLUTIONS;

    /**
     * Map contains all the phone available cameras and their supported resolution sizes
     * This list is being update at the device start up in.
     * @see org.atalk.impl.neomedia.device.MediaRecorderSystem
     */
    private static Map<Integer, List<Camera.Size>> cameraSupportSize = new HashMap<>();

    /**
     * Returns <tt>true</tt> if given <tt>size</tt> is on the list of preferred sizes.
     *
     * @param size the size to check.
     * @return <tt>true</tt> if given <tt>size</tt> is on the list of preferred sizes.
     */
    public static boolean isPreferredSize(Dimension size)
    {
        for (Dimension s : PREFERRED_SIZES) {
            if (s.width == size.width && s.height == size.height) {
                return true;
            }
        }
        return false;
    }

    /**
     * Constructs a <tt>String</tt> representation of a specific <tt>Iterable</tt> of
     * <tt>Camera.Size</tt>s. The elements of the specified <tt>Iterable</tt> are delimited by
     * &quot;, &quot;. The method has been introduced because the <tt>Camera.Size</tt> class does
     * not provide a <tt>String</tt> representation which contains the <tt>width</tt> and the
     * <tt>height</tt> in human-readable form.
     *
     * @param sizes the <tt>Iterable</tt> of <tt>Camera.Size</tt>s which is to be represented as a
     * human-readable <tt>String</tt>
     * @return the human-readable <tt>String</tt> representation of the specified <tt>sizes</tt>
     */
    public static String cameraSizesToString(Iterable<Camera.Size> sizes)
    {
        StringBuilder s = new StringBuilder();

        for (Camera.Size size : sizes) {
            if (s.length() != 0)
                s.append(", ");
            s.append(size.width).append('x').append(size.height);
        }
        return s.toString();
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
    public static String dimensionsToString(Iterable<Dimension> sizes)
    {
        StringBuilder s = new StringBuilder();
        for (Dimension size : sizes) {
            if (s.length() != 0)
                s.append(", ");
            s.append(size.width).append('x').append(size.height);
        }
        return s.toString();
    }

    /**
     * Returns the string representation of the formats contained in given list.
     *
     * @param formats the list of image formats integers defined in <tt>ImageFormat</tt> class.
     * @return the string representation of the formats contained in given list.
     */
    public static String cameraImgFormatsToString(List<Integer> formats)
    {
        StringBuilder s = new StringBuilder();

        for (int format : formats) {
            if (s.length() != 0)
                s.append(", ");

            switch (format) {
                case ImageFormat.YV12:
                    s.append("YV12");
                    break;
                case ImageFormat.NV21:
                    s.append("NV21");
                    break;
                case ImageFormat.JPEG:
                    s.append("JPEG");
                    break;
                case ImageFormat.NV16:
                    s.append("NV16");
                    break;
                case ImageFormat.RGB_565:
                    s.append("RGB_565");
                    break;
                case ImageFormat.YUY2:
                    s.append("YUY2");
                    break;
                default:
                    s.append("?");
            }
        }
        return s.toString();
    }

    /**
     * Gets a <tt>Camera</tt> instance which corresponds to a specific <tt>MediaLocator</tt>.
     *
     * @param locator the <tt>MediaLocator</tt> specifying/describing the <tt>Camera</tt> instance to get
     * @return a <tt>Camera</tt> instance which corresponds to the specified <tt>MediaLocator</tt>
     * @throws java.io.IOException if an I/O error occurs while getting the <tt>Camera</tt> instance
     */
    public static Camera getCamera(MediaLocator locator)
            throws IOException
    {
        if (locator == null) {
            return null;
        }

        int cameraId = AndroidCamera.getCameraId(locator);
        Camera camera = Camera.open(cameraId);
        /*
         * Tell the Camera that the intent of the application is to record video, not to take still
         * pictures in order to enable MediaRecorder to start faster and with fewer glitches on output.
         */
        try {
            Method setRecordingHint = Camera.Parameters.class.getMethod("setRecordingHint", Boolean.class);
            Camera.Parameters params = camera.getParameters();
            setRecordingHint.invoke(params, Boolean.TRUE);
            camera.setParameters(params);
        } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException
                | NoSuchMethodException iae) {
            // Ignore because we only tried to set a hint.
        }
        return camera;
    }

    /**
     * Sets the {@link PreviewSurfaceProvider} that will be used with camera
     *
     * @param provider the surface provider to set
     */
    public static void setPreviewSurfaceProvider(PreviewSurfaceProvider provider)
    {
        surfaceProvider = provider;
    }

    /**
     * Calculates camera preview orientation value for the {@link android.view.Display}'s
     * <tt>rotation</tt> in degrees.
     *
     * @return camera preview orientation value in degrees that can be used to adjust the preview
     * using method {@link android.hardware.Camera#setDisplayOrientation(int)}.
     */
    public static int getCameraDisplayRotation(int cameraId)
    {
        // rotation current {@link android.view.Display} rotation value.
        int rotation = surfaceProvider.getDisplayRotation();

        Camera.CameraInfo camInfo = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, camInfo);
        int cameraRotationOffset = camInfo.orientation;

        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        int displayRotation;
        boolean isFrontFacingCam = (camInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT);
        if (isFrontFacingCam) {
            displayRotation = (cameraRotationOffset + degrees) % 360;
            displayRotation = (360 - displayRotation) % 360; // compensate the mirror
        }
        else {
            // back-facing
            displayRotation = (cameraRotationOffset - degrees + 360) % 360;
        }
        return displayRotation;
    }

    /**
     * Releases the camera.
     *
     * @param camera the camera to release.
     */
    public static void releaseCamera(final Camera camera)
    {
        camera.stopPreview();
        surfaceProvider.onObjectReleased();
        camera.release();
    }

    /**
     * Returns <tt>SurfaceHolder</tt> that should be used for displaying camera preview.
     *
     * @return <tt>SurfaceHolder</tt> that should be used for displaying camera preview.
     */
    public static SurfaceHolder obtainPreviewSurface()
    {
        return surfaceProvider.obtainObject();
    }

    /**
     * Get the optimize size that is supported by the camera resolution capability
     * closely match to the preview size requested.
     * Note: Camera native natural oritation is always in landscape mode
     *
     * @param previewSize requested preview size
     * @param sizes List of camera supported sizes
     * @return optimized preview size based on camera capability
     */
    public static Dimension getOptimalPreviewSize(Dimension previewSize, List<Camera.Size> sizes)
    {
        if (sizes == null)
            return previewSize;

        int w = previewSize.width;
        int h = previewSize.height;

        int maxH = sizes.get(0).height;
        final double ASPECT_TOLERANCE = 0.05;
        double targetRatio = (double) w / h;

        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        // obtain the highest possible resolution
        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE)
                continue;

            if (Math.abs(size.height - h) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - h);
            }
        }

        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                if (Math.abs(size.height - h) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - h);
                }
            }
        }
        return new Dimension(optimalSize.width, optimalSize.height);
    }

    /**
     * Store the supported video resolution by camera cameraId
     * @param cameraId camera ID
     * @param supportSizes list of camera support video resolutions
     */
    public static void setCameraSupportSize(int cameraId, List<Camera.Size> supportSizes)
    {
        cameraSupportSize.put(cameraId, supportSizes);
    }

    /**
     * Get the list of camera video resolutions supported by cameraId
     *
     * @param cameraId the request camera Id resolutions
     * @return List of camera video resolutions supported by cameraId
     */
    public static List<Camera.Size> getSupportSizeForCameraId(int cameraId)
    {
        return cameraSupportSize.get(cameraId);
    }

    public static boolean getSupportedSizes(String vs, List<Dimension> sizes)
    {
        if (!TextUtils.isEmpty(vs)) {
            String[] videoSizes = vs.split(", ");
            for (String videoSize : videoSizes) {
                if (!TextUtils.isEmpty(videoSize) && videoSize.contains("x")) {
                    String[] wh = videoSize.split("x");
                    Dimension candidate = new Dimension(Integer.parseInt(wh[0]), Integer.parseInt(wh[1]));
                    if (CameraUtils.isPreferredSize(candidate)) {
                        sizes.add(candidate);
                    }
                }
            }
            return true;
        }
        return false;
    }
}
