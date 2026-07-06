/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.event;

import java.util.Date;
import java.util.EventObject;

import net.java.sip.communicator.impl.protocol.jabber.MessageJabberImpl;
import net.java.sip.communicator.service.protocol.Contact;

import org.atalk.android.gui.chat.ChatMessage;

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
    private String correctionUid;

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
     * @param correctionUid The ID of the message being corrected.
     */
    public MessageDeliveredEvent(MessageJabberImpl source, Contact contact, String sender, String correctionUid) {
        this(source, contact, sender, new Date());
        this.correctionUid = correctionUid;
    }

    /**
     * Creates a <code>MessageDeliveredEvent</code> representing delivery of the <code>source</code>
     * message to the specified <code>to</code> contact.
     *
     * @param source the <code>IMessage</code> whose delivery this event represents.
     * @param contact the <code>Contact</code> that this message was sent to.
     * @param sender the fullJid from which this message was sent
     * @param timestamp a date indicating the exact moment when the event occurred
     */
    public MessageDeliveredEvent(MessageJabberImpl source, Contact contact, String sender, Date timestamp) {
        super(source);
        mContact = contact;
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
    public MessageJabberImpl getMessage() {
        return (MessageJabberImpl) getSource();
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
        return ChatMessage.MESSAGE_OUT;
    }

    /**
     * Returns the ID of the message being corrected, or null if this was a new message and not a
     * message correction.
     *
     * @return the ID of the message being corrected, or null if this was a new message and not a
     * message correction.
     */
    public String getCorrectedMessageUid() {
        return correctionUid;
    }

    /**
     * Sets the ID of the message being corrected to the passed ID.
     *
     * @param correctionUid The ID of the message being corrected.
     */
    public void setCorrectedMessageUid(String correctionUid) {
        this.correctionUid = correctionUid;
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
