/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.protocol.audiosilence;

import org.atalk.impl.neomedia.codec.AbstractCodec2;
import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractPushBufferCaptureDevice;
import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractPushBufferStream;
import org.atalk.impl.neomedia.jmfext.media.renderer.audio.AbstractAudioRenderer;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import javax.media.Buffer;
import javax.media.Format;
import javax.media.MediaLocator;
import javax.media.control.FormatControl;
import javax.media.format.AudioFormat;
import javax.media.protocol.BufferTransferHandler;

import timber.log.Timber;

/**
 * Implements a <code>CaptureDevice</code> which provides silence in the form of audio media.
 *
 * @author Lyubomir Marinov
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class DataSource extends AbstractPushBufferCaptureDevice
{
    /**
     * The compile-time flag which determines whether <code>AudioSilenceCaptureDevice</code> and, more
     * specifically, <code>AudioSilenceStream</code> are to be used by <code>AudioMixer</code> for the mere
     * purposes of ticking the clock which makes <code>AudioMixer</code> read media from its inputs, mix
     * it, and write it to its outputs. The preferred value is <code>true</code> because it causes the
     * <code>AudioMixer</code> to not push media unless at least one <code>Channel</code> is receiving
     * actual media.
     */
    private static final boolean CLOCK_ONLY = true;

    /**
     * The interval of time in milliseconds between two consecutive ticks of the clock used by
     * <code>AudioSilenceCaptureDevice</code> and, more specifically, <code>AudioSilenceStream</code>.
     */
    private static final long CLOCK_TICK_INTERVAL = 20;

    /**
     * The optional {@link MediaLocator} remainder to indicate to
     * {@code DataSource} and its associated {@code AudioSilenceStream} that
     * {@code BufferTransferHandler.transferData(PushBufferStream)} is to not be
     * invoked. If {@code true}, then the {@code DataSource} is a dummy suitable
     * for scenarios in which an capature device is required but no audio
     * samples from it are necessary such as negotiating signaling for audio but
     * actually RTP translating other participants/peers' audio.
     */
    public static final String NO_TRANSFER_DATA = "noTransferData";

    /**
     * The list of <code>Format</code>s supported by the <code>AudioSilenceCaptureDevice</code> instances.
     */
    public static final Format[] SUPPORTED_FORMATS = new Format[]{
            new AudioFormat(
                    AudioFormat.LINEAR,
                    48000,
                    16,
                    1,
                    AudioFormat.LITTLE_ENDIAN,
                    AudioFormat.SIGNED,
                    Format.NOT_SPECIFIED,
                    Format.NOT_SPECIFIED,
                    Format.byteArray)
    };

    /**
     * {@inheritDoc}
     *
     * Implements {@link AbstractPushBufferCaptureDevice#createStream(int, FormatControl)}.
     */
    @Override
    protected AudioSilenceStream createStream(int streamIndex, FormatControl formatControl)
    {
        return new AudioSilenceStream(
                this,
                formatControl,
                !NO_TRANSFER_DATA.equalsIgnoreCase(getLocator().getRemainder()));
    }

    /**
     * {@inheritDoc}
     *
     * Overrides the super implementation in order to return the list of <code>Format</code>s hardcoded
     * as supported in <code>AudioSilenceCaptureDevice</code> because the super looks them up by
     * <code>CaptureDeviceInfo</code> and this instance does not have one.
     */
    @Override
    protected Format[] getSupportedFormats(int streamIndex)
    {
        return SUPPORTED_FORMATS.clone();
    }

    /**
     * Implements a <code>PushBufferStream</code> which provides silence in the form of audio media.
     */
    private static class AudioSilenceStream extends AbstractPushBufferStream<DataSource>
            implements Runnable
    {
        /**
         * The indicator which determines whether {@link #start()} has been invoked on this instance
         * without an intervening {@link #stop()}.
         */
        private boolean started;

        /**
         * The <code>Thread</code> which pushes available media data out of this instance to its
         * consumer i.e. <code>BufferTransferHandler</code>.
         */
        private Thread thread;

        /**
         * The indicator which determined whether this instance is to invoke
         * {@code BufferTransferHandler.transferData(PushBufferStream)} and,
         * thus, tick the media clock that it represents. If {@code false}, then
         * it is a dummy suitable for scenarios in which an audio capture device
         * is required but no audio samples from it are necessary such as
         * negotiating signaling for audio but actually RTP translating other
         * participants/peers' audio.
         */
        private final boolean transferData;

        /**
         * Initializes a new <code>AudioSilenceStream</code> which is to be exposed
         * by a specific <code>AudioSilenceCaptureDevice</code> and which is to have
         * its <code>Format</code>-related information abstracted by a specific <code>FormatControl</code>.
         *
         * @param dataSource the <code>AudioSilenceCaptureDevice</code> which is
         * initializing the new instance and which is to expose it in its array
         * of <code>PushBufferStream</code>s
         * @param formatControl the <code>FormatControl</code> which is to abstract
         * the <code>Format</code>-related information of the new instance
         * @param transferData {@code true} if the new instance is to invoke
         * {@code BufferTransferHandler.transferData(PushBufferStream)} and,
         * thus, tick the media clock that it represents; otherwise, {@code false}.
         */
        public AudioSilenceStream(DataSource dataSource, FormatControl formatControl, boolean transferData)
        {
            super(dataSource, formatControl);
            this.transferData = transferData;
        }

        /**
         * Reads available media data from this instance into a specific <code>Buffer</code>.
         *
         * @param buffer the <code>Buffer</code> to write the available media data into
         * @throws IOException if an I/O error has prevented the reading of available media data from this
         * instance into the specified <code>buffer</code>
         */
        @Override
        public void read(Buffer buffer)
                throws IOException
        {
            if (CLOCK_ONLY) {
                buffer.setLength(0);
            }
            else {
                AudioFormat format = (AudioFormat) getFormat();
                int frameSizeInBytes = format.getChannels() * (((int) format.getSampleRate()) / 50)
                        * (format.getSampleSizeInBits() / 8);

                byte[] data = AbstractCodec2.validateByteArraySize(buffer, frameSizeInBytes, false);
                Arrays.fill(data, 0, frameSizeInBytes, (byte) 0);

                buffer.setFormat(format);
                buffer.setLength(frameSizeInBytes);
                buffer.setOffset(0);
            }
        }

        /**
         * Runs in {@link #thread} and pushes available media data out of this instance to its
         * consumer i.e. <code>BufferTransferHandler</code>.
         */
        @Override
        public void run()
        {
            try {
                /*
                 * Make sure that the current thread which implements the actual ticking of the
                 * clock implemented by this instance uses a thread priority considered appropriate
                 * for audio processing.
                 */
                AbstractAudioRenderer.useAudioThreadPriority();

                /*
                 * The method implements a clock which ticks at a certain and regular interval of
                 * time which is not affected by the duration of the execution of, for example, the
                 * invocation of BufferTransferHandler.transferData(PushBufferStream).
                 *
                 * XXX The implementation utilizes System.currentTimeMillis() and, consequently, may
                 * be broken by run-time adjustments to the system time.
                 */
                long tickTime = System.currentTimeMillis();

                while (true) {
                    long sleepInterval = tickTime - System.currentTimeMillis();
                    boolean tick = (sleepInterval <= 0);

                    if (tick) {
                        /*
                         * The current thread has woken up just in time or too late for the next
                         * scheduled clock tick and, consequently, the clock should tick right now.
                         */
                        tickTime += CLOCK_TICK_INTERVAL;
                    }
                    else {
                        /*
                         * The current thread has woken up too early for the next scheduled clock
                         * tick and, consequently, it should sleep until the time of the next
                         * scheduled clock tick comes.
                         */
                        try {
                            Thread.sleep(sleepInterval);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                        /*
                         * The clock will not tick and spurious wakeups will be handled. However,
                         * the current thread will first check whether it is still utilized by this
                         * AudioSilenceStream in order to not delay stop requests.
                         */
                    }

                    synchronized (this) {
                        /*
                         * If the current Thread is no longer utilized by this AudioSilenceStream,
                         * it no longer has the right to touch it. If this AudioSilenceStream has
                         * been stopped, the current Thread should stop as well.
                         */
                        if ((thread != Thread.currentThread()) || !started)
                            break;
                    }

                    if (tick) {
                        BufferTransferHandler transferHandler = this.transferHandler;

                        if (transferHandler != null) {
                            try {
                                transferHandler.transferData(this);
                            } catch (Throwable t) {
                                if (t instanceof ThreadDeath)
                                    throw (ThreadDeath) t;
                                else {
                                    Timber.e("%s", t.getMessage());
                                }
                            }
                        }
                    }
                }
            } finally {
                synchronized (this) {
                    if (thread == Thread.currentThread()) {
                        thread = null;
                        started = false;
                        notifyAll();
                    }
                }
            }
        }

        /**
         * Starts the transfer of media data from this instance.
         *
         * @throws IOException if an error has prevented the start of the transfer of media from this instance
         */
        @Override
        public synchronized void start()
                throws IOException
        {
            if (!transferData) {
                // Skip creating the thread that will invoke transferData.
                this.started = true;
                return;
            }

            if (thread == null) {
                String className = getClass().getName();

                thread = new Thread(this, className);
                thread.setDaemon(true);
                boolean started = false;

                try {
                    thread.start();
                    started = true;
                } finally {
                    this.started = started;
                    if (!started) {
                        thread = null;
                        notifyAll();
                        throw new IOException("Failed to start " + className);
                    }
                }
            }
        }

        /**
         * Stops the transfer of media data from this instance.
         *
         * @throws IOException if an error has prevented the stopping of the transfer of media from this instance
         */
        @Override
        public synchronized void stop()
                throws IOException
        {
            this.started = false;
            notifyAll();

            boolean interrupted = false;

            // a workaround for an issue we see where we cannot stop this stream as the thread waiting to
            // transfer data is waiting for data that never comes. So we timeout after short period and
            // we interrupt the thread to clean it
            long WAIT_TIMEOUT = 100; // ms.
            boolean waited = false;
            long started = System.nanoTime();
            while (thread != null) {
                if (waited) {
                    // our stop had timed out, so let's interrupt the thread
                    this.thread.interrupt();
                    break;
                }
                try {
                    wait(WAIT_TIMEOUT);
                    waited = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) >= WAIT_TIMEOUT;
                } catch (InterruptedException ie) {
                    interrupted = true;
                }
            }
            if (interrupted)
                Thread.currentThread().interrupt();
        }
    }
}
