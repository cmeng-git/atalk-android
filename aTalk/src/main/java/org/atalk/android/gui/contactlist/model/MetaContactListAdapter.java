/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.contactlist.model;

import android.text.TextUtils;

import net.java.sip.communicator.service.contactlist.*;
import net.java.sip.communicator.service.contactlist.event.*;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.ContactPresenceStatusChangeEvent;
import net.java.sip.communicator.service.protocol.event.ContactPresenceStatusListener;

import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.chat.ChatSessionManager;
import org.atalk.android.gui.contactlist.ContactListFragment;
import org.atalk.android.gui.contactlist.PresenceFilter;
import org.atalk.android.plugin.timberlog.TimberLog;

import java.util.*;
import java.util.regex.Pattern;

import timber.log.Timber;

/**
 * Contact list model is responsible for caching current contact list obtained from contact
 * sources.(It will apply contact source filters which result in different output model).
 *
 * Note: All contactList view update (from events) must be performed on UI thread using UI thread handler;
 * IllegalStateException: The content of the adapter has changed but ListView did not receive a notification.
 * Make sure the content of your adapter is not modified from a background thread, but only from the UI thread.
 * Make sure your adapter calls notifyDataSetChanged() when its content changes.
 *
 * @author Yana Stamcheva
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class MetaContactListAdapter extends BaseContactListAdapter
        implements MetaContactListListener, ContactPresenceStatusListener, UIGroupRenderer
{
    /**
     * The list of contact list in original groups before filtered
     */
    private final LinkedList<MetaContactGroup> originalGroups;

    /**
     * The list of contact list groups for view display
     */
    private final LinkedList<MetaContactGroup> groups;

    /**
     * The list of original contacts before filtered.
     */
    private final LinkedList<TreeSet<MetaContact>> originalContacts;

    /**
     * The list of contacts for view display.
     */
    private final LinkedList<TreeSet<MetaContact>> contacts;

    /**
     * The <tt>MetaContactListService</tt>, which is the back end of this contact list adapter.
     */
    private MetaContactListService contactListService;

    /**
     * <tt>MetaContactRenderer</tt> instance used by this adapter.
     */
    private MetaContactRenderer contactRenderer;

    /**
     * The presence filter.
     */
    public static final PresenceFilter presenceFilter = new PresenceFilter();

//    /**
//     * The default filter is initially set to the PresenceFilter. But anyone
//     * could change it by calling setDefaultFilter().
//     */
//    private final ContactListFilter defaultFilter = presenceFilter;

