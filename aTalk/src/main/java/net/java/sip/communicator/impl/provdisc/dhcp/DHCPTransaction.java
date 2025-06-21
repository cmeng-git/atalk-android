/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
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
package net.java.sip.communicator.impl.provdisc.dhcp;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import timber.log.Timber;

/**
 * DHCP transaction class.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class DHCPTransaction {
    /**
     * Number of retransmission before giving up.
     */
    private int maxRetransmit = 2;

    /**
     * Current number of retransmission.
     */
    private int nbRetransmit = 0;

    /**
     * Fix interval for retransmission. This final interval will be obtained
     * by adding a random number between [-1, 1].
     */
    private int mInterval = 2;

    /**
     * The Timer that will trigger retransmission.
     */
    private final Timer mTimer;

    /**
     * The DHCP packet content.
     */
    private final DatagramPacket mPacket;

    /**
     * The socket that will be used to retransmit DHCP packet.
     */
    private final DatagramSocket mSock;

    /**
     * Constructor.
     *
     * @param sock UDP socket
     * @param packet DHCP packet content
     */
    public DHCPTransaction(DatagramSocket sock, DatagramPacket packet) {
        mSock = sock;
        mPacket = packet;
        mTimer = new Timer();
    }

    /**
     * Schedule a timer for retransmission.
     *
     * @throws Exception if message cannot be sent on the socket
     */
    public void schedule()
            throws Exception {
        mSock.send(mPacket);

        /* choose a random between [-1, 1] */
        int rand = new Random().nextInt(2) - 1;
        mTimer.schedule(new RetransmissionHandler(), (mInterval + rand) * 1000L);
    }

    /**
     * Cancel the transaction (i.e stop retransmission).
     */
    public void cancel() {
        mTimer.cancel();
    }

    /**
     * Set the maximum retransmission for a transaction.
     *
     * @param maxRetry maximum retransmission for this transaction
     */
    public void setMaxRetransmit(int maxRetry) {
        maxRetransmit = maxRetry;
    }

    /**
     * Set the fixed interval for retransmission.
     *
     * @param interval interval to set
     */
    public void setInterval(int interval) {
        mInterval = interval;
    }

    /**
     * A <code>TimerTask</code> that will handle retransmission of DHCP INFORM.
     *
     * @author Sebastien Vincent
     */
    private class RetransmissionHandler extends TimerTask {
        /**
         * Thread entry point.
         */
        @Override
        public void run() {
            int rand = new Random().nextInt(2) - 1;

            try {
                mSock.send(mPacket);
            } catch (Exception e) {
                Timber.w(e, "Failed to send DHCP packet");
            }

            nbRetransmit++;
            if (nbRetransmit < maxRetransmit) {
                mTimer.schedule(new RetransmissionHandler(), (mInterval + rand) * 1000L);
            }
        }
    }
}
