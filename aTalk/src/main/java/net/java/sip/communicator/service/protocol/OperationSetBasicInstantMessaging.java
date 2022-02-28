/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol;

import net.java.sip.communicator.service.protocol.event.MessageListener;

import org.jivesoftware.smackx.omemo.OmemoManager;

/**
 * Provides basic functionality for sending and receiving InstantMessages.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
public interface OperationSetBasicInstantMessaging extends OperationSet
{
    /**
     * Create a IMessage instance for sending arbitrary MIME-encoding content.
     *
     * @param content content value
     * @param encType the encryption type for the <code>content</code>
     * @param subject a <code>String</code> subject or <code>null</code> for now subject.
     * @return the newly created message.
     */
    IMessage createMessage(String content, int encType, String subject);

    /**
     * Create a IMessage instance for sending a simple text messages with default (text/plain)
     * content type and encoding.
     *
     * @param messageText the string content of the message.
     * @return IMessage the newly created message
     */
    IMessage createMessage(String messageText);

    /**
     * Create a IMessage instance with the specified UID, content type and a default encoding. This
     * method can be useful when message correction is required. One can construct the corrected
     * message to have the same UID as the message before correction.
     *
     * @param messageText the string content of the message.
     * @param encType the mime and encryption type for the <code>content</code>
     * @param messageUID the unique identifier of this message.
     * @return IMessage the newly created message
     */
    IMessage createMessageWithUID(String messageText, int encType, String messageUID);

    /**
     * Sends the <code>message</code> to the destination indicated by the <code>to</code> contact.
     *
     * @param to the <code>Contact</code> to send <code>message</code> to
     * @param message the <code>IMessage</code> to send.
     * @throws java.lang.IllegalStateException if the underlying ICQ stack is not registered and initialized.
     * @throws java.lang.IllegalArgumentException if <code>to</code> is not an instance belonging to the underlying implementation.
     */
    void sendInstantMessage(Contact to, IMessage message)
            throws IllegalStateException, IllegalArgumentException;

    /**
     * Sends the <code>message</code> to the destination indicated by the <code>to</code> contact and the
     * specific <code>toResource</code>.
     *
     * @param to the <code>Contact</code> to send <code>message</code> to
     * @param toResource the resource to which the message should be send
     * @param message the <code>IMessage</code> to send.
     * @throws java.lang.IllegalStateException if the underlying ICQ stack is not registered and initialized.
     * @throws java.lang.IllegalArgumentException if <code>to</code> is not an instance belonging to the underlying implementation.
     */
    void sendInstantMessage(Contact to, ContactResource toResource, IMessage message);

    void sendInstantMessage(Contact to, ContactResource resource, IMessage message, String correctedMessageUID,
            OmemoManager omemoManager);

    /**
     * Registers a <code>MessageListener</code> with this operation set so that it gets notifications of
     * successful message delivery, failure or reception of incoming messages.
     *
     * @param listener the <code>MessageListener</code> to register.
     */
    void addMessageListener(MessageListener listener);

    /**
     * Unregisters <code>listener</code> so that it won't receive any further notifications upon
     * successful message delivery, failure or reception of incoming messages.
     *
     * @param listener the <code>MessageListener</code> to unregister.
     */
    void removeMessageListener(MessageListener listener);

    /**
     * Determines whether the protocol provider (or the protocol itself) support sending and
     * receiving offline messages. Most often this method would return true for protocols that
     * support offline messages and false for those that don't. It is however possible for a
     * protocol to support these messages and yet have a particular account that does not (i.e.
     * feature not enabled on the protocol server). In cases like this it is possible for this
     * method to return <code>true</code> even when offline messaging is not supported, and then have
     * the sendMessage method throw an <code>OperationFailedException</code> with code
     * OFFLINE_MESSAGES_NOT_SUPPORTED.
     *
     * @return <code>true</code> if the protocol supports offline messages and <code>false</code> otherwise.
     */
    boolean isOfflineMessagingSupported();

    /**
     * Determines whether the protocol supports the supplied content type
     *
     * @param mimeType the mime type we want to check
     * @return <code>true</code> if the protocol supports it and <code>false</code> otherwise.
     */
    boolean isContentTypeSupported(int mimeType);

    /**
     * Determines whether the protocol supports the supplied content type for the given contact.
     *
     * @param mimeType the encode mode we want to check
     * @param contact contact which is checked for supported encType
     * @return <code>true</code> if the contact supports it and <code>false</code> otherwise.
     */
    boolean isContentTypeSupported(int mimeType, Contact contact);

    /**
     * Returns the inactivity timeout in milliseconds.
     *
     * @return The inactivity timeout in milliseconds. Or -1 if undefined
     */
    long getInactivityTimeout();
}
