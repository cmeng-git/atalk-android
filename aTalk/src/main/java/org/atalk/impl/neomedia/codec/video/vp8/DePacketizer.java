/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.video.vp8;

import org.atalk.impl.timberlog.TimberLog;
import org.atalk.impl.neomedia.codec.AbstractCodec2;
import org.atalk.service.neomedia.codec.Constants;
import org.atalk.util.ByteArrayBuffer;
import org.atalk.util.RTPUtils;

import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ArrayBlockingQueue;

import javax.media.Buffer;
import javax.media.ResourceUnavailableException;
import javax.media.format.VideoFormat;

import timber.log.Timber;

/**
 * A depacketizer from VP8 codec.
 * See {@link "https://tools.ietf.org/html/rfc7741"}
 * See {@link "https://tools.ietf.org/html/draft-ietf-payload-vp8-17"}
 *
 * Stores the RTP payloads (VP8 payload descriptor stripped) from RTP packets belonging to a
 * single VP8 compressed frame. Maps an RTP sequence number to a buffer which contains the payload.
 *
 * @author Boris Grozev
 * @author George Politis
 * @author Eng Chong Meng
 */
public class DePacketizer extends AbstractCodec2
{
    private final SortedMap<Integer, Container> data = new TreeMap<>(RTPUtils.sequenceNumberComparator);

    /**
     * Stores unused <code>Container</code>'s.
     */
    private final Queue<Container> free = new ArrayBlockingQueue<>(100);

    /**
     * Stores the first (earliest) sequence number stored in <code>data</code>, or -1 if <code>data</code> is empty.
     */
    private int firstSeq = -1;

    /**
     * Stores the last (latest) sequence number stored in <code>data</code>, or -1 if <code>data</code> is empty.
     */
    private int lastSeq = -1;

    /**
     * Stores the value of the <code>PictureID</code> field for the VP8 compressed
     * frame, parts of which are currently stored in <code>data</code>, or -1 if
     * the <code>PictureID</code> field is not in use or <code>data</code> is empty.
     */
    private int pictureId = -1;

    /**
     * Stores the RTP timestamp of the packets stored in <code>data</code>, or -1 if they don't have a timestamp set.
     */
    private long timestamp = -1L;

    /**
     * Whether we have stored any packets in <code>data</code>. Equivalent to <code>data.isEmpty()</code>.
     */
    private boolean empty = true;

    /**
     * Whether we have stored in <code>data</code> the last RTP packet of the VP8
     * compressed frame, parts of which are currently stored in <code>data</code>.
     */
    private boolean haveEnd = false;

    /**
     * Whether we have stored in <code>data</code> the first RTP packet of the VP8
     * compressed frame, parts of which are currently stored in <code>data</code>.
     */
    private boolean haveStart = false;

    /**
     * Stores the sum of the lengths of the data stored in <code>data</code>, that
     * is the total length of the VP8 compressed frame to be constructed.
     */
    private int frameLength = 0;

    /**
     * The sequence number of the last RTP packet, which was included in the output.
     */
    private int lastSentSeq = -1;

