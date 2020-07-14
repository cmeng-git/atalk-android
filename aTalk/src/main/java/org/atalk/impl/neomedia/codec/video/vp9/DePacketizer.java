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
package org.atalk.impl.neomedia.codec.video.vp9;

import org.atalk.android.plugin.timberlog.TimberLog;
import org.atalk.impl.neomedia.codec.AbstractCodec2;
import org.atalk.service.neomedia.codec.Constants;
import org.atalk.util.ByteArrayBuffer;
import org.atalk.util.RTPUtils;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;

import javax.media.Buffer;
import javax.media.ResourceUnavailableException;
import javax.media.format.VideoFormat;

import timber.log.Timber;

/**
 * A depacketizer for VP9 codec.
 * See {@link "https://tools.ietf.org/html/draft-ietf-payload-vp9-10"}
 *
 * @author George Politis
 * @author Eng Chong Meng
 */
public class DePacketizer extends AbstractCodec2
{
    /**
     * Stores the RTP payloads (VP9 payload descriptor stripped) from RTP packets belonging to a
     * single VP9 compressed frame. Maps an RTP sequence number to a buffer which contains the payload.
     */
    private SortedMap<Integer, Container> data = new TreeMap<>(RTPUtils.sequenceNumberComparator);

    /**
     * Stores unused <tt>Container</tt>'s.
     */
    private Queue<Container> free = new ArrayBlockingQueue<>(100);

    /**
     * Stores the first (earliest) sequence number stored in <tt>data</tt>, or -1 if <tt>data</tt> is empty.
     */
    private int firstSeq = -1;

    /**
     * Stores the last (latest) sequence number stored in <tt>data</tt>, or -1 if <tt>data</tt> is empty.
     */
    private int lastSeq = -1;

    /**
     * Stores the value of the <tt>PictureID</tt> field for the VP9 compressed
     * frame, parts of which are currently stored in <tt>data</tt>, or -1 if
     * the <tt>PictureID</tt> field is not in use or <tt>data</tt> is empty.
     */
    private int pictureId = -1;

    /**
     * Stores the RTP timestamp of the packets stored in <tt>data</tt>, or -1 if they don't have a timestamp set.
     */
    private long timestamp = -1L;

    /**
     * Whether we have stored any packets in <tt>data</tt>. Equivalent to <tt>data.isEmpty()</tt>.
     */
    private boolean empty = true;

    /**
     * Whether we have stored in <tt>data</tt> the last RTP packet of the VP9
     * compressed frame, parts of which are currently stored in <tt>data</tt>.
     */
    private boolean haveEnd = false;

    /**
     * Whether we have stored in <tt>data</tt> the first RTP packet of the VP9
     * compressed frame, parts of which are currently stored in <tt>data</tt>.
     */
    private boolean haveStart = false;

    /**
     * Stores the sum of the lengths of the data stored in <tt>data</tt>, that
     * is the total length of the VP9 compressed frame to be constructed.
     */
    private int frameLength = 0;

    /**
     * The sequence number of the last RTP packet, which was included in the output.
     */
    private int lastSentSeq = -1;

    /**
     * Initializes a new <tt>AbstractCodec2</tt> instance with a specific <tt>PlugIn</tt> name, a
     * specific <tt>Class</tt> of input and output <tt>Format</tt>s and a specific list of
     * <tt>Format</tt>s supported as output.
     *
     * @param name the <tt>PlugIn</tt> name of the new instance
     * @param formatClass the <tt>Class</tt> of input and output <tt>Format</tt>s supported by the new instance
     * @param supportedOutputFormats the list of <tt>Format</tt>s supported by the new instance as output
     * @Super parameters
     */
    public DePacketizer()
    {
        super("VP9 RTP DePacketizer",
                VideoFormat.class,
                new VideoFormat[]{new VideoFormat(Constants.VP9)});
        inputFormats = new VideoFormat[]{new VideoFormat(Constants.VP9_RTP)};
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
        Timber.log(TimberLog.FINER, "Opened VP9 dePacketizer");
    }

