/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.protocol;

import java.io.IOException;

import javax.media.Buffer;
import javax.media.Format;
import javax.media.format.AudioFormat;
import javax.media.protocol.PullBufferStream;
import javax.media.protocol.PullSourceStream;

/**
 * Represents a <code>PullBufferStream</code> which reads its data from a specific
 * <code>PullSourceStream</code>.
 *
 * @author Lyubomir Marinov
 */
public class PullBufferStreamAdapter extends BufferStreamAdapter<PullSourceStream>
        implements PullBufferStream {

    /**
     * Initializes a new <code>PullBufferStreamAdapter</code> instance which reads its data from a
     * specific <code>PullSourceStream</code> with a specific <code>Format</code>
     *
     * @param stream the <code>PullSourceStream</code> the new instance is to read its data from
     * @param format the <code>Format</code> of the specified input <code>stream</code> and of the new instance
     */
    public PullBufferStreamAdapter(PullSourceStream stream, Format format) {
        super(stream, format);
    }

    /**
     * Gets the frame size measured in bytes defined by a specific <code>Format</code>.
     *
     * @param format the <code>Format</code> to determine the frame size in bytes of
     *
     * @return the frame size measured in bytes defined by the specified <code>Format</code>
     */
    private static int getFrameSizeInBytes(Format format) {
        AudioFormat audioFormat = (AudioFormat) format;
        int frameSizeInBits = audioFormat.getFrameSizeInBits();

        if (frameSizeInBits <= 0)
            return (audioFormat.getSampleSizeInBits() / 8) * audioFormat.getChannels();
        return (frameSizeInBits <= 8) ? 1 : (frameSizeInBits / 8);
    }

    /**
     * Implements PullBufferStream#read(Buffer). Delegates to the wrapped PullSourceStream by
     * either allocating a new byte[] buffer or using the existing one in the specified Buffer.
     *
     * @param buffer <code>Buffer</code> to read
     *
     * @throws IOException if I/O errors occurred during read operation
     */
    public void read(Buffer buffer)
            throws IOException {
        Object data = buffer.getData();
        byte[] bytes = null;

        if (data != null) {
            if (data instanceof byte[]) {
                bytes = (byte[]) data;
            }
            else if (data instanceof short[]) {
                short[] shorts = (short[]) data;

                bytes = new byte[2 * shorts.length];
            }
            else if (data instanceof int[]) {
                int[] ints = (int[]) data;

                bytes = new byte[4 * ints.length];
            }
        }
        if (bytes == null) {
            int frameSizeInBytes = getFrameSizeInBytes(getFormat());

            bytes = new byte[1024 * ((frameSizeInBytes <= 0) ? 4 : frameSizeInBytes)];
        }

        read(buffer, bytes, 0, bytes.length);
    }

    /**
     * Implements <code>BufferStreamAdapter#doRead(Buffer, byte[], int, int)</code>. Delegates to the
     * wrapped <code>PullSourceStream</code>.
     *
     * @param buffer
     * @param data byte array to read
     * @param offset to start reading
     * @param length length to read
     *
     * @return number of bytes read
     * @throws IOException if I/O related errors occurred during read operation
     */
    @Override
    protected int doRead(Buffer buffer, byte[] data, int offset, int length)
            throws IOException {
        return stream.read(data, offset, length);
    }

    /**
     * Implements PullBufferStream#willReadBlock(). Delegates to the wrapped PullSourceStream.
     *
     * @return true if this stream will block on read operation, false otherwise
     */
    public boolean willReadBlock() {
        return stream.willReadBlock();
    }
}
