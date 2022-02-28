/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia.event;

/**
 * Represents a listener of <code>RTCPFeedbackMessageListener</code> instances.
 *
 * @author Hristo Terezov
 */
public interface RTCPFeedbackMessageCreateListener
{
	/**
	 * Notifies this <code>RTCPFeedbackCreateListener</code> that a <code>RTCPFeedbackMessageListener</code>
	 * is created.
	 *
	 * @param rtcpFeedbackMessageListener
	 *        the created <code>RTCPFeedbackMessageListener</code> instance
	 */
	public void onRTCPFeedbackMessageCreate(RTCPFeedbackMessageListener rtcpFeedbackMessageListener);
}
