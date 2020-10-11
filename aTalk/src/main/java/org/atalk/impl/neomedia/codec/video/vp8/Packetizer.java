/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.video.vp8;

import org.atalk.android.plugin.timberlog.TimberLog;
import org.atalk.impl.neomedia.codec.AbstractCodec2;
import org.atalk.service.neomedia.codec.Constants;

import javax.media.Buffer;
import javax.media.format.VideoFormat;

import timber.log.Timber;

/**
 * Packetizes VP8 encoded frames in accord with
 * {@link "https://tools.ietf.org/html/draft-ietf-payload-vp8-07"}
 * <p>
 * Uses the simplest possible scheme, only splitting large packets. Extended
 * bits are never added, and PartID is always set to 0. The only bit that
 * changes is the Start of Partition bit, which is set only for the first packet
 * encoding a frame.
 *
 * @author Boris Grozev
 */
public class Packetizer extends AbstractCodec2
{
    /**
     * Maximum size of packets (excluding the payload descriptor and any other
     * headers (RTP, UDP))
     */
    private static final int MAX_SIZE = 1350;

    /**
     * Whether this is the first packet from the frame.
     */
    private boolean firstPacket = true;

    /**
     * Initializes a new <tt>Packetizer</tt> instance.
     */
    public Packetizer()
    {
        super("VP8 Packetizer", VideoFormat.class,
                new VideoFormat[]{new VideoFormat(Constants.VP8_RTP)});

        inputFormats = new VideoFormat[]{new VideoFormat(Constants.VP8)};
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doClose()
    {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doOpen()
    {
        Timber.log(TimberLog.FINER, "Opened VP8 packetizer");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected int doProcess(Buffer inputBuffer, Buffer outputBuffer)
    {
        int inLen;

        if (inputBuffer.isDiscard() || ((inLen = inputBuffer.getLength()) == 0)) {
            outputBuffer.setDiscard(true);
            return BUFFER_PROCESSED_OK;
        }

        byte[] output;
        int offset;
        int pdMaxLen = DePacketizer.VP8PayloadDescriptor.MAX_LENGTH;

        //The input will fit in a single packet
        int inOff = inputBuffer.getOffset();
        int len = Math.min(inLen, MAX_SIZE);

        offset = pdMaxLen;
        output = validateByteArraySize(outputBuffer, offset + len, true);
        System.arraycopy(inputBuffer.getData(), inOff, output, offset, len);

        //get the payload descriptor and copy it to the output
        byte[] pd = DePacketizer.VP8PayloadDescriptor.create(firstPacket);
        System.arraycopy(pd, 0, output, offset - pd.length, pd.length);
        offset -= pd.length;

        //set up the output buffer
        outputBuffer.setFormat(new VideoFormat(Constants.VP8_RTP));
        outputBuffer.setOffset(offset);
        outputBuffer.setLength(len + pd.length);

        if (inLen <= MAX_SIZE) {
            firstPacket = true;
            outputBuffer.setFlags(outputBuffer.getFlags() | Buffer.FLAG_RTP_MARKER);
            return BUFFER_PROCESSED_OK;
        }
        else {
            firstPacket = false;
            inputBuffer.setLength(inLen - MAX_SIZE);
            inputBuffer.setOffset(inOff + MAX_SIZE);
            return INPUT_BUFFER_NOT_CONSUMED;
        }
    }
}
