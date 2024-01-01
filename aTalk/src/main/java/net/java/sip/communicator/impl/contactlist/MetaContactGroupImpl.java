/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions
 * and limitations under the License.
 */
package net.java.sip.communicator.impl.contactlist;

import androidx.annotation.NonNull;

import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.service.contactlist.MetaContactGroup;
import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.ContactGroup;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;

import org.atalk.impl.timberlog.TimberLog;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;

import timber.log.Timber;

/**
 * A straightforward implementation of the meta contact group. The group implements a simple
 * algorithm of sorting its children according to their status.
 *
 * @author Emil Ivov
 * @author Lubomir Marinov
 * @author Eng Chong Meng
 */
public class MetaContactGroupImpl implements MetaContactGroup
{
    /**
     * All the subgroups that this group contains.
     */
    private Set<MetaContactGroupImpl> subgroups = new TreeSet<>();

    /**
     * A list containing all child contacts.
     */
    private final Set<MetaContactImpl> childContacts = new TreeSet<>();

    /**
     * A list of the contact groups encapsulated by this MetaContactGroup
     */
    private Vector<ContactGroup> mProtoGroups = new Vector<>();

    /**
     * An id uniquely identifying the meta contact group in this contact list.
     */
    private String groupUID;

    /**
     * The name of the group (fixed for root groups since it won't show).
     */
    private String groupName;

    /**
     * We use this copy for returning iterators and searching over the list in order to avoid
     * creating it upon each query. The copy is updated upon each modification
     */
    private List<MetaContact> childContactsOrderedCopy = new LinkedList<>();

    /**
     * We use this copy for returning iterators and searching over the list in order to avoid
     * creating it upon each query. The copy is updated upon each modification
     */
    private List<MetaContactGroup> subgroupsOrderedCopy = new LinkedList<>();

    /**
     * The meta contact group that is currently containing us.
     */
    private MetaContactGroupImpl parentMetaContactGroup = null;

    /**
     * The <code>MetaContactListService</code> implementation which manages this
     * <code>MetaContactGroup</code> and its associated hierarchy.
     */
    private final MetaContactListServiceImpl mclServiceImpl;

    /**
     * The user-specific key-value associations stored in this instance.
     *
     * Like the Widget implementation of Eclipse SWT, the storage type takes into account that
     * there are likely to be many {@code MetaContactGroupImpl} instances and
     * {@code Map}s are thus likely to impose increased memory use. While an array may
     * very well perform worse than a {@code Map} with respect to search, the mechanism of
     * user-defined key-value associations explicitly states that it is not guaranteed to be
     * optimized for any particular use and only covers the most basic cases and
     * performance-savvy code will likely implement a more optimized solution anyway.
     * </p>
     */
    private Object[] data;

    /**
     * Creates an instance of the root meta contact group.
     *
     * @param mclServiceImpl the <code>MetaContactListService</code> implementation which is to use the new
     * <code>MetaContactGroup</code> instance as its root
     * @param groupName the name of the group to create
     */
    MetaContactGroupImpl(MetaContactListServiceImpl mclServiceImpl, String groupName)
    {
        this(mclServiceImpl, groupName, null);
    }

    /**
     * Creates an instance of the root meta contact group assigning it the specified meta contact
     * uid. This constructor MUST NOT be used for any other purposes except restoring contacts
     * extracted from the database
     *
     * @param mclServiceImpl the implementation of the <code>MetaContactListService</code>, to which this group belongs
     * @param groupName the name of the group to create
     * @param mcgUID a metaContact UID that has been stored earlier or null when a new UID needs to be
     * created.
     */
    MetaContactGroupImpl(MetaContactListServiceImpl mclServiceImpl, String groupName, String mcgUID)
    {
        this.mclServiceImpl = mclServiceImpl;
        this.groupName = groupName;
        this.groupUID = (mcgUID == null)
                ? System.currentTimeMillis() + String.valueOf(hashCode()) : mcgUID;
    }

