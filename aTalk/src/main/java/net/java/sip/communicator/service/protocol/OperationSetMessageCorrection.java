/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol;

/**
 * Provides functionality for correcting instant messages.
 *
 * @author Ivan Vergiliev
 */
public interface OperationSetMessageCorrection extends OperationSetBasicInstantMessaging
{
    /**
     * Replaces the message with ID <code>correctedMessageUID</code> sent to the contact <code>to</code>
     * with the message <code>message</code>
     *
     * @param to The contact to send the message to.
     * @param resource The ContactResource to send the message to.
     * @param message The new message.
     * @param correctedMessageUID The ID of the message being replaced.
     */
    public void correctMessage(Contact to, ContactResource resource, IMessage message, String correctedMessageUID);
}
