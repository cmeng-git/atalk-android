/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import net.java.sip.communicator.service.protocol.*;

import org.jivesoftware.smack.roster.RosterGroup;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;

import java.util.*;

/**
 * A dummy ContactGroup implementation representing the ContactList root for Jabber contact lists.
 *
 * @author Damian Minkov
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
public class RootContactGroupJabberImpl extends AbstractContactGroupJabberImpl
{
    /**
     * Maps all Jid in our roster to the actual contacts so that we could easily search the set of
     * existing contacts. Note that we only store lower case strings in the left column because JIDs
     * in XMPP are not case-sensitive.
     */
    private final Map<Jid, Contact> contacts = new Hashtable<>();

    /**
     * Root group is always assumed resolved to avoid accidentally removal.
     */
    private final boolean isResolved = true;

    /**
     * A list of all the groups in the root tree.
     * subGroups also include itself.
     */
    private final List<ContactGroup> subGroups = new LinkedList<>();

    /**
     * The provider.
     */
    private final ProtocolProviderServiceJabberImpl protocolProvider;

    /**
     * Creates a ContactGroup instance; and include itself into the subGroups list.
     */
    RootContactGroupJabberImpl(ProtocolProviderServiceJabberImpl protocolProvider)
    {
        this.protocolProvider = protocolProvider;

        // Do not add itself to subGroups => problem. Hanlder in code
        // subGroups.add(this);
    }

    /**
     * Returns the number, which is always 0, of <tt>Contact</tt> members of this <tt>ContactGroup</tt>
     *
     * @return an int indicating the number of <tt>Contact</tt>s, members of this <tt>ContactGroup</tt>.
     */
    public int countContacts()
    {
        return contacts.size();
    }

    /**
     * Returns null as this is the root contact group.
     *
     * @return null as this is the root contact group.
     */
    public ContactGroup getParentContactGroup()
    {
        return null;
    }

    /**
     * Adds the specified contact to the end of this group.
     *
     * @param contact the new contact to add to this group
     */
    public void addContact(ContactJabberImpl contact)
    {
        contacts.put(contact.getJid(), contact);
    }

    /**
     * Removes the specified contact from this contact group
     *
     * @param contact the contact to remove.
     */
    public void removeContact(ContactJabberImpl contact)
    {
        contacts.remove(contact.getJid());
    }

    /**
     * Returns an Iterator over all contacts, member of this <tt>ContactGroup</tt>.
     *
     * @return a java.util.Iterator over all contacts inside this <tt>ContactGroup</tt>
     */
    public Iterator<Contact> contacts()
    {
        return contacts.values().iterator();
    }

    /**
     * Returns the <tt>Contact</tt> with the specified address or identifier.
     *
     * @param id the address or identifier of the <tt>Contact</tt> we are looking for.
     * @return the <tt>Contact</tt> with the specified id or address.
     */
    public Contact getContact(String id)
    {
        try {
            return findContact(JidCreate.from(id));
        } catch (XmppStringprepException | IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Returns the contact encapsulating with the specified name or null if no such contact was found.
     *
     * @param id the id for the contact we're looking for.
     * @return the <tt>ContactJabberImpl</tt> corresponding to the specified jid or null if no such contact existed.
     */
    public ContactJabberImpl findContact(Jid id)
    {
        return (id == null) ? null : (ContactJabberImpl) contacts.get(id.asBareJid());
    }

    /**
     * Returns the name of this group which is always <tt>ROOT_PROTO_GROUP_UID</tt>.
     *
     * @return a String containing the name of this group.
     */
    public String getGroupName()
    {
        return ROOT_PROTO_GROUP_UID;
    }

    /**
     * The ContactListRoot is the only group that can contain subgroups.
     *
     * @return true (always)
     */
    public boolean canContainSubgroups()
    {
        return true;
    }

    /**
     * Returns the subgroup with the specified index.
     *
     * @param index the index of the <tt>ContactGroup</tt> to retrieve.
     * @return the <tt>ContactGroup</tt> with the specified index.
     */
    public ContactGroup getGroup(int index)
    {
        return subGroups.get(index);
    }

    /**
     * Returns the subgroup with the specified name.
     *
     * @param groupName the name of the <tt>ContactGroup</tt> to retrieve.
     * @return the <tt>ContactGroup</tt> with the specified index.
     */
    public ContactGroup getGroup(String groupName)
    {
        Iterator<ContactGroup> subgroups = subgroups();
        while (subgroups.hasNext()) {
            ContactGroup grp = subgroups.next();

            if (grp.getGroupName().equals(groupName))
                return grp;
        }
        return null;
    }

    /**
     * Returns an iterator over the sub groups that this <tt>ContactGroup</tt> contains.
     *
     * @return a java.util.Iterator over the <tt>ContactGroup</tt> children of this group (i.e. subgroups).
     */
    public Iterator<ContactGroup> subgroups()
    {
        return new ArrayList<>(subGroups).iterator();
    }

    /**
     * Returns the number of subgroups contained by this <tt>RootContactGroupImpl</tt>.
     *
     * @return an int indicating the number of subgroups that this ContactGroup contains.
     */
    public int countSubgroups()
    {
        return subGroups.size();
    }

    /**
     * Adds the specified group to the end of the list of sub groups.
     * Include the rootGroup, so change to pass-in para to ContactGroup
     *
     * @param group the group to add.
     */
    void addSubGroup(ContactGroup group)
    {
        subGroups.add(group);
    }

    /**
     * Removes the sub group with the specified index.
     *
     * @param index the index of the group to remove
     */
    void removeSubGroup(int index)
    {
        subGroups.remove(index);
    }

    /**
     * Removes the specified from the list of sub groups
     *
     * @param group the group to remove.
     */
    void removeSubGroup(ContactGroupJabberImpl group)
    {
        removeSubGroup(subGroups.indexOf(group));
    }

    /**
     * Returns the protocol provider that this group belongs to.
     *
     * @return a reference to the ProtocolProviderService instance that this ContactGroup belongs to.
     */
    public ProtocolProviderService getProtocolProvider()
    {
        return protocolProvider;
    }

    /**
     * Returns a string representation of the root contact group that contains all subGroups and subContacts of this group.
     * Ensure group.toString() does not end in endless loop under all circumstances
     *
     * @return a string representation of this root contact group.
     */
    @Override
    public String toString()
    {
        StringBuilder buff = new StringBuilder(getGroupName());
        buff.append(".subGroups=").append(countSubgroups()).append(":\n");

        Iterator<ContactGroup> subGroups = subgroups();
        while (subGroups.hasNext()) {
            ContactGroup group = subGroups.next();
            buff.append(group.toString());
            if (subGroups.hasNext())
                buff.append("\n");
        }

        buff.append(".rootContacts=").append(countContacts()).append(":\n");

        Iterator<Contact> contactsIter = contacts();
        while (contactsIter.hasNext()) {
            buff.append(contactsIter.next());
            if (contactsIter.hasNext())
                buff.append("\n");
        }

        return buff.toString();
    }

    /**
     * Determines whether or not this contact group is being stored by the server. Non persistent
     * contact groups exist for the sole purpose of containing non persistent contacts.
     *
     * @return true if the contact group is persistent and false otherwise.
     */
    public boolean isPersistent()
    {
        return true;
    }

    /**
     * Returns null as no persistent data is required and the group name is sufficient for restoring the contact.
     *
     * @return null as no such data is needed.
     */
    public String getPersistentData()
    {
        return null;
    }

    /**
     * Determines whether or not this group has been resolved against the server. Unresolved groups
     * are used when initially loading a contact list that has been stored in a local file until
     * the presence operation set has managed to retrieve all the contact list from the server
     * and has properly mapped groups to their on-line buddies.
     *
     * The root group must always be resolved to avoid any un-intention removal.
     *
     * @return true if the group has been resolved (mapped against a buddy) and false otherwise.
     */
    public boolean isResolved()
    {
        return isResolved;
    }

    /**
     * Returns a <tt>String</tt> that uniquely represents the group. In this we use the name of the
     * group as an identifier. This may cause problems though, in case the name is changed by some
     * other application between consecutive runs of the sip-communicator.
     *
     * @return a String representing this group in a unique and persistent way.
     */
    public String getUID()
    {
        return getGroupName();
    }
}
