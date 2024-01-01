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
package org.atalk.impl.neomedia.rtp.translator;

import org.atalk.impl.timberlog.TimberLog;
import org.atalk.impl.neomedia.RTCPFeedbackMessagePacket;
import org.atalk.impl.neomedia.VideoMediaStreamImpl;
import org.atalk.impl.neomedia.rtp.StreamRTPManager;
import org.atalk.service.neomedia.MediaStream;
import org.atalk.service.neomedia.event.RTCPFeedbackMessageEvent;
import org.atalk.util.concurrent.PeriodicRunnable;
import org.atalk.util.concurrent.RecurringRunnableExecutor;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import timber.log.Timber;

/**
 * Allows sending RTCP feedback message packets such as FIR, takes care of their
 * (command) sequence numbers.
 *
 * @author Boris Grozev
 * @author Lyubomir Marinov
 * @author George Politis
 * @author Eng Chong Meng
 */
public class RTCPFeedbackMessageSender
{
    /**
     * The interval in milliseconds at which we re-send an FIR, if the previous
     * one was not satisfied.
     */
    private static final int FIR_RETRY_INTERVAL_MS = 300;

    /**
     * The maximum number of times to send a FIR.
     */
    private static final int FIR_MAX_RETRIES = 10;

    /**
     * The <code>RTPTranslatorImpl</code> through which this <code>RTCPFeedbackMessageSender</code> sends
     * RTCP feedback message packets. The synchronization source identifier (SSRC) of
     * <code>rtpTranslator</code> is used as the SSRC of packet sender.
     */
    private final RTPTranslatorImpl rtpTranslator;

    /**
     * The {@link RecurringRunnableExecutor} which will periodically call
     * {@link KeyframeRequester#run()} and trigger their retry logic.
     */
    private final RecurringRunnableExecutor recurringRunnableExecutor
            = new RecurringRunnableExecutor(RTCPFeedbackMessageSender.class.getSimpleName());

    /**
     * The keyframe requester. One per media source SSRC.
     */
    private final ConcurrentMap<Long, KeyframeRequester> kfRequesters
            = new ConcurrentHashMap<>();

    /**
     * Initializes a new <code>RTCPFeedbackMessageSender</code> instance which is to
     * send RTCP feedback message packets through a specific
     * <code>RTPTranslatorImpl</code>.
     *
     * @param rtpTranslator the <code>RTPTranslatorImpl</code> through which the new instance is to send RTCP
     * feedback message packets and the SSRC of which is to be used as the SSRC of packet
     * sender
     */
    public RTCPFeedbackMessageSender(RTPTranslatorImpl rtpTranslator)
    {
        this.rtpTranslator = rtpTranslator;
    }

    /**
     * Gets the synchronization source identifier (SSRC) to be used as SSRC of
     * packet sender in RTCP feedback message packets.
     *
     * @return the SSRC of packet sender
     */
    private long getSenderSSRC()
    {
        long ssrc = rtpTranslator.getLocalSSRC(null);
        return (ssrc == Long.MAX_VALUE) ? -1 : ssrc;
    }

    /**
     * Sends an RTCP Full Intra Request (FIR) or Picture Loss Indication (PLI),
     * to the media sender/source with a specific synchronization source
     * identifier (SSRC).
     * Whether to send a FIR or a PLI message is decided based on whether the
     * {@link MediaStream} associated with the SSRC supports FIR or PLI.
     *
     * @param mediaSenderSSRC the SSRC of the media sender/source
     * @return {@code true} if an RTCP message was sent; otherwise, {@code false}.
     * @deprecated Use the generic {@link #requestKeyframe(long)} instead.
     */
    @Deprecated
    public boolean sendFIR(int mediaSenderSSRC)
    {
        return requestKeyframe(mediaSenderSSRC & 0xffff_ffffL);
    }

