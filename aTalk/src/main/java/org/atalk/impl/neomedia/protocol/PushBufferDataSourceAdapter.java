/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.protocol;

import net.sf.fmj.media.util.MediaThread;

import org.atalk.impl.neomedia.jmfext.media.renderer.AbstractRenderer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.media.Buffer;
import javax.media.Format;
import javax.media.format.AudioFormat;
import javax.media.format.VideoFormat;
import javax.media.protocol.BufferTransferHandler;
import javax.media.protocol.ContentDescriptor;
import javax.media.protocol.DataSource;
import javax.media.protocol.PullBufferDataSource;
import javax.media.protocol.PullBufferStream;
import javax.media.protocol.PushBufferDataSource;
import javax.media.protocol.PushBufferStream;
import javax.media.protocol.SourceStream;

import timber.log.Timber;

/**
 * Implements <code>PushBufferDataSource</code> for a specific <code>PullBufferDataSource</code>.
 *
 * @author Lyubomir Marinov
 */
public class PushBufferDataSourceAdapter extends PushBufferDataSourceDelegate<PullBufferDataSource>
{

    /**
     * Implements <code>PushBufferStream</code> for a specific <code>PullBufferStream</code>.
     */
    private static class PushBufferStreamAdapter implements PushBufferStream
    {

        /**
         * The <code>Buffer</code> which contains the media data read by this instance from
         * {@link #stream} and to be returned by this implementation of
         * {@link PushBufferStream#read(Buffer)} by copying.
         */
        private final Buffer buffer = new Buffer();

        /**
         * The indicator which determines whether {@link #buffer} contains media data read by this
         * instance from {@link #stream} and not returned by this implementation of
         * {@link PushBufferStream#read(Buffer)} yet.
         */
        private boolean bufferIsWritten = false;

        /**
         * The indicator which determined whether {@link #start()} has been called without a
         * subsequent call to {@link #stop()}.
         */
        private boolean started = false;

        /**
         * The <code>PullBufferStream</code> to which this instance provides <code>PushBufferStream</code>
         * capabilities.
         */
        public final PullBufferStream stream;

        /**
         * The <code>IOException</code>, if any, which has been thrown by the last call to
         * {@link PullBufferStream#read(Buffer)} on {@link #stream} and which still hasn't been
         * rethrown by this implementation of {@link PushBufferStream#read(Buffer)}.
         */
        private IOException streamReadException;

        /**
         * The <code>Thread</code> which currently reads media data from {@link #stream} into
         * {@link #buffer}.
         */
        private Thread streamReadThread;

        /**
         * The <code>Object</code> which synchronizes the access to {@link #streamReadThread}-related
         * members.
         */
        private final Object streamReadThreadSyncRoot = new Object();

        /**
         * The <code>BufferTransferHandler</code> through which this <code>PushBufferStream</code> notifies
         * its user that media data is available for reading.
         */
        private BufferTransferHandler transferHandler;

        /**
         * Initializes a new <code>PushBufferStreamAdapter</code> instance which is to implement
         * <code>PushBufferStream</code> for a specific <code>PullBufferStream</code>.
         *
         * @param stream the <code>PullBufferStream</code> the new instance is to implement
         * <code>PushBufferStream</code> for
         */
        public PushBufferStreamAdapter(PullBufferStream stream)
        {
            if (stream == null)
                throw new NullPointerException("stream");
            this.stream = stream;
        }

        /**
         * Disposes of this <code>PushBufferStreamAdapter</code>. Afterwards, this instance is not
         * guaranteed to be operation and considered to be available for garbage collection.
         */
        void close()
        {
            stop();
        }

        /**
         * Implements {@link SourceStream#endOfStream()}. Delegates to the wrapped
         * <code>PullBufferStream</code>.
         *
         * @return <code>true</code> if the wrapped <code>PullBufferStream</code> has reached the end of
         * the
         * media data; otherwise, <code>false</code>
         */
        @Override
        public boolean endOfStream()
        {
            return stream.endOfStream();
        }

        /**
         * Implements {@link SourceStream#getContentDescriptor()}. Delegates to the wrapped
         * <code>PullBufferStream</code>.
         *
         * @return the <code>ContentDescriptor</code> of the wrapped <code>PullBufferStream</code> which
         * describes the type of the media data it gives access to
         */
        @Override
        public ContentDescriptor getContentDescriptor()
        {
            return stream.getContentDescriptor();
        }

