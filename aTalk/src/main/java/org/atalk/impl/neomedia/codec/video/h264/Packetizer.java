/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.video.h264;

import net.sf.fmj.media.AbstractPacketizer;

import org.atalk.impl.neomedia.codec.AbstractCodec2;
import org.atalk.impl.neomedia.format.ParameterizedVideoFormat;
import org.atalk.impl.neomedia.format.VideoMediaFormatImpl;
import org.atalk.service.neomedia.codec.Constants;

import java.awt.Dimension;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.media.Buffer;
import javax.media.Format;
import javax.media.ResourceUnavailableException;
import javax.media.format.VideoFormat;

/**
 * Packetizes H.264 encoded data/NAL units into RTP packets in accord with RFC 3984
 * "RTP Payload Format for H.264 Video".
 *
 * @author Damian Minkov
 * @author Lyubomir Marinov
 */
public class Packetizer extends AbstractPacketizer {
    /**
     * Maximum payload size without the headers.
     */
    public static final int MAX_PAYLOAD_SIZE = 1024;

    /**
     * Name of the plugin.
     */
    private static final String PLUGIN_NAME = "H264 Packetizer";

    /**
     * The <code>Formats</code> supported by <code>Packetizer</code> instances as output.
     */
    static final Format[] SUPPORTED_OUTPUT_FORMATS = {
            new ParameterizedVideoFormat(
                    Constants.H264_RTP,
                    VideoMediaFormatImpl.H264_PACKETIZATION_MODE_FMTP,
                    "0"),
            new ParameterizedVideoFormat(
                    Constants.H264_RTP,
                    VideoMediaFormatImpl.H264_PACKETIZATION_MODE_FMTP,
                    "1")
    };

    /**
     * Finds the index in <code>byteStream</code> at which the start_code_prefix_one_3bytes of a NAL
     * unit begins.
     *
     * @param byteStream the H.264 encoded byte stream composed of NAL units in which the index of the
     * beginning of the start_code_prefix_one_3bytes of a NAL unit is to be found
     * @param beginIndex the inclusive index in <code>byteStream</code> at which the search is to begin
     * @param endIndex the exclusive index in <code>byteStream</code> at which the search is to end
     *
     * @return the index in <code>byteStream</code> at which the start_code_prefix_one_3bytes of a NAL
     * unit begins if it is found; otherwise, <code>endIndex</code>
     */
    private static int ff_avc_find_startcode(byte[] byteStream, int beginIndex, int endIndex) {
        for (; beginIndex < (endIndex - 3); beginIndex++) {
            if ((byteStream[beginIndex] == 0)
                    && (byteStream[beginIndex + 1] == 0)
                    && (byteStream[beginIndex + 2] == 1)) {
                return beginIndex;
            }
        }
        return endIndex;
    }

    /**
     * The list of NAL units to be sent as payload in RTP packets.
     */
    private final List<byte[]> nals = new LinkedList<>();

    /**
     * The timeStamp of the RTP packets in which <code>nals</code> are to be sent.
     */
    private long nalsTimeStamp;

    /**
     * The sequence number of the next RTP packet to be output by this <code>Packetizer</code>.
     */
    private int sequenceNumber;

    /**
     * Initializes a new <code>Packetizer</code> instance which is to packetize H.264 encoded data/NAL
     * units into RTP packets in accord with RFC 3984 "RTP Payload Format for H.264 Video".
     */
    public Packetizer() {
        inputFormats = JNIEncoder.SUPPORTED_OUTPUT_FORMATS;
        inputFormat = null;
        outputFormat = null;
    }

    /**
     * Close this <code>Packetizer</code>.
     */
    @Override
    public synchronized void close() {
        if (opened) {
            opened = false;
            super.close();
        }
    }

