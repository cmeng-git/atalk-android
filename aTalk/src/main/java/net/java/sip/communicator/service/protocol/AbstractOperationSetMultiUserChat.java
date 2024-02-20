/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol;

import net.java.sip.communicator.service.protocol.event.ChatRoomInvitationListener;
import net.java.sip.communicator.service.protocol.event.ChatRoomInvitationReceivedEvent;
import net.java.sip.communicator.service.protocol.event.ChatRoomInvitationRejectedEvent;
import net.java.sip.communicator.service.protocol.event.ChatRoomInvitationRejectionListener;
import net.java.sip.communicator.service.protocol.event.LocalUserChatRoomPresenceChangeEvent;
import net.java.sip.communicator.service.protocol.event.LocalUserChatRoomPresenceListener;

import org.jxmpp.jid.EntityBareJid;

import java.util.Date;
import java.util.List;
import java.util.Vector;

/**
 * Represents a default implementation of <code>OperationSetMultiUserChat</code> in order to make it
 * easier for implementers to provide complete solutions while focusing on implementation-specific details.
 *
 * @author Lubomir Marinov
 */
public abstract class AbstractOperationSetMultiUserChat implements OperationSetMultiUserChat
{
    /**
     * The list of the currently registered <code>ChatRoomInvitationListener</code>s.
     */
    private final List<ChatRoomInvitationListener> invitationListeners = new Vector<>();

    /**
     * The list of <code>ChatRoomInvitationRejectionListener</code>s subscribed for events
     * indicating rejection of a multi user chat invitation sent by us.
     */
    private final List<ChatRoomInvitationRejectionListener> invitationRejectionListeners = new Vector<>();

    /**
     * Listeners that will be notified of changes in our status in the room such as us being kicked,
     * banned, or granted admin permissions.
     */
    private final List<LocalUserChatRoomPresenceListener> presenceListeners = new Vector<>();

    /*
     * Implements OperationSetMultiUserChat#addInvitationListener( ChatRoomInvitationListener).
     */
    public void addInvitationListener(ChatRoomInvitationListener listener)
    {
        synchronized (invitationListeners) {
            if (!invitationListeners.contains(listener))
                invitationListeners.add(listener);
        }
    }

    /*
     * ImplementsOperationSetMultiUserChat
     * #addInvitationRejectionListener(ChatRoomInvitationRejectionListener).
     */
    public void addInvitationRejectionListener(ChatRoomInvitationRejectionListener listener)
    {
        synchronized (invitationRejectionListeners) {
            if (!invitationRejectionListeners.contains(listener))
                invitationRejectionListeners.add(listener);
        }
    }

    /*
     * Implements OperationSetMultiUserChat#addPresenceListener(LocalUserChatRoomPresenceListener).
     */
    public void addPresenceListener(LocalUserChatRoomPresenceListener listener)
    {
        synchronized (presenceListeners) {
            if (!presenceListeners.contains(listener))
                presenceListeners.add(listener);
        }
    }

    /**
     * Fires a new <code>ChatRoomInvitationReceivedEvent</code> to all currently registered
     * <code>ChatRoomInvitationListener</code>s to notify about the receipt of a specific
     * <code>ChatRoomInvitation</code>.
     *
     * @param invitation the <code>ChatRoomInvitation</code> which has been received
     */
    protected void fireInvitationReceived(ChatRoomInvitation invitation)
    {
        ChatRoomInvitationReceivedEvent evt = new ChatRoomInvitationReceivedEvent(this, invitation,
                new Date(System.currentTimeMillis()));

        ChatRoomInvitationListener[] listeners;
        synchronized (invitationListeners) {
            listeners = invitationListeners.toArray(new ChatRoomInvitationListener[0]);
        }

        for (ChatRoomInvitationListener listener : listeners)
            listener.invitationReceived(evt);
    }

    /**
     * Delivers a <code>ChatRoomInvitationRejectedEvent</code> to all registered
     * <code>ChatRoomInvitationRejectionListener</code>s.
     *
     * @param sourceChatRoom the room that invitation refers to
     * @param invitee the name of the invitee that rejected the invitation
     * @param reason the reason of the rejection
     */
    protected void fireInvitationRejectedEvent(ChatRoom sourceChatRoom, EntityBareJid invitee, String reason)
    {
        ChatRoomInvitationRejectedEvent evt = new ChatRoomInvitationRejectedEvent(this,
                sourceChatRoom, invitee, reason, new Date(System.currentTimeMillis()));

        ChatRoomInvitationRejectionListener[] listeners;
        synchronized (invitationRejectionListeners) {
            listeners = invitationRejectionListeners.toArray(new ChatRoomInvitationRejectionListener[0]);
        }

        for (ChatRoomInvitationRejectionListener listener : listeners)
            listener.invitationRejected(evt);
    }

    /**
     * Delivers a <code>LocalUserChatRoomPresenceChangeEvent</code> to all registered
     * <code>LocalUserChatRoomPresenceListener</code>s.
     *
     * @param chatRoom the <code>ChatRoom</code> which has been joined, left, etc.
     * @param eventType the type of this event; one of LOCAL_USER_JOINED, LOCAL_USER_LEFT, etc.
     * @param reason the reason
     */
    public void fireLocalUserPresenceEvent(ChatRoom chatRoom, String eventType, String reason)
    {
        this.fireLocalUserPresenceEvent(chatRoom, eventType, reason, null);
    }

    /**
     * Delivers a <code>LocalUserChatRoomPresenceChangeEvent</code> to all registered
     * <code>LocalUserChatRoomPresenceListener</code>s.
     *
     * @param chatRoom the <code>ChatRoom</code> which has been joined, left, etc.
     * @param eventType the type of this event; one of LOCAL_USER_JOINED, LOCAL_USER_LEFT, etc.
     * @param reason the reason
     * @param alternateAddress address of the new room, if old is destroyed.
     */
    public void fireLocalUserPresenceEvent(ChatRoom chatRoom, String eventType, String reason, String alternateAddress)
    {
        LocalUserChatRoomPresenceChangeEvent evt = new LocalUserChatRoomPresenceChangeEvent(this,
                chatRoom, eventType, reason, alternateAddress);

        LocalUserChatRoomPresenceListener[] listeners;
        synchronized (presenceListeners) {
            listeners = presenceListeners.toArray(new LocalUserChatRoomPresenceListener[0]);
        }

        for (LocalUserChatRoomPresenceListener listener : listeners)
            listener.localUserPresenceChanged(evt);
    }

    /*
     * Implements OperationSetMultiUserChat#removeInvitationListener( ChatRoomInvitationListener).
     */
    public void removeInvitationListener(ChatRoomInvitationListener listener)
    {
        synchronized (invitationListeners) {
            invitationListeners.remove(listener);
        }
    }

    /*
     * Implements OperationSetMultiUserChat#removeInvitationRejectionListener(ChatRoomInvitationRejectionListener).
     */
    public void removeInvitationRejectionListener(ChatRoomInvitationRejectionListener listener)
    {
        synchronized (invitationRejectionListeners) {
            invitationRejectionListeners.remove(listener);
        }
    }

    /*
     * Implements OperationSetMultiUserChat#removePresenceListener(LocalUserChatRoomPresenceListener).
     */
    public void removePresenceListener(LocalUserChatRoomPresenceListener listener)
    {
        synchronized (presenceListeners) {
            presenceListeners.remove(listener);
        }
    }
}