    /**
     * Sends an RTCP Full Intra Request (FIR) or Picture Loss Indication (PLI), to the media
     * sender/source with a specific synchronization source identifier (SSRC). Whether to send a
     * FIR or a PLI message is decided based on whether the
     * {@link MediaStream} associated with the SSRC supports FIR or PLI.
     *
     * @param mediaSenderSSRC the SSRC of the media sender/source
     * @return {@code true} if an RTCP message was sent; otherwise, {@code false}.
     */
    public boolean requestKeyframe(long mediaSenderSSRC)
    {
        boolean registerRecurringRunnable = false;
        KeyframeRequester keyframeRequester = kfRequesters.get(mediaSenderSSRC);
        if (keyframeRequester == null) {
            // Avoided repeated creation of unneeded objects until get fails.
            keyframeRequester = new KeyframeRequester(mediaSenderSSRC);
            KeyframeRequester existingKfRequester = kfRequesters.putIfAbsent(
                    mediaSenderSSRC, keyframeRequester);
            if (existingKfRequester != null) {
                // Another thread beat this one to putting a keyframe requester.
                // That other thread is responsible for registering the keyframe
                // requester with the recurring runnable executor.
                keyframeRequester = existingKfRequester;
            }
            else {
                registerRecurringRunnable = true;
            }
        }

        if (registerRecurringRunnable) {
            // TODO (2016-12-29) Think about eventually de-registering these
            // runnable, but note that with the current code this MUST NOT happen inside run()
            // because of concurrent modification of the executor's list.
            recurringRunnableExecutor.registerRecurringRunnable(keyframeRequester);
        }
        return keyframeRequester.maybeRequest(true);
    }

    /**
     * Sends an RTCP Full Intra Request (FIR) or Picture Loss Indication (PLI),
     * to media senders/sources with a specific synchronization source identifiers (SSRCs).
     * Whether to send a FIR or a PLI message is decided based on whether the
     * {@link MediaStream} associated with the SSRC supports FIR or PLI.
     *
     * @param mediaSenderSSRCs the SSRCs of the media senders/sources
     * @return {@code true} if an RTCP message was sent; otherwise,
     * {@code false}.
     * @deprecated Use the generic {@link #requestKeyframe(long[])} instead.
     */
    @Deprecated
    public boolean sendFIR(int[] mediaSenderSSRCs)
    {
        long[] ssrcsAsLong = new long[mediaSenderSSRCs.length];
        for (int i = 0; i < ssrcsAsLong.length; i++) {
            ssrcsAsLong[i] = mediaSenderSSRCs[i] & 0xffff_ffffL;
        }
        return requestKeyframe(ssrcsAsLong);
    }

    /**
     * Sends an RTCP Full Intra Request (FIR) or Picture Loss Indication (PLI),
     * to media senders/sources with a specific synchronization source identifiers (SSRCs).
     * Whether to send a FIR or a PLI message is decided based on whether the
     * {@link MediaStream} associated with the SSRC supports FIR or PLI.
     *
     * @param mediaSenderSSRCs the SSRCs of the media senders/sources
     * @return {@code true} if an RTCP message was sent; otherwise, {@code false}.
     */
    public boolean requestKeyframe(long[] mediaSenderSSRCs)
    {
        if (mediaSenderSSRCs == null || mediaSenderSSRCs.length == 0) {
            return false;
        }

        boolean requested = false;
        for (long mediaSenderSSRC : mediaSenderSSRCs) {
            if (requestKeyframe(mediaSenderSSRC)) {
                requested = true;
            }
        }
        return requested;
    }

    /**
     * Notifies this instance that an RTP packet has been received from a peer
     * represented by a specific <code>StreamRTPManagerDesc</code>.
     *
     * @param streamRTPManager a <code>StreamRTPManagerDesc</code> which identifies
     * the peer from which an RTP packet has been received
     * @param buf the buffer which contains the bytes of the received RTP or
     * RTCP packet
     * @param off the zero-based index in <code>buf</code> at which the bytes of the
     * received RTP or RTCP packet begin
     * @param len the number of bytes in <code>buf</code> beginning at <code>off</code>
     * which represent the received RTP or RTCP packet
     */
    public void maybeStopRequesting(StreamRTPManagerDesc streamRTPManager,
            long ssrc, byte[] buf, int off, int len)
    {
        KeyframeRequester kfRequester = kfRequesters.get(ssrc);
        if (kfRequester != null) {
            kfRequester.maybeStopRequesting(streamRTPManager, buf, off, len);
        }
    }

    /**
     * Releases the resources allocated by this instance in the course of its
     * execution and prepares it to be garbage collected.
     */
    void dispose()
    {
        recurringRunnableExecutor.close();
    }

    /**
     * The <code>KeyframeRequester</code> is responsible for sending FIR requests to
     * a specific media sender identified by its SSRC.
     */
    class KeyframeRequester extends PeriodicRunnable
    {
        /**
         * The media sender SSRC of this <code>KeyframeRequester</code>
         */
        private final long mediaSenderSSRC;

        /**
         * The sequence number of the next FIR.
         */
        private final AtomicInteger sequenceNumber = new AtomicInteger(0);

        /**
         * The number of FIR that are left to be sent before stopping.
         */
        private int remainingRetries;

