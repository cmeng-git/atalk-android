/*
 * Copyright @ 2017 Atlassian Pty Ltd
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
package org.atalk.impl.neomedia.transform.fec;

import org.atalk.impl.neomedia.transform.PacketTransformer;
import org.atalk.service.configuration.ConfigurationService;
import org.atalk.service.libjitsi.LibJitsi;
import org.atalk.service.neomedia.RawPacket;
import org.atalk.util.RTPUtils;

import java.util.SortedMap;
import java.util.TreeMap;

import timber.log.Timber;

/**
 * A {@link PacketTransformer} which handles incoming fec packets.  This class
 * contains only the generic fec handling logic.
 *
 * @author bgrozev
 * @author bbaldino
 * @author Eng Chong Meng
 */
public abstract class AbstractFECReceiver implements PacketTransformer
{
    /**
     * Statistics for this fec receiver
     */
    protected final Statistics statistics = new Statistics();

    /**
     * The SSRC of the fec stream
     * NOTE that for ulpfec this might be the same as the associated media
     * stream, whereas for flexfec it will be different
     */
    protected long ssrc;

    /**
     * Allow disabling of handling of ulpfec packets for testing purposes.
     */
    protected boolean handleFec = true;

    /**
     * The number of media packets to keep.
     */
    private static final int MEDIA_BUF_SIZE;

    /**
     * The maximum number of ulpfec packets to keep.
     */
    private static final int FEC_BUF_SIZE;

    /**
     * The name of the <tt>ConfigurationService</tt> property which specifies
     * the value of {@link #MEDIA_BUF_SIZE}.
     */
    private static final String MEDIA_BUF_SIZE_PNAME = "neomedia.transform.fec.AbstractFECReciever.MEDIA_BUFF_SIZE";

    /**
     * The name of the <tt>ConfigurationService</tt> property which specifies
     * the value of {@link #FEC_BUF_SIZE}.
     */
    private static final String FEC_BUF_SIZE_PNAME = "neomedia.transform.fec.AbstractFECReciever.FEC_BUFF_SIZE";

    static {
        ConfigurationService cfg = LibJitsi.getConfigurationService();
        int fecBufSize = 32;
        int mediaBufSize = 64;

        if (cfg != null) {
            fecBufSize = cfg.getInt(FEC_BUF_SIZE_PNAME, fecBufSize);
            mediaBufSize = cfg.getInt(MEDIA_BUF_SIZE_PNAME, mediaBufSize);
        }
        FEC_BUF_SIZE = fecBufSize;
        MEDIA_BUF_SIZE = mediaBufSize;
    }

    /**
     * The payload type of the fec stream
     */
    private byte payloadType;

    /**
     * Buffer which keeps (copies of) received media packets.
     *
     * We keep them ordered by their RTP sequence numbers, so that
     * we can easily select the oldest one to discard when the buffer is
     * full (when the map has more than <tt>MEDIA_BUFF_SIZE</tt> entries).
     *
     * We keep them in a <tt>Map</tt> so that we can easily search for a
     * packet with a specific sequence number.
     *
     * Note: This might turn out to be inefficient, especially with increased
     * buffer sizes. In the vast majority of cases (e.g. on every received
     * packet) we do an insert at one end and a delete from the other -- this
     * can be optimized. We very rarely (when we receive a packet out of order)
     * need to insert at an arbitrary location.
     * FIXME: Look at using the existing packet cache instead of our own here
     */
    protected final SortedMap<Integer, RawPacket> mediaPackets = new TreeMap<Integer, RawPacket>(RTPUtils.sequenceNumberComparator);

    /**
     * Buffer which keeps (copies of) received fec packets.
     *
     * We keep them ordered by their RTP sequence numbers, so that
     * we can easily select the oldest one to discard when the buffer is
     * full (when the map has more than <tt>FEC_BUFF_SIZE</tt> entries.
     *
     * We keep them in a <tt>Map</tt> so that we can easily search for a
     * packet with a specific sequence number.
     *
     * Note: This might turn out to be inefficient, especially with increased
     * buffer sizes. In the vast majority of cases (e.g. on every received
     * packet) we do an insert at one end and a delete from the other -- this
     * can be optimized. We very rarely (when we receive a packet out of order)
     * need to insert at an arbitrary location.
     * FIXME: Look at using the existing packet cache instead of our own here
     */
    protected final SortedMap<Integer, RawPacket> fecPackets = new TreeMap<Integer, RawPacket>(RTPUtils.sequenceNumberComparator);