    /**
     * Gets the output formats matching a specific input format.
     *
     * @param input the input format to get the matching output formats for
     *
     * @return an array of output formats matching the specified input format
     */
    private Format[] getMatchingOutputFormats(Format input) {
        VideoFormat videoInput = (VideoFormat) input;
        Dimension size = videoInput.getSize();
        float frameRate = videoInput.getFrameRate();
        String packetizationMode = getPacketizationMode(input);

        return new Format[]{new ParameterizedVideoFormat(
                Constants.H264_RTP,
                size,
                Format.NOT_SPECIFIED,
                Format.byteArray,
                frameRate,
                ParameterizedVideoFormat.toMap(
                        VideoMediaFormatImpl.H264_PACKETIZATION_MODE_FMTP,
                        packetizationMode))
        };
    }

    /**
     * Gets the name of this <code>PlugIn</code>.
     *
     * @return the name of this <code>PlugIn</code>
     */
    @Override
    public String getName() {
        return PLUGIN_NAME;
    }

    /**
     * Gets the value of the <code>packetization-mode</code> format parameter assigned by a specific
     * <code>Format</code>.
     *
     * @param format the <code>Format</code> which assigns a value to the <code>packetization-mode</code> format
     * parameter
     *
     * @return the value of the <code>packetization-mode</code> format parameter assigned by the
     * specified <code>format</code>
     */
    private String getPacketizationMode(Format format) {
        String packetizationMode = null;

        if (format instanceof ParameterizedVideoFormat)
            packetizationMode = ((ParameterizedVideoFormat) format).getFormatParameter(
                    VideoMediaFormatImpl.H264_PACKETIZATION_MODE_FMTP);
        if (packetizationMode == null)
            packetizationMode = "0";
        return packetizationMode;
    }

    /**
     * Return the list of formats supported at the output.
     *
     * @param in input <code>Format</code> to determine corresponding output <code>Format/code>s
     *
     * @return array of formats supported at output
     */
    @Override
    public Format[] getSupportedOutputFormats(Format in) {
        // null input format
        if (in == null)
            return SUPPORTED_OUTPUT_FORMATS;

        // mismatch input format
        if (!(in instanceof VideoFormat)
                || (null == AbstractCodec2.matches(in, inputFormats)))
            return new Format[0];

        return getMatchingOutputFormats(in);
    }

    /**
     * Open this <code>Packetizer</code>.
     *
     * @throws ResourceUnavailableException if something goes wrong during initialization of the Packetizer.
     */
    @Override
    public synchronized void open()
            throws ResourceUnavailableException {
        if (!opened) {
            nals.clear();
            sequenceNumber = 0;
            super.open();
            opened = true;
        }
    }

    /**
     * Packetizes a specific NAL unit of H.264 encoded data so that it becomes ready to be sent as
     * the payload of RTP packets. If the specified NAL unit does not fit into a single RTP
     * packet i.e. will not become a "Single NAL Unit Packet", splits it into "Fragmentation
     * Units (FUs)" of type FU-A.
     *
     * @param nal the bytes which contain the NAL unit of H.264 encoded data to be packetized
     * @param nalOffset the offset in <code>nal</code> at which the NAL unit of H.264 encoded data to be
     * packetized begins
     * @param nalLength the length in <code>nal</code> beginning at <code>nalOffset</code> of the NAL unit of H.264
     * encoded data to be packetized
     *
     * @return <code>true</code> if at least one RTP packet payload has been packetized i.e. prepared
     * for sending; otherwise, <code>false</code>
     */
    private boolean packetizeNAL(byte[] nal, int nalOffset, int nalLength) {
        /*
         * If the NAL fits into a "Single NAL Unit Packet", it's already packetized.
         */
        if (nalLength <= MAX_PAYLOAD_SIZE) {
            byte[] singleNALUnitPacket = new byte[nalLength];

            System.arraycopy(nal, nalOffset, singleNALUnitPacket, 0, nalLength);
            return nals.add(singleNALUnitPacket);
        }

        // Otherwise, split it into "Fragmentation Units (FUs)".
        byte octet = nal[nalOffset];
        int forbidden_zero_bit = octet & 0x80;
        int nri = octet & 0x60;
        int nal_unit_type = octet & 0x1F;

        byte fuIndicator = (byte) (0xFF & (forbidden_zero_bit | nri
                | 28 /* nal_unit_type FU-A */));
        byte fuHeader = (byte) (0xFF & (0x80 /* Start bit */
                | 0 /* End bit */
                | 0 /* Reserved bit */
                | nal_unit_type));

        nalOffset++;
        nalLength--;

        int maxFUPayloadLength = MAX_PAYLOAD_SIZE - 2 /* FU indicator & FU header */;
        boolean nalsAdded = false;

        while (nalLength > 0) {
            int fuPayloadLength;

            if (nalLength > maxFUPayloadLength)
                fuPayloadLength = maxFUPayloadLength;
            else {
                fuPayloadLength = nalLength;
                fuHeader |= 0x40; // Turn on the End bit.
            }

            /*
             * Tests with Asterisk suggest that the fragments of a fragmented NAL unit must be
             * with one and the same size. There is also a similar question on the x264-devel
             * mailing list but, unfortunately, it is unanswered.
             */
            byte[] fua = new byte[2 /* FU indicator & FU header */ + maxFUPayloadLength];

            fua[0] = fuIndicator;
            fua[1] = fuHeader;
            System.arraycopy(nal, nalOffset, fua, 2, fuPayloadLength);
            nalOffset += fuPayloadLength;
            nalLength -= fuPayloadLength;

            nalsAdded = nals.add(fua) || nalsAdded;
            fuHeader &= ~0x80; // Turn off the Start bit.
        }
        return nalsAdded;
    }

