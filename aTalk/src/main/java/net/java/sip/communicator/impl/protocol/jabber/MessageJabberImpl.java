/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import java.util.Date;

import net.java.sip.communicator.service.protocol.AbstractMessage;

/**
 * A simple implementation of the <code>IMessage</code> interface.
 * Right now the message only supports test contents and no binary data.
 *
 * @author Damian Minkov
 * @author Lubomir Marinov
 * @author Eng Chong Meng
 */
public class MessageJabberImpl extends AbstractMessage {
    private final boolean isOutgoing;
    private final Date messageCreatedDate;

    /**
     * Creates an instance of this Message with the specified parameters.
     *
     * @param content the text content of the message.
     * @param encType contains both mime and encryption types etc @see ChatMessage.ENC_TYPE definition
     * @param subject the subject of the message or null for empty.
     * @param messageUid @see net.java.sip.communicator.service.protocol.IMessage#getMessageUid()
     * @param outgoing true if it is an outgoing message
     */
    public MessageJabberImpl(String content, int encType, String subject, String messageUid, boolean outgoing) {
        super(content, encType, subject, messageUid);
        isOutgoing = outgoing;
        messageCreatedDate = new Date();
    }

    public MessageJabberImpl(String content, int encType, String subject, String messageUid, int status, int receiptStatus,
            String serverMsgId, String remoteMsgId, boolean outgoing, Date createdDate) {
        super(content, encType, subject, messageUid, status, receiptStatus, serverMsgId, remoteMsgId);
        isOutgoing = outgoing;
        messageCreatedDate = createdDate;
    }

    public boolean isOutgoing() {
        return isOutgoing;
    }

    public Date getMessageCreatedDate() {
        return messageCreatedDate;
    }
}