        /**
         * Implements {@link SourceStream#getContentLength()}. Delegates to the wrapped
         * <code>PullBufferStream</code>.
         *
         * @return the length of the content the wrapped <code>PullBufferStream</code> gives access to
         */
        @Override
        public long getContentLength()
        {
            return stream.getContentLength();
        }

        /**
         * Implements {@link javax.media.Controls#getControl(String)}. Delegates to the wrapped
         * <code>PullBufferStream</code>.
         *
         * @param controlType a <code>String</code> value which specifies the type of the control of the wrapped
         * <code>PullBufferStream</code> to be retrieved
         * @return an <code>Object</code> which represents the control of the wrapped
         * <code>PushBufferStream</code> of the requested type if the wrapped
         * <code>PushBufferStream</code> has such a control; <code>null</code> if the wrapped
         * <code>PushBufferStream</code> does not have a control of the specified type
         */
        @Override
        public Object getControl(String controlType)
        {
            return stream.getControl(controlType);
        }

        /**
         * Implements {@link javax.media.Controls#getControls()}. Delegates to the wrapped
         * <code>PushBufferStream</code>.
         *
         * @return an array of <code>Object</code>s which represent the controls available for the
         * wrapped <code>PushBufferStream</code>
         */
        @Override
        public Object[] getControls()
        {
            return stream.getControls();
        }

        /**
         * Implements {@link PushBufferStream#getFormat()}. Delegates to the wrapped
         * <code>PullBufferStream</code>.
         *
         * @return the <code>Format</code> of the wrapped <code>PullBufferStream</code>
         */
        @Override
        public Format getFormat()
        {
            return stream.getFormat();
        }

        /**
         * Implements {@link PushBufferStream#read(Buffer)}.
         *
         * @param buffer a <code>Buffer</code> in which media data is to be written by this
         * <code>PushBufferDataSource</code>
         * @throws IOException if anything wrong happens while reading media data from this
         * <code>PushBufferDataSource</code> into the specified <code>buffer</code>
         */
        @Override
        public void read(Buffer buffer)
                throws IOException
        {
            synchronized (this.buffer) {
                /*
                 * If stream has throw an exception during its last read, rethrow it as an
                 * exception
                 * of this stream.
                 */
                if (streamReadException != null) {
                    IOException ie = new IOException();

                    ie.initCause(streamReadException);
                    streamReadException = null;
                    throw ie;
                }
                else if (bufferIsWritten) {
                    buffer.copy(this.buffer);
                    bufferIsWritten = false;
                }
                else
                    buffer.setLength(0);
            }
        }

        /**
         * Executes an iteration of {@link #streamReadThread} i.e. reads media data from
         * {@link #stream} into {@link #buffer} and invokes
         * {@link BufferTransferHandler#transferData(PushBufferStream)} on {@link #transferHandler}
         * if any.
         */
        private void runInStreamReadThread()
        {
            boolean bufferIsWritten;
            boolean yield;

            synchronized (buffer) {
                try {
                    stream.read(buffer);
                    this.bufferIsWritten = !buffer.isDiscard();
                    streamReadException = null;
                } catch (IOException ie) {
                    this.bufferIsWritten = false;
                    streamReadException = ie;
                }
                bufferIsWritten = this.bufferIsWritten;
                /*
                 * If an exception has been thrown by the stream's read method, it may be better to
                 * give the stream's underlying implementation (e.g. PortAudio) a little time to
                 * possibly get its act together.
                 */
                yield = (!bufferIsWritten && (streamReadException != null));
            }

            if (bufferIsWritten) {
                BufferTransferHandler transferHandler = this.transferHandler;

                if (transferHandler != null)
                    transferHandler.transferData(this);
            }
            else if (yield)
                Thread.yield();
        }

        /**
         * Implements {@link PushBufferStream#setTransferHandler(BufferTransferHandler)}. Sets the
         * means through which this <code>PushBufferStream</code> is to notify its user that media data
         * is available for reading.
         *
         * @param transferHandler the <code>BufferTransferHandler</code> through which <code>PushBufferStream</code> is to
         * notify its user that media data is available for reading
         */
        @Override
        public void setTransferHandler(BufferTransferHandler transferHandler)
        {
            if (this.transferHandler != transferHandler)
                this.transferHandler = transferHandler;
        }

