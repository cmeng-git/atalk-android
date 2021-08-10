/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device;

import org.atalk.impl.neomedia.MediaServiceImpl;
import org.atalk.impl.neomedia.codec.FFmpeg;
import org.atalk.impl.neomedia.codec.video.AVFrameFormat;
import org.atalk.impl.neomedia.quicktime.*;
import org.atalk.util.MediaType;

import javax.media.*;
import javax.media.format.RGBFormat;

import timber.log.Timber;

/**
 * Discovers and registers QuickTime/QTKit capture devices with JMF.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public class QuickTimeSystem extends DeviceSystem
{
    /**
     * The protocol of the <tt>MediaLocator</tt>s identifying QuickTime/QTKit capture devices.
     */
    private static final String LOCATOR_PROTOCOL = LOCATOR_PROTOCOL_QUICKTIME;

    /**
     * Initializes a new <tt>QuickTimeSystem</tt> instance which discovers and registers
     * QuickTime/QTKit capture devices with JMF.
     *
     * @throws Exception if anything goes wrong while discovering and registering QuickTime/QTKit capture defines with JMF
     */
    public QuickTimeSystem()
            throws Exception
    {
        super(MediaType.VIDEO, LOCATOR_PROTOCOL);
    }

    @Override
    protected void doInitialize()
            throws Exception
    {
        QTCaptureDevice[] inputDevices = QTCaptureDevice
                .inputDevicesWithMediaType(QTMediaType.Video);
        boolean captureDeviceInfoIsAdded = false;

        for (QTCaptureDevice inputDevice : inputDevices) {
            CaptureDeviceInfo device = new CaptureDeviceInfo(inputDevice.localizedDisplayName(),
                    new MediaLocator(LOCATOR_PROTOCOL + ':' + inputDevice.uniqueID()), new Format[]{
                    new AVFrameFormat(FFmpeg.PIX_FMT_ARGB), new RGBFormat()});

            for (QTFormatDescription f : inputDevice.formatDescriptions()) {
                Timber.i("Webcam available resolution for %s:%s", inputDevice.localizedDisplayName(),
                        f.sizeForKey(QTFormatDescription.VideoEncodedPixelsSizeAttribute));
            }

            CaptureDeviceManager.addDevice(device);
            captureDeviceInfoIsAdded = true;
            Timber.d("Added CaptureDeviceInfo %s", device);
        }
        if (captureDeviceInfoIsAdded && !MediaServiceImpl.isJmfRegistryDisableLoad())
            CaptureDeviceManager.commit();
    }
}
