/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device;

import org.atalk.impl.neomedia.MediaServiceImpl;
import org.atalk.impl.neomedia.codec.FFmpeg;
import org.atalk.impl.neomedia.codec.video.AVFrameFormat;
import org.atalk.impl.neomedia.jmfext.media.protocol.video4linux2.DataSource;
import org.atalk.impl.neomedia.jmfext.media.protocol.video4linux2.Video4Linux2;
import org.atalk.util.MediaType;

import javax.media.CaptureDeviceInfo;
import javax.media.CaptureDeviceManager;
import javax.media.Format;
import javax.media.MediaLocator;

import timber.log.Timber;

/**
 * Discovers and registers <code>CaptureDevice</code>s which implement the Video for Linux Two API
 * Specification with JMF.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public class Video4Linux2System extends DeviceSystem
{
    /**
     * The protocol of the <code>MediaLocator</code>s identifying <code>CaptureDevice</code> which implement
     * the Video for Linux Two API Specification.
     */
    private static final String LOCATOR_PROTOCOL = LOCATOR_PROTOCOL_VIDEO4LINUX2;

    /**
     * Initializes a new <code>Video4Linux2System</code> instance which discovers and registers
     * <code>CaptureDevice</code>s which implement the Video for Linux Two API Specification with JMF.
     *
     * @throws Exception if anything goes wrong while discovering and registering <code>CaptureDevice</code>s
     * which implement the Video for Linux Two API Specification with JMF
     */
    public Video4Linux2System()
            throws Exception
    {
        super(MediaType.VIDEO, LOCATOR_PROTOCOL);
    }

    /**
     * Discovers and registers a <code>CaptureDevice</code> implementing the Video for Linux Two API
     * Specification with a specific device name with JMF.
     *
     * @param deviceName the device name of a candidate for a <code>CaptureDevice</code> implementing the Video for
     * Linux Two API Specification to be discovered and registered with JMF
     * @return <code>true</code> if a <code>CaptureDeviceInfo</code> for the specified
     * <code>CaptureDevice</code> has been added to <code>CaptureDeviceManager</code>; otherwise,
     * <code>false</code>
     * @throws Exception if anything goes wrong while discovering and registering the specified
     * <code>CaptureDevice</code> with JMF
     */
    private boolean discoverAndRegister(String deviceName)
            throws Exception
    {
        int fd = Video4Linux2.open(deviceName, Video4Linux2.O_RDWR);
        boolean captureDeviceInfoIsAdded = false;

        if (-1 != fd) {
            try {
                long v4l2_capability = Video4Linux2.v4l2_capability_alloc();

                if (0 != v4l2_capability) {
                    try {
                        if ((Video4Linux2.ioctl(fd, Video4Linux2.VIDIOC_QUERYCAP, v4l2_capability) != -1)
                                && ((Video4Linux2.v4l2_capability_getCapabilities(v4l2_capability) & Video4Linux2.V4L2_CAP_VIDEO_CAPTURE) == Video4Linux2.V4L2_CAP_VIDEO_CAPTURE)) {
                            captureDeviceInfoIsAdded = register(deviceName, fd, v4l2_capability);
                        }
                    } finally {
                        Video4Linux2.free(v4l2_capability);
                    }
                }
            } finally {
                Video4Linux2.close(fd);
            }
        }
        return captureDeviceInfoIsAdded;
    }

    @Override
    protected void doInitialize()
            throws Exception
    {
        String baseDeviceName = "/dev/video";
        boolean captureDeviceInfoIsAdded = discoverAndRegister(baseDeviceName);

        for (int deviceMinorNumber = 0; deviceMinorNumber <= 63; deviceMinorNumber++) {
            captureDeviceInfoIsAdded = discoverAndRegister(baseDeviceName + deviceMinorNumber)
                    || captureDeviceInfoIsAdded;
        }
        if (captureDeviceInfoIsAdded && !MediaServiceImpl.isJmfRegistryDisableLoad())
            CaptureDeviceManager.commit();
    }

    /**
     * Registers a <code>CaptureDevice</code> implementing the Video for Linux Two API Specification
     * with a specific device name, a specific <code>open()</code> file descriptor and a specific
     * <code>v4l2_capability</code> with JMF.
     *
     * @param deviceName name of the device (i.e. /dev/videoX)
     * @param fd file descriptor of the device
     * @param v4l2_capability device V4L2 capability
     * @return <code>true</code> if a <code>CaptureDeviceInfo</code> for the specified
     * <code>CaptureDevice</code> has been added to <code>CaptureDeviceManager</code>; otherwise,
     * <code>false</code>
     * @throws Exception if anything goes wrong while registering the specified <code>CaptureDevice</code> with
     * JMF
     */
    private boolean register(String deviceName, int fd, long v4l2_capability)
            throws Exception
    {
        long v4l2_format = Video4Linux2.v4l2_format_alloc(Video4Linux2.V4L2_BUF_TYPE_VIDEO_CAPTURE);
        int pixelformat = 0;
        String supportedRes = null;

        if (0 != v4l2_format) {
            try {
                if (Video4Linux2.ioctl(fd, Video4Linux2.VIDIOC_G_FMT, v4l2_format) != -1) {
                    long fmtPix = Video4Linux2.v4l2_format_getFmtPix(v4l2_format);

                    pixelformat = Video4Linux2.v4l2_pix_format_getPixelformat(fmtPix);

                    if (FFmpeg.PIX_FMT_NONE == DataSource.getFFmpegPixFmt(pixelformat)) {
                        Video4Linux2.v4l2_pix_format_setPixelformat(fmtPix,
                                Video4Linux2.V4L2_PIX_FMT_RGB24);
                        if (Video4Linux2.ioctl(fd, Video4Linux2.VIDIOC_S_FMT, v4l2_format) != -1) {
                            pixelformat = Video4Linux2.v4l2_pix_format_getPixelformat(fmtPix);
                        }
                    }
                    supportedRes = Video4Linux2.v4l2_pix_format_getWidth(fmtPix) + "x"
                            + Video4Linux2.v4l2_pix_format_getHeight(fmtPix);
                }
            } finally {
                Video4Linux2.free(v4l2_format);
            }
        }

        Format format;
        int ffmpegPixFmt = DataSource.getFFmpegPixFmt(pixelformat);

        if (FFmpeg.PIX_FMT_NONE != ffmpegPixFmt)
            format = new AVFrameFormat(ffmpegPixFmt, pixelformat);
        else
            return false;

        String name = Video4Linux2.v4l2_capability_getCard(v4l2_capability);

        if ((name == null) || (name.length() == 0))
            name = deviceName;
        else
            name += " (" + deviceName + ")";

        if (supportedRes != null) {
            Timber.i("Webcam available resolution for %s:%s", name, supportedRes);
        }

        CaptureDeviceManager.addDevice(new CaptureDeviceInfo(name, new MediaLocator(
                LOCATOR_PROTOCOL + ":" + deviceName), new Format[]{format}));
        return true;
    }
}