    /**
     * Re-initializes the fields which store information about the currently held data. Empties <tt>data</tt>.
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
     * are stored in <tt>data</tt>).
     *
     * @return <tt>true</tt> if the currently help VP8 compressed frame is complete, <tt>false</tt> otherwise.
     */
    private boolean frameComplete()
    {
        return haveStart && haveEnd && !haveMissing();
    }

    /**
     * Checks whether there are packets with sequence numbers between <tt>firstSeq</tt> and
     * <tt>lastSeq</tt> which are *not* stored in <tt>data</tt>.
     *
     * @return <tt>true</tt> if there are packets with sequence numbers between
     * <tt>firstSeq</tt> and <tt>lastSeq</tt> which are *not* stored in <tt>data</tt>.
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

        if (!VP9PayloadDescriptor.isValid(inData, inOffset, inLength)) {
            Timber.w("Invalid RTP/VP9 packet discarded.");
            outBuffer.setDiscard(true);
            return BUFFER_PROCESSED_FAILED; //XXX: FAILED or OK?
        }

        int inSeq = (int) inBuffer.getSequenceNumber();
        long inRtpTimestamp = inBuffer.getRtpTimeStamp();
        int inPictureId = VP9PayloadDescriptor.getPictureId(inData, inOffset);
        boolean inMarker = (inBuffer.getFlags() & Buffer.FLAG_RTP_MARKER) != 0;
        boolean inIsStartOfFrame = VP9PayloadDescriptor.isStartOfFrame(inData, inOffset, inLength);

        int inPdSize = VP9PayloadDescriptor.getSize(inData, inOffset, inLength);
        int inPayloadLength = inLength - inPdSize;

        if (empty && lastSentSeq != -1
                && RTPUtils.sequenceNumberComparator.compare(inSeq, lastSentSeq) != 1) {
            Timber.d("Discarding old packet (while empty) %s", inSeq);
            outBuffer.setDiscard(true);
            return BUFFER_PROCESSED_OK;
        }

        if (!empty) {
            // if the incoming packet has a different PictureID or timestamp than those of the
            // current frame, then it belongs to a different frame.
            if ((inPictureId != -1 && pictureId != -1
                    && inPictureId != pictureId)
                    | (timestamp != -1 && inRtpTimestamp != -1
                    && inRtpTimestamp != timestamp)) {
                //inSeq <= firstSeq
                if (RTPUtils.sequenceNumberComparator.compare(inSeq, firstSeq) != 1) {
                    // the packet belongs to a previous frame. discard it
                    Timber.i("Discarding old packet %s", inSeq);
                    outBuffer.setDiscard(true);
                    return BUFFER_PROCESSED_OK;
                }
                else //inSeq > firstSeq (and also presumably isSeq > lastSeq)
                {
                    // the packet belongs to a subsequent frame (to the one
                    // currently being held). Drop the current frame.

                    Timber.i("Discarding saved packets on arrival of a packet for a subsequent frame: %s", inSeq);

                    // TODO: this would be the place to complain about the
                    // not-well-received PictureID by sending a RTCP SLI or NACK.
                    reinit();
                }
            }
        }

        // a whole frame in a single packet. avoid the extra copy to this.data and output it immediately.
        if (empty && inMarker && inIsStartOfFrame) {
            byte[] outData = validateByteArraySize(outBuffer, inPayloadLength, false);
            System.arraycopy(
                    inData,
                    inOffset + inPdSize,
                    outData,
                    0,
                    inPayloadLength);
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

        System.arraycopy(
                inData,
                inOffset + inPdSize,
                container.buf,
                0,
                inPayloadLength);
        container.len = inPayloadLength;
        data.put(inSeq, container);

        // update fields
        frameLength += inPayloadLength;
        if (firstSeq == -1
                || (RTPUtils.sequenceNumberComparator.compare(firstSeq, inSeq) == 1))
            firstSeq = inSeq;
        if (lastSeq == -1
                || (RTPUtils.sequenceNumberComparator.compare(inSeq, lastSeq) == 1))
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
     * Returns true if the buffer contains a VP9 key frame at offset <tt>offset</tt>.
     *
     * @param buf the byte buffer to check
     * @param off the offset in the byte buffer where the actual data starts
     * @param len the length of the data in the byte buffer
     * @return true if the buffer contains a VP9 key frame at offset <tt>offset</tt>.
     */
    public static boolean isKeyFrame(byte[] buf, int off, int len)
    {
        return VP9PayloadDescriptor.isKeyFrame(buf, off, len);
    }

