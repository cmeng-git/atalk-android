/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia.recording;

/**
 * @author Boris Grozev
 */
public interface Synchronizer
{
	/**
	 * Sets the clock rate of the RTP clock for a specific SSRC.
	 * 
	 * @param ssrc
	 *        the SSRC for which to set the RTP clock rate.
	 * @param clockRate
	 *        the clock rate.
	 */
	public void setRtpClockRate(long ssrc, long clockRate);

	/**
	 * Sets the endpoint identifier for a specific SSRC.
	 * 
	 * @param ssrc
	 *        the SSRC for which to set the endpoint identifier.
	 * @param endpointId
	 *        the endpoint identifier to set.
	 */
	public void setEndpoint(long ssrc, String endpointId);

	/**
	 * Notifies this <code>Synchronizer</code> that the RTP timestamp <code>rtpTime</code> (for SSRC
	 * <code>ssrc</code>) corresponds to the NTP timestamp <code>ntpTime</code>.
	 * 
	 * @param ssrc
	 *        the SSRC.
	 * @param rtpTime
	 *        the RTP timestamp which corresponds to <code>ntpTime</code>.
	 * @param ntpTime
	 *        the NTP timestamp which corresponds to <code>rtpTime</code>.
	 */
	public void mapRtpToNtp(long ssrc, long rtpTime, double ntpTime);

	/**
	 * Notifies this <code>Synchronizer</code> that the local timestamp <code>localTime</code> corresponds
	 * to the NTP timestamp <code>ntpTime</code> (for SSRC <code>ssrc</code>).
	 * 
	 * @param ssrc
	 *        the SSRC.
	 * @param localTime
	 *        the local timestamp which corresponds to <code>ntpTime</code>.
	 * @param ntpTime
	 *        the NTP timestamp which corresponds to <code>localTime</code>.
	 */
	public void mapLocalToNtp(long ssrc, long localTime, double ntpTime);

	/**
	 * Tries to find the local time (as returned by <code>System.currentTimeMillis()</code>) that
	 * corresponds to the RTP timestamp <code>rtpTime</code> for the SSRC <code>ssrc</code>.
	 *
	 * Returns -1 if the local time cannot be found (for example because not enough information for
	 * the SSRC has been previously provided to the <code>Synchronizer</code>).
	 *
	 * @param ssrc
	 *        the SSRC with which <code>rtpTime</code> is associated.
	 * @param rtpTime
	 *        the RTP timestamp
	 * @return the local time corresponding to <code>rtpTime</code> for SSRC <code>ssrc</code> if it can be
	 *         calculated, and -1 otherwise.
	 */
	public long getLocalTime(long ssrc, long rtpTime);
}
