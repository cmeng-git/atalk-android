/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.conference;

import org.atalk.impl.timberlog.TimberLog;
import org.atalk.impl.neomedia.MediaStreamImpl;
import org.atalk.impl.neomedia.control.ReadOnlyBufferControlDelegate;
import org.atalk.impl.neomedia.control.ReadOnlyFormatControlDelegate;
import org.atalk.impl.neomedia.device.MediaDeviceImpl;
import org.atalk.impl.neomedia.device.ReceiveStreamPushBufferDataSource;
import org.atalk.impl.neomedia.protocol.BufferStreamAdapter;
import org.atalk.impl.neomedia.protocol.CachingPushBufferStream;
import org.atalk.impl.neomedia.protocol.PullBufferStreamAdapter;
import org.atalk.impl.neomedia.protocol.PushBufferDataSourceAdapter;
import org.atalk.impl.neomedia.protocol.PushBufferStreamAdapter;
import org.atalk.impl.neomedia.protocol.TranscodingDataSource;
import org.atalk.util.OSUtils;

import java.io.IOException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import javax.media.Buffer;
import javax.media.CaptureDeviceInfo;
import javax.media.Controls;
import javax.media.Format;
import javax.media.Time;
import javax.media.control.BufferControl;
import javax.media.control.FormatControl;
import javax.media.format.AudioFormat;
import javax.media.protocol.CaptureDevice;
import javax.media.protocol.ContentDescriptor;
import javax.media.protocol.DataSource;
import javax.media.protocol.PullBufferDataSource;
import javax.media.protocol.PullBufferStream;
import javax.media.protocol.PullDataSource;
import javax.media.protocol.PullSourceStream;
import javax.media.protocol.PushBufferStream;
import javax.media.protocol.PushDataSource;
import javax.media.protocol.PushSourceStream;
import javax.media.protocol.SourceStream;

import timber.log.Timber;

