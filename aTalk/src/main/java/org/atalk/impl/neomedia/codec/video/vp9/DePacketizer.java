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

import org.atalk.impl.timberlog.TimberLog;
import org.atalk.impl.neomedia.codec.AbstractCodec2;
import org.atalk.service.neomedia.codec.Constants;
import org.atalk.util.ByteArrayBuffer;
import org.atalk.util.RTPUtils;

import java.awt.Dimension;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ArrayBlockingQueue;

import javax.media.Buffer;
import javax.media.ResourceUnavailableException;
import javax.media.format.VideoFormat;

import timber.log.Timber;

/**
 * A depacketizer for VP9 codec which handles Constants.VP9_RTP stream data
 * See {@link "https://tools.ietf.org/html/draft-ietf-payload-vp9-15"}
 *
 * Stores the RTP payloads (VP9 payload descriptor stripped) from RTP packets belonging to a
 * single VP9 compressed frame. Maps an RTP sequence number to a buffer which contains the payload.
 *
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
     * Stores the value of the <code>PictureID</code> field for the VP9 compressed
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
     * Whether we have stored in <code>data</code> the last RTP packet of the VP9
     * compressed frame, parts of which are currently stored in <code>data</code>.
     */
    private boolean haveEnd = false;

    /**
     * Whether we have stored in <code>data</code> the first RTP packet of the VP9
     * compressed frame, parts of which are currently stored in <code>data</code>.
     */
    private boolean haveStart = false;

    /**
     * Stores the sum of the lengths of the data stored in <code>data</code>, that
     * is the total length of the VP9 compressed frame to be constructed.
     */
    private int frameLength = 0;

    /**
     * The sequence number of the last RTP packet, which was included in the output.
     */
    private int lastSentSeq = -1;

    private static int pid = Math.abs(new Random().nextInt());

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
        super("VP9 RTP DePacketizer", VideoFormat.class,
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

        if (!VP9PayloadDescriptor.isValid(inData, inOffset, inLength)) {
            Timber.w("Invalid VP9/RTP packet discarded.");
            outBuffer.setDiscard(true);
            return BUFFER_PROCESSED_FAILED; //XXX: FAILED or OK?
        }

        int inSeq = (int) inBuffer.getSequenceNumber();
        long inRtpTimestamp = inBuffer.getRtpTimeStamp();
        int inPictureId = VP9PayloadDescriptor.getPictureId(inData, inOffset);
        boolean inMarker = (inBuffer.getFlags() & Buffer.FLAG_RTP_MARKER) != 0;
        boolean inIsStartOfFrame = VP9PayloadDescriptor.isStartOfFrame(inData, inOffset, inLength);

        /*
         * inPdSize: inBuffer payload descriptor length, need to be stripped off in filter output
         * inPayloadLength: the actual media frame data length
         */
        int inPdSize = VP9PayloadDescriptor.getSize(inData, inOffset, inLength);
        int inPayloadLength = inLength - inPdSize;


        // Timber.d("VP9: DePacketizer: %s %s %s:\nData: %s", inBuffer.getFormat(), inPdSize, inPayloadLength,
        //        bytesToHex((byte[]) inBuffer.getData(), 32));

        // Timber.d("VP9: %s", VP9PayloadDescriptor.toString(inData, inOffset, inLength));
        // Timber.d("VP9: DePacketizer: %s %s %s %s %s %s %s %s %s %s %s", bytesToHex(inData, 48), inOffset, inLength,
        //        inSeq, Integer.toHexString(inPictureId), inMarker, inIsStartOfFrame, inPdSize, inPayloadLength, empty, lastSentSeq);

        if (empty && lastSentSeq != -1
                && RTPUtils.sequenceNumberComparator.compare(inSeq, lastSentSeq) <= 0) {
            Timber.d("Discarding old packet (while empty) %s <= %s", inSeq, lastSentSeq);
            // resync lastSentSeq = current inSeq
            lastSentSeq = inSeq;
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
     * Returns true if the buffer contains a VP9 key frame at offset <code>offset</code>.
     *
     * @param buf the byte buffer to check
     * @param off the offset in the byte buffer where the actual data starts
     * @param len the length of the data in the byte buffer
     * @return true if the buffer contains a VP9 key frame at offset <code>offset</code>.
     */
    public static boolean isKeyFrame(byte[] buf, int off, int len)
    {
        return VP9PayloadDescriptor.isKeyFrame(buf, off, len);
    }

    /**
     * A class that represents the VP9 Payload Descriptor structure defined
     * in {@link "https://tools.ietf.org/html/draft-ietf-payload-vp9-15"}
     */
    // VP9 format:
    //
    // Payload descriptor for F = 1 (flexible mode)
    //       0 1 2 3 4 5 6 7
    //      +-+-+-+-+-+-+-+-+
    //      |I|P|L|F|B|E|V|Z| (REQUIRED)
    //      +-+-+-+-+-+-+-+-+
    // I:   |M| PICTURE ID  | (RECOMMENDED)
    //      +-+-+-+-+-+-+-+-+
    // M:   | EXTENDED PID  | (RECOMMENDED)
    //      +-+-+-+-+-+-+-+-+
    // L:   |  T  |U|  S  |D| (CONDITIONALLY RECOMMENDED)
    //      +-+-+-+-+-+-+-+-+                             -|
    // P,F: | P_DIFF      |N| (CONDITIONALLY RECOMMENDED)  . up to 3 times
    //      +-+-+-+-+-+-+-+-+                             -|
    // V:   | SS            |
    //      | ..            |
    //      +-+-+-+-+-+-+-+-+
    //
    // Payload descriptor for F = 0 (non-flexible mode)
    //       0 1 2 3 4 5 6 7
    //      +-+-+-+-+-+-+-+-+
    //      |I|P|L|F|B|E|V|Z| (REQUIRED)
    //      +-+-+-+-+-+-+-+-+
    // I:   |M| PICTURE ID  | (RECOMMENDED)
    //      +-+-+-+-+-+-+-+-+
    // M:   | EXTENDED PID  | (RECOMMENDED)
    //      +-+-+-+-+-+-+-+-+
    // L:   |  T  |U|  S  |D| (CONDITIONALLY RECOMMENDED)
    //      +-+-+-+-+-+-+-+-+
    //      |   TL0PICIDX   | (CONDITIONALLY REQUIRED)
    //      +-+-+-+-+-+-+-+-+
    // V:   | SS            |
    //      | ..            |
    //      +-+-+-+-+-+-+-+-+
    //
    // Scalability structure (SS).
    //
    //      +-+-+-+-+-+-+-+-+
    // V:   | N_S |Y|G|-|-|-|
    //      +-+-+-+-+-+-+-+-+              -|
    // Y:   |     WIDTH     | (OPTIONAL)    .
    //      +               +               .
    //      |               | (OPTIONAL)    .
    //      +-+-+-+-+-+-+-+-+               . N_S + 1 times
    //      |     HEIGHT    | (OPTIONAL)    .
    //      +               +               .
    //      |               | (OPTIONAL)    .
    //      +-+-+-+-+-+-+-+-+              -|
    // G:   |      N_G      | (OPTIONAL)
    //      +-+-+-+-+-+-+-+-+                           -|
    // N_G: |  T  |U| R |-|-| (OPTIONAL)                 .
    //      +-+-+-+-+-+-+-+-+              -|            . N_G times
    //      |    P_DIFF     | (OPTIONAL)    . R times    .
    //      +-+-+-+-+-+-+-+-+              -|           -|
    //

    public static class VP9PayloadDescriptor
    {
        /**
         * I: Picture ID (PID) present; bit from the first byte of the Payload Descriptor.
         */
        private static final byte I_BIT = (byte) 0x80;

        /**
         * P: Inter-picture predicted layer frame; bit from the first byte of the Payload Descriptor.
         */
        private static final byte P_BIT = (byte) 0x40;

        /**
         * L: Layer indices present; bit from the first byte of the Payload Descriptor.
         */
        private static final byte L_BIT = (byte) 0x20;

        /**
         * F: The Flexible mode; bit from the first byte of the Payload Descriptor.
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
         * Z: Not a reference frame for upper spatial layers.; bit from the first byte of the Payload Descriptor.
         */
        private static final byte Z_BIT = (byte) 0x01;

        /**
         * M: The Extended flag; bit from the PID.
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
         * a. VP9 Payload Description - SS = 4 + 3 = 7
         * V: Scalability structure (SS) data = V + (N_S + 1) * 4 + G + N_G * (1 + 3)
         * = 1 + (8 * 4) + 1 + (255 * (1 + 3))
         * = 34 + (255 * 4)
         */
        // public static final int MAX_LENGTH = 7 + 34 + (255 * 4); // 1061 or webric(1200)
        public static final int MAX_LENGTH = 23;  // practical length in aTalk

        /**
         * Returns <code>true</code> if the B bit from the first byte of the payload descriptor has value 0.
         *
         * @param buf the byte buffer that holds the VP9 payload.
         * @param off the offset in the byte buffer where the VP9 payload starts.
         * @param len the length of the VP9 payload.
         * @return <code>true</code> if the B bit from the first byte of the payload descriptor has value 0, false otherwise.
         */
        public static boolean isStartOfFrame(byte[] buf, int off, int len)
        {
            // Check if this is the start of a VP9 layer frame in the payload descriptor.
            return isValid(buf, off, len) && (buf[off] & B_BIT) != 0;
        }

        /**
         * Returns <code>true</code> if the E bit from the first byte of the payload descriptor has value 0.
         *
         * @param buf the byte buffer that holds the VP9 payload.
         * @param off the offset in the byte buffer where the VP9 payload starts.
         * @param len the length of the VP9 payload.
         * @return <code>true</code> if the E bit from the first byte of the payload descriptor has value 0, false otherwise.
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
                // check if it is an extended pid.
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
         * Check if the current packet contains a key frame:
         *
         * A key picture is a picture whose base spatial layer frame is a key frame,
         * and which thus completely resets the encoder state. This packet will have:
         * a. P bit equal to zero,
         * b. SID or D bit (described below) equal to zero, and
         * c. B bit (described below) equal to 1.
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

            // P_BIT must be 0 and B_BIT is 1
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
                    // an extended pid.
                    loff += 1;
                }
            }
            // SID or D bit equal to zero
            return ((buf[loff] & SID_MASK) >> 1) == 0 || (buf[loff] & D_MASK) == 0;
        }

        /**
         * Returns a simple Payload Descriptor, the 'start of a Frame' bit set
         * according to <code>startOfFrame</code>, and all other bits set to 0.
         *
         * @param startOfFrame create start of frame header with B-bit set and more header info
         * @return a simple Payload Descriptor, with 'start of a Frame' bit set
         * according to <code>startOfFrame</code>, and all other bits set to 0.
         */
        // SYNC_CODE /* equal to 0x498342 */
        // D/(DePacketizer.java:214)#doProcess: VP9: DePacketizer: VP9/RTP, fmtps={} 11 1032:
        //        Data: 00000000 00000000 00000000 8BCE9818 019202D0 01040182 49834200 19102CF4
        // D/(DePacketizer.java:214)#doProcess: VP9: DePacketizer: VP9/RTP, fmtps={} 3 1041:
        //        Data: 00000000 00000000 00000000 81CE98F4 531AE1CE 91275C60 5977EED3 5F205A9A
        // D/(DePacketizer.java:214)#doProcess: VP9: DePacketizer: VP9/RTP, fmtps={} 3 1041:
        //        Data: 00000000 00000000 00000000 81CE9876 68DA0B96 04CD716D FCA00918 6B855DE4
        // D/(DePacketizer.java:214)#doProcess: VP9: DePacketizer: VP9/RTP, fmtps={} 3 1041:
        //        Data: 00000000 00000000 00000000 85CE9889 CC970F97 DEF46D09 0DD8D5F8 44B2E8DC
        // D/(DePacketizer.java:214)#doProcess: VP9: DePacketizer: VP9/RTP, fmtps={} 11 1168:
        //        Data: 00000000 00000000 00000000 8BCE9918 019202D0 01040182 49834200 19102CF4
        // D/(DePacketizer.java:214)#doProcess: VP9: DePacketizer: VP9/RTP, fmtps={} 3 1176:
        //        Data: 00000000 00000000 00000000 81CE998B B67D0750 CE0C8CE9 82B77952 9C91D8E6
        // D/(DePacketizer.java:214)#doProcess: VP9: DePacketizer: VP9/RTP, fmtps={} 3 1176:
        //        Data: 00000000 00000000 00000000 81CE9904 6156411E 965433C6 8B122BBF 235A6944
        // D/(DePacketizer.java:214)#doProcess: VP9: DePacketizer: VP9/RTP, fmtps={} 3 1177:
        //        Data: 00000000 00000000 00000000 85CE99C5 A7447E9D E6AC11B1 3E7E75A7 6A0E68B2
        public static byte[] create(boolean startOfFrame, Dimension size)
        {
            byte[] pd;
            if (startOfFrame) {
                pid += 1;
                pd = new byte[]{(byte) 0x8B, 0x00, 0x00,
                        (byte) 0x18, 0x00, 0x00, 0x00, 0x00, (byte) 0x01, (byte) 0x04, (byte) 0x01};

                pd[4] = (byte) ((size.width & 0xFF00) >> 8);
                pd[5] = (byte) (size.width & 0xFF);
                pd[6] = (byte) ((size.height & 0xFF00) >> 8);
                pd[7] = (byte) (size.height & 0xFF);
            }
            else {
                pd = new byte[]{(byte) 0x81, 0x00, 0x00};
            }

            pd[1] = (byte) (0x80 | ((pid & 0x7F00) >> 8));
            pd[2] = (byte) (pid & 0xFF);
            return pd;
        }

        /**
         * The size in bytes of the Payload Descriptor at offset <code>offset</code> in <code>input</code>.
         *
         * @param baBuffer the <code>ByteArrayBuffer</code> that holds the VP9 payload descriptor.
         * @return The size in bytes of the Payload Descriptor at offset
         * <code>offset</code> in <code>input</code>, or -1 if the input is not a valid VP9 Payload Descriptor.
         */
        public static int getSize(ByteArrayBuffer baBuffer)
        {
            if (baBuffer == null) {
                return -1;
            }
            return getSize(baBuffer.getBuffer(), baBuffer.getOffset(), baBuffer.getLength());
        }

        /**
         * The size in bytes of the Payload Descriptor at offset <code>off</code> in <code>buf</code>.
         *
         * @param buf the byte buffer that contains the VP9 payload.
         * @param off the offset in the byte buffer where the VP9 payload starts.
         * @param len the length of the VP9 payload in the byte buffer.
         * @return The size in bytes of the Payload Descriptor at offset
         * <code>offset</code> in <code>input</code>, or -1 if the input is not a valid VP9 Payload Descriptor.
         */
        public static int getSize(byte[] buf, int off, int len)
        {
            // Y-bit from the Scalability Structure (SS)_header: spatial layer's frame resolution present flag.
            final byte Y_BIT = (byte) 0x10; // Each spatial layer's frame resolution present

            // Y bit from the ss_header: GOF description present flag.
            final byte G_BIT = (byte) 0x08;

            // Value N_S mask: the number of spatial layers in SS group.
            final byte N_S_MASK = (byte) 0xE0;

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
                byte ss_header = buf[off + size];

                // number of spatial layers present in the VP9 stream i.e. N_S + 1
                int ns_size = (ss_header & N_S_MASK) >> 5;
                // Timber.d("ss_header: %s %s %s", size, Integer.toHexString(ss_header), ns_size);

                // frame resolution: width x height
                int y_size = (ss_header & Y_BIT) != 0 ? 4 : 0;
                size += (ns_size + 1) * y_size + 1;  // + V-Byte

                // PG description
                if ((ss_header & G_BIT) != 0) {
                    int ng_size = buf[off + size];
                    size += ng_size * 2 + 1; // N_G-Byte
                }
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
         * Returns <code>true</code> if the arguments specify a valid non-empty buffer.
         *
         * @param buf the byte buffer that holds the VP9 payload.
         * @param off the offset in the byte buffer where the VP9 payload starts.
         * @param len the length of the VP9 payload.
         * @return <code>true</code> if the arguments specify a valid non-empty buffer.
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
                    ", pid=" + Integer.toHexString(getPictureId(buf, off)) +
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
         * Returns true if the <code>P</code> (inverse key frame flag) field of the
         * VP9 Payload Header at offset <code>offset</code> in <code>input</code> is 0.
         *
         * @return true if the <code>P</code> (inverse key frame flag) field of the
         * VP9 Payload Header at offset <code>offset</code> in <code>input</code> is 0, false otherwise.
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