    /**
     * Returns a String identifier (the actual contents is left to implementations) that uniquely
     * represents this <code>MetaContactGroup</code> in the containing <code>MetaContactList</code>
     *
     * @return a String uniquely identifying this metaContactGroup.
     */
    public String getMetaUID()
    {
        return groupUID;
    }

    /**
     * Returns the MetaContactGroup currently containing this group or null/aTalk if this is the
     * root group
     *
     * @return a reference to the MetaContactGroup currently containing this meta contact group or
     * null if this is the root group.
     */
    public MetaContactGroup getParentMetaContactGroup()
    {
        return parentMetaContactGroup;
    }

    /**
     * Determines whether or not this group can contain subgroups.
     *
     * @return always <code>true</code> since this is the root contact group and in our impl it can
     * only contain groups.
     */
    public boolean canContainSubgroups()
    {
        return false;
    }

    /**
     * Returns the number of <code>MetaContact</code>s that this group contains.
     *
     *
     * @return the number of <code>MetaContact</code>s that this group contains.
     */
    public int countChildContacts()
    {
        return childContacts.size();
    }

    /**
     * Returns the number of online <code>MetaContact</code>s that this group contains.
     *
     *
     * @return the number of online <code>MetaContact</code>s that this group contains.
     */
    public int countOnlineChildContacts()
    {
        int onlineContactsNumber = 0;
        try {
            Iterator<MetaContact> itr = getChildContacts();
            while (itr.hasNext()) {
                Contact contact = itr.next().getDefaultContact();

                if (contact == null)
                    continue;

                if (contact.getPresenceStatus().isOnline()) {
                    onlineContactsNumber++;
                }
            }
        } catch (Exception e) {
            Timber.d(e, "Failed to count online contacts.");
        }
        return onlineContactsNumber;
    }

    /**
     * Returns the number of <code>ContactGroups</code>s that this group encapsulates
     *
     *
     * @return an int indicating the number of ContactGroups-s that this group encapsulates.
     */
    public int countContactGroups()
    {
        return mProtoGroups.size();
    }

    /**
     * Returns the number of subgroups that this <code>MetaContactGroup</code> contains.
     *
     * @return an int indicating the number of subgroups in this group.
     */
    public int countSubgroups()
    {
        return subgroups.size();
    }

    /**
     * Returns a <code>java.util.Iterator</code> over the <code>MetaContact</code>s contained in this
     * <code>MetaContactGroup</code>.
     *
     * In order to prevent problems with concurrency, the <code>Iterator</code> returned by this
     * method is not over the actual list of groups but over a copy of that list.
     *
     *
     * @return a <code>java.util.Iterator</code> over an empty contacts list.
     */
    public Iterator<MetaContact> getChildContacts()
    {
        return childContactsOrderedCopy.iterator();
    }

    /**
     * Returns the contact with the specified identifier
     *
     * @param metaContactID a String identifier obtained through the <code>MetaContact.getMetaUID()</code> method.
     *
     * @return the <code>MetaContact</code> with the specified identifier.
     */
    public MetaContact getMetaContact(String metaContactID)
    {
        Iterator<MetaContact> contactsIter = getChildContacts();
        while (contactsIter.hasNext()) {
            MetaContact contact = contactsIter.next();

            if (contact.getMetaUID().equals(metaContactID))
                return contact;
        }
        return null;
    }

    /**
     * Returns the index of metaContact according to other contacts in this or -1 if metaContact
     * does not belong to this group. The returned index is only valid until another contact has
     * been added / removed or a contact has changed its status and hence - position. In such
     * a case a REORDERED event is fired.
     *
     * @param metaContact the <code>MetaContact</code> whose index we're looking for.
     * @return the index of <code>metaContact</code> in the list of child contacts or -1 if
     * <code>metaContact</code>.
     */
    public int indexOf(MetaContact metaContact)
    {
        int i = 0;
        Iterator<MetaContact> childrenIter = getChildContacts();

        while (childrenIter.hasNext()) {
            MetaContact current = childrenIter.next();

            if (current == metaContact) {
                return i;
            }
            i++;
        }
        // if we got here then metaContact is not in this list
        return -1;
    }

