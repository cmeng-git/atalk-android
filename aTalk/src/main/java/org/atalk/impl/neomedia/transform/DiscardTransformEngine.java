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
package org.atalk.impl.neomedia.transform;

import net.sf.fmj.media.rtp.RTCPPacket;

import org.atalk.android.plugin.timberlog.TimberLog;
import org.atalk.impl.neomedia.rtp.ResumableStreamRewriter;
import org.atalk.service.neomedia.MediaStream;
import org.atalk.service.neomedia.RawPacket;
import org.atalk.util.RTCPUtils;

import java.util.HashMap;
import java.util.Map;

import javax.media.Buffer;

import timber.log.Timber;

/**
 * As the name suggests, the DiscardTransformEngine discards packets that are
 * flagged for discard. The packets that are passed on in the chain have their
 * sequence numbers rewritten hiding the gaps created by the dropped packets.
 * <p>
 * Instances of this class are not thread-safe. If multiple threads access an
 * instance concurrently, it must be synchronized externally.
 *
 * @author George Politis
 * @author Eng Chong Meng
 */
public class DiscardTransformEngine implements TransformEngine
{
    /**
     * A map of source ssrc to {@link ResumableStreamRewriter}.
     */
    private final Map<Long, ResumableStreamRewriter> ssrcToRewriter = new HashMap<>();

    /**
     * The {@link MediaStream} that owns this instance.
     */
    private final MediaStream stream;

    /**
     * Ctor.
     *
     * @param stream the {@link MediaStream} that owns this instance.
     */
    public DiscardTransformEngine(MediaStream stream)
    {
        this.stream = stream;
    }

    /**
     * The {@link PacketTransformer} for RTCP packets.
     */
    private final PacketTransformer rtpTransformer = new SinglePacketTransformerAdapter()
    {
        /**
         * {@inheritDoc}
         */
        @Override
        public RawPacket reverseTransform(RawPacket pkt)
        {
            if (pkt == null) {
                return null;
            }

            boolean dropPkt = (pkt.getFlags() & Buffer.FLAG_DISCARD) == Buffer.FLAG_DISCARD;

            long ssrc = pkt.getSSRCAsLong();
            ResumableStreamRewriter rewriter;
            synchronized (ssrcToRewriter) {
                rewriter = ssrcToRewriter.get(ssrc);
                if (rewriter == null) {
                    rewriter = new ResumableStreamRewriter();
                    ssrcToRewriter.put(ssrc, rewriter);
                }
            }
            rewriter.rewriteRTP(!dropPkt, pkt.getBuffer(), pkt.getOffset(), pkt.getLength());

            Timber.log(TimberLog.FINER, "%s RTP ssrc = %s, seqnum = %s, ts = %s, streamHashCode = %s",
                    (dropPkt ? "discarding " : "passing through "), pkt.getSSRCAsLong(),
                    pkt.getSequenceNumber(), pkt.getTimestamp(),  stream.hashCode());
            return dropPkt ? null : pkt;
        }
    };

    /**
     * The {@link PacketTransformer} for RTP packets.
     */
    private final PacketTransformer rtcpTransformer = new SinglePacketTransformerAdapter()
    {
        /**
         * {@inheritDoc}
         */
        @Override
        public RawPacket reverseTransform(RawPacket pkt)
        {
            if (pkt == null) {
                return pkt;
            }

            byte[] buf = pkt.getBuffer();
            int offset = pkt.getOffset(), length = pkt.getLength();

            // The correct thing to do here is a loop because the RTCP packet
            // can be compound. However, in practice we haven't seen multiple
            // SRs being bundled in the same compound packet, and we're only
            // interested in SRs.

            // Check RTCP packet validity. This makes sure that pktLen > 0
            // so this loop will eventually terminate.
            if (!RawPacket.isRtpRtcp(buf, offset, length)) {
                return pkt;
            }

            int pktLen = RTCPUtils.getLength(buf, offset, length);
            int pt = RTCPUtils.getPacketType(buf, offset, pktLen);
            if (pt == RTCPPacket.SR) {
                long ssrc = RawPacket.getRTCPSSRC(buf, offset, pktLen);

                ResumableStreamRewriter rewriter;
                synchronized (ssrcToRewriter) {
                    rewriter = ssrcToRewriter.get(ssrc);
                }
                if (rewriter != null) {
                    rewriter.processRTCP(true /* rewrite */, buf, offset, pktLen);
                }
            }
            return pkt;
        }
    };

    /**
     * {@inheritDoc}
     */
    @Override
    public PacketTransformer getRTPTransformer()
    {
        return rtpTransformer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PacketTransformer getRTCPTransformer()
    {
        return rtcpTransformer;
    }
}