    /**
     * Initialize the FEC receiver
     *
     * @param ssrc the ssrc of the stream on which fec packets will be received
     * @param payloadType the payload type of the fec packets
     */
    AbstractFECReceiver(long ssrc, byte payloadType)
    {
        this.ssrc = ssrc;
        this.payloadType = payloadType;
    }

    /**
     * Saves <tt>p</tt> into <tt>fecPackets</tt>. If the size of
     * <tt>fecPackets</tt> has reached <tt>FEC_BUFF_SIZE</tt> discards the
     * oldest packet from it.
     *
     * @param p the packet to save.
     */
    private void saveFec(RawPacket p)
    {
        if (fecPackets.size() >= FEC_BUF_SIZE)
            fecPackets.remove(fecPackets.firstKey());

        fecPackets.put(p.getSequenceNumber(), p);
    }

    /**
     * Makes a copy of <tt>p</tt> into <tt>mediaPackets</tt>. If the size of
     * <tt>mediaPackets</tt> has reached <tt>MEDIA_BUFF_SIZE</tt> discards
     * the oldest packet from it and reuses it.
     *
     * @param p the packet to copy.
     */
    protected void saveMedia(RawPacket p)
    {
        RawPacket newMedia;
        if (mediaPackets.size() < MEDIA_BUF_SIZE) {
            newMedia = new RawPacket();
            newMedia.setBuffer(new byte[FECTransformEngine.INITIAL_BUFFER_SIZE]);
            newMedia.setOffset(0);
        }
        else {
            newMedia = mediaPackets.remove(mediaPackets.firstKey());
        }

        int pLen = p.getLength();
        if (pLen > newMedia.getBuffer().length) {
            newMedia.setBuffer(new byte[pLen]);
        }

        System.arraycopy(p.getBuffer(), p.getOffset(), newMedia.getBuffer(), 0, pLen);
        newMedia.setLength(pLen);
        newMedia.setOffset(0);

        mediaPackets.put(newMedia.getSequenceNumber(), newMedia);
    }

    /**
     * Sets the ulpfec payload type.
     *
     * @param payloadType the payload type.
     * FIXME(brian): do we need both this and the ability to pass the payload
     * type in the ctor? Can we get rid of this or get rid of the arg in the ctor?
     */
    public void setPayloadType(byte payloadType)
    {
        this.payloadType = payloadType;
    }

    /**
     * {@inheritDoc}
     *
     * Don't touch "outgoing".
     */
    @Override
    public RawPacket[] transform(RawPacket[] pkts)
    {
        return pkts;
    }

    @Override
    public synchronized RawPacket[] reverseTransform(RawPacket[] pkts)
    {
        for (int i = 0; i < pkts.length; ++i) {
            RawPacket pkt = pkts[i];
            if (pkt == null) {
                continue;
            }
            if (pkt.getPayloadType() == payloadType) {
                // Don't forward it
                pkts[i] = null;

                statistics.numRxFecPackets++;
                if (handleFec) {
                    saveFec(pkt);
                }
            }
            else {
                if (handleFec) {
                    saveMedia(pkt);
                }
            }
        }

        pkts = doReverseTransform(pkts);

        return pkts;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close()
    {
        Timber.i("Closing FEC-Receiver for SSRC: %d. Received %d FEC packets, recovered %s media packets. Recovery failed %d times",
                ssrc, statistics.numRxFecPackets, statistics.numRecoveredPackets, statistics.failedRecoveries);
    }

    /**
     * Perform fec receive logic specific to the fec implementation
     *
     * @param pkts the input media packets
     * @return a RawPacket[] containing the given media packets as well as any
     * media packets that were recovered
     */
    protected abstract RawPacket[] doReverseTransform(RawPacket[] pkts);

    class Statistics
    {
        int numRxFecPackets;
        int numRecoveredPackets;
        int failedRecoveries;
    }
}