    /**
     * Returns the index of metaContactGroup in relation to other subgroups in this group or -1 if
     * metaContact does not belong to this group. The returned index is only valid until another
     * group has been added / removed or renamed In such a case a REORDERED event is fired.
     *
     * @param metaContactGroup the <code>MetaContactGroup</code> whose index we're looking for.
     * @return the index of <code>metaContactGroup</code> in the list of child contacts or -1 if
     * <code>metaContact</code>.
     */
    public int indexOf(MetaContactGroup metaContactGroup)
    {
        int i = 0;
        Iterator<MetaContactGroup> childrenIter = getSubgroups();

        while (childrenIter.hasNext()) {
            MetaContactGroup current = childrenIter.next();

            if (current == metaContactGroup) {
                return i;
            }
            i++;
        }
        // if we got here then metaContactGroup is not in this list
        return -1;
    }

    /**
     * Returns the meta contact encapsulating a contact belonging to the specified
     * <code>provider</code> with the specified identifier.
     *
     * @param provider the ProtocolProviderService that the specified <code>contactID</code> is pertaining to.
     * @param contactID a String identifier of the protocol specific contact whose container meta contact
     * we're looking for.
     * @return the <code>MetaContact</code> with the specified identifier.
     */
    public MetaContact getMetaContact(ProtocolProviderService provider, String contactID)
    {
        Iterator<MetaContact> metaContactsIter = getChildContacts();
        while (metaContactsIter.hasNext()) {
            MetaContact metaContact = metaContactsIter.next();

            if (metaContact.getContact(contactID, provider) != null)
                return metaContact;
        }
        return null;

    }

    /**
     * Returns a meta contact, a child of this group or its subgroups, that has the specified
     * metaUID. If no such meta contact exists, the method would return null.
     *
     * @param metaUID the Meta UID of the contact we're looking for.
     * @return the MetaContact with the specified UID or null if no such contact exists.
     */
    public MetaContact findMetaContactByMetaUID(String metaUID)
    {
        // first go through the contacts that are direct children of this method.
        Iterator<MetaContact> contactsIter = getChildContacts();

        while (contactsIter.hasNext()) {
            MetaContact metaContact = contactsIter.next();

            if (metaContact.getMetaUID().equals(metaUID))
                return metaContact;
        }

        // if we didn't find it here, let's try in the subgroups
        Iterator<MetaContactGroup> groupsIter = getSubgroups();

        while (groupsIter.hasNext()) {
            MetaContactGroupImpl mGroup = (MetaContactGroupImpl) groupsIter.next();

            MetaContact metaContact = mGroup.findMetaContactByMetaUID(metaUID);

            if (metaContact != null)
                return metaContact;
        }
        return null;
    }

    /**
     * Returns a meta contact group this group or some of its subgroups, that has the specified
     * metaUID. If no such meta contact group exists, the method would return null.
     *
     * @param metaUID the Meta UID of the contact group we're looking for.
     * @return the MetaContactGroup with the specified UID or null if no such contact exists.
     */
    public MetaContactGroup findMetaContactGroupByMetaUID(String metaUID)
    {
        if (metaUID.equals(groupUID))
            return this;

        // if we didn't find it here, let's try in the subgroups
        Iterator<MetaContactGroup> groupsIter = getSubgroups();

        while (groupsIter.hasNext()) {
            MetaContactGroupImpl mGroup = (MetaContactGroupImpl) groupsIter.next();

            if (metaUID.equals(mGroup.getMetaUID()))
                return mGroup;
            else
                mGroup.findMetaContactByMetaUID(metaUID);
        }
        return null;
    }

    /**
     * Returns an iterator over all the protocol specific groups that this contact group
     * represents.
     *
     * In order to prevent problems with concurrency, the <code>Iterator</code> returned by this
     * method is not over the actual list of groups but over a copy of that list.
     *
     *
     * @return an Iterator over the protocol specific groups that this group represents.
     */
    public Iterator<ContactGroup> getContactGroups()
    {
        return new LinkedList<>(mProtoGroups).iterator();
    }