        /**
         * Starts the reading of media data of this <code>PushBufferStreamAdapter</code> from the
         * wrapped <code>PullBufferStream</code>.
         */
        void start()
        {
            synchronized (streamReadThreadSyncRoot) {
                PushBufferStreamAdapter.this.started = true;

                if (streamReadThread == null) {
                    streamReadThread = new Thread(getClass().getName() + ".streamReadThread")
                    {
                        @Override
                        public void run()
                        {
                            try {
                                setStreamReadThreadPriority(stream);

                                while (true) {
                                    synchronized (streamReadThreadSyncRoot) {
                                        if (!PushBufferStreamAdapter.this.started)
                                            break;
                                        if (streamReadThread != Thread.currentThread())
                                            break;
                                    }
                                    runInStreamReadThread();
                                }
                            } finally {
                                synchronized (streamReadThreadSyncRoot) {
                                    if (streamReadThread == Thread.currentThread()) {
                                        streamReadThread = null;
                                        streamReadThreadSyncRoot.notifyAll();
                                    }
                                }
                            }
                        }
                    };
                    streamReadThread.setDaemon(true);
                    streamReadThread.start();
                }
            }
        }

        /**
         * Stops the reading of media data of this <code>PushBufferStreamAdapter</code> from the
         * wrapped <code>PullBufferStream</code>.
         */
        void stop()
        {
            synchronized (streamReadThreadSyncRoot) {
                started = false;
                if (STRICT_STOP) {
                    boolean interrupted = false;

                    while (streamReadThread != null) {
                        try {
                            streamReadThreadSyncRoot.wait();
                        } catch (InterruptedException iex) {
                            Timber.i(iex, " %s interrupted while waiting for PullBufferStream read thread to stop.",
                                    getClass().getSimpleName());
                            interrupted = true;
                        }
                    }
                    if (interrupted)
                        Thread.currentThread().interrupt();
                }
                else
                    streamReadThread = null;
            }
        }
    }

    /**
     * The indicator which determines whether the <code>PushBufferStreamAdapater</code> instances
     * should wait for their {@link PushBufferStreamAdapter#streamReadThread}s to exit before their
     * {@link PushBufferStreamAdapter#stop()} returns.
     */
    private static final boolean STRICT_STOP = false;

    /**
     * The indicator which determines whether {@link #start()} has been called on this
     * <code>DataSource</code> without a subsequent call to {@link #stop()}.
     */
    private boolean started = false;

    /**
     * The <code>PushBufferStream</code>s through which this <code>PushBufferDataSource</code> gives access
     * to its media data.
     */
    private final List<PushBufferStreamAdapter> streams = new ArrayList<>();

    /**
     * Initializes a new <code>PushBufferDataSourceAdapter</code> which is to implement
     * <code>PushBufferDataSource</code> capabilities for a specific <code>PullBufferDataSource</code>.
     *
     * @param dataSource the <code>PullBufferDataSource</code> the new instance is to implement
     * <code>PushBufferDataSource</code> capabilities for
     */
    public PushBufferDataSourceAdapter(PullBufferDataSource dataSource)
    {
        super(dataSource);
    }

    /**
     * Implements {@link DataSource#disconnect()}. Disposes of the
     * <code>PushBufferStreamAdapter</code>s which wrap the <code>PullBufferStream</code>s of the
     * <code>PullBufferDataSource</code> wrapped by this instance.
     */
    @Override
    public void disconnect()
    {
        synchronized (streams) {
            Iterator<PushBufferStreamAdapter> streamIter = streams.iterator();

            while (streamIter.hasNext()) {
                PushBufferStreamAdapter stream = streamIter.next();

                streamIter.remove();
                stream.close();
            }
        }
        super.disconnect();
    }

