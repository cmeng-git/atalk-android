/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.protocol.opensles;

import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractBufferStream;
import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractPullBufferCaptureDevice;
import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractPullBufferStream;

import java.io.IOException;

import javax.media.Buffer;
import javax.media.Format;
import javax.media.MediaLocator;
import javax.media.control.FormatControl;
import javax.media.format.AudioFormat;

/**
 * Implements an audio <code>CaptureDevice</code> using OpenSL ES.
 *
 * @author Lyubomir Marinov
 */
public class DataSource extends AbstractPullBufferCaptureDevice {
    static {
        System.loadLibrary("jnopensles");
    }

    /**
     * Initializes a new <code>DataSource</code> instance.
     */
    public DataSource() {
    }

    /**
     * Initializes a new <code>DataSource</code> from a specific <code>MediaLocator</code>.
     *
     * @param locator the <code>MediaLocator</code> to create the new instance from
     */
    public DataSource(MediaLocator locator) {
        super(locator);
    }

    private static native long connect(String encoding, double sampleRate, int sampleSizeInBits,
            int channels, int endian, int signed, Class<?> dataType)
            throws IOException;

    /**
     * Creates a new <code>PullBufferStream</code> which is to be at a specific zero-based index in the
     * list of streams of this <code>PullBufferDataSource</code>. The <code>Format</code>-related
     * information of the new instance is to be abstracted by a specific <code>FormatControl</code>.
     *
     * @param streamIndex the zero-based index of the <code>PullBufferStream</code> in the list of streams of this
     * <code>PullBufferDataSource</code>
     * @param formatControl the <code>FormatControl</code> which is to abstract the <code>Format</code>-related
     * information of the new instance
     *
     * @return a new <code>PullBufferStream</code> which is to be at the specified <code>streamIndex</code>
     * in the list of streams of this <code>PullBufferDataSource</code> and which has its
     * <code>Format</code>-related information abstracted by the specified
     * <code>formatControl</code>
     * @see AbstractPullBufferCaptureDevice#createStream(int, FormatControl)
     */
    protected AbstractPullBufferStream createStream(int streamIndex, FormatControl formatControl) {
        return new OpenSLESStream(this, formatControl);
    }

    private static native void disconnect(long ptr);

    /**
     * Opens a connection to the media source specified by the <code>MediaLocator</code> of this
     * <code>DataSource</code>.
     *
     * @throws IOException if anything goes wrong while opening the connection to the media source specified by
     * the <code>MediaLocator</code> of this <code>DataSource</code>
     * @see AbstractPullBufferCaptureDevice#doConnect()
     */
    @Override
    protected void doConnect()
            throws IOException {
        super.doConnect();

        /*
         * XXX The OpenSLESStream will connect upon start in order to be able to respect requests to set its format.
         */
    }

    /**
     * Closes the connection to the media source specified by the <code>MediaLocator</code> of this <code>DataSource</code>.
     *
     * @see AbstractPullBufferCaptureDevice#doDisconnect()
     */
    @Override
    protected void doDisconnect() {
        synchronized (getStreamSyncRoot()) {
            Object[] streams = streams();

            if (streams != null)
                for (Object stream : streams)
                    ((OpenSLESStream) stream).disconnect();
        }

        super.doDisconnect();
    }

    private static native int read(long ptr, Object data, int offset, int length)
            throws IOException;

    /**
     * Attempts to set the <code>Format</code> to be reported by the <code>FormatControl</code> of a
     * <code>PullBufferStream</code> at a specific zero-based index in the list of streams of this
     * <code>PullBufferDataSource</code>. The <code>PullBufferStream</code> does not exist at the time of
     * the attempt to set its <code>Format</code>. Allows extenders to override the default behavior
     * which is to not attempt to set the specified <code>Format</code> so that they can enable setting
     * the <code>Format</code> prior to creating the <code>PullBufferStream</code>. If setting the
     * <code>Format</code> of an existing <code>PullBufferStream</code> is desired,
     * <code>AbstractPullBufferStream#doSetFormat(Format)</code> should be overridden instead.
     *
     * @param streamIndex the zero-based index of the <code>PullBufferStream</code> the <code>Format</code>
     * of which is to be set
     * @param oldValue the last-known <code>Format</code> for the <code>PullBufferStream</code> at the specified
     * <code>streamIndex</code>
     * @param newValue the <code>Format</code> which is to be set
     *
     * @return the <code>Format</code> to be reported by the <code>FormatControl</code> of the
     * <code>PullBufferStream</code> at the specified <code>streamIndex</code> in the list of
     * streams of this <code>PullBufferStream</code> or <code>null</code> if the attempt to set the
     * <code>Format</code> did not success and any last-known <code>Format</code> is to be left in effect
     * @see AbstractPullBufferCaptureDevice#setFormat(int, Format, Format)
     */
    @Override
    protected Format setFormat(int streamIndex, Format oldValue, Format newValue) {
        /*
         * Accept format specifications prior to the initialization of OpenSLESStream. Afterwards,
         * OpenSLESStream will decide whether to accept further format specifications.
         */
        return newValue;
    }

    private static native void start(long ptr)
            throws IOException;

    private static native void stop(long ptr)
            throws IOException;

