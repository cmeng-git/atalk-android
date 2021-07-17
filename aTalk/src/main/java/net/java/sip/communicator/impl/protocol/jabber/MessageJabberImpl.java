/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import net.java.sip.communicator.service.protocol.AbstractMessage;

import org.atalk.android.gui.chat.ChatMessage;

/**
 * A simple implementation of the <tt>IMessage</tt> interface. Right now the message only supports
 * test contents and no binary data.
 *
 * @author Damian Minkov
 * @author Lubomir Marinov
 * @author Eng Chong Meng
 */
public class MessageJabberImpl extends AbstractMessage
{
    /**
     * Creates an instance of this Message with the specified parameters.
     *
     * @param content the text content of the message.
     * @param encType contains both mime and encryption types @see ChatMessage.ENC_TYPE definition
     * @param subject the subject of the message or null for empty.
     * @param messageUID @see net.java.sip.communicator.service.protocol.IMessage#getMessageUID()
     */
    public MessageJabberImpl(String content, int encType, String subject, String messageUID)
    {
        super(content, encType, subject, messageUID);
    }
}
