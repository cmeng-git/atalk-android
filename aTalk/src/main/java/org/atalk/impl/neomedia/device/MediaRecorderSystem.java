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
import android.support.v4.content.ContextCompat;

import net.java.sip.communicator.util.Logger;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.util.java.awt.Dimension;
import org.atalk.impl.neomedia.device.util.AndroidCamera;
import org.atalk.impl.neomedia.device.util.CameraUtils;
import org.atalk.impl.neomedia.format.ParameterizedVideoFormat;
import org.atalk.impl.neomedia.format.VideoMediaFormatImpl;
import org.atalk.service.neomedia.MediaType;
import org.atalk.service.neomedia.codec.Constants;

import java.util.*;

import javax.media.*;

/**
 * Discovers and registers <tt>MediaRecorder</tt> capture devices with FMJ.
 *
 * @author Lyubomir Marinov
 * @author Pawel Domas
 */
public class MediaRecorderSystem extends DeviceSystem
{
    /**
     * The logger used by the <tt>MediaRecorderSystem</tt> class and its instances for logging
     * output.
     */
    private static final Logger logger = Logger.getLogger(MediaRecorderSystem.class);

    public static final String CAMERA_FACING_BACK = "CAMERA_FACING_BACK";

    public static final String CAMERA_FACING_FRONT = "CAMERA_FACING_FRONT";

    /**
     * The protocol of the <tt>MediaLocator</tt>s identifying <tt>MediaRecorder</tt> capture
     * devices.
     */
    private static final String LOCATOR_PROTOCOL = LOCATOR_PROTOCOL_MEDIARECORDER;

    public static Dimension[] SUPPORTED_SIZES = new Dimension[]{};

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
     * &quot;. The method has been introduced to match
     * {@link CameraUtils#cameraSizesToString(Iterable)}.
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
            throws Exception
    {
        int cameraCount = Camera.getNumberOfCameras();
        if (cameraCount <= 0)
            return;

        if (ContextCompat.checkSelfPermission(aTalkApp.getGlobalContext(),
                Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            return;

        Camera.CameraInfo cameraInfo = null;
        List<CaptureDeviceInfo> captureDevices = new LinkedList<CaptureDeviceInfo>();

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
            Camera camera = Camera.open(cameraId);
            List<Dimension> sizes = new ArrayList<>(CameraUtils.PREFERRED_SIZES.length);

            // Locator protocol contains camera id and it's facing
            MediaLocator locator = AndroidCamera.constructLocator(LOCATOR_PROTOCOL, cameraId, cameraInfo);

            try {
                Camera.Parameters params = camera.getParameters();
                List<Camera.Size> supportedSizes = params.getSupportedVideoSizes();

                if (supportedSizes == null) {
                    /*
                     * The video size is the same as the preview size.
                     * MediaRecorder.setVideoSize(int,int) will most likely fail, print a line in
                     * logcat and not throw an exception (in DataSource.doStart()).
                     */
                    supportedSizes = params.getSupportedPreviewSizes();
                    if (logger.isDebugEnabled() && (supportedSizes != null)) {
                        logger.debug("Preview sizes supported by " + locator + ": "
                                + CameraUtils.cameraSizesToString(supportedSizes));
                    }
                }
                else if (logger.isDebugEnabled()) {
                    logger.debug("Video sizes supported by " + locator + ": "
                            + CameraUtils.cameraSizesToString(supportedSizes));
                }
                if (supportedSizes != null) {
                    for (Dimension preferredSize : CameraUtils.PREFERRED_SIZES) {
                        for (Camera.Size supportedSize : supportedSizes) {
                            if ((preferredSize.height == supportedSize.height)
                                    && (preferredSize.width == supportedSize.width)) {
                                sizes.add(preferredSize);
                            }
                        }
                    }
                    if (logger.isDebugEnabled()) {
                        logger.debug("Sizes supported by " + locator + ": " + dimensionsToString(sizes));
                    }
                }
            } finally {
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
    }

    //	void checkPermission(){
    //		if (ContextCompat.checkSelfPermission(aTalkApp.getGlobalContext(),
    //				Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
    //
    //			// Should we show an explanation?
    //			if (ActivityCompat.shouldShowRequestPermissionRationale(aTalkApp.getCurrentActivity(),
    //					Manifest.permission.CAMERA)) {
    //
    //				// Show an explanation to the user *asynchronously* -- don't block
    //				// this thread waiting for the user's response! After the user
    //				// sees the explanation, try again to request the permission.
    //
    //			} else {
    //
    //				// No explanation needed, we can request the permission.
    //				ActivityCompat.requestPermissions(aTalkApp.getGlobalContext(),
    //						new String[]{Manifest.permission.CAMERA},
    //						MY_PERMISSIONS_REQUEST_READ_CONTACTS);
    //
    //				// MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
    //				// app-defined int constant. The callback method gets the result of the request.
    //			}
    //		}
    //	}
    //
    //	@Override
    //	public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
    //		switch (requestCode) {
    //			case MY_PERMISSIONS_REQUEST_READ_CONTACTS: {
    //				// If request is cancelled, the result arrays are empty.
    //				if (grantResults.length > 0
    //						&& grantResults[0] == PackageManager.PERMISSION_GRANTED) {
    //
    //					// permission was granted, yay! Do the contacts-related task you need to do.
    //
    //				} else {
    //
    //					// permission denied, boo! Disable the functionality that depends on this permission.
    //				}
    //				return;
    //			}
    //			// other 'case' lines to check for other permissions this app might request
    //		}
    //	}
}