    /**
     * A class that represents the VP9 Payload Descriptor structure defined
     * in {@link "https://tools.ietf.org/html/draft-ietf-payload-vp9-10"}
     *
     * When F-bit == 1:
     *
     * 0 1 2 3 4 5 6 7
     * +-+-+-+-+-+-+-+-+
     * |I|P|L|F|B|E|V|-| (REQUIRED)
     * +-+-+-+-+-+-+-+-+
     * I:   |M| PICTURE ID  | (REQUIRED)
     * +-+-+-+-+-+-+-+-+
     * M:   | EXTENDED PID  | (RECOMMENDED)
     * +-+-+-+-+-+-+-+-+
     * L:   |  T  |U|  S  |D| (CONDITIONALLY RECOMMENDED)
     * +-+-+-+-+-+-+-+-+                             -\
     * P,F: | P_DIFF      |N| (CONDITIONALLY REQUIRED)    - up to 3 times
     * +-+-+-+-+-+-+-+-+                             -/
     * V:   | SS            |
     * | ..            |
     * +-+-+-+-+-+-+-+-+
     *
     * When F-bit == 0:
     *
     * 0 1 2 3 4 5 6 7
     * +-+-+-+-+-+-+-+-+
     * |I|P|L|F|B|E|V|-| (REQUIRED)
     * +-+-+-+-+-+-+-+-+
     * I:   |M| PICTURE ID  | (RECOMMENDED)
     * +-+-+-+-+-+-+-+-+
     * M:   | EXTENDED PID  | (RECOMMENDED)
     * +-+-+-+-+-+-+-+-+
     * L:   |  T  |U|  S  |D| (CONDITIONALLY RECOMMENDED)
     * +-+-+-+-+-+-+-+-+
     * |   TL0PICIDX   | (CONDITIONALLY REQUIRED)
     * +-+-+-+-+-+-+-+-+
     * V:   | SS            |
     * | ..            |
     * +-+-+-+-+-+-+-+-+
     */
    public static class VP9PayloadDescriptor
    {
        /**
         * I: Picture ID (PID) present; bit from the first byte of the Payload Descriptor.
         */
        private static final byte I_BIT = (byte) 0x80;
        ;

        /**
         * P: Inter-picture predicted layer frame; bit from the first byte of the Payload Descriptor.
         */
        private static final byte P_BIT = (byte) 0x40;

        /**
         * L: Layer indices present; bit from the first byte of the Payload Descriptor.
         */
        private static final byte L_BIT = (byte) 0x20;

        /**
         * F: Flexible mode; bit from the first byte of the Payload Descriptor.
         */
        private static final byte F_BIT = (byte) 0x10;

        /**
         * B: Start of a layer frame; bit from the first byte of the Payload Descriptor.
         */
        private static final byte B_BIT = (byte) 0x08;

        /**
         * E: End of a layer frame; bit from the first byte of the Payload Descriptor.
         */
        private static final byte E_BIT = (byte) 0x04;

        /**
         * E: Scalability structure (SS) data present; bit from the first byte of the Payload Descriptor.
         */
        private static final byte V_BIT = (byte) 0x02;

        /**
         * M: Extended flag; bit from the PID.
         */
        private static final byte M_BIT = (byte) 0x80;