    /**
     * Returns a contact group encapsulated by this meta contact group, having the specified
     * groupName and coming from the indicated ownerProvider.
     *
     * @param grpName the name of the contact group who we're looking for.
     * @param ownerProvider a reference to the ProtocolProviderService that the contact we're looking for belongs
     * to.
     * @return a reference to a <code>ContactGroup</code>, encapsulated by this MetaContactGroup,
     * carrying the specified name and originating from the specified ownerProvider or null if no
     * such contact group was found.
     */
    public ContactGroup getContactGroup(String grpName, ProtocolProviderService ownerProvider)
    {
        Iterator<ContactGroup> encapsulatedGroups = getContactGroups();

        while (encapsulatedGroups.hasNext()) {
            ContactGroup group = encapsulatedGroups.next();

            if (group.getGroupName().equals(grpName)
                    && group.getProtocolProvider() == ownerProvider) {
                return group;
            }
        }
        return null;
    }

    /**
     * Returns all protocol specific ContactGroups, encapsulated by this MetaContactGroup and
     * coming from the indicated ProtocolProviderService. If none of the contactGroups encapsulated by
     * this MetaContact is originating from the specified provider then an empty iterator is returned.
     *
     * @param provider a reference to the <code>ProtocolProviderService</code> whose ContactGroups we'd like to get.
     * @return an <code>Iterator</code> over all contacts encapsulated in this <code>MetaContact</code>
     * and originating from the specified provider.
     */
    public Iterator<ContactGroup> getContactGroupsForProvider(ProtocolProviderService provider)
    {
        Iterator<ContactGroup> encapsulatedGroups = getContactGroups();
        LinkedList<ContactGroup> protoGroups = new LinkedList<>();

        while (encapsulatedGroups.hasNext()) {
            ContactGroup group = encapsulatedGroups.next();

            if (group.getProtocolProvider() == provider) {
                protoGroups.add(group);
            }
        }
        return protoGroups.iterator();
    }

    /**
     * Returns all protocol specific ContactGroups, encapsulated by this MetaContactGroup and
     * coming from the provider matching the <code>accountID</code> param. If none of the contacts
     * encapsulated by this MetaContact is originating from the specified account then an
     * empty iterator is returned.
     *
     * Note to implementers: In order to prevent problems with concurrency, the <code>Iterator</code>
     * returned by this method should not be over the actual list of groups but rather over a
     * copy of that list.
     *
     * @param accountID the id of the account whose contact groups we'd like to retrieve.
     * @return an <code>Iterator</code> over all contacts encapsulated in this <code>MetaContact</code>
     * and originating from the provider with the specified account id.
     */
    public Iterator<ContactGroup> getContactGroupsForAccountID(String accountID)
    {
        Iterator<ContactGroup> encapsulatedGroups = getContactGroups();
        LinkedList<ContactGroup> protoGroups = new LinkedList<>();

        while (encapsulatedGroups.hasNext()) {
            ContactGroup group = encapsulatedGroups.next();

            if (group.getProtocolProvider().getAccountID().getAccountUniqueID()
                    .equals(accountID)) {
                protoGroups.add(group);
            }
        }
        return protoGroups.iterator();
    }

    /**
     * Returns a meta contact, a child of this group or its subgroups, that has the specified
     * protocol specific contact. If no such meta contact exists, the method would return null.
     *
     * @param protoContact the protocol specific contact whom meta contact we're looking for.
     * @return the MetaContactImpl that contains the specified protocol specific contact.
     */
    public MetaContact findMetaContactByContact(Contact protoContact)
    {
        // first go through the contacts that are direct children of this method.
        Iterator<MetaContact> contactsIter = getChildContacts();

        while (contactsIter.hasNext()) {
            MetaContact mContact = contactsIter.next();

            Contact storedProtoContact
                    = mContact.getContact(protoContact.getAddress(), protoContact.getProtocolProvider());

            if (storedProtoContact != null)
                return mContact;
        }

        // if we didn't find it here, let's try in the subgroups
        Iterator<MetaContactGroup> groupsIter = getSubgroups();

        while (groupsIter.hasNext()) {
            MetaContactGroupImpl mGroup = (MetaContactGroupImpl) groupsIter.next();

            MetaContact mContact = mGroup.findMetaContactByContact(protoContact);

            if (mContact != null)
                return mContact;
        }
        return null;
    }

