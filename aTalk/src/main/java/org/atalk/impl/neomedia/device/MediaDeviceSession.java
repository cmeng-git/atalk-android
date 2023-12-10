/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device;

import org.atalk.android.aTalkApp;
import org.atalk.android.plugin.timberlog.TimberLog;
import org.atalk.impl.neomedia.MediaStreamImpl;
import org.atalk.impl.neomedia.ProcessorUtility;
import org.atalk.impl.neomedia.control.AbstractControls;
import org.atalk.impl.neomedia.format.MediaFormatImpl;
import org.atalk.impl.neomedia.format.ParameterizedVideoFormat;
import org.atalk.impl.neomedia.protocol.InbandDTMFDataSource;
import org.atalk.impl.neomedia.protocol.MuteDataSource;
import org.atalk.impl.neomedia.protocol.RewritablePullBufferDataSource;
import org.atalk.impl.neomedia.protocol.RewritablePushBufferDataSource;
import org.atalk.impl.neomedia.protocol.TranscodingDataSource;
import org.atalk.service.neomedia.DTMFInbandTone;
import org.atalk.service.neomedia.MediaDirection;
import org.atalk.service.neomedia.codec.Constants;
import org.atalk.service.neomedia.control.AdvancedAttributesAwareCodec;
import org.atalk.service.neomedia.control.FormatParametersAwareCodec;
import org.atalk.service.neomedia.device.MediaDevice;
import org.atalk.service.neomedia.format.MediaFormat;
import org.atalk.util.MediaType;
import org.atalk.util.OSUtils;
import org.atalk.util.event.PropertyChangeNotifier;

import java.awt.Dimension;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.media.ConfigureCompleteEvent;
import javax.media.ControllerClosedEvent;
import javax.media.ControllerErrorEvent;
import javax.media.ControllerEvent;
import javax.media.ControllerListener;
import javax.media.Format;
import javax.media.Manager;
import javax.media.NoProcessorException;
import javax.media.NotConfiguredError;
import javax.media.NotRealizedError;
import javax.media.Player;
import javax.media.Processor;
import javax.media.RealizeCompleteEvent;
import javax.media.Renderer;
import javax.media.UnsupportedPlugInException;
import javax.media.control.FormatControl;
import javax.media.control.TrackControl;
import javax.media.format.AudioFormat;
import javax.media.format.VideoFormat;
import javax.media.protocol.ContentDescriptor;
import javax.media.protocol.DataSource;
import javax.media.protocol.PullBufferDataSource;
import javax.media.protocol.PushBufferDataSource;
import javax.media.rtp.ReceiveStream;

import timber.log.Timber;

