/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia.rtp;

import net.sf.fmj.media.rtp.RTCPReport;

/**
 * A default implementation of <code>RTCPReportListener</code> to facilitate implementers.
 *
 * @author Lyubomir Marinov
 */
public class RTCPReportAdapter implements RTCPReportListener
{
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void rtcpExtendedReportReceived(RTCPExtendedReport extendedReport)
	{
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void rtcpExtendedReportSent(RTCPExtendedReport extendedReport)
	{
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void rtcpReportReceived(RTCPReport report)
	{
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void rtcpReportSent(RTCPReport report)
	{
	}
}
