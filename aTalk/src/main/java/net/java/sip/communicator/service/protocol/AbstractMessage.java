/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol;

import net.java.sip.communicator.util.Logger;

/**
 * Represents a default implementation of {@link Message} in order to make it easier for
 * implementers to provide complete solutions while focusing on implementation-specific details.
 *
 * @author Lubomir Marinov
 */
public abstract class AbstractMessage implements Message
{
	/**
	 * The <tt>Logger</tt> used by the <tt>AbstractMessage</tt> class and its instances for logging
	 * output.
	 */
	private static final Logger logger = Logger.getLogger(AbstractMessage.class);

	private String content;

	private final int encType;

	private final String messageUID;

	/**
	 * The content of this message, in raw bytes according to the encoding.
	 */
	private byte[] rawData;

	private final String subject;

	protected AbstractMessage(String content, int encType, String subject)
	{
		this.encType = encType;
		this.subject = subject;

		setContent(content);
		this.messageUID = createMessageUID();
	}

	protected AbstractMessage(String content, int encType, String subject, String messageUID)
	{
		this.encType = encType;
		this.subject = subject;

		setContent(content);
		this.messageUID = messageUID == null ? createMessageUID() : messageUID;
	}

	protected String createMessageUID()
	{
		return String.valueOf(System.currentTimeMillis()) + String.valueOf(hashCode());
	}

	/**
	 * Returns the content of this message if representable in text form or null if this message
	 * does not contain text data.
	 * <p>
	 * The implementation is final because it caches the raw data of the content.
	 * </p>
	 *
	 * @return a String containing the content of this message or null if the message does not
	 *         contain data representable in text form.
	 */
	public final String getContent()
	{
		return content;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.java.sip.communicator.service.protocol.Message#getEncType()
	 */
	public int getEncType()
	{
		return encType;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.java.sip.communicator.service.protocol.Message#getMessageUID()
	 */
	public String getMessageUID()
	{
		return messageUID;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.java.sip.communicator.service.protocol.Message#getRawData()
	 */
	public byte[] getRawData()
	{
		if (rawData == null) {
			String content = getContent();
			rawData = content.getBytes();
		}
		return rawData;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.java.sip.communicator.service.protocol.Message#getSize()
	 */
	public int getSize()
	{
		return getRawData().length;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.java.sip.communicator.service.protocol.Message#getSubject()
	 */
	public String getSubject()
	{
		return subject;
	}

	protected void setContent(String content)
	{
		if (!equals(this.content, content)) {
			this.content = content;
			this.rawData = null;
		}
	}

	private static boolean equals(String a, String b)
	{
		return (a == null) ? (b == null) : a.equals(b);
	}
}