/**
 * Represents an audio mixer which manages the mixing of multiple audio streams i.e. it is able to
 * output a single audio stream which contains the audio of multiple input audio streams.
 * <p>
 * The input audio streams are provided to the <code>AudioMixer</code> through
 * {@link #addInDataSource(DataSource)} in the form of input <code>DataSource</code> s giving access to
 * one or more input <code>SourceStreams</code>.
 * </p>
 * <p>
 * The output audio stream representing the mix of the multiple input audio streams is provided by
 * the <code>AudioMixer</code> in the form of a <code>AudioMixingPushBufferDataSource</code> giving access
 * to a <code>AudioMixingPushBufferStream</code>. Such an output is obtained through
 * {@link #createOutDataSource()}. The <code>AudioMixer</code> is able to provide multiple output audio
 * streams at one and the same time, though, each of them containing the mix of a subset of the
 * input audio streams.
 * </p>
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public class AudioMixer
{
    /**
     * The default output <code>AudioFormat</code> in which <code>AudioMixer</code>,
     * <code>AudioMixingPushBufferDataSource</code> and <code>AudioMixingPushBufferStream</code> output
     * audio.
     */
    private static final AudioFormat DEFAULT_OUTPUT_FORMAT = new AudioFormat(
            AudioFormat.LINEAR,
            8000,
            16,
            1,
            AudioFormat.LITTLE_ENDIAN,
            AudioFormat.SIGNED);

    // cmeng - added
    private static final AudioFormat PREFFERED_OUTPUT_FORMAT = new AudioFormat(
            AudioFormat.LINEAR,
            22050,
            16,
            1,
            AudioFormat.LITTLE_ENDIAN,
            AudioFormat.SIGNED);

    /**
     * Gets the <code>Format</code> in which a specific <code>DataSource</code> provides stream data.
     *
     * @param dataSource the <code>DataSource</code> for which the <code>Format</code> in which it provides stream data
     * is to be determined
     * @return the <code>Format</code> in which the specified <code>dataSource</code> provides stream data
     * if it was determined; otherwise, <code>null</code>
     */
    private static Format getFormat(DataSource dataSource)
    {
        FormatControl formatControl
                = (FormatControl) dataSource.getControl(FormatControl.class.getName());

        return (formatControl == null) ? null : formatControl.getFormat();
    }

    /**
     * Gets the <code>Format</code> in which a specific <code>SourceStream</code> provides data.
     *
     * @param stream the <code>SourceStream</code> for which the <code>Format</code> in which it provides data is
     * to be determined
     * @return the <code>Format</code> in which the specified <code>SourceStream</code> provides data if it
     * was determined; otherwise, <code>null</code>
     */
    private static Format getFormat(SourceStream stream)
    {
        if (stream instanceof PushBufferStream)
            return ((PushBufferStream) stream).getFormat();
        if (stream instanceof PullBufferStream)
            return ((PullBufferStream) stream).getFormat();
        return null;
    }

    /**
     * The <code>BufferControl</code> of this instance and, respectively, its
     * <code>AudioMixingPushBufferDataSource</code>s.
     */
    private BufferControl bufferControl;

    /**
     * The <code>CaptureDevice</code> capabilities provided by the
     * <code>AudioMixingPushBufferDataSource</code>s created by this <code>AudioMixer</code> . JMF's
     * <code>Manager.createMergingDataSource(DataSource[])</code> requires the interface implementation
     * for audio if it is implemented for video and it is indeed the case for our use case of
     * <code>AudioMixingPushBufferDataSource</code>.
     */
    protected final CaptureDevice captureDevice;

    /**
     * The number of output <code>AudioMixingPushBufferDataSource</code>s reading from this
     * <code>AudioMixer</code> which are connected. When the value is greater than zero, this
     * <code>AudioMixer</code> is connected to the input <code>DataSource</code>s it manages.
     */
    private int connected;

    /**
     * The collection of input <code>DataSource</code>s this instance reads audio data from.
     */
    private final List<InDataSourceDesc> inDataSources = new ArrayList<>();

    /**
     * The <code>AudioMixingPushBufferDataSource</code> which contains the mix of
     * <code>inDataSources</code> excluding <code>captureDevice</code> and is thus meant for playback on
     * the local peer in a call.
     */
    private final AudioMixingPushBufferDataSource localOutDataSource;

    /**
     * The output <code>AudioMixerPushBufferStream</code> through which this instance pushes audio
     * sample data to <code>AudioMixingPushBufferStream</code> s to be mixed.
     */
    private AudioMixerPushBufferStream outStream;

    /**
     * The number of output <code>AudioMixingPushBufferDataSource</code>s reading from this
     * <code>AudioMixer</code> which are started. When the value is greater than zero, this
     * <code>AudioMixer</code> is started and so are the input <code>DataSource</code>s it manages.
     */
    private int started;

    /**
     * The greatest generation with which {@link #start(AudioMixerPushBufferStream, long)} or
     * {@link #stop(AudioMixerPushBufferStream, long)} has been invoked.
     */
    private long startedGeneration;

    /**
     * Initializes a new <code>AudioMixer</code> instance. Because JMF's
     * <code>Manager.createMergingDataSource(DataSource[])</code> requires the implementation of
     * <code>CaptureDevice</code> for audio if it is implemented for video and it is indeed the cause
     * for our use case of <code>AudioMixingPushBufferDataSource</code>, the new <code>AudioMixer</code>
     * instance provides specified <code>CaptureDevice</code> capabilities to the
     * <code>AudioMixingPushBufferDataSource</code>s it creates. The specified <code>CaptureDevice</code> is
     * also added as the first input <code>DataSource</code> of the new instance.
     *
     * @param captureDevice the <code>CaptureDevice</code> capabilities to be provided to the
     * <code>AudioMixingPushBufferDataSource</code>s created by the new instance and its first
     * input <code>DataSource</code>
     */
    public AudioMixer(CaptureDevice captureDevice)
    {
        /*
         * AudioMixer provides PushBufferDataSources so it needs a way to push them. It does the
         * pushing by using the pushes of its CaptureDevice i.e. it has to be a
         * PushBufferDataSource.
         */
        if (captureDevice instanceof PullBufferDataSource) {
            captureDevice = new PushBufferDataSourceAdapter((PullBufferDataSource) captureDevice);
        }

        // Try to enable tracing on captureDevice.
        if (TimberLog.isTraceEnable) {
            captureDevice = MediaDeviceImpl.createTracingCaptureDevice(captureDevice);
        }

        this.captureDevice = captureDevice;
        this.localOutDataSource = createOutDataSource();
        addInDataSource((DataSource) this.captureDevice, this.localOutDataSource);
    }

    /**
     * Adds a new input <code>DataSource</code> to the collection of input <code>DataSource</code>s from
     * which this instance reads audio. If the specified <code>DataSource</code> indeed provides audio,
     * the respective contributions to the mix are always included.
     *
     * @param inDataSource a new <code>DataSource</code> to input audio to this instance
     */
    public void addInDataSource(DataSource inDataSource)
    {
        addInDataSource(inDataSource, null);
    }

    /**
     * Adds a new input <code>DataSource</code> to the collection of input <code>DataSource</code>s from
     * which this instance reads audio. If the specified <code>DataSource</code> indeed provides audio,
     * the respective contributions to the mix will be excluded from the mix output provided
     * through a specific <code>AudioMixingPushBufferDataSource</code>.
     *
     * @param inDataSource a new <code>DataSource</code> to input audio to this instance
     * @param outDataSource the <code>AudioMixingPushBufferDataSource</code> to not include the audio contributions of
     * <code>inDataSource</code> in the mix it outputs
     */
    void addInDataSource(DataSource inDataSource, AudioMixingPushBufferDataSource outDataSource)
    {
        if (inDataSource == null)
            throw new NullPointerException("inDataSource");

        synchronized (inDataSources) {
            for (InDataSourceDesc inDataSourceDesc : inDataSources)
                if (inDataSource.equals(inDataSourceDesc.inDataSource))
                    throw new IllegalArgumentException("inDataSource");

            InDataSourceDesc inDataSourceDesc = new InDataSourceDesc(inDataSource, outDataSource);
            boolean added = inDataSources.add(inDataSourceDesc);

            if (added) {
                Timber.log(TimberLog.FINER, "Added input DataSource with hashCode %s", inDataSource.hashCode());

                /*
                 * If the other inDataSources have already been connected, connect to the new
                 * one as well.
                 */
                if (connected > 0) {
                    try {
                        inDataSourceDesc.connect(this);
                    } catch (IOException ioex) {
                        throw new UndeclaredThrowableException(ioex);
                    }
                }

                // Update outStream with any new inStreams.
                if (outStream != null)
                    getOutStream();

                /*
                 * If the other inDataSources have been started, start the new one as well.
                 */
                if (started > 0) {
                    try {
                        inDataSourceDesc.start();
                    } catch (IOException ioe) {
                        throw new UndeclaredThrowableException(ioe);
                    }
                }
            }
        }
    }

    /**
     * Notifies this <code>AudioMixer</code> that an output <code>AudioMixingPushBufferDataSource</code>
     * reading from it has been connected. The first of the many
     * <code>AudioMixingPushBufferDataSource</code> s reading from this <code>AudioMixer</code> which gets
     * connected causes it to connect to the input <code>DataSource</code>s it manages.
     *
     * @throws IOException if input/output error occurred
     */
    void connect()
            throws IOException
    {
        synchronized (inDataSources) {
            if (connected == 0) {
                for (InDataSourceDesc inDataSourceDesc : inDataSources)
                    try {
                        inDataSourceDesc.connect(this);
                    } catch (IOException ioe) {
                        Timber.e(ioe, "Failed to connect to inDataSource %s",
                                MediaStreamImpl.toString(inDataSourceDesc.inDataSource));
                        throw ioe;
                    }

                /*
                 * Since the media of the input streams is to be mixed, their bufferLengths have to
                 * be equal. After a DataSource is connected, its BufferControl is available and
                 * its * bufferLength may change so make sure that the bufferLengths of the input
                 * streams are equal.
                 */
                if (outStream != null)
                    outStream.equalizeInStreamBufferLength();
            }
            connected++;
        }
    }

    /**
     * Connects to a specific <code>DataSource</code> which this <code>AudioMixer<code>
     * will read audio from. The specified <code>DataSource</code> is known to exist because of a
     * specific <code>DataSource</code> added as an input to this instance i.e. it may be an actual
     * input <code>DataSource</code> added to this instance or a <code>DataSource</code> transcoding an
     * input <code>DataSource</code> added to this instance.
     *
     * @param dataSource the <code>DataSource</code> to connect to
     * @param inDataSource the <code>DataSource</code> which is the cause for <code>dataSource</code> to exist in this
     * <code>AudioMixer</code>
     * @throws IOException if anything wrong happens while connecting to <code>dataSource</code>
     */
    protected void connect(DataSource dataSource, DataSource inDataSource)
            throws IOException
    {
        dataSource.connect();
    }

    /**
     * Notifies this <code>AudioMixer</code> that a specific input <code>DataSource</code> has finished its
     * connecting procedure. Primarily meant for input <code>DataSource</code> which have their
     * connecting executed in a separate thread as are, for example, input <code>DataSource</code>s
     * which are being transcoded.
     *
     * @param inDataSource the <code>InDataSourceDesc</code> of the input <code>DataSource</code> which has finished its
     * connecting procedure
     * @throws IOException if anything wrong happens while including <code>inDataSource</code> into the mix
     */
    void connected(InDataSourceDesc inDataSource)
            throws IOException
    {
        synchronized (inDataSources) {
            if (inDataSources.contains(inDataSource) && (connected > 0)) {
                if (started > 0)
                    inDataSource.start();
                if (outStream != null)
                    getOutStream();
            }
        }
    }

    /**
     * Creates a new <code>InStreamDesc</code> instance which is to describe a specific input
     * <code>SourceStream</code> originating from a specific input <code>DataSource</code> given by its
     * <code>InDataSourceDesc</code>.
     *
     * @param inStream the input <code>SourceStream</code> to be described by the new instance
     * @param inDataSourceDesc the input <code>DataSource</code> given by its <code>InDataSourceDesc</code> to be described
     * by the new instance
     * @return a new <code>InStreamDesc</code> instance which describes the specified input
     * <code>SourceStream</code> and <code>DataSource</code>
     */
    private InStreamDesc createInStreamDesc(SourceStream inStream, InDataSourceDesc inDataSourceDesc)
    {
        return new InStreamDesc(inStream, inDataSourceDesc);
    }

    /**
     * Creates a new <code>AudioMixingPushBufferDataSource</code> which gives access to a single audio
     * stream representing the mix of the audio streams input into this <code>AudioMixer</code> through
     * its input <code>DataSource</code>s. The returned <code>AudioMixingPushBufferDataSource</code> can
     * also be used to include new input <code>DataSources</code> in this <code>AudioMixer</code> but have
     * their contributions not included in the mix available through the returned
     * <code>AudioMixingPushBufferDataSource</code>.
     *
     * @return a new <code>AudioMixingPushBufferDataSource</code> which gives access to a single audio
     * stream representing the mix of the audio streams input into this <code>AudioMixer</code>
     * through its input <code>DataSource</code>s
     */
    public AudioMixingPushBufferDataSource createOutDataSource()
    {
        return new AudioMixingPushBufferDataSource(this);
    }

    /**
     * Creates a <code>DataSource</code> which attempts to transcode the tracks of a specific input
     * <code>DataSource</code> into a specific output <code>Format</code> .
     *
     * @param inDataSourceDesc the <code>InDataSourceDesc</code> describing the input <code>DataSource</code> to be
     * transcoded into the specified output <code>Format</code> and to receive the transcoding
     * <code>DataSource</code>
     * @param outFormat the <code>Format</code> in which the tracks of the input <code>DataSource</code> are to be
     * transcoded
     * @return <code>true</code> if a new transcoding <code>DataSource</code> has been created for the
     * input <code>DataSource</code> described by <code>inDataSourceDesc</code>; otherwise, <code>false</code>
     * @throws IOException if an error occurs while creating the transcoding <code>DataSource</code>, connecting to
     * it or staring it
     */
    private boolean createTranscodingDataSource(InDataSourceDesc inDataSourceDesc, Format outFormat)
            throws IOException
    {
        if (inDataSourceDesc.createTranscodingDataSource(outFormat)) {
            if (connected > 0)
                inDataSourceDesc.connect(this);
            if (started > 0)
                inDataSourceDesc.start();
            return true;
        }
        else
            return false;
    }

    /**
     * Notifies this <code>AudioMixer</code> that an output <code>AudioMixingPushBufferDataSource</code>
     * reading from it has been disconnected. The last of the many
     * <code>AudioMixingPushBufferDataSource</code>s reading from this <code>AudioMixer</code> which gets
     * disconnected causes it to disconnect from the input <code>DataSource</code>s it manages.
     */
    void disconnect()
    {
        synchronized (inDataSources) {
            if (connected <= 0)
                return;

            connected--;
            if (connected == 0) {
                for (InDataSourceDesc inDataSourceDesc : inDataSources)
                    inDataSourceDesc.disconnect();

                /*
                 * XXX Make the outStream to release the inStreams. Otherwise, the PushBufferStream
                 * ones which have been wrapped into CachingPushBufferStream may remain waiting.
                 */
                // cmeng - outStream may be null if earlier setup failed
                if (outStream != null) {
                    outStream.setInStreams(null);
                    outStream = null;
                }
                startedGeneration = 0;
            }
        }
    }

    /**
     * Gets the <code>BufferControl</code> of this instance and, respectively, its
     * <code>AudioMixingPushBufferDataSource</code> s.
     *
     * @return the <code>BufferControl</code> of this instance and, respectively, its
     * <code>AudioMixingPushBufferDataSource</code>s if such a control is available for the
     * <code>CaptureDevice</code> of this instance; otherwise, <code>null</code>
     */
    BufferControl getBufferControl()
    {
        if ((bufferControl == null) && (captureDevice instanceof Controls)) {
            BufferControl captureDeviceBufferControl = (BufferControl) ((Controls) captureDevice)
                    .getControl(BufferControl.class.getName());

            if (captureDeviceBufferControl != null)
                bufferControl = new ReadOnlyBufferControlDelegate(captureDeviceBufferControl);
        }
        return bufferControl;
    }

    /**
     * Gets the <code>CaptureDeviceInfo</code> of the <code>CaptureDevice</code> this <code>AudioMixer</code>
     * provides through its output <code>AudioMixingPushBufferDataSource</code>s.
     *
     * @return the <code>CaptureDeviceInfo</code> of the <code>CaptureDevice</code> this
     * <code>AudioMixer</code> provides through its output <code>AudioMixingPushBufferDataSource</code>s
     */
    CaptureDeviceInfo getCaptureDeviceInfo()
    {
        return captureDevice.getCaptureDeviceInfo();
    }

    /**
     * Gets the content type of the data output by this <code>AudioMixer</code>.
     *
     * @return the content type of the data output by this <code>AudioMixer</code>
     */
    String getContentType()
    {
        return ContentDescriptor.RAW;
    }

    /**
     * Gets the duration of each one of the output streams produced by this <code>AudioMixer</code>.
     *
     * @return the duration of each one of the output streams produced by this <code>AudioMixer</code>
     */
    Time getDuration()
    {
        return ((DataSource) captureDevice).getDuration();
    }

    /**
     * Gets an <code>InStreamDesc</code> from a specific existing list of <code>InStreamDesc</code>s which
     * describes a specific <code>SourceStream</code>. If such an <code>InStreamDesc</code> does not exist,
     * returns <code>null</code>.
     *
     * @param inStream the <code>SourceStream</code> to locate an <code>InStreamDesc</code> for in
     * <code>existingInStreamDescs</code>
     * @param existingInStreamDescs the list of existing <code>InStreamDesc</code>s in which an <code>InStreamDesc</code> for
     * <code>inStream</code> is to be located
     * @return an <code>InStreamDesc</code> from <code>existingInStreamDescs</code> which describes
     * <code>inStream</code> if such an <code>InStreamDesc</code> exists; otherwise, <code>null</code>
     */
    private InStreamDesc getExistingInStreamDesc(SourceStream inStream,
            InStreamDesc[] existingInStreamDescs)
    {
        if (existingInStreamDescs == null)
            return null;

        for (InStreamDesc existingInStreamDesc : existingInStreamDescs) {
            SourceStream existingInStream = existingInStreamDesc.getInStream();

            if (existingInStream == inStream)
                return existingInStreamDesc;
            if ((existingInStream instanceof BufferStreamAdapter<?>)
                    && (((BufferStreamAdapter<?>) existingInStream).getStream() == inStream))
                return existingInStreamDesc;
            if ((existingInStream instanceof CachingPushBufferStream)
                    && (((CachingPushBufferStream) existingInStream).getStream() == inStream))
                return existingInStreamDesc;
        }
        return null;
    }

    /**
     * Gets an array of <code>FormatControl</code>s for the <code>CaptureDevice</code> this
     * <code>AudioMixer</code> provides through its output <code>AudioMixingPushBufferDataSource</code>s.
     *
     * @return an array of <code>FormatControl</code>s for the <code>CaptureDevice</code> this
     * <code>AudioMixer</code> provides through its output
     * <code>AudioMixingPushBufferDataSource</code>s
     */
    FormatControl[] getFormatControls()
    {
        /*
         * Setting the format of the captureDevice once we've started using it is likely to wreak
         * havoc so disable it.
         */
        FormatControl[] formatControls = captureDevice.getFormatControls();

        if (!OSUtils.IS_ANDROID && (formatControls != null)) {
            for (int i = 0; i < formatControls.length; i++) {
                formatControls[i] = new ReadOnlyFormatControlDelegate(formatControls[i]);
            }
        }
        return formatControls;
    }

    /**
     * Gets the <code>SourceStream</code>s (in the form of <code>InStreamDesc</code>) of a specific
     * <code>DataSource</code> (provided in the form of <code>InDataSourceDesc</code>) which produce
     * data in a specific <code>AudioFormat</code> (or a matching one).
     *
     * @param inDataSourceDesc the <code>DataSource</code> (in the form of <code>InDataSourceDesc</code>) which is to be
     * examined for <code>SourceStreams</code> producing data in the specified
     * <code>AudioFormat</code>
     * @param outFormat the <code>AudioFormat</code> in which the collected <code>SourceStream</code>s are to produce
     * data
     * @param existingInStreams the <code>InStreamDesc</code> instances which already exist and which are used to avoid
     * creating multiple <code>InStreamDesc</code>s for input <code>SourceStream</code>s which
     * already have ones
     * @param inStreams the <code>List</code> of <code>InStreamDesc</code> in which the discovered
     * <code>SourceStream</code>s are to be returned
     * @return <code>true</code> if <code>SourceStream</code>s produced by the specified input
     * <code>DataSource</code> and outputting data in the specified <code>AudioFormat</code> were
     * discovered and reported in <code>inStreams</code>; otherwise, <code>false</code>
     */
    private boolean getInStreamsFromInDataSource(InDataSourceDesc inDataSourceDesc,
            AudioFormat outFormat, InStreamDesc[] existingInStreams, List<InStreamDesc> inStreams)
    {
        SourceStream[] inDataSourceStreams = inDataSourceDesc.getStreams();

        if (inDataSourceStreams != null) {
            boolean added = false;

            for (SourceStream inStream : inDataSourceStreams) {
                Format inFormat = getFormat(inStream);

                if (matches(inFormat, outFormat)) {
                    InStreamDesc inStreamDesc = getExistingInStreamDesc(inStream, existingInStreams);

                    if (inStreamDesc == null)
                        inStreamDesc = createInStreamDesc(inStream, inDataSourceDesc);
                    if (inStreams.add(inStreamDesc))
                        added = true;
                }
            }
            return added;
        }

        DataSource inDataSource = inDataSourceDesc.getEffectiveInDataSource();

        if (inDataSource == null)
            return false;

        Format inFormat = getFormat(inDataSource);

        if ((inFormat != null) && !matches(inFormat, outFormat)) {
            if (inDataSource instanceof PushDataSource) {
                for (PushSourceStream inStream : ((PushDataSource) inDataSource).getStreams()) {
                    InStreamDesc inStreamDesc = getExistingInStreamDesc(inStream, existingInStreams);

                    if (inStreamDesc == null)
                        inStreamDesc = createInStreamDesc(new PushBufferStreamAdapter(inStream,
                                inFormat), inDataSourceDesc);
                    inStreams.add(inStreamDesc);
                }
                return true;
            }
            if (inDataSource instanceof PullDataSource) {
                for (PullSourceStream inStream : ((PullDataSource) inDataSource).getStreams()) {
                    InStreamDesc inStreamDesc = getExistingInStreamDesc(inStream, existingInStreams);

                    if (inStreamDesc == null)
                        inStreamDesc = createInStreamDesc(new PullBufferStreamAdapter(inStream, inFormat), inDataSourceDesc);
                    inStreams.add(inStreamDesc);
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Gets the <code>SourceStream</code>s (in the form of <code>InStreamDesc</code>) of the
     * <code>DataSource</code>s from which this <code>AudioMixer</code> reads data which produce data in a
     * specific <code>AudioFormat</code>. When an input <code>DataSource</code> does not have such
     * <code>SourceStream</code>s, an attempt is made to transcode its tracks so that such
     * <code>SourceStream</code>s can be retrieved from it after transcoding.
     *
     * @param outFormat the <code>AudioFormat</code> in which the retrieved <code>SourceStream</code>s are to produce
     * data
     * @param existingInStreams the <code>SourceStream</code>s which are already known to this <code>AudioMixer</code>
     * @return a new collection of <code>SourceStream</code>s (in the form of <code>InStreamDesc</code>)
     * retrieved from the input <code>DataSource</code>s of this <code>AudioMixer</code> and
     * producing data in the specified <code>AudioFormat</code>
     * @throws IOException if anything wrong goes while retrieving the input <code>SourceStream</code>s from the
     * input <code>DataSource</code>s
     */
    private Collection<InStreamDesc> getInStreamsFromInDataSources(AudioFormat outFormat,
            InStreamDesc[] existingInStreams)
            throws IOException
    {
        List<InStreamDesc> inStreams = new ArrayList<>();

        synchronized (inDataSources) {
            for (InDataSourceDesc inDataSourceDesc : inDataSources) {
                boolean got = getInStreamsFromInDataSource(inDataSourceDesc, outFormat,
                        existingInStreams, inStreams);

                if (!got && createTranscodingDataSource(inDataSourceDesc, outFormat))
                    getInStreamsFromInDataSource(inDataSourceDesc, outFormat, existingInStreams,
                            inStreams);
            }
        }
        return inStreams;
    }

    /**
     * Gets the <code>AudioMixingPushBufferDataSource</code> containing the mix of all input
     * <code>DataSource</code>s excluding the <code>CaptureDevice</code> of this <code>AudioMixer</code> and is
     * thus meant for playback on the local peer in a call.
     *
     * @return the <code>AudioMixingPushBufferDataSource</code> containing the mix of all input
     * <code>DataSource</code>s excluding the <code>CaptureDevice</code> of this <code>AudioMixer</code>
     * and is thus meant for playback on the local peer in a call
     */
    public AudioMixingPushBufferDataSource getLocalOutDataSource()
    {
        return localOutDataSource;
    }

    /**
     * Gets the <code>AudioFormat</code> in which the input <code>DataSource</code>s of this
     * <code>AudioMixer</code> can produce data and which is to be the output <code>Format</code> of this
     * <code>AudioMixer</code>.
     *
     * @return the <code>AudioFormat</code> in which the input <code>DataSource</code>s of this
     * <code>AudioMixer</code> can produce data and which is to be the output <code>Format</code> of
     * this <code>AudioMixer</code>
     */
    private AudioFormat getOutFormatFromInDataSources()
    {
        String formatControlType = FormatControl.class.getName();
        AudioFormat outFormat = null;

        synchronized (inDataSources) {
            for (InDataSourceDesc inDataSource : inDataSources) {
                DataSource effectiveInDataSource = inDataSource.getEffectiveInDataSource();

                if (effectiveInDataSource == null)
                    continue;

                FormatControl formatControl = (FormatControl) effectiveInDataSource
                        .getControl(formatControlType);

                if (formatControl != null) {
                    AudioFormat format = (AudioFormat) formatControl.getFormat();

                    if (format != null) {
                        // SIGNED
                        int signed = format.getSigned();

                        if ((AudioFormat.SIGNED == signed) || (Format.NOT_SPECIFIED == signed)) {
                            // LITTLE_ENDIAN
                            int endian = format.getEndian();

                            if ((AudioFormat.LITTLE_ENDIAN == endian)
                                    || (Format.NOT_SPECIFIED == endian)) {
                                outFormat = format;
                                break;
                            }
                        }
                    }
                }
            }
        }

        if (outFormat == null)
            outFormat = DEFAULT_OUTPUT_FORMAT;

        Timber.log(TimberLog.FINER, "Determined outFormat of AudioMixer from inDataSources to be %s", outFormat);
        return outFormat;
    }

    /**
     * Gets the <code>AudioMixerPushBufferStream</code>, first creating it if it does not exist
     * already, which reads data from the input <code>DataSource</code>s of this <code>AudioMixer</code>
     * and pushes it to output <code>AudioMixingPushBufferStream</code>s for audio mixing.
     *
     * @return the <code>AudioMixerPushBufferStream</code> which reads data from the input
     * <code>DataSource</code>s of this <code>AudioMixer</code> and pushes it to output
     * <code>AudioMixingPushBufferStream</code>s for audio mixing
     */
    AudioMixerPushBufferStream getOutStream()
    {
        synchronized (inDataSources) {
            AudioFormat outFormat = (outStream == null) ? getOutFormatFromInDataSources()
                    : outStream.getFormat();

            // force to preferred output format
            // outFormat = PREFFERED_OUTPUT_FORMAT;

            setOutFormatToInDataSources(outFormat);
            Collection<InStreamDesc> inStreams;

            try {
                inStreams = getInStreamsFromInDataSources(outFormat, (outStream == null)
                        ? null : outStream.getInStreams());
            } catch (IOException ioex) {
                throw new UndeclaredThrowableException(ioex);
            }

            if (outStream == null) {
                outStream = new AudioMixerPushBufferStream(this, outFormat);
                startedGeneration = 0;
            }
            outStream.setInStreams(inStreams);
            return outStream;
        }
    }

    /**
     * Searches this object's <code>inDataSource</code>s for one that matches <code>inDataSource</code>,
     * and returns it's associated <code>TranscodingDataSource</code>. Currently this is only used
     * when the <code>MediaStream</code> needs access to the codec chain used to playback one of it's
     * <code>ReceiveStream</code>s.
     *
     * @param inDataSource the <code>DataSource</code> to search for.
     * @return The <code>TranscodingDataSource</code> associated with <code>inDataSource</code>, if we can
     * find one, <code>null</code> otherwise.
     */
    public TranscodingDataSource getTranscodingDataSource(DataSource inDataSource)
    {
        for (InDataSourceDesc inDataSourceDesc : inDataSources) {
            DataSource ourDataSource = inDataSourceDesc.getInDataSource();

            if (ourDataSource == inDataSource)
                return inDataSourceDesc.getTranscodingDataSource();
            else if (ourDataSource instanceof ReceiveStreamPushBufferDataSource) {
                // Sometimes the inDataSource has come to AudioMixer wrapped in
                // a ReceiveStreamPushBufferDataSource. We consider it to match.
                if (((ReceiveStreamPushBufferDataSource) ourDataSource).getDataSource() == inDataSource)
                    return inDataSourceDesc.getTranscodingDataSource();
            }
        }
        return null;
    }

    /**
     * Determines whether a specific <code>Format</code> matches a specific <code>Format</code> in the
     * sense of JMF <code>Format</code> matching. Since this <code>AudioMixer</code> and the audio mixing
     * functionality related to it can handle varying characteristics of a certain output
     * <code>Format</code>, the only requirement for the specified <code>Format</code>s to match is for
     * both of them to have one and the same encoding.
     *
     * @param input the <code>Format</code> for which it is required to determine whether it matches a
     * specific <code>Format</code>
     * @param pattern the <code>Format</code> against which the specified <code>input</code> is to be matched
     * @return <code>true</code> if the specified <code>input<code> matches the specified
     * <code>pattern</code> in the sense of JMF <code>Format</code> matching; otherwise, <code>false</code>
     */
    private boolean matches(Format input, AudioFormat pattern)
    {
        return ((input instanceof AudioFormat) && input.isSameEncoding(pattern));
    }

    /**
     * Reads media from a specific <code>PushBufferStream</code> which belongs to a specific
     * <code>DataSource</code> into a specific output <code>Buffer</code>. Allows extenders to tap into the
     * reading and monitor and customize it.
     *
     * @param stream the <code>PushBufferStream</code> to read media from and known to belong to the specified
     * <code>DataSOurce</code>
     * @param buffer the output <code>Buffer</code> in which the media read from the specified <code>stream</code>
     * is to be written so that it gets returned to the caller
     * @param dataSource the <code>DataSource</code> from which <code>stream</code> originated
     * @throws IOException if anything wrong happens while reading from the specified <code>stream</code>
     */
    protected void read(PushBufferStream stream, Buffer buffer, DataSource dataSource)
            throws IOException
    {
        stream.read(buffer);
    }

    /**
     * Removes <code>DataSource</code>s accepted by a specific <code>DataSourceFilter</code> from the list
     * of input <code>DataSource</code>s of this <code>AudioMixer</code> from which it reads audio to be
     * mixed.
     *
     * @param dataSourceFilter the <code>DataSourceFilter</code> which selects the <code>DataSource</code>s to be removed
     * from the list of input <code>DataSource</code>s of this <code>AudioMixer</code> from which it
     * reads audio to be mixed
     */
    public void removeInDataSources(DataSourceFilter dataSourceFilter)
    {
        synchronized (inDataSources) {
            Iterator<InDataSourceDesc> inDataSourceIter = inDataSources.iterator();
            boolean removed = false;

            while (inDataSourceIter.hasNext()) {
                InDataSourceDesc inDsDesc = inDataSourceIter.next();
                if (dataSourceFilter.accept(inDsDesc.getInDataSource())) {
                    inDataSourceIter.remove();
                    removed = true;

                    try {
                        inDsDesc.stop();
                        inDsDesc.disconnect();
                    } catch (IOException ex) {
                        Timber.e(ex, "Failed to stop DataSource");
                    }
                }
            }
            if (removed && (outStream != null))
                getOutStream();
        }
    }

    /**
     * Sets a specific <code>AudioFormat</code>, if possible, as the output format of the input
     * <code>DataSource</code>s of this <code>AudioMixer</code> in an attempt to not have to perform
     * explicit transcoding of the input <code>SourceStream</code> s.
     *
     * @param outFormat the <code>AudioFormat</code> in which the input <code>DataSource</code>s of this
     * <code>AudioMixer</code> are to be instructed to output
     */
    private void setOutFormatToInDataSources(AudioFormat outFormat)
    {
        String formatControlType = FormatControl.class.getName();

        synchronized (inDataSources) {
            for (InDataSourceDesc inDataSourceDesc : inDataSources) {
                FormatControl formatControl
                        = (FormatControl) inDataSourceDesc.getControl(formatControlType);

                if (formatControl != null) {
                    Format inFormat = formatControl.getFormat();

                    if ((inFormat == null) || !matches(inFormat, outFormat)) {
                        Format setFormat = formatControl.setFormat(outFormat);

                        if (setFormat == null)
                            Timber.e("Failed to set format of inDataSource to %s", outFormat);
                        else if (setFormat != outFormat)
                            Timber.w("Failed to change format of inDataSource from %s to %s", setFormat, outFormat);
                        else
                            Timber.log(TimberLog.FINER, "Set format of inDataSource to %s", setFormat);
                    }
                }
            }
        }
    }

    /**
     * Starts the input <code>DataSource</code>s of this <code>AudioMixer</code>.
     *
     * @param outStream the <code>AudioMixerPushBufferStream</code> which requests this <code>AudioMixer</code> to
     * start. If <code>outStream</code> is the current one and only
     * <code>AudioMixerPushBufferStream</code> of this <code>AudioMixer</code>, this
     * <code>AudioMixer</code> starts if it hasn't started yet. Otherwise, the request is ignored.
     * @param generation a value generated by <code>outStream</code> indicating the order of the invocations of the
     * <code>start</code> and <code>stop</code> methods performed by <code>outStream</code> allowing it
     * to execute the said methods outside synchronized blocks for the purposes of reducing
     * deadlock risks
     * @throws IOException if any of the input <code>DataSource</code>s of this <code>AudioMixer</code> throws such an
     * exception while attempting to start it
     */
    void start(AudioMixerPushBufferStream outStream, long generation)
            throws IOException
    {
        synchronized (inDataSources) {
            /*
             * AudioMixer has only one outStream at a time and only its current outStream knows
             * when it has to start (and stop).
             */
            if (this.outStream != outStream)
                return;
            /*
             * The notion of generations was introduced in order to allow outStream to invoke the
             * start and stop methods outside synchronized blocks. The generation value always
             * increases in a synchronized block.
             */
            if (startedGeneration < generation)
                startedGeneration = generation;
            else
                return;

            if (started == 0) {
                for (InDataSourceDesc inDataSourceDesc : inDataSources)
                    inDataSourceDesc.start();
            }

            started++;
        }
    }

    /**
     * Stops the input <code>DataSource</code>s of this <code>AudioMixer</code>.
     *
     * @param outStream the <code>AudioMixerPushBufferStream</code> which requests this <code>AudioMixer</code> to
     * stop. If <code>outStream</code> is the current one and only
     * <code>AudioMixerPushBufferStream</code> of this <code>AudioMixer</code>, this
     * <code>AudioMixer</code> stops. Otherwise, the request is ignored.
     * @param generation a value generated by <code>outStream</code> indicating the order of the invocations of the
     * <code>start</code> and <code>stop</code> methods performed by <code>outStream</code> allowing it
     * to execute the said methods outside synchronized blocks for the purposes of reducing
     * deadlock risks
     * @throws IOException if any of the input <code>DataSource</code>s of this <code>AudioMixer</code> throws such an
     * exception while attempting to stop it
     */
    void stop(AudioMixerPushBufferStream outStream, long generation)
            throws IOException
    {
        synchronized (inDataSources) {
            /*
             * AudioMixer has only one outStream at a time and only its current outStream knows
             * when it has to stop (and start).
             */
            if (this.outStream != outStream)
                return;
            /*
             * The notion of generations was introduced in order to allow outStream to invoke the
             * start and stop methods outside synchronized blocks. The generation value always
             * increases in a synchronized block.
             */
            if (startedGeneration < generation)
                startedGeneration = generation;
            else
                return;

            if (started <= 0)
                return;

            started--;

            if (started == 0) {
                for (InDataSourceDesc inDataSourceDesc : inDataSources)
                    inDataSourceDesc.stop();
            }
        }
    }
}