    /**
     * Returns a meta contact, a child of this group or its subgroups, with address equald to
     * <code>contactAddress</code> and a source protocol provider with the matching
     * <code>accountID</code>. If no such meta contact exists, the method would return null.
     *
     * @param contactAddress the address of the protocol specific contact whose meta contact we're looking for.
     * @param accountID the ID of the account that the contact we are looking for must belong to.
     * @return the MetaContactImpl that contains the specified protocol specific contact.
     */
    public MetaContact findMetaContactByContact(String contactAddress, String accountID)
    {
        // first go through the contacts that are direct children of this method.
        Iterator<MetaContact> contactsIter = getChildContacts();

        while (contactsIter.hasNext()) {
            MetaContactImpl mContact = (MetaContactImpl) contactsIter.next();
            Contact storedProtoContact = mContact.getContact(contactAddress, accountID);
            if (storedProtoContact != null)
                return mContact;
        }

        // if we didn't find it here, let's try in the subgroups
        Iterator<MetaContactGroup> groupsIter = getSubgroups();

        while (groupsIter.hasNext()) {
            MetaContactGroupImpl mGroup = (MetaContactGroupImpl) groupsIter.next();
            MetaContact mContact = mGroup.findMetaContactByContact(contactAddress, accountID);
            if (mContact != null)
                return mContact;
        }
        return null;
    }

    /**
     * Returns a meta contact group, encapsulated by this group or its subgroups, that has the
     * specified protocol specific contact. If no such meta contact group exists, the method
     * would return null.
     *
     * @param protoContactGroup the protocol specific contact group whose meta contact group we're looking for.
     * @return the MetaContactImpl that contains the specified protocol specific contact.
     */
    public MetaContactGroupImpl findMetaContactGroupByContactGroup(ContactGroup protoContactGroup)
    {
        // first check here, in this meta group
        if (mProtoGroups.contains(protoContactGroup))
            return this;

        // if we didn't find it here, let's try in the subgroups
        Iterator<MetaContactGroup> groupsIter = getSubgroups();

        while (groupsIter.hasNext()) {
            MetaContactGroupImpl mGroup = (MetaContactGroupImpl) groupsIter.next();
            MetaContactGroupImpl foundMetaContactGroup = mGroup.findMetaContactGroupByContactGroup(protoContactGroup);
            if (foundMetaContactGroup != null)
                return foundMetaContactGroup;
        }
        return null;
    }

    /**
     * Returns the meta contact on the specified index.
     *
     * @param index the index of the meta contact to return.
     * @return the MetaContact with the specified index,
     *
     * @throws IndexOutOfBoundsException in case <code>index</code> is not a valid index for this group.
     */
    public MetaContact getMetaContact(int index)
            throws IndexOutOfBoundsException
    {
        return this.childContactsOrderedCopy.get(index);
    }

    /**
     * Adds the specified <code>metaContact</code> to ths local list of child contacts.
     *
     * @param metaContact the <code>MetaContact</code> to add in the local vector.
     */
    void addMetaContact(MetaContactImpl metaContact)
    {
        // set this group as a callback in the meta contact
        metaContact.setParentGroup(this);
        lightAddMetaContact(metaContact);
    }

    /**
     * Adds the <code>metaContact</code> to the local list of child contacts without setting its
     * parent contact and without any synchronization. This method is meant for use _PRIMARILY_
     * by the <code>MetaContact</code> itself upon change in its encapsulated protocol specific
     * contacts.
     *
     * @param metaContact the <code>MetaContact</code> to add in the local vector.
     * @return the index at which the contact was added.
     */
    int lightAddMetaContact(MetaContactImpl metaContact)
    {
        synchronized (childContacts) {
            this.childContacts.add(metaContact);
            // no need to synch it's not a disaster if s.o. else reads the old copy.
            childContactsOrderedCopy = new LinkedList<>(childContacts);
            return childContactsOrderedCopy.indexOf(metaContact);
        }
    }