/**
 * Represents the use of a specific <code>MediaDevice</code> by a <code>MediaStream</code>.
 *
 * @author Lyubomir Marinov
 * @author Damian Minkov
 * @author Emil Ivov
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
public class MediaDeviceSession extends PropertyChangeNotifier
{
    /**
     * The name of the <code>MediaDeviceSession</code> instance property the value of which represents
     * the output <code>DataSource</code> of the <code>MediaDeviceSession</code> instance which provides
     * the captured (RTP) data to be sent by <code>MediaStream</code> to <code>MediaStreamTarget</code>.
     */
    public static final String OUTPUT_DATA_SOURCE = "OUTPUT_DATA_SOURCE";

    /**
     * The name of the property that corresponds to the array of SSRC identifiers that we store in this
     * <code>MediaDeviceSession</code> instance and that we update upon adding and removing <code>ReceiveStream</code>
     */
    public static final String SSRC_LIST = "SSRC_LIST";

    /**
     * The JMF <code>DataSource</code> of {@link #device} through which this instance accesses the media captured by it.
     */
    private DataSource captureDevice;

    /**
     * The indicator which determines whether {@link DataSource#connect()} has been successfully
     * executed on {@link #captureDevice}.
     */
    private boolean captureDeviceIsConnected;

    /**
     * The <code>ContentDescriptor</code> which specifies the content type in which this
     * <code>MediaDeviceSession</code> is to output the media captured by its <code>MediaDevice</code>.
     */
    private ContentDescriptor contentDescriptor;

    /**
     * The <code>MediaDevice</code> used by this instance to capture and play back media.
     */
    private final AbstractMediaDevice device;

    /**
     * The last JMF <code>Format</code> set to this instance by a call to its
     * {@link #setFormat(MediaFormat)} and to be set as the output format of {@link #processor}.
     */
    private MediaFormatImpl<? extends Format> format;

    /**
     * The indicator which determines whether this <code>MediaDeviceSession</code> is set to output
     * "silence" instead of the actual media captured from {@link #captureDevice}.
     */
    private boolean mute = false;

    /**
     * The list of playbacks of <code>ReceiveStream</code>s and/or <code>DataSource</code>s performed by
     * respective <code>Player</code>s on the <code>MediaDevice</code> represented by this instance. The
     * (read and write) accesses to the field are to be synchronized using {@link #playbacksLock}.
     */
    private final List<Playback> playbacks = new LinkedList<>();

    /**
     * The <code>ReadWriteLock</code> which is used to synchronize the (read and write) accesses to {@link #playbacks}.
     */
    private final ReadWriteLock playbacksLock = new ReentrantReadWriteLock();

    /**
     * The <code>ControllerListener</code> which listens to the <code>Player</code>s of
     * {@link #playbacks} for <code>ControllerEvent</code>s.
     */
    private final ControllerListener playerControllerListener = new ControllerListener()
    {
        /**
         * Notifies this <code>ControllerListener</code> that the <code>Controller</code> which it is
         * registered with has generated an event.
         *
         * @param ev the <code>ControllerEvent</code> specifying the <code>Controller</code> which is
         * the source of the event, and the very type of the event.
         * @see ControllerListener#controllerUpdate(ControllerEvent)
         */
        public void controllerUpdate(ControllerEvent ev)
        {
            // Timber.w("Media device session controller updated: %s", ev.getSource());
            playerControllerUpdate(ev);
        }
    };

    /**
     * The JMF <code>Processor</code> which transcodes {@link #captureDevice} into the format of this instance.
     */
    private Processor processor;

    /**
     * The <code>ControllerListener</code> which listens to {@link #processor} for <code>ControllerEvent</code>s.
     */
    private ControllerListener processorControllerListener;

    /**
     * The indicator which determines whether {@link #processor} has received a
     * <code>ControllerClosedEvent</code> at an unexpected time in its execution. A value of
     * <code>false</code> does not mean that <code>processor</code> exists or that it is not closed, it
     * just means that if <code>processor</code> failed to be initialized or it received a
     * <code>ControllerClosedEvent</code>, it was at an expected time of its execution and that the
     * fact in question was reflected, for example, by setting <code>processor</code> to <code>null</code>.
     * If there is no <code>processorIsPrematurelyClosed</code> field and <code>processor</code> is set to
     * <code>null</code> or left existing after the receipt of <code>ControllerClosedEvent</code>, it will
     * either lead to not firing a <code>PropertyChangeEvent</code> for <code>OUTPUT_DATA_SOURCE</code>
     * when it has actually changed and, consequently, cause the <code>SendStream</code>s of
     * <code>MediaStreamImpl</code> to not be recreated or it will be impossible to detect that
     * <code>processor</code> cannot have its format set and will thus be left broken even for
     * subsequent calls to {@link #setFormat(MediaFormat)}.
     */
    private boolean processorIsPrematurelyClosed;

    /**
     * The list of SSRC identifiers representing the parties that we are currently handling receive streams from.
     */
    private long[] ssrcList = null;

    /**
     * The <code>MediaDirection</code> in which this <code>MediaDeviceSession</code> has been started.
     */
    private MediaDirection startedDirection = MediaDirection.INACTIVE;

    /**
     * If the player have to be disposed when we {@link #close(MediaDirection)} this instance.
     */
    private boolean disposePlayerOnClose = true;

    /**
     * Whether output size has changed after latest processor config. Used for video streams.
     */
    protected boolean outputSizeChanged = false;

    /**
     * Whether this device session is used by a stream which uses a translator.
     */
    public boolean useTranslator = false;

    /**
     * Initializes a new <code>MediaDeviceSession</code> instance which is to represent the use of a
     * specific <code>MediaDevice</code> by a <code>MediaStream</code>.
     *
     * @param device the <code>MediaDevice</code> the use of which by a <code>MediaStream</code> is to be
     * represented by the new instance
     */
    protected MediaDeviceSession(AbstractMediaDevice device)
    {
        checkDevice(device);
        this.device = device;
    }

    /**
     * Sets the indicator which determines whether this instance is to dispose of its associated
     * player upon closing.
     *
     * @param disposePlayerOnClose <code>true</code> to have this instance dispose of its associated player upon closing;
     * otherwise, <code>false</code>
     */
    public void setDisposePlayerOnClose(boolean disposePlayerOnClose)
    {
        this.disposePlayerOnClose = disposePlayerOnClose;
    }

    /**
     * Adds <code>ssrc</code> to the array of SSRC identifiers representing parties that this
     * <code>MediaDeviceSession</code> is currently receiving streams from. We use this method mostly
     * as a way of to caching SSRC identifiers during a conference call so that the streams that
     * are sending CSRC lists could have them ready for use rather than have to construct them for
     * every RTP packet.
     *
     * @param ssrc the new SSRC identifier that we'd like to add to the array of <code>ssrc</code>
     * identifiers stored by this session.
     */
    protected void addSSRC(long ssrc)
    {
        // init if necessary
        if (ssrcList == null) {
            setSsrcList(new long[]{ssrc});
            return;
        }

        // check whether we already have this ssrc
        for (long aSsrcList : ssrcList) {
            if (ssrc == aSsrcList)
                return;
        }

        // resize the array and add the new ssrc to the end.
        long[] newSsrcList = new long[ssrcList.length + 1];

        System.arraycopy(ssrcList, 0, newSsrcList, 0, ssrcList.length);
        newSsrcList[newSsrcList.length - 1] = ssrc;
        setSsrcList(newSsrcList);
    }

    /**
     * For JPEG, we know that they only work for particular sizes. So we'll perform extra
     * checking here to make sure they are of the right sizes.
     *
     * @param sourceFormat the original format to check the size of
     * @return the modified <code>VideoFormat</code> set to the size we support
     */
    private static VideoFormat assertSize(VideoFormat sourceFormat)
    {
        int width, height;

        // JPEG
        if (sourceFormat.matches(new Format(VideoFormat.JPEG_RTP))) {
            Dimension size = sourceFormat.getSize();

            // For JPEG, make sure width and height are divisible by 8.
            width = (size.width % 8 == 0) ? size.width : ((size.width / 8) * 8);
            height = (size.height % 8 == 0) ? size.height : ((size.height / 8) * 8);
        }
        else {
            // For other video format, we'll just leave it alone then.
            return sourceFormat;
        }

        VideoFormat result = new VideoFormat(
                null,
                new Dimension(width, height),
                Format.NOT_SPECIFIED,
                null,
                Format.NOT_SPECIFIED);
        return (VideoFormat) result.intersects(sourceFormat);
    }

    /**
     * Asserts that a specific <code>MediaDevice</code> is acceptable to be set as the
     * <code>MediaDevice</code> of this instance. Allows extenders to override and customize the check.
     *
     * @param device the <code>MediaDevice</code> to be checked for suitability to become the
     * <code>MediaDevice</code> of this instance
     */
    protected void checkDevice(AbstractMediaDevice device)
    {
    }

    /**
     * Releases the resources allocated by this instance in the course of its execution and
     * prepares it to be garbage collected.
     *
     * cmeng: should close only the required direction e.g. toggle camera should not close remote video
     * Need to clean up this section
     */
    public void close(MediaDirection direction)
    {
        try {
            stop(direction);
        } finally {
            /*
             * XXX The order of stopping the playback and capture is important here because when we
             * use echo cancellation the capture accesses data from the playback and thus there is
             * synchronization to avoid segfaults but this synchronization can sometimes lead to a
             * slow stop of the playback. That is why we stop the capture first.
             */

            // capture
            disconnectCaptureDevice();
            closeProcessor();

            // playback
            if (disposePlayerOnClose)
                disposePlayer();

            processor = null;
            captureDevice = null;
        }
    }

    /**
     * Makes sure {@link #processor} is closed.
     */
    private void closeProcessor()
    {
        if (processor != null) {
            if (processorControllerListener != null)
                processor.removeControllerListener(processorControllerListener);

            processor.stop();
            Timber.log(TimberLog.FINER, "Stopped Processor with hashCode %s", processor.hashCode());

            if (processor.getState() == Processor.Realized) {
                DataSource dataOutput;

                try {
                    dataOutput = processor.getDataOutput();
                } catch (NotRealizedError nre) {
                    dataOutput = null;
                }
                if (dataOutput != null)
                    dataOutput.disconnect();
            }
            processor.deallocate();
            processor.close();
            processorIsPrematurelyClosed = false;

            /*
             * Once the processor uses the captureDevice, the captureDevice has to be reconnected
             * on its next use.
             */
            disconnectCaptureDevice();
        }
    }

    /**
     * Creates the <code>DataSource</code> that this instance is to read captured media from.
     *
     * @return the <code>DataSource</code> that this instance is to read captured media from
     */
    protected DataSource createCaptureDevice()
    {
        DataSource captureDevice = getDevice().createOutputDataSource();
        if (captureDevice != null) {
            MuteDataSource muteDataSource = AbstractControls.queryInterface(captureDevice, MuteDataSource.class);
            if (muteDataSource == null) {
                // Try to enable muting.
                if (captureDevice instanceof PushBufferDataSource) {
                    captureDevice = new RewritablePushBufferDataSource((PushBufferDataSource) captureDevice);
                }
                else if (captureDevice instanceof PullBufferDataSource) {
                    captureDevice = new RewritablePullBufferDataSource((PullBufferDataSource) captureDevice);
                }
                muteDataSource = AbstractControls.queryInterface(captureDevice, MuteDataSource.class);
            }
            if (muteDataSource != null)
                muteDataSource.setMute(mute);
        }
        return captureDevice;
    }

    /**
     * Creates a new <code>Player</code> for a specific <code>DataSource</code> so that it is played
     * back on the <code>MediaDevice</code> represented by this instance.
     *
     * @param dataSource the <code>DataSource</code> to create a new <code>Player</code> for
     * @return a new <code>Player</code> for the specified <code>dataSource</code>
     */
    protected Player createPlayer(DataSource dataSource)
    {
        Processor player = null;
        Throwable exception = null;

        try {
            player = getDevice().createPlayer(dataSource);
        } catch (Exception ex) {
            exception = ex;
        }
        if (exception != null) {
            Timber.e(exception, "Failed to create Player for %s", MediaStreamImpl.toString(dataSource));
        }
        else if (player != null) {
            /*
             * We cannot wait for the Player to get configured (e.g. with waitForState) because it
             * will stay in the Configuring state until it reads some media. In the case of a
             * ReceiveStream not sending media (e.g. abnormally stopped), it will leave us blocked.
             */
            player.addControllerListener(playerControllerListener);

            player.configure();
            Timber.log(TimberLog.FINER, "Created Player with hashCode %s for %s",
                    player.hashCode(), MediaStreamImpl.toString(dataSource));
        }
        return player;
    }

    /**
     * Initializes a new FMJ <code>Processor</code> which is to transcode {@link #captureDevice} into
     * the format of this instance.
     *
     * @return a new FMJ <code>Processor</code> which is to transcode <code>captureDevice</code> into the
     * format of this instance
     */
    protected Processor createProcessor()
    {
        DataSource captureDevice = getConnectedCaptureDevice();
        if (captureDevice != null) {
            Processor processor = null;
            Throwable exception = null;

            try {
                processor = Manager.createProcessor(captureDevice);
            } catch (IOException | NoProcessorException ioe) {
                exception = ioe;
            }

            if (exception != null)
                Timber.e(exception, "Failed to create Processor for %s", captureDevice);
            else {
                if (processorControllerListener == null) {
                    processorControllerListener = new ControllerListener()
                    {
                        /**
                         * Notifies this <code>ControllerListener</code> that the <code>Controller</code>
                         * which it is registered with has generated an event.
                         *
                         * @param event
                         *        the <code>ControllerEvent</code> specifying the <code>Controller</code>
                         *        which is the source of the event and the very type of the event
                         * @see ControllerListener#controllerUpdate(ControllerEvent)
                         */
                        public void controllerUpdate(ControllerEvent event)
                        {
                            processorControllerUpdate(event);
                        }
                    };
                }
                processor.addControllerListener(processorControllerListener);

                if (waitForState(processor, Processor.Configured)) {
                    this.processor = processor;
                    processorIsPrematurelyClosed = false;
                }
                else {
                    if (processorControllerListener != null)
                        processor.removeControllerListener(processorControllerListener);
                    this.processor = null;
                }
            }
        }
        return this.processor;
    }

    /**
     * Creates a <code>ContentDescriptor</code> to be set on a specific <code>Processor</code> of captured
     * media to be sent to the remote peer. Allows extenders to override. The default implementation
     * returns {@link ContentDescriptor#RAW_RTP}.
     *
     * @param processor the <code>Processor</code> of captured media to be sent to the remote peer which is to
     * have its <code>contentDescriptor</code> set to the returned <code>ContentDescriptor</code>
     * @return a <code>ContentDescriptor</code> to be set on the specified <code>processor</code> of
     * captured media to be sent to the remote peer
     */
    protected ContentDescriptor createProcessorContentDescriptor(Processor processor)
    {
        return (contentDescriptor == null) ? new ContentDescriptor(ContentDescriptor.RAW_RTP)
                : contentDescriptor;
    }

    /**
     * Initializes a <code>Renderer</code> instance which is to be utilized by a specific
     * <code>Player</code> in order to play back the media represented by a specific
     * <code>TrackControl</code>. Allows extenders to override and, optionally, perform additional
     * configuration of the returned <code>Renderer</code>.
     *
     * @param player the <code>Player</code> which is to utilize the initialized/returned <code>Renderer</code>
     * @param trackControl the <code>TrackControl</code> which represents the media to be played back (and,
     * technically, on which the initialized/returned <code>Renderer</code> is to be set)
     * @return the <code>Renderer</code> which is to be set on the specified <code>trackControl</code>. If
     * <code>null</code>, {@link TrackControl#setRenderer(Renderer)} is not invoked on the
     * specified <code>trackControl</code>.
     */
    protected Renderer createRenderer(Player player, TrackControl trackControl)
    {
        return getDevice().createRenderer();
    }

    /**
     * Makes sure {@link #captureDevice} is disconnected.
     */
    private void disconnectCaptureDevice()
    {
        if (captureDevice != null) {
            /*
             * As reported by Carlos Alexandre, stopping before disconnecting resolves a slow disconnect on Linux.
             */
            try {
                captureDevice.stop();
            } catch (IOException ioe) {
                /*
                 * We cannot do much about the exception because we're not really interested in the
                 * stopping but rather in calling DataSource#disconnect() anyway.
                 */
                Timber.e(ioe, "Failed to properly stop captureDevice %s", captureDevice);
            }
            captureDevice.disconnect();
            captureDeviceIsConnected = false;
        }
    }

    /**
     * Releases the resources allocated by the <code>Player</code>s of {@link #playbacks} in the course
     * of their execution and prepares them to be garbage collected.
     */
    private void disposePlayer()
    {
        Lock writeLock = playbacksLock.writeLock();
        writeLock.lock();
        try {
            for (Playback playback : playbacks) {
                if (playback.player != null) {
                    disposePlayer(playback.player);
                    playback.player = null;
                }
            }
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Releases the resources allocated by a specific <code>Player</code> in the course of its
     * execution
     * and prepares it to be garbage collected.
     *
     * @param player the <code>Player</code> to dispose of
     */
    protected void disposePlayer(Player player)
    {
        player.removeControllerListener(playerControllerListener);
        player.stop();
        player.deallocate();
        player.close();
    }

    /**
     * Finds the first <code>Format</code> instance in a specific list of <code>Format</code>s which
     * matches a specific <code>Format</code>. The implementation considers a pair of
     * <code>Format</code>s matching if they have the same encoding.
     *
     * @param formats the array of <code>Format</code>s to be searched for a match to the specified <code>format</code>
     * @param format the <code>Format</code> to search for a match in the specified <code>formats</code>
     * @return the first element of <code>formats</code> which matches <code>format</code> i.e. is of the same encoding
     */
    private static Format findFirstMatchingFormat(Format[] formats, Format format)
    {
        double formatSampleRate = (format instanceof AudioFormat) ?
                ((AudioFormat) format).getSampleRate() : Format.NOT_SPECIFIED;
        ParameterizedVideoFormat parameterizedVideoFormat = (format instanceof ParameterizedVideoFormat)
                ? (ParameterizedVideoFormat) format : null;

        for (Format match : formats) {
            if (match.isSameEncoding(format)) {
                /*
                 * The encoding alone is, of course, not enough. For example, AudioFormats may have
                 * different sample rates (i.e. clock rates as we call them in MediaFormat).
                 */
                if (match instanceof AudioFormat) {
                    if (formatSampleRate != Format.NOT_SPECIFIED) {
                        double matchSampleRate = ((AudioFormat) match).getSampleRate();

                        if ((matchSampleRate != Format.NOT_SPECIFIED)
                                && (matchSampleRate != formatSampleRate))
                            continue;
                    }
                }
                else if (match instanceof ParameterizedVideoFormat) {
                    if (!((ParameterizedVideoFormat) match).formatParametersMatch(format))
                        continue;
                }
                else if (parameterizedVideoFormat != null) {
                    if (!parameterizedVideoFormat.formatParametersMatch(match))
                        continue;
                }
                return match;
            }
        }
        return null;
    }

    /**
     * Gets the <code>DataSource</code> that this instance uses to read captured media from. If it does
     * not exist yet, it is created.
     *
     * @return the <code>DataSource</code> that this instance uses to read captured media from
     */
    public synchronized DataSource getCaptureDevice()
    {
        if (captureDevice == null)
            captureDevice = createCaptureDevice();
        return captureDevice;
    }

    /**
     * Gets {@link #captureDevice} in a connected state. If this instance is not connected to
     * <code>captureDevice</code> yet, first tries to connect to it. Returns <code>null</code> if this
     * instance fails to create <code>captureDevice</code> or to connect to it.
     *
     * @return {@link #captureDevice} in a connected state; <code>null</code> if this instance fails to
     * create <code>captureDevice</code> or to connect to it
     */
    protected DataSource getConnectedCaptureDevice()
    {
        DataSource captureDevice = getCaptureDevice();
        if ((captureDevice != null) && !captureDeviceIsConnected) {
            /*
             * Give this instance a chance to set up an optimized media codec chain by setting the
             * output Format on the input CaptureDevice.
             */
            try {
                if (this.format != null)
                    setCaptureDeviceFormat(captureDevice, this.format);
            } catch (Throwable t) {
                Timber.w(t, "Failed to setup an optimized media codec chain by setting the output Format on the input CaptureDevice");
            }
            Throwable exception = null;

            try {
                getDevice().connect(captureDevice);
            } catch (IOException ioex) {
                exception = ioex;
            }

            if (exception == null)
                captureDeviceIsConnected = true;
            else {
                Timber.e(exception, "Failed to connect to %s", MediaStreamImpl.toString(captureDevice));
                captureDevice = null;
            }
        }
        return captureDevice;
    }

    /**
     * Gets the <code>MediaDevice</code> associated with this instance and the work of a
     * <code>MediaStream</code> with which is represented by it.
     *
     * @return the <code>MediaDevice</code> associated with this instance and the work of a
     * <code>MediaStream</code> with which is represented by it
     */
    public AbstractMediaDevice getDevice()
    {
        return device;
    }

    /**
     * Gets the JMF <code>Format</code> in which this instance captures media.
     *
     * @return the JMF <code>Format</code> in which this instance captures media.
     */
    public Format getProcessorFormat()
    {
        Processor processor = getProcessor();
        if ((processor != null) && (this.processor == processor)
                && !processorIsPrematurelyClosed) {
            MediaType mediaType = getMediaType();

            for (TrackControl trackControl : processor.getTrackControls()) {
                if (!trackControl.isEnabled())
                    continue;

                Format jmfFormat = trackControl.getFormat();
                MediaType type = (jmfFormat instanceof VideoFormat) ? MediaType.VIDEO : MediaType.AUDIO;
                if (mediaType.equals(type))
                    return jmfFormat;
            }
        }
        return null;
    }

    /**
     * Gets the <code>MediaFormat</code> in which this instance captures media from its associated <code>MediaDevice</code>.
     *
     * @return the <code>MediaFormat</code> in which this instance captures media from its associated <code>MediaDevice</code>
     */
    public MediaFormatImpl<? extends Format> getFormat()
    {
        /*
         * If the Format of the processor is different than the format of this MediaDeviceSession,
         * we'll likely run into unexpected issues so debug whether there are such cases.
         */
        if (processor != null) {
            Format processorFormat = getProcessorFormat();
            Format format = (this.format == null) ? null : this.format.getFormat();
            boolean processorFormatMatchesFormat = (processorFormat == null)
                    ? (format == null) : processorFormat.matches(format);

            if (!processorFormatMatchesFormat) {
                Timber.d("processorFormat != format; processorFormat = `%s`; format = `%s'",
                        processorFormat, format);
            }
        }
        return format;
    }

    /**
     * Gets the <code>MediaType</code> of the media captured and played back by this instance. It is
     * the same as the <code>MediaType</code> of its associated <code>MediaDevice</code>.
     *
     * @return the <code>MediaType</code> of the media captured and played back by this instance as
     * reported by {@link MediaDevice#getMediaType()} of its associated <code>MediaDevice</code>
     */
    private MediaType getMediaType()
    {
        return getDevice().getMediaType();
    }

    /**
     * Gets the output <code>DataSource</code> of this instance which provides the captured (RTP) data
     * to be sent by <code>MediaStream</code> to <code>MediaStreamTarget</code>.
     *
     * @return the output <code>DataSource</code> of this instance which provides the captured (RTP)
     * data to be sent by <code>MediaStream</code> to <code>MediaStreamTarget</code>
     */
    public DataSource getOutputDataSource()
    {
        Processor processor = getProcessor();
        DataSource outputDataSource;
        if ((processor == null)
                || ((processor.getState() < Processor.Realized) && !waitForState(processor, Processor.Realized)))
            outputDataSource = null;
        else {
            outputDataSource = processor.getDataOutput();
            if (outputDataSource != null) {
                Timber.log(TimberLog.FINER, "Processor with hashCode %s provided %s",
                        processor.hashCode(), MediaStreamImpl.toString(outputDataSource));
            }

            /*
             * Whoever wants the outputDataSource, they expect it to be started in accord with the
             * previously-set direction.
             */
            startProcessorInAccordWithDirection(processor);
        }
        return outputDataSource;
    }

    /**
     * Gets the information related to the playback of a specific <code>DataSource</code> on the
     * <code>MediaDevice</code> represented by this <code>MediaDeviceSession</code>.
     *
     * @param dataSource the <code>DataSource</code> to get the information related to the playback of
     * @return the information related to the playback of the specified <code>DataSource</code> on the
     * <code>MediaDevice</code> represented by this <code>MediaDeviceSession</code>
     */
    private Playback getPlayback(DataSource dataSource)
    {
        Lock readLock = playbacksLock.readLock();
        readLock.lock();
        try {
            for (Playback playback : playbacks) {
                if (playback.dataSource == dataSource)
                    return playback;
            }
        } finally {
            readLock.unlock();
        }
        return null;
    }

    /**
     * Gets the information related to the playback of a specific <code>ReceiveStream</code> on the
     * <code>MediaDevice</code> represented by this <code>MediaDeviceSession</code>.
     *
     * @param receiveStream the <code>ReceiveStream</code> to get the information related to the playback of
     * @return the information related to the playback of the specified <code>ReceiveStream</code> on
     * the <code>MediaDevice</code> represented by this <code>MediaDeviceSession</code>
     */
    private Playback getPlayback(ReceiveStream receiveStream)
    {
        Lock readLock = playbacksLock.readLock();
        readLock.lock();
        try {
            for (Playback playback : playbacks) {
                if (playback.receiveStream == receiveStream)
                    return playback;
            }
        } finally {
            readLock.unlock();
        }
        return null;
    }

    /**
     * Gets the <code>Player</code> rendering the <code>ReceiveStream</code> with a specific SSRC.
     *
     * @param ssrc the SSRC of the <code>ReceiveStream</code> to get the rendering the <code>Player</code> of
     * @return the <code>Player</code> rendering the <code>ReceiveStream</code> with the specified <code>ssrc</code>
     */
    protected Player getPlayer(long ssrc)
    {
        Lock readLock = playbacksLock.readLock();
        readLock.lock();
        try {
            for (Playback playback : playbacks) {
                long playbackSSRC = 0xFFFFFFFFL & playback.receiveStream.getSSRC();
                if (playbackSSRC == ssrc)
                    return playback.player;
            }
        } finally {
            readLock.unlock();
        }
        return null;
    }

    /**
     * Gets the <code>Player</code>s rendering the <code>ReceiveStream</code>s of this instance on its
     * associated <code>MediaDevice</code>.
     *
     * @return the <code>Player</code>s rendering the <code>ReceiveStream</code>s of this instance on its
     * associated <code>MediaDevice</code>
     */
    protected List<Player> getPlayers()
    {
        Lock readLock = playbacksLock.readLock();
        List<Player> players;

        readLock.lock();
        try {
            players = new ArrayList<>(playbacks.size());
            for (Playback playback : playbacks) {
                if (playback.player != null)
                    players.add(playback.player);
            }
        } finally {
            readLock.unlock();
        }
        return players;
    }

    /**
     * Gets the JMF <code>Processor</code> which transcodes the <code>MediaDevice</code> of this instance
     * into the format of this instance. If the <code>Processor</code> in question does not exist, the
     * method will create it.
     * <p>
     * <b>Warning</b>: Because the method will unconditionally create the <code>Processor</code> if it
     * does not exist and because the creation of the <code>Processor</code> will connect to the
     * <code>CaptureDevice</code> of this instance, extreme care is to be taken when invoking the
     * method in order to ensure that the existence of the <code>Processor</code> is really in accord
     * with the rest of the state of this instance. Overall, the method is to be considered
     * private and is to not be invoked outside the <code>MediaDeviceSession</code> class.
     * </p>
     *
     * @return the JMF <code>Processor</code> which transcodes the <code>MediaDevice</code> of this
     * instance into the format of this instance
     */
    private Processor getProcessor()
    {
        if (processor == null)
            processor = createProcessor();
        return processor;
    }

    /**
     * Gets a list of the <code>ReceiveStream</code>s being played back on the <code>MediaDevice</code>
     * represented by this instance.
     *
     * @return a list of <code>ReceiveStream</code>s being played back on the <code>MediaDevice</code>
     * represented by this instance
     */
    public List<ReceiveStream> getReceiveStreams()
    {
        Lock readLock = playbacksLock.readLock();
        List<ReceiveStream> receiveStreams;

        readLock.lock();
        try {
            receiveStreams = new ArrayList<>(playbacks.size());
            for (Playback playback : playbacks) {
                if (playback.receiveStream != null)
                    receiveStreams.add(playback.receiveStream);
            }
        } finally {
            readLock.unlock();
        }
        return receiveStreams;
    }

    /**
     * Returns the list of SSRC identifiers that this device session is handling streams from. In
     * this case (i.e. the case of a device session handling a single remote party) we would rarely
     * (if ever) have more than a single SSRC identifier returned. However, we would also be using
     * the same method to query a device session operating over a mixer in which case we would have
     * the SSRC IDs of all parties currently contributing to the mixing.
     *
     * @return a <code>long[]</code> array of SSRC identifiers that this device session is handling streams from.
     */
    public long[] getRemoteSSRCList()
    {
        return ssrcList;
    }

    /**
     * Gets the <code>MediaDirection</code> in which this instance has been started. For example, a
     * <code>MediaDirection</code> which returns <code>true</code> for <code>allowsSending()</code> signals
     * that this instance is capturing media from its <code>MediaDevice</code>.
     *
     * @return the <code>MediaDirection</code> in which this instance has been started
     */
    public MediaDirection getStartedDirection()
    {
        return startedDirection;
    }

    /**
     * Gets a list of the <code>MediaFormat</code>s in which this instance is capable of capturing
     * media from its associated <code>MediaDevice</code>.
     *
     * @return a new list of <code>MediaFormat</code>s in which this instance is capable of capturing
     * media from its associated <code>MediaDevice</code>
     */
    public List<MediaFormat> getSupportedFormats()
    {
        Processor processor = getProcessor();
        Set<Format> supportedFormats = new HashSet<>();

        if ((processor != null) && (this.processor == processor)
                && !processorIsPrematurelyClosed) {
            MediaType mediaType = getMediaType();

            for (TrackControl trackControl : processor.getTrackControls()) {
                if (!trackControl.isEnabled())
                    continue;

                for (Format supportedFormat : trackControl.getSupportedFormats()) {
                    switch (mediaType) {
                        case AUDIO:
                            if (supportedFormat instanceof AudioFormat)
                                supportedFormats.add(supportedFormat);
                            break;
                        case VIDEO:
                            if (supportedFormat instanceof VideoFormat)
                                supportedFormats.add(supportedFormat);
                            break;
                        default:
                            // FMJ and LibJitsi handle audio and video only and it seems unlikely
                            // that support for any other types of media will be added here.
                            break;
                    }
                }
            }
        }

        List<MediaFormat> supportedMediaFormats = new ArrayList<>(supportedFormats.size());
        for (Format format : supportedFormats)
            supportedMediaFormats.add(MediaFormatImpl.createInstance(format));
        return supportedMediaFormats;
    }

    /**
     * Determines whether this <code>MediaDeviceSession</code> is set to output "silence" instead of
     * the actual media fed from its <code>CaptureDevice</code> .
     *
     * @return <code>true</code> if this <code>MediaDeviceSession</code> is set to output "silence" instead
     * of the actual media fed from its <code>CaptureDevice</code>; otherwise, <code>false</code>
     */
    public boolean isMute()
    {
        DataSource captureDevice = this.captureDevice;
        if (captureDevice == null)
            return mute;

        MuteDataSource muteDataSource = AbstractControls.queryInterface(captureDevice, MuteDataSource.class);
        return (muteDataSource != null) && muteDataSource.isMute();
    }

    /**
     * Notifies this <code>MediaDeviceSession</code> that a <code>DataSource</code> has been added for
     * playback on the represented <code>MediaDevice</code>.
     *
     * @param playbackDataSource the <code>DataSource</code> which has been added for playback on the represented
     * <code>MediaDevice</code>
     */
    protected void playbackDataSourceAdded(DataSource playbackDataSource)
    {
    }

    /**
     * Notifies this <code>MediaDeviceSession</code> that a <code>DataSource</code> has been removed from
     * playback on the represented <code>MediaDevice</code>.
     *
     * @param playbackDataSource the <code>DataSource</code> which has been removed from playback on the represented
     * <code>MediaDevice</code>
     */
    protected void playbackDataSourceRemoved(DataSource playbackDataSource)
    {
    }

    /**
     * Notifies this <code>MediaDeviceSession</code> that a <code>DataSource</code> has been changed on the
     * represented <code>MediaDevice</code>.
     *
     * @param playbackDataSource the <code>DataSource</code> which has been added for playback on the represented
     * <code>MediaDevice</code>
     */
    protected void playbackDataSourceUpdated(DataSource playbackDataSource)
    {
    }

    /**
     * Notifies this <code>MediaDeviceSession</code> that a <code>DataSource</code> has been changed on the
     * represented <code>MediaDevice</code>.
     *
     * @param playbackDataSource the <code>DataSource</code> which has been added for playback on the represented
     * <code>MediaDevice</code>
     */
    public void playbackDataSourceChanged(DataSource playbackDataSource)
    {
        playbackDataSourceUpdated(playbackDataSource);
    }

    /**
     * Notifies this instance that a specific <code>Player</code> of remote content has generated a
     * <code>ConfigureCompleteEvent</code>. Allows extenders to carry out additional processing on the <code>Player</code>.
     *
     * @param player the <code>Player</code> which is the source of a <code>ConfigureCompleteEvent</code>
     */
    protected void playerConfigureComplete(Processor player)
    {
        TrackControl[] tcs = player.getTrackControls();

        if ((tcs != null) && (tcs.length != 0)) {
            for (int i = 0; i < tcs.length; i++) {
                TrackControl tc = tcs[i];
                Renderer renderer = createRenderer(player, tc);

                if (renderer != null) {
                    try {
                        tc.setRenderer(renderer);
                    } catch (UnsupportedPlugInException upie) {
                        Timber.w(upie, "Failed to set %s renderer on track %s", renderer.getClass().getName(), i);
                    }
                }
            }
        }
    }

    /**
     * Gets notified about <code>ControllerEvent</code>s generated by a specific <code>Player</code> of remote content.
     * <p>
     * Extenders who choose to override are advised to override more specialized methods such as
     * {@link #playerConfigureComplete(Processor)} and {@link #playerRealizeComplete(Processor)}.
     * In any case, extenders overriding this method should call the super implementation.
     * </p>
     *
     * @param ev the <code>ControllerEvent</code> specifying the <code>Controller</code> which is the source of
     * the event and the very type of the event
     */
    protected void playerControllerUpdate(ControllerEvent ev)
    {
        if (ev instanceof ConfigureCompleteEvent) {
            Processor player = (Processor) ev.getSourceController();
            if (player != null) {
                playerConfigureComplete(player);

                /*
                 * To use the processor as a Player we must set its ContentDescriptor to null.
                 */
                try {
                    player.setContentDescriptor(null);
                } catch (NotConfiguredError nce) {
                    Timber.e(nce, "Failed to set ContentDescriptor to Player.");
                    return;
                }
                player.realize();
            }
        }
        else if (ev instanceof RealizeCompleteEvent) {
            Processor player = (Processor) ev.getSourceController();

            if (player != null) {
                playerRealizeComplete(player);
                player.start();
            }
        }
    }

    /**
     * Notifies this instance that a specific <code>Player</code> of remote content has generated a
     * <code>RealizeCompleteEvent</code>. Allows extenders to carry out additional processing on the <code>Player</code>.
     *
     * @param player the <code>Player</code> which is the source of a <code>RealizeCompleteEvent</code>
     */
    protected void playerRealizeComplete(Processor player)
    {
    }

    /**
     * Gets notified about <code>ControllerEvent</code>s generated by {@link #processor}.
     *
     * @param ev the <code>ControllerEvent</code> specifying the <code>Controller</code> which is the source of
     * the event and the very type of the event
     */
    protected void processorControllerUpdate(ControllerEvent ev)
    {
        if (ev instanceof ConfigureCompleteEvent) {
            Processor processor = (Processor) ev.getSourceController();
            if (processor != null) {
                try {
                    processor.setContentDescriptor(createProcessorContentDescriptor(processor));
                } catch (NotConfiguredError nce) {
                    Timber.e(nce, "Failed to set ContentDescriptor to Processor.");
                }
                if (format != null)
                    setProcessorFormat(processor, format);
            }
        }
        else if (ev instanceof ControllerClosedEvent) {
            // cmeng: unsupported hw codec will trigger this event
            if (ev instanceof ControllerErrorEvent) {
                String errMessage = ((ControllerErrorEvent) ev).getMessage();
                Timber.w("ControllerErrorEvent: %s", errMessage);
                aTalkApp.showToastMessage(errMessage);
            }
            else {
                Timber.d("ControllerClosedEvent: %s", ((ControllerClosedEvent) ev).getMessage());
            }

            Processor processor = (Processor) ev.getSourceController();
            if ((processor != null) && (this.processor == processor))
                processorIsPrematurelyClosed = true;
        }
        else if (ev instanceof RealizeCompleteEvent) {
            Processor processor = (Processor) ev.getSourceController();

            for (FormatParametersAwareCodec fpac : getAllTrackControls(FormatParametersAwareCodec.class, processor)) {
                Map<String, String> formatParameters = format == null ? null : format.getFormatParameters();
                if (formatParameters != null)
                    fpac.setFormatParameters(formatParameters);
            }
            for (AdvancedAttributesAwareCodec aaac : getAllTrackControls(
                    AdvancedAttributesAwareCodec.class, processor)) {
                Map<String, String> advanceAttrs = format == null ?
                        null : format.getAdvancedAttributes();
                if (advanceAttrs != null)
                    aaac.setAdvancedAttributes(advanceAttrs);
            }
        }
    }

    /**
     * Removes <code>ssrc</code> from the array of SSRC identifiers representing parties that this
     * <code>MediaDeviceSession</code> is currently receiving streams from.
     *
     * @param ssrc the SSRC identifier that we'd like to remove from the array of <code>ssrc</code>
     * identifiers stored by this session.
     */
    protected void removeSSRC(long ssrc)
    {
        // find the ssrc
        int index = -1;
        if ((ssrcList == null) || (ssrcList.length == 0)) {
            // list is already empty so there's nothing to do.
            return;
        }

        for (int i = 0; i < ssrcList.length; i++) {
            if (ssrcList[i] == ssrc) {
                index = i;
                break;
            }
        }
        if (index < 0 || index >= ssrcList.length) {
            // the ssrc we are trying to remove is not in the list so there's nothing to do.
            return;
        }

        // if we get here and the list has a single element this would mean we
        // simply need to empty it as the only element is the one we are removing
        if (ssrcList.length == 1) {
            setSsrcList(null);
            return;
        }

        long[] newSsrcList = new long[ssrcList.length - 1];

        System.arraycopy(ssrcList, 0, newSsrcList, 0, index);
        if (index < ssrcList.length - 1) {
            System.arraycopy(ssrcList, index + 1, newSsrcList, index, ssrcList.length - index - 1);
        }
        setSsrcList(newSsrcList);
    }

    /**
     * Notifies this instance that a specific <code>ReceiveStream</code> has been added to the list of
     * playbacks of <code>ReceiveStream</code>s and/or <code>DataSource</code>s performed by respective
     * <code>Player</code>s on the <code>MediaDevice</code> represented by this instance.
     *
     * @param receiveStream the <code>ReceiveStream</code> which has been added to the list of playbacks of
     * <code>ReceiveStream</code>s and/or <code>DataSource</code> s performed by respective
     * <code>Player</code>s on the <code>MediaDevice</code> represented by this instance
     */
    protected void receiveStreamAdded(ReceiveStream receiveStream)
    {
    }

    /**
     * Notifies this instance that a specific <code>ReceiveStream</code> has been removed from the list
     * of playbacks of <code>ReceiveStream</code>s and/or <code>DataSource</code>s performed by respective
     * <code>Player</code>s on the <code>MediaDevice</code> represented by this instance.
     *
     * @param receiveStream the <code>ReceiveStream</code> which has been removed from the list of playbacks of
     * <code>ReceiveStream</code>s and/or <code>DataSource</code>s performed by respective
     * <code>Player</code>s on the <code>MediaDevice</code> represented by this instance
     */
    protected void receiveStreamRemoved(ReceiveStream receiveStream)
    {
    }

    protected void setCaptureDeviceFormat(DataSource captureDevice, MediaFormatImpl<? extends Format> mediaFormat)
    {
        Format format = mediaFormat.getFormat();
        if (format instanceof AudioFormat) {
            AudioFormat audioFormat = (AudioFormat) format;
            int channels = audioFormat.getChannels();
            double sampleRate = OSUtils.IS_ANDROID ? audioFormat.getSampleRate() : Format.NOT_SPECIFIED;

            if ((channels != Format.NOT_SPECIFIED) || (sampleRate != Format.NOT_SPECIFIED)) {
                FormatControl formatControl = (FormatControl) captureDevice.getControl(FormatControl.class.getName());

                if (formatControl != null) {
                    Format[] supportedFormats = formatControl.getSupportedFormats();
                    if ((supportedFormats != null) && (supportedFormats.length != 0)) {
                        if (sampleRate != Format.NOT_SPECIFIED) {
                            /*
                             * As per RFC 3551.4.5.2, because of a mistake in RFC 1890 and for
                             * backward compatibility, G.722 should always be announced as 8000
                             * even though it is wideband.
                             */
                            String encoding = audioFormat.getEncoding();
                            if ((Constants.G722.equalsIgnoreCase(encoding)
                                    || Constants.G722_RTP.equalsIgnoreCase(encoding))
                                    && (sampleRate == 8000)) {
                                sampleRate = 16000;
                            }
                        }

                        Format supportedAudioFormat = null;
                        for (Format sf : supportedFormats) {
                            if (sf instanceof AudioFormat) {
                                AudioFormat saf = (AudioFormat) sf;
                                if ((Format.NOT_SPECIFIED != channels)
                                        && (saf.getChannels() != channels))
                                    continue;
                                if ((Format.NOT_SPECIFIED != sampleRate)
                                        && (saf.getSampleRate() != sampleRate))
                                    continue;
                                supportedAudioFormat = saf;
                                break;
                            }
                        }
                        if (supportedAudioFormat != null)
                            formatControl.setFormat(supportedAudioFormat);
                    }
                }
            }
        }
    }

    /**
     * Sets the <code>ContentDescriptor</code> which specifies the content type in which this
     * <code>MediaDeviceSession</code> is to output the media captured by its <code>MediaDevice</code>. The
     * default content type in which <code>MediaDeviceSession</code> outputs the media captured by its
     * <code>MediaDevice</code> is {@link ContentDescriptor#RAW_RTP}.
     *
     * @param contentDescriptor the <code>ContentDescriptor</code> which specifies the content type in which this
     * <code>MediaDeviceSession</code> is to output the media captured by its <code>MediaDevice</code>
     */
    public void setContentDescriptor(ContentDescriptor contentDescriptor)
    {
        if (contentDescriptor == null)
            throw new NullPointerException("contentDescriptor");
        this.contentDescriptor = contentDescriptor;
    }

    /**
     * Sets the <code>MediaFormat</code> in which this <code>MediaDeviceSession</code> outputs the media
     * captured by its <code>MediaDevice</code>.
     *
     * @param format the <code>MediaFormat</code> in which this <code>MediaDeviceSession</code> is to output the
     * media captured by its <code>MediaDevice</code>
     */
    public void setFormat(MediaFormat format)
    {
        if (!getMediaType().equals(format.getMediaType()))
            throw new IllegalArgumentException("format");

        /*
         * We need javax.media.Format and we know how to convert MediaFormat to it only for
         * MediaFormatImpl so assert early.
         */
        @SuppressWarnings("unchecked")
        MediaFormatImpl<? extends Format> mediaFormatImpl = (MediaFormatImpl<? extends Format>) format;

        this.format = mediaFormatImpl;
        Timber.log(TimberLog.FINER, "Set format %s on %s %s", this.format, getClass().getSimpleName(), hashCode());

        /*
         * If the processor is after Configured, setting a different format will silently fail.
         * Recreate the processor in order to be able to set the different format.
         */
        if (processor != null) {
            int processorState = processor.getState();

            if (processorState == Processor.Configured)
                setProcessorFormat(processor, this.format);
            else if (processorIsPrematurelyClosed
                    || ((processorState > Processor.Configured)
                    && !this.format.getFormat().equals(getProcessorFormat()))
                    || outputSizeChanged) {
                outputSizeChanged = false;
                setProcessor(null);
            }
        }
    }

    /**
     * Sets the <code>MediaFormatImpl</code> in which a specific <code>Processor</code> producing media to
     * be streamed to the remote peer is to output.
     *
     * @param processor the <code>Processor</code> to set the output <code>MediaFormatImpl</code> of
     * @param mediaFormat the <code>MediaFormatImpl</code> to set on <code>processor</code>
     */
    protected void setProcessorFormat(Processor processor, MediaFormatImpl<? extends Format> mediaFormat)
    {
        TrackControl[] trackControls = processor.getTrackControls();
        MediaType mediaType = getMediaType();
        Format format = mediaFormat.getFormat();

        for (int trackIndex = 0; trackIndex < trackControls.length; trackIndex++) {
            TrackControl trackControl = trackControls[trackIndex];
            if (!trackControl.isEnabled())
                continue;

            Format[] supportedFormats = trackControl.getSupportedFormats();
            if ((supportedFormats == null) || (supportedFormats.length < 1)) {
                trackControl.setEnabled(false);
                continue;
            }

            Format supportedFormat = null;
            switch (mediaType) {
                case AUDIO:
                    if (supportedFormats[0] instanceof AudioFormat) {
                        supportedFormat = findFirstMatchingFormat(supportedFormats, format);

                        /*
                         * We've failed to find a supported format so try to use whatever we've
                         * been told and, if it fails, the caller will at least know why.
                         */
                        if (supportedFormat == null)
                            supportedFormat = format;
                    }
                    break;
                case VIDEO:
                    if (supportedFormats[0] instanceof VideoFormat) {
                        supportedFormat = findFirstMatchingFormat(supportedFormats, format);

                        /*
                         * We've failed to find a supported format so try to use whatever we've
                         * been told and, if it fails, the caller will at least know why.
                         */
                        if (supportedFormat == null)
                            supportedFormat = format;
                        if (supportedFormat != null)
                            supportedFormat = assertSize((VideoFormat) supportedFormat);
                    }
                    break;
                default:
                    // FMJ and LibJitsi handle audio and video only and it seems unlikely
                    // that support for any other types of media will be added here.
                    break;
            }

            if (supportedFormat == null)
                trackControl.setEnabled(false);
            else if (!supportedFormat.equals(trackControl.getFormat())) {
                Format setFormat = setProcessorFormat(trackControl, mediaFormat, supportedFormat);

                if (setFormat == null)
                    Timber.e("Failed to set format of track %s to %s. Processor is in state %s",
                            trackIndex, supportedFormat, processor.getState());
                else if (setFormat != supportedFormat)
                    Timber.w("Failed to change format of track %s from %s to %s. Processor is in state %s",
                            trackIndex, setFormat, supportedFormat, processor.getState());
                else
                    Timber.log(TimberLog.FINER, "Set format of track %s to %s", trackIndex, setFormat);
            }
        }
    }

    /**
     * Sets the <code>MediaFormatImpl</code> of a specific <code>TrackControl</code> of the
     * <code>Processor</code> which produces the media to be streamed by this
     * <code>MediaDeviceSession</code> to the remote peer. Allows extenders to override the set procedure
     * and to detect when the JMF <code>Format</code> of the specified <code>TrackControl</code> changes.
     *
     * @param trackControl the <code>TrackControl</code> to set the JMF <code>Format</code> of
     * @param mediaFormat the <code>MediaFormatImpl</code> to be set on the specified <code>TrackControl</code>. Though
     * <code>mediaFormat</code> encapsulates a JMF <code>Format</code>, <code>format</code> is to be set
     * on the specified <code>trackControl</code> because it may be more specific. In any case,
     * the two JMF <code>Format</code>s match. The <code>MediaFormatImpl</code> is provided anyway
     * because it carries additional information such as format parameters.
     * @param format the JMF <code>Format</code> to be set on the specified <code>TrackControl</code>. Though
     * <code>mediaFormat</code> encapsulates a JMF <code>Format</code>, the specified <code>format</code>
     * is to be set on the specified <code>trackControl</code> because it may be more specific
     * than the JMF <code>Format</code> of the <code>mediaFormat</code>
     * @return the JMF <code>Format</code> set on <code>TrackControl</code> after the attempt to set the
     * specified <code>format</code> or <code>null</code> if the specified <code>format</code> was found
     * to be incompatible with <code>trackControl</code>
     */
    protected Format setProcessorFormat(TrackControl trackControl,
            MediaFormatImpl<? extends Format> mediaFormat, Format format)
    {
        return trackControl.setFormat(format);
    }

    /**
     * Sets the indicator which determines whether this <code>MediaDeviceSession</code> is set to
     * output "silence" instead of the actual media fed from its <code>CaptureDevice</code>.
     *
     * @param mute <code>true</code> to set this <code>MediaDeviceSession</code> to output "silence" instead of
     * the actual media fed from its <code>CaptureDevice</code>; otherwise, <code>false</code>
     */
    public void setMute(boolean mute)
    {
        if (this.mute != mute) {
            this.mute = mute;
            MuteDataSource muteDataSource = AbstractControls.queryInterface(captureDevice, MuteDataSource.class);
            if (muteDataSource != null)
                muteDataSource.setMute(this.mute);
        }
    }

    /**
     * Adds a new inband DTMF tone to send.
     *
     * @param tone the DTMF tone to send.
     */
    public void addDTMF(DTMFInbandTone tone)
    {
        InbandDTMFDataSource inbandDTMFDataSource
                = AbstractControls.queryInterface(captureDevice, InbandDTMFDataSource.class);
        if (inbandDTMFDataSource != null)
            inbandDTMFDataSource.addDTMF(tone);
    }

    /**
     * Adds a specific <code>DataSource</code> to the list of playbacks of <code>ReceiveStream</code>s
     * and/or <code>DataSource</code>s performed by respective <code>Player</code>s on the
     * <code>MediaDevice</code> represented by this instance.
     *
     * @param playbackDataSource the <code>DataSource</code> which to be added to the list of playbacks of
     * <code>ReceiveStream</code>s and/or <code>DataSource</code>s performed by respective
     * <code>Player</code>s on the <code>MediaDevice</code> represented by this instance
     */
    public void addPlaybackDataSource(DataSource playbackDataSource)
    {
        Lock writeLock = playbacksLock.writeLock();
        Lock readLock = playbacksLock.readLock();
        boolean added = false;

        writeLock.lock();
        try {
            Playback playback = getPlayback(playbackDataSource);

            if (playback == null) {
                if (playbackDataSource instanceof ReceiveStreamPushBufferDataSource) {
                    ReceiveStream receiveStream
                            = ((ReceiveStreamPushBufferDataSource) playbackDataSource).getReceiveStream();
                    playback = getPlayback(receiveStream);
                }
                if (playback == null) {
                    playback = new Playback(playbackDataSource);
                    playbacks.add(playback);
                }
                else
                    playback.dataSource = playbackDataSource;

                playback.player = createPlayer(playbackDataSource);
                readLock.lock();
                added = true;
            }
        } finally {
            writeLock.unlock();
        }
        if (added) {
            try {
                playbackDataSourceAdded(playbackDataSource);
            } finally {
                readLock.unlock();
            }
        }
    }

    /**
     * Removes a specific <code>DataSource</code> from the list of playbacks of <code>ReceiveStream</code>s
     * and/or <code>DataSource</code>s performed by respective <code>Player</code>s on the
     * <code>MediaDevice</code> represented by this instance.
     *
     * @param playbackDataSource the <code>DataSource</code> which to be removed from the list of playbacks of
     * <code>ReceiveStream</code>s and/or <code>DataSource</code>s performed by respective
     * <code>Player</code>s on the <code>MediaDevice</code> represented by this instance
     */
    public void removePlaybackDataSource(DataSource playbackDataSource)
    {
        Lock writeLock = playbacksLock.writeLock();
        Lock readLock = playbacksLock.readLock();
        boolean removed = false;

        writeLock.lock();
        try {
            Playback playback = getPlayback(playbackDataSource);

            if (playback != null) {
                if (playback.player != null) {
                    disposePlayer(playback.player);
                    playback.player = null;
                }

                playback.dataSource = null;
                if (playback.receiveStream == null)
                    playbacks.remove(playback);

                readLock.lock();
                removed = true;
            }
        } finally {
            writeLock.unlock();
        }
        if (removed) {
            try {
                playbackDataSourceRemoved(playbackDataSource);
            } finally {
                readLock.unlock();
            }
        }
    }

    /**
     * Sets the JMF <code>Processor</code> which is to transcode {@link #captureDevice} into the format
     * of this instance.
     *
     * @param processor the JMF <code>Processor</code> which is to transcode {@link #captureDevice} into the
     * format of this instance
     */
    private void setProcessor(Processor processor)
    {
        if (this.processor != processor) {
            closeProcessor();
            this.processor = processor;

            /*
             * Since the processor has changed, its output DataSource known to the public has also changed.
             */
            firePropertyChange(OUTPUT_DATA_SOURCE, null, null);
        }
    }

    /**
     * Adds a specific <code>ReceiveStream</code> to the list of playbacks of <code>ReceiveStream</code>s
     * and/or <code>DataSource</code>s performed by respective <code>Player</code>s on the
     * <code>MediaDevice</code> represented by this instance.
     *
     * @param receiveStream the <code>ReceiveStream</code> which to be added to the list of playbacks of
     * <code>ReceiveStream</code>s and/or <code>DataSource</code>s performed by respective
     * <code>Player</code>s on the <code>MediaDevice</code> represented by this instance
     */
    public void addReceiveStream(ReceiveStream receiveStream)
    {
        Lock writeLock = playbacksLock.writeLock();
        Lock readLock = playbacksLock.readLock();
        boolean added = false;

        writeLock.lock();
        try {
            if (getPlayback(receiveStream) == null) {
                playbacks.add(new Playback(receiveStream));
                addSSRC(0xFFFFFFFFL & receiveStream.getSSRC());

                // playbackDataSource
                DataSource receiveStreamDataSource = receiveStream.getDataSource();
                if (receiveStreamDataSource != null) {
                    if (receiveStreamDataSource instanceof PushBufferDataSource) {
                        receiveStreamDataSource = new ReceiveStreamPushBufferDataSource(
                                receiveStream, (PushBufferDataSource) receiveStreamDataSource,
                                true);
                    }
                    else {
                        Timber.w("Adding ReceiveStream with DataSource not of type PushBufferDataSource but "
                                + "%s which may prevent the ReceiveStream from properly transferring to another"
                                + " MediaDevice if such a need arises.", receiveStreamDataSource.getClass().getSimpleName());
                    }
                    addPlaybackDataSource(receiveStreamDataSource);
                }
                readLock.lock();
                added = true;
            }
        } finally {
            writeLock.unlock();
        }
        if (added) {
            try {
                receiveStreamAdded(receiveStream);
            } finally {
                readLock.unlock();
            }
        }
    }

    /**
     * Removes a specific <code>ReceiveStream</code> from the list of playbacks of
     * <code>ReceiveStream</code>s and/or <code>DataSource</code>s performed by respective <code>Player</code>s
     * on the <code>MediaDevice</code> represented by this instance.
     *
     * @param receiveStream the <code>ReceiveStream</code> which to be removed from the list of playbacks of
     * <code>ReceiveStream</code>s and/or <code>DataSource</code>s performed by respective
     * <code>Player</code>s on the <code>MediaDevice</code> represented by this instance
     */
    public void removeReceiveStream(ReceiveStream receiveStream)
    {
        Lock writeLock = playbacksLock.writeLock();
        Lock readLock = playbacksLock.readLock();
        boolean removed = false;

        writeLock.lock();
        try {
            Playback playback = getPlayback(receiveStream);

            if (playback != null) {
                removeSSRC(0xFFFFFFFFL & receiveStream.getSSRC());
                if (playback.dataSource != null)
                    removePlaybackDataSource(playback.dataSource);

                if (playback.dataSource != null) {
                    Timber.w("Removing ReceiveStream with an associated DataSource.");
                }
                playbacks.remove(playback);

                readLock.lock();
                removed = true;
            }
        } finally {
            writeLock.unlock();
        }
        if (removed) {
            try {
                receiveStreamRemoved(receiveStream);
            } finally {
                readLock.unlock();
            }
        }
    }

    /**
     * Sets the list of SSRC identifiers that this device stores to <code>newSsrcList</code> and
     * fires a <code>PropertyChangeEvent</code> for the <code>SSRC_LIST</code> property.
     *
     * @param newSsrcList that SSRC array that we'd like to replace the existing SSRC list with.
     */
    private void setSsrcList(long[] newSsrcList)
    {
        // use getRemoteSSRCList() instead of direct access to ssrcList as the extender may
        // override it
        long[] oldSsrcList = getRemoteSSRCList();

        ssrcList = newSsrcList;
        firePropertyChange(SSRC_LIST, oldSsrcList, getRemoteSSRCList());
    }

    /**
     * Starts the processing of media in this instance in a specific direction.
     *
     * @param direction a <code>MediaDirection</code> value which represents the direction of the processing of
     * media to be started. For example, {@link MediaDirection#SENDRECV} to start both
     * capture and playback of media in this instance or {@link MediaDirection#SENDONLY} to
     * only start the capture of media in this instance
     */
    public void start(MediaDirection direction)
    {
        if (direction == null)
            throw new NullPointerException("direction");

        MediaDirection oldValue = startedDirection;
        startedDirection = startedDirection.or(direction);
        if (!oldValue.equals(startedDirection))
            startedDirectionChanged(oldValue, startedDirection);
    }

    /**
     * Notifies this instance that the value of its <code>startedDirection</code> property has changed
     * from a specific <code>oldValue</code> to a specific <code>newValue</code>. Allows extenders to
     * override and perform additional processing of the change. Overriding implementations must
     * call this implementation in order to ensure the proper execution of this <code>MediaDeviceSession</code>.
     *
     * @param oldValue the <code>MediaDirection</code> which used to be the value of the
     * <code>startedDirection</code> property of this instance
     * @param newValue the <code>MediaDirection</code> which is the value of the <code>startedDirection</code>
     * property of this instance
     */
    protected void startedDirectionChanged(MediaDirection oldValue, MediaDirection newValue)
    {
        if (newValue.allowsSending()) {
            Processor processor = getProcessor();
            if (processor != null)
                startProcessorInAccordWithDirection(processor);
        }
        else if ((processor != null) && (processor.getState() > Processor.Configured)) {
            processor.stop();
            Timber.log(TimberLog.FINER, "Stopped Processor with hashCode %s", processor.hashCode());
        }
    }

    /**
     * Starts a specific <code>Processor</code> if this <code>MediaDeviceSession</code> has been started
     * and
     * the specified <code>Processor</code> is not started.
     *
     * @param processor the <code>Processor</code> to start
     */
    protected void startProcessorInAccordWithDirection(Processor processor)
    {
        if (startedDirection.allowsSending() && (processor.getState() != Processor.Started)) {
            processor.start();
            Timber.log(TimberLog.FINER, "Started Processor with hashCode %s", processor.hashCode());
        }
    }

    /**
     * Stops the processing of media in this instance in a specific direction.
     *
     * @param direction a <code>MediaDirection</code> value which represents the direction of the processing of
     * media to be stopped. For example, {@link MediaDirection#SENDRECV} to stop both capture
     * and playback of media in this instance or {@link MediaDirection#SENDONLY} to only stop
     * the capture of media in this instance
     */
    public void stop(MediaDirection direction)
    {
        if (direction == null)
            throw new NullPointerException("direction");

        MediaDirection oldValue = startedDirection;
        switch (startedDirection) {
            case SENDRECV:
                if (direction.equals(startedDirection))
                    startedDirection = MediaDirection.INACTIVE;
                else if (direction.allowsReceiving())
                    startedDirection = MediaDirection.SENDONLY;
                else if (direction.allowsSending())
                    startedDirection = MediaDirection.RECVONLY;
                break;
            case SENDONLY:
                if (direction.allowsSending())
                    startedDirection = MediaDirection.INACTIVE;
                break;
            case RECVONLY:
                if (direction.allowsReceiving())
                    startedDirection = MediaDirection.INACTIVE;
                break;
            case INACTIVE:
                /*
                 * This MediaDeviceSession is already inactive so there's nothing to stop.
                 */
                break;
            default:
                throw new IllegalArgumentException("direction");
        }
        if (!oldValue.equals(startedDirection))
            startedDirectionChanged(oldValue, startedDirection);
    }

    /**
     * Waits for the specified JMF <code>Processor</code> to enter the specified <code>state</code> and
     * returns <code>true</code> if <code>processor</code> has successfully entered <code>state</code> or
     * <code>false</code> if <code>process</code> has failed to enter <code>state</code>.
     *
     * @param processor the JMF <code>Processor</code> to wait on
     * @param state the state as defined by the respective <code>Processor</code> state constants to wait
     * <code>processor</code> to enter
     * @return <code>true</code> if <code>processor</code> has successfully entered <code>state</code>; otherwise, <code>false</code>
     */
    private static boolean waitForState(Processor processor, int state)
    {
        return new ProcessorUtility().waitForState(processor, state);
    }

    /**
     * Copies the playback part of a specific <code>MediaDeviceSession</code> into this instance.
     *
     * @param deviceSession the <code>MediaDeviceSession</code> to copy the playback part of into this instance
     */
    public void copyPlayback(MediaDeviceSession deviceSession)
    {
        if (deviceSession.disposePlayerOnClose) {
            Timber.e("Cannot copy playback if MediaDeviceSession has closed it");
        }
        else {
            /*
             * TODO Technically, we should be synchronizing the (read and write) accesses to the
             * playbacks fields. In practice, we are not doing it because it likely was the easiest
             * to not bother with it at the time of its writing.
             */
            playbacks.addAll(deviceSession.playbacks);
            setSsrcList(deviceSession.ssrcList);
        }
    }

    /**
     * Represents the information related to the playback of a <code>DataSource</code> on the
     * <code>MediaDevice</code> represented by a <code>MediaDeviceSession</code>. The <code>DataSource</code>
     * may have an associated <code>ReceiveStream</code>.
     */
    private static class Playback
    {
        /**
         * The <code>DataSource</code> the information related to the playback of which is represented
         * by this instance and which is associated with {@link #receiveStream}.
         */
        public DataSource dataSource;

        /**
         * The <code>ReceiveStream</code> the information related to the playback of which is
         * represented by this instance and which is associated with {@link #dataSource}.
         */
        public ReceiveStream receiveStream;

        /**
         * The <code>Player</code> which performs the actual playback.
         */
        public Player player;

        /**
         * Initializes a new <code>Playback</code> instance which is to represent the information
         * related to the playback of a specific <code>DataSource</code>.
         *
         * @param dataSource the <code>DataSource</code> the information related to the playback of which is to be
         * represented by the new instance
         */
        public Playback(DataSource dataSource)
        {
            this.dataSource = dataSource;
        }

        /**
         * Initializes a new <code>Playback</code> instance which is to represent the information
         * related to the playback of a specific <code>ReceiveStream</code>.
         *
         * @param receiveStream the <code>ReceiveStream</code> the information related to the playback of which is to
         * be represented by the new instance
         */
        public Playback(ReceiveStream receiveStream)
        {
            this.receiveStream = receiveStream;
        }

    }

    /**
     * Returns the <code>TranscodingDataSource</code> associated with <code>receiveStream</code>.
     *
     * @param receiveStream the <code>ReceiveStream</code> to use
     * @return the <code>TranscodingDataSource</code> associated with <code>receiveStream</code>.
     */
    public TranscodingDataSource getTranscodingDataSource(ReceiveStream receiveStream)
    {
        TranscodingDataSource transcodingDataSource = null;
        if (device instanceof AudioMixerMediaDevice) {
            transcodingDataSource
                    = ((AudioMixerMediaDevice) device).getTranscodingDataSource(receiveStream.getDataSource());
        }
        return transcodingDataSource;
    }

    /**
     * Searches for controls of type <code>controlType</code> in the <code>TrackControl</code>s of the
     * <code>Processor</code> used to transcode the <code>MediaDevice</code> of this instance into the
     * format of this instance. Returns a <code>Set</code> of instances of class <code>controlType</code>,
     * always non-null.
     *
     * @param controlType the name of the class to search for.
     * @return A non-null <code>Set</code> of all <code>controlType</code>s found.
     */
    public <T> Set<T> getEncoderControls(Class<T> controlType)
    {
        return getAllTrackControls(controlType, this.processor);
    }

    /**
     * Searches for controls of type <code>controlType</code> in the <code>TrackControl</code>s of the
     * <code>Processor</code> used to decode <code>receiveStream</code>. Returns a <code>Set</code> of
     * instances of class <code>controlType</code>, always non-null.
     *
     * @param receiveStream the <code>ReceiveStream</code> whose <code>Processor</code>'s <code>TrackControl</code>s are to be
     * searched.
     * @param controlType the name of the class to search for.
     * @return A non-null <code>Set</code> of all <code>controlType</code>s found.
     */
    public <T> Set<T> getDecoderControls(ReceiveStream receiveStream, Class<T> controlType)
    {
        TranscodingDataSource transcodingDataSource = getTranscodingDataSource(receiveStream);
        if (transcodingDataSource == null)
            return Collections.emptySet();
        else {
            return getAllTrackControls(controlType, transcodingDataSource.getTranscodingProcessor());
        }
    }

    /**
     * Returns the <code>Set</code> of controls of type <code>controlType</code>, which are controls for
     * some of <code>processor</code>'s <code>TrackControl</code>s.
     *
     * @param controlType the name of the class to search for.
     * @param processor the <code>Processor</code> whose <code>TrackControls</code>s will be searched.
     * @return A non-null <code>Set</code> of all <code>controlType</code>s found.
     */
    private <T> Set<T> getAllTrackControls(Class<T> controlType, Processor processor)
    {
        Set<T> controls = null;
        if ((processor != null) && (processor.getState() >= Processor.Realized)) {
            TrackControl[] trackControls = processor.getTrackControls();

            if ((trackControls != null) && (trackControls.length != 0)) {
                String className = controlType.getName();

                for (TrackControl trackControl : trackControls) {
                    Object o = trackControl.getControl(className);

                    if (controlType.isInstance(o)) {
                        @SuppressWarnings("unchecked")
                        T t = (T) o;
                        if (controls == null)
                            controls = new HashSet<T>();
                        controls.add(t);
                    }
                }
            }
        }
        if (controls == null)
            controls = Collections.emptySet();
        return controls;
    }

    /**
     * Updates the value of <code>useTranslator</code>.
     *
     * @param useTranslator whether this device session is used by a <code>MediaStream</code> that is having a translator.
     */
    public void setUseTranslator(boolean useTranslator)
    {
        this.useTranslator = useTranslator;
    }
}
