/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.event;

import org.atalk.service.neomedia.DTMFTone;

import java.util.EventObject;

/**
 * <code>DTMFReceivedEvent</code>s indicate reception of a DTMF tone.
 *
 * @author Damian Minkov
 * @author Boris Grozev
 */
public class DTMFReceivedEvent extends EventObject
{
	/**
	 * Serial version UID.
	 */
	private static final long serialVersionUID = 0L;

	/**
	 * The tone.
	 */
	private final DTMFTone value;

	/**
	 * The duration.
	 */
	private final long duration;

	/**
	 * Whether this <code>DTMFReceivedEvent</code> represents the start of reception of a tone (if
	 * <code>true</code>), the end of reception of a tone (if <code>false</code>), or the reception of a
	 * tone with a given duration (if <code>null</code>).
	 */
	private final Boolean start;

	/**
	 * Creates a <code>MessageReceivedEvent</code> representing reception of the <code>source</code> message
	 * received from the specified <code>from</code> contact.
	 *
	 * @param source
	 *        the source of the event.
	 * @param value
	 *        dmtf tone value.
	 * @param start
	 *        whether this event represents the start of reception (if <code>true</code>), the end of
	 *        reception (if <code>false</code>) or the reception of a tone with a given direction (if
	 *        <code>null</code>).
	 */
	public DTMFReceivedEvent(Object source, DTMFTone value, boolean start)
	{
		this(source, value, -1, start);
	}

	/**
	 * Creates a <code>MessageReceivedEvent</code> representing reception of the <code>source</code> message
	 * received from the specified <code>from</code> contact.
	 *
	 * @param source
	 *        the source of the event.
	 * @param value
	 *        dmtf tone value.
	 * @param duration
	 *        duration of the DTMF tone.
	 */
	public DTMFReceivedEvent(Object source, DTMFTone value, long duration)
	{
		this(source, value, duration, null);
	}

	/**
	 * Creates a <code>MessageReceivedEvent</code> representing reception of the <code>source</code> message
	 * received from the specified <code>from</code> contact.
	 *
	 * @param source
	 *        the source of the event.
	 * @param value
	 *        dmtf tone value.
	 * @param duration
	 *        duration of the DTMF tone.
	 * @param start
	 *        whether this event represents the start of reception (if <code>true</code>), the end of
	 *        reception (if <code>false</code>) or the reception of a tone with a given direction (if
	 *        <code>null</code>).
	 */
	public DTMFReceivedEvent(Object source, DTMFTone value, long duration, Boolean start)
	{
		super(source);

		this.value = value;
		this.duration = duration;
		this.start = start;
	}

	/**
	 * Returns the tone this event is indicating of.
	 * 
	 * @return the tone this event is indicating of.
	 */
	public DTMFTone getValue()
	{
		return value;
	}

	/**
	 * Returns the tone duration for this event.
	 * 
	 * @return the tone duration for this event.
	 */
	public long getDuration()
	{
		return duration;
	}

	/**
	 * Returns the value of the <code>start</code> attribute of this <code>DTMFReceivedEvent</code>, which
	 * indicates whether this <code>DTMFReceivedEvent</code> represents the start of reception of a tone
	 * (if <code>true</code>), the end of reception of a tone (if <code>false</code>), or the reception of a
	 * tone with a given duration (if <code>null</code>).
	 */
	public Boolean getStart()
	{
		return start;
	}
}