    /**
     * Implements {@link PushBufferDataSource#getStreams()}. Gets the <code>PushBufferStream</code>s
     * through which this <code>PushBufferDataSource</code> gives access to its media data.
     *
     * @return an array of <code>PushBufferStream</code>s through which this
     * <code>PushBufferDataSource</code> gives access to its media data
     */
    @Override
    public PushBufferStream[] getStreams()
    {
        synchronized (streams) {
            PullBufferStream[] dataSourceStreams = dataSource.getStreams();
            int dataSourceStreamCount;

            /*
             * I don't know whether dataSource returns a copy of its internal storage so I'm not
             * sure if it's safe to modify dataSourceStreams.
             */
            if (dataSourceStreams != null) {
                dataSourceStreams = dataSourceStreams.clone();
                dataSourceStreamCount = dataSourceStreams.length;
            }
            else
                dataSourceStreamCount = 0;

            /*
             * Dispose of the PushBufferStreamAdapters which adapt PullBufferStreams which are no
             * longer returned by dataSource.
             */
            Iterator<PushBufferStreamAdapter> streamIter = streams.iterator();

            while (streamIter.hasNext()) {
                PushBufferStreamAdapter streamAdapter = streamIter.next();
                PullBufferStream stream = streamAdapter.stream;
                boolean removeStream = true;

                for (int dataSourceStreamIndex = 0; dataSourceStreamIndex < dataSourceStreamCount;
                     dataSourceStreamIndex++)
                    if (stream == dataSourceStreams[dataSourceStreamIndex]) {
                        removeStream = false;
                        dataSourceStreams[dataSourceStreamIndex] = null;
                        break;
                    }
                if (removeStream) {
                    streamIter.remove();
                    streamAdapter.close();
                }
            }

            /*
             * Create PushBufferStreamAdapters for the PullBufferStreams returned by dataSource
             * which are not adapted yet.
             */
            for (int dataSourceStreamIndex = 0; dataSourceStreamIndex < dataSourceStreamCount;
                 dataSourceStreamIndex++) {
                PullBufferStream dataSourceStream = dataSourceStreams[dataSourceStreamIndex];

                if (dataSourceStream != null) {
                    PushBufferStreamAdapter stream = new PushBufferStreamAdapter(dataSourceStream);

                    streams.add(stream);
                    if (started)
                        stream.start();
                }
            }
            return streams.toArray(EMPTY_STREAMS);
        }
    }

    /**
     * Sets the priority of the <code>streamReadThread</code> of a <code>PushBufferStreamAdapter</code>
     * that adapts a specific <code>PullBufferStream</code> in accord with the <code>Format</code> of the
     * media data.
     *
     * @param stream the <code>PullBufferStream</code> adapted by a <code>PushBufferStreamAdapter</code> that is to
     * have the priority of its <code>streamReadThread</code> set
     */
    private static void setStreamReadThreadPriority(PullBufferStream stream)
    {
        try {
            Format format = stream.getFormat();
            int threadPriority;

            if (format instanceof AudioFormat)
                threadPriority = MediaThread.getAudioPriority();
            else if (format instanceof VideoFormat)
                threadPriority = MediaThread.getVideoPriority();
            else
                return;

            AbstractRenderer.useThreadPriority(threadPriority);
        } catch (Throwable t) {
            if (t instanceof InterruptedException)
                Thread.currentThread().interrupt();
            else if (t instanceof ThreadDeath)
                throw (ThreadDeath) t;

            Timber.w("Failed to set the priority of streamReadThread");
        }
    }

    /**
     * Implements {@link DataSource#start()}. Starts the wrapped <code>PullBufferDataSource</code> and
     * the pushing from the <code>PushBufferStreamAdapter</code>s which wrap the
     * <code>PullBufferStream</code>s of the <code>PullBufferDataSource</code> wrapped by this instance.
     *
     * @throws IOException if anything wrong happens while starting the wrapped <code>PullBufferDataSource</code> or
     * the pushing from the <code>PushBufferStreamAdapter</code>s which wrap the
     * <code>PullBufferStream</code>s of the <code>PullBufferDataSource</code> wrapped by this
     * instance
     */
    @Override
    public void start()
            throws IOException
    {
        super.start();

        synchronized (streams) {
            started = true;

            for (PushBufferStreamAdapter stream : streams)
                stream.start();
        }
    }

    /**
     * Implements {@link DataSource#start()}. Stops the wrapped <code>PullBufferDataSource</code> and
     * the pushing from the <code>PushBufferStreamAdapter</code>s which wrap the
     * <code>PullBufferStream</code>s of the <code>PullBufferDataSource</code> wrapped by this instance.
     *
     * @throws IOException if anything wrong happens while stopping the wrapped <code>PullBufferDataSource</code> or
     * the pushing from the <code>PushBufferStreamAdapter</code>s which wrap the
     * <code>PullBufferStream</code>s of the <code>PullBufferDataSource</code> wrapped by this
     * instance
     */
    @Override
    public void stop()
            throws IOException
    {
        synchronized (streams) {
            started = false;

            for (PushBufferStreamAdapter stream : streams)
                stream.stop();
        }
        super.stop();
    }
}
