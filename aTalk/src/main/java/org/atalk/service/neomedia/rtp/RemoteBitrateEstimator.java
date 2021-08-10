/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia.rtp;

import java.util.Collection;

/**
 * webrtc/modules/remote_bitrate_estimator/include/remote_bitrate_estimator.cc
 * webrtc/modules/remote_bitrate_estimator/include/remote_bitrate_estimator.h
 *
 * @author Lyubomir Marinov
 */
public interface RemoteBitrateEstimator extends CallStatsObserver
{
	/**
	 * webrtc/modules/remote_bitrate_estimator/include/bwe_defines.h
	 */
	int kBitrateWindowMs = 1000;

    int kBitrateScale = 8000;

	int kDefaultMinBitrateBps = 30000;

	int kProcessIntervalMs = 500;

	int kStreamTimeOutMs = 2000;

	int kTimestampGroupLengthMs = 5;

	/**
	 * Returns the estimated payload bitrate in bits per second if a valid estimate exists;
	 * otherwise, <tt>-1</tt>.
	 *
	 * @return the estimated payload bitrate in bits per seconds if a valid estimate exists;
	 *         otherwise, <tt>-1</tt>
	 */
	long getLatestEstimate();

    /**
     * Returns the estimated payload bitrate in bits per second if a valid
     * estimate exists; otherwise, <tt>-1</tt>.
     *
     * @return the estimated payload bitrate in bits per seconds if a valid
     * estimate exists; otherwise, <tt>-1</tt>
     */
    Collection<Long> getSsrcs();

    /**
     * Removes all data for <tt>ssrc</tt>.
     *
     * @param ssrc
     */
    void removeStream(long ssrc);

    /**
     * Sets the minimum bitrate for this instance.
     *
     * @param minBitrateBps the minimum bitrate in bps.
     */
    void setMinBitrate(int minBitrateBps);

    /**
     * Notifies this instance of an incoming packet.
     *
     * @param arrivalTimeMs the arrival time of the packet in millis.
     * @param timestamp the 32bit send timestamp of the packet. Note that the
     * specific format depends on the specific implementation.
     * @param payloadSize the payload size of the packet.
     * @param ssrc the SSRC of the packet.
     */
    void incomingPacketInfo(long arrivalTimeMs, long timestamp, int payloadSize, long ssrc);
}