        /**
         * Mask for TID value from Layer Indices byte of the Payload Descriptor.
         */
        private static final byte TID_MASK = (byte) 0xE0;

        /**
         * Mask for SID value from Layer Indices byte of the Payload Descriptor.
         */
        private static final byte SID_MASK = 0x0E;

        /**
         * Mask for D value from Layer Indices byte of the Payload Descriptor.
         */
        private static final byte D_MASK = 0x01;

        /**
         * Maximum length of a VP9 Payload Descriptor pending:
         * a. VP9 Payload Description - SS = 5
         * V: Scalability structure (SS) data = (N_S + 1) * 7 (max) = 8 * 7
         */
        public static final int MAX_LENGTH = 5 + (8 * 7);

        /**
         * Returns <tt>true</tt> if the B bit from the first byte of the payload descriptor has value 0.
         *
         * @param buf the byte buffer that holds the VP9 payload.
         * @param off the offset in the byte buffer where the VP9 payload starts.
         * @param len the length of the VP9 payload.
         * @return <tt>true</tt> if the B bit from the first byte of the payload descriptor has value 0, false otherwise.
         */
        public static boolean isStartOfFrame(byte[] buf, int off, int len)
        {
            // Check if this is the start of a VP9 layer frame in the payload descriptor.
            return isValid(buf, off, len) && (buf[off] & B_BIT) != 0;
        }

        /**
         * Returns <tt>true</tt> if the E bit from the first byte of the payload descriptor has value 0.
         *
         * @param buf the byte buffer that holds the VP9 payload.
         * @param off the offset in the byte buffer where the VP9 payload starts.
         * @param len the length of the VP9 payload.
         * @return <tt>true</tt> if the E bit from the first byte of the payload descriptor has value 0, false otherwise.
         */
        public static boolean isEndOfFrame(byte[] buf, int off, int len)
        {
            // Check if this is the end of a VP9 layer frame in the payload descriptor.
            return isValid(buf, off, len) && (buf[off] & E_BIT) != 0;
        }

        /**
         * Gets the temporal layer index (TID), if that's set.
         *
         * @param buf the byte buffer that holds the VP9 payload.
         * @param off the offset in the byte buffer where the VP9 payload starts.
         * @param len the length of the VP9 payload.
         * @return the temporal layer index (TID), if that's set, -1 otherwise.
         */
        public static int getTemporalLayerIndex(byte[] buf, int off, int len)
        {
            if (!isValid(buf, off, len) || (buf[off] & L_BIT) == 0) {
                return -1;
            }

            int loff = off + 1;
            if ((buf[off] & I_BIT) != 0) {
                loff++;
                // check if it it an extended pid.
                if ((buf[off + 1] & M_BIT) != 0) {
                    loff++;
                }
            }
            return (buf[loff] & TID_MASK) >> 5;
        }

        /**
         * Gets the spatial layer index (SID), if that's set.
         *
         * @param buf the byte buffer that holds the VP9 payload.
         * @param off the offset in the byte buffer where the VP9 payload starts.
         * @param len the length of the VP9 payload.
         * @return the spatial layer index (SID), if that's set, -1 otherwise.
         */
        public static int getSpatialLayerIndex(byte[] buf, int off, int len)
        {
            if (!isValid(buf, off, len) || (buf[off] & L_BIT) == 0) {
                return -1;
            }
            int loff = off + 1;
            if ((buf[off] & I_BIT) != 0) {
                loff++;
                // check if it is an extended pid.
                if ((buf[off + 1] & M_BIT) != 0) {
                    loff++;
                }
            }
            return (buf[loff] & SID_MASK) >> 1;
        }