    /**
     * Removes the <code>metaContact</code> from the local list of child contacts without unsetting
     * synchronization. This method is meant for use _PRIMARILY_ by the <code>MetaContact</code>
     * itself upon change in its encapsulated protocol specific contacts. The method would also
     * regenerate the ordered copy used for generating iterators and performing search operations
     * over the group.
     *
     * @param metaContact the <code>MetaContact</code> to remove from the local vector.
     */
    void lightRemoveMetaContact(MetaContactImpl metaContact)
    {
        synchronized (childContacts) {
            this.childContacts.remove(metaContact);
            // no need to sync it's not a disaster if s.o. else reads the old copy.
            childContactsOrderedCopy = new LinkedList<>(childContacts);
        }
    }

    /**
     * Removes the specified <code>metaContact</code> from the local list of contacts.
     *
     * @param metaContact the <code>MetaContact</code>
     */
    void removeMetaContact(MetaContactImpl metaContact)
    {
        metaContact.unsetParentGroup(this);
        lightRemoveMetaContact(metaContact);
    }

    /**
     * Returns the <code>MetaContactGroup</code> with the specified index.
     *
     *
     * @param index the index of the group to return.
     * @return the <code>MetaContactGroup</code> with the specified index.
     *
     * @throws IndexOutOfBoundsException if <code>index</code> is not a valid index.
     */
    public MetaContactGroup getMetaContactSubgroup(int index)
            throws IndexOutOfBoundsException
    {
        return subgroupsOrderedCopy.get(index);
    }

    /**
     * Returns the <code>MetaContactGroup</code> with the specified name.
     *
     * @param grpName the name of the group to return.
     * @return the <code>MetaContactGroup</code> with the specified name or null if no such group
     * exists.
     */
    public MetaContactGroup getMetaContactSubgroup(String grpName)
    {
        Iterator<MetaContactGroup> groupsIter = getSubgroups();
        while (groupsIter.hasNext()) {
            MetaContactGroup mcGroup = groupsIter.next();
            if (mcGroup.getGroupName().equals(grpName))
                return mcGroup;
        }
        return null;
    }

    /**
     * Returns the <code>MetaContactGroup</code> with the specified groupUID.
     *
     * @param grpUID the uid of the group to return.
     * @return the <code>MetaContactGroup</code> with the specified uid or null if no such group
     * exists.
     */
    public MetaContactGroup getMetaContactSubgroupByUID(String grpUID)
    {
        Iterator<MetaContactGroup> groupsIter = getSubgroups();

        while (groupsIter.hasNext()) {
            MetaContactGroup mcGroup = groupsIter.next();
            if (mcGroup.getMetaUID().equals(grpUID))
                return mcGroup;
        }
        return null;
    }

    /**
     * Returns true if and only if <code>contact</code> is a direct child of this group.
     *
     * @param contact the <code>MetaContact</code> whose relation to this group we'd like to determine.
     * @return <code>true</code> if <code>contact</code> is a direct child of this group and
     * <code>false</code> otherwise.
     */
    public boolean contains(MetaContact contact)
    {
        synchronized (childContacts) {
            return this.childContacts.contains(contact);
        }
    }

    /**
     * Returns true if and only if <code>group</code> is a direct subgroup of this
     * <code>MetaContactGroup</code>.
     *
     * @param group the <code>MetaContactGroup</code> whose relation to this group we'd like to determine.
     * @return <code>true</code> if <code>group</code> is a direct child of this <code>MetaContactGroup</code>
     * and <code>false</code> otherwise.
     */
    public boolean contains(MetaContactGroup group)
    {
        return this.subgroups.contains(group);
    }

