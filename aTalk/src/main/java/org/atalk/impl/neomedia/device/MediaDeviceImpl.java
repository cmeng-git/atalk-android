/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device;

import org.atalk.impl.timberlog.TimberLog;
import org.atalk.impl.neomedia.MediaServiceImpl;
import org.atalk.impl.neomedia.MediaUtils;
import org.atalk.impl.neomedia.NeomediaServiceUtils;
import org.atalk.impl.neomedia.format.MediaFormatImpl;
import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractPullBufferCaptureDevice;
import org.atalk.impl.neomedia.protocol.CaptureDeviceDelegatePushBufferDataSource;
import org.atalk.service.neomedia.MediaDirection;
import org.atalk.service.neomedia.QualityPreset;
import org.atalk.service.neomedia.codec.EncodingConfiguration;
import org.atalk.service.neomedia.device.MediaDevice;
import org.atalk.service.neomedia.device.ScreenDevice;
import org.atalk.service.neomedia.format.MediaFormat;
import org.atalk.util.MediaType;

import java.awt.Dimension;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.media.CaptureDeviceInfo;
import javax.media.Manager;
import javax.media.MediaLocator;
import javax.media.NoDataSourceException;
import javax.media.control.FormatControl;
import javax.media.protocol.CaptureDevice;
import javax.media.protocol.DataSource;
import javax.media.protocol.PushBufferDataSource;

import timber.log.Timber;

