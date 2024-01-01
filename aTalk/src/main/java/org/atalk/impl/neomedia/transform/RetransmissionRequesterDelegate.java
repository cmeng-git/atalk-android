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
package org.atalk.impl.neomedia.transform;

import org.atalk.impl.timberlog.TimberLog;
import org.atalk.impl.neomedia.rtcp.NACKPacket;
import org.atalk.service.neomedia.MediaStream;
import org.atalk.service.neomedia.RawPacket;
import org.atalk.service.neomedia.TransmissionFailedException;
import org.atalk.util.RTPUtils;
import org.atalk.util.TimeProvider;
import org.atalk.util.concurrent.RecurringRunnable;
import org.atalk.util.logging.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import timber.log.Timber;

/**
 * Detects lost RTP packets for a particular <code>RtpChannel</code> and requests
 * their retransmission by sending RTCP NACK packets.
 *
 * @author Boris Grozev
 * @author George Politis
 * @author bbaldino
 * @author Eng Chong Meng
 */
public class RetransmissionRequesterDelegate implements RecurringRunnable
{
    /**
     * If more than <code>MAX_MISSING</code> consecutive packets are lost, we will
     * not request retransmissions for them, but reset our state instead.
     */
    public static final int MAX_MISSING = 100;

    /**
     * The maximum number of retransmission requests to be sent for a single RTP packet.
     */
    public static final int MAX_REQUESTS = 10;

    /**
     * The interval after which another retransmission request will be sent
     * for a packet, unless it arrives. Ideally this should not be a constant,
     * but should be based on the RTT to the endpoint.
     */
    public static final int RE_REQUEST_AFTER_MILLIS = 150;

    /**
     * The interval we'll ask the {@link RecurringRunnableExecutor} to check back
     * in if there is no current work
     * TODO(brian): i think we should actually be able to get rid of this and
     * just rely on scheduled work and the 'work ready now' callback
     */
    public static final long WAKEUP_INTERVAL_MILLIS = 1000;

    /**
     * Maps an SSRC to the <code>Requester</code> instance corresponding to it.
     * TODO: purge these somehow (RTCP BYE? Timeout?)
     */
    private final Map<Long, Requester> requesters = new HashMap<>();

    /**
     * The {@link MediaStream} that this instance belongs to.
     */
    private final MediaStream stream;

    /**
     * The SSRC which will be used as Packet Sender SSRC in NACK packets sent
     * by this {@code RetransmissionRequesterDelegate}.
     */
    private long senderSsrc = -1;


    protected final TimeProvider timeProvider;

    /**
     * A callback which allows this class to signal it has nack work that is ready to be run
     */
    protected Runnable workReadyCallback = null;

    /**
     * Initializes a new <code>RetransmissionRequesterDelegate</code> for the given <code>RtpChannel</code>.
     *
     * @param stream the {@link MediaStream} that the instance belongs to.
     */
    public RetransmissionRequesterDelegate(MediaStream stream, TimeProvider timeProvider)
    {
        this.stream = stream;
        this.timeProvider = timeProvider;
    }

