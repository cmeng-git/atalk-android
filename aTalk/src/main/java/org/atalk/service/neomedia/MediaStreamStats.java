/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia;

import org.atalk.android.util.java.awt.Dimension;
import org.atalk.service.neomedia.rtp.*;
import org.atalk.service.neomedia.stats.MediaStreamStats2;

/**
 * Class used to compute stats concerning a MediaStream.
 *
 * @author Vincent Lucas
 * @author Lyubomir Marinov
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
public interface MediaStreamStats
{
	/**
	 * Returns the jitter average of this download stream.
	 *
	 * @return the last jitter average computed (in ms).
	 * @deprecated use the appropriate method from {@link MediaStreamStats2} instead.
	 */
	@Deprecated
	double getDownloadJitterMs();

	/**
	 * Returns the percent loss of the download stream.
	 *
	 * @return the last loss rate computed (in %).
	 * @deprecated use the appropriate method from {@link MediaStreamStats2} instead.
	 */
	@Deprecated
	double getDownloadPercentLoss();

	/**
	 * Returns the bandwidth used by this download stream.
	 *
	 * @return the last used download bandwidth computed (in Kbit/s).
	 * @deprecated use the appropriate method from {@link MediaStreamStats2} instead.
	 */
	@Deprecated
	double getDownloadRateKiloBitPerSec();

	/**
	 * Returns the download video size if this stream downloads a video, or <tt>null</tt> if not.
	 *
	 * @return the download video size if this stream downloads a video, or <tt>null</tt> if not.
	 */
	Dimension getDownloadVideoSize();

	/**
	 * Returns the <tt>MediaStream</tt> encoding.
	 *
	 * @return the encoding used by the stream.
	 */
	String getEncoding();

	/**
	 * Returns the <tt>MediaStream</tt> encoding rate (in Hz).
	 *
	 * @return the encoding rate used by the stream.
	 */
	String getEncodingClockRate();

	/**
	 * Returns the delay in milliseconds introduced by the jitter buffer.
	 *
	 * @return the delay in milliseconds introduced by the jitter buffer
	 */
	int getJitterBufferDelayMs();

	/**
	 * Returns the delay in number of packets introduced by the jitter buffer.
	 *
	 * @return the delay in number of packets introduced by the jitter buffer
	 */
	int getJitterBufferDelayPackets();

	/**
	 * Returns the local IP address of the <tt>MediaStream</tt>.
	 *
	 * @return the local IP address of the stream.
	 */
	String getLocalIPAddress();

	/**
	 * Returns the local port of the <tt>MediaStream</tt>.
	 *
	 * @return the local port of the stream.
	 */
	int getLocalPort();

	/**
	 * Returns the number of received bytes since the beginning of the session.
	 *
	 * @return the number of received bytes for this stream.
	 * @deprecated use the appropriate method from {@link MediaStreamStats2} instead.
	 */
	@Deprecated
	long getNbReceivedBytes();

	/**
	 * Returns the number of sent bytes since the beginning of the session.
	 *
	 * @return the number of sent bytes for this stream.
	 * @deprecated use the appropriate method from {@link MediaStreamStats2} instead.
	 */
	@Deprecated
	long getNbSentBytes();

	/**
	 * Returns the total number of discarded packets since the beginning of the session.
	 *
	 * @return the total number of discarded packets since the beginning of the session.
	 */
	long getNbDiscarded();

	/**
	 * Returns the number of packets discarded since the beginning of the session, because the
	 * packet queue was full.
	 *
	 * @return the number of packets discarded since the beginning of the session, because the
	 * packet queue was full.
	 */
	int getNbDiscardedFull();

	/**
	 * Returns the number of packets discarded since the beginning of the session, because they
	 * were late.
	 *
	 * @return the number of packets discarded since the beginning of the session, because they
	 * were late.
	 */
	int getNbDiscardedLate();

	/**
	 * Returns the number of packets discarded since the beginning of the session, because the
	 * packet queue was reset.
	 *
	 * @return the number of packets discarded since the beginning of the session, because the
	 * packet queue was reset.
	 */
	int getNbDiscardedReset();

	/**
	 * Returns the number of packets discarded since the beginning of the session, while the packet
	 * queue was shrinking.
	 *
	 * @return the number of packets discarded since the beginning of the session, while the packet
	 * queue was shrinking.
	 */
	int getNbDiscardedShrink();

	/**
	 * Returns the number of packets for which FEC data was decoded.
	 *
	 * @return the number of packets for which FEC data was decoded
	 */
	long getNbFec();

	/**
	 * Returns the total number of packets that are send or receive for this
	 * stream since the stream is created.
	 *
	 * @return the total number of packets.
	 * @deprecated use the appropriate method from {@link MediaStreamStats2}
	 * instead.
	 */
	@Deprecated
	long getNbPackets();

	/**
	 * Returns the number of lost packets for that stream.
	 *
	 * @return the number of lost packets.
	 * @deprecated use the appropriate method from {@link MediaStreamStats2} instead.
	 */
	@Deprecated
	long getNbPacketsLost();

	/**
	 * Returns the number of packets currently in the packet queue.
	 *
	 * @return the number of packets currently in the packet queue.
	 */
	int getPacketQueueCountPackets();

	/**
	 * Returns the current size of the packet queue.
	 *
	 * @return the current size of the packet queue.
	 */
	int getPacketQueueSize();

	/**
	 * Returns the current percent of discarded packets.
	 *
	 * @return the current percent of discarded packets.
	 */
	double getPercentDiscarded();

	/**
	 * Returns the remote IP address of the <tt>MediaStream</tt>.
	 *
	 * @return the remote IP address of the stream.
	 */
	String getRemoteIPAddress();

	/**
	 * Returns the remote port of the <tt>MediaStream</tt>.
	 *
	 * @return the remote port of the stream.
	 */
	int getRemotePort();

	/**
	 * Gets the detailed statistics about the RTCP reports sent and received by the associated
	 * local peer.
	 *
	 * @return the detailed statistics about the RTCP reports sent and received by the associated
	 * local peer
	 */
	RTCPReports getRTCPReports();

	/**
	 * Returns the RTT computed with the RTCP feedback (cf. RFC3550, section 6.4.1, subsection
	 * "delay since last SR (DLSR): 32 bits").
	 *
	 * @return The RTT computed with the RTCP feedback. Returns <tt>-1</tt> if
	 * the RTT has not been computed yet. Otherwise the RTT in ms.
	 * @deprecated use the appropriate method from {@link MediaStreamStats2} instead.
	 */
	@Deprecated
	long getRttMs();

	/**
	 * Returns the jitter average of this upload stream.
	 *
	 * @return the last jitter average computed (in ms).
	 * @deprecated use the appropriate method from {@link MediaStreamStats2} instead.
	 */
	@Deprecated
	double getUploadJitterMs();

	/**
	 * Returns the percent loss of the upload stream.
	 *
	 * @return the last loss rate computed (in %).
	 * @deprecated use the appropriate method from {@link MediaStreamStats2} instead.
	 */
	@Deprecated
	double getUploadPercentLoss();

	/**
	 * Returns the bandwidth used by this download stream.
	 *
	 * @return the last used upload bandwidth computed (in Kbit/s).
	 * @deprecated use the appropriate method from {@link MediaStreamStats2} instead.
	 */
	@Deprecated
	double getUploadRateKiloBitPerSec();

	/**
	 * Returns the upload video size if this stream uploads a video, or <tt>null</tt> if not.
	 *
	 * @return the upload video size if this stream uploads a video, or <tt>null</tt> if not.
	 */
	Dimension getUploadVideoSize();

	/**
	 * Checks whether there is an adaptive jitter buffer enabled for at least one of the
	 * <tt>ReceiveStream</tt>s of the <tt>MediaStreamImpl</tt>.
	 *
	 * @return <tt>true</tt> if there is an adaptive jitter buffer enabled for at least one of the
	 * <tt>ReceiveStream</tt>s of the <tt>MediaStreamImpl</tt>; otherwise, <tt>false</tt>
	 */
	boolean isAdaptiveBufferEnabled();

	/**
	 * Computes and updates information for a specific stream.
	 */
	void updateStats();

	/**
	 * Gets the minimum RTP jitter value reported by us in an RTCP report, in milliseconds. Returns
	 * -1D if the value is unknown.
	 *
	 * @return the minimum RTP jitter value reported by us in an RTCP report, in milliseconds.
	 */
	double getMinDownloadJitterMs();

	/**
	 * Gets the maximum RTP jitter value reported by us in an RTCP report, in milliseconds. Returns
	 * -1D if the value is unknown.
	 *
	 * @return the maximum RTP jitter value reported by us in an RTCP report, in milliseconds.
	 */
	double getMaxDownloadJitterMs();

	/**
	 * Gets the average of the RTP jitter values reported to us in RTCP reports, in milliseconds.
	 * Returns -1D if the value is unknown.
	 *
	 * @return the average of the RTP jitter values reported to us in RTCP reports, in
	 * milliseconds.
	 * Returns -1D if the value is unknown.
	 */
	double getAvgDownloadJitterMs();

	/**
	 * Gets the minimum RTP jitter value reported to us in an RTCP report, in milliseconds. Returns
	 * -1D if the value is unknown.
	 *
	 * @return the minimum RTP jitter value reported to us in an RTCP report, in milliseconds.
	 */
	double getMinUploadJitterMs();

	/**
	 * Gets the maximum RTP jitter value reported to us in an RTCP report, in milliseconds. Returns
	 * -1D if the value is unknown.
	 *
	 * @return the maximum RTP jitter value reported to us in an RTCP report, in milliseconds.
	 */
	double getMaxUploadJitterMs();

	/**
	 * Gets the average of the RTP jitter values reported to us in RTCP reports, in milliseconds.
	 * Returns -1D if the value is unknown.
	 *
	 * @return the average of the RTP jitter values reported to us in RTCP reports, in
	 * milliseconds.
	 * Returns -1D if the value is unknown.
	 */
	double getAvgUploadJitterMs();

	/**
	 * Returns the number of packets sent since the beginning of the session.
	 *
	 * @return the number of packets sent since the beginning of the session.
	 * @deprecated use the appropriate method from {@link MediaStreamStats2} instead.
	 */
	@Deprecated
	long getNbPacketsSent();

	/**
	 * Returns the number of packets received since the beginning of the session.
	 *
	 * @return the number of packets received since the beginning of the session.
	 * @deprecated use the appropriate method from {@link MediaStreamStats2} instead.
	 */
	@Deprecated
	long getNbPacketsReceived();

	/**
	 * Returns the number of RTP packets sent by the remote side, but not received by us.
	 *
	 * @return the number of RTP packets sent by the remote side, but not received by us.
	 * @deprecated use the appropriate method from {@link MediaStreamStats2} instead.
	 */
	@Deprecated
	long getDownloadNbPacketLost();

	/**
	 * Returns the number of RTP packets sent by us, but not received by the remote side.
	 *
	 * @return the number of RTP packets sent by us, but not received by the remote side.
	 * @deprecated use the appropriate method from {@link MediaStreamStats2} instead.
	 */
	@Deprecated
	long getUploadNbPacketLost();

	/**
	 * Adds a listener which will be notified when NACK packets are received.
	 *
	 * @param listener
	 * 		the listener.
	 */
	void addRTCPPacketListener(RTCPPacketListener listener);

	/**
	 * Adds a listener which will be notified when REMB packets are received.
	 *
	 * @param listener
	 * 		the listener.
	 */
	void removeRTCPPacketListener(RTCPPacketListener listener);

	/**
	 * Gets the rate at which we are currently sending data to the remote endpoint in bits per
	 * second. This is almost the same as {@link #getUploadRateKiloBitPerSec()}. The duplication
	 * is necessary, because of implementation details.
	 *
	 * @return the rate at which we are currently sending data to the remote endpoint, in bits
	 * per second.
	 * @deprecated use the appropriate method from {@link MediaStreamStats2} instead.
	 */
	@Deprecated
	long getSendingBitrate();
}