/**
 * Implements <code>MediaDevice</code> for the JMF <code>CaptureDevice</code>.
 *
 * @author Lyubomir Marinov
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
public class MediaDeviceImpl extends AbstractMediaDevice
{
    /**
     * Creates a new <code>CaptureDevice</code> which traces calls to a specific <code>CaptureDevice</code>
     * for debugging purposes.
     *
     * @param captureDevice the <code>CaptureDevice</code> which is to have its calls traced for debugging output
     * @return a new <code>CaptureDevice</code> which traces the calls to the specified <code>captureDevice</code>
     */
    public static CaptureDevice createTracingCaptureDevice(CaptureDevice captureDevice)
    {
        if (captureDevice instanceof PushBufferDataSource)
            captureDevice = new CaptureDeviceDelegatePushBufferDataSource(captureDevice)
            {
                @Override
                public void connect()
                        throws IOException
                {
                    super.connect();
                    Timber.log(TimberLog.FINER, "Connected %s", MediaDeviceImpl.toString(this.captureDevice));
                }

                @Override
                public void disconnect()
                {
                    super.disconnect();
                    Timber.log(TimberLog.FINER, "Disconnected %s", MediaDeviceImpl.toString(this.captureDevice));
                }

                @Override
                public void start()
                        throws IOException
                {
                    super.start();
                    Timber.log(TimberLog.FINER, "Started %s", MediaDeviceImpl.toString(this.captureDevice));
                }

                @Override
                public void stop()
                        throws IOException
                {
                    super.stop();
                    Timber.log(TimberLog.FINER, "Stopped %s", MediaDeviceImpl.toString(this.captureDevice));
                }
            };
        return captureDevice;
    }

    /**
     * Returns a human-readable representation of a specific <code>CaptureDevice</code> in the form of a <code>String</code> value.
     *
     * @param captureDevice the <code>CaptureDevice</code> to get a human-readable representation of
     * @return a <code>String</code> value which gives a human-readable representation of the specified <code>captureDevice</code>
     */
    private static String toString(CaptureDevice captureDevice)
    {
        StringBuilder str = new StringBuilder();

        str.append("CaptureDevice with hashCode ");
        str.append(captureDevice.hashCode());
        str.append(" and captureDeviceInfo ");

        CaptureDeviceInfo captureDeviceInfo = captureDevice.getCaptureDeviceInfo();
        MediaLocator mediaLocator = null;
        if (captureDeviceInfo != null) {
            mediaLocator = captureDeviceInfo.getLocator();
        }
        str.append((mediaLocator == null) ? captureDeviceInfo : mediaLocator);
        return str.toString();
    }

    /**
     * The <code>CaptureDeviceInfo</code> of the device that this instance is representing.
     */
    private final CaptureDeviceInfo captureDeviceInfo;

    /**
     * The <code>MediaType</code> of this instance and the <code>CaptureDevice</code> that it wraps.
     */
    private final MediaType mediaType;

    /**
     * Initializes a new <code>MediaDeviceImpl</code> instance which is to provide an implementation of
     * <code>MediaDevice</code> for a <code>CaptureDevice</code> with a specific <code>CaptureDeviceInfo</code>
     * and which is of a specific <code>MediaType</code>.
     *
     * @param captureDeviceInfo the <code>CaptureDeviceInfo</code> of the JMF <code>CaptureDevice</code> the new instance is
     * to provide an implementation of <code>MediaDevice</code> for
     * @param mediaType the <code>MediaType</code> of the new instance
     */
    public MediaDeviceImpl(CaptureDeviceInfo captureDeviceInfo, MediaType mediaType)
    {
        if (captureDeviceInfo == null)
            throw new NullPointerException("captureDeviceInfo");
        if (mediaType == null)
            throw new NullPointerException("mediaType");

        this.captureDeviceInfo = captureDeviceInfo;
        this.mediaType = mediaType;
    }

    /**
     * Initializes a new <code>MediaDeviceImpl</code> instance with a specific <code>MediaType</code> and
     * with <code>MediaDirection</code> which does not allow sending.
     *
     * @param mediaType the <code>MediaType</code> of the new instance
     */
    public MediaDeviceImpl(MediaType mediaType)
    {
        this.captureDeviceInfo = null;
        this.mediaType = mediaType;
    }

    /**
     * Creates the JMF <code>CaptureDevice</code> this instance represents and provides an
     * implementation of <code>MediaDevice</code> for.
     *
     * @return the JMF <code>CaptureDevice</code> this instance represents and provides an
     * implementation of <code>MediaDevice</code> for; <code>null</code> if the creation fails
     */
    protected CaptureDevice createCaptureDevice()
    {
        CaptureDevice captureDevice = null;
        if (getDirection().allowsSending()) {
            CaptureDeviceInfo captureDeviceInfo = getCaptureDeviceInfo();
            Throwable exception = null;

            try {
                captureDevice = (CaptureDevice) Manager.createDataSource(captureDeviceInfo.getLocator());
            } catch (IOException | NoDataSourceException ioe) {
                exception = ioe;
            }

            if (exception != null) {
                Timber.e(exception, "Failed to create CaptureDevice from CaptureDeviceInfo %s",
                        captureDeviceInfo);
            }
            else {
                if (captureDevice instanceof AbstractPullBufferCaptureDevice) {
                    ((AbstractPullBufferCaptureDevice) captureDevice).setCaptureDeviceInfo(captureDeviceInfo);
                }
                // Try to enable tracing on captureDevice.
                captureDevice = createTracingCaptureDevice(captureDevice);
            }
        }
        return captureDevice;
    }

    /**
     * Creates a <code>DataSource</code> instance for this <code>MediaDevice</code> which gives access to the captured media.
     *
     * @return a <code>DataSource</code> instance which gives access to the media captured by this <code>MediaDevice</code>
     * @see AbstractMediaDevice#createOutputDataSource()
     */
    @Override
    protected DataSource createOutputDataSource()
    {
        return getDirection().allowsSending() ? (DataSource) createCaptureDevice() : null;
    }

    /**
     * Gets the <code>CaptureDeviceInfo</code> of the JMF <code>CaptureDevice</code> represented by this instance.
     *
     * @return the <code>CaptureDeviceInfo</code> of the <code>CaptureDevice</code> represented by this instance
     */
    public CaptureDeviceInfo getCaptureDeviceInfo()
    {
        return captureDeviceInfo;
    }

    /**
     * Gets the protocol of the <code>MediaLocator</code> of the <code>CaptureDeviceInfo</code> represented by this instance.
     *
     * @return the protocol of the <code>MediaLocator</code> of the <code>CaptureDeviceInfo</code> represented by this instance
     */
    public String getCaptureDeviceInfoLocatorProtocol()
    {
        CaptureDeviceInfo cdi = getCaptureDeviceInfo();
        if (cdi != null) {
            MediaLocator locator = cdi.getLocator();

            if (locator != null)
                return locator.getProtocol();
        }
        return null;
    }

    /**
     * Returns the <code>MediaDirection</code> supported by this device.
     *
     * @return {@link MediaDirection#SENDONLY} if this is a read-only device,
     * {@link MediaDirection#RECVONLY} if this is a write-only device or
     * {@link MediaDirection#SENDRECV} if this <code>MediaDevice</code> can both capture and render media
     * @see MediaDevice#getDirection()
     */
    public MediaDirection getDirection()
    {
        if (getCaptureDeviceInfo() != null)
            return MediaDirection.SENDRECV;
        else
            return MediaType.AUDIO.equals(getMediaType()) ? MediaDirection.INACTIVE : MediaDirection.RECVONLY;
    }

    /**
     * Gets the <code>MediaFormat</code> in which this <code>MediaDevice</code> captures media.
     *
     * @return the <code>MediaFormat</code> in which this <code>MediaDevice</code> captures media
     * @see MediaDevice#getFormat()
     */
    public MediaFormat getFormat()
    {
        CaptureDevice captureDevice = createCaptureDevice();
        if (captureDevice != null) {
            MediaType mediaType = getMediaType();

            for (FormatControl formatControl : captureDevice.getFormatControls()) {
                MediaFormat format = MediaFormatImpl.createInstance(formatControl.getFormat());

                if ((format != null) && format.getMediaType().equals(mediaType))
                    return format;
            }
        }
        return null;
    }

    /**
     * Gets the <code>MediaType</code> that this device supports.
     *
     * @return {@link MediaType#AUDIO} if this is an audio device or {@link MediaType#VIDEO} if this is a video device
     * @see MediaDevice#getMediaType()
     */
    public MediaType getMediaType()
    {
        return mediaType;
    }

    /**
     * Gets the list of <code>MediaFormat</code>s supported by this <code>MediaDevice</code> and enabled in <code>encodingConfiguration</code>.
     *
     * @param encodingConfiguration the <code>EncodingConfiguration</code> instance to use
     * @return the list of <code>MediaFormat</code>s supported by this device and enabled in <code>encodingConfiguration</code>.
     * @see MediaDevice#getSupportedFormats()
     */
    public List<MediaFormat> getSupportedFormats(EncodingConfiguration encodingConfiguration)
    {
        return getSupportedFormats(null, null, encodingConfiguration);
    }

    /**
     * Gets the list of <code>MediaFormat</code>s supported by this <code>MediaDevice</code>. Uses the
     * current <code>EncodingConfiguration</code> from the media service (i.e. the global configuration).
     *
     * @param sendPreset the preset used to set some of the format parameters, used for video and settings.
     * @param receivePreset the preset used to set the receive format parameters, used for video and settings.
     * @return the list of <code>MediaFormat</code>s supported by this device
     * @see MediaDevice#getSupportedFormats()
     */
    public List<MediaFormat> getSupportedFormats(QualityPreset sendPreset, QualityPreset receivePreset)
    {
        return getSupportedFormats(sendPreset, receivePreset,
                NeomediaServiceUtils.getMediaServiceImpl().getCurrentEncodingConfiguration());
    }

    /**
     * Gets the list of <code>MediaFormat</code>s supported by this <code>MediaDevice</code> and enabled in
     * <code>encodingConfiguration</code>.
     *
     * @param sendPreset the preset used to set some of the format parameters, used for video and settings.
     * @param receivePreset the preset used to set the receive format parameters, used for video and settings.
     * @param encodingConfiguration the <code>EncodingConfiguration</code> instance to use
     * @return the list of <code>MediaFormat</code>s supported by this device and enabled in <code>encodingConfiguration</code>.
     * @see MediaDevice#getSupportedFormats()
     */
    public List<MediaFormat> getSupportedFormats(QualityPreset sendPreset,
            QualityPreset receivePreset, EncodingConfiguration encodingConfiguration)
    {
        MediaServiceImpl mediaServiceImpl = NeomediaServiceUtils.getMediaServiceImpl();
        MediaFormat[] enabledEncodings = encodingConfiguration.getEnabledEncodings(getMediaType());
        List<MediaFormat> supportedFormats = new ArrayList<MediaFormat>();

        // If there is preset, check and set the format attributes where needed.
        if (enabledEncodings != null) {
            for (MediaFormat f : enabledEncodings) {
                if ("h264".equalsIgnoreCase(f.getEncoding())) {
                    Map<String, String> advancedAttrs = f.getAdvancedAttributes();

                    CaptureDeviceInfo captureDeviceInfo = getCaptureDeviceInfo();
                    MediaLocator captureDeviceInfoLocator;
                    Dimension sendSize = null;

                    // change send size only for video calls
                    if ((captureDeviceInfo != null)
                            && ((captureDeviceInfoLocator = captureDeviceInfo.getLocator()) != null)
                            && !DeviceSystem.LOCATOR_PROTOCOL_IMGSTREAMING
                            .equals(captureDeviceInfoLocator.getProtocol())) {
                        if (sendPreset != null)
                            sendSize = sendPreset.getResolution();
                        else {
                            /*
                             * XXX We cannot default to any video size here because we do not know
                             * how this MediaDevice instance will be used. If the caller wanted to
                             * limit the video size, she would've specified an actual sendPreset.
                             */
                            // sendSize = mediaServiceImpl.getDeviceConfiguration().getVideoSize();
                        }
                    }
                    Dimension receiveSize;
                    // if there is specified preset, send its settings
                    if (receivePreset != null)
                        receiveSize = receivePreset.getResolution();
                    else {
                        // or just send the max video resolution of the PC as we do by default
                        ScreenDevice screen = mediaServiceImpl.getDefaultScreenDevice();
                        receiveSize = (screen == null) ? null : screen.getSize();
                    }

                    advancedAttrs.put("imageattr",
                            MediaUtils.createImageAttr(sendSize, receiveSize));
                    f = mediaServiceImpl.getFormatFactory().createMediaFormat(f.getEncoding(),
                            f.getClockRate(), f.getFormatParameters(), advancedAttrs);
                }
                if (f != null)
                    supportedFormats.add(f);
            }
        }
        return supportedFormats;
    }

    /**
     * Gets a human-readable <code>String</code> representation of this instance.
     *
     * @return a <code>String</code> providing a human-readable representation of this instance
     */
    @Override
    public String toString()
    {
        CaptureDeviceInfo captureDeviceInfo = getCaptureDeviceInfo();
        return (captureDeviceInfo == null) ? super.toString() : captureDeviceInfo.toString();
    }
}