    /**
     * Implements <code>PullBufferStream</code> using OpenSL ES.
     */
    private static class OpenSLESStream extends AbstractPullBufferStream {
        private int length;

        private long ptr;

        /**
         * The indicator which determines whether this <code>OpenSLESStream</code> is to set the
         * priority of the thread in which its {@link #read(Buffer)} method is executed.
         */
        private boolean setThreadPriority = true;

        /**
         * The indicator which determines whether this <code>OpenSLESStream</code> is started i.e.
         * whether {@link javax.media.protocol.PullBufferStream#read(javax.media.Buffer)} should
         * really attempt to read from {@link #ptr}.
         */
        private boolean started;

        /**
         * Initializes a new <code>OpenSLESStream</code> instance which is to have its <code>Format</code>
         * -related information abstracted by a specific <code>FormatControl</code>.
         *
         * @param dataSource the <code>DataSource</code> which is creating the new instance so that it becomes one
         * of its <code>streams</code>
         * @param formatControl the <code>FormatControl</code> which is to abstract the <code>Format</code>-related
         * information of the new instance
         */
        public OpenSLESStream(DataSource dataSource, FormatControl formatControl) {
            super(dataSource, formatControl);
        }

        /**
         * Opens a connection to the media source of the associated <code>DataSource</code>.
         *
         * @throws IOException if anything goes wrong while opening a connection to the media source of the
         * associated <code>DataSource</code>
         */
        public synchronized void connect()
                throws IOException {
            if (ptr == 0) {
                AudioFormat format = (AudioFormat) getFormat();
                double sampleRate = format.getSampleRate();
                int sampleSizeInBits = format.getSampleSizeInBits();
                int channels = format.getChannels();

                if (channels == Format.NOT_SPECIFIED)
                    channels = 1;

                /*
                 * Apart from the thread in which #read(Buffer) is executed, use the thread priority
                 * for the thread which will create the OpenSL ES Audio Recorder.
                 */
                org.atalk.impl.neomedia.jmfext.media.protocol.audiorecord.DataSource.setThreadPriority();
                ptr = DataSource.connect(format.getEncoding(), sampleRate, sampleSizeInBits,
                        channels, format.getEndian(), format.getSigned(), format.getDataType());
                if (ptr == 0)
                    throw new IOException();
                else {
                    length = (int) (20 /* milliseconds */
                            * (sampleRate / 1000) * channels * (sampleSizeInBits / 8));
                    setThreadPriority = true;
                }
            }
        }

        /**
         * Closes the connection to the media source of the associated <code>DataSource</code>.
         */
        public synchronized void disconnect() {
            if (ptr != 0) {
                DataSource.disconnect(ptr);
                ptr = 0;
                setThreadPriority = true;
            }
        }

        /**
         * Reads media data from this <code>PullBufferStream</code> into a specific <code>Buffer</code> with blocking.
         *
         * @param buffer the <code>Buffer</code> in which media data is to be read from this <code>PullBufferStream</code>
         *
         * @throws IOException if anything goes wrong while reading media data from this
         * <code>PullBufferStream</code> into the specified <code>buffer</code>
         * @see javax.media.protocol.PullBufferStream#read(Buffer)
         */
        public void read(Buffer buffer)
                throws IOException {
            if (setThreadPriority) {
                setThreadPriority = false;
                org.atalk.impl.neomedia.jmfext.media.protocol.audiorecord.DataSource.setThreadPriority();
            }

            Object data = buffer.getData();
            int length = this.length;

            if (data instanceof byte[]) {
                if (((byte[]) data).length < length)
                    data = null;
            }
            else
                data = null;
            if (data == null) {
                data = new byte[length];
                buffer.setData(data);
            }

            int read = 0;
            int offset = 0;

            while (read < 1) {
                synchronized (this) {
                    if (started) {
                        if (ptr == 0)
                            throw new IOException("ptr");
                        else
                            read = DataSource.read(ptr, data, offset, length);
                    }
                    else
                        break;
                }
            }
            length = read;

            buffer.setLength(length);
            buffer.setOffset(offset);
        }

        /**
         * Starts the transfer of media data from this <code>AbstractBufferStream</code>.
         *
         * @throws IOException if anything goes wrong while starting the transfer of media data from this
         * <code>AbstractBufferStream</code>
         * @see AbstractBufferStream#start()
         */
        @Override
        public void start()
                throws IOException {
            /*
             * Connect upon start because the connect has been delayed to allow this OpenSLESStream
             * to respect requests to set its format.
             */
            synchronized (this) {
                if (ptr == 0)
                    connect();
            }

            super.start();

            synchronized (this) {
                if (ptr != 0) {
                    setThreadPriority = true;
                    DataSource.start(ptr);
                    started = true;
                }
            }
        }

        /**
         * Stops the transfer of media data from this <code>AbstractBufferStream</code>.
         *
         * @throws IOException if anything goes wrong while stopping the transfer of media data from this
         * <code>AbstractBufferStream</code>
         * @see AbstractBufferStream#stop()
         */
        @Override
        public void stop()
                throws IOException {
            synchronized (this) {
                if (ptr != 0) {
                    DataSource.stop(ptr);
                    setThreadPriority = true;
                    started = false;
                }
            }
            super.stop();
        }
    }
}
