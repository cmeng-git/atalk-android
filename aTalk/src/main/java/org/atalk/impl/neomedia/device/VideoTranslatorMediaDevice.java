/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device;

import org.atalk.impl.neomedia.AbstractRTPConnector;
import org.atalk.impl.neomedia.format.MediaFormatImpl;
import org.atalk.service.neomedia.MediaDirection;
import org.atalk.service.neomedia.QualityPreset;
import org.atalk.service.neomedia.codec.EncodingConfiguration;
import org.atalk.service.neomedia.device.MediaDevice;
import org.atalk.service.neomedia.device.MediaDeviceWrapper;
import org.atalk.service.neomedia.format.MediaFormat;
import org.atalk.util.MediaType;
import org.atalk.util.event.VideoEvent;
import org.atalk.util.event.VideoListener;

import java.awt.Component;
import java.util.LinkedList;
import java.util.List;

import javax.media.Format;
import javax.media.Player;
import javax.media.Processor;
import javax.media.protocol.DataSource;

/**
 * Implements a <code>MediaDevice</code> which is to be used in video conferencing implemented with an
 * RTP translator.
 *
 * @author Lyubomir Marinov
 * @author Hristo Terezov
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
public class VideoTranslatorMediaDevice extends AbstractMediaDevice implements MediaDeviceWrapper, VideoListener
{
    /**
     * The <code>MediaDevice</code> which this instance enables to be used in a video conference
     * implemented with an RTP translator.
     */
    private final MediaDeviceImpl device;

    /**
     * The <code>VideoMediaDeviceSession</code> of {@link #device} the <code>outputDataSource</code> of
     * which is the <code>captureDevice</code> of {@link #streamDeviceSessions}.
     */
    private VideoMediaDeviceSession deviceSession;

    /**
     * The <code>MediaStreamMediaDeviceSession</code>s sharing the <code>outputDataSource</code> of
     * {@link #device} as their <code>captureDevice</code>.
     */
    private final List<MediaStreamMediaDeviceSession> streamDeviceSessions = new LinkedList<>();

    /**
     * Initializes a new <code>VideoTranslatorMediaDevice</code> which enables a specific
     * <code>MediaDevice</code> to be used in video conferencing implemented with an RTP translator.
     *
     * @param device the <code>MediaDevice</code> which the new instance is to enable to be used in video
     * conferencing implemented with an RTP translator
     */
    public VideoTranslatorMediaDevice(MediaDeviceImpl device)
    {
        this.device = device;
    }

    /**
     * Releases the resources allocated by this instance in the course of its execution and
     * prepares it to be garbage collected when all {@link #streamDeviceSessions} have been closed.
     *
     * @param streamDeviceSession the <code>MediaStreamMediaDeviceSession</code> which has been closed
     */
    private synchronized void close(MediaStreamMediaDeviceSession streamDeviceSession)
    {
        streamDeviceSessions.remove(streamDeviceSession);
        if (deviceSession != null) {
            deviceSession.removeRTCPFeedbackMessageCreateListner(streamDeviceSession);
        }
        if (streamDeviceSessions.isEmpty()) {
            if (deviceSession != null) {
                deviceSession.removeVideoListener(this);
                deviceSession.close(MediaDirection.SENDRECV);
            }
            deviceSession = null;
        }
        else
            updateDeviceSessionStartedDirection();
    }

    /**
     * Creates a <code>DataSource</code> instance for this <code>MediaDevice</code> which gives access to
     * the captured media.
     *
     * @return a <code>DataSource</code> instance which gives access to the media captured by this
     * <code>MediaDevice</code>
     * @see AbstractMediaDevice#createOutputDataSource()
     */
    @Override
    protected synchronized DataSource createOutputDataSource()
    {
        if (deviceSession == null) {
            MediaFormatImpl<? extends Format> format = null;
            MediaDirection startedDirection = MediaDirection.INACTIVE;

            for (MediaStreamMediaDeviceSession streamDeviceSession : streamDeviceSessions) {
                MediaFormatImpl<? extends Format> streamFormat = streamDeviceSession.getFormat();

                if ((streamFormat != null) && (format == null))
                    format = streamFormat;
                startedDirection = startedDirection.or(streamDeviceSession.getStartedDirection());
            }

            MediaDeviceSession newDeviceSession = device.createSession();
            if (newDeviceSession instanceof VideoMediaDeviceSession) {
                deviceSession = (VideoMediaDeviceSession) newDeviceSession;
                deviceSession.addVideoListener(this);

                for (MediaStreamMediaDeviceSession streamDeviceSession : streamDeviceSessions) {
                    deviceSession.addRTCPFeedbackMessageCreateListener(streamDeviceSession);
                }
            }
            if (format != null)
                deviceSession.setFormat(format);

            deviceSession.start(startedDirection);
        }
        return (deviceSession == null) ? null : deviceSession.getOutputDataSource();
    }

    /**
     * Creates a new <code>MediaDeviceSession</code> instance which is to represent the use of this
     * <code>MediaDevice</code> by a <code>MediaStream</code>.
     *
     * @return a new <code>MediaDeviceSession</code> instance which is to represent the use of this
     * <code>MediaDevice</code> by a <code>MediaStream</code>
     * @see AbstractMediaDevice#createSession()
     */
    @Override
    public synchronized MediaDeviceSession createSession()
    {
        MediaStreamMediaDeviceSession streamDeviceSession = new MediaStreamMediaDeviceSession();

        streamDeviceSessions.add(streamDeviceSession);
        return streamDeviceSession;
    }

    /**
     * Returns the <code>MediaDirection</code> supported by this device.
     *
     * @return <code>MediaDirection.SENDONLY</code> if this is a read-only device,
     * <code>MediaDirection.RECVONLY</code> if this is a write-only device and
     * <code>MediaDirection.SENDRECV</code> if this <code>MediaDevice</code> can both capture and
     * render media
     * @see MediaDevice#getDirection()
     */
    public MediaDirection getDirection()
    {
        return device.getDirection();
    }

    /**
     * Returns the <code>MediaFormat</code> that this device is currently set to use when capturing
     * data.
     *
     * @return the <code>MediaFormat</code> that this device is currently set to provide media in.
     * @see MediaDevice#getFormat()
     */
    public MediaFormat getFormat()
    {
        return device.getFormat();
    }

    /**
     * Returns the <code>MediaType</code> that this device supports.
     *
     * @return <code>MediaType.AUDIO</code> if this is an audio device or <code>MediaType.VIDEO</code> in
     * case of a video device
     * @see MediaDevice#getMediaType()
     */
    public MediaType getMediaType()
    {
        return device.getMediaType();
    }

    /**
     * Returns a list of <code>MediaFormat</code> instances representing the media formats supported by
     * this <code>MediaDevice</code>.
     *
     * @param localPreset the preset used to set the send format parameters, used for video and settings
     * @param remotePreset the preset used to set the receive format parameters, used for video and settings
     * @return the list of <code>MediaFormat</code>s supported by this device
     * @see MediaDevice#getSupportedFormats(QualityPreset, QualityPreset)
     */
    public List<MediaFormat> getSupportedFormats(QualityPreset localPreset,
            QualityPreset remotePreset)
    {
        return device.getSupportedFormats(localPreset, remotePreset);
    }

    /**
     * Returns a list of <code>MediaFormat</code> instances representing the media formats supported by
     * this <code>MediaDevice</code> and enabled in <code>encodingConfiguration</code>..
     *
     * @param localPreset the preset used to set the send format parameters, used for video and settings
     * @param remotePreset the preset used to set the receive format parameters, used for video and settings
     * @param encodingConfiguration the <code>EncodingConfiguration</code> instance to use
     * @return the list of <code>MediaFormat</code>s supported by this device and enabled in
     * <code>encodingConfiguration</code>.
     * @see MediaDevice#getSupportedFormats(QualityPreset, QualityPreset, EncodingConfiguration)
     */
    public List<MediaFormat> getSupportedFormats(QualityPreset localPreset,
            QualityPreset remotePreset, EncodingConfiguration encodingConfiguration)
    {
        return device.getSupportedFormats(localPreset, remotePreset, encodingConfiguration);
    }

    /**
     * Gets the actual <code>MediaDevice</code> which this <code>MediaDevice</code> is effectively built on
     * top of and forwarding to.
     *
     * @return the actual <code>MediaDevice</code> which this <code>MediaDevice</code> is effectively built
     * on top of and forwarding to
     * @see MediaDeviceWrapper#getWrappedDevice()
     */
    public MediaDevice getWrappedDevice()
    {
        return device;
    }

    /**
     * Updates the value of the <code>startedDirection</code> property of {@link #deviceSession} to be
     * in accord with the values of the property of {@link #streamDeviceSessions}.
     */
    private synchronized void updateDeviceSessionStartedDirection()
    {
        if (deviceSession == null)
            return;

        MediaDirection startDirection = MediaDirection.INACTIVE;

        for (MediaStreamMediaDeviceSession streamDeviceSession : streamDeviceSessions) {
            startDirection = startDirection.or(streamDeviceSession.getStartedDirection());
        }
        deviceSession.start(startDirection);
        MediaDirection stopDirection = MediaDirection.INACTIVE;

        if (!startDirection.allowsReceiving())
            stopDirection = stopDirection.or(MediaDirection.RECVONLY);
        if (!startDirection.allowsSending())
            stopDirection = stopDirection.or(MediaDirection.SENDONLY);
        deviceSession.stop(stopDirection);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Forwards <code>event</code>, to each of the managed <code>MediaStreamMediaDeviceSession</code>
     * instances. The event is expected to come from <code>this.deviceSession</code>, since
     * <code>this</code> is registered there as a <code>VideoListener</code>.
     */
    @Override
    public void videoAdded(VideoEvent event)
    {
        for (MediaStreamMediaDeviceSession sds : streamDeviceSessions) {
            sds.fireVideoEvent(event, false);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Forwards <code>event</code>, to each of the managed <code>MediaStreamMediaDeviceSession</code>
     * instances. The event is expected to come from <code>this.deviceSession</code>, since
     * <code>this</code> is registered there as a <code>VideoListener</code>.
     */
    @Override
    public void videoRemoved(VideoEvent event)
    {
        for (MediaStreamMediaDeviceSession sds : streamDeviceSessions) {
            sds.fireVideoEvent(event, false);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Forwards <code>event</code>, to each of the managed <code>MediaStreamMediaDeviceSession</code>
     * instances. The event is expected to come from <code>this.deviceSession</code>, since
     * <code>this</code> is registered there as a <code>VideoListener</code>.
     */
    @Override
    public void videoUpdate(VideoEvent event)
    {
        for (MediaStreamMediaDeviceSession sds : streamDeviceSessions) {
            sds.fireVideoEvent(event, false);
        }
    }

    /**
     * Represents the use of this <code>VideoTranslatorMediaDevice</code> by a <code>MediaStream</code>.
     */
    private class MediaStreamMediaDeviceSession extends VideoMediaDeviceSession
    {
        /**
         * Initializes a new <code>MediaStreamMediaDeviceSession</code> which is to represent the
         * use of this <code>VideoTranslatorMediaDevice</code> by a <code>MediaStream</code>.
         */
        public MediaStreamMediaDeviceSession()
        {
            super(VideoTranslatorMediaDevice.this);
        }

        /**
         * Releases the resources allocated by this instance in the course of its execution and
         * prepares it to be garbage collected.
         */
        @Override
        public void close(MediaDirection direction)
        {
            super.close(direction);
            VideoTranslatorMediaDevice.this.close(this);
        }

        /**
         * Creates the <code>DataSource</code> that this instance is to read captured media from.
         *
         * @return the <code>DataSource</code> that this instance is to read captured media from
         * @see VideoMediaDeviceSession#createCaptureDevice()
         */
        @Override
        protected DataSource createCaptureDevice()
        {
            return VideoTranslatorMediaDevice.this.createOutputDataSource();
        }

        /**
         * Initializes a new <code>Player</code> instance which is to provide the local visual/video
         * <code>Component</code>. The new instance is initialized to render the media of a specific
         * <code>DataSource</code>.
         *
         * @param captureDevice the <code>DataSource</code> which is to have its media rendered by the new instance as
         * the local visual/video <code>Component</code>
         * @return a new <code>Player</code> instance which is to provide the local visual/video
         * <code>Component</code>
         */
        @Override
        protected Player createLocalPlayer(DataSource captureDevice)
        {
            synchronized (VideoTranslatorMediaDevice.this) {
                if (deviceSession != null)
                    captureDevice = deviceSession.getCaptureDevice();
            }

            return super.createLocalPlayer(captureDevice);
        }

        /**
         * Initializes a new FMJ <code>Processor</code> which is to transcode {@link #captureDevice}
         * into the format of this instance.
         *
         * @return a new FMJ <code>Processor</code> which is to transcode <code>captureDevice</code> into
         * the format of this instance
         */
        @Override
        protected Processor createProcessor()
        {
            return null;
        }

        /**
         * Gets the output <code>DataSource</code> of this instance which provides the captured (RTP)
         * data to be sent by <code>MediaStream</code> to <code>MediaStreamTarget</code>.
         *
         * @return the output <code>DataSource</code> of this instance which provides the captured
         * (RTP) data to be sent by <code>MediaStream</code> to <code>MediaStreamTarget</code>
         * @see MediaDeviceSession#getOutputDataSource()
         */
        @Override
        public DataSource getOutputDataSource()
        {
            return getConnectedCaptureDevice();
        }

        /**
         * Sets the <code>RTPConnector</code> that will be used to initialize some codec for RTCP
         * feedback and adds the instance to RTCPFeedbackCreateListners of deviceSession.
         *
         * @param rtpConnector the RTP connector
         */
        @Override
        public void setConnector(AbstractRTPConnector rtpConnector)
        {
            super.setConnector(rtpConnector);
            if (deviceSession != null)
                deviceSession.addRTCPFeedbackMessageCreateListener(this);
        }

        /**
         * Notifies this instance that the value of its <code>startedDirection</code> property has
         * changed from a specific <code>oldValue</code> to a specific <code>newValue</code>.
         *
         * @param oldValue the <code>MediaDirection</code> which used to be the value of the
         * <code>startedDirection</code> property of this instance
         * @param newValue the <code>MediaDirection</code> which is the value of the <code>startedDirection</code>
         * property of this instance
         */
        @Override
        protected void startedDirectionChanged(MediaDirection oldValue, MediaDirection newValue)
        {
            super.startedDirectionChanged(oldValue, newValue);
            VideoTranslatorMediaDevice.this.updateDeviceSessionStartedDirection();
        }

        /**
         * {@inheritDoc} Returns the local visual <code>Component</code> for this
         * <code>MediaStreamMediaDeviceSession</code>, which, if present, is maintained in
         * <code>this.deviceSession</code>.
         */
        @Override
        public Component getLocalVisualComponent()
        {
            if (deviceSession != null)
                return deviceSession.getLocalVisualComponent();
            return null;
        }

        /**
         * {@inheritDoc}
         * <p>
         * Creates, if necessary, the local visual <code>Component</code> depicting the video being
         * streamed from the local peer to a remote peer. The <code>Component</code> is provided by the
         * single <code>Player</code> instance, which is maintained for this
         * <code>VideoTranslatorMediaDevice</code> and is managed by <code>this.deviceSession</code>.
         */
        @Override
        protected Component createLocalVisualComponent()
        {
            if (deviceSession != null)
                return deviceSession.createLocalVisualComponent();
            return null;
        }

        /**
         * {@inheritDoc}
         * <p>
         * Returns the <code>Player</code> instance which provides the local visual/video
         * <code>Component</code>. A single <code>Player</code> is maintained for this
         * <code>VideoTranslatorMediaDevice</code>, and it is managed by <code>this.deviceSession</code>.
         */
        @Override
        protected Player getLocalPlayer()
        {
            if (deviceSession != null)
                return deviceSession.getLocalPlayer();
            return null;
        }

        /**
         * {@inheritDoc}
         * <p>
         * Does nothing, because there is no <code>Player</code> associated with this
         * <code>MediaStreamMediaDeviceSession</code> and therefore nothing to dispose of.
         *
         * @param player the <code>Player</code> to dispose of.
         */
        @Override
        protected void disposeLocalPlayer(Player player)
        {
        }

    }
}
