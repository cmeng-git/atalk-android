/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import net.java.sip.communicator.service.protocol.OperationSetMultiUserChat;

import org.jxmpp.jid.Jid;
import org.jxmpp.jid.parts.Resourcepart;

/**
 * The Jabber implementation for Volatile Contact
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
public class VolatileContactJabberImpl extends ContactJabberImpl
{
    /**
     * This contact id
     */
    private Jid contactId;

    /**
     * Indicates whether the contact is private messaging contact or not.
     */
    private boolean isPrivateMessagingContact = false;

    /**
     * The display name of the contact. This property is used only for private messaging contacts.
     */
    protected String displayName = null;

    /**
     * Creates an Volatile JabberContactImpl with the specified id
     *
     * @param id String the user id/address
     * @param ssclCallback a reference to the ServerStoredContactListImpl instance that created us.
     */
    VolatileContactJabberImpl(Jid id, ServerStoredContactListJabberImpl ssclCallback)
    {
        this(id, ssclCallback, false, null);
    }

    /**
     * Creates an Volatile JabberContactImpl with the specified id
     *
     * @param id String the user id/address
     * @param ssclCallback a reference to the ServerStoredContactListImpl instance that created us.
     * @param isPrivateMessagingContact if <tt>true</tt> this should be private messaging contact.
     */
    VolatileContactJabberImpl(Jid id, ServerStoredContactListJabberImpl ssclCallback, boolean isPrivateMessagingContact)
    {
        this(id, ssclCallback, isPrivateMessagingContact, null);
    }

    /**
     * Creates an Volatile JabberContactImpl with the specified id
     *
     * @param id String the user id/address (bareJid from subscription)
     * <presence to='swan@atalk.org/atalk' from='leopard@icrypto.com' type='subscribe'/>
     * @param ssclCallback a reference to the ServerStoredContactListImpl instance that created us.
     * @param isPrivateMessagingContact if <tt>true</tt> this should be private messaging contact.
     * @param displayName the display name of the contact
     */
    VolatileContactJabberImpl(Jid id, ServerStoredContactListJabberImpl ssclCallback,
            boolean isPrivateMessagingContact, String displayName)
    {
        super(null, ssclCallback, false, false);
        this.isPrivateMessagingContact = isPrivateMessagingContact;

        if (this.isPrivateMessagingContact) {
            this.displayName = id.getResourceOrEmpty() + " from " + id.asBareJid();
            this.contactId = id;
            setJid(id);
        }
        else {
            this.contactId = id.asBareJid();
            this.displayName = (displayName == null) ? contactId.toString() : displayName;
            Resourcepart resource = id.getResourceOrNull();
            if (resource != null) {
                setJid(id);
            }
        }
    }

    /**
     * Returns the Jabber UserId of this contact
     *
     * @return the Jabber UserId of this contact
     */
    @Override
    public String getAddress()
    {
        return contactId.toString();
    }

    @Override
    public Jid getJid()
    {
        return contactId;
    }

    /**
     * Returns a String that could be used by any user interacting modules for referring to this
     * contact. An alias is not necessarily unique but is often more human readable than an address (or id).
     *
     * @return a String that can be used for referring to this contact when interacting with the user.
     */
    @Override
    public String getDisplayName()
    {
        return displayName;
    }

    /**
     * Returns a string representation of this contact, containing most of its representative details.
     *
     * @return a string representation of this contact.
     */
    @Override
    public String toString()
    {
        return "VolatileJabberContact[ id=" + getAddress() + "]";
    }

    /**
     * Determines whether or not this contact is to be stored at local DB. Non persistent
     * contact exist for the sole purpose of displaying any received messages.
     *
     * @return true if the contact group is persistent and false otherwise.
     */
    @Override
    public boolean isPersistent()
    {
        return true;
    }

    /**
     * Checks if the contact is private messaging contact or not.
     *
     * @return <tt>true</tt> if this is private messaging contact and <tt>false</tt> if it isn't.
     */
    public boolean isPrivateMessagingContact()
    {
        return isPrivateMessagingContact;
    }

    /**
     * Returns the real address of the contact. If the contact is not private messaging contact the
     * result will be the same as <tt>getAddress</tt>'s result.
     *
     * @return the real address of the contact.
     */
    @Override
    public String getPersistableAddress()
    {
        if (!isPrivateMessagingContact)
            return getAddress();

        ChatRoomMemberJabberImpl chatRoomMember = null;
        OperationSetMultiUserChatJabberImpl mucOpSet = (OperationSetMultiUserChatJabberImpl)
                getProtocolProvider().getOperationSet(OperationSetMultiUserChat.class);
        if (mucOpSet != null) {
            chatRoomMember = mucOpSet.getChatRoom(contactId.asBareJid()).findMemberForNickName(contactId.getResourceOrEmpty());
        }
        return ((chatRoomMember == null) ? null : chatRoomMember.getJabberID().asBareJid().toString());
    }
}
