/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.event;

import net.java.sip.communicator.service.protocol.ChatRoom;
import net.java.sip.communicator.service.protocol.ChatRoomMember;
import net.java.sip.communicator.service.protocol.IMessage;

import java.util.EventObject;

/**
 * <code>ChatRoomMessageDeliveredEvent</code>s confirm successful delivery of an instant message.
 *
 * @author Emil Ivov
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public class ChatRoomMessageDeliveryFailedEvent extends EventObject
{
    /**
     * Serial version UID.
     */
    private static final long serialVersionUID = 0L;

    /**
     * The chat room member that this message has been sent to.
     */
    private final ChatRoomMember to;

    /**
     * An error code indicating the reason for the failure of this delivery.
     */
    private final int errorCode;

    /**
     * Contains a human readable message indicating the reason for the failure or null if the reason is unknown.
     */
    private final String reason;

    /**
     * A timestamp indicating the exact date when the event occurred.
     */
    private final long timestamp;

    /**
     * The received <code>IMessage</code>.
     */
    private final IMessage message;

    /**
     * Creates a <code>ChatRoomMessageDeliveryFailedEvent</code> indicating failure of delivery of a
     * message to the specified <code>ChatRoomMember</code> in the specified <code>ChatRoom</code>.
     *
     * @param source the <code>ChatRoom</code> in which the message was sent
     * @param to the <code>ChatRoomMember</code> that this message was sent to.
     * @param errorCode an errorCode indicating the reason of the failure.
     * @param timestamp the exact timestamp when it was determined that delivery had failed.
     * @param reason a human readable message indicating the reason for the failure or null if the reason is unknown.
     * @param message the received <code>IMessage</code>.
     */
    public ChatRoomMessageDeliveryFailedEvent(ChatRoom source, ChatRoomMember to, int errorCode, long timestamp,
            String reason, IMessage message)
    {
        super(source);
        this.to = to;
        this.errorCode = errorCode;
        this.timestamp = timestamp;
        this.reason = reason;
        this.message = message;
    }

    /**
     * Returns a reference to the <code>ChatRoomMember</code> that the source (failed) <code>IMessage</code>
     * was sent to.
     *
     * @return a reference to the <code>ChatRoomMember</code> that the source failed <code>IMessage</code>
     * was sent to.
     */
    public ChatRoomMember getDestinationChatRoomMember()
    {
        return to;
    }

    /**
     * Returns the received message.
     *
     * @return the <code>IMessage</code> that triggered this event.
     */
    public IMessage getMessage()
    {
        return message;
    }

    /**
     * Returns an error code describing the reason for the failure of the message delivery.
     *
     * @return an error code describing the reason for the failure of the message delivery.
     */
    public int getErrorCode()
    {
        return errorCode;
    }

    /**
     * Returns a human readable message indicating the reason for the failure or null if the reason is unknown.
     *
     * @return a human readable message indicating the reason for the failure or null if the reason is unknown.
     */
    public String getReason()
    {
        return reason;
    }

    /**
     * A timestamp indicating the exact date when the event occurred (in this case it is the moment
     * when it was determined that message delivery has failed).
     *
     * @return a long indicating when the event occurred in the form of date timestamp.
     */
    public long getTimestamp()
    {
        return timestamp;
    }

    /**
     * Returns the <code>ChatRoom</code> that triggered this event.
     *
     * @return the <code>ChatRoom</code> that triggered this event.
     */
    public ChatRoom getSourceChatRoom()
    {
        return (ChatRoom) getSource();
    }
}
