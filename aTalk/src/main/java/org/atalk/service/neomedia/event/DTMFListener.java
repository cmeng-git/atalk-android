/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia.event;

/**
 * The purpose of a <code>DTMFListener</code> is to notify implementors when new DMTF tones are received
 * by this MediaService implementation.
 *
 * @author Emil Ivov
 */
public interface DTMFListener
{

	/**
	 * Indicates that we have started receiving a <code>DTMFTone</code>.
	 *
	 * @param event
	 *        the <code>DTMFToneEvent</code> instance containing the <code>DTMFTone</code>
	 */
	public void dtmfToneReceptionStarted(DTMFToneEvent event);

	/**
	 * Indicates that reception of a DTMF tone has stopped.
	 *
	 * @param event
	 *        the <code>DTMFToneEvent</code> instance containing the <code>DTMFTone</code>
	 */
	public void dtmfToneReceptionEnded(DTMFToneEvent event);
}
