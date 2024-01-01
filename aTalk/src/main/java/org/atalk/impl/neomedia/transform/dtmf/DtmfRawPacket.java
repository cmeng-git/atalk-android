/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform.dtmf;

import org.atalk.impl.timberlog.TimberLog;
import org.atalk.service.neomedia.RawPacket;

import timber.log.Timber;

/**
 * <code>DtmfRawPacket</code> represent an RTP Packet. You create your <code>DtmfRawPacket</code> by calling
 * the constructor. You specify the DTMF attributes : code=9, end=false, marker=true ... Then you
 * fill the packet using init( ... dtmf attributes ... );
 *
 * @author Romain Philibert
 * @author Emil Ivov
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
public class DtmfRawPacket extends RawPacket implements Cloneable
{
    /**
     * The event code to send.
     */
    private int code;

    /**
     * Is this an end packet.
     */
    private boolean end;

    /**
     * The duration of the current packet.
     */
    private int duration;

    /**
     * The volume of the current packet.
     */
    private int volume;

    /**
     * Creates a <code>DtmfRawPacket</code> using the specified buffer.
     *
     * @param buffer the <code>byte</code> array that we should use to store packet content
     * @param offset the index where we should start using the <code>buffer</code>.
     * @param length Length of the packet's data.
     * @param payload the payload that has been negotiated for telephone events by our signaling modules.
     */
    public DtmfRawPacket(byte[] buffer, int offset, int length, byte payload)
    {
        super(buffer, offset, length);
        setPayloadType(payload);
    }

    /**
     * Used for incoming DTMF packets, creating <code>DtmfRawPacket</code> from RTP one.
     *
     * @param pkt the RTP packet.
     */
    public DtmfRawPacket(RawPacket pkt)
    {
        super(pkt.getBuffer(), pkt.getOffset(), pkt.getLength());

        int at = getHeaderLength();
        code = readByte(at++);
        byte b = readByte(at++);
        end = (b & 0x80) != 0;
        volume = b & 0x7f;
        duration = ((readByte(at++) & 0xFF) << 8) | (readByte(at++) & 0xFF);
    }

    /**
     * Initializes DTMF specific values in this packet.
     *
     * @param code the DTMF code representing the digit.
     * @param end the DTMF End flag
     * @param marker the RTP Marker flag
     * @param duration the DTMF duration
     * @param timestamp the RTP timestamp
     * @param volume the DTMF volume
     */
    public void init(int code, boolean end, boolean marker, int duration, long timestamp, int volume)
    {
        Timber.log(TimberLog.FINER, "DTMF send on RTP, code: %s duration = %s timestamps = %s Marker = %s End = %s",
                code, duration, timestamp, marker, end);

        // Set the marker
        setMarker(marker);

        // set the Timestamp
        setTimestamp(timestamp);

        // Clear any RTP header extensions
        removeExtension();

        // Create the RTP data
        setDtmfPayload(code, end, duration, volume);
    }

    /**
     * Initializes the  a DTMF raw data using event, E and duration field.
     * Event : the digits to transmit (0-15).
     * E : End field, used to mark the two last packets.
     * R always = 0.
     * Volume always = 0.
     * Duration : duration increments for each dtmf sending updates,
     * stay unchanged at the end for the 3 last packets.
     * <pre>
     *  0                   1                   2                   3
     *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
     *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *  |     event     |E R| volume    |          duration             |
     *  |       ?       |? 0|    0      |              ?                |
     *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * </pre>
     *
     * @param code the digit to transmit 0-15
     * @param end boolean used to mark the two last packets
     * @param duration int increments for each dtmf sending updates, stay unchanged at the end for the 2 last
     * packets.
     * @param volume describes the power level of the tone, expressed in dBm0
     */
    private void setDtmfPayload(int code, boolean end, int duration, int volume)
    {
        this.code = code;
        this.end = end;
        this.duration = duration;
        this.volume = volume;
        int at = getHeaderLength();

        writeByte(at++, (byte) code);
        writeByte(at++, end ? (byte) (volume | 0x80) : (byte) (volume & 0x7f));
        writeByte(at++, (byte) (duration >> 8));
        writeByte(at++, (byte) duration);

        // packet finished setting its payload, set correct length
        setLength(at);
    }

    /**
     * The event code of the current packet.
     *
     * @return the code
     */
    public int getCode()
    {
        return code;
    }

    /**
     * Is this an end packet.
     *
     * @return the end
     */
    public boolean isEnd()
    {
        return end;
    }

    /**
     * The duration of the current event.
     *
     * @return the duration
     */
    public int getDuration()
    {
        return duration;
    }

    /**
     * The volume of the current event.
     *
     * @return the volume
     */
    public int getVolume()
    {
        return volume;
    }

    /**
     * Initializes a new <code>DtmfRawPacket</code> instance which has the same properties as this instance.
     *
     * @return a new <code>DtmfRawPacket</code> instance which has the same properties as this instance
     */
    @Override
    public Object clone()
    {
        RawPacket pkt = new RawPacket(getBuffer().clone(), getOffset(), getLength());
        return new DtmfRawPacket(pkt);
    }
}
