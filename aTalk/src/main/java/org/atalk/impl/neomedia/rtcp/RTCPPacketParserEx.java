/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.rtcp;

import net.sf.fmj.media.rtp.*;
import net.sf.fmj.media.rtp.util.BadFormatException;
import net.sf.fmj.media.rtp.util.UDPPacket;

import org.atalk.android.plugin.timberlog.TimberLog;
import org.atalk.service.neomedia.RawPacket;
import org.atalk.service.neomedia.event.RTCPFeedbackMessageEvent;
import org.atalk.service.neomedia.rtp.RTCPExtendedReport;
import org.atalk.util.RTPUtils;

import java.io.*;

import timber.log.Timber;

/**
 * Extends {@link RTCPPacketParser} to allow the parsing of additional RTCP packet types such as
 * REMB, NACK and XR.
 *
 * @author George Politis
 * @author Boris Grozev
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public class RTCPPacketParserEx extends RTCPPacketParser
{
    /**
     * Initializes a new {@code RawPacket} instance from a specific {@code RTCPPacket}.
     *
     * @param rtcp the {@code RTCPPacket} to represent as a {@code RawPacket}
     * @return a new {@code RawPacket} instance which represents the specified {@code rtcp}
     * @throws IOException if an input/output error occurs during the serialization/writing of the binary
     * representation of the specified {@code rtcp}
     */
    public static RawPacket toRawPacket(RTCPPacket rtcp)
            throws IOException
    {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        DataOutputStream dataOutputStream = new DataOutputStream(byteArrayOutputStream);

        rtcp.assemble(dataOutputStream);
        byte[] buf = byteArrayOutputStream.toByteArray();
        return new RawPacket(buf, 0, buf.length);
    }

    public RTCPPacket parse(byte[] data, int offset, int length)
            throws BadFormatException
    {
        UDPPacket udp = new UDPPacket();

        udp.data = data;
        udp.length = length;
        udp.offset = offset;
        udp.received = false;
        return parse(udp);
    }

    /**
     * @param base
     * @param firstbyte the first byte of the RTCP packet
     * @param type the packet type of the RTCP packet
     * @param length the length in bytes of the RTCP packet, including all
     * headers and excluding padding.
     * @param in the binary representation from which the new
     * instance is to be initialized, excluding the first 4 bytes.
     * @return
     * @throws BadFormatException
     * @throws IOException
     */
    @Override
    protected RTCPPacket parse(RTCPCompoundPacket base,
            int firstbyte, int type, int length, DataInputStream in)
            throws BadFormatException, IOException
    {
        if (type == RTCPFBPacket.RTPFB || type == RTCPFBPacket.PSFB) {
/*
	0                   1                   2                   3
    0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   |V=2|P|   FMT   |       PT      |          length               |
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   |                  SSRC of packet sender                        |
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   |                  SSRC of media source                         |
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   :            Feedback Control Information (FCI)                 :
   :                                                               :
*/
            long senderSSRC = in.readInt() & 0xffffffffL;
            long sourceSSRC = in.readInt() & 0xffffffffL;

            if (type == RTCPFBPacket.RTPFB) {
                return parseRTCPFBPacket(base, firstbyte, RTCPFBPacket.RTPFB, length, in,
                        senderSSRC, sourceSSRC);
            }
            else {
                int fmt = firstbyte & 0x1f;
                switch (fmt) {
                    case RTCPREMBPacket.FMT: // REMB
/*
    0                   1                   2                   3
    0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   |V=2|P| FMT=15  |   PT=206      |             length            |
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   |                  SSRC of packet sender                        |
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   |                  SSRC of media source                         |
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   |  Unique identifier 'R' 'E' 'M' 'B'                            |
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   |  Num SSRC     | BR Exp    |  BR Mantissa                      |
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   |   SSRC feedback                                               |
   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
   |  ...                                                          |
 */
                        RTCPREMBPacket remb = new RTCPREMBPacket(base);

                        remb.senderSSRC = senderSSRC;
                        remb.sourceSSRC = sourceSSRC;

                        // Unique identifier 'R' 'E' 'M' 'B'
                        in.readInt();

                        int destlen = in.readUnsignedByte();

                        byte[] buf = new byte[3];
                        in.read(buf);
                        remb.exp = (buf[0] & 0xFC) >> 2;
                        remb.mantissa = ((buf[0] & 0x3) << 16) & 0xFF0000
                                | (buf[1] << 8) & 0x00FF00
                                | buf[2] & 0x0000FF;

                        remb.dest = new long[destlen];
                        for (int i = 0; i < remb.dest.length; i++)
                            remb.dest[i] = in.readInt() & 0xffffffffL;

                        return remb;
                    default:
                        return parseRTCPFBPacket(base, firstbyte, RTCPFBPacket.PSFB, length, in,
                                senderSSRC, sourceSSRC);
                }
            }
        }
        else if (type == RTCPExtendedReport.XR) {
            return new RTCPExtendedReport(firstbyte, type, length, in);
        }
        else {
            return null;
        }
    }

    /**
     * Creates a new {@link RTCPFBPacket} instance.
     *
     * @param base
     * @param firstbyte the first byte of the RTCP packet.
     * @param type the packet type.
     * @param length the length in bytes.
     * @param in
     * @param senderSSRC
     * @param sourceSSRC
     * @return
     * @throws IOException
     */
    private RTCPFBPacket parseRTCPFBPacket(RTCPCompoundPacket base, int firstbyte, int type,
            int length, DataInputStream in, long senderSSRC, long sourceSSRC)
            throws IOException
    {
        RTCPFBPacket fb;

        int fmt = firstbyte & 0x1f;
        if (type == RTCPFBPacket.RTPFB && fmt == NACKPacket.FMT) {
            fb = new NACKPacket(base);
        }
        else if (type == RTCPFBPacket.RTPFB && fmt == RTCPTCCPacket.FMT) {
            fb = new RTCPTCCPacket(base);
        }
        else {
            fb = new RTCPFBPacket(base);
        }

        fb.fmt = fmt;
        fb.type = type;
        fb.senderSSRC = senderSSRC;
        fb.sourceSSRC = sourceSSRC;

        int fcilen = length - 12; // header + ssrc + ssrc = 14

        if (fcilen != 0) {
            fb.fci = new byte[fcilen];
            in.read(fb.fci);
        }

        if (TimberLog.isTraceEnable) {
            String ptStr; // Payload type (PT)
            String fmtStr = null; // Feedback message type (FMT)
            String detailStr = null;

            switch (fb.type) {
                case RTCPFBPacket.PSFB:
                    ptStr = "PSFB";
                    switch (fb.fmt) {
                        case RTCPFeedbackMessageEvent.FMT_FIR:
                            fmtStr = "FIR";
                            break;
                        case RTCPFeedbackMessageEvent.FMT_PLI:
                            fmtStr = "PLI";
                            break;
                        case RTCPREMBPacket.FMT:
                            fmtStr = "REMB";
                            break;
                    }
                    break;
                case RTCPFBPacket.RTPFB:
                    ptStr = "RTPFB";
                    switch (fb.fmt) {
                        case 1: /* Generic NACK */
                            fmtStr = "Generic NACK";
                            break;
                        case 3: /* Temporary Maximum Media Stream Bit Rate Request (TMMBR) */
                            fmtStr = "TMMBR";
                            break;
                        case 4: /* Temporary Maximum Media Stream Bit Rate Notification (TMMBN) */
                            fmtStr = "TMMBN";

                            // Log the TMMBN FCI entries.
                            StringBuilder tmmbnFciEntryStr = new StringBuilder();

                            for (int i = 0, end = fcilen - 8; i < end; i += 8) {
                                int ssrc = RTPUtils.readInt(fb.fci, i);
                                byte b4 = fb.fci[i + 4];
                                int mxTbrExp /* 6 bits */ = (b4 & 0xFC) >>> 2;
                                byte b6 = fb.fci[i + 6];
                                int mxTbrMantissa /* 17 bits */
                                        = (((b4 & 0x1) << 16) & 0xFF0000)
                                        | ((fb.fci[i + 5] << 8) & 0x00FF00)
                                        | (b6 & 0x0000FF);
                                int measuredOverhead /* 9 bits */
                                        = (((b6 & 0x1) << 8) & 0xFF00)
                                        | (fb.fci[i + 7] & 0x00FF);

                                tmmbnFciEntryStr.append(", SSRC 0x");
                                tmmbnFciEntryStr.append(Long.toHexString(ssrc & 0xFFFFFFFFL));
                                tmmbnFciEntryStr.append(", MxTBR Exp ");
                                tmmbnFciEntryStr.append(mxTbrExp);
                                tmmbnFciEntryStr.append(", MxTBR Mantissa ");
                                tmmbnFciEntryStr.append(mxTbrMantissa);
                                tmmbnFciEntryStr.append(", Measured Overhead ");
                                tmmbnFciEntryStr.append(measuredOverhead);
                            }
                            detailStr = tmmbnFciEntryStr.toString();
                            break;
                    }
                    break;
                default:
                    ptStr = Integer.toString(fb.type);
                    break;
            }
            if (fmtStr == null)
                fmtStr = Integer.toString(fb.fmt);
            if (detailStr == null)
                detailStr = "";
            Timber.log(TimberLog.FINER, "SSRC of packet sender: 0x8x (%s), SSRC of media source: 0x8x (%s), Payload type (PT): %s, Feedback message type (FMT): %s%s",
                    senderSSRC, senderSSRC, sourceSSRC, sourceSSRC, ptStr, fmtStr, detailStr);
        }
        return fb;
    }
}