        /**
         * Ctor.
         *
         * @param mediaSenderSSRC
         */
        public KeyframeRequester(long mediaSenderSSRC)
        {
            super(FIR_RETRY_INTERVAL_MS);
            this.mediaSenderSSRC = mediaSenderSSRC;
            this.remainingRetries = 0;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void run()
        {
            super.run();
            this.maybeRequest(false);
        }

        /**
         * Notifies this instance that an RTP packet has been received from a
         * peer represented by a specific <code>StreamRTPManagerDesc</code>.
         *
         * @param streamRTPManager a <code>StreamRTPManagerDesc</code> which
         * identifies the peer from which an RTP packet has been received
         * @param buf the buffer which contains the bytes of the received RTP or RTCP packet
         * @param off the zero-based index in <code>buf</code> at which the bytes of
         * the received RTP or RTCP packet begin
         * @param len the number of bytes in <code>buf</code> beginning at
         * <code>off</code> which represent the received RTP or RTCP packet
         */
        public void maybeStopRequesting(
                StreamRTPManagerDesc streamRTPManager, byte[] buf, int off, int len)
        {
            if (remainingRetries == 0) {
                return;
            }

            if (!streamRTPManager.streamRTPManager.getMediaStream().isKeyFrame(buf, off, len)) {
                return;
            }
            Timber.log(TimberLog.FINER, "Stopping FIRs to ssrc = %s", mediaSenderSSRC);

            // This lock only runs while we're waiting for a key frame. It
            // should not slow things down significantly.
            synchronized (this) {
                remainingRetries = 0;
            }
        }

        /**
         * Sends an FIR RTCP message.
         *
         * @param allowResetRemainingRetries true if it's allowed to reset the remaining retries, false otherwise.
         */
        public boolean maybeRequest(boolean allowResetRemainingRetries)
        {
            synchronized (this) {
                if (allowResetRemainingRetries) {
                    if (remainingRetries == 0) {
                        Timber.log(TimberLog.FINER, "Starting FIRs to ssrc = %s", mediaSenderSSRC);
                        remainingRetries = FIR_MAX_RETRIES;
                    }
                    else {
                        // There's a pending FIR. Pretend that we're sending an FIR.
                        Timber.log(TimberLog.FINER, "Pending FIRs to ssrc = %s", mediaSenderSSRC);
                        return true;
                    }
                }
                else if (remainingRetries == 0) {
                    return false;
                }

                remainingRetries--;
                Timber.i("Sending a FIR to ssrc = %s remainingRetries = %s", mediaSenderSSRC, remainingRetries);
            }

            long senderSSRC = getSenderSSRC();

            if (senderSSRC == -1) {
                Timber.w("Not sending an FIR because the sender SSRC is -1.");
                return false;
            }

            StreamRTPManager streamRTPManager
                    = rtpTranslator.findStreamRTPManagerByReceiveSSRC((int) mediaSenderSSRC);

            if (streamRTPManager == null) {
                Timber.w("Not sending an FIR because the stream RTP manager is null.");
                return false;
            }

            // TODO: Use only one of the RTCP packet implementations
            // (RTCPFeedbackMessagePacket or RTCPFBPacket)
            RTCPFeedbackMessagePacket request;
            VideoMediaStreamImpl videoMediaStream
                    = (VideoMediaStreamImpl) streamRTPManager.getMediaStream();

            // If the media sender supports both, we will send a PLI. If it
            // supports neither, we will also send a PLI to better handle the
            // case where signaling is inaccurate (e.g. missing), because all
            // currently known browsers support PLI.
            if (!videoMediaStream.supportsPli()
                    && videoMediaStream.supportsFir()) {
                request = new RTCPFeedbackMessagePacket(
                        RTCPFeedbackMessageEvent.FMT_FIR,
                        RTCPFeedbackMessageEvent.PT_PS,
                        senderSSRC,
                        mediaSenderSSRC);
                request.setSequenceNumber(sequenceNumber.incrementAndGet());
            }
            else {
                request = new RTCPFeedbackMessagePacket(
                        RTCPFeedbackMessageEvent.FMT_PLI,
                        RTCPFeedbackMessageEvent.PT_PS,
                        senderSSRC,
                        mediaSenderSSRC);
                if (!videoMediaStream.supportsPli()) {
                    Timber.w("Sending a PLI to a media sender for which PLI support hasn't been explicitly signaled.");
                }
            }
            return rtpTranslator.writeControlPayload(request, videoMediaStream);
        }
    }
}
