/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber.extensions.notification;

import org.jivesoftware.smack.packet.IQ;

/**
 * Notification Event. Events are send to notify for some event holding a value.
 *
 * @author Damian Minkov
 */
public class NotificationEventIQ extends IQ
{
	/**
	 * The namespace that input notification belongs to.
	 */
	public static final String NAMESPACE = "sip-communicator:iq:notification";

	/**
	 * The name of the element that contains the notification event data.
	 */
	public static final String ELEMENT_NAME = "notification";

	/**
	 * The name of the argument that contains the event name.
	 */
	static final String EVENT_NAME = "name";

	/**
	 * The name of the argument that contains the event value.
	 */
	static final String EVENT_VALUE = "value";

	/**
	 * The name of the argument that contains the event source.
	 */
	static final String EVENT_SOURCE = "source";

	/**
	 * Current notification event name.
	 */
	private String eventName;

	/**
	 * Current notification event value.
	 */
	private String eventValue;

	/**
	 * Current notification event source.
	 */
	private String eventSource;

	public NotificationEventIQ()
	{
		super(ELEMENT_NAME, NAMESPACE);
	}

	/**
	 * Returns the sub-element XML section of the IQ packet, or <tt>null</tt> if there isn't one.
	 * Packet extensions <b>must</b> be included, if any are defined.
	 * <p>
	 * <p/>
	 * Extensions of this class must override this method.
	 *
	 * @return the child element section of the IQ XML.
	 */
	@Override
	protected IQChildElementXmlStringBuilder getIQChildElementBuilder(
		IQChildElementXmlStringBuilder xml)
	{
		xml.append('>');
		xml.element(EVENT_NAME, this.getEventName());
		xml.optElement(EVENT_VALUE, this.getEventValue());
		xml.optElement(EVENT_SOURCE, this.getEventSource());

		return xml;
	}

	/**
	 * Current notification event name.
	 * 
	 * @return current event name.
	 */
	public String getEventName()
	{
		return eventName;
	}

	/**
	 * Sets event new name.
	 * 
	 * @param eventName
	 *        new event name.
	 */
	public void setEventName(String eventName)
	{
		this.eventName = eventName;
	}

	/**
	 * Current notification event value.
	 * 
	 * @return current event value.
	 */
	public String getEventValue()
	{
		return eventValue;
	}

	/**
	 * Sets event new value.
	 * 
	 * @param eventValue
	 *        new event value.
	 */
	public void setEventValue(String eventValue)
	{
		this.eventValue = eventValue;
	}

	/**
	 * Current notification event source.
	 * 
	 * @return the current notification event source.
	 */
	public String getEventSource()
	{
		return eventSource;
	}

	/**
	 * Sets event source new value.
	 * 
	 * @param eventSource
	 *        value.
	 */
	public void setEventSource(String eventSource)
	{
		this.eventSource = eventSource;
	}
}