        /**
         * Check if the current the packet contains a key frame:
         *
         * A key picture is a picture whose base spatial layer frame is a key frame,
         * and which thus completely resets the encoder state. This packet will have:
         * P bit equal to zero,
         * SID or D bit (described below) equal to zero,
         * and B bit (described below) equal to 1.
         *
         * @param buf the byte buffer that holds the VP9 payload.
         * @param off the offset in the byte buffer where the VP9 payload starts.
         * @param len the length of the VP9 payload.
         * @return true if the frame is a key frame, false otherwise.
         */
        public static boolean isKeyFrame(byte[] buf, int off, int len)
        {
            // This packet will have its P bit equal to zero, SID or D bit (described below)
            // equal to zero, and B bit (described below) equal to 1

            if (!isValid(buf, off, len)) {
                return false;
            }

            // P_BIT must be 0 and B_BIT 1
            // L_BIT must be 1 to ensure we can do further checks for SID and D
            if ((buf[off] & P_BIT) != 0 ||
                    (buf[off] & B_BIT) == 0 ||
                    (buf[off] & L_BIT) == 0) {
                return false;
            }

            int loff = off + 1;
            if ((buf[off] & I_BIT) != 0) {
                loff += 1;
                if ((buf[off + 1] & (1 << 7)) != 0) {
                    // extended pid.
                    loff += 1;
                }
            }
            //SID or D bit equal to zero
            return ((buf[loff] & SID_MASK) >> 1) == 0 || (buf[loff] & D_MASK) == 0;
        }

        /**
         * Returns a simple Payload Descriptor, the 'start of a Frame' bit set
         * according to <tt>startOfFrame</tt>, and all other bits set to 0.
         *
         * @param startOfFrame whether to 'start of a Frame' bit should be set
         * @return a simple Payload Descriptor, with 'start of a Frame' bit set
         * according to <tt>startOfFrame</tt>, and all other bits set to 0.
         */
        public static byte[] create(boolean startOfFrame)
        {
            byte[] pd = new byte[1];
            pd[0] = startOfFrame ? B_BIT : 0;
            return pd;
        }

        /**
         * The size in bytes of the Payload Descriptor at offset <tt>offset</tt> in <tt>input</tt>.
         *
         * @param baBuffer the <tt>ByteArrayBuffer</tt> that holds the VP9 payload descriptor.
         * @return The size in bytes of the Payload Descriptor at offset
         * <tt>offset</tt> in <tt>input</tt>, or -1 if the input is not a valid VP9 Payload Descriptor.
         */
        public static int getSize(ByteArrayBuffer baBuffer)
        {
            if (baBuffer == null) {
                return -1;
            }
            return getSize(baBuffer.getBuffer(), baBuffer.getOffset(), baBuffer.getLength());
        }

        /**
         * The size in bytes of the Payload Descriptor at offset <tt>off</tt> in <tt>buf</tt>.
         *
         * @param buf the byte buffer that contains the VP9 payload.
         * @param off the offset in the byte buffer where the VP9 payload starts.
         * @param len the length of the VP9 payload in the byte buffer.
         * @return The size in bytes of the Payload Descriptor at offset
         * <tt>offset</tt> in <tt>input</tt>, or -1 if the input is not a valid VP9 Payload Descriptor.
         */
        public static int getSize(byte[] buf, int off, int len)
        {
            // Y-bit from the Scalability Structure (SS)_header: spatial layer's frame resolution present flag.
            final byte Y_BIT = (byte) 0x10; //Each spatial layer's frame resolution present

            // Y bit from the ss_header: GOF description present flag.
            final byte G_BIT = (byte) 0x08;

            // Value N_S mask: the number of spatial layers in SS group.
            final byte N_S_MASK = (byte) 0xC0;


            if (!isValid(buf, off, len))
                return -1;

            int size = 1;
            // Picture ID (PID) present
            if ((buf[off] & I_BIT) != 0) {
                size += ((buf[off + 1] & M_BIT) != 0) ? 2 : 1;
            }

            // Layer indices size with F_BIT
            if ((buf[off] & L_BIT) != 0) {
                size += ((buf[off] & (F_BIT)) != 0) ? 1 : 2;
            }

            // Scalability structure (SS) data present
            if ((buf[off] & V_BIT) != 0) {
                byte ss_header = buf[size - 1];

                // number of spatial layers present in the VP9 stream
                int ssl_size = (ss_header & N_S_MASK) >> 5;

                // frame resolution
                int ss_size = (ss_header & Y_BIT) != 0 ? 4 : 0;

                // GOF description
                if ((ss_header & G_BIT) != 0) {
                    ss_size++;
                    if (buf[size + ss_size] != 0) {
                        ss_size += 2;
                    }
                }
                size += ssl_size * ss_size;
            }
            return size;
        }

