/*
 * Copyright @ 2015 - present 8x8, Inc
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
package org.atalk.impl.neomedia.transform.srtp.utils;


import org.atalk.util.ByteArrayBuffer;

import java.util.*;

import static org.atalk.util.ByteArrayUtils.readInt;
import static org.atalk.util.ByteArrayUtils.readUint16;

/**
 * SrtpPacket is the low-level utilities to get the data fields needed by SRTP.
 */
public class SrtpPacketUtils
{
    /**
     * The size of the fixed part of the RTP header as defined by RFC 3550.
     */
    private static final int FIXED_HEADER_SIZE = 12;

    /**
     * The size of the fixed part of the extension header as defined by RFC 3550.
     */
    private static final int EXT_HEADER_SIZE = 4;


    /**
     * Returns <tt>true</tt> if the extension bit of an SRTP packet has been set
     * and <tt>false</tt> otherwise.
     *
     * @param buf The SRTP packet.
     * @return  <tt>true</tt> if the extension bit of this packet has been set
     * and <tt>false</tt> otherwise.
     */
    static boolean getExtensionBit(ByteArrayBuffer buf)
    {
        byte[] buffer = buf.getBuffer();
        int offset = buf.getOffset();

        return (buffer[offset] & 0x10) == 0x10;
    }

    /**
     * Returns the number of CSRC identifiers included in an SRTP packet.
     *
     * Note: this does not verify that the packet is indeed long enough for the claimed number of CSRCs.
     *
     * @param buf The SRTP packet.
     *
     * @return the CSRC count for this packet.
     */
    static int getCsrcCount(ByteArrayBuffer buf)
    {
        byte[] buffer = buf.getBuffer();
        int offset = buf.getOffset();

        return buffer[offset] & 0x0f;
    }

    /**
     * Returns the length of the variable-length part of the header extensions present in an SRTP packet.
     *
     * Note: this does not verify that the header extension bit is indeed set, nor that the packet is long
     * enough for the header extension specified.
     *
     * @param buf The SRTP packet.
     * @return the length of the extensions present in this packet.
     */
    public static int getExtensionLength(ByteArrayBuffer buf)
    {
        int cc = getCsrcCount(buf);

        // The extension length comes after the RTP header, the CSRC list, and
        // two bytes in the extension header called "defined by profile".
        int extLenIndex = FIXED_HEADER_SIZE + cc * 4 + 2;

        int len = readUint16(buf, extLenIndex) * 4;

        return len;
    }

    /**
     * Reads the sequence number of an SRTP packet.
     *
     * @param buf The buffer holding the SRTP packet.
     */
    public static int getSequenceNumber(ByteArrayBuffer buf)
    {
        return readUint16(buf, 2);
    }

    /**
     * Reads the SSRC of an SRTP packet.
     *
     * @param buf The buffer holding the SRTP packet.
     */
    public static int getSsrc(ByteArrayBuffer buf)
    {
        return readInt(buf, 8);
    }

    /**
     * Validate that the contents of a ByteArrayBuffer could contain a valid SRTP packet.
     *
     * This validates that the packet is long enough to be a valid packet, i.e. attempts to read
     * fields of the packet will not fail.
     *
     * @param buf The buffer holding the SRTP packet.
     * @param authTagLen The length of the packet's authentication tag.
     * @return true if the packet is syntactically valid (i.e., long enough); false if not.
     */
    public static boolean validatePacketLength(ByteArrayBuffer buf, int authTagLen)
    {
        int length = buf.getLength();
        int neededLength = FIXED_HEADER_SIZE + authTagLen;
        if (length < neededLength)
        {
            return false;
        }

        int cc = getCsrcCount(buf);
        neededLength += cc*4;
        if (length < neededLength)
        {
            return false;
        }

        if (getExtensionBit(buf))
        {
            neededLength += EXT_HEADER_SIZE;
            if (length < neededLength)
            {
                return false;
            }

            int extLen = getExtensionLength(buf);
            neededLength += extLen;
            if (length < neededLength)
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Gets the total header length of an SRTP packet.
     *
     * @param buf The buffer holding the SRTP packet.
     */
    public static int getTotalHeaderLength(ByteArrayBuffer buf)
    {
        int length = FIXED_HEADER_SIZE + getCsrcCount(buf)*4;

        if (getExtensionBit(buf))
        {
            length += EXT_HEADER_SIZE + getExtensionLength(buf);
        }

        return length;
    }

    /**
     * Formats the current state of an SRTP/SRTCP replay window, for debugging purposes.
     */
    public static String formatReplayWindow(long maxIdx, long replayWindow, long replayWindowSize)
    {
        StringBuilder out = new StringBuilder();
        Formatter formatter = new Formatter(out);
        formatter.format("maxIdx=%d, window=0x%016x: [", maxIdx, replayWindow);

        boolean printedSomething = false;
        for (long i = replayWindowSize - 1; i >= 0; i--)
        {
            if (((replayWindow >> i) & 0x1) != 0)
            {
                if (printedSomething)
                {
                    out.append(", ");
                }
                printedSomething = true;
                out.append(maxIdx - i);
            }
        }

        out.append("]");

        return out.toString();
    }
}
