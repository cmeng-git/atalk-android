/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.renderer.audio;

import org.atalk.impl.neomedia.MediaUtils;
import org.atalk.impl.neomedia.device.AudioSystem;
import org.atalk.impl.neomedia.device.PulseAudioSystem;
import org.atalk.impl.neomedia.pulseaudio.PA;
import org.atalk.service.neomedia.BasicVolumeControl;

import java.beans.PropertyChangeEvent;
import java.io.IOException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.Objects;

import javax.media.Buffer;
import javax.media.Format;
import javax.media.GainControl;
import javax.media.MediaLocator;
import javax.media.PlugIn;
import javax.media.ResourceUnavailableException;
import javax.media.format.AudioFormat;

/**
 * Implements an audio <code>Renderer</code> which uses PulseAudio.
 *
 * @author Lyubomir Marinov
 */
public class PulseAudioRenderer extends AbstractAudioRenderer<PulseAudioSystem>
{
    /**
     * The human-readable <code>PlugIn</code> name of the <code>PulseAudioRenderer</code> instances.
     */
    private static final String PLUGIN_NAME = "PulseAudio Renderer";

    /*
     * FIXME The control of the volume through the native PulseAudio API has been reported to
     * maximize the system-wide volume of the source with flat volumes i.e.
     * https://java.net/jira/browse/JITSI-1050 (Pulseaudio changes volume to maximum values).
     */
    private static final boolean SOFTWARE_GAIN = true;

    /**
     * The list of JMF <code>Format</code>s of audio data which <code>PulseAudioRenderer</code> instances
     * are capable of rendering.
     */
    private static final Format[] SUPPORTED_INPUT_FORMATS = new Format[]{
            new AudioFormat(
                    AudioFormat.LINEAR,
                    Format.NOT_SPECIFIED /* sampleRate */,
                    16,
                    Format.NOT_SPECIFIED /* channels */,
                    AudioFormat.LITTLE_ENDIAN,
                    AudioFormat.SIGNED,
                    Format.NOT_SPECIFIED /* frameSizeInBits */,
                    Format.NOT_SPECIFIED /* frameRate */,
                    Format.byteArray)
    };

    /**
     * The number of channels of audio data this <code>PulseAudioRenderer</code> is configured to render.
     */
    private int channels;

    /**
     * The indicator which determines whether {@link #stream}'s playback is paused or resumed.
     */
    private boolean corked = true;

    /**
     * The <code>pa_cvolume</code> (structure) instance used by this <code>PulseAudioRenderer</code> to set
     * the per-channel volume of {@link #stream}.
     */
    private long cvolume;

    /**
     * The name of the sink {@link #stream} is connected to.
     */
    private String dev;

    /**
     * The level of the volume specified by the <code>GainControl</code> associated with this
     * <code>PulseAudioRenderer</code> which has been applied to {@link #stream}.
     */
    private float gainControlLevel;

    /**
     * The PulseAudio logic role of the media played back by this <code>PulseAudioRenderer</code>.
     */
    private final String mediaRole;

    /**
     * The PulseAudio stream which performs the actual rendering of audio data for this <code>PulseAudioRenderer</code>.
     */
    private long stream;

    /**
     * The PulseAudio callback which notifies this <code>PulseAudioRenderer</code> that {@link #stream}
     * requests audio data to play back.
     */
    private final PA.stream_request_cb_t writeCb = new PA.stream_request_cb_t()
    {
        @Override
        public void callback(long s, int nbytes)
        {
            audioSystem.signalMainloop(false);
        }
    };

    /**
     * Initializes a new <code>PulseAudioRenderer</code> instance with a default PulseAudio media role.
     */
    public PulseAudioRenderer()
    {
        this(null);
    }

    /**
     * Initializes a new <code>PulseAudioRenderer</code> instance with a specific PulseAudio media
     * role.
     *
     * @param mediaRole the PulseAudio media role to initialize the new instance with
     */
    public PulseAudioRenderer(String mediaRole)
    {
        super(
                PulseAudioSystem.getPulseAudioSystem(),
                ((mediaRole == null)
                        || PulseAudioSystem.MEDIA_ROLE_PHONE.equals(mediaRole))
                        ? AudioSystem.DataFlow.PLAYBACK
                        : AudioSystem.DataFlow.NOTIFY);

        if (audioSystem == null)
            throw new IllegalStateException("audioSystem");

        this.mediaRole = (mediaRole == null) ? PulseAudioSystem.MEDIA_ROLE_PHONE : mediaRole;
    }