        /**
         * Determines whether the VP9 payload specified in the buffer that is
         * passed as an argument has a picture ID or not.
         *
         * @param buf the byte buffer that contains the VP9 payload.
         * @param off the offset in the byte buffer where the VP9 payload starts.
         * @param len the length of the VP9 payload in the byte buffer.
         * @return true if the VP9 payload contains a picture ID, false otherwise.
         */
        public static boolean hasPictureId(byte[] buf, int off, int len)
        {
            return isValid(buf, off, len) && (buf[off] & I_BIT) != 0;
        }

        /**
         * Determines whether the VP9 payload specified in the buffer that is
         * passed as an argument has an extended picture ID or not.
         *
         * @param buf the byte buffer that contains the VP9 payload.
         * @param off the offset in the byte buffer where the VP9 payload starts.
         * @param len the length of the VP9 payload in the byte buffer.
         * @return true if the VP9 payload contains an extended picture ID, false otherwise.
         */
        public static boolean hasExtendedPictureId(byte[] buf, int off, int len)
        {
            return hasPictureId(buf, off, len) && ((buf[off + 1] & M_BIT) != 0);
        }

        /**
         * Gets the value of the PictureID field of a VP9 Payload Descriptor.
         *
         * @param buf the byte buffer that contains the VP9 payload.
         * @param off he offset in the byte buffer where the VP9 payload starts.
         * @return the value of the PictureID field of a VP9 Payload Descriptor,
         * or -1 if the fields is not present.
         */
        public static int getPictureId(byte[] buf, int off)
        {
            if (buf == null
                    || !hasPictureId(buf, off, buf.length - off)) {
                return -1;
            }

            boolean isLong = (buf[off + 1] & M_BIT) != 0;
            if (isLong)
                return (buf[off + 1] & 0x7f) << 8
                        | (buf[off + 2] & 0xff);
            else
                return buf[off + 1] & 0x7f;
        }

        /**
         * Sets the extended picture ID for the VP9 payload specified in the
         * buffer that is passed as an argument. (cmeng: Need to update for VP9)
         *
         * @param buf the byte buffer that contains the VP9 payload.
         * @param off the offset in the byte buffer where the VP9 payload starts.
         * @param len the length of the VP9 payload in the byte buffer.
         * @return true if the operation succeeded, false otherwise.
         */
        public static boolean setExtendedPictureId(byte[] buf, int off, int len, int val)
        {
            if (!hasExtendedPictureId(buf, off, len)) {
                return false;
            }

            buf[off + 1] = (byte) (0x80 | (val >> 8) & 0x7F);
            buf[off + 2] = (byte) (val & 0xFF);

            return true;
        }

        /**
         * Sets the TL0PICIDX field for the VP9 payload specified in the buffer that is passed as an argument.
         *
         * @param buf the byte buffer that contains the VP9 payload.
         * @param off the offset in the byte buffer where the VP9 payload starts.
         * @param len the length of the VP9 payload in the byte buffer.
         * @return true if the operation succeeded, false otherwise.
         */
        public static boolean setTL0PICIDX(byte[] buf, int off, int len, int val)
        {
            if (!isValid(buf, off, len)
                    || (buf[off] & F_BIT) != 0 || (buf[off] & L_BIT) == 0) {
                return false;
            }

            int offTL0PICIDX = 2;
            if ((buf[off + 1] & I_BIT) != 0) {
                offTL0PICIDX++;
                if ((buf[off + 1] & M_BIT) != 0) {
                    offTL0PICIDX++;
                }
            }

            buf[off + offTL0PICIDX] = (byte) val;
            return true;
        }

