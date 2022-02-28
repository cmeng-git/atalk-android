/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia.event;

/**
 * Represents a listener of RTCP feedback messages such as PLI (Picture Loss Indication) or FIR
 * (Full Intra Request).
 *
 * @author Sebastien Vincent
 */
public interface RTCPFeedbackMessageListener
{
	/**
	 * Notifies this <code>RTCPFeedbackMessageListener</code> that an RTCP feedback message has been
	 * received
	 *
	 * @param event
	 *        an <code>RTCPFeedbackMessageEvent</code> which specifies the details of the notification
	 *        event such as the feedback message type and the payload type
	 */
	public void rtcpFeedbackMessageReceived(RTCPFeedbackMessageEvent event);
}
