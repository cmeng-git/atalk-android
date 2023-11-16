/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia.event;

import org.atalk.service.neomedia.AudioMediaStream;
import org.atalk.service.neomedia.DTMFRtpTone;

import java.util.EventObject;

/**
 * This event represents starting or ending reception of a specific <code>DTMFRtpTone</code>.
 *
 * @author Emil Ivov
 */
public class DTMFToneEvent extends EventObject
{
	/**
	 * Serial version UID.
	 */
	private static final long serialVersionUID = 0L;

	/**
	 * The tone that this event is pertaining to.
	 */
	private final DTMFRtpTone dtmfTone;

	/**
	 * Creates an instance of this <code>DTMFToneEvent</code> with the specified source stream and DTMF
	 * tone.
	 *
	 * @param source
	 *        the <code>AudioMediaSteam</code> instance that received the tone.
	 * @param dtmfTone
	 *        the tone that we (started/stopped) receiving.
	 */
	public DTMFToneEvent(AudioMediaStream source, DTMFRtpTone dtmfTone)
	{
		super(source);

		this.dtmfTone = dtmfTone;
	}

	/**
	 * Returns the <code>DTMFTone</code> instance that this event pertains to.
	 *
	 * @return the <code>DTMFTone</code> instance that this event pertains to.
	 */
	public DTMFRtpTone getDtmfTone()
	{
		return dtmfTone;
	}
}