    /**
     * Processes (packetize) a buffer.
     *
     * @param inBuffer input buffer
     * @param outBuffer output buffer
     *
     * @return <code>BUFFER_PROCESSED_OK</code> if buffer has been successfully processed
     */
    @Override
    public int process(Buffer inBuffer, Buffer outBuffer) {
        // if there are some nals we check and send them
        if (nals.size() > 0) {
            byte[] nal = nals.remove(0);

            // Send the NAL.
            outBuffer.setData(nal);
            outBuffer.setLength(nal.length);
            outBuffer.setOffset(0);
            outBuffer.setTimeStamp(nalsTimeStamp);
            outBuffer.setSequenceNumber(sequenceNumber++);

            // If there are other NALs, send them as well.
            if (nals.size() > 0)
                return (BUFFER_PROCESSED_OK | INPUT_BUFFER_NOT_CONSUMED);
            else {
                int flags = outBuffer.getFlags() | Buffer.FLAG_RTP_MARKER;

                /*
                 * It's the last NAL of the current frame so mark it. In order to (at least
                 * partially) support feeding this Packetizer one NAL at a time, do NOT always
                 * mark it i.e. the NALs with a value for nal_unit_type which signals that they
                 * cannot be the last NALs in an access unit should probably NOT be marked anyway.
                 */
                if (nal.length > 0) {
                    int nal_unit_type = nal[0] & 0x1F;

                    if ((nal_unit_type == 28 /* FU-A */) && (nal.length > 1)) {
                        byte fuHeader = nal[1];

                        if ((fuHeader & 0x40 /* End bit */) == 0) {
                            /*
                             * A FU-A without the End bit cannot possibly be the last NAL unit of
                             * an access unit.
                             */
                            flags &= ~Buffer.FLAG_RTP_MARKER;
                        }
                        else
                            nal_unit_type = fuHeader & 0x1F;
                    }

                    switch (nal_unit_type) {
                        case 6 /* Supplemental enhancement information (SEI) */:
                        case 7 /* Sequence parameter set */:
                        case 8 /* Picture parameter set */:
                        case 9 /* Access unit delimiter */:
                            flags &= ~Buffer.FLAG_RTP_MARKER;
                            break;
                    }
                }

                outBuffer.setFlags(flags);
                return BUFFER_PROCESSED_OK;
            }
        }

        if (isEOM(inBuffer)) {
            propagateEOM(outBuffer);
            reset();
            return BUFFER_PROCESSED_OK;
        }
        if (inBuffer.isDiscard()) {
            outBuffer.setDiscard(true);
            reset();
            return BUFFER_PROCESSED_OK;
        }

        Format inFormat = inBuffer.getFormat();

        if ((inFormat != inputFormat) && !inFormat.matches(inputFormat))
            setInputFormat(inFormat);

        int inLength = inBuffer.getLength();

        /*
         * We need 3 bytes for start_code_prefix_one_3bytes and at least 1 byte for the NAL unit
         * i.e. its octet serving as the payload header.
         */
        if (inLength < 4) {
            outBuffer.setDiscard(true);
            reset();
            return BUFFER_PROCESSED_OK;
        }

        byte[] inData = (byte[]) inBuffer.getData();
        int inOffset = inBuffer.getOffset();
        boolean nalsAdded = false;

        /*
         * Split the H.264 encoded data into NAL units. Each NAL unit begins with
         * start_code_prefix_one_3bytes. Refer to "B.1 Byte stream NAL unit syntax and semantics"
         * of "ITU-T Rec. H.264 Advanced video coding for generic audiovisual services" for
         * further details.
         */
        int endIndex = inOffset + inLength;
        int beginIndex = ff_avc_find_startcode(inData, inOffset, endIndex);

        if (beginIndex < endIndex) {
            beginIndex += 3;

            for (
                    int nextBeginIndex; (beginIndex < endIndex)
                    && ((nextBeginIndex = ff_avc_find_startcode(
                    inData, beginIndex,
                    endIndex)) <= endIndex); beginIndex = nextBeginIndex + 3) {
                int nalLength = nextBeginIndex - beginIndex;

                // Discard any trailing_zero_8bits.
                while ((nalLength > 0) && (inData[beginIndex + nalLength - 1] == 0)) {
                    nalLength--;
                }

                if (nalLength > 0)
                    nalsAdded = packetizeNAL(inData, beginIndex, nalLength) || nalsAdded;
            }
        }
        nalsTimeStamp = inBuffer.getTimeStamp();
        return nalsAdded ? process(inBuffer, outBuffer) : OUTPUT_BUFFER_NOT_FILLED;
    }