    /**
     * Initializes a new <code>AbstractCodec2</code> instance with a specific <code>PlugIn</code> name, a
     * specific <code>Class</code> of input and output <code>Format</code>s, and a specific list of
     * <code>Format</code>s supported as output.
     *
     * name: the <code>PlugIn</code> name of the new instance
     * VideoFormat.class: the <code>Class</code> of input and output <code>Format</code>s supported by the new instance
     * VideoFormat: the list of <code>Format</code>s supported by the new instance as output @Super parameters
     */
    public DePacketizer()
    {
        super("VP8 RTP DePacketizer", VideoFormat.class,
                new VideoFormat[]{new VideoFormat(Constants.VP8)});
        inputFormats = new VideoFormat[]{new VideoFormat(Constants.VP8_RTP)};
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
            throws ResourceUnavailableException
    {
        Timber.log(TimberLog.FINER, "Opened VP8 dePacketizer");
    }

    /**
     * Re-initializes the fields which store information about the currently held data. Empties <code>data</code>.
     */
    private void reinit()
    {
        firstSeq = lastSeq = -1;
        timestamp = -1L;
        pictureId = -1;
        empty = true;
        haveEnd = haveStart = false;
        frameLength = 0;

        Iterator<Map.Entry<Integer, Container>> it = data.entrySet().iterator();
        Map.Entry<Integer, Container> e;
        while (it.hasNext()) {
            e = it.next();
            free.offer(e.getValue());
            it.remove();
        }
    }

    /**
     * Checks whether the currently held VP8 compressed frame is complete (e.g all its packets
     * are stored in <code>data</code>).
     *
     * @return <code>true</code> if the currently help VP8 compressed frame is complete, <code>false</code> otherwise.
     */
    private boolean frameComplete()
    {
        return haveStart && haveEnd && !haveMissing();
    }

    /**
     * Checks whether there are packets with sequence numbers between <code>firstSeq</code> and
     * <code>lastSeq</code> which are *not* stored in <code>data</code>.
     *
     * @return <code>true</code> if there are packets with sequence numbers between
     * <code>firstSeq</code> and <code>lastSeq</code> which are *not* stored in <code>data</code>.
     */
    private boolean haveMissing()
    {
        Set<Integer> seqs = data.keySet();
        int s = firstSeq;
        while (s != lastSeq) {
            if (!seqs.contains(s))
                return true;
            s = (s + 1) & 0xffff;
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected int doProcess(Buffer inBuffer, Buffer outBuffer)
    {
        byte[] inData = (byte[]) inBuffer.getData();
        int inOffset = inBuffer.getOffset();
        int inLength = inBuffer.getLength();

        if (!VP8PayloadDescriptor.isValid(inData, inOffset, inLength)) {
            Timber.w("Invalid VP8/RTP packet discarded.");
            outBuffer.setDiscard(true);
            return BUFFER_PROCESSED_FAILED; //XXX: FAILED or OK?
        }

        int inSeq = (int) inBuffer.getSequenceNumber();
        long inRtpTimestamp = inBuffer.getRtpTimeStamp();
        int inPictureId = VP8PayloadDescriptor.getPictureId(inData, inOffset);
        boolean inMarker = (inBuffer.getFlags() & Buffer.FLAG_RTP_MARKER) != 0;
        boolean inIsStartOfFrame = VP8PayloadDescriptor.isStartOfFrame(inData, inOffset);

        int inPdSize = VP8PayloadDescriptor.getSize(inData, inOffset, inLength);
        int inPayloadLength = inLength - inPdSize;

        if (empty && lastSentSeq != -1
                && RTPUtils.sequenceNumberComparator.compare(inSeq, lastSentSeq) <= 0) {
            Timber.d("Discarding old packet (while empty) %s", inSeq);
            outBuffer.setDiscard(true);
            return BUFFER_PROCESSED_OK;
        }

        if (!empty) {
            // if the incoming packet has a different PictureID or timestamp than those of
            // the current frame, then it belongs to a different frame.
            if ((inPictureId != -1 && pictureId != -1
                    && inPictureId != pictureId)
                    | (timestamp != -1 && inRtpTimestamp != -1
                    && inRtpTimestamp != timestamp)) {
                //inSeq <= firstSeq
                if (RTPUtils.sequenceNumberComparator.compare(inSeq, firstSeq) <= 0) {
                    // the packet belongs to a previous frame. discard it
                    Timber.i("Discarding old packet %s", inSeq);
                    outBuffer.setDiscard(true);
                    return BUFFER_PROCESSED_OK;
                }
                // ReSync the firstSeq if process has dropped out data; must do this else MediaCodec decoder has problem
                else if (RTPUtils.sequenceNumberComparator.compare(inSeq, firstSeq) > 0) {
                    firstSeq = inSeq;
                }
                // never reach here?
                else {
                    // the packet belongs to a subsequent frame (to the one currently being held). Drop the current frame.
                    Timber.i("Discarding saved packets on arrival of a packet for a subsequent frame: %s; %s", inSeq, firstSeq);
                    reinit();
                }
            }
        }

        // a whole frame in a single packet. avoid the extra copy to this.data and output it immediately.
        if (empty && inMarker && inIsStartOfFrame) {
            byte[] outData = validateByteArraySize(outBuffer, inPayloadLength, false);
            System.arraycopy(inData, inOffset + inPdSize, outData, 0, inPayloadLength);
            outBuffer.setOffset(0);
            outBuffer.setLength(inPayloadLength);
            outBuffer.setRtpTimeStamp(inBuffer.getRtpTimeStamp());

            Timber.log(TimberLog.FINER, "Out PictureID = %s", inPictureId);
            lastSentSeq = inSeq;
            return BUFFER_PROCESSED_OK;
        }

        // add to this.data
        Container container = free.poll();
        if (container == null)
            container = new Container();
        if (container.buf == null || container.buf.length < inPayloadLength)
            container.buf = new byte[inPayloadLength];

        if (data.get(inSeq) != null) {
            Timber.i("(Probable) duplicate packet detected, discarding %s", inSeq);
            outBuffer.setDiscard(true);
            return BUFFER_PROCESSED_OK;
        }

        System.arraycopy(inData, inOffset + inPdSize, container.buf, 0, inPayloadLength);
        container.len = inPayloadLength;
        data.put(inSeq, container);

        // update fields
        frameLength += inPayloadLength;
        if (firstSeq == -1
                || (RTPUtils.sequenceNumberComparator.compare(firstSeq, inSeq) > 0))
            firstSeq = inSeq;
        if (lastSeq == -1
                || (RTPUtils.sequenceNumberComparator.compare(inSeq, lastSeq) > 0))
            lastSeq = inSeq;

        if (empty) {
            // the first received packet for the current frame was just added
            empty = false;
            timestamp = inRtpTimestamp;
            pictureId = inPictureId;
        }

        if (inMarker)
            haveEnd = true;
        if (inIsStartOfFrame)
            haveStart = true;

        // check if we have a full frame
        if (frameComplete()) {
            byte[] outData = validateByteArraySize(outBuffer, frameLength, false);
            int ptr = 0;
            Container b;
            for (Map.Entry<Integer, Container> entry : data.entrySet()) {
                b = entry.getValue();
                System.arraycopy(b.buf, 0, outData, ptr, b.len);
                ptr += b.len;
            }

            outBuffer.setOffset(0);
            outBuffer.setLength(frameLength);
            outBuffer.setRtpTimeStamp(inBuffer.getRtpTimeStamp());

            Timber.log(TimberLog.FINER, "Out PictureID = %s", inPictureId);
            lastSentSeq = lastSeq;

            // prepare for the next frame
            reinit();
            return BUFFER_PROCESSED_OK;
        }
        else {
            // frame not complete yet
            outBuffer.setDiscard(true);
            return OUTPUT_BUFFER_NOT_FILLED;
        }
    }

    /**
     * Returns true if the buffer contains a VP8 key frame at offset <code>offset</code>.
     *
     * @param buf the byte buffer to check
     * @param off the offset in the byte buffer where the actual data starts
     * @param len the length of the data in the byte buffer
     * @return true if the buffer contains a VP8 key frame at offset <code>offset</code>.
     */
    public static boolean isKeyFrame(byte[] buf, int off, int len)
    {
        // Check if this is the start of a VP8 partition in the payload descriptor.
        if (!DePacketizer.VP8PayloadDescriptor.isValid(buf, off, len)) {
            return false;
        }

        if (!DePacketizer.VP8PayloadDescriptor.isStartOfFrame(buf, off)) {
            return false;
        }

        int szVP8PayloadDescriptor = DePacketizer.VP8PayloadDescriptor.getSize(buf, off, len);
        return DePacketizer.VP8PayloadHeader.isKeyFrame(buf, off + szVP8PayloadDescriptor);
    }

    /**
     * A class that represents the VP8 Payload Descriptor structure defined
     * in {@link "https://tools.ietf.org/html/rfc7741"}
     */
    public static class VP8PayloadDescriptor
    {
        /**
         * The bitmask for the TL0PICIDX field.
         */
        public static final int TL0PICIDX_MASK = 0xff;

        /**
         * The bitmask for the extended picture id field.
         */
        public static final int EXTENDED_PICTURE_ID_MASK = 0x7fff;

        /**
         * I bit from the X byte of the Payload Descriptor.
         */
        private static final byte I_BIT = (byte) 0x80;

        /**
         * K bit from the X byte of the Payload Descriptor.
         */
        private static final byte K_BIT = (byte) 0x10;
        /**
         * L bit from the X byte of the Payload Descriptor.
         */
        private static final byte L_BIT = (byte) 0x40;

        /**
         * I bit from the I byte of the Payload Descriptor.
         */
        private static final byte M_BIT = (byte) 0x80;

        /**
         * S bit from the first byte of the Payload Descriptor.
         */
        private static final byte S_BIT = (byte) 0x10;
        /**
         * T bit from the X byte of the Payload Descriptor.
         */
        private static final byte T_BIT = (byte) 0x20;

        /**
         * X bit from the first byte of the Payload Descriptor.
         */
        private static final byte X_BIT = (byte) 0x80;

        /**
         * N bit from the first byte of the Payload Descriptor.
         */
        private static final byte N_BIT = (byte) 0x20;

        /**
         * The bitmask for the temporal-layer index
         */
        private static final byte TID_MASK = (byte) 0xC0;

        /**
         * Y bit from the TID/Y/KEYIDX extension byte.
         */
        private static final byte Y_BIT = (byte) 0x20;

        /**
         * The bitmask for the temporal key frame index
         */
        private static final byte KEYIDX_MASK = (byte) 0x1F;

        /**
         * Maximum length of a VP8 Payload Descriptor.
         */
        public static final int MAX_LENGTH = 6;

        /**
         * Gets the TID/Y/KEYIDX extension byte if available
         *
         * @param buf the byte buffer that holds the VP8 packet.
         * @param off the offset in the byte buffer where the VP8 packet starts.
         * @param len the length of the VP8 packet.
         * @return the TID/Y/KEYIDX extension byte, if that's set, -1 otherwise.
         */
        private static byte getTidYKeyIdxExtensionByte(byte[] buf, int off, int len)
        {
            if (buf == null || buf.length < off + len || len < 2) {
                return -1;
            }

            if ((buf[off] & X_BIT) == 0 || (buf[off + 1] & (T_BIT | K_BIT)) == 0) {
                return -1;
            }

            int sz = getSize(buf, off, len);
            if (buf.length < off + sz || sz < 1) {
                return -1;
            }

            return (byte) (buf[off + sz - 1] & 0xFF);
        }

        /**
         * Gets the temporal layer index (TID), if that's set.
         *
         * @param buf the byte buffer that holds the VP8 packet.
         * @param off the offset in the byte buffer where the VP8 packet starts.
         * @param len the length of the VP8 packet.
         * @return the temporal layer index (TID), if that's set, -1 otherwise.
         */
        public static int getTemporalLayerIndex(byte[] buf, int off, int len)
        {
            byte tidYKeyIdxByte = getTidYKeyIdxExtensionByte(buf, off, len);

            return tidYKeyIdxByte != -1 && (buf[off + 1] & T_BIT) != 0 ? (tidYKeyIdxByte & TID_MASK) >> 6 : tidYKeyIdxByte;
        }

        /**
         * Gets the 1 layer sync bit (Y BIT), if that's set.
         *
         * @param buf the byte buffer that holds the VP8 packet.
         * @param off the offset in the byte buffer where the VP8 packet starts.
         * @param len the length of the VP8 packet.
         * @return the 1 layer sync bit (Y BIT), if that's set, -1 otherwise.
         */
        public static int getFirstLayerSyncBit(byte[] buf, int off, int len)
        {
            byte tidYKeyIdxByte = getTidYKeyIdxExtensionByte(buf, off, len);

            return tidYKeyIdxByte != -1 ? (tidYKeyIdxByte & Y_BIT) >> 5 : tidYKeyIdxByte;
        }

        /**
         * Gets the temporal key frame index (KEYIDX), if that's set.
         *
         * @param buf the byte buffer that holds the VP8 packet.
         * @param off the offset in the byte buffer where the VP8 packet starts.
         * @param len the length of the VP8 packet.
         * @return the temporal key frame index (KEYIDX), if that's set, -1 otherwise.
         */
        public static int getTemporalKeyFrameIndex(byte[] buf, int off, int len)
        {
            byte tidYKeyIdxByte = getTidYKeyIdxExtensionByte(buf, off, len);

            return tidYKeyIdxByte != -1 && (buf[off + 1] & K_BIT) != 0 ? (tidYKeyIdxByte & KEYIDX_MASK) : tidYKeyIdxByte;
        }

        /**
         * Returns a simple Payload Descriptor, with PartID = 0, the 'start of partition' bit set
         * according to <code>startOfPartition</code>, and all other bits set to 0.
         *
         * @param startOfPartition whether to 'start of partition' bit should be set
         * @return a simple Payload Descriptor, with PartID = 0, the 'start of partition' bit set
         * according to <code>startOfPartition</code>, and all other bits set to 0.
         */
        public static byte[] create(boolean startOfPartition)
        {
            byte[] pd = new byte[1];
            pd[0] = startOfPartition ? (byte) 0x10 : 0;
            return pd;
        }

        /**
         * The size in bytes of the Payload Descriptor at offset
         * <code>offset</code> in <code>input</code>. The size is between 1 and 6.
         *
         * @param baf the <code>ByteArrayBuffer</code> that holds the VP8 payload descriptor.
         * @return The size in bytes of the Payload Descriptor at offset
         * <code>offset</code> in <code>input</code>, or -1 if the input is not a valid
         * VP8 Payload Descriptor. The size is between 1 and 6.
         */
        public static int getSize(ByteArrayBuffer baf)
        {
            if (baf == null) {
                return -1;
            }
            return getSize(baf.getBuffer(), baf.getOffset(), baf.getLength());
        }

        /**
         * The size in bytes of the Payload Descriptor at offset
         * <code>offset</code> in <code>input</code>. The size is between 1 and 6.
         *
         * @param input input
         * @param offset offset
         * @param length length
         * @return The size in bytes of the Payload Descriptor at offset
         * <code>offset</code> in <code>input</code>, or -1 if the input is not a valid
         * VP8 Payload Descriptor. The size is between 1 and 6.
         */
        public static int getSize(byte[] input, int offset, int length)
        {
            if (!isValid(input, offset, length))
                return -1;

            if ((input[offset] & X_BIT) == 0)
                return 1;

            int size = 2;
            if ((input[offset + 1] & I_BIT) != 0) {
                size++;
                if ((input[offset + 2] & M_BIT) != 0)
                    size++;
            }
            if ((input[offset + 1] & (L_BIT | T_BIT)) != 0)
                size++;
            if ((input[offset + 1] & (T_BIT | K_BIT)) != 0)
                size++;

            return size;
        }

        /**
         * Determines whether the VP8 payload specified in the buffer that is
         * passed as an argument has a picture ID or not.
         *
         * @param buf the byte buffer that contains the VP8 payload.
         * @param off the offset in the byte buffer where the VP8 payload starts.
         * @param len the length of the VP8 payload in the byte buffer.
         * @return true if the VP8 payload contains a picture ID, false otherwise.
         */
        public static boolean hasPictureId(byte[] buf, int off, int len)
        {
            return isValid(buf, off, len)
                    && (buf[off] & X_BIT) != 0 && (buf[off + 1] & I_BIT) != 0;
        }

        /**
         * Determines whether the VP8 payload specified in the buffer that is
         * passed as an argument has an extended picture ID or not.
         *
         * @param buf the byte buffer that contains the VP8 payload.
         * @param off the offset in the byte buffer where the VP8 payload starts.
         * @param len the length of the VP8 payload in the byte buffer.
         * @return true if the VP8 payload contains an extended picture ID, false otherwise.
         */
        public static boolean hasExtendedPictureId(byte[] buf, int off, int len)
        {
            return hasPictureId(buf, off, len) && (buf[off + 2] & M_BIT) != 0;
        }

        /**
         * Gets the value of the PictureID field of a VP8 Payload Descriptor.
         *
         * @param input
         * @param offset
         * @return the value of the PictureID field of a VP8 Payload Descriptor,
         * or -1 if the fields is not present.
         */
        public static int getPictureId(byte[] input, int offset)
        {
            if (input == null
                    || !hasPictureId(input, offset, input.length - offset)) {
                return -1;
            }

            boolean isLong = (input[offset + 2] & M_BIT) != 0;
            if (isLong)
                return (input[offset + 2] & 0x7f) << 8
                        | (input[offset + 3] & 0xff);
            else
                return input[offset + 2] & 0x7f;

        }

        /**
         * Sets the extended picture ID for the VP8 payload specified in the
         * buffer that is passed as an argument.
         *
         * @param buf the byte buffer that contains the VP8 payload.
         * @param off the offset in the byte buffer where the VP8 payload starts.
         * @param len the length of the VP8 payload in the byte buffer.
         * @return true if the operation succeeded, false otherwise.
         */
        public static boolean setExtendedPictureId(
                byte[] buf, int off, int len, int val)
        {
            if (!hasExtendedPictureId(buf, off, len)) {
                return false;
            }

            buf[off + 2] = (byte) (0x80 | (val >> 8) & 0x7F);
            buf[off + 3] = (byte) (val & 0xFF);

            return true;
        }

        /**
         * Sets the TL0PICIDX field for the VP8 payload specified in the buffer that is passed as an argument.
         *
         * @param buf the byte buffer that contains the VP8 payload.
         * @param off the offset in the byte buffer where the VP8 payload starts.
         * @param len the length of the VP8 payload in the byte buffer.
         * @return true if the operation succeeded, false otherwise.
         */
        public static boolean setTL0PICIDX(byte[] buf, int off, int len, int val)
        {
            if (!isValid(buf, off, len)
                    || (buf[off] & X_BIT) == 0 || (buf[off + 1] & L_BIT) == 0) {
                return false;
            }

            int offTL0PICIDX = 2;
            if ((buf[off + 1] & I_BIT) != 0) {
                offTL0PICIDX++;
                if ((buf[off + 2] & M_BIT) != 0) {
                    offTL0PICIDX++;
                }
            }

            buf[off + offTL0PICIDX] = (byte) val;
            return true;
        }

        /**
         * Checks whether the arguments specify a valid buffer.
         *
         * @param buf the byte buffer that contains the VP8 payload.
         * @param off the offset in the byte buffer where the VP8 payload starts.
         * @param len the length of the VP8 payload in the byte buffer.
         * @return true if the arguments specify a valid buffer, false
         * otherwise.
         */
        public static boolean isValid(byte[] buf, int off, int len)
        {
            return buf != null && buf.length >= off + len && off > -1 && len > 0;
        }

        /**
         * Checks whether the '<code>start of partition</code>' bit is set in the
         * VP8 Payload Descriptor at offset <code>offset</code> in <code>input</code>.
         *
         * @param input input
         * @param offset offset
         * @return <code>true</code> if the '<code>start of partition</code>' bit is set,
         * <code>false</code> otherwise.
         */
        public static boolean isStartOfPartition(byte[] input, int offset)
        {
            return (input[offset] & S_BIT) != 0;
        }

        /**
         * Returns <code>true</code> if both the '<code>start of partition</code>' bit
         * is set and the <code>PID</code> fields has value 0 in the VP8 Payload
         * Descriptor at offset <code>offset</code> in <code>input</code>.
         *
         * @param input input
         * @param offset offset
         * @return <code>true</code> if both the '<code>start of partition</code>' bit
         * is set and the <code>PID</code> fields has value 0 in the VP8 Payload
         * Descriptor at offset <code>offset</code> in <code>input</code>.
         */
        public static boolean isStartOfFrame(byte[] input, int offset)
        {
            return isStartOfPartition(input, offset)
                    && getPartitionId(input, offset) == 0;
        }

        /**
         * Returns the value of the <code>PID</code> (partition ID) field of the
         * VP8 Payload Descriptor at offset <code>offset</code> in <code>input</code>.
         *
         * @param input input
         * @param offset offset
         * @return the value of the <code>PID</code> (partition ID) field of the
         * VP8 Payload Descriptor at offset <code>offset</code> in <code>input</code>.
         */
        public static int getPartitionId(byte[] input, int offset)
        {
            return input[offset] & 0x07;
        }

        /**
         * Gets a boolean indicating if the non-reference bit is set.
         *
         * @param buf the byte buffer that holds the VP8 payload descriptor.
         * @param off the offset in the byte buffer where the payload descriptor starts.
         * @param len the length of the payload descriptor in the byte buffer.
         * @return true if the non-reference bit is NOT set, false otherwise.
         */
        public static boolean isReference(byte[] buf, int off, int len)
        {
            return (buf[off] & N_BIT) == 0;
        }

        /**
         * Gets the TL0PICIDX from the payload descriptor.
         *
         * @param buf the byte buffer that holds the VP8 payload descriptor.
         * @param off the offset in the byte buffer where the payload descriptor starts.
         * @param len the length of the payload descriptor in the byte buffer.
         * @return the TL0PICIDX from the payload descriptor.
         */
        public static int getTL0PICIDX(byte[] buf, int off, int len)
        {
            int sz = getSize(buf, off, len);
            if (sz < 1) {
                return -1;
            }

            return buf[off + sz - 2] & 0xff;
        }

        /**
         * Provides a string description of the VP8 descriptor that can be used
         * for debugging purposes.
         *
         * @param buf the byte buffer that holds the VP8 payload descriptor.
         * @param off the offset in the byte buffer where the payload descriptor starts.
         * @param len the length of the payload descriptor in the byte buffer.
         * @return Descriptive string of the vp8 info
         */
        public static String toString(byte[] buf, int off, int len)
        {
            return "VP8PayloadDescriptor" +
                    "[size=" + getSize(buf, off, len) +
                    ", tid=" + getTemporalLayerIndex(buf, off, len) +
                    ", tl0picidx=" + getTL0PICIDX(buf, off, len) +
                    ", pid=" + getPictureId(buf, off) +
                    ", isExtended=" + hasExtendedPictureId(buf, off, len) +
                    ", hex=" + RTPUtils.toHexString(buf, off, Math.min(len, MAX_LENGTH), false) +
                    "]";
        }
    }

    /**
     * A class that represents the VP8 Payload Header structure defined
     * in {@link "https://tools.ietf.org/html/rfc7741"}
     */
    public static class VP8PayloadHeader
    {
        /**
         * P bit of the Payload Descriptor.
         */
        private static final byte P_BIT = (byte) 0x01;

        /**
         * Returns true if the <code>P</code> (inverse key frame flag) field of the
         * VP8 Payload Header at offset <code>offset</code> in <code>input</code> is 0.
         *
         * @return true if the <code>P</code> (inverse key frame flag) field of the
         * VP8 Payload Header at offset <code>offset</code> in <code>input</code> is 0, false otherwise.
         */
        public static boolean isKeyFrame(byte[] input, int offset)
        {
            // When set to 0 the current frame is a key frame.  When set to 1
            // the current frame is an interframe. Defined in [RFC6386]

            return (input[offset] & P_BIT) == 0;
        }
    }

    /**
     * A class that represents a keyframe header structure (see RFC 6386, paragraph 9.1).
     *
     * @author George Politis
     */
    public static class VP8KeyframeHeader
    {
        /*
         * From RFC 6386, the keyframe header has this format.
         *
         * Start code byte 0: 0x9d
         * Start code byte 1: 0x01
         * Start code byte 2: 0x2a
         *
         * 16 bits : (2 bits Horizontal Scale << 14) | Width (14 bits)
         * 16 bits : (2 bits Vertical Scale << 14) | Height (14 bits)
         */

        /**
         * @return the height of this instance.
         */
        public static int getHeight(byte[] buf, int off)
        {
            return (((buf[off + 6] & 0xff) << 8) | buf[off + 5] & 0xff) & 0x3fff;
        }
    }

    /**
     * A simple container for a <code>byte[]</code> and an integer.
     */
    private static class Container
    {
        /**
         * This <code>Container</code>'s data.
         */
        private byte[] buf;

        /**
         * Length used.
         */
        private int len = 0;
    }
}