    /**
     * Returns an <code>java.util.Iterator</code> over the sub groups that this
     * <code>MetaContactGroup</code> contains.
     *
     * In order to prevent problems with concurrency, the <code>Iterator</code> returned by this
     * method is not over the actual list of groups but over a copy of that list.
     *
     *
     * @return a <code>java.util.Iterator</code> containing all subgroups.
     */
    public Iterator<MetaContactGroup> getSubgroups()
    {
        return subgroupsOrderedCopy.iterator();
    }

    /**
     * Returns the name of this group.
     *
     * @return a String containing the name of this group.
     */
    public String getGroupName()
    {
        return groupName;
    }

    /**
     * Sets the name of this group.
     *
     * @param newGroupName a String containing the new name of this group.
     */
    void setGroupName(String newGroupName)
    {
        this.groupName = newGroupName;
    }

    /**
     * Returns a String representation of this group and the contacts it contains (may turn out to
     * be a relatively long string).
     *
     * @return a String representing this group and its child contacts.
     */
    @NonNull
    @Override
    public String toString()
    {
        StringBuilder buff = new StringBuilder(getGroupName());
        buff.append(".subGroups=" + countSubgroups() + ":\n");

        Iterator<MetaContactGroup> subGroups = getSubgroups();
        while (subGroups.hasNext()) {
            MetaContactGroup group = subGroups.next();
            buff.append(group.getGroupName());
            if (subGroups.hasNext())
                buff.append("\n");
        }

        buff.append("\nProtoGroups=" + countContactGroups() + ":[");
        Iterator<ContactGroup> contactGroups = getContactGroups();
        while (contactGroups.hasNext()) {
            ContactGroup contactGroup = contactGroups.next();
            buff.append(contactGroup.getProtocolProvider());
            buff.append(".");
            buff.append(contactGroup.getGroupName());
            if (contactGroups.hasNext())
                buff.append(", ");
        }
        buff.append("]");

        buff.append("\nRootChildContacts=" + countChildContacts() + ":[");

        Iterator<MetaContact> contacts = getChildContacts();
        while (contacts.hasNext()) {
            MetaContact contact = contacts.next();
            buff.append(contact.toString());
            if (contacts.hasNext())
                buff.append(", ");
        }
        return buff.append("]").toString();
    }

    /**
     * Adds the specified group to the list of protocol specific groups that we're encapsulating
     * in this meta contact group.
     *
     * @param protoGroup the root to add to the groups merged in this meta contact group.
     */
    void addProtoGroup(ContactGroup protoGroup)
    {
        mProtoGroups.add(protoGroup);
    }

    /**
     * Removes the specified group from the list of protocol specific groups that we're
     * encapsulating in this meta contact group.
     *
     * @param protoGroup the group to remove from the groups merged in this meta contact group.
     */
    void removeProtoGroup(ContactGroup protoGroup)
    {
        mProtoGroups.remove(protoGroup);
    }

    /**
     * Adds the specified meta group to the subgroups of this one.
     *
     * @param subgroup the MetaContactGroup to register as a subgroup to this root meta contact group.
     */
    void addSubgroup(MetaContactGroup subgroup)
    {
        Timber.log(TimberLog.FINER, "Adding subgroup %s to %s", subgroup.getGroupName(), getGroupName());
        this.subgroups.add((MetaContactGroupImpl) subgroup);
        ((MetaContactGroupImpl) subgroup).parentMetaContactGroup = this;

        this.subgroupsOrderedCopy = new LinkedList<>(subgroups);
    }

    /**
     * Removes the meta contact group with the specified index.
     *
     * @param index the index of the group to remove.
     * @return the <code>MetaContactGroup</code> that has just been removed.
     */
    MetaContactGroupImpl removeSubgroup(int index)
    {
        MetaContactGroupImpl subgroup = (MetaContactGroupImpl) subgroupsOrderedCopy.get(index);

        if (subgroups.remove(subgroup))
            subgroup.parentMetaContactGroup = null;

        subgroupsOrderedCopy = new LinkedList<>(subgroups);
        return subgroup;
    }

