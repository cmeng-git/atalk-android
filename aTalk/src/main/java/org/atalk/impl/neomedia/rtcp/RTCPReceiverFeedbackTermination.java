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
package org.atalk.impl.neomedia.rtcp;

import net.sf.fmj.media.rtp.*;

import org.atalk.android.plugin.timberlog.TimberLog;
import org.atalk.impl.neomedia.MediaStreamImpl;
import org.atalk.impl.neomedia.RTCPPacketPredicate;
import org.atalk.impl.neomedia.rtp.StreamRTPManager;
import org.atalk.impl.neomedia.rtp.remotebitrateestimator.RemoteBitrateEstimatorWrapper;
import org.atalk.impl.neomedia.rtp.translator.RTPTranslatorImpl;
import org.atalk.impl.neomedia.transform.*;
import org.atalk.service.neomedia.*;
import org.atalk.service.neomedia.event.RTCPFeedbackMessageEvent;
import org.atalk.util.*;
import org.atalk.util.concurrent.PeriodicRunnable;
import org.atalk.util.function.RTCPGenerator;

import java.util.*;

import javax.media.rtp.ReceiveStream;

import timber.log.Timber;

/**
 * Terminates RRs and REMBs.
 *
 * @author George Politis
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
public class RTCPReceiverFeedbackTermination extends PeriodicRunnable implements TransformEngine
{
    /**
     * The maximum number of RTCP report blocks that an RR can contain.
     */
    private static final int MAX_RTCP_REPORT_BLOCKS = 31;

    /**
     * The minimum number of RTCP report blocks that an RR can contain.
     */
    private static final int MIN_RTCP_REPORT_BLOCKS = 0;

    /**
     * The reporting period for RRs and REMBs.
     */
    private static final long REPORT_PERIOD_MS = 500;

    /**
     * The generator that generates <tt>RawPacket</tt>s from <tt>RTCPCompoundPacket</tt>s.
     */
    private final RTCPGenerator generator = new RTCPGenerator();

    /**
     * A reusable array that holds {@link #MIN_RTCP_REPORT_BLOCKS} <tt>RTCPReportBlock</tt>s.
     */
    private static final RTCPReportBlock[] MIN_RTCP_REPORT_BLOCKS_ARRAY
            = new RTCPReportBlock[MIN_RTCP_REPORT_BLOCKS];

    /**
     * The {@link MediaStream} that owns this instance.
     */
    private final MediaStreamImpl stream;

    /**
     *
     */
    private final RTCPTransformer rtcpTransformer = new RTCPTransformer();

    /**
     * Ctor.
     *
     * @param stream the {@link MediaStream} that owns this instance.
     */
    public RTCPReceiverFeedbackTermination(MediaStreamImpl stream)
    {
        super(REPORT_PERIOD_MS);
        this.stream = stream;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run()
    {
        super.run();

        // cmeng - just return if stream or rtpTranslator is null i.e. nothing to report
        if ((stream == null) || stream.getRTPTranslator() == null)
            return;

        // Create and return the packet.
        // We use the stream's local source ID (SSRC) as the SSRC of packet sender.
        long senderSSRC = getSenderSSRC();
        if (senderSSRC == -1) {
            return;
        }

        // RRs
        RTCPRRPacket[] rrs = makeRRs(senderSSRC);

        // Bail out (early) if we have nothing to report.
        if (ArrayUtils.isNullOrEmpty(rrs)) {
            return;
        }

        // REMB
        RTCPREMBPacket remb = makeREMB(senderSSRC);

        // Build the RTCP compound packet to return.
        RTCPPacket[] rtcpPackets;
        if (remb == null) {
            rtcpPackets = rrs;
        }
        else {
            // NOTE the add method throws an exception if remb == null.
            rtcpPackets = ArrayUtils.add(rrs, RTCPPacket.class, remb);
        }
        RTCPCompoundPacket compound = new RTCPCompoundPacket(rtcpPackets);

        // inject the packets into the MediaStream.
        RawPacket pkt = generator.apply(compound);

        try {
            stream.injectPacket(pkt, false, this);
        } catch (TransmissionFailedException e) {
            Timber.e(e, "transmission of an RTCP packet failed.");
        }
    }

    /**
     * (attempts) to get the local SSRC that will be used in the media sender
     * SSRC field of the RTCP reports. TAG(cat4-local-ssrc-hurricane)
     *
     * @return the local sender SSRC ID
     */
    private long getSenderSSRC()
    {
        StreamRTPManager streamRTPManager = stream.getStreamRTPManager();
        if (streamRTPManager == null) {
            return -1;
        }
        return streamRTPManager.getLocalSSRC();
    }

    /**
     * Makes <tt>RTCPRRPacket</tt>s using information in FMJ.
     *
     * @return A <tt>List</tt> of <tt>RTCPRRPacket</tt>s to inject into the <tt>MediaStream</tt>.
     */
    private RTCPRRPacket[] makeRRs(long senderSSRC)
    {
        RTCPReportBlock[] reportBlocks = makeReportBlocks();
        if (ArrayUtils.isNullOrEmpty(reportBlocks)) {
            return null;
        }

        int mod = reportBlocks.length % MAX_RTCP_REPORT_BLOCKS;
        int div = reportBlocks.length / MAX_RTCP_REPORT_BLOCKS;

        RTCPRRPacket[] rrs = new RTCPRRPacket[mod == 0 ? div : div + 1];

        // Since a maximum of 31 reception report blocks will fit in an SR or RR packet,
        // additional RR packets SHOULD be stacked after the initial SR or RR packet as needed to
        // contain the reception reports for all sources heard during the interval since the last report.
        if (reportBlocks.length > MAX_RTCP_REPORT_BLOCKS) {
            int rrIdx = 0;
            for (int off = 0; off < reportBlocks.length; off += MAX_RTCP_REPORT_BLOCKS) {
                int blockCount = Math.min(reportBlocks.length - off, MAX_RTCP_REPORT_BLOCKS);
                RTCPReportBlock[] blocks = new RTCPReportBlock[blockCount];
                System.arraycopy(reportBlocks, off, blocks, 0, blocks.length);
                rrs[rrIdx++] = new RTCPRRPacket((int) senderSSRC, blocks);
            }
        }
        else {
            rrs[0] = new RTCPRRPacket((int) senderSSRC, reportBlocks);
        }
        return rrs;
    }

    /**
     * Iterate through all the <tt>ReceiveStream</tt>s that this <tt>MediaStream</tt> has and
     * make <tt>RTCPReportBlock</tt>s for all of them.
     *
     * cmeng: rptTranslator is currently disabled for Android or peer is not the conference focus.
     *
     * {@link net.java.sip.communicator.service.protocol.media.MediaAwareCallConference#getRTPTranslator(MediaType)}
     *
     * @return
     */
    private RTCPReportBlock[] makeReportBlocks()
    {
        // State validation.
        if (stream == null) {
            Timber.w("stream is null.");
            return MIN_RTCP_REPORT_BLOCKS_ARRAY;
        }

        StreamRTPManager streamRTPManager = stream.getStreamRTPManager();
        if (streamRTPManager == null) {
            Timber.w("streamRTPManager is null.");
            return MIN_RTCP_REPORT_BLOCKS_ARRAY;
        }

        /*
         * XXX MediaStreamImpl's implementation of #getReceiveStreams() says that, unfortunately,
         * it has been observed that sometimes there are valid ReceiveStreams in MediaStreamImpl
         * which are not returned by FMJ's RTPManager. Since
         * (1) MediaStreamImpl#getReceiveStreams() will include the results of StreamRTPManager#getReceiveStreams() and
         * (2) we are going to check the results against SSRCCache, it should be relatively safe
         * to rely on MediaStreamImpl's implementation.
         */
        Collection<ReceiveStream> receiveStreams = stream.getReceiveStreams();

        if (receiveStreams == null || receiveStreams.isEmpty()) {
            Timber.d("There are no receive streams to build report blocks for.");
            return MIN_RTCP_REPORT_BLOCKS_ARRAY;
        }

        RTPTranslator rtpTranslator = stream.getRTPTranslator();
        SSRCCache cache = rtpTranslator.getSSRCCache();
        // SSRCCache cache = stream.getRTPTranslator().getSSRCCache();
        if (cache == null) {
            Timber.i("cache is null.");
            return MIN_RTCP_REPORT_BLOCKS_ARRAY;
        }
        // Create and populate the return object.
        Collection<RTCPReportBlock> reportBlocks = new ArrayList<>();

        for (ReceiveStream receiveStream : receiveStreams) {
            // Dig into the guts of FMJ and get the stats for the current receiveStream.
            SSRCInfo info = cache.cache.get((int) receiveStream.getSSRC());

            if (info == null) {
                Timber.w("We have a ReceiveStream but not an SSRCInfo for that ReceiveStream.");
                continue;
            }
            if (!info.ours && info.sender) {
                RTCPReportBlock reportBlock = info.makeReceiverReport(getLastProcessTime());
                reportBlocks.add(reportBlock);

                Timber.log(TimberLog.FINER, "%s", stream.getDiagnosticContext()
                        .makeTimeSeriesPoint("created_report_block")
                        .addField("rtcp_termination", hashCode())
                        .addField("ssrc", reportBlock.getSSRC())
                        .addField("num_lost", reportBlock.getNumLost())
                        .addField("fraction_lost", reportBlock.getFractionLost() / 256D)
                        .addField("jitter", reportBlock.getJitter())
                        .addField("xtnd_seqnum", reportBlock.getXtndSeqNum()));
            }
        }
        return reportBlocks.toArray(new RTCPReportBlock[reportBlocks.size()]);
    }

    /**
     * Makes an <tt>RTCPREMBPacket</tt> that provides receiver feedback to the
     * endpoint from which we receive.
     *
     * @return an <tt>RTCPREMBPacket</tt> that provides receiver feedback to the
     * endpoint from which we receive.
     */
    private RTCPREMBPacket makeREMB(long senderSSRC)
    {
        // Destination
        RemoteBitrateEstimatorWrapper remoteBitrateEstimator = stream.getRemoteBitrateEstimator();
        if (!remoteBitrateEstimator.receiveSideBweEnabled()) {
            return null;
        }
        Collection<Long> ssrcs = remoteBitrateEstimator.getSsrcs();

        // TODO(gp) intersect with SSRCs from signaled simulcast layers
        // NOTE(gp) The Google Congestion Control algorithm (sender side)
        // doesn't seem to care about the SSRCs in the dest field.
        long[] dest = new long[ssrcs.size()];
        int i = 0;

        for (Long ssrc : ssrcs)
            dest[i++] = ssrc;

        // Exp & mantissa
        long bitrate = remoteBitrateEstimator.getLatestEstimate();

        Timber.d("Estimated bitrate (bps): %s, dest: %s, time (ms): %s",
                bitrate, Arrays.toString(dest), System.currentTimeMillis());
        if (bitrate == -1) {
            return null;
        }
        else {
            return new RTCPREMBPacket(senderSSRC, 0L, bitrate, dest);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PacketTransformer getRTPTransformer()
    {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PacketTransformer getRTCPTransformer()
    {
        return rtcpTransformer;
    }

    /**
     *
     */
    class RTCPTransformer extends SinglePacketTransformerAdapter
    {
        /**
         * Ctor.
         */
        RTCPTransformer()
        {
            super(RTCPPacketPredicate.INSTANCE);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public RawPacket transform(RawPacket pkt)
        {
            // Kill the RRs that FMJ is sending.
            return doTransform(pkt, true);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public RawPacket reverseTransform(RawPacket pkt)
        {
            return doTransform(pkt, false);
        }

        private RawPacket doTransform(RawPacket pkt, boolean send)
        {
            RTCPIterator it = new RTCPIterator(pkt);
            while (it.hasNext()) {
                ByteArrayBuffer baf = it.next();
                int pt = RTCPUtils.getPacketType(baf);
                if (pt == RTCPRRPacket.RR
                        || RTCPREMBPacket.isREMBPacket(baf)
                        || RTCPTCCPacket.isTCCPacket(baf)) {
                    it.remove();
                    continue;
                }

                if (!send && pt > -1) {
                    int fmt = RTCPUtils.getReportCount(baf);
                    if ((pt == RTCPFeedbackMessageEvent.PT_PS
                            && fmt == RTCPFeedbackMessageEvent.FMT_PLI)
                            || (pt == RTCPFeedbackMessageEvent.PT_PS
                            && fmt == RTCPFeedbackMessageEvent.FMT_FIR)) {
                        long source = RTCPFBPacket.getSourceSSRC(baf);
                        ((RTPTranslatorImpl) stream.getRTPTranslator()).getRtcpFeedbackMessageSender().requestKeyframe(source);
                        it.remove();
                    }
                }
            }
            return pkt.getLength() == 0 ? null : pkt;
        }
    }
}