//    /**
//     * The current filter.
//     */
//    private final ContactListFilter currentFilter = defaultFilter;

    /**
     * The currently used filter query.
     */
    private String currentFilterQuery;

    /**
     * if mDialogMode, update only contact status changes, not to filter data with isShownOnline
     */
    private boolean mDialogMode = false;

    public MetaContactListAdapter(ContactListFragment contactListFragment, boolean mainContactList)
    {
        super(contactListFragment, mainContactList);

        this.originalContacts = new LinkedList<>();
        this.contacts = new LinkedList<>();
        this.originalGroups = new LinkedList<>();
        this.groups = new LinkedList<>();
    }

    /**
     * Initializes the adapter data.
     */
    public void initModelData()
    {
        contactListService = AndroidGUIActivator.getContactListService();
        if (contactListService != null) {
            addContacts(contactListService.getRoot(), true);
            contactListService.addMetaContactListListener(this);
        }
    }

    /**
     * Releases all resources used by this instance.
     */
    public void dispose()
    {
        if (contactListService != null) {
            contactListService.removeMetaContactListListener(this);
            removeContacts(contactListService.getRoot());
        }
    }

    /**
     * Locally implemented UIGroupRenderer
     * {@inheritDoc}
     */
    @Override
    public UIGroupRenderer getGroupRenderer(int groupPosition)
    {
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UIContactRenderer getContactRenderer(int groupPosition)
    {
        if (contactRenderer == null) {
            contactRenderer = new MetaContactRenderer();
        }
        return contactRenderer;
    }

    /**
     * Returns the group at the given <tt>groupPosition</tt>.
     *
     * @param groupPosition the index of the group
     */
    @Override
    public Object getGroup(int groupPosition)
    {
        if (groupPosition >= 0 && groupPosition < groups.size())
            return groups.get(groupPosition);
        else {
            return null;
        }
    }

    /**
     * Returns the count of all groups contained in this adapter.
     */
    @Override
    public int getGroupCount()
    {
        return groups.size();
    }

    /**
     * Finds group index for given <tt>MetaContactGroup</tt>.
     *
     * @param group the group for which we need the index.
     * @return index of given <tt>MetaContactGroup</tt> or -1 if not found
     */
    public int getGroupIndex(MetaContactGroup group)
    {
        return groups.indexOf(group);
    }

    /**
     * Finds <tt>MetaContact</tt> index in <tt>MetaContactGroup</tt> identified by given <tt>groupIndex</tt>.
     *
     * @param groupIndex index of group we want to search.
     * @param contact the <tt>MetaContact</tt> to find inside the group.
     * @return index of <tt>MetaContact</tt> inside group identified by given group index.
     */
    public int getChildIndex(int groupIndex, MetaContact contact)
    {
        return getChildIndex(getContactList(groupIndex), contact);
    }

    /**
     * Returns the count of children contained in the group given by the <tt>groupPosition</tt>.
     *
     * @param groupPosition the index of the group, which children we would like to count
     */
    @Override
    public int getChildrenCount(int groupPosition)
    {
        TreeSet<MetaContact> contactList = getContactList(groupPosition);
        if (contactList != null)
            return contactList.size();
        else
            return 0;
    }

    /**
     * Get group contact list from filtered contact list.
     *
     * @param groupIndex contact group index.
     * @return group contact list from filtered contact list.
     */
    private TreeSet<MetaContact> getContactList(int groupIndex)
    {
        if (groupIndex >= 0 && groupIndex < contacts.size()) {
            return contacts.get(groupIndex);
        }
        else {
            return null;
        }
    }

    /**
     * Get group contact list from original contact list.
     *
     * @param groupIndex contact group index.
     * @return group contact list from original contact list.
     */
    private TreeSet<MetaContact> getOriginalCList(int groupIndex)
    {
        if (groupIndex >= 0 && groupIndex < originalContacts.size()) {
            return originalContacts.get(groupIndex);
        }
        else {
            return null;
        }
    }

    /**
     * Adds all child contacts for the given <tt>group</tt>. Omit metaGroup of zero child
     *
     * @param group the group, which child contacts to add
     */
    private void addContacts(MetaContactGroup group, boolean filtered)
    {
        if (!filtered || (group.countChildContacts() > 0)) {
            // Add the new metaGroup
            addGroup(group, filtered);

            // Use Iterator to avoid ConcurrentModificationException on addContact()
            Iterator<MetaContact> childContacts = group.getChildContacts();
            while (childContacts.hasNext()) {
                addContact(group, childContacts.next());
            }
        }

        Iterator<MetaContactGroup> subGroups = group.getSubgroups();
        while (subGroups.hasNext()) {
            addContacts(subGroups.next(), filtered);
        }
    }

    /**
     * Adds the given <tt>group</tt> to both the originalGroups and Groups with
     * zero contact if no existing group is found
     *
     * @param metaGroup the <tt>MetaContactGroup</tt> to add
     * @param filtered false will also create group if not found
     */
    private void addGroup(MetaContactGroup metaGroup, boolean filtered)
    {
        if (!originalGroups.contains(metaGroup)) {
            originalGroups.add(metaGroup);
            originalContacts.add(new TreeSet<>());
        }

        // cmeng: invalidateView causes childContact to be null, contact list not properly updated
        // add new group will have no contact; hence cannot remove the check for isMatching
        if ((!filtered || isMatching(metaGroup, currentFilterQuery)) && !groups.contains(metaGroup)) {
            groups.add(metaGroup);
            contacts.add(new TreeSet<>());
        }
    }

    /**
     * Remove an existing <tt>group</tt> from both the originalGroups and Groups
     *
     * @param metaGroup the <tt>MetaContactGroup</tt> to be removed
     */
    private void removeGroup(MetaContactGroup metaGroup)
    {
        int origGroupIndex = originalGroups.indexOf(metaGroup);
        if (origGroupIndex != -1) {
            originalContacts.remove(origGroupIndex);
            originalGroups.remove(metaGroup);
        }

        int groupIndex = groups.indexOf(metaGroup);
        if (groupIndex != -1) {
            contacts.remove(groupIndex);
            groups.remove(metaGroup);
        }
    }

    /**
     * Adds all child contacts for the given <tt>group</tt>.
     *
     * @param metaGroup the parent group of the child contact to add
     * @param metaContact the <tt>MetaContact</tt> to add
     */
    private void addContact(MetaContactGroup metaGroup, MetaContact metaContact)
    {
        addContactStatusListener(metaContact, this);
        int origGroupIndex = originalGroups.indexOf(metaGroup);
        int groupIndex = groups.indexOf(metaGroup);
        boolean isMatchingQuery = isMatching(metaContact, currentFilterQuery);

        // Add new group element and update both the Indexes (may be difference)
        if ((origGroupIndex < 0) || (isMatchingQuery && (groupIndex < 0))) {
            addGroup(metaGroup, true);
            origGroupIndex = originalGroups.indexOf(metaGroup);
            groupIndex = groups.indexOf(metaGroup);
        }

        TreeSet<MetaContact> origContactList = getOriginalCList(origGroupIndex);
        if (origContactList != null && getChildIndex(origContactList, metaContact) < 0) {
            origContactList.add(metaContact);
        }

        // cmeng: new group & contact are added only if isMatchingQuery is true
        if (isMatchingQuery) {
            TreeSet<MetaContact> contactList = getContactList(groupIndex);
            if ((contactList != null) && (getChildIndex(contactList, metaContact) < 0)) {
                // do no allow duplication with multiple accounts registration on same server
                //	if (!contactList.contains(metaContact)) ??? not correct test
                contactList.add(metaContact);
            }
        }
    }

    /**
     * Removes the contacts contained in the given group.
     *
     * @param group the <tt>MetaContactGroup</tt>, which contacts we'd like to remove
     */
    private void removeContacts(MetaContactGroup group)
    {
        removeGroup(group);
        Iterator<MetaContact> childContacts = group.getChildContacts();
        while (childContacts.hasNext()) {
            removeContact(group, childContacts.next());
        }

        Iterator<MetaContactGroup> subGroups = group.getSubgroups();
        while (subGroups.hasNext()) {
            removeContacts(subGroups.next());
        }
    }

    /**
     * Removes the given <tt>metaContact</tt> from both the original and the filtered list of this adapter.
     *
     * @param metaGroup the parent <tt>MetaContactGroup</tt> of the contact to remove
     * @param metaContact the <tt>MetaContact</tt> to remove
     */
    private void removeContact(MetaContactGroup metaGroup, MetaContact metaContact)
    {
        removeContactStatusListener(metaContact, this);

        // Remove the contact from the original list and its group if empty.
        int origGroupIndex = originalGroups.indexOf(metaGroup);
        if (origGroupIndex != -1) {
            TreeSet<MetaContact> origContactList = getOriginalCList(origGroupIndex);
            if (origContactList != null) {
                origContactList.remove(metaContact);

                if (origContactList.isEmpty())
                    removeGroup(metaGroup);
            }
        }

        // Remove the contact from the filtered list and its group if empty
        int groupIndex = groups.indexOf(metaGroup);
        if (groupIndex != -1) {
            TreeSet<MetaContact> contactList = getContactList(groupIndex);
            if (contactList != null) {
                contactList.remove(metaContact);

                if (contactList.isEmpty())
                    removeGroup(metaGroup);
            }
        }
    }

    /**
     * Updates the display name of the given <tt>metaContact</tt>.
     *
     * @param metaContact the <tt>MetaContact</tt>, which display name to update
     */
    private void updateDisplayName(MetaContact metaContact)
    {
        int groupIndex = groups.indexOf(metaContact.getParentMetaContactGroup());
        if (groupIndex >= 0) {
            int contactIndex = getChildIndex(getContactList(groupIndex), metaContact);
            if (contactIndex >= 0)
                updateDisplayName(groupIndex, contactIndex);
        }
    }

    /**
     * Updates the avatar of the given <tt>metaContact</tt>.
     *
     * @param metaContact the <tt>MetaContact</tt>, which avatar to update
     */
    private void updateAvatar(MetaContact metaContact)
    {
        int groupIndex = groups.indexOf(metaContact.getParentMetaContactGroup());
        if (groupIndex >= 0) {
            int contactIndex = getChildIndex(getContactList(groupIndex), metaContact);
            if (contactIndex >= 0)
                updateAvatar(groupIndex, contactIndex, metaContact);
        }
    }

    /**
     * Updates the status of the given <tt>metaContact</tt>.
     *
     * @param metaContact the <tt>MetaContact</tt>, which status to update
     */
    private void updateStatus(MetaContact metaContact)
    {
        int groupIndex = groups.indexOf(metaContact.getParentMetaContactGroup());
        if (groupIndex >= 0) {
            int contactIndex = getChildIndex(getContactList(groupIndex), metaContact);
            if (contactIndex >= 0) {
                updateStatus(groupIndex, contactIndex, metaContact);
            }
        }
    }

    /**
     * Indicates that a <tt>MetaContact</tt> has been added to the list.
     *
     * @param evt the <tt>MetaContactEvent</tt> that notified us
     */
    public void metaContactAdded(MetaContactEvent evt)
    {
        Timber.d("CONTACT ADDED: %s", evt.getSourceMetaContact());
        uiHandler.post(() -> {
            addContact(evt.getParentGroup(), evt.getSourceMetaContact());
            notifyDataSetChanged();
        });
    }

    /**
     * Indicates that a <tt>MetaContact</tt> has been modified.
     *
     * @param evt the <tt>MetaContactEvent</tt> that notified us
     */
    public void metaContactModified(MetaContactModifiedEvent evt)
    {
        Timber.d("META CONTACT MODIFIED: %s", evt.getSourceMetaContact());
        invalidateViews();
    }

    /**
     * Indicates that a <tt>MetaContact</tt> has been removed from the list.
     *
     * @param evt the <tt>MetaContactEvent</tt> that notified us
     */
    public void metaContactRemoved(MetaContactEvent evt)
    {
        Timber.d("CONTACT REMOVED: %s", evt.getSourceMetaContact());
        uiHandler.post(() -> {
            removeContact(evt.getParentGroup(), evt.getSourceMetaContact());
            notifyDataSetChanged();
        });
    }

    /**
     * Indicates that a <tt>MetaContact</tt> has been moved.
     *
     * @param evt the <tt>MetaContactEvent</tt> that notified us
     */
    public void metaContactMoved(MetaContactMovedEvent evt)
    {
        final MetaContactGroup oldParent = evt.getOldParent();
        final MetaContactGroup newParent = evt.getNewParent();

        final MetaContact metaContact = evt.getSourceMetaContact();
        final String destGroup = newParent.getGroupName();
        Timber.d("CONTACT MOVED (%s): %s to %s", metaContact, oldParent, destGroup);

        // Happen when a contact is moved to RootGroup i.e. "Contacts"; RootGroup is not ContactGroupJabberImpl
        if (!groups.contains(newParent)) {
            Timber.w("Add missing move-to group: %s (%s)", destGroup, newParent.getMetaUID());
            addGroup(newParent, false);
        }

        // Modify original group
        int oldGroupIdx = originalGroups.indexOf(oldParent);
        int newGroupIdx = originalGroups.indexOf(newParent);
        if (oldGroupIdx < 0 || newGroupIdx < 0) {
            Timber.e("Move group error for originalGroups, srcGroupIdx: %s, dstGroupIdx: %s (%s)",
                    oldGroupIdx, newGroupIdx, destGroup);
        }
        else {
            TreeSet<MetaContact> srcGroup = getOriginalCList(oldGroupIdx);
            if (srcGroup != null) {
                srcGroup.remove(metaContact);
            }
            TreeSet<MetaContact> dstGroup = getOriginalCList(newGroupIdx);
            if (dstGroup != null) {
                dstGroup.add(metaContact);
            }
        }

        // Move results group
        oldGroupIdx = groups.indexOf(oldParent);
        newGroupIdx = groups.indexOf(newParent);
        if (oldGroupIdx < 0 || newGroupIdx < 0) {
            Timber.e("Move group error for groups, srcGroupIdx: %s. dstGroupIdx: %s (%s)",
                    oldGroupIdx, newGroupIdx, destGroup);
        }
        else {
            TreeSet<MetaContact> srcGroup = getContactList(oldGroupIdx);
            if (srcGroup != null) {
                srcGroup.remove(metaContact);
            }
            TreeSet<MetaContact> dstGroup = getContactList(newGroupIdx);
            if (dstGroup != null) {
                dstGroup.add(metaContact);
            }

            // Hide oldParent if zero-contacts - not to do this to allow user delete empty new group
            // if (oldParent.countChildContacts() == 0) {
            //    groups.remove(oldParent);
            // }
        }

        // Note: use refreshModelData - create other problems with contacts = null
        uiHandler.post(this::notifyDataSetChanged);
    }

    /**
     * Indicates that a <tt>MetaContact</tt> has been removed from the list.
     *
     * @param evt the <tt>MetaContactEvent</tt> that notified us
     */
    public void metaContactRenamed(final MetaContactRenamedEvent evt)
    {
        Timber.d("CONTACT RENAMED: %s", evt.getSourceMetaContact());
        uiHandler.post(() -> updateDisplayName(evt.getSourceMetaContact()));
    }

    /**
     * Indicates that a protocol specific <tt>Contact</tt> has been added to the list.
     *
     * @param evt the <tt>MetaContactEvent</tt> that notified us
     */
    public void protoContactAdded(final ProtoContactEvent evt)
    {
        Timber.d("PROTO CONTACT ADDED: %s", evt.getNewParent());
        uiHandler.post(() -> updateStatus(evt.getNewParent()));
    }

    /**
     * Indicates that a protocol specific <tt>Contact</tt> has been renamed.
     *
     * @param evt the <tt>MetaContactEvent</tt> that notified us
     */
    public void protoContactRenamed(ProtoContactEvent evt)
    {
        Timber.d("PROTO CONTACT RENAMED: %s", evt.getProtoContact().getAddress());
        invalidateViews();
    }

    /**
     * Indicates that a protocol specific <tt>Contact</tt> has been modified.
     *
     * @param evt the <tt>MetaContactEvent</tt> that notified us
     */
    public void protoContactModified(ProtoContactEvent evt)
    {
        Timber.d("PROTO CONTACT MODIFIED: %s", evt.getProtoContact().getAddress());
        invalidateViews();
    }

    /**
     * Indicates that a protocol specific <tt>Contact</tt> has been removed from the list.
     *
     * @param evt the <tt>MetaContactEvent</tt> that notified us
     */
    public void protoContactRemoved(final ProtoContactEvent evt)
    {
        Timber.d("PROTO CONTACT REMOVED: %s", evt.getProtoContact().getAddress());
        uiHandler.post(() -> updateStatus(evt.getOldParent()));
    }

    /**
     * Indicates that a protocol specific <tt>Contact</tt> has been moved.
     *
     * @param evt the <tt>MetaContactEvent</tt> that notified us
     */
    public void protoContactMoved(final ProtoContactEvent evt)
    {
        Timber.d("PROTO CONTACT MOVED: %s", evt.getProtoContact().getAddress());
        uiHandler.post(() -> {
            updateStatus(evt.getOldParent());
            updateStatus(evt.getNewParent());
        });
    }

    /**
     * Indicates that a <tt>MetaContactGroup</tt> has been added to the list.
     * Need to do it asap, as this method is called as sub-dialog of the addContact and MoveContact
     * Otherwise has problem in i.e. both the originalGroups and Groups do not contain the new metaGroup
     *
     * @param evt the <tt>MetaContactEvent</tt> that notified us
     * @see #metaContactMoved(MetaContactMovedEvent)
     */
    public void metaContactGroupAdded(MetaContactGroupEvent evt)
    {
        MetaContactGroup metaGroup = evt.getSourceMetaContactGroup();
        Timber.d("META CONTACT GROUP ADDED: %s", metaGroup);
        // filtered = false; to add new group to both originalGroups and Groups even with zero contact
        addContacts(metaGroup, false);

        uiHandler.post(this::notifyDataSetChanged);
    }

    /**
     * Indicates that a <tt>MetaContactGroup</tt> has been modified.
     *
     * @param evt the <tt>MetaContactEvent</tt> that notified us
     */
    public void metaContactGroupModified(MetaContactGroupEvent evt)
    {
        Timber.d("META CONTACT GROUP MODIFIED: %s", evt.getSourceMetaContactGroup());
        invalidateViews();
    }

    /**
     * Indicates that a <tt>MetaContactGroup</tt> has been removed from the list.
     *
     * @param evt the <tt>MetaContactEvent</tt> that notified us
     */
    public void metaContactGroupRemoved(MetaContactGroupEvent evt)
    {
        Timber.d("META CONTACT GROUP REMOVED: %s", evt.getSourceMetaContactGroup());
        uiHandler.post(() -> {
            removeGroup(evt.getSourceMetaContactGroup());
            notifyDataSetChanged();
        });
    }

    /**
     * Indicates that the child contacts of a given <tt>MetaContactGroup</tt> has been reordered.
     * Note:
     * 1. add (insert) new before remove old data to avoid indexOutOfBound
     * 2. synchronized LinkList access to avoid ConcurrentModificationException
     *
     * @param evt the <tt>MetaContactEvent</tt> that notified us
     */
    public void childContactsReordered(MetaContactGroupEvent evt)
    {
        Timber.d("CHILD CONTACTS REORDERED: %s", evt.getSourceMetaContactGroup());
        uiHandler.post(() -> {
            MetaContactGroup group = evt.getSourceMetaContactGroup();
            int origGroupIndex = originalGroups.indexOf(group);
            int groupIndex = groups.indexOf(group);

            if (origGroupIndex >= 0) {
                TreeSet<MetaContact> contactList = getOriginalCList(origGroupIndex);

                if (contactList != null) {
                    // Timber.w("Modify originalGroups: " + origGroupIndex + " / " + originalGroups.size());
                    synchronized (originalContacts) {
                        originalContacts.add(origGroupIndex, new TreeSet<>(contactList));
                        originalContacts.remove(origGroupIndex + 1);
                    }
                }
            }

            if (groupIndex >= 0) {
                TreeSet<MetaContact> contactList = getContactList(groupIndex);

                if (contactList != null) {
                    // Timber.w("Modify groups: " + groupIndex + " / " + groups.size());
                    synchronized (contacts) {
                        contacts.add(groupIndex, new TreeSet<>(contactList));
                        contacts.remove(groupIndex + 1);
                    }
                }
            }
            notifyDataSetChanged();
        });
    }

    /**
     * Indicates that a <tt>MetaContact</tt> avatar has changed and needs to be updated.
     *
     * @param evt the <tt>MetaContactEvent</tt> that notified us
     */
    public void metaContactAvatarUpdated(final MetaContactAvatarUpdateEvent evt)
    {
        Timber.log(TimberLog.FINER, "metaContact avatar updated: %s", evt.getSourceMetaContact());
        uiHandler.post(() -> updateAvatar(evt.getSourceMetaContact()));
    }

    /**
     * Returns the contained object on the given <tt>groupPosition</tt> and <tt>childPosition</tt>.
     * Note that this method must be called on UI thread.
     *
     * @param groupPosition the index of the group
     * @param childPosition the index of the child
     * @return the contained object on the given <tt>groupPosition</tt> and <tt>childPosition</tt>
     */
    @Override
    public Object getChild(int groupPosition, int childPosition)
    {
        if (contacts.size() > 0) {
            TreeSet<MetaContact> contactList = getContactList(groupPosition);
            if (contactList != null) {
                int i = 0;
                for (MetaContact metaContact : contactList) {
                    if (i == childPosition) {
                        return metaContact;
                    }
                    i++;
                }
            }
        }
        return null;
    }

    /**
     * Return metaContact index in the contactList
     **/
    private int getChildIndex(TreeSet<MetaContact> contactList, MetaContact metaContact)
    {
        if ((contactList == null) || contactList.isEmpty())
            return -1;

        int i = 0;
        for (MetaContact mContact : contactList) {
            if (metaContact.equals(mContact))
                return i;
            i++;
        }
        return -1;
    }

    /**
     * Filters list data to match the given <tt>query</tt>.
     *
     * @param query the query we'd like to match
     */
    public void filterData(String query)
    {
        uiHandler.post(() -> {
            currentFilterQuery = query.toLowerCase(Locale.US);
            groups.clear();
            contacts.clear();

            if (presenceFilter.isShowOffline() && TextUtils.isEmpty(query)) {
                // hide group contains zero contact
                for (MetaContactGroup metaGroup : originalGroups) {
                    if (metaGroup.countChildContacts() > 0) {
                        int groupIndex = originalGroups.indexOf(metaGroup);
                        groups.add(metaGroup);
                        contacts.add(getOriginalCList(groupIndex));
                    }
                }
            }
            else {
                for (MetaContactGroup metaGroup : originalGroups) {
                    if (metaGroup.countChildContacts() > 0) {
                        int groupIndex = originalGroups.indexOf(metaGroup);
                        TreeSet<MetaContact> contactList = getOriginalCList(groupIndex);

                        if (contactList != null) {
                            TreeSet<MetaContact> filteredList = new TreeSet<>();
                            for (MetaContact metaContact : contactList) {
                                if (isMatching(metaContact, query))
                                    filteredList.add(metaContact);
                            }

                            if (filteredList.size() > 0) {
                                groups.add(metaGroup);
                                contacts.add(filteredList);
                            }
                        }
                    }
                }
            }
            notifyDataSetChanged();
            expandAllGroups();
        });
    }


    /**
     * Create group/contacts TreeView with non-zero groups
     */
    public void nonZeroContactGroupList()
    {
        groups.clear();
        contacts.clear();

        // hide group contains zero contact
        for (MetaContactGroup metaGroup : originalGroups) {
            if (metaGroup.countChildContacts() > 0) {
                int groupIndex = originalGroups.indexOf(metaGroup);
                groups.add(metaGroup);
                contacts.add(getOriginalCList(groupIndex));
            }
        }
    }

    /**
     * Checks if the given <tt>metaContact</tt> is matching the given <tt>query</tt>.
     * A <tt>MetaContact</tt> would be matching the filter if one of the following is true:<br>
     * - it is online or user chooses show offline contacts
     * - its display name contains the filter string
     * - at least one of its child protocol contacts has a display name or
     * - an address that contains the filter string.
     *
     * @param metaContact the <tt>MetaContact</tt> to check
     * @param query the query string to check for matches
     * @return <tt>true</tt> to indicate that the given <tt>metaContact</tt> is matching the
     * current filter, otherwise returns <tt>false</tt>
     */
    private boolean isMatching(MetaContact metaContact, String query)
    {
        if (presenceFilter.isMatching(metaContact)) {
            if (TextUtils.isEmpty(query))
                return true;

            Pattern queryPattern = Pattern.compile(query, Pattern.CASE_INSENSITIVE | Pattern.LITERAL);
            if (queryPattern.matcher(metaContact.getDisplayName()).find())
                return true;
            else {
                Iterator<Contact> contacts = metaContact.getContacts();
                while (contacts.hasNext()) {
                    Contact contact = contacts.next();

                    if (queryPattern.matcher(contact.getDisplayName()).find()
                            || queryPattern.matcher(contact.getAddress()).find()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Checks if the given <tt>metaGroup</tt> is matching the current filter. A group is matching
     * the current filter only if it contains at least one child <tt>MetaContact</tt>, which is
     * matching the current filter.<br/>
     * Note that this method must be called on UI thread.
     *
     * @param metaGroup the <tt>MetaContactGroup</tt> to check
     * @param query the query string to check for matches
     * @return <tt>true</tt> to indicate that the given <tt>metaGroup</tt> is matching the current
     * filter, otherwise returns <tt>false</tt>
     */
    private boolean isMatching(MetaContactGroup metaGroup, String query)
    {
        if (presenceFilter.isMatching(metaGroup)) {
            if (TextUtils.isEmpty(query))
                return true;

            Iterator<MetaContact> contacts = metaGroup.getChildContacts();
            while (contacts.hasNext()) {
                MetaContact metaContact = contacts.next();

                if (isMatching(metaContact, query))
                    return true;
            }
        }
        return false;
    }

    public void setDialogMode(boolean isDialogMode)
    {
        mDialogMode = isDialogMode;
    }

    /**
     * Indicates that a contact Presence Status Change has been received.
     *
     * mDialog true indicates the contact list is shown for user multiple selection e.g. invite;
     * in this case do not refreshModelData() to sort, as items selected is tracked by their position
     *
     * @param event the <tt>ContactPresenceStatusChangeEvent</tt> that notified us
     */
    @Override
    public void contactPresenceStatusChanged(final ContactPresenceStatusChangeEvent event)
    {
        uiHandler.post(() -> {
            //  mDialogMode: just update the status icon without sorting
            if (mDialogMode) {
                Contact sourceContact = event.getSourceContact();
                Timber.d("Contact presence status changed: %s", sourceContact.getAddress());

                MetaContact metaContact = contactListService.findMetaContactByContact(sourceContact);
                // metaContact is already existing, just update it
                if (metaContact != null) {
                    updateStatus(metaContact);
                }
            }
            else {
                refreshModelData();
            }
        });
    }

    /**
     * Refresh the contact list from contactListService, with contact presence status sorted esp originalContacts
     * Then perform filterData if showOffline contacts is disabled, other newly online contacts are not included
     */
    private void refreshModelData()
    {
        originalGroups.clear();
        originalContacts.clear();
        groups.clear();
        contacts.clear();
        addContacts(contactListService.getRoot(), true);

        if (!presenceFilter.isShowOffline()) {
            filterData("");
        }
    }

    /**
     * Adds the given <tt>ContactPresenceStatusListener</tt> to listen for contact presence status change.
     *
     * @param metaContact the <tt>MetaContact</tt> for which we add the listener
     * @param l the <tt>MessageListener</tt> to add
     */
    private void addContactStatusListener(MetaContact metaContact, ContactPresenceStatusListener l)
    {
        Iterator<Contact> protoContacts = metaContact.getContacts();
        while (protoContacts.hasNext()) {
            Contact protoContact = protoContacts.next();
            OperationSetPresence presenceOpSet
                    = protoContact.getProtocolProvider().getOperationSet(OperationSetPresence.class);

            if (presenceOpSet != null) {
                presenceOpSet.addContactPresenceStatusListener(l);
            }
        }
    }

    /**
     * Remove the given <tt>ContactPresenceStatusListener</tt> to listen for contact presence status change.
     *
     * @param metaContact the <tt>MetaContact</tt> for which we remove the listener
     * @param l the <tt>MessageListener</tt> to remove
     */
    private void removeContactStatusListener(MetaContact metaContact, ContactPresenceStatusListener l)
    {
        Iterator<Contact> protoContacts = metaContact.getContacts();
        while (protoContacts.hasNext()) {
            Contact protoContact = protoContacts.next();
            OperationSetPresence presenceOpSet
                    = protoContact.getProtocolProvider().getOperationSet(OperationSetPresence.class);

            if (presenceOpSet != null) {
                presenceOpSet.removeContactPresenceStatusListener(l);
            }
        }
    }

    /**
     * Checks if given <tt>metaContact</tt> is considered to be selected. That is if the chat
     * session with given <tt>metaContact</tt> is the one currently visible.
     *
     * @param metaContact the <tt>MetaContact</tt> to check.
     * @return <tt>true</tt> if given <tt>metaContact</tt> is considered to be selected.
     */
    public static boolean isContactSelected(MetaContact metaContact)
    {
        return ChatSessionManager.getCurrentChatId() != null
                && ChatSessionManager.getActiveChat(metaContact) != null
                && ChatSessionManager.getCurrentChatId().equals(
                ChatSessionManager.getActiveChat(metaContact).getChatSession().getChatId());
    }

    /**
     * Implements {@link UIGroupRenderer}. {@inheritDoc}
     */
    @Override
    public String getDisplayName(Object groupImpl)
    {
        MetaContactGroup metaGroup = (MetaContactGroup) groupImpl;
        if (metaGroup.equals(contactListService.getRoot()))
            return ContactGroup.ROOT_GROUP_NAME;
        else
            return metaGroup.getGroupName();
    }

    //	/**
    //	 * Sets the default filter to the given <tt>filter</tt>.
    //	 *
    //	 * @param filter the <tt>ContactListFilter</tt> to set as default
    //	 */
    //	public void setDefaultFilter(ContactListFilter filter) {
    //		this.defaultFilter = filter;
    //		this.currentFilter = defaultFilter;
    //	}
    //
    //	/**
    //	 * Gets the default filter for this contact list.
    //	 *
    //	 * @return the default filter for this contact list
    //	 */
    //	public ContactListFilter getDefaultFilter() {
    //		return defaultFilter;
    //	}
    //
    //	/**
    //	 * Returns the currently applied filter.
    //	 *
    //	 * @return the currently applied filter
    //	 */
    //	public ContactListFilter getCurrentFilter() {
    //		return currentFilter;
    //	}
    //
    //	/**
    //	 * Returns the currently applied filter.
    //	 *
    //	 * @return the currently applied filter
    //	 */
    //
    //	public String getCurrentFilterQuery() {
    //		return currentFilterQuery;
    //	}
    //
    //	/**
    //	 * Initializes the list of available contact sources for this contact list.
    //	 */
    //	private void initContactSources() {
    //		List<ContactSourceService> contactSources = AndroidGUIActivator.getContactSources();
    //		for (ContactSourceService contactSource : contactSources) {
    //			if (!(contactSource instanceof AsyncContactSourceService)
    //					|| ((AsyncContactSourceService) contactSource).canBeUsedToSearchContacts()) {
    //
    //				// ExternalContactSource extContactSource = new ExternalContactSource(contactSource, this);
    //				int sourceIndex = contactSource.getIndex();
    ////				if (sourceIndex >= 0 && mContactSources.size() >= sourceIndex)
    ////					mContactSources.add(sourceIndex, extContactSource);
    ////				else
    ////					mContactSources.add(extContactSource);
    //			}
    //		}
    ////		AndroidGUIActivator.bundleContext.addServiceListener(new ContactSourceServiceListener());
    //	}
    //
    //	/**
    //	 * Returns the list of registered contact sources to search in.
    //	 *
    //	 * @return the list of registered contact sources to search in
    //	 */
    //	public List<ContactSourceService> getContactSources() {
    //		return mContactSources;
    //	}
    //
    //
    //	/**
    //	 * Adds the given contact source to the list of available contact sources.
    //	 *
    //	 * @param contactSource
    //	 * 		the <tt>ContactSourceService</tt>
    //	 */
    //	public void addContactSource(ContactSourceService contactSource) {
    ////		if (!(contactSource instanceof AsyncContactSourceService)
    ////				|| ((AsyncContactSourceService) contactSource).canBeUsedToSearchContacts()) {
    ////			mContactSources.add(new ExternalContactSource(contactSource, this));
    ////		}
    //	}
    //
    //	/**
    //	 * Removes the given contact source from the list of available contact
    //	 * sources.
    //	 *
    //	 * @param contactSource
    //	 */
    //	public void removeContactSource(ContactSourceService contactSource) {
    ////		for (ContactSourceService extSource : mContactSources) {
    ////			if (extSource.getContactSourceService().equals(contactSource)) {
    ////				mContactSources.remove(extSource);
    ////				break;
    ////			}
    ////		}
    //	}
    //
    //	/**
    //	 * Removes all stored contact sources.
    //	 */
    //	public void removeAllContactSources() {
    //		mContactSources.clear();
    //	}
    //
    //	/**
    //	 * Returns the notification contact source.
    //	 *
    //	 * @return the notification contact source
    //	 */
    ////	public static NotificationContactSource getNotificationContactSource()
    ////	{
    ////		if (notificationSource == null)
    ////			notificationSource = new NotificationContactSource();
    ////		return notificationSource;
    ////	}
    //
    //	/**
    //	 * Returns the <tt>ExternalContactSource</tt> corresponding to the given
    //	 * <tt>ContactSourceService</tt>.
    //	 *
    //	 * @param contactSource the <tt>ContactSourceService</tt>, which
    //	 * 		corresponding external source implementation we're looking for
    //	 * @return the <tt>ExternalContactSource</tt> corresponding to the given <tt>ContactSourceService</tt>
    //	 */
    //	public ContactSourceService getContactSource(ContactSourceService contactSource) {
    ////		for (ContactSourceService extSource : mContactSources) {
    ////			if (extSource.getContactSourceService().equals(contactSource)) {
    ////				return extSource;
    ////			}
    ////		}
    //		return null;
    //	}
    //
    //	/**
    //	 * Returns all <tt>UIContactSource</tt>s of the given type.
    //	 *
    //	 * @param type the type of sources we're looking for
    //	 * @return a list of all <tt>UIContactSource</tt>s of the given type
    //	 */
    //	public List<ContactSourceService> getContactSources(int type) {
    ////		List<ContactSourceService> sources = new ArrayList<>();
    //
    ////		for (ContactSourceService extSource : mContactSources) {
    ////			if (extSource.getContactSourceService().getType() == type)
    ////				sources.add(extSource);
    ////		}
    //		return null; // sources;
    //	}
}
