/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.protocol;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.LinkedList;

import javax.media.Buffer;
import javax.media.Format;
import javax.media.format.AudioFormat;
import javax.media.protocol.BufferTransferHandler;
import javax.media.protocol.PushBufferDataSource;
import javax.media.protocol.PushBufferStream;

import org.atalk.impl.neomedia.control.AbstractControls;
import org.atalk.service.neomedia.DTMFInbandTone;

/**
 * Implements a <code>PushBufferDataSource</code> wrapper which provides mute support for the wrapped
 * instance.
 * <p>
 * Because the class wouldn't work for our use case without it, <code>CaptureDevice</code> is
 * implemented and is being delegated to the wrapped <code>DataSource</code> (if it supports the
 * interface in question).
 * </p>
 *
 * @author Lyubomir Marinov
 */
public class RewritablePushBufferDataSource
        extends PushBufferDataSourceDelegate<PushBufferDataSource>
        implements MuteDataSource, InbandDTMFDataSource {

    /**
     * The indicator which determines whether this <code>DataSource</code> is mute.
     */
    private boolean mute;

    /**
     * The tones to send via inband DTMF, if not empty.
     */
    private final LinkedList<DTMFInbandTone> tones = new LinkedList<>();

    /**
     * Initializes a new <code>RewritablePushBufferDataSource</code> instance which is to provide mute
     * support for a specific <code>PushBufferDataSource</code>.
     *
     * @param dataSource the <code>PushBufferDataSource</code> the new instance is to provide mute support for
     */
    public RewritablePushBufferDataSource(PushBufferDataSource dataSource) {
        super(dataSource);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Overrides the super implementation to include the type hierarchy of the very wrapped
     * <code>dataSource</code> instance into the search for the specified <code>controlType</code>.
     */
    @Override
    public Object getControl(String controlType) {
        if (InbandDTMFDataSource.class.getName().equals(controlType)
                || MuteDataSource.class.getName().equals(controlType)) {
            return this;
        }
        else {
            /*
             * The super implements a delegate so we can be sure that it delegates the
             * invocation of Controls#getControl(String) to the wrapped dataSource.
             */
            return AbstractControls.queryInterface(dataSource, controlType);
        }
    }

    /**
     * Implements {@link PushBufferDataSource#getStreams()}. Wraps the streams of the wrapped
     * <code>PushBufferDataSource</code> into <code>MutePushBufferStream</code> instances in order to
     * provide mute support to them.
     *
     * @return an array of <code>PushBufferStream</code> instances with enabled mute support
     */
    @Override
    public PushBufferStream[] getStreams() {
        PushBufferStream[] streams = dataSource.getStreams();

        if (streams != null) {
            for (int streamIndex = 0; streamIndex < streams.length; streamIndex++) {
                PushBufferStream stream = streams[streamIndex];

                if (stream != null)
                    streams[streamIndex] = new MutePushBufferStream(stream);
            }
        }
        return streams;
    }

    /**
     * Determines whether this <code>DataSource</code> is mute.
     *
     * @return <code>true</code> if this <code>DataSource</code> is mute; otherwise, <code>false</code>
     */
    public synchronized boolean isMute() {
        return mute;
    }

    /**
     * Replaces the media data contained in a specific <code>Buffer</code> with a compatible
     * representation of silence.
     *
     * @param buffer the <code>Buffer</code> the data contained in which is to be replaced with silence
     */
    public static void mute(Buffer buffer) {
        Object data = buffer.getData();

        if (data != null) {
            Class<?> dataClass = data.getClass();
            final int fromIndex = buffer.getOffset();
            final int toIndex = fromIndex + buffer.getLength();

            if (Format.byteArray.equals(dataClass))
                Arrays.fill((byte[]) data, fromIndex, toIndex, (byte) 0);
            else if (Format.intArray.equals(dataClass))
                Arrays.fill((int[]) data, fromIndex, toIndex, 0);
            else if (Format.shortArray.equals(dataClass))
                Arrays.fill((short[]) data, fromIndex, toIndex, (short) 0);

            buffer.setData(data);
        }
    }

    /**
     * Sets the mute state of this <code>DataSource</code>.
     *
     * @param mute <code>true</code> to mute this <code>DataSource</code>; otherwise, <code>false</code>
     */
    public synchronized void setMute(boolean mute) {
        this.mute = mute;
    }

    /**
     * Adds a new inband DTMF tone to send.
     *
     * @param tone the DTMF tone to send.
     */
    public void addDTMF(DTMFInbandTone tone) {
        this.tones.add(tone);
    }

    /**
     * Determines whether this <code>DataSource</code> sends a DTMF tone.
     *
     * @return <code>true</code> if this <code>DataSource</code> is sending a DTMF tone; otherwise,
     * <code>false</code>.
     */
    public boolean isSendingDTMF() {
        return !this.tones.isEmpty();
    }

    /**
     * Replaces the media data contained in a specific <code>Buffer</code> with an inband DTMF tone
     * signal.
     *
     * @param buffer the <code>Buffer</code> the data contained in which is to be replaced with the DTMF tone
     * @param tone the <code>DMFTTone</code> to send via inband DTMF signal.
     */
    public static void sendDTMF(Buffer buffer, DTMFInbandTone tone) {
        Object data = buffer.getData();
        Format format;

        // Send the inband DTMF tone only if the buffer contains audio data.
        if ((data != null) && ((format = buffer.getFormat()) instanceof AudioFormat)) {
            AudioFormat audioFormat = (AudioFormat) format;
            int sampleSizeInBits = audioFormat.getSampleSizeInBits();
            // Generates the inband DTMF signal.
            short[] samples = tone.getAudioSamples(audioFormat.getSampleRate(), sampleSizeInBits);

            int fromIndex = buffer.getOffset();
            int toIndex = fromIndex + samples.length * (sampleSizeInBits / 8);
            ByteBuffer newData = ByteBuffer.allocate(toIndex);

            // Prepares newData to be endian compliant with original buffer
            // data.
            newData.order((audioFormat.getEndian() == AudioFormat.BIG_ENDIAN)
                    ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);

            // Keeps data unchanged if stored before the original buffer offset
            // index. Takes care of original data array type (byte, short or int).
            Class<?> dataType = data.getClass();

            if (Format.byteArray.equals(dataType)) {
                newData.put((byte[]) data, 0, fromIndex);
            }
            else if (Format.shortArray.equals(dataType)) {
                short[] shortData = (short[]) data;

                for (int i = 0; i < fromIndex; ++i)
                    newData.putShort(shortData[i]);
            }
            else if (Format.intArray.equals(dataType)) {
                int[] intData = (int[]) data;

                for (int i = 0; i < fromIndex; ++i)
                    newData.putInt(intData[i]);
            }

            // Copies inband DTMF singal into newData. Takes care of audio format data type
            // (byte, short or int).
            switch (sampleSizeInBits) {
                case 8:
                    for (short sample : samples)
                        newData.put((byte) sample);
                    break;
                case 16:
                    for (short sample : samples)
                        newData.putShort(sample);
                    break;
                case 24:
                case 32:
                default:
                    throw new IllegalArgumentException(
                            "buffer.format.sampleSizeInBits must be either 8 or 16, not "
                                    + sampleSizeInBits);
            }

            // Copies newData up to date into the original buffer.
            // Takes care of original data array type (byte, short or int).
            if (Format.byteArray.equals(dataType))
                buffer.setData(newData.array());
            else if (Format.shortArray.equals(dataType))
                buffer.setData(newData.asShortBuffer().array());
            else if (Format.intArray.equals(dataType))
                buffer.setData(newData.asIntBuffer().array());

            // Updates the buffer length.
            buffer.setLength(toIndex - fromIndex);
        }
    }

    /**
     * Implements a <code>PushBufferStream</code> wrapper which provides mute support for the wrapped
     * instance.
     */
    private class MutePushBufferStream extends SourceStreamDelegate<PushBufferStream>
            implements PushBufferStream {
        /**
         * Initializes a new <code>MutePushBufferStream</code> instance which is to provide mute
         * support to a specific <code>PushBufferStream</code> .
         *
         * @param stream the <code>PushBufferStream</code> the new instance is to provide mute support to
         */
        public MutePushBufferStream(PushBufferStream stream) {
            super(stream);
        }

        /**
         * Implements {@link PushBufferStream#getFormat()}. Delegates to the wrapped
         * <code>PushBufferStream</code>.
         *
         * @return the <code>Format</code> of the wrapped <code>PushBufferStream</code>
         */
        public Format getFormat() {
            return stream.getFormat();
        }

        /**
         * Implements {@link PushBufferStream#read(Buffer)}. If this instance is muted (through its
         * owning <code>RewritablePushBufferDataSource</code>), overwrites the data read from the
         * wrapped <code>PushBufferStream</code> with silence data.
         *
         * @param buffer a <code>Buffer</code> in which the read data is to be returned to the caller
         *
         * @throws IOException if reading from the wrapped <code>PushBufferStream</code> fails
         */
        public void read(Buffer buffer)
                throws IOException {
            stream.read(buffer);

            if (isSendingDTMF())
                sendDTMF(buffer, tones.poll());
            else if (isMute())
                mute(buffer);
        }

        /**
         * Implements {@link PushBufferStream#setTransferHandler(BufferTransferHandler)}. Sets up
         * the hiding of the wrapped <code>PushBufferStream</code> from the specified
         * <code>transferHandler</code> and thus gives this <code>MutePushBufferStream</code> full control
         * when the <code>transferHandler</code> in question starts calling to the stream given to
         * it in <code>BufferTransferHandler#transferData(PushBufferStream)</code>.
         *
         * @param transferHandler a <code>BufferTransferHandler</code> to be notified by this instance when data is
         * available for reading from it
         */
        public void setTransferHandler(BufferTransferHandler transferHandler) {
            stream.setTransferHandler((transferHandler == null) ? null
                    : new StreamSubstituteBufferTransferHandler(transferHandler, stream, this));
        }
    }
}
