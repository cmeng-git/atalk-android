/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol;

import net.java.sip.communicator.service.protocol.event.MessageListener;

import java.io.File;

/**
 * Provides basic functionality for sending and receiving SMS Messages.
 *
 * @author Damian Minkov
 */
public interface OperationSetSmsMessaging extends OperationSet
{
	/**
	 * Create a IMessage instance for sending arbitrary MIME-encoding content.
	 *
	 * @param content
	 *        content value
	 * @param contentType
	 *        the MIME-type for <code>content</code>
	 * @param contentEncoding
	 *        encoding used for <code>content</code>
	 * @return the newly created message.
	 */
	public IMessage createMessage(byte[] content, String contentType, String contentEncoding);

	/**
	 * Create a IMessage instance for sending a sms messages with default (text/plain) content type
	 * and encoding.
	 *
	 * @param messageText
	 *        the string content of the message.
	 * @return IMessage the newly created message
	 */
	public IMessage createMessage(String messageText);

	/**
	 * Sends the <code>message</code> to the destination indicated by the <code>to</code> contact.
	 * 
	 * @param to
	 *        the <code>Contact</code> to send <code>message</code> to
	 * @param message
	 *        the <code>IMessage</code> to send.
	 * @throws java.lang.IllegalStateException
	 *         if the underlying stack is not registered and initialized.
	 * @throws java.lang.IllegalArgumentException
	 *         if <code>to</code> is not an instance belonging to the underlying implementation.
	 */
	public void sendSmsMessage(Contact to, IMessage message)
		throws IllegalStateException, IllegalArgumentException;

	/**
	 * Sends the <code>message</code> to the destination indicated by the <code>to</code> parameter.
	 * 
	 * @param to
	 *        the destination to send <code>message</code> to
	 * @param message
	 *        the <code>IMessage</code> to send.
	 * @throws java.lang.IllegalStateException
	 *         if the underlying stack is not registered and initialized.
	 * @throws java.lang.IllegalArgumentException
	 *         if <code>to</code> is not an instance belonging to the underlying implementation.
	 */
	public void sendSmsMessage(String to, IMessage message)
		throws IllegalStateException, IllegalArgumentException;

	/**
	 * Sends the <code>file</code> to the destination indicated by the <code>to</code> parameter.
	 * 
	 * @param to
	 *        the destination to send <code>message</code> to
	 * @param file
	 *        the <code>file</code> to send.
	 * @throws java.lang.IllegalStateException
	 *         if the underlying stack is not registered and initialized.
	 * @throws java.lang.IllegalArgumentException
	 *         if <code>to</code> is not an instance belonging to the underlying implementation.
	 * @throws OperationNotSupportedException
	 *         if the given contact client or server does not support file transfers
	 */
	public FileTransfer sendMultimediaFile(Contact to, File file)
		throws IllegalStateException, IllegalArgumentException, OperationNotSupportedException;

	/**
	 * Registers a MessageListener with this operation set so that it gets notifications of
	 * successful message delivery, failure or reception of incoming messages..
	 *
	 * @param listener
	 *        the <code>MessageListener</code> to register.
	 */
	public void addMessageListener(MessageListener listener);

	/**
	 * Unregisters <code>listener</code> so that it won't receive any further notifications upon
	 * successful message delivery, failure or reception of incoming messages..
	 *
	 * @param listener
	 *        the <code>MessageListener</code> to unregister.
	 */
	public void removeMessageListener(MessageListener listener);

	/**
	 * Determines whether the protocol supports the supplied content type
	 *
	 * @param contentType
	 *        the type we want to check
	 * @return <code>true</code> if the protocol supports it and <code>false</code> otherwise.
	 */
	public boolean isContentTypeSupported(String contentType);

	/**
	 * Returns the contact to send sms to.
	 * 
	 * @param to
	 *        the number to send sms.
	 * @return the contact representing the receiver of the sms.
	 */
	public Contact getContact(String to);

	/**
	 * Whether the implementation do not know how to send sms to the supplied contact and should as
	 * for number.
	 * 
	 * @param to
	 *        the contact to send sms.
	 * @return whether user needs to enter number for the sms recipient.
	 */
	public boolean askForNumber(Contact to);
}