    /**
     * Sets the input format.
     *
     * @param in format to set
     *
     * @return format
     */
    @Override
    public Format setInputFormat(Format in) {
        /*
         * Return null if the specified input Format is incompatible with this Packetizer.
         */
        if (!(in instanceof VideoFormat)
                || null == AbstractCodec2.matches(in, inputFormats))
            return null;

        inputFormat = in;
        return in;
    }

    /**
     * Sets the <code>Format</code> in which this <code>Codec</code> is to output media data.
     *
     * @param format the <code>Format</code> in which this <code>Codec</code> is to output media data
     *
     * @return the <code>Format</code> in which this <code>Codec</code> is currently configured to output
     * media data or <code>null</code> if <code>format</code> was found to be incompatible with this
     * <code>Codec</code>
     */
    @Override
    public Format setOutputFormat(Format format) {
        /*
         * Return null if the specified output Format is incompatible with this Packetizer.
         */
        if (!(format instanceof VideoFormat)
                || (null == AbstractCodec2.matches(format, getMatchingOutputFormats(inputFormat))))
            return null;

        VideoFormat videoFormat = (VideoFormat) format;
        /*
         * A Packetizer translates raw media data in RTP payloads. Consequently, the size of the
         * output is equal to the size of the input.
         */
        Dimension size = null;

        if (inputFormat != null)
            size = ((VideoFormat) inputFormat).getSize();
        if ((size == null) && format.matches(outputFormat))
            size = ((VideoFormat) outputFormat).getSize();

        Map<String, String> fmtps = null;

        if (format instanceof ParameterizedVideoFormat)
            fmtps = ((ParameterizedVideoFormat) format).getFormatParameters();
        if (fmtps == null)
            fmtps = new HashMap<>();
        if (fmtps.get(VideoMediaFormatImpl.H264_PACKETIZATION_MODE_FMTP) == null) {
            fmtps.put(VideoMediaFormatImpl.H264_PACKETIZATION_MODE_FMTP,
                    getPacketizationMode(inputFormat));
        }

        outputFormat = new ParameterizedVideoFormat(
                videoFormat.getEncoding(),
                size,
                Format.NOT_SPECIFIED, /* maxDataLength */
                Format.byteArray,
                videoFormat.getFrameRate(),
                fmtps);

        // Return the outputFormat which is actually set.
        return outputFormat;
    }
}
