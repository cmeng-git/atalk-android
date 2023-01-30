/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.event;

import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.ContactResource;
import net.java.sip.communicator.service.protocol.IMessage;

import org.atalk.android.gui.chat.ChatMessage;

import java.util.Date;
import java.util.EventObject;

/**
 * <code>MessageDeliveredEvent</code>s confirm successful delivery of an instant message.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
public class MessageDeliveredEvent extends EventObject {
    /**
     * Serial version UID.
     */
    private static final long serialVersionUID = 0L;

    /**
     * The contact that has sent this message.
     */
    private final Contact mContact;

    /**
     * The <code>ContactResource</code>, from which the message was sent.
     */
    private final ContactResource mContactResource;

    /**
     * Message sender full jid
     */
    private final String mSender;

    /**
     * A timestamp indicating the exact date when the event occurred.
     */
    private final Date mTimestamp;

    /**
     * The ID of the message being corrected, or null if this was a new message and not a message correction.
     */
    private String correctedMessageUID;

    /**
     * Whether the delivered message is a sms message.
     */
    private boolean smsMessage = false;

    /**
     * Whether the delivered message is encrypted or not.
     */
    private boolean isMessageEncrypted = false;

    /**
     * Creates a <code>MessageDeliveredEvent</code> representing delivery of the <code>source</code>
     * message to the specified <code>to</code> contact.
     *
     * @param source the <code>IMessage</code> whose delivery this event represents.
     * @param contact the <code>Contact</code> that this message was sent to.
     * @param correctedMessageUID The ID of the message being corrected.
     */
    public MessageDeliveredEvent(IMessage source, Contact contact, ContactResource contactResource,
            String sender, String correctedMessageUID) {
        this(source, contact, contactResource, sender, new Date());
        this.correctedMessageUID = correctedMessageUID;
    }

    /**
     * Creates a <code>MessageDeliveredEvent</code> representing delivery of the <code>source</code>
     * message to the specified <code>to</code> contact.
     *
     * @param source the <code>IMessage</code> whose delivery this event represents.
     * @param contact the <code>Contact</code> that this message was sent to.
     * @param contactResource the <code>Contact</code> resource that this message was sent to
     * @param sender the fullJid from which this message was sent
     * @param timestamp a date indicating the exact moment when the event occurred
     */
    public MessageDeliveredEvent(IMessage source, Contact contact, ContactResource contactResource, String sender, Date timestamp) {
        super(source);
        mContact = contact;
        mContactResource = contactResource;
        mSender = sender;
        mTimestamp = timestamp;
    }

    /**
     * Returns a reference to the <code>Contact</code> that <code>IMessage</code> was sent to.
     *
     * @return a reference to the <code>Contact</code> that has send the <code>IMessage</code>
     * whose reception this event represents.
     */
    public Contact getContact() {
        return mContact;
    }

    /**
     * Returns a reference to the <code>ContactResource</code> that has sent the <code>IMessage</code>
     * whose reception this event represents.
     *
     * @return a reference to the <code>ContactResource</code> that has sent the <code>IMessage</code>
     * whose reception this event represents.
     */
    public ContactResource getContactResource() {
        return mContactResource;
    }

    /**
     * Get the message sender fullJid
     *
     * @return sender fullJid
     */
    public String getSender() {
        return mSender;
    }

    /**
     * Returns the message that triggered this event
     *
     * @return the <code>IMessage</code> that triggered this event.
     */
    public IMessage getSourceMessage() {
        return (IMessage) getSource();
    }

    /**
     * A timestamp indicating the exact date when the event occurred.
     *
     * @return a Date indicating when the event occurred.
     */
    public Date getTimestamp() {
        return mTimestamp;
    }

    /**
     * Returns the type of message event represented by this event instance.
     *
     * @return one of the XXX_MESSAGE_DELIVERED fields of this class indicating the type of this event.
     */
    public int getEventType() {
        return isSmsMessage() ? ChatMessage.MESSAGE_SMS_OUT : ChatMessage.MESSAGE_OUT;
    }

    /**
     * Returns the ID of the message being corrected, or null if this was a new message and not a
     * message correction.
     *
     * @return the ID of the message being corrected, or null if this was a new message and not a
     * message correction.
     */
    public String getCorrectedMessageUID() {
        return correctedMessageUID;
    }

    /**
     * Sets the ID of the message being corrected to the passed ID.
     *
     * @param correctedMessageUID The ID of the message being corrected.
     */
    public void setCorrectedMessageUID(String correctedMessageUID) {
        this.correctedMessageUID = correctedMessageUID;
    }

    /**
     * Sets whether the message is a sms one.
     *
     * @param smsMessage whether it is a sms one.
     */
    public void setSmsMessage(boolean smsMessage) {
        this.smsMessage = smsMessage;
    }

    /**
     * Returns whether the delivered message is a sms one.
     *
     * @return whether the delivered message is a sms one.
     */
    public boolean isSmsMessage() {
        return smsMessage;
    }

    /**
     * Returns <code>true</code> if the message is encrypted and <code>false</code> if not.
     *
     * @return <code>true</code> if the message is encrypted and <code>false</code> if not.
     */
    public boolean isMessageEncrypted() {
        return isMessageEncrypted;
    }

    /**
     * Sets the message encrypted flag of the event.
     *
     * @param isMessageEncrypted the value to be set.
     */
    public void setMessageEncrypted(boolean isMessageEncrypted) {
        this.isMessageEncrypted = isMessageEncrypted;
    }
}
