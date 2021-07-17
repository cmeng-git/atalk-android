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

import org.atalk.impl.neomedia.rtp.MediaStreamTrackReceiver;
import org.atalk.impl.neomedia.rtp.RTPEncodingDesc;
import org.atalk.service.neomedia.*;
import org.atalk.service.neomedia.codec.Constants;
import org.atalk.service.neomedia.format.MediaFormat;
import org.atalk.util.TimeProvider;
import org.atalk.util.concurrent.RecurringRunnableExecutor;

import timber.log.Timber;

/**
 * Detects lost RTP packets for a particular <tt>RtpChannel</tt> and requests their retransmission
 * by sending RTCP NACK packets.
 *
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
public class RetransmissionRequesterImpl extends SinglePacketTransformerAdapter
        implements TransformEngine, RetransmissionRequester
{
    /**
     * Whether this {@link RetransmissionRequester} is enabled or not.
     */
    private boolean enabled = true;

    /**
     * Whether this <tt>PacketTransformer</tt> has been closed.
     */
    private boolean closed = false;

    /**
     * The delegate for this {@link RetransmissionRequesterImpl} which handles
     * the main logic for determining when to send nacks
     */
    private final RetransmissionRequesterDelegate retransmissionRequesterDelegate;

    /**
     * The {@link MediaStream} that this instance belongs to.
     */
    private final MediaStream stream;

    /**
     * Create a single executor to service the nack processing for all the
     * {@link RetransmissionRequesterImpl} instances
     */
    private static RecurringRunnableExecutor recurringRunnableExecutor
            = new RecurringRunnableExecutor(RetransmissionRequesterImpl.class.getSimpleName());

    /**
     * Initializes a new <tt>RetransmissionRequester</tt> for the given <tt>RtpChannel</tt>.
     *
     * @param stream the {@link MediaStream} that the instance belongs to.
     */
    public RetransmissionRequesterImpl(MediaStream stream)
    {
        this.stream = stream;
        retransmissionRequesterDelegate = new RetransmissionRequesterDelegate(stream, new TimeProvider());
        recurringRunnableExecutor.registerRecurringRunnable(retransmissionRequesterDelegate);
        retransmissionRequesterDelegate.setWorkReadyCallback(() -> recurringRunnableExecutor.startOrNotifyThread());
    }

    /**
     * {@inheritDoc}
     *
     * Implements {@link SinglePacketTransformer#reverseTransform(RawPacket)}.
     */
    @Override
    public RawPacket reverseTransform(RawPacket pkt)
    {
        if (enabled && !closed) {
            Long ssrc;
            int seq;

            MediaFormat format = stream.getFormat(pkt.getPayloadType());
            if (format == null) {
                ssrc = null;
                seq = -1;
                Timber.w("format_not_found, stream_hash = %s", stream.hashCode());
            }
            else if (Constants.RTX.equalsIgnoreCase(format.getEncoding())) {
                MediaStreamTrackReceiver receiver = stream.getMediaStreamTrackReceiver();
                RTPEncodingDesc encoding = receiver.findRTPEncodingDesc(pkt);

                if (encoding != null) {
                    ssrc = encoding.getPrimarySSRC();
                    seq = pkt.getOriginalSequenceNumber();
                }
                else {
                    ssrc = null;
                    seq = -1;
                    Timber.w("encoding_not_found, stream_hash = %s", stream.hashCode());
                }
            }
            else {
                ssrc = pkt.getSSRCAsLong();
                seq = pkt.getSequenceNumber();
            }

            if (ssrc != null) {
                retransmissionRequesterDelegate.packetReceived(ssrc, seq);
            }
        }
        return pkt;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close()
    {
        closed = true;
        recurringRunnableExecutor.deRegisterRecurringRunnable(retransmissionRequesterDelegate);
    }

    // TransformEngine methods

    /**
     * {@inheritDoc}
     */
    @Override
    public PacketTransformer getRTPTransformer()
    {
        return this;
    }

    /**
     * Implements {@link TransformEngine#getRTCPTransformer()}.
     */
    @Override
    public PacketTransformer getRTCPTransformer()
    {
        return null;
    }

    // RetransmissionRequester methods

    /**
     * {@inheritDoc}
     */
    @Override
    public void enable(boolean enable)
    {
        this.enabled = enable;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSenderSsrc(long ssrc)
    {
        this.retransmissionRequesterDelegate.setSenderSsrc(ssrc);
    }
}