    /**
     * Removes the specified group from the list of groups in this list.
     *
     * @param group the <code>MetaContactGroup</code> to remove.
     * @return true if the group has been successfully removed and false otherwise.
     */
    boolean removeSubgroup(MetaContactGroup group)
    {
        if (subgroups.contains(group)) {
            removeSubgroup(subgroupsOrderedCopy.indexOf(group));
            return true;
        }
        else {
            return false;
        }
    }

    /**
     * Returns the implementation of the <code>MetaContactListService</code>, to which this group belongs.
     *
     * @return the implementation of the <code>MetaContactListService</code>
     */
    final MetaContactListServiceImpl getMclServiceImpl()
    {
        return mclServiceImpl;
    }

    /**
     * Implements {@link MetaContactGroup#getData(Object)}.
     *
     * @return the data value corresponding to the given key
     */
    public Object getData(Object key)
    {
        if (key == null)
            throw new NullPointerException("key");

        int index = dataIndexOf(key);
        return (index == -1) ? null : data[index + 1];
    }

    /**
     * Implements {@link MetaContactGroup#setData(Object, Object)}.
     *
     * @param key the of the data
     * @param value the value of the data
     */
    public void setData(Object key, Object value)
    {
        if (key == null)
            throw new NullPointerException("key");

        int index = dataIndexOf(key);

        if (index == -1) {
            /*
             * If value is null, remove the association with key (or just don't add it).
             */
            if (data == null) {
                if (value != null)
                    data = new Object[]{key, value};
            }
            else if (value == null) {
                int length = data.length - 2;

                if (length > 0) {
                    Object[] newData = new Object[length];

                    System.arraycopy(data, 0, newData, 0, index);
                    System.arraycopy(data, index + 2, newData, index, length - index);
                    data = newData;
                }
                else
                    data = null;
            }
            else {
                int length = data.length;
                Object[] newData = new Object[length + 2];

                System.arraycopy(data, 0, newData, 0, length);
                data = newData;
                data[length++] = key;
                data[length++] = value;
            }
        }
        else
            data[index + 1] = value;
    }

    /**
     * Determines whether or not this meta group contains only groups that are being stored by a
     * server.
     *
     * @return true if the meta group is persistent and false otherwise.
     */
    public boolean isPersistent()
    {
        Iterator<ContactGroup> contactGroupsIter = getContactGroups();

        while (contactGroupsIter.hasNext()) {
            ContactGroup contactGroup = contactGroupsIter.next();
            if (contactGroup.isPersistent())
                return true;
        }

        // this is new and empty group, we can store it as user want this
        return (countContactGroups() == 0);
    }

    /**
     * Determines the index in {@code #data} of a specific key.
     *
     * @param key the key to retrieve the index in {@code #data} of
     * @return the index in {@code #data} of the specified {@code key} if it is
     * contained; <code>-1</code> if {@code key} is not
     * contained in {@code #data}
     */
    private int dataIndexOf(Object key)
    {
        if (data != null)
            for (int index = 0; index < data.length; index += 2)
                if (key.equals(data[index]))
                    return index;
        return -1;
    }

    /**
     * Compares this meta contact group with the specified object for order. Returns a negative
     * integer, zero, or a positive integer as this meta contact group is less than, equal to, or
     * greater than the specified object.
     *
     * The result of this method is calculated the following way:
     *
     * + getGroupName().compareTo(o.getGroupName()) * 10 000 + getMetaUID().compareTo(o.getMetaUID
     * ())<br>
     *
     * Or in other words ordering of meta groups would be first done by display name, and finally
     * (in order to avoid equalities) be the fairly random meta contact group metaUID.
     *
     *
     * @param target the {@code MetaContactGroup} to be compared.
     * @return a negative integer, zero, or a positive integer as this object is less than, equal
     * to, or greater than the specified object.
     */
    public int compareTo(MetaContactGroup target)
    {
        return getGroupName().compareToIgnoreCase(target.getGroupName()) * 10000
                + getMetaUID().compareTo(target.getMetaUID());
    }
}
