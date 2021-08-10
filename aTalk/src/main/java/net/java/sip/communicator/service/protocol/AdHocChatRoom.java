/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol;

import net.java.sip.communicator.service.protocol.event.AdHocChatRoomMessageListener;
import net.java.sip.communicator.service.protocol.event.AdHocChatRoomParticipantPresenceListener;

import org.jivesoftware.smackx.omemo.OmemoManager;
import org.jxmpp.jid.EntityBareJid;

import java.util.List;

/**
 * Represents an ad-hoc rendezvous point where multiple chat users could communicate together. This
 * interface describes the main methods used by some protocols for multi user chat, without useless
 * methods (such as kicking a participant) which aren't supported by these protocols (MSN, ICQ etc.).
 * <p>
 * <tt>AdHocChatRoom</tt> acts like a simplified <tt>ChatRoom</tt>.
 *
 * @author Valentin Martinet
 * @author Eng Chong Meng
 */
public interface AdHocChatRoom
{
    /**
     * Returns the name of this <tt>AdHocChatRoom</tt>. The name can't be changed until the
     * <tt>AdHocChatRoom</tt> is ended.
     *
     * @return a <tt>String</tt> containing the name
     */
    String getName();

    /**
     * Returns the identifier of this <tt>AdHocChatRoom</tt>. The identifier of the ad-hoc chat
     * room would have the following syntax: [adHocChatRoomName]@[adHocChatRoomServer]@[accountID]
     *
     * @return a <tt>String</tt> containing the identifier of this <tt>AdHocChatRoom</tt>.
     */
    String getIdentifier();

    /**
     * Adds a listener that will be notified of changes in our participation in the ad-hoc room
     * such as us being join, left...
     *
     * @param listener a member participation listener.
     */
    void addParticipantPresenceListener(AdHocChatRoomParticipantPresenceListener listener);

    /**
     * Removes a participant presence listener.
     *
     * @param listener a member participation listener.
     */
    void removeParticipantPresenceListener(AdHocChatRoomParticipantPresenceListener listener);

    /**
     * Registers <tt>listener</tt> so that it would receive events every time a new message is
     * received on this ad-hoc chat room.
     *
     * @param listener a <tt>MessageListener</tt> that would be notified every time a new message is received
     * on this ad-hoc chat room.
     */
    void addMessageListener(AdHocChatRoomMessageListener listener);

    /**
     * Removes <tt>listener</tt> so that it won't receive any further message events from this
     * ad-hoc room.
     *
     * @param listener the <tt>MessageListener</tt> to remove from this ad-hoc room
     */
    void removeMessageListener(AdHocChatRoomMessageListener listener);

    /**
     * Invites another <tt>Contact</tt> to this ad-hoc chat room.
     *
     * @param userAddress the address of the <tt>Contact</tt> of the user to invite to the ad-hoc room.
     * @param reason a reason, subject, or welcome message that would tell users why they are being invited.
     */
    void invite(EntityBareJid userAddress, String reason);

    /**
     * Returns a <tt>List</tt> of <tt>Contact</tt>s corresponding to all participants currently
     * participating in this room.
     *
     * @return a <tt>List</tt> of <tt>Contact</tt>s instances corresponding to all room members.
     */
    List<Contact> getParticipants();

    /**
     * Returns the number of participants that are currently in this ad-hoc chat room.
     *
     * @return int the number of <tt>Contact</tt>s, currently participating in this ad-hoc room.
     */
    int getParticipantsCount();

    /**
     * Create a <tt>IMessage</tt> instance for sending a simple text messages with default
     * (text/plain) content type and encoding.
     *
     * @param messageText the string content of the message.
     * @return IMessage the newly created message
     */
    IMessage createMessage(String messageText);

    /**
     * Create a IMessage instance for sending arbitrary MIME-encoding content.
     *
     * @param content content value
     * @param encType See IMessage for definition of encType e.g. Encryption, encode & remoteOnly
     * @param subject a <tt>String</tt> subject or <tt>null</tt> for now subject.
     * @return the newly created message.
     */
    IMessage createMessage(String content, int encType, String subject);

    /**
     * Sends the <tt>IMessage</tt> to this ad-hoc chat room.
     *
     * @param message the <tt>IMessage</tt> to send.
     */
    void sendMessage(IMessage message);

    void sendMessage(IMessage message, OmemoManager omemoManager);

    /**
     * Returns a reference to the provider that created this room.
     *
     * @return a reference to the <tt>ProtocolProviderService</tt> instance that created this ad-hoc room.
     */
    ProtocolProviderService getParentProvider();

    /**
     * Joins this ad-hoc chat room with the nickname of the local user so that the user would start
     * receiving events and messages for it.
     *
     * @throws OperationFailedException with the corresponding code if an error occurs while joining the ad-hoc room.
     */
    void join()
            throws OperationFailedException;

    /**
     * Leaves this chat room. Once this method is called, the user won't be listed as a member of
     * the chat room any more and no further chat events will be delivered.
     */
    void leave();
}