    /**
     * Applies the volume specified by a specific <code>GainControl</code> on a specified sample of audio <code>data</code>.
     *
     * @param gainControl the <code>GainControl</code> which specifies the volume to set on <code>data</code>
     * @param data the audio data to set the volume specified by <code>gainControl</code> on
     * @param offset the offset in <code>data</code> at which the valid audio data begins
     * @param length the number of bytes of valid audio data in <code>data</code> beginning at <code>offset</code>
     */
    @SuppressWarnings("unused")
    private void applyGain(GainControl gainControl, byte[] data, int offset, int length)
    {
        if (SOFTWARE_GAIN || (cvolume == 0)) {
            if (length > 0)
                BasicVolumeControl.applyGain(gainControl, data, offset, length);
        }
        else {
            float gainControlLevel = gainControl.getLevel();

            if (this.gainControlLevel != gainControlLevel) {
                this.gainControlLevel = gainControlLevel;
                setStreamVolume(stream, gainControlLevel);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close()
    {
        audioSystem.lockMainloop();
        try {
            long stream = this.stream;

            if (stream != 0) {
                try {
                    stopWithMainloopLock();
                } finally {
                    long cvolume = this.cvolume;

                    this.cvolume = 0;
                    this.stream = 0;

                    corked = true;
                    dev = null;

                    audioSystem.signalMainloop(false);

                    if (cvolume != 0)
                        PA.cvolume_free(cvolume);
                    PA.stream_disconnect(stream);
                    PA.stream_unref(stream);
                }
            }

            super.close();
        } finally {
            audioSystem.unlockMainloop();
        }
    }

    /**
     * Pauses or resumes the playback of audio data through {@link #stream}.
     *
     * @param b <code>true</code> to pause the playback of audio data or <code>false</code> to resume it
     */
    private void cork(boolean b)
    {
        try {
            PulseAudioSystem.corkStream(stream, b);
            corked = b;
        } catch (IOException ioe) {
            throw new UndeclaredThrowableException(ioe);
        } finally {
            audioSystem.signalMainloop(false);
        }
    }

    /**
     * Returns the name of the sink this <code>PulseAudioRenderer</code> is configured to connect
     * {@link #stream} to.
     *
     * @return the name of the sink this <code>PulseAudioRenderer</code> is configured to connect
     * {@link #stream} to
     */
    private String getLocatorDev()
    {
        MediaLocator locator = getLocator();
        String locatorDev;

        if (locator == null) {
            locatorDev = null;
        }
        else {
            locatorDev = locator.getRemainder();
            if ((locatorDev != null) && (locatorDev.length() <= 0))
                locatorDev = null;
        }
        return locatorDev;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName()
    {
        return PLUGIN_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Format[] getSupportedInputFormats()
    {
        return SUPPORTED_INPUT_FORMATS.clone();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void open()
            throws ResourceUnavailableException
    {
        audioSystem.lockMainloop();
        try {
            openWithMainloopLock();

            super.open();
        } finally {
            audioSystem.unlockMainloop();
        }
    }

    /**
     * Opens this <code>PulseAudioRenderer</code> i.e. initializes the PulseAudio stream which is to
     * play audio data back. The method executes with the assumption that the PulseAudio event loop
     * object is locked by the executing thread.
     *
     * @throws ResourceUnavailableException if the opening of this <code>PulseAudioRenderer</code> failed
     */
    private void openWithMainloopLock()
            throws ResourceUnavailableException
    {
        if (stream != 0)
            return;

        AudioFormat format = this.inputFormat;
        int sampleRate = (int) format.getSampleRate();
        int channels = format.getChannels();
        int sampleSizeInBits = format.getSampleSizeInBits();

        if ((sampleRate == Format.NOT_SPECIFIED)
                && (MediaUtils.MAX_AUDIO_SAMPLE_RATE != Format.NOT_SPECIFIED))
            sampleRate = (int) MediaUtils.MAX_AUDIO_SAMPLE_RATE;
        if (channels == Format.NOT_SPECIFIED)
            channels = 1;
        if (sampleSizeInBits == Format.NOT_SPECIFIED)
            sampleSizeInBits = 16;

        long stream = 0;
        Throwable exception = null;

        try {
            stream = audioSystem.createStream(sampleRate, channels,
                    getClass().getName(), mediaRole);
            this.channels = channels;
        } catch (IllegalStateException ise) {
            exception = ise;
        } catch (RuntimeException re) {
            exception = re;
        }
        if (exception != null) {
            ResourceUnavailableException rue = new ResourceUnavailableException();

            rue.initCause(exception);
            throw rue;
        }
        if (stream == 0)
            throw new ResourceUnavailableException("stream");

        try {
            long attr = PA.buffer_attr_new(-1, 2 /* millis / 10 */
                    * (sampleRate / 100) * channels * (sampleSizeInBits / 8), -1, -1, -1);

            if (attr == 0)
                throw new ResourceUnavailableException("pa_buffer_attr_new");

            try {
                Runnable stateCallback = () -> audioSystem.signalMainloop(false);

                PA.stream_set_state_callback(stream, stateCallback);
                String dev = getLocatorDev();
                PA.stream_connect_playback(stream, dev, attr,
                        PA.STREAM_ADJUST_LATENCY | PA.STREAM_START_CORKED, 0, 0);

                try {
                    if (attr != 0) {
                        PA.buffer_attr_free(attr);
                        attr = 0;
                    }

                    int state = audioSystem.waitForStreamState(stream, PA.STREAM_READY);

                    if (state != PA.STREAM_READY)
                        throw new ResourceUnavailableException("stream.state");

                    PA.stream_set_write_callback(stream, writeCb);

                    setStreamVolume(stream);

                    this.stream = stream;
                    this.dev = dev;
                } finally {
                    if (this.stream == 0)
                        PA.stream_disconnect(stream);
                }
            } finally {
                if (attr != 0)
                    PA.buffer_attr_free(attr);
            }
        } finally {
            if (this.stream == 0)
                PA.stream_unref(stream);
        }
    }

    /**
     * Notifies this instance that the value of the {@link AudioSystem#PROP_PLAYBACK_DEVICE}
     * property of its associated <code>AudioSystem</code> has changed.
     *
     * @param ev a <code>PropertyChangeEvent</code> which specifies details about the change such as the
     * name of the property and its old and new values
     */
    @Override
    protected void playbackDevicePropertyChange(PropertyChangeEvent ev)
    {
        /*
         * FIXME Disabled due to freezes reported by Vincent Lucas and Kertesz Laszlo on the dev
         * mailing list.
         */
        audioSystem.lockMainloop();
        try {
            /*
             * If the stream is not open, changes to the default playback device do not really
             * concern this Renderer because it will pick them up when it gets open.
             */
            boolean open = (stream != 0);

            if (open) {
                /*
                 * One and the same name of the sink that stream is connected to in the server may
                 * come from different MediaLocator instances.
                 */
                String locatorDev = getLocatorDev();

                if (!Objects.equals(dev, locatorDev)) {
                    /*
                     * PulseAudio has the capability to move a stream to a different device while
                     * the stream is connected to a sink. In other words, it may turn out that the
                     * stream is already connected to the sink with the specified name at this time
                     * of the execution.
                     */
                    String streamDev = PA.stream_get_device_name(stream);
                    if (!Objects.equals(streamDev, locatorDev)) {
                        /*
                         * The close method will stop this Renderer if it is currently started.
                         */
                        boolean start = !corked;
                        close();

                        try {
                            open();
                        } catch (ResourceUnavailableException rue) {
                            throw new UndeclaredThrowableException(rue);
                        }

                        if (start)
                            start();
                    }
                }
            }
        } finally {
            audioSystem.unlockMainloop();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int process(Buffer buffer)
    {
        if (buffer.isDiscard())
            return PlugIn.BUFFER_PROCESSED_OK;
        if (buffer.getLength() <= 0)
            return PlugIn.BUFFER_PROCESSED_OK;

        int ret;

        audioSystem.lockMainloop();
        try {
            ret = processWithMainloopLock(buffer);
        } finally {
            audioSystem.unlockMainloop();
        }
        if ((ret != PlugIn.BUFFER_PROCESSED_FAILED) && (buffer.getLength() > 0))
            ret |= PlugIn.INPUT_BUFFER_NOT_CONSUMED;

        return ret;
    }

    /**
     * Plays back the audio data of a specific FMJ <code>Buffer</code> through {@link #stream}. The
     * method executes with the assumption that the PulseAudio event loop object is locked by the
     * executing thread.
     *
     * @param buffer the FMJ <code>Buffer</code> which specifies the audio data to play back
     * @return <code>BUFFER_PROCESSED_OK</code> if the specified <code>buffer</code> was successfully
     * sumbitted for playback through {@link #stream}; otherwise,
     * <code>BUFFER_PROCESSED_FAILED</code>
     */
    private int processWithMainloopLock(Buffer buffer)
    {
        if ((stream == 0) || corked)
            return PlugIn.BUFFER_PROCESSED_FAILED;

        int writableSize = PA.stream_writable_size(stream);
        int ret;

        if (writableSize <= 0) {
            audioSystem.waitMainloop();
            ret = PlugIn.BUFFER_PROCESSED_OK;
        }
        else {
            byte[] data = (byte[]) buffer.getData();
            int offset = buffer.getOffset();
            int length = buffer.getLength();

            if (writableSize > length)
                writableSize = length;

            GainControl gainControl = getGainControl();
            if (gainControl != null)
                applyGain(gainControl, data, offset, writableSize);

            int writtenSize = PA.stream_write(stream, data, offset, writableSize, null, 0,
                    PA.SEEK_RELATIVE);

            if (writtenSize < 0) {
                ret = PlugIn.BUFFER_PROCESSED_FAILED;
            }
            else {
                ret = PlugIn.BUFFER_PROCESSED_OK;
                buffer.setLength(length - writtenSize);
                buffer.setOffset(offset + writtenSize);
            }
        }
        return ret;
    }

    /**
     * Sets the volume of a specific PulseAudio <code>stream</code> to a level specified by the
     * <code>GainControl</code> associated with this <code>PulseAudioRenderer</code>.
     *
     * @param stream the PulseAudio stream to set the volume of
     */
    @SuppressWarnings("unused")
    private void setStreamVolume(long stream)
    {
        GainControl gainControl;

        if (!SOFTWARE_GAIN && ((gainControl = getGainControl()) != null)) {
            cvolume = PA.cvolume_new();
            boolean freeCvolume = true;

            try {
                float gainControlLevel = gainControl.getLevel();

                setStreamVolume(stream, gainControlLevel);
                this.gainControlLevel = gainControlLevel;
                freeCvolume = false;
            } finally {
                if (freeCvolume) {
                    PA.cvolume_free(cvolume);
                    cvolume = 0;
                }
            }
        }
    }

    /**
     * Sets the volume of a specific PulseAudio <code>stream</code> to a specific <code>level</code>.
     *
     * @param stream the PulseAudio stream to set the volume of
     * @param level the volume to set on <code>stream</code>
     */
    private void setStreamVolume(long stream, float level)
    {
        int volume = PA.sw_volume_from_linear(level * (BasicVolumeControl.MAX_VOLUME_PERCENT / 100));
        PA.cvolume_set(cvolume, channels, volume);

        long o = PA.context_set_sink_input_volume(audioSystem.getContext(),
                PA.stream_get_index(stream), cvolume, null);

        if (o != 0)
            PA.operation_unref(o);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start()
    {
        audioSystem.lockMainloop();
        try {
            if (stream == 0) {
                try {
                    openWithMainloopLock();
                } catch (ResourceUnavailableException rue) {
                    throw new UndeclaredThrowableException(rue);
                }
            }

            cork(false);
        } finally {
            audioSystem.unlockMainloop();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop()
    {
        audioSystem.lockMainloop();
        try {
            stopWithMainloopLock();
        } finally {
            audioSystem.unlockMainloop();
        }
    }

    /**
     * Pauses the playback of audio data performed by {@link #stream}. The method executes with the
     * assumption that the PulseAudio event loop object is locked by the executing thread.
     */
    private void stopWithMainloopLock()
    {
        if (stream != 0)
            cork(true);
    }
}
