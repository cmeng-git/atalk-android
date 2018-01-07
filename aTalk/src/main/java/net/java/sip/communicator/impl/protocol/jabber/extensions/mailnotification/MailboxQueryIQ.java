/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber.extensions.mailnotification;

import org.jivesoftware.smack.packet.IQ;

/**
 * A straightforward <tt>IQ</tt> extension. The <tt>QueryNotify</tt> object is used to create
 * queries for the Gmail mail server. It creates a simple <tt>IQ</tt> packet which represents the
 * query.
 *
 * @author Matthieu Helleringer
 * @author Alain Knaebel
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
public class MailboxQueryIQ extends IQ
{
	/**
	 * The name of the element that Google use to transport new mail notifications.
	 */
	public static final String ELEMENT_NAME = "query";

	/**
	 * The name space for new mail notification packets.
	 */
	public static final String NAMESPACE = "google:mail:notify";

	/**
	 * newer-than-time element name.
	 */
	public static final String ELEMENT_NEWER_THAN_TIME = "newer-than-time";

	/**
	 * newer-than-tid element name.
	 */
	public static final String ELEMENT_NEWER_THAN_TID = "newer-than-tid";

	/**
	 * The value of the newer-than-time attribute in this query;
	 */
	private long newerThanTime = -1;

	/**
	 * The value of the newer-than-tid attribute in this query;
	 */
	private long newerThanTid = -1;

	public MailboxQueryIQ()
	{
		super(ELEMENT_NAME, NAMESPACE);
	}

	/**
	 * Returns the sub-element XML section of the IQ packet.
	 *
	 * @return the child element section of the IQ XML. String
	 */
	@Override
	protected IQChildElementXmlStringBuilder getIQChildElementBuilder(
		IQChildElementXmlStringBuilder xml)
	{
		if (getNewerThanTime() != -1)
			xml.optLongAttribute(ELEMENT_NEWER_THAN_TIME, getNewerThanTime());

		if (getNewerThanTid() != -1)
			xml.optLongAttribute(ELEMENT_NEWER_THAN_TID, getNewerThanTid());

		xml.append("/>");
		return xml;
	}

	/**
	 * Sets the value of the "newer-than-time" attribute. The value indicates the time of the oldest
	 * unread email to retrieve, in milliseconds since the UNIX epoch (00:00:00 UTC, January 1
	 * 1970). When querying for the first time, you should omit this attribute (i.e. not call this
	 * method or call it with a <tt>-1</tt> value) to return a set of the most recent unread mail.
	 * The sever will return only unread mail received after this time. If using this attribute, you
	 * should also use newer-than-tid for best results.
	 *
	 * @param newerThanTime
	 *        the time of the oldest unread email to retrieve or <tt>-1</tt> if the newer-than-time
	 *        attribute should be omitted.
	 */
	public void setNewerThanTime(long newerThanTime)
	{
		this.newerThanTime = newerThanTime;
	}

	/**
	 * Returns the value of the "newer-than-time" attribute. The value indicates the time of the
	 * oldest unread email to retrieve, in milliseconds since the UNIX epoch (00:00:00 UTC, January
	 * 1 1970). When querying for the first time, you should omit this attribute (i.e. not call this
	 * method or call it with a <tt>-1</tt> value) to return a set of the most recent unread mail.
	 * The sever will return only unread mail received after this time. If using this attribute, you
	 * should also use newer-than-tid for best results.
	 *
	 * @return the time of the oldest unread email to retrieve or <tt>-1</tt> if the attribute is to
	 *         be omitted.
	 */
	public long getNewerThanTime()
	{
		return this.newerThanTime;
	}

	/**
	 * Sets the value of the "newer-than-tid" attribute. The value indicates the highest thread
	 * number of messages to return, where higher numbers are more recent email threads. The server
	 * will return only threads newer than that specified by this attribute. If using this
	 * attribute, you should also use newer-than-time for best results. When querying for the first
	 * time, you should omit this value.
	 *
	 * @param newerThanTid
	 *        the time of the oldest unread email to retrieve or <tt>-1</tt> if the newer-than-time
	 *        attribute should be omitted.
	 */
	public void setNewerThanTid(long newerThanTid)
	{
		this.newerThanTid = newerThanTid;
	}

	/**
	 * Returns the value of the "newer-than-tid" attribute. The value indicates the highest thread
	 * number of messages to return, where higher numbers are more recent email threads. The server
	 * will return only threads newer than that specified by this attribute. If using this
	 * attribute, you should also use newer-than-time for best results. When querying for the first
	 * time, you should omit this value.
	 *
	 * @return the time of the oldest unread email to retrieve or <tt>-1</tt> if the newer-than-time
	 *         attribute is to be omitted.
	 */
	public long getNewerThanTid()
	{
		return this.newerThanTid;
	}
}
