/*
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.impl.neomedia.device;

import org.atalk.impl.neomedia.codec.AbstractCodec2;
import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractPushBufferCaptureDevice;
import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractPushBufferStream;
import org.atalk.impl.neomedia.jmfext.media.renderer.audio.AbstractAudioRenderer;

import java.io.IOException;
import java.util.Arrays;

import javax.media.Buffer;
import javax.media.Format;
import javax.media.control.FormatControl;
import javax.media.format.AudioFormat;
import javax.media.protocol.BufferTransferHandler;

/**
 * Implements a <code>CaptureDevice</code> which provides silence in the form of
 * audio media.
 *
 * @author Lyubomir Marinov
 * @author Boris Grozev
 */
class AudioSilenceCaptureDevice
    extends AbstractPushBufferCaptureDevice
{

    /**
     * The interval of time in milliseconds between two consecutive ticks of the
     * clock used by <code>AudioSilenceCaptureDevice</code> and, more specifically,
     * <code>AudioSilenceStream</code>.
     */
    private static final long CLOCK_TICK_INTERVAL = 20;

    /**
     * The list of <code>Format</code>s supported by the
     * <code>AudioSilenceCaptureDevice</code> instances.
     */
    private static final Format[] SUPPORTED_FORMATS
        = new Format[]
                {
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
     * The flag which determines whether <code>AudioSilenceCaptureDevice</code> and,
     * more specifically, <code>AudioSilenceStream</code> are to be used by
     * <code>AudioMixer</code> for the mere purposes of ticking the clock which
     * makes <code>AudioMixer</code> read media from its inputs, mix it, and write
     * it to its outputs. The preferred value is <code>true</code> because it
     * causes the <code>AudioMixer</code> to not push media unless at least one
     * <code>Channel</code> is receiving actual media.
     */
    private final boolean clockOnly;

    /**
     * Initializes a new {@link AudioSilenceCaptureDevice}.
     * @param clockOnly whether the {@link
     * AudioSilenceCaptureDevice.AudioSilenceStream}s created by this instance
     * are to be used only for the purpose of ticking the clock which makes
     * {@link org.atalk.impl.neomedia.conference.AudioMixer} run.
     */
    AudioSilenceCaptureDevice(boolean clockOnly)
    {
        this.clockOnly = clockOnly;
    }

    /**
     * {@inheritDoc}
     *
     * Implements
     * {@link AbstractPushBufferCaptureDevice#createStream(int, FormatControl)}.
     */
    @Override
    protected AudioSilenceStream createStream(
            int streamIndex,
            FormatControl formatControl)
    {
        return new AudioSilenceStream(this, formatControl, clockOnly);
    }

    /**
     * {@inheritDoc}
     *
     * Overrides the super implementation in order to return the list of
     * <code>Format</code>s hardcoded as supported in
     * <code>AudioSilenceCaptureDevice</code> because the super looks them up by
     * <code>CaptureDeviceInfo</code> and this instance does not have one.
     */
    @Override
    protected Format[] getSupportedFormats(int streamIndex)
    {
        return SUPPORTED_FORMATS.clone();
    }

    /**
     * Implements a <code>PushBufferStream</code> which provides silence in the form
     * of audio media.
     */
    private static class AudioSilenceStream
        extends AbstractPushBufferStream<AudioSilenceCaptureDevice>
        implements Runnable
    {
        /**
         * The indicator which determines whether {@link #start()} has been
         * invoked on this instance without an intervening {@link #stop()}.
         */
        private boolean started;

        /**
         * The <code>Thread</code> which pushes available media data out of this
         * instance to its consumer i.e. <code>BufferTransferHandler</code>.
         */
        private Thread thread;

        /**
         * The flag which determines whether this {@code AudioSilenceStream}
         * is to be used by <code>AudioMixer</code> for the mere purposes of ticking
         * the clock which makes <code>AudioMixer</code> read media from its inputs,
         * mix it, and write it to its outputs.
         */
        private final boolean clockOnly;

        /**
         * Initializes a new <code>AudioSilenceStream</code> which is to be exposed
         * by a specific <code>AudioSilenceCaptureDevice</code> and which is to have
         * its <code>Format</code>-related information abstracted by a specific
         * <code>FormatControl</code>.
         *
         * @param dataSource the <code>AudioSilenceCaptureDevice</code> which is
         * initializing the new instance and which is to expose it in its array
         * of <code>PushBufferStream</code>s
         * @param formatControl the <code>FormatControl</code> which is to abstract
         * the <code>Format</code>-related information of the new instance
         */
        public AudioSilenceStream(
                AudioSilenceCaptureDevice dataSource,
                FormatControl formatControl,
                boolean clockOnly)
        {
            super(dataSource, formatControl);
            this.clockOnly = clockOnly;
        }

        /**
         * Reads available media data from this instance into a specific
         * <code>Buffer</code>.
         *
         * @param buffer the <code>Buffer</code> to write the available media data
         * into
         * @throws IOException if an I/O error has prevented the reading of
         * available media data from this instance into the specified
         * <code>buffer</code>
         */
        @Override
        public void read(Buffer buffer)
            throws IOException
        {
            if (clockOnly)
            {
                buffer.setLength(0);
            }
            else
            {
                AudioFormat format = (AudioFormat) getFormat();
                int frameSizeInBytes
                    = format.getChannels()
                        * (((int) format.getSampleRate()) / 50)
                        * (format.getSampleSizeInBits() / 8);

                byte[] data
                    = AbstractCodec2.validateByteArraySize(
                            buffer,
                            frameSizeInBytes,
                            false);

                Arrays.fill(data, 0, frameSizeInBytes, (byte) 0);

                buffer.setFormat(format);
                buffer.setLength(frameSizeInBytes);
                buffer.setOffset(0);
            }
        }

        /**
         * Runs in {@link #thread} and pushes available media data out of this
         * instance to its consumer i.e. <code>BufferTransferHandler</code>.
         */
        @Override
        public void run()
        {
            try
            {
                /*
                 * Make sure that the current thread which implements the actual
                 * ticking of the clock implemented by this instance uses a
                 * thread priority considered appropriate for audio processing.
                 */
                AbstractAudioRenderer.useAudioThreadPriority();

                /*
                 * The method implements a clock which ticks at a certain and
                 * regular interval of time which is not affected by the
                 * duration of the execution of, for example, the invocation of
                 * BufferTransferHandler.transferData(PushBufferStream).
                 *
                 * XXX The implementation utilizes System.currentTimeMillis()
                 * and, consequently, may be broken by run-time adjustments to
                 * the system time. 
                 */
                long tickTime = System.currentTimeMillis();

                while (true)
                {
                    long sleepInterval = tickTime - System.currentTimeMillis();
                    boolean tick = (sleepInterval <= 0);

                    if (tick)
                    {
                        /*
                         * The current thread has woken up just in time or too
                         * late for the next scheduled clock tick and,
                         * consequently, the clock should tick right now.
                         */
                        tickTime += CLOCK_TICK_INTERVAL;
                    }
                    else
                    {
                        /*
                         * The current thread has woken up too early for the
                         * next scheduled clock tick and, consequently, it
                         * should sleep until the time of the next scheduled
                         * clock tick comes.
                         */
                        try
                        {
                            Thread.sleep(sleepInterval);
                        }
                        catch (InterruptedException ie)
                        {
                        }
                        /*
                         * The clock will not tick and spurious wakeups will be
                         * handled. However, the current thread will first check
                         * whether it is still utilized by this
                         * AudioSilenceStream in order to not delay stop
                         * requests.
                         */
                    }

                    synchronized (this)
                    {
                        /*
                         * If the current Thread is no longer utilized by this
                         * AudioSilenceStream, it no longer has the right to
                         * touch it. If this AudioSilenceStream has been
                         * stopped, the current Thread should stop as well. 
                         */
                        if ((thread != Thread.currentThread()) || !started)
                            break;
                    }

                    if (tick)
                    {
                        BufferTransferHandler transferHandler
                            = this.transferHandler;

                        if (transferHandler != null)
                        {
                            try
                            {
                                transferHandler.transferData(this);
                            }
                            catch (Throwable t)
                            {
                                if (t instanceof ThreadDeath)
                                    throw (ThreadDeath) t;
                                else
                                {
                                    // TODO Auto-generated method stub
                                }
                            }
                        }
                    }
                }
            }
            finally
            {
                synchronized (this)
                {
                    if (thread == Thread.currentThread())
                    {
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
         * @throws IOException if an error has prevented the start of the
         * transfer of media from this instance
         */
        @Override
        public synchronized void start()
            throws IOException
        {
            if (thread == null)
            {
                String className = getClass().getName();

                thread = new Thread(this, className);
                thread.setDaemon(true);

                boolean started = false;

                try
                {
                    thread.start();
                    started = true;
                }
                finally
                {
                    this.started = started;
                    if (!started)
                    {
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
         * @throws IOException if an error has prevented the stopping of the
         * transfer of media from this instance
         */
        @Override
        public synchronized void stop()
            throws IOException
        {
            this.started = false;
            notifyAll();

            boolean interrupted = false;

            while (thread != null)
            {
                try
                {
                    wait();
                }
                catch (InterruptedException ie)
                {
                    interrupted = true;
                }
            }
            if (interrupted)
                Thread.currentThread().interrupt();
        }
    }
}
