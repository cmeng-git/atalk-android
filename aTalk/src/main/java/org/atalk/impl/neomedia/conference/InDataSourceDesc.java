/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.conference;

import org.atalk.impl.neomedia.MediaStreamImpl;
import org.atalk.impl.neomedia.protocol.TranscodingDataSource;

import java.io.IOException;

import javax.media.Format;
import javax.media.protocol.DataSource;
import javax.media.protocol.PullBufferDataSource;
import javax.media.protocol.PushBufferDataSource;
import javax.media.protocol.SourceStream;

import timber.log.Timber;

/**
 * Describes additional information about a specific input <code>DataSource</code> of an
 * <code>AudioMixer</code> so that the <code>AudioMixer</code> can, for example, quickly discover the output
 * <code>AudioMixingPushBufferDataSource</code> in the mix of which the contribution of the
 * <code>DataSource</code> is to not be included.
 * <p>
 * Private to <code>AudioMixer</code> and <code>AudioMixerPushBufferStream</code> but extracted into its own
 * file for the sake of clarity.
 * </p>
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
class InDataSourceDesc
{
    /**
     * The constant which represents an empty array with <code>SourceStream</code> element type.
     * Explicitly defined in order to avoid unnecessary allocations.
     */
    private static final SourceStream[] EMPTY_STREAMS = new SourceStream[0];

    /**
     * The indicator which determines whether the effective input <code>DataSource</code> described by
     * this instance is currently connected.
     */
    private boolean connected;

    /**
     * The <code>Thread</code> which currently executes {@link DataSource#connect()} on the effective
     * input <code>DataSource</code> described by this instance.
     */
    private Thread connectThread;

    /**
     * The <code>DataSource</code> for which additional information is described by this instance.
     */
    public final DataSource inDataSource;

    /**
     * The <code>AudioMixingPushBufferDataSource</code> in which the mix contributions of
     * {@link #inDataSource} are to not be included.
     */
    public final AudioMixingPushBufferDataSource outDataSource;

    /**
     * The <code>DataSource</code>, if any, which transcodes the tracks of {@link #inDataSource} in the
     * output <code>Format</code> of the associated <code>AudioMixer</code>.
     */
    private DataSource transcodingDataSource;

    /**
     * Initializes a new <code>InDataSourceDesc</code> instance which is to describe additional
     * information about a specific input <code>DataSource</code> of an <code>AudioMixer</code>. Associates
     * the specified <code>DataSource</code> with the <code>AudioMixingPushBufferDataSource</code> in which
     * the mix contributions of the specified input <code>DataSource</code> are to not be included.
     *
     * @param inDataSource a <code>DataSource</code> for which additional information is to be described by the new
     * instance
     * @param outDataSource the <code>AudioMixingPushBufferDataSource</code> in which the mix contributions of
     * <code>inDataSource</code> are to not be included
     */
    public InDataSourceDesc(DataSource inDataSource, AudioMixingPushBufferDataSource outDataSource)
    {
        this.inDataSource = inDataSource;
        this.outDataSource = outDataSource;
    }

    /**
     * Connects the effective input <code>DataSource</code> described by this instance upon request
     * from a specific <code>AudioMixer</code>. If the effective input <code>DataSource</code> is to be
     * asynchronously connected, the completion of the connect procedure will be reported to the
     * specified <code>AudioMixer</code> by calling its {@link AudioMixer#connected(InDataSourceDesc)}.
     *
     * @param audioMixer the <code>AudioMixer</code> requesting the effective input <code>DataSource</code> described
     * by this instance to be connected
     * @throws IOException if anything wrong happens while connecting the effective input <code>DataSource</code>
     * described by this instance
     */
    synchronized void connect(final AudioMixer audioMixer)
            throws IOException
    {
        final DataSource effectiveInDataSource = (transcodingDataSource == null)
                ? inDataSource : transcodingDataSource;

        if (effectiveInDataSource instanceof TranscodingDataSource) {
            if (connectThread == null) {
                connectThread = new Thread()
                {
                    @Override
                    public void run()
                    {
                        try {
                            audioMixer.connect(effectiveInDataSource, inDataSource);
                            synchronized (InDataSourceDesc.this) {
                                connected = true;
                            }
                            audioMixer.connected(InDataSourceDesc.this);
                        } catch (IOException ioex) {
                            Timber.e(ioex, "Failed to connect to inDataSource %s", MediaStreamImpl.toString(inDataSource));
                        } finally {
                            synchronized (InDataSourceDesc.this) {
                                if (connectThread == Thread.currentThread())
                                    connectThread = null;
                            }
                        }
                    }
                };
                connectThread.setDaemon(true);
                connectThread.start();
            }
        }
        else {
            audioMixer.connect(effectiveInDataSource, inDataSource);
            connected = true;
        }
    }

    /**
     * Creates a <code>DataSource</code> which attempts to transcode the tracks of the input
     * <code>DataSource</code> described by this instance into a specific output <code>Format</code>.
     *
     * @param outFormat the <code>Format</code> in which the tracks of the input <code>DataSource</code> described by
     * this instance are to be transcoded
     * @return <code>true</code> if a new transcoding <code>DataSource</code> has been created for the
     * input <code>DataSource</code> described by this instance; otherwise, <code>false</code>
     */
    synchronized boolean createTranscodingDataSource(Format outFormat)
    {
        if (transcodingDataSource == null) {
            setTranscodingDataSource(new TranscodingDataSource(inDataSource, outFormat));
            return true;
        }
        else
            return false;
    }

    /**
     * Disconnects the effective input <code>DataSource</code> described by this instance if it is
     * already connected.
     */
    synchronized void disconnect()
    {
        if (connected) {
            getEffectiveInDataSource().disconnect();
            connected = false;
        }
    }

    /**
     * Gets the control available for the effective input <code>DataSource</code> described by this
     * instance with a specific type.
     *
     * @param controlType a <code>String</code> value which specifies the type of the control to be retrieved
     * @return an <code>Object</code> which represents the control available for the effective input
     * <code>DataSource</code> described by this instance with the specified
     * <code>controlType</code> if such a control exists; otherwise, <code>null</code>
     */
    public synchronized Object getControl(String controlType)
    {
        DataSource effectiveInDataSource = getEffectiveInDataSource();

        return (effectiveInDataSource == null)
                ? null : effectiveInDataSource.getControl(controlType);
    }

    /**
     * Gets the actual <code>DataSource</code> from which the associated <code>AudioMixer</code> directly
     * reads in order to retrieve the mix contribution of the <code>DataSource</code> described by this
     * instance.
     *
     * @return the actual <code>DataSource</code> from which the associated <code>AudioMixer</code>
     * directly reads in order to retrieve the mix contribution of the <code>DataSource</code> described
     * by this instance
     */
    public synchronized DataSource getEffectiveInDataSource()
    {
        return (transcodingDataSource == null)
                ? inDataSource : (connected ? transcodingDataSource : null);
    }

    /**
     * Returns this instance's <code>inDataSource</code>
     *
     * @return this instance's <code>inDataSource</code>
     */
    public DataSource getInDataSource()
    {
        return inDataSource;
    }

    /**
     * Gets the <code>SourceStream</code>s of the effective input <code>DataSource</code> described by this
     * instance.
     *
     * @return an array of the <code>SourceStream</code>s of the effective input <code>DataSource</code>
     * described by this instance
     */
    public synchronized SourceStream[] getStreams()
    {
        if (!connected)
            return EMPTY_STREAMS;

        DataSource inDataSource = getEffectiveInDataSource();

        if (inDataSource instanceof PushBufferDataSource)
            return ((PushBufferDataSource) inDataSource).getStreams();
        else if (inDataSource instanceof PullBufferDataSource)
            return ((PullBufferDataSource) inDataSource).getStreams();
        else if (inDataSource instanceof TranscodingDataSource)
            return ((TranscodingDataSource) inDataSource).getStreams();
        else
            return null;
    }

    /**
     * Returns the <code>TranscodingDataSource</code> object used in this instance.
     *
     * @return the <code>TranscodingDataSource</code> object used in this instance.
     */
    public TranscodingDataSource getTranscodingDataSource()
    {
        return (TranscodingDataSource) transcodingDataSource;
    }

    /**
     * Sets the <code>DataSource</code>, if any, which transcodes the tracks of the input
     * <code>DataSource</code> described by this instance in the output <code>Format</code> of the
     * associated <code>AudioMixer</code>.
     *
     * @param transcodingDataSource the <code>DataSource</code> which transcodes the tracks of the input <code>DataSource</code>
     * described by this instance in the output <code>Format</code> of the associated
     * <code>AudioMixer</code>
     */
    private synchronized void setTranscodingDataSource(DataSource transcodingDataSource)
    {
        this.transcodingDataSource = transcodingDataSource;
        connected = false;
    }

    /**
     * Starts the effective input <code>DataSource</code> described by this instance if it is connected.
     *
     * @throws IOException if starting the effective input <code>DataSource</code> described by this instance fails
     */
    synchronized void start()
            throws IOException
    {
        if (connected)
            getEffectiveInDataSource().start();
    }

    /**
     * Stops the effective input <code>DataSource</code> described by this instance if it is connected.
     *
     * @throws IOException if stopping the effective input <code>DataSource</code> described by this instance fails
     */
    synchronized void stop()
            throws IOException
    {
        if (connected)
            getEffectiveInDataSource().stop();
    }
}