    /**
     * Notify this requester that a packet has been received
     */
    public void packetReceived(long ssrc, int seqNum)
    {
        // TODO(gp) Don't NACK higher temporal layers.
        Requester requester = getOrCreateRequester(ssrc);
        // If the reception of this packet resulted in there being work that
        // is ready to be done now, fire the work ready callback
        if (requester.received(seqNum)) {
            if (workReadyCallback != null) {
                workReadyCallback.run();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getTimeUntilNextRun()
    {
        long now = timeProvider.currentTimeMillis();
        Requester nextDueRequester = getNextDueRequester();
        if (nextDueRequester == null) {
            return WAKEUP_INTERVAL_MILLIS;
        }
        else {
            Timber.log(TimberLog.FINER, hashCode() + "%s: Next nack is scheduled for ssrc %s at %s. (current time is %s)",
                    nextDueRequester.ssrc, Math.max(nextDueRequester.nextRequestAt, 0), now);
            return Math.max(nextDueRequester.nextRequestAt - now, 0);
        }
    }

    public void setWorkReadyCallback(Runnable workReadyCallback)
    {
        this.workReadyCallback = workReadyCallback;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run()
    {
        long now = timeProvider.currentTimeMillis();
        Timber.log(TimberLog.FINER, "%s running at %s", hashCode(), now);
        List<Requester> dueRequesters = getDueRequesters(now);
        Timber.log(TimberLog.FINER, "%s has %s due requesters", hashCode(), dueRequesters.size());
        if (!dueRequesters.isEmpty()) {
            List<NACKPacket> nackPackets = createNackPackets(now, dueRequesters);
            Timber.log(TimberLog.FINER, "%s injecting %s nack packets", hashCode(), nackPackets.size());
            if (!nackPackets.isEmpty()) {
                injectNackPackets(nackPackets);
            }
        }
    }

    private Requester getOrCreateRequester(long ssrc)
    {
        Requester requester;
        synchronized (requesters) {
            requester = requesters.get(ssrc);
            if (requester == null) {
                Timber.d("Creating new Requester for SSRC %s", ssrc);
                requester = new Requester(ssrc);
                requesters.put(ssrc, requester);
            }
        }
        return requester;
    }

    private Requester getNextDueRequester()
    {
        Requester nextDueRequester = null;
        synchronized (requesters) {
            for (Requester requester : requesters.values()) {
                if (requester.nextRequestAt != -1
                        && (nextDueRequester == null || requester.nextRequestAt < nextDueRequester.nextRequestAt)) {
                    nextDueRequester = requester;
                }
            }
        }
        return nextDueRequester;
    }

    /**
     * Get a list of the requesters (not necessarily in sorted order)
     * which are due to request as of the given time
     *
     * @param currentTime the current time
     * @return a list of the requesters (not necessarily in sorted order)
     * which are due to request as of the given time
     */
    private List<Requester> getDueRequesters(long currentTime)
    {
        List<Requester> dueRequesters = new ArrayList<>();
        synchronized (requesters) {
            for (Requester requester : requesters.values()) {
                if (requester.isDue(currentTime)) {
                    Timber.log(TimberLog.FINER, hashCode() + "%s requester for ssrc %s has work due at %s(now = %s) and is missing packets: %s",
                            requester.ssrc, requester.nextRequestAt, currentTime, requester.getMissingSeqNums());

                    dueRequesters.add(requester);
                }
            }
        }
        return dueRequesters;
    }

    /**
     * Inject the given nack packets into the outgoing stream
     *
     * @param nackPackets the nack packets to inject
     */
    private void injectNackPackets(List<NACKPacket> nackPackets)
    {
        for (NACKPacket nackPacket : nackPackets) {
            try {
                RawPacket packet;
                try {
                    packet = nackPacket.toRawPacket();
                } catch (IOException ioe) {
                    Timber.w(ioe, "Failed to create a NACK packet");
                    continue;
                }

                Timber.log(TimberLog.FINER, "Sending a NACK: %s", nackPacket);
                stream.injectPacket(packet, /* data */ false, /* after */ null);
            } catch (TransmissionFailedException e) {
                Timber.w(e.getCause(), "Failed to inject packet in MediaStream.");
            }
        }
    }

    /**
     * Gather the packets currently marked as missing and create
     * NACKs for them
     *
     * @param dueRequesters the requesters which are due to have nack packets
     * generated
     */
    protected List<NACKPacket> createNackPackets(long now, List<Requester> dueRequesters)
    {
        Map<Long, Set<Integer>> packetsToRequest = new HashMap<>();
        for (Requester dueRequester : dueRequesters) {
            synchronized (dueRequester) {
                Set<Integer> missingPackets = dueRequester.getMissingSeqNums();
                if (!missingPackets.isEmpty()) {
                    Timber.log(TimberLog.FINER, "%S Sending nack with packets %S for ssrc %S",
                            hashCode(), missingPackets, dueRequester.ssrc);
                    packetsToRequest.put(dueRequester.ssrc, missingPackets);
                    dueRequester.notifyNackCreated(now, missingPackets);
                }
            }
        }

        List<NACKPacket> nackPackets = new ArrayList<>();
        for (Map.Entry<Long, Set<Integer>> entry : packetsToRequest.entrySet()) {
            long sourceSsrc = entry.getKey();
            Set<Integer> missingPackets = entry.getValue();
            NACKPacket nack = new NACKPacket(senderSsrc, sourceSsrc, missingPackets);
            nackPackets.add(nack);
        }
        return nackPackets;
    }

    /**
     * Handles packets for a single SSRC.
     */
    private class Requester
    {
        /**
         * The SSRC for this instance.
         */
        private final long ssrc;

        /**
         * The highest received RTP sequence number.
         */
        private int lastReceivedSeq = -1;

        /**
         * The time that the next request for this SSRC should be sent.
         */
        private long nextRequestAt = -1;

        /**
         * The set of active requests for this SSRC. The keys are the sequence
         * numbers.
         */
        private final Map<Integer, Request> requests = new HashMap<>();

        /**
         * Initializes a new <code>Requester</code> instance for the given SSRC.
         */
        private Requester(long ssrc)
        {
            this.ssrc = ssrc;
        }

        /**
         * Check if this {@link Requester} is due to send a nack
         *
         * @param currentTime the current time, in ms
         * @return true if this {@link Requester} is due to send a nack, false
         * otherwise
         */
        public boolean isDue(long currentTime)
        {
            return nextRequestAt != -1 && nextRequestAt <= currentTime;
        }

        /**
         * Handles a received RTP packet with a specific sequence number.
         *
         * @param seq the RTP sequence number of the received packet.
         * @return true if there is work for this requester ready to be
         * done now, false otherwise
         */
        synchronized private boolean received(int seq)
        {
            if (lastReceivedSeq == -1) {
                lastReceivedSeq = seq;
                return false;
            }

            int diff = RTPUtils.getSequenceNumberDelta(seq, lastReceivedSeq);
            if (diff <= 0) {
                // An older packet, possibly already requested.
                Request r = requests.remove(seq);
                if (requests.isEmpty()) {
                    nextRequestAt = -1;
                }

                if (r != null) {
                    long rtt = stream.getMediaStreamStats().getSendStats().getRtt();
                    if (rtt > 0) {

                        // firstRequestSentAt is if we created a Request, but
                        // haven't yet sent a NACK. Assume a delta of 0 in that case.
                        long firstRequestSentAt = r.firstRequestSentAt;
                        long delta = firstRequestSentAt > 0
                                ? timeProvider.currentTimeMillis() - r.firstRequestSentAt : 0;

                        Timber.d("%s retr_received,stream = %d; delay = %d; rtt = %d",
                                Logger.Category.STATISTICS, stream.hashCode(), delta, rtt);
                    }
                }
            }
            else if (diff == 1) {
                // The very next packet, as expected.
                lastReceivedSeq = seq;
            }
            else if (diff <= MAX_MISSING) {
                for (int missing = (lastReceivedSeq + 1) % (1 << 16);
                     missing != seq;
                     missing = (missing + 1) % (1 << 16)) {
                    Request request = new Request(missing);
                    requests.put(missing, request);
                }

                lastReceivedSeq = seq;
                nextRequestAt = 0;

                return true;
            }
            else // if (diff > MAX_MISSING)
            {
                // Too many packets missing. Reset.
                lastReceivedSeq = seq;
                Timber.d("Resetting retransmission requester state. SSRC: %S, last received: %S, current: %S. Removing %S unsatisfied requests.",
                        ssrc, lastReceivedSeq, seq, requests.size());
                requests.clear();
                nextRequestAt = -1;
            }
            return false;
        }

        /**
         * Returns a set of RTP sequence numbers which are considered still MIA,
         * and for which a retransmission request needs to be sent.
         * Assumes that the returned set of sequence numbers will be requested
         * immediately and updates the state accordingly (i.e. increments the
         * timesRequested counters and sets the time of next request).
         *
         * @return a set of RTP sequence numbers which are considered still MIA,
         * and for which a retransmission request needs to be sent.
         */
        synchronized private @NotNull
        Set<Integer> getMissingSeqNums()
        {
            return new HashSet<>(requests.keySet());
        }

        /**
         * Notify this requester that a nack was sent at the given time
         *
         * @param time the time at which the nack was sent
         */
        public synchronized void notifyNackCreated(long time, Collection<Integer> sequenceNumbers)
        {
            for (Integer seqNum : sequenceNumbers) {
                Request request = requests.get(seqNum);
                request.timesRequested++;
                if (request.timesRequested == MAX_REQUESTS) {
                    Timber.d("Generated the last NACK for SSRC = %S seq = %S. Time since the first request: %S",
                            ssrc, request.seq, (time - request.firstRequestSentAt));
                    requests.remove(seqNum);
                    continue;
                }
                if (request.timesRequested == 1) {
                    request.firstRequestSentAt = time;
                }
            }
            nextRequestAt = (requests.size() > 0) ? time + RE_REQUEST_AFTER_MILLIS : -1;
        }

    }

    /**
     * Represents a request for the retransmission of a specific RTP packet.
     */
    private static class Request
    {
        /**
         * The RTP sequence number.
         */
        final int seq;

        /**
         * The system time at the moment a retransmission request for this
         * packet was first sent.
         */
        long firstRequestSentAt = -1;

        /**
         * The number of times that a retransmission request for this packet
         * has been sent.
         */
        int timesRequested = 0;

        /**
         * Initializes a new <code>Request</code> instance with the given RTP
         * sequence number.
         *
         * @param seq the RTP sequence number.
         */
        Request(int seq)
        {
            this.seq = seq;
        }
    }

    /**
     * {@inheritDoc}
     */
    public void setSenderSsrc(long ssrc)
    {
        senderSsrc = ssrc;
    }
}