        /**
         * Returns <tt>true</tt> if the arguments specify a valid non-empty buffer.
         *
         * @param buf the byte buffer that holds the VP9 payload.
         * @param off the offset in the byte buffer where the VP9 payload starts.
         * @param len the length of the VP9 payload.
         * @return <tt>true</tt> if the arguments specify a valid non-empty buffer.
         */
        private static boolean isValid(byte[] buf, int off, int len)
        {
            return ((buf != null) && (buf.length >= (off + len)) && (off > -1) && (len > 0));
        }

        /**
         * Return boolean indicates whether the non-reference bit is set.
         *
         * @param buf the byte buffer that holds the VP9 payload descriptor.
         * @param off the offset in the byte buffer where the payload descriptor starts.
         * @param len the length of the payload descriptor in the byte buffer.
         * @return true if the non-reference bit is NOT set, false otherwise.
         */
        public static boolean isReference(byte[] buf, int off, int len)
        {
            return (((buf[off] & P_BIT) != 0) && ((buf[off] & F_BIT) != 0));
        }

        /**
         * Gets the TL0PICIDX from the payload descriptor.
         *
         * @param buf the byte buffer that holds the VP9 payload descriptor.
         * @param off the offset in the byte buffer where the payload descriptor starts.
         * @param len the length of the payload descriptor in the byte buffer.
         * @return the TL0PICIDX from the payload descriptor.
         */
        public static int getTL0PICIDX(byte[] buf, int off, int len)
        {
            if (!isValid(buf, off, len)
                    || (buf[off] & F_BIT) != 0 || (buf[off] & L_BIT) == 0) {
                return -1;
            }

            int offTL0PICIDX = 2;
            if ((buf[off + 1] & I_BIT) != 0) {
                offTL0PICIDX++;
                if ((buf[off + 1] & M_BIT) != 0) {
                    offTL0PICIDX++;
                }
            }

            return buf[off + offTL0PICIDX];
        }

        /**
         * Provides a string description of the VP9 descriptor that can be used for debugging purposes.
         *
         * @param buf the byte buffer that holds the VP9 payload descriptor.
         * @param off the offset in the byte buffer where the payload descriptor starts.
         * @param len the length of the payload descriptor in the byte buffer.
         * @return Descriptive string of the VP9 info
         */
        public static String toString(byte[] buf, int off, int len)
        {
            return "VP9PayloadDescriptor" +
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
     * A class that represents the VP9 Payload Header structure defined in {@link "https://tools.ietf.org/html/rfc7741"}
     */
    public static class VP9PayloadHeader
    {
        /**
         * P bit of the Payload Descriptor.
         */
        private static final byte P_BIT = (byte) 0x01;

        /**
         * Returns true if the <tt>P</tt> (inverse key frame flag) field of the
         * VP9 Payload Header at offset <tt>offset</tt> in <tt>input</tt> is 0.
         *
         * @return true if the <tt>P</tt> (inverse key frame flag) field of the
         * VP9 Payload Header at offset <tt>offset</tt> in <tt>input</tt> is 0, false otherwise.
         */
        public static boolean isKeyFrame(byte[] input, int offset)
        {
            // When set to 0 the current frame is a key frame.  When set to 1
            // the current frame is an inter-frame. Defined in [RFC6386]

            return (input[offset] & P_BIT) == 0;
        }
    }

    /**
     * A class represents a keyframe header structure (see RFC 6386, paragraph 9.1).
     */
    public static class VP9KeyframeHeader
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
     * A simple container for a <tt>byte[]</tt> and an integer.
     */
    private static class Container
    {
        /**
         * This <tt>Container</tt>'s data.
         */
        private byte[] buf;

        /**
         * Length used.
         */
        private int len = 0;
    }
}
