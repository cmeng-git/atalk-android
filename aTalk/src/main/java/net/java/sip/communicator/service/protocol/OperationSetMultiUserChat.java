/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol;

import net.java.sip.communicator.service.protocol.event.ChatRoomInvitationListener;
import net.java.sip.communicator.service.protocol.event.ChatRoomInvitationRejectionListener;
import net.java.sip.communicator.service.protocol.event.LocalUserChatRoomPresenceListener;

import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.Jid;
import org.jxmpp.stringprep.XmppStringprepException;

import java.util.List;
import java.util.Map;

/**
 * Allows creating, configuring, joining and administering of individual text-based conference
 * rooms.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
public interface OperationSetMultiUserChat extends OperationSet
{
    /**
     * Returns the <code>List</code> of <code>String</code>s indicating chat rooms currently available on
     * the server that this protocol provider is connected to.
     *
     * @return a <code>java.util.List</code> of the name <code>String</code>s for chat rooms that are
     * currently available on the server that this protocol provider is connected to.
     * @throws OperationFailedException if we failed retrieving this list from the server.
     * @throws OperationNotSupportedException if the server does not support multi-user chat
     */
    List<EntityBareJid> getExistingChatRooms()
            throws OperationFailedException, OperationNotSupportedException;

    /**
     * Returns a list of the chat rooms that we have joined and are currently active in.
     *
     * @return a <code>List</code> of the rooms where the user has joined using a given connection.
     */
    List<ChatRoom> getCurrentlyJoinedChatRooms();

    /**
     * Returns a list of the chat rooms that <code>chatRoomMember</code> has joined and is currently active in.
     *
     * @param chatRoomMember the chatRoomMember whose current ChatRooms we will be querying.
     * @return a list of the chat rooms that <code>chatRoomMember</code> has joined and is currently
     * active in.
     * @throws OperationFailedException if an error occurs while trying to discover the room on the server.
     * @throws OperationNotSupportedException if the server does not support multi-user chat
     */
    List<String> getCurrentlyJoinedChatRooms(ChatRoomMember chatRoomMember)
            throws OperationFailedException, OperationNotSupportedException;

    /**
     * Creates a room with the named <code>roomName</code> and according to the specified
     * <code>roomProperties</code> on the server that this protocol provider is currently connected to.
     * When the method returns the room the local user will not have joined it and thus will not
     * receive messages on it until the <code>ChatRoom.join()</code> method is called.
     *
     * @param roomName the name of the <code>ChatRoom</code> to create.
     * @param roomProperties properties specifying how the room should be created; <code>null</code> for no properties
     * just like an empty <code>Map</code>
     * @return the newly created <code>ChatRoom</code> named <code>roomName</code>.
     * @throws OperationFailedException if the room couldn't be created for some reason (e.g. room already exists; user
     * already joined to an existent room or user has no permissions to create a chat room).
     * @throws OperationNotSupportedException if chat room creation is not supported by this server
     */
    ChatRoom createChatRoom(String roomName, Map<String, Object> roomProperties)
            throws OperationFailedException, OperationNotSupportedException, XmppStringprepException;

    /**
     * Returns a reference to a chatRoom named <code>roomName</code> or null if no room with the given
     * name exist on the server.
     *
     * @param roomName the name of the <code>ChatRoom</code> that we're looking for.
     * @return the <code>ChatRoom</code> named <code>roomName</code> if it exists, null otherwise.
     * @throws OperationFailedException if an error occurs while trying to discover the room on the server.
     * @throws OperationNotSupportedException if the server does not support multi-user chat
     */
    ChatRoom findRoom(String roomName)
            throws OperationFailedException, OperationNotSupportedException, XmppStringprepException;

    /**
     * @param entityBareJid ChatRoom EntityBareJid
     * @return ChatRoom
     */
    ChatRoom findRoom(EntityBareJid entityBareJid);

    /**
     * Informs the sender of an invitation that we decline their invitation.
     *
     * @param invitation the invitation we are rejecting.
     * @param rejectReason the reason to reject the invitation (optional)
     */
    void rejectInvitation(ChatRoomInvitation invitation, String rejectReason)
            throws OperationFailedException;

    /**
     * Adds a listener to invitation notifications. The listener will be fired anytime an invitation is received.
     *
     * @param listener an invitation listener.
     */
    void addInvitationListener(ChatRoomInvitationListener listener);

    /**
     * Removes <code>listener</code> from the list of invitation listeners registered to receive invitation events.
     *
     * @param listener the invitation listener to remove.
     */
    void removeInvitationListener(ChatRoomInvitationListener listener);

    /**
     * Adds a listener to invitation notifications. The listener will be fired anytime an invitation is received.
     *
     * @param listener an invitation listener.
     */
    void addInvitationRejectionListener(ChatRoomInvitationRejectionListener listener);

    /**
     * Removes the given listener from the list of invitation listeners registered to receive events
     * every time an invitation has been rejected.
     *
     * @param listener the invitation listener to remove.
     */
    void removeInvitationRejectionListener(ChatRoomInvitationRejectionListener listener);

    /**
     * Returns true if <code>contact</code> supports multi-user chat sessions.
     *
     * @param contact reference to the contact whose support for chat rooms we are currently querying.
     * @return a boolean indicating whether <code>contact</code> supports chat rooms.
     */
    boolean isMultiChatSupportedByContact(Contact contact);

    /**
     * Checks if the contact Jid is associated with private messaging contact or not.
     *
     * @return <code>true</code> if the contact Jid not null and is associated with
     * private messaging contact and <code>false</code> if not.
     */
    boolean isPrivateMessagingContact(Jid contactJid);

    /**
     * Adds a listener that will be notified of changes in our participation in a chat room such as
     * us being kicked, joined, left.
     *
     * @param listener a local user participation listener.
     */
    void addPresenceListener(LocalUserChatRoomPresenceListener listener);

    /**
     * Removes a listener that was being notified of changes in our participation in a room such as
     * us being kicked, joined, left.
     *
     * @param listener a local user participation listener.
     */
    void removePresenceListener(LocalUserChatRoomPresenceListener listener);
}
